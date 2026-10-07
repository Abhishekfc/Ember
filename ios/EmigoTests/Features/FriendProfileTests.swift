import XCTest
@testable import Emigo

private final class SignedOutTokens: AccessTokenProviding {
    func idToken() async -> String? { "token" }
}

private func friendJSON(id: String = "f1", pinned: Bool = false) -> String {
    """
    {"friendshipId":"fs-\(id)","friendId":"\(id)","displayName":"Ann Lee","username":"ann",
     "profilePhotoUrl":null,"pinnedByMe":\(pinned),"pinnedByThem":false,"lastActivityAt":null,
     "lastActivityBySelf":null,"streak":4}
    """
}

@MainActor
final class FriendProfileTests: XCTestCase {
    private var api: APIClient!

    override func setUp() async throws {
        api = APIClient(
            baseURL: URL(string: "https://api.example/")!,
            tokenProvider: SignedOutTokens(),
            session: APIClient.makeSession(protocolClasses: [StubURLProtocol.self])
        )
    }

    override func tearDown() async throws {
        StubURLProtocol.handler = nil
    }

    private func makeModel(_ subject: ProfileSubject) -> FriendProfileViewModel {
        FriendProfileViewModel(subject: subject, friends: FriendRepository(api: api), safety: SafetyRepository(api: api))
    }

    private func friend(pinned: Bool = false) throws -> FriendSummary {
        try JSONDecoder.emigo.decode(FriendSummary.self, from: Data(friendJSON(pinned: pinned).utf8))
    }

    private func pending() throws -> PendingFriendRequest {
        let json = #"{"friendshipId":"fs-req","requesterId":"u9","displayName":"Bo","username":"bo","profilePhotoUrl":null,"createdAt":"2026-10-06T10:00:00Z"}"#
        return try JSONDecoder.emigo.decode(PendingFriendRequest.self, from: Data(json.utf8))
    }

    private func respond(_ routes: [String: (Int, String)]) {
        StubURLProtocol.handler = { request in
            let key = "\(request.httpMethod ?? "GET") \(request.url?.path ?? "")"
            let (status, body) = routes[key] ?? (404, #"{"status":404,"error":"Not Found","message":null}"#)
            return (status, Data(body.utf8))
        }
    }

    func testPinningAFriendUpdatesTheProfile() async throws {
        respond(["POST /friends/fs-f1/pin": (200, friendJSON(pinned: true))])
        let model = makeModel(.friend(try friend()))

        let worked = await model.togglePin()

        XCTAssertTrue(worked)
        XCTAssertEqual(model.subject.friend?.pinnedByMe, true)
    }

    func testUnpinningUsesTheOtherEndpoint() async throws {
        respond(["DELETE /friends/fs-f1/pin": (200, friendJSON(pinned: false))])
        let model = makeModel(.friend(try friend(pinned: true)))

        let worked = await model.togglePin()
        XCTAssertTrue(worked)
        XCTAssertEqual(model.subject.friend?.pinnedByMe, false)
    }

    func testAcceptingARequestMakesThemAFriend() async throws {
        respond(["POST /friends/accept": (200, friendJSON(id: "u9"))])
        let model = makeModel(.pendingRequest(try pending()))

        let worked = await model.acceptRequest()
        XCTAssertTrue(worked)
        XCTAssertEqual(model.subject.friend?.friendId, "u9")
    }

    func testAskingSomeoneToBeFriendsMarksTheRequestSent() async throws {
        respond(["POST /friends/request": (200, #"{"friendshipId":"fs-new","requesterId":"me","displayName":"Cy","username":"cy","profilePhotoUrl":null,"createdAt":"2026-10-06T10:00:00Z"}"#)])
        let model = makeModel(.searchResult(FriendSearchResult(userId: "u5", displayName: "Cy", username: "cy", requested: false)))

        let worked = await model.sendRequest()
        XCTAssertTrue(worked)
        guard case .searchResult(let result) = model.subject else { return XCTFail("still a search result") }
        XCTAssertTrue(result.requested)
        XCTAssertTrue(result.isPendingFromMe)
        XCTAssertEqual(result.friendshipId, "fs-new")
    }

    func testCancellingARequestPutsItBack() async {
        respond(["DELETE /friends/fs-new": (204, "")])
        let sent = FriendSearchResult(userId: "u5", displayName: "Cy", username: "cy", requested: true, friendshipId: "fs-new", isPendingFromMe: true)
        let model = makeModel(.searchResult(sent))

        let worked = await model.cancelRequest()
        XCTAssertTrue(worked)
        guard case .searchResult(let result) = model.subject else { return XCTFail("still a search result") }
        XCTAssertFalse(result.requested)
        XCTAssertNil(result.friendshipId)
    }

    func testAFailureSaysSoAndChangesNothing() async throws {
        respond(["POST /friends/fs-f1/pin": (500, #"{"status":500,"error":"Oops","message":null}"#)])
        let original = try friend()
        let model = makeModel(.friend(original))

        let worked = await model.togglePin()

        XCTAssertFalse(worked)
        XCTAssertEqual(model.errorMessage, "Something went wrong")
        XCTAssertEqual(model.subject.friend, original)
        XCTAssertNil(model.running)
    }

    func testSomeoneYoureAlreadyFriendsWithBecomesARealProfile() async throws {
        let page = #"{"items":[\#(friendJSON(id: "u5"))],"hasMore":false}"#
        respond(["GET /friends": (200, page)])
        let alreadyFriend = FriendSearchResult(userId: "u5", displayName: "Ann Lee", username: "ann", requested: true)
        let model = makeModel(.searchResult(alreadyFriend))

        await model.resolveExistingFriend()

        XCTAssertEqual(model.subject.friend?.friendId, "u5")
    }

    func testBlockingAndReporting() async throws {
        respond([
            "POST /users/f1/block": (204, ""),
            "POST /users/f1/report": (204, ""),
        ])
        let model = makeModel(.friend(try friend()))

        let blocked = await model.blockUser()
        let reported = await model.reportUser(reason: .spam)
        XCTAssertTrue(blocked)
        XCTAssertTrue(reported)
        XCTAssertTrue(model.reportSubmitted)
        model.dismissReportConfirmation()
        XCTAssertFalse(model.reportSubmitted)
    }

    func testTheSubjectKnowsWhoItIs() throws {
        let request = ProfileSubject.pendingRequest(try pending())
        XCTAssertEqual(request.userId, "u9", "for a request, the person is the one who asked")
        XCTAssertEqual(request.friendshipId, "fs-req")
        XCTAssertNil(request.friend)

        let found = ProfileSubject.searchResult(FriendSearchResult(userId: "u5", displayName: "Cy", username: "cy", requested: false))
        XCTAssertNil(found.profilePhotoURL)
        XCTAssertNil(found.friendshipId)
    }
}
