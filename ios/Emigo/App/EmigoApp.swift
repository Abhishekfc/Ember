import SwiftUI

@main
struct EmigoApp: App {
    @State private var session: AppSession
    private let environment: AppEnvironment

    init() {
        FontRegistry.registerBundledFonts()
        let environment = AppEnvironment.make()
        self.environment = environment
        _session = State(initialValue: AppSession(environment: environment))
    }

    var body: some Scene {
        WindowGroup {
            RootView(session: session, environment: environment)
                .environment(\.theme, environment.themes.activeTheme)
                .environment(\.imageLoader, environment.imageLoader)
                .preferredColorScheme(.dark)
                // Text follows the iPhone's text-size setting up to the standard size, no further,
                // so it stays the same size as the Android app and the layouts never overflow.
                .dynamicTypeSize(...DynamicTypeSize.large)
                .task { await session.start() }
        }
    }
}
