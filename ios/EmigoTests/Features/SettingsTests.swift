import XCTest
@testable import Emigo

private final class FixedToken: AccessTokenProviding {
    func idToken() async -> String? { "token" }
}

@MainActor
private final class FakeAuthorizer: NotificationAuthorizing {
    var allowed: Bool
    private(set) var asked = 0

    init(allowed: Bool) { self.allowed = allowed }

    func isAllowed() async -> Bool { allowed }

    func requestPermission() async -> Bool {
        asked += 1
        return allowed
    }
}

@MainActor
final class SettingsViewModelTests: XCTestCase {
    private func makeStore() -> SessionStore {
        SessionStore(defaults: UserDefaults(suiteName: "SettingsTests-\(UUID().uuidString)")!)
    }

    func testTurningOnAsksAndKeepsTheAnswer() async {
        let store = makeStore()
        let authorizer = FakeAuthorizer(allowed: true)
        let model = SettingsViewModel(store: store, authorizer: authorizer)

        await model.setNotifications(true)

        XCTAssertTrue(model.notificationsEnabled)
        XCTAssertTrue(store.notificationsEnabled)
        XCTAssertEqual(authorizer.asked, 1)
        XCTAssertFalse(model.isShowingNotificationsDenied)
    }

    func testTurningOnWhenIOSSaysNoStaysOffAndExplains() async {
        let store = makeStore()
        let model = SettingsViewModel(store: store, authorizer: FakeAuthorizer(allowed: false))

        await model.setNotifications(true)

        XCTAssertFalse(model.notificationsEnabled)
        XCTAssertTrue(model.isShowingNotificationsDenied)
    }

    func testTurningOffIsSavedAndNeverAsks() async {
        let store = makeStore()
        let authorizer = FakeAuthorizer(allowed: true)
        let model = SettingsViewModel(store: store, authorizer: authorizer)
        await model.setNotifications(true)

        await model.setNotifications(false)

        XCTAssertFalse(model.notificationsEnabled)
        XCTAssertFalse(store.notificationsEnabled)
        XCTAssertEqual(authorizer.asked, 1)
    }

    func testTheSwitchReadsOnlyWhenWantedAndAllowed() async {
        let store = makeStore()
        let authorizer = FakeAuthorizer(allowed: true)
        let model = SettingsViewModel(store: store, authorizer: authorizer)

        await model.refresh()
        XCTAssertTrue(model.notificationsEnabled, "wanted by default, and allowed")

        authorizer.allowed = false
        await model.refresh()
        XCTAssertFalse(model.notificationsEnabled, "turned off in the iPhone's own Settings")

        authorizer.allowed = true
        store.notificationsEnabled = false
        await model.refresh()
        XCTAssertFalse(model.notificationsEnabled, "allowed, but switched off in Emigo")
    }

    func testNotificationsAreOnUntilSwitchedOffAndForgottenOnSignOut() {
        let store = makeStore()
        XCTAssertTrue(store.notificationsEnabled)
        store.notificationsEnabled = false
        store.clear()
        XCTAssertTrue(store.notificationsEnabled)
    }
}

@MainActor
final class DeleteAccountViewModelTests: XCTestCase {
    private func makeModel() -> DeleteAccountViewModel {
        let api = APIClient(
            baseURL: URL(string: "https://api.example/")!,
            tokenProvider: FixedToken(),
            session: APIClient.makeSession(protocolClasses: [StubURLProtocol.self])
        )
        return DeleteAccountViewModel(users: UserRepository(api: api))
    }

    override func tearDown() async throws {
        StubURLProtocol.handler = nil
    }

    func testOnlyTheWordDeleteUnlocksTheButton() {
        let model = makeModel()
        XCTAssertFalse(model.canDelete)

        for text in ["", "del", "delete me", "remove"] {
            model.typedText = text
            XCTAssertFalse(model.canDelete, text)
        }
        for text in ["delete", "Delete", "DELETE", "  delete \n"] {
            model.typedText = text
            XCTAssertTrue(model.canDelete, text)
        }
    }

    func testDeletingCallsTheServerAndReportsSuccess() async {
        var request: URLRequest?
        StubURLProtocol.handler = { received in
            request = received
            return (204, Data())
        }
        let model = makeModel()
        model.typedText = "delete"

        let deleted = await model.delete()

        XCTAssertTrue(deleted)
        XCTAssertEqual(request?.httpMethod, "DELETE")
        XCTAssertEqual(request?.url?.path, "/users/me")
        XCTAssertNil(model.errorMessage)
    }

    func testNothingIsSentUntilTheWordIsTyped() async {
        var wasCalled = false
        StubURLProtocol.handler = { _ in
            wasCalled = true
            return (204, Data())
        }
        let model = makeModel()
        model.typedText = "dele"

        let deleted = await model.delete()

        XCTAssertFalse(deleted)
        XCTAssertFalse(wasCalled)
    }

    func testAFailureSaysSoAndLeavesTheAccountAlone() async {
        StubURLProtocol.handler = { _ in (500, Data(#"{"status":500,"error":"Oops","message":null}"#.utf8)) }
        let model = makeModel()
        model.typedText = "delete"

        let deleted = await model.delete()

        XCTAssertFalse(deleted)
        XCTAssertEqual(model.errorMessage, "Couldn't delete your account")
        XCTAssertFalse(model.isDeleting)

        model.clearError()
        XCTAssertNil(model.errorMessage)
    }
}
