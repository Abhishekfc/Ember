import XCTest
@testable import Emigo

private final class TokenStub: AccessTokenProviding {
    func idToken() async -> String? { "token" }
}

/// What the stubbed server answers for "when did you last open Activity". Read from the URL
/// loading thread, set from tests.
private final class LastSeenAnswer: @unchecked Sendable {
    var status = 200
    var body = #"{"lastSeenAt":null}"#
    /// How long that answer takes, so it can arrive after the events do.
    var delay: TimeInterval = 0
}

/// The number on the bell must never show a wrong value, even for a moment: the app used to count
/// every event as new until the "last seen" time arrived, so a badge popped up and vanished.
@MainActor
final class ActivityBadgeTests: XCTestCase {
    private let answer = LastSeenAnswer()

    private let events = #"""
    {"items":[
      {"type":"PHOTO_RECEIVED","actorId":"u1","actorDisplayName":"Ann","actorProfilePhotoUrl":null,"message":"sent a photo","createdAt":"2026-10-06T10:00:00Z","warn":false,"photoUrl":null},
      {"type":"REQUEST_ACCEPTED","actorId":"u2","actorDisplayName":"Bo","actorProfilePhotoUrl":null,"message":"accepted","createdAt":"2026-10-06T11:00:00Z","warn":false,"photoUrl":null}
    ],"hasMore":false}
    """#

    private func makeModel() -> ActivityViewModel {
        let answer = answer
        let events = events
        StubURLProtocol.handler = { request in
            switch request.url?.path {
            case "/activity":
                return (200, Data(events.utf8))
            case "/activity/seen":
                if answer.delay > 0 { Thread.sleep(forTimeInterval: answer.delay) }
                return (answer.status, Data(answer.body.utf8))
            default:
                return (204, Data())
            }
        }
        let api = APIClient(
            baseURL: URL(string: "https://api.example/")!,
            tokenProvider: TokenStub(),
            session: APIClient.makeSession(protocolClasses: [StubURLProtocol.self])
        )
        return ActivityViewModel(repository: ActivityRepository(api: api))
    }

    override func tearDown() async throws {
        StubURLProtocol.handler = nil
    }

    func testTheBellNeverCountsEverythingWhileTheLastSeenTimeIsStillOnItsWay() async throws {
        let model = makeModel()
        answer.body = #"{"lastSeenAt":"2026-10-06T12:00:00Z"}"#   // everything has been seen
        answer.delay = 0.3

        let loading = Task { await model.load() }
        while !model.hasLoaded {
            XCTAssertEqual(model.newCount, 0, "a badge flashed up before the last-seen time arrived")
            XCTAssertTrue(model.events.isEmpty, "events were shown before the badge could be worked out")
            try await Task.sleep(for: .milliseconds(10))
        }
        await loading.value

        XCTAssertEqual(model.events.count, 2)
        XCTAssertEqual(model.newCount, 0)
    }

    func testOnlyEventsAfterTheLastSeenTimeCount() async {
        let model = makeModel()
        answer.body = #"{"lastSeenAt":"2026-10-06T10:30:00Z"}"#

        await model.load()

        XCTAssertEqual(model.newCount, 1, "only Bo's 11:00 event is newer than 10:30")
    }

    func testSomeoneWhoNeverOpenedActivityHasEverythingNew() async {
        let model = makeModel()
        answer.body = #"{"lastSeenAt":null}"#

        await model.load()

        XCTAssertEqual(model.newCount, 2)
    }

    func testIfTheLastSeenTimeCannotBeReadTheBellStaysEmptyInsteadOfGuessing() async {
        let model = makeModel()
        answer.status = 500
        answer.body = #"{"status":500,"error":"Oops","message":null}"#

        await model.load()

        XCTAssertTrue(model.hasLoaded)
        XCTAssertEqual(model.events.count, 2)
        XCTAssertEqual(model.newCount, 0)
    }

    func testOpeningActivityClearsTheBell() async {
        let model = makeModel()
        answer.body = #"{"lastSeenAt":null}"#
        await model.load()
        XCTAssertEqual(model.newCount, 2)

        await model.markSeen()

        XCTAssertEqual(model.newCount, 0)
    }
}
