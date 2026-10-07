import Foundation

extension AppEnvironment {
    /// Picks the environment for this launch. Debug builds started with the `-EmigoDemo` argument
    /// run on built-in sample data with no account and no network, for looking at screens and for
    /// screenshots. Release builds can never take that path.
    static func make() -> AppEnvironment {
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("-EmigoDemo") { return .demo() }
        #endif
        return .live()
    }
}
