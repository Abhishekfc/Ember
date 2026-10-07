import Foundation

/// The screens that open on top of the tabs, hiding the dock until you come back.
enum MainRoute: Hashable {
    case profile
    case activity
    case findPeople
    case blockedAccounts
    case gold
    case appearance
    case otherSettings
    case recipientPicker
    case sentPhotos
    case friendProfile(ProfileSubject)

    /// Debug builds can open straight onto one of these (launch argument `-EmigoRoute activity`),
    /// for repeatable screenshots. Always nil in Release builds.
    static var launchOverride: MainRoute? {
        #if DEBUG
        switch UserDefaults.standard.string(forKey: "EmigoRoute") {
        case "profile": return .profile
        case "activity": return .activity
        case "findPeople": return .findPeople
        case "blocked": return .blockedAccounts
        case "gold": return .gold
        case "appearance": return .appearance
        case "other": return .otherSettings
        case "recipients": return .recipientPicker
        case "sent": return .sentPhotos
        default: return nil
        }
        #else
        return nil
        #endif
    }
}
