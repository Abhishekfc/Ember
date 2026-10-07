import Foundation

/// What signing in needs the caller to do next.
///
/// `needsProfile` is a Firebase identity with no Emigo profile. The sign-in screen treats it as a
/// plain failed sign-in, worded like a wrong password, so signing in never quietly becomes signing
/// up and the wording can't be used to find out which addresses are registered.
///
/// `needsVerification` is a completed profile whose account must verify its email and hasn't yet.
/// It is a successful sign-in that isn't allowed into the app yet. `verifyBy` is the deadline the
/// server enforces; for an older account it may already be past.
enum SignInOutcome: Equatable {
    case signedIn
    case needsProfile(suggestedDisplayName: String)
    case needsVerification(email: String, verifyBy: Date)
}

/// How long a new account has to confirm its email. Must match the backend's
/// `EmailVerificationExpiryService`; this only drives the countdown, the server is what enforces it.
let emailVerificationGracePeriod: TimeInterval = 10 * 60

/// True when the server says this profile still needs email verification. Only the server knows
/// the deadline, so it alone decides.
func needsEmailVerification(_ profile: UserProfile) -> Bool {
    profile.emailVerificationRequired
}

/// The verification deadline, counted from when the account was created, so reopening the screen
/// or the app never restarts the countdown. Falls back to a fresh window from `now` when the
/// profile has no creation date.
func verifyByDeadline(for profile: UserProfile, now: Date = Date()) -> Date {
    (profile.createdAt ?? now).addingTimeInterval(emailVerificationGracePeriod)
}

/// Sign-up, sign-in and session resume: Firebase proves who someone is, the Emigo backend holds
/// their profile. Port of Android's `AuthRepository`.
@MainActor
final class AuthRepository {
    private let api: APIClient
    private let identity: IdentityProvider
    private let store: SessionStore

    init(api: APIClient, identity: IdentityProvider, store: SessionStore) {
        self.api = api
        self.identity = identity
        self.store = store
    }

    // MARK: - Checks while signing up (no account or token exists yet)

    func isEmailAvailable(_ email: String) async throws -> Bool {
        do {
            return try await api.send(.emailAvailabilityPublic(email)).available
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw UserFacingError(Strings.Failure.checkEmail)
        }
    }

    func usernameAvailability(_ username: String) async throws -> UsernameAvailability {
        do {
            return try await api.send(.usernameAvailabilityPublic(username))
        } catch is CancellationError {
            throw CancellationError()
        } catch {
            throw UserFacingError(Strings.Failure.checkUsername)
        }
    }

    // MARK: - Sign up

    /// Creates the Firebase identity, then the Emigo profile on top of it. Safe to call again if
    /// the second step fails: an identity already signed in for the same address is reused rather
    /// than created twice, which would fail as a duplicate.
    func signUp(email: String, password: String, displayName: String, username: String) async throws -> UserProfile {
        let trimmedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        // Reuse a signed-in identity only if it is for this same address. After a sign-in that
        // landed on "no profile yet", backing out to type a different email would otherwise attach
        // the new profile to the wrong account.
        if identity.hasSession, identity.email?.lowercased() != trimmedEmail.lowercased() {
            try? identity.signOut()
        }
        if !identity.hasSession {
            try await identity.createAccount(email: trimmedEmail, password: password)
        }
        // Fire and forget: failing to send must never fail sign-up. Verification is still enforced
        // by the server, and the verification screen can resend.
        try? await identity.sendEmailVerification()
        return try await completeProfile(displayName: displayName, username: username)
    }

    /// The backend call every sign-up ends with, once Firebase has a signed-in identity.
    func completeProfile(displayName: String, username: String) async throws -> UserProfile {
        do {
            let profile = try await api.send(.completeProfile(displayName: displayName, username: username))
            store.displayName = profile.displayName
            return profile
        } catch APIError.server(let status, let message) {
            let fallback = String(localized: Strings.Failure.createAccount) + " (\(status))"
            throw UserFacingError(message ?? fallback)
        }
    }

    // MARK: - Sign in and resume

    /// `identifier` is usually an email, but Firebase has no usernames, so anything without an "@"
    /// is resolved to its email by the backend first. An unknown username fails exactly like a
    /// wrong password so usernames can't be probed.
    func signIn(identifier: String, password: String) async throws -> SignInOutcome {
        let trimmed = identifier.trimmingCharacters(in: .whitespacesAndNewlines)
        let email: String
        if trimmed.contains("@") {
            email = trimmed
        } else {
            let lookup: UsernameLoginLookup
            do {
                lookup = try await api.send(.resolveUsernameForLogin(trimmed))
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                throw UserFacingError(Strings.Failure.checkUsername)
            }
            guard let found = lookup.email else { throw UserFacingError(Strings.Login.errorBadCredentials) }
            email = found
        }
        try await identity.signIn(email: email, password: password)
        return try await checkExistingProfile()
    }

    /// The third way into the app besides sign-up and sign-in: a session Firebase already has on
    /// disk, with no sign-in screen. Reloads and force-refreshes first, because the cached
    /// "verified" flag and token date from before the app was closed, and someone who clicked the
    /// link meanwhile would otherwise read as unverified.
    ///
    /// Callers act only on an explicit `.needsVerification`. A thrown error is usually just being
    /// offline and must never sign anyone out.
    func resumeSession() async throws -> SignInOutcome {
        guard identity.hasSession else { throw UserFacingError(Strings.Failure.sessionExpired) }
        try? await identity.reload()
        try? await identity.refreshIDToken()
        return try await checkExistingProfile()
    }

    /// Firebase has already said who this is; what's left is whether an Emigo profile exists and
    /// whether it may pass the other endpoints yet. Checking here avoids a brief flash into the app
    /// before bouncing back to the verification screen.
    private func checkExistingProfile() async throws -> SignInOutcome {
        do {
            let profile = try await api.send(.myProfile())
            if needsEmailVerification(profile) {
                let deadline = verifyByDeadline(for: profile)
                if let uid = identity.uid {
                    store.savePendingVerification(PendingVerification(firebaseUid: uid, email: profile.email, deadline: deadline))
                }
                return .needsVerification(email: profile.email, verifyBy: deadline)
            }
            store.displayName = profile.displayName
            store.clearPendingVerification()
            return .signedIn
        } catch APIError.unauthorized {
            return .needsProfile(suggestedDisplayName: identity.displayName ?? "")
        }
    }

    // MARK: - Email verification

    func rememberPendingVerification(email: String, deadline: Date) {
        guard let uid = identity.uid else { return }
        store.savePendingVerification(PendingVerification(firebaseUid: uid, email: email, deadline: deadline))
    }

    func forgetPendingVerification() {
        store.clearPendingVerification()
    }

    func resendVerificationEmail() async throws {
        try await identity.sendEmailVerification()
    }

    /// Re-reads the account and says whether Firebase now considers the email confirmed.
    func refreshEmailVerified() async -> Bool {
        try? await identity.reload()
        guard identity.isEmailVerified else { return false }
        try? await identity.refreshIDToken()
        return true
    }

    // MARK: - Password reset

    /// Firebase sends the reset email and hosts the reset page; the Emigo backend isn't involved.
    func sendPasswordReset(to email: String) async throws {
        try await identity.sendPasswordReset(to: email)
    }

    // MARK: - Sign out

    /// Ends the Firebase session and forgets local account details.
    func signOut() {
        try? identity.signOut()
        store.clear()
    }
}
