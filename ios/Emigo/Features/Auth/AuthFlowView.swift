import SwiftUI

/// The whole signed-out experience: a welcome screen, then pushed steps for signing in, resetting
/// a password, creating an account and confirming an email. Uses a native `NavigationStack`, so
/// the swipe-back gesture and back button behave exactly as on any iPhone app.
struct AuthFlowView: View {
    @State private var model: AuthFlowViewModel
    private let onStartOver: () -> Void

    init(environment: AppEnvironment, session: AppSession) {
        _model = State(initialValue: AuthFlowViewModel(
            auth: environment.auth,
            pending: session.pendingVerification,
            onAuthenticated: { session.didAuthenticate() }
        ))
        onStartOver = { session.signOut() }
    }

    var body: some View {
        NavigationStack(path: $model.path) {
            WelcomeView(model: model)
                .navigationDestination(for: AuthStep.self) { step in
                    destination(for: step)
                }
        }
        .tint(EmigoTheme.onboarding.colors.cream)
        // Sign-in always looks the same, whichever theme someone chose before signing out.
        .environment(\.theme, .onboarding)
    }

    @ViewBuilder
    private func destination(for step: AuthStep) -> some View {
        switch step {
        case .login: LoginView(model: model)
        case .forgotPassword: ForgotPasswordView(model: model)
        case .registerEmail: RegisterEmailView(model: model)
        case .registerPassword: RegisterPasswordView(model: model)
        case .registerName: RegisterNameView(model: model)
        case .registerUsername: RegisterUsernameView(model: model)
        case .verifyEmail: VerifyEmailView(model: model, onStartOver: onStartOver)
        case .registerInvite: InviteFriendsView(model: model)
        }
    }
}
