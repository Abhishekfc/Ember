import XCTest
@testable import Emigo

private final class NoTokens: AccessTokenProviding {
    func idToken() async -> String? { nil }
}

private func friend(_ id: String, name: String = "Friend", pinned: Bool = false) -> FriendSummary {
    let json = """
    {"friendshipId":"fs-\(id)","friendId":"\(id)","displayName":"\(name)","username":"u\(id)",
     "profilePhotoUrl":null,"pinnedByMe":\(pinned),"pinnedByThem":false,"lastActivityAt":null,
     "lastActivityBySelf":null,"streak":0}
    """
    return try! JSONDecoder.emigo.decode(FriendSummary.self, from: Data(json.utf8))
}

@MainActor
final class CameraLogicTests: XCTestCase {
    private var defaults: UserDefaults!
    private var store: SessionStore!
    private var api: APIClient!
    private var directory: URL!

    override func setUp() async throws {
        defaults = UserDefaults(suiteName: "emigo.tests.\(UUID().uuidString)")!
        store = SessionStore(defaults: defaults)
        api = APIClient(baseURL: URL(string: "https://api.example/")!, tokenProvider: NoTokens(), session: APIClient.makeSession(protocolClasses: [StubURLProtocol.self]))
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("EmigoCamTests-\(UUID().uuidString)")
    }

    override func tearDown() async throws {
        try? FileManager.default.removeItem(at: directory)
    }

    private func makeCameraModel() -> CameraViewModel {
        let photos = PhotoRepository(api: api)
        return CameraViewModel(
            friends: FriendRepository(api: api),
            photos: photos,
            subscription: SubscriptionRepository(api: api, store: store),
            queue: PendingSendQueue(directory: directory, transport: photos),
            store: store
        )
    }

    // MARK: Who a photo goes to by default

    func testThePinnedFriendIsChosenFirst() {
        let model = makeCameraModel()
        store.lastRecipientIds = ["b"]
        model.provideFriends([friend("a"), friend("b"), friend("c", pinned: true)])
        XCTAssertEqual(model.selectedRecipientIds, ["c"])
        XCTAssertTrue(model.hasPinnedSelected)
    }

    func testWithNobodyPinnedTheLastRecipientsAreChosen() {
        let model = makeCameraModel()
        store.lastRecipientIds = ["b", "gone"]
        model.provideFriends([friend("a"), friend("b")])
        XCTAssertEqual(model.selectedRecipientIds, ["b"], "someone who's no longer a friend is skipped")
    }

    func testWithNoHistoryNobodyIsChosenYet() {
        let model = makeCameraModel()
        model.provideFriends([friend("a"), friend("b")])
        XCTAssertTrue(model.selectedRecipientIds.isEmpty)
        XCTAssertFalse(model.hasRecipients)
    }

    func testTheChosenFriendsAreListedInTheOrderOfTheFriendsList() {
        let model = makeCameraModel()
        model.provideFriends([friend("a"), friend("b"), friend("c")])
        model.setSelectedRecipients(["c", "a"])
        XCTAssertEqual(model.selectedFriends.map(\.friendId), ["a", "c"])
    }

    func testGalleryPhotosNeedGold() {
        let model = makeCameraModel()
        var opened = false
        model.galleryTapped { opened = true }
        XCTAssertFalse(opened)
        XCTAssertTrue(model.showGoldUpsell)

        store.isGoldMember = true
        let goldModel = makeCameraModel()
        goldModel.galleryTapped { opened = true }
        XCTAssertTrue(opened)
        XCTAssertFalse(goldModel.showGoldUpsell)
    }

    func testSendingWithNobodyChosenSaysSo() async {
        let model = makeCameraModel()
        let photo = CapturedPhoto(jpeg: SimulatedCamera.jpeg(), isFrontCamera: false)
        await model.didCapture(photo)
        XCTAssertTrue(model.isReviewing)

        await model.sendCaptured()
        XCTAssertEqual(model.errorMessage, "Select at least one friend first")
        XCTAssertTrue(model.isReviewing, "the photo is still there to send")
    }

    func testSendingQueuesThePhotoAndClearsTheScreen() async {
        let model = makeCameraModel()
        model.provideFriends([friend("a")])
        model.setSelectedRecipients(["a"])
        await model.didCapture(CapturedPhoto(jpeg: SimulatedCamera.jpeg(), isFrontCamera: false))
        model.captionText = "hello"

        await model.sendCaptured()

        XCTAssertFalse(model.isReviewing)
        XCTAssertEqual(model.sendAnimState, .sending)
        XCTAssertEqual(store.lastRecipientIds, ["a"], "remembered for next time")
        XCTAssertEqual(model.captionText, "")
    }

    func testRetakeThrowsTheCaptureAway() async {
        let model = makeCameraModel()
        await model.didCapture(CapturedPhoto(jpeg: SimulatedCamera.jpeg(), isFrontCamera: false))
        model.captionText = "draft"
        model.discardCapture()
        XCTAssertFalse(model.isReviewing)
        XCTAssertEqual(model.captionText, "")
    }

    // MARK: Picking people

    private func makePicker(selected: [String], friends: [FriendSummary]) -> RecipientPickerViewModel {
        RecipientPickerViewModel(repository: FriendRepository(api: api), store: store, initialSelected: selected, initialFriends: friends)
    }

    func testTheChosenFriendsAreListedFirst() {
        let picker = makePicker(selected: ["c"], friends: [friend("a"), friend("b"), friend("c")])
        XCTAssertEqual(picker.visibleFriends.map(\.friendId), ["c", "a", "b"])
    }

    func testTogglingDoesNotReshuffleTheList() {
        let picker = makePicker(selected: ["c"], friends: [friend("a"), friend("b"), friend("c")])
        picker.toggle("a")
        XCTAssertEqual(picker.visibleFriends.map(\.friendId), ["c", "a", "b"])
        XCTAssertEqual(picker.selectionInOrder, ["a", "c"])
    }

    func testSearchFindsByNameOrUsername() {
        let picker = makePicker(selected: [], friends: [friend("1", name: "Aditya Rao"), friend("2", name: "Margot Lin")])
        picker.searchQuery = "marg"
        XCTAssertEqual(picker.visibleFriends.map(\.friendId), ["2"])
        picker.searchQuery = "u1"
        XCTAssertEqual(picker.visibleFriends.map(\.friendId), ["1"])
    }

    func testEveryoneSelectsAllFriends() {
        let picker = makePicker(selected: [], friends: [friend("a"), friend("b")])
        picker.selectEveryone()
        XCTAssertEqual(picker.selectedFriendIds, ["a", "b"])
        XCTAssertEqual(picker.activeFilterId, RecipientPickerViewModel.everyoneBadgeId)
    }

    func testTheRightBadgeIsLitForTheCurrentSelection() {
        let friends = [friend("a"), friend("b")]
        XCTAssertEqual(
            RecipientPickerViewModel.matchingFilter(selected: ["a", "b"], recent: [], friends: friends, lists: []),
            RecipientPickerViewModel.everyoneBadgeId
        )
        XCTAssertEqual(
            RecipientPickerViewModel.matchingFilter(selected: ["a"], recent: ["a"], friends: friends, lists: []),
            RecipientPickerViewModel.recentBadgeId
        )
        XCTAssertNil(RecipientPickerViewModel.matchingFilter(selected: ["a"], recent: [], friends: friends, lists: []))
    }

    // MARK: Sent photos

    func testASentPhotoCanBeTakenBackFor24Hours() throws {
        let json = #"{"photoId":"p","photoUrl":"https://x/p.jpg","createdAt":"2026-10-06T10:00:00Z"}"#
        let photo = try JSONDecoder.emigo.decode(SentPhoto.self, from: Data(json.utf8))
        let deadline = SentPhotosViewModel.unsendDeadline(for: photo)
        XCTAssertEqual(deadline.timeIntervalSince(photo.createdAt), 24 * 3600, accuracy: 0.001)
    }
}
