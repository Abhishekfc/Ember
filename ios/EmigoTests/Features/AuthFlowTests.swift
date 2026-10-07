import XCTest
@testable import Emigo

@MainActor
private final class FakeIdentity: IdentityProvider {
    var hasSession = false
    var uid: String? = "uid-1"
    var email: String?
    var displayName: String?
    var isEmailVerified = false

    func idToken() async -> String? { "token" }
    func signIn(email: String, password: String) async throws { hasSession = true }
    func createAccount(email: String, password: String) async throws {
        hasSession = true
        self.email = email
    }
    func signOut() throws { hasSession = false }
    func sendEmailVerification() async throws {}
    func sendPasswordReset(to email: String) async throws {}
    func changePassword(current: String, new: String) async throws {}
    func reload() async throws {}
    func refreshIDToken() async throws {}
}

@MainActor
final class AuthFlowTests: XCTestCase {
    private var authenticatedCount = 0

    override func tearDown() async throws {
        StubURLProtocol.handler = nil
    }

    private func makeModel(verificationRequired: Bool) -> AuthFlowViewModel {
        StubURLProtocol.handler = { request in
            switch request.url?.path {
            case "/auth/username-availability":
                return (200, Data(#"{"available":true,"suggestions":[]}"#.utf8))
            case "/auth/complete-profile":
                let json = #"{"userId":"u1","displayName":"Ann Lee","username":"ann","email":"ann@example.com","profilePhotoUrl":null,"createdAt":"2026-10-06T10:00:00Z","emailVerificationRequired":\#(verificationRequired)}"#
                return (200, Data(json.utf8))
            default:
                return (404, Data(#"{"status":404,"error":"Not Found","message":null}"#.utf8))
            }
        }
        let identity = FakeIdentity()
        let api = APIClient(
            baseURL: URL(string: "https://api.example/")!,
            tokenProvider: identity,
            session: APIClient.makeSession(protocolClasses: [StubURLProtocol.self])
        )
        let store = SessionStore(defaults: UserDefaults(suiteName: "AuthFlowTests-\(UUID().uuidString)")!)
        let auth = AuthRepository(api: api, identity: identity, store: store)
        authenticatedCount = 0
        return AuthFlowViewModel(auth: auth, pending: nil, onAuthenticated: { [unowned self] in authenticatedCount += 1 })
    }

    private func waitUntil(_ condition: () -> Bool) async {
        for _ in 0..<100 {
            if condition() { return }
            try? await Task.sleep(for: .milliseconds(50))
        }
    }

    /// Fills in the four sign-up questions and presses Create account.
    private func signUp(_ model: AuthFlowViewModel) async {
        model.email = "ann@example.com"
        model.password = "longenough"
        model.firstName = "Ann"
        model.lastName = "Lee"
        model.usernameDraft = "ann"
        model.usernameEdited()
        await waitUntil { model.usernameCheck == .available }
        model.submitUsername()
    }

    func testANewAccountIsOfferedTheInviteStepBeforeTheAppOpens() async {
        let model = makeModel(verificationRequired: false)

        await signUp(model)
        await waitUntil { model.path.last == .registerInvite }

        XCTAssertEqual(model.path.last, .registerInvite)
        XCTAssertEqual(authenticatedCount, 0, "the app only opens once the invite step is finished or skipped")

        model.finishOnboarding()
        XCTAssertEqual(authenticatedCount, 1)
    }

    func testAnAccountThatMustVerifyItsEmailStopsOnThatScreenFirst() async {
        let model = makeModel(verificationRequired: true)

        await signUp(model)
        await waitUntil { model.path.last == .verifyEmail }

        XCTAssertEqual(model.path.last, .verifyEmail)
        XCTAssertEqual(model.verificationEmail, "ann@example.com")
        XCTAssertEqual(authenticatedCount, 0)
    }

    func testTheRegistrationBarsCountFourSteps() {
        XCTAssertEqual(AuthProgress.registration(3).step, 3)
        XCTAssertEqual(AuthProgress.registration(3).of, 4)
    }
}
