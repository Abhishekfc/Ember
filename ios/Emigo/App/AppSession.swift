import Foundation
import Observation

/// Whether someone is signed in, and what happens when that changes. The one object that decides
/// which half of the app is on screen (sign-in flow or the main app).
@MainActor
@Observable
final class AppSession {
    enum Phase: Equatable {
        case signedOut
        case signedIn
    }

    private(set) var phase: Phase
    /// Changes on every sign-out so the sign-in flow starts from a clean slate instead of
    /// remembering the previous person's half-typed details.
    private(set) var authFlowID = UUID()
    /// Set when the app should open on the "check your inbox" screen.
    private(set) var pendingVerification: PendingVerification?

    private let environment: AppEnvironment

    init(environment: AppEnvironment) {
        self.environment = environment
        // The first frame is decided without the network. A saved account that still has to
        // verify its email opens on that screen; any other saved account opens the app and is
        // re-checked in `start()`.
        let identity = environment.identity
        let pending = environment.sessionStore.pendingVerification
        if let pending, pending.firebaseUid == identity.uid {
            phase = .signedOut
            pendingVerification = pending
        } else {
            phase = identity.hasSession ? .signedIn : .signedOut
            pendingVerification = nil
        }
        environment.api.onSessionExpired = { [weak self] in self?.signOut() }
    }

    /// Re-checks a saved session in the background. Only an explicit "must verify your email"
    /// moves anyone off the app: failing to reach the server, which is usually just being offline,
    /// never signs anyone out.
    func start() async {
        guard phase == .signedIn else { return }
        guard let outcome = try? await environment.auth.resumeSession() else { return }
        if case .needsVerification(let email, let deadline) = outcome, let uid = environment.identity.uid {
            pendingVerification = PendingVerification(firebaseUid: uid, email: email, deadline: deadline)
            authFlowID = UUID()
            phase = .signedOut
        }
    }

    /// Called by the sign-in flow when the server has accepted the account.
    func didAuthenticate() {
        pendingVerification = nil
        phase = .signedIn
    }

    func signOut() {
        guard phase == .signedIn || pendingVerification != nil || environment.identity.hasSession else { return }
        environment.auth.signOut()
        environment.outbox.clear()
        environment.themes.reset()
        environment.subscription.reset()
        // What was remembered belongs to the account that just left.
        environment.photos.clearCache()
        environment.friends.clearCache()
        environment.activity.clearCache()
        environment.users.clearCache()
        pendingVerification = nil
        authFlowID = UUID()
        phase = .signedOut
    }
}
