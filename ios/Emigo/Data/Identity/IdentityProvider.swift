import Foundation

/// Why a sign-in, sign-up or email request failed, in terms the UI can show. The wording is the
/// same as Android's `firebaseErrorMessage`.
enum IdentityError: LocalizedError, Equatable {
    case notConfigured
    case noAccount
    case emailInUse
    case weakPassword
    case invalidCredentials
    case recentLoginRequired
    case tooManyRequests
    case network
    /// Anything not specifically recognised; shown as a plain "something went wrong".
    case unknown

    var errorDescription: String? {
        switch self {
        case .notConfigured: String(localized: Strings.Failure.notConfigured)
        case .noAccount: String(localized: Strings.Identity.noAccount)
        case .emailInUse: String(localized: Strings.Identity.emailInUse)
        case .weakPassword: String(localized: Strings.Identity.weakPassword)
        case .invalidCredentials: String(localized: Strings.Identity.badCredentials)
        case .recentLoginRequired: String(localized: Strings.Identity.recentLoginRequired)
        case .tooManyRequests: String(localized: Strings.Identity.tooManyRequests)
        case .network: String(localized: Strings.Failure.noConnection)
        case .unknown: String(localized: Strings.Failure.somethingWentWrong)
        }
    }
}

/// Who is signed in, and the operations that change that. The app talks to this protocol, not to
/// Firebase directly, so screens and repositories can be tested and demoed without a real account.
@MainActor
protocol IdentityProvider: AccessTokenProviding {
    /// True when a signed-in identity is saved on this device, even if it hasn't been re-checked
    /// with the server yet. Used to paint the first frame without waiting for the network.
    var hasSession: Bool { get }
    var uid: String? { get }
    var email: String? { get }
    var displayName: String? { get }
    var isEmailVerified: Bool { get }

    func signIn(email: String, password: String) async throws
    func createAccount(email: String, password: String) async throws
    func signOut() throws
    func sendEmailVerification() async throws
    func sendPasswordReset(to email: String) async throws
    /// Re-checks the current password, then sets a new one.
    func changePassword(current: String, new: String) async throws
    /// Re-reads the account from Firebase, so an email verified in a browser is noticed.
    func reload() async throws
    func refreshIDToken() async throws
}
