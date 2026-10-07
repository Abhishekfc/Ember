import Foundation

/// A signed-up account that still has to confirm its email. Saved on the device so a cold start
/// can open straight on the "check your inbox" screen instead of flashing the app first.
struct PendingVerification: Codable, Equatable {
    let firebaseUid: String
    let email: String
    let deadline: Date
}

/// Small facts about the signed-in session that must survive a relaunch. Everything sensitive
/// (the sign-in itself) is held by Firebase in the Keychain; this only keeps display details.
final class SessionStore {
    private let defaults: UserDefaults

    private enum Key {
        static let displayName = "session.displayName"
        static let pendingVerification = "session.pendingVerification"
        static let isGoldMember = "session.isGoldMember"
        static let lastRecipientIds = "session.lastRecipientIds"
        static let selectedTheme = "session.selectedTheme"
        static let notificationsEnabled = "session.notificationsEnabled"
    }

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    /// Whether the account had an active Emigo Gold subscription the last time it was checked, so
    /// Gold-only controls are right from the first frame, before the network answers.
    var isGoldMember: Bool {
        get { defaults.bool(forKey: Key.isGoldMember) }
        set { defaults.set(newValue, forKey: Key.isGoldMember) }
    }

    /// Who the last photo was sent to; they're pre-selected next time when nobody is pinned.
    var lastRecipientIds: [String] {
        get { defaults.stringArray(forKey: Key.lastRecipientIds) ?? [] }
        set { defaults.set(newValue, forKey: Key.lastRecipientIds) }
    }

    /// The raw name of the theme this person applied, or nil if they never chose one.
    var selectedTheme: String? {
        get { defaults.string(forKey: Key.selectedTheme) }
        set { defaults.set(newValue, forKey: Key.selectedTheme) }
    }

    /// The Notifications switch in Settings. On until the person turns it off.
    var notificationsEnabled: Bool {
        get { defaults.object(forKey: Key.notificationsEnabled) as? Bool ?? true }
        set { defaults.set(newValue, forKey: Key.notificationsEnabled) }
    }

    var displayName: String? {
        get { defaults.string(forKey: Key.displayName) }
        set { defaults.set(newValue, forKey: Key.displayName) }
    }

    var pendingVerification: PendingVerification? {
        guard let data = defaults.data(forKey: Key.pendingVerification) else { return nil }
        return try? JSONDecoder().decode(PendingVerification.self, from: data)
    }

    func savePendingVerification(_ pending: PendingVerification) {
        defaults.set(try? JSONEncoder().encode(pending), forKey: Key.pendingVerification)
    }

    func clearPendingVerification() {
        defaults.removeObject(forKey: Key.pendingVerification)
    }

    /// Forgets everything. Called on sign-out so one account's details never reach the next.
    func clear() {
        defaults.removeObject(forKey: Key.displayName)
        defaults.removeObject(forKey: Key.isGoldMember)
        defaults.removeObject(forKey: Key.lastRecipientIds)
        defaults.removeObject(forKey: Key.selectedTheme)
        defaults.removeObject(forKey: Key.notificationsEnabled)
        clearPendingVerification()
    }
}
