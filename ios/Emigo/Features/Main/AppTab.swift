import Foundation

/// The five tabs, in the order they appear in the bar (same order as Android).
enum AppTab: Int, CaseIterable, Identifiable {
    case memories
    case home
    case camera
    case friends
    case settings

    var id: Int { rawValue }

    /// Debug builds can open on a chosen tab (launch argument `-EmigoInitialTab friends`), which
    /// makes screenshots of each screen repeatable. Always nil in Release builds.
    static var launchOverride: AppTab? {
        #if DEBUG
        guard let name = UserDefaults.standard.string(forKey: "EmigoInitialTab") else { return nil }
        return allCases.first { String(describing: $0) == name }
        #else
        return nil
        #endif
    }

    var title: LocalizedStringResource {
        switch self {
        case .memories: Strings.Tab.memories
        case .home: Strings.Tab.home
        case .camera: Strings.Tab.camera
        case .friends: Strings.Tab.friends
        case .settings: Strings.Tab.settings
        }
    }

    /// SF Symbol shown when the tab is not selected.
    var symbol: String {
        switch self {
        case .memories: "calendar"
        case .home: "square.stack.3d.up"
        case .camera: "camera"
        case .friends: "person.2"
        case .settings: "gearshape"
        }
    }

    /// SF Symbol shown when the tab is selected.
    var selectedSymbol: String {
        switch self {
        case .memories: "calendar"
        case .home: "square.stack.3d.up.fill"
        case .camera: "camera.fill"
        case .friends: "person.2.fill"
        case .settings: "gearshape.fill"
        }
    }
}
