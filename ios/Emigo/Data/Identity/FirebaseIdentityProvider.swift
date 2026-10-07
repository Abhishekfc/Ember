import FirebaseAuth
import FirebaseCore
import Foundation
import OSLog

/// Starts Firebase if this build has its config file. The file (`GoogleService-Info.plist`) is
/// downloaded from the Firebase console for the iOS app and kept out of git, like Android's
/// `google-services.json`.
enum FirebaseBootstrap {
    private static let logger = Logger(subsystem: "com.emigo.app", category: "firebase")

    private static var isConfigured = false

    @discardableResult
    static func configureIfPossible() -> Bool {
        if isConfigured { return true }
        guard Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil else {
            logger.notice("GoogleService-Info.plist is missing; sign-in is disabled in this build")
            return false
        }
        FirebaseApp.configure()
        isConfigured = true
        return true
    }
}

/// `IdentityProvider` backed by Firebase Authentication (email and password).
@MainActor
final class FirebaseIdentityProvider: IdentityProvider {
    private var auth: Auth { Auth.auth() }

    var hasSession: Bool { auth.currentUser != nil }
    var uid: String? { auth.currentUser?.uid }
    var email: String? { auth.currentUser?.email }
    var displayName: String? { auth.currentUser?.displayName }
    var isEmailVerified: Bool { auth.currentUser?.isEmailVerified ?? false }

    func signIn(email: String, password: String) async throws {
        do { _ = try await auth.signIn(withEmail: email, password: password) } catch { throw Self.map(error) }
    }

    func createAccount(email: String, password: String) async throws {
        do { _ = try await auth.createUser(withEmail: email, password: password) } catch { throw Self.map(error) }
    }

    func signOut() throws {
        do { try auth.signOut() } catch { throw Self.map(error) }
    }

    func sendEmailVerification() async throws {
        guard let user = auth.currentUser else { throw IdentityError.unknown }
        do { try await user.sendEmailVerification() } catch { throw Self.map(error) }
    }

    func sendPasswordReset(to email: String) async throws {
        do { try await auth.sendPasswordReset(withEmail: email) } catch { throw Self.map(error) }
    }

    func changePassword(current: String, new: String) async throws {
        guard let user = auth.currentUser, let email = user.email else { throw IdentityError.unknown }
        do {
            try await user.reauthenticate(with: EmailAuthProvider.credential(withEmail: email, password: current))
            try await user.updatePassword(to: new)
        } catch {
            throw Self.map(error)
        }
    }

    func reload() async throws {
        guard let user = auth.currentUser else { return }
        do { try await user.reload() } catch { throw Self.map(error) }
    }

    func refreshIDToken() async throws {
        guard let user = auth.currentUser else { return }
        do { _ = try await user.getIDTokenResult(forcingRefresh: true) } catch { throw Self.map(error) }
    }

    /// Firebase caches the token and refreshes it itself shortly before it expires, so asking for
    /// it on every request is cheap. A token the server later rejects is caught by the 401 handling
    /// in `APIClient`.
    func idToken() async -> String? {
        guard let user = auth.currentUser else { return nil }
        return try? await user.getIDToken()
    }

    private static func map(_ error: Error) -> IdentityError {
        let nsError = error as NSError
        guard nsError.domain == AuthErrors.domain, let code = AuthErrorCode(rawValue: nsError.code) else {
            return (error as? URLError) != nil ? .network : .unknown
        }
        switch code {
        case .userNotFound, .userDisabled: return .noAccount
        case .emailAlreadyInUse: return .emailInUse
        case .weakPassword: return .weakPassword
        // Wrong password and malformed credential read the same to the user, and the SDK no
        // longer tells them apart.
        case .wrongPassword, .invalidCredential, .invalidEmail: return .invalidCredentials
        case .requiresRecentLogin: return .recentLoginRequired
        case .tooManyRequests: return .tooManyRequests
        case .networkError: return .network
        default: return .unknown
        }
    }
}

/// Stands in when the build has no Firebase config file. The app still launches and shows its
/// screens; any attempt to sign in explains why it can't.
@MainActor
final class UnconfiguredIdentityProvider: IdentityProvider {
    var hasSession: Bool { false }
    var uid: String? { nil }
    var email: String? { nil }
    var displayName: String? { nil }
    var isEmailVerified: Bool { false }

    func signIn(email: String, password: String) async throws { throw IdentityError.notConfigured }
    func createAccount(email: String, password: String) async throws { throw IdentityError.notConfigured }
    func signOut() throws {}
    func sendEmailVerification() async throws { throw IdentityError.notConfigured }
    func sendPasswordReset(to email: String) async throws { throw IdentityError.notConfigured }
    func changePassword(current: String, new: String) async throws { throw IdentityError.notConfigured }
    func reload() async throws {}
    func refreshIDToken() async throws {}
    func idToken() async -> String? { nil }
}
