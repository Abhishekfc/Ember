import SwiftUI

/// Chooses between the sign-in flow and the main app, and moves between them with a short fade.
struct RootView: View {
    let session: AppSession
    let environment: AppEnvironment

    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            theme.colors.background.ignoresSafeArea()

            switch session.phase {
            case .signedIn:
                MainView(environment: environment, session: session)
                    .id(session.authFlowID)
                    .transition(.opacity)
            case .signedOut:
                AuthFlowView(environment: environment, session: session)
                    .id(session.authFlowID)
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: session.phase)
    }
}
