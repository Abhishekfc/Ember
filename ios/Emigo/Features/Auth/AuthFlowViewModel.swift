import Foundation
import Observation

/// The screens of the sign-in and sign-up flow, in the order they are pushed.
enum AuthStep: Hashable {
    case login
    case forgotPassword
    case registerEmail
    case registerPassword
    case registerName
    case registerUsername
    case verifyEmail
    /// The last step of a new account: invite a first friend.
    case registerInvite
}

enum UsernameCheck: Equatable {
    case idle
    case checking
    case available
    case taken(suggestions: [String])
}

let minimumPasswordLength = 8
let minimumUsernameLength = 3
private let maximumUsernameLength = 30
private let usernameDebounce: Duration = .milliseconds(400)
/// How long after an account is created before the verification email can be sent again.
private let resendCooldown: TimeInterval = 3 * 60

/// Drives every screen of the sign-in flow. Port of Android's `LoginViewModel`: the same checks,
/// in the same order, with the same wording.
@MainActor
@Observable
final class AuthFlowViewModel {
    var path: [AuthStep]

    // Sign in
    var loginIdentifier = ""
    var password = ""

    // Sign up
    var email = ""
    var firstName = ""
    var lastName = ""
    var usernameDraft = ""
    private(set) var usernameCheck: UsernameCheck = .idle

    // Forgot password
    var forgotPasswordEmail = ""
    private(set) var isSendingReset = false
    private(set) var passwordResetSent = false

    // Email verification
    private(set) var verificationEmail: String
    private(set) var verificationDeadline: Date
    private(set) var isResendingVerification = false
    private(set) var isCheckingVerification = false
    private(set) var verificationResendMessage: String?
    private(set) var verificationCheckError: String?

    // Shared
    private(set) var isLoading = false
    private(set) var errorMessage: String?

    /// True once the Firebase and Emigo accounts exist, so going back and pressing Create again
    /// never tries to create them twice.
    private var accountCreated = false
    private var usernameTask: Task<Void, Never>?

    private let auth: AuthRepository
    private let onAuthenticated: () -> Void

    init(auth: AuthRepository, pending: PendingVerification?, onAuthenticated: @escaping () -> Void) {
        self.auth = auth
        self.onAuthenticated = onAuthenticated
        verificationEmail = pending?.email ?? ""
        verificationDeadline = pending?.deadline ?? .distantPast
        path = pending == nil ? [] : [.verifyEmail]
    }

    // MARK: - Validation

    var isLoginEmailValid: Bool { EmailValidator.isValid(loginIdentifier) }
    var isEmailValid: Bool { EmailValidator.isValid(email) }
    var isPasswordValid: Bool { password.count >= minimumPasswordLength }
    var isNameValid: Bool { !firstName.trimmingCharacters(in: .whitespaces).isEmpty }
    var isForgotPasswordEmailValid: Bool { EmailValidator.isValid(forgotPasswordEmail) }
    var canCreateAccount: Bool { usernameCheck == .available && usernameDraft.count >= minimumUsernameLength }

    // MARK: - Navigation

    func push(_ step: AuthStep) {
        errorMessage = nil
        path.append(step)
    }

    func startRegistration() {
        password = ""
        push(.registerEmail)
    }

    func startSignIn() {
        password = ""
        push(.login)
    }

    func showForgotPassword() {
        forgotPasswordEmail = loginIdentifier
        passwordResetSent = false
        push(.forgotPassword)
    }

    /// Any edit clears a stale error, so a message never lingers after the person has fixed the problem.
    func fieldEdited() {
        errorMessage = nil
    }

    // MARK: - Sign in

    func submitLogin() {
        guard !isLoading else { return }
        guard !loginIdentifier.trimmingCharacters(in: .whitespaces).isEmpty, !password.isEmpty else {
            errorMessage = String(localized: Strings.Login.errorFillAll)
            return
        }
        if loginIdentifier.contains("@"), !isLoginEmailValid {
            errorMessage = String(localized: Strings.Login.errorInvalidEmail)
            return
        }
        Task {
            isLoading = true
            errorMessage = nil
            defer { isLoading = false }
            do {
                handle(try await auth.signIn(identifier: loginIdentifier, password: password))
            } catch is CancellationError {
                return
            } catch {
                Haptics.error()
                errorMessage = error.userMessage
            }
        }
    }

    private func handle(_ outcome: SignInOutcome) {
        password = ""
        switch outcome {
        case .signedIn:
            Haptics.success()
            onAuthenticated()
        case .needsProfile:
            // A Firebase identity with no Emigo profile reads exactly like a wrong password.
            auth.signOut()
            Haptics.error()
            errorMessage = String(localized: Strings.Login.errorBadCredentials)
        case .needsVerification(let email, let deadline):
            verificationEmail = email
            verificationDeadline = deadline
            push(.verifyEmail)
        }
    }

    // MARK: - Forgot password

    func sendPasswordReset() {
        guard isForgotPasswordEmailValid, !isSendingReset else { return }
        Task {
            isSendingReset = true
            defer { isSendingReset = false }
            // Always reported as sent, whether or not the address has an account, so the screen
            // can't be used to find out who is registered.
            try? await auth.sendPasswordReset(to: forgotPasswordEmail.trimmingCharacters(in: .whitespaces))
            passwordResetSent = true
        }
    }

    // MARK: - Sign up

    func submitEmail() {
        guard isEmailValid, !isLoading else { return }
        Task {
            isLoading = true
            errorMessage = nil
            defer { isLoading = false }
            do {
                if try await auth.isEmailAvailable(email.trimmingCharacters(in: .whitespaces)) {
                    push(.registerPassword)
                } else {
                    Haptics.error()
                    errorMessage = String(localized: Strings.Login.errorEmailTaken)
                }
            } catch is CancellationError {
                return
            } catch {
                // Being unable to check isn't a reason to block sign-up; the final step catches duplicates.
                push(.registerPassword)
            }
        }
    }

    func submitPassword() {
        guard isEmailValid, isPasswordValid else {
            errorMessage = String(localized: Strings.Login.errorCheckDetails)
            return
        }
        push(.registerName)
    }

    func submitName() {
        guard isNameValid else {
            errorMessage = String(localized: Strings.Login.errorFirstName)
            return
        }
        push(.registerUsername)
    }

    /// Keeps only what a username may contain and checks availability once typing pauses.
    func usernameEdited() {
        let filtered = String(
            usernameDraft
                .filter { $0.isLetter || $0.isNumber || $0 == "_" || $0 == "." }
                .lowercased()
                .prefix(maximumUsernameLength)
        )
        if filtered != usernameDraft { usernameDraft = filtered }
        errorMessage = nil
        usernameTask?.cancel()

        guard filtered.count >= minimumUsernameLength else {
            usernameCheck = .idle
            return
        }
        usernameTask = Task {
            usernameCheck = .checking
            try? await Task.sleep(for: usernameDebounce)
            guard !Task.isCancelled else { return }
            do {
                let result = try await auth.usernameAvailability(filtered)
                guard !Task.isCancelled else { return }
                usernameCheck = result.available ? .available : .taken(suggestions: result.suggestions)
            } catch {
                if !Task.isCancelled { usernameCheck = .idle }
            }
        }
    }

    func pickSuggestion(_ name: String) {
        usernameDraft = name
        usernameEdited()
    }

    func submitUsername() {
        if accountCreated {
            // The accounts already exist (this is a retry after a later step failed).
            finishSignUp()
            return
        }
        guard !isLoading else { return }
        guard usernameDraft.count >= minimumUsernameLength else {
            errorMessage = String(localized: Strings.Register.usernameTooShort)
            return
        }
        guard usernameCheck == .available else {
            errorMessage = String(localized: Strings.Register.usernamePickAvailable)
            return
        }
        Task {
            isLoading = true
            errorMessage = nil
            defer { isLoading = false }
            let displayName = "\(firstName.trimmingCharacters(in: .whitespaces)) \(lastName.trimmingCharacters(in: .whitespaces))"
                .trimmingCharacters(in: .whitespaces)
            do {
                let profile = try await auth.signUp(email: email, password: password, displayName: displayName, username: usernameDraft)
                accountCreated = true
                password = ""
                if needsEmailVerification(profile) {
                    verificationEmail = profile.email
                    verificationDeadline = verifyByDeadline(for: profile)
                    auth.rememberPendingVerification(email: verificationEmail, deadline: verificationDeadline)
                    push(.verifyEmail)
                } else {
                    finishSignUp()
                }
            } catch is CancellationError {
                return
            } catch {
                Haptics.error()
                errorMessage = error.userMessage
            }
        }
    }

    /// The account is ready (and its email confirmed, if that was needed). Before the app opens,
    /// a new account is offered one last step: inviting a first friend.
    private func finishSignUp() {
        // The email screen underneath can fire this again when the app comes to the front.
        guard path.last != .registerInvite else { return }
        Haptics.success()
        push(.registerInvite)
    }

    /// Leaves the sign-up flow and opens the app. The invite step's way out, whether or not anyone
    /// was invited.
    func finishOnboarding() {
        onAuthenticated()
    }

    // MARK: - Email verification

    var verificationCreatedAt: Date {
        verificationDeadline.addingTimeInterval(-emailVerificationGracePeriod)
    }

    /// When the verification email can be sent again.
    var resendAvailableAt: Date {
        verificationCreatedAt.addingTimeInterval(resendCooldown)
    }

    func resendVerificationEmail() {
        guard !isResendingVerification else { return }
        Task {
            isResendingVerification = true
            verificationResendMessage = nil
            defer { isResendingVerification = false }
            do {
                try await auth.resendVerificationEmail()
                verificationResendMessage = String(localized: Strings.Verify.resent)
            } catch {
                verificationResendMessage = (error as? IdentityError)?.errorDescription ?? String(localized: Strings.Verify.resendFailed)
            }
        }
    }

    func confirmEmailVerified() {
        guard !isCheckingVerification else { return }
        Task {
            isCheckingVerification = true
            verificationCheckError = nil
            defer { isCheckingVerification = false }
            guard await auth.refreshEmailVerified() else {
                verificationCheckError = String(localized: Strings.Verify.stillUnverified)
                return
            }
            let outcome = try? await auth.resumeSession()
            if outcome == .signedIn {
                auth.forgetPendingVerification()
                if accountCreated { finishSignUp() } else { onAuthenticated() }
            } else {
                if case .needsVerification(let email, let deadline) = outcome {
                    verificationEmail = email
                    verificationDeadline = deadline
                }
                verificationCheckError = String(localized: Strings.Verify.stillUnverified)
            }
        }
    }
}
