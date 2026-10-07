import UserNotifications

/// Whether iOS lets Emigo show notifications. A protocol so the Settings switch can be tested
/// without the system prompt.
@MainActor
protocol NotificationAuthorizing {
    /// True if notifications are already allowed. Never shows the system prompt.
    func isAllowed() async -> Bool
    /// Shows the system prompt the first time. True if notifications end up allowed.
    func requestPermission() async -> Bool
}

struct SystemNotificationAuthorizer: NotificationAuthorizing {
    func isAllowed() async -> Bool {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        return Self.allows(settings.authorizationStatus)
    }

    func requestPermission() async -> Bool {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .notDetermined:
            return (try? await center.requestAuthorization(options: [.alert, .badge, .sound])) ?? false
        default:
            // Already decided: iOS never asks twice, so the answer is whatever was chosen before.
            return Self.allows(settings.authorizationStatus)
        }
    }

    private static func allows(_ status: UNAuthorizationStatus) -> Bool {
        switch status {
        case .authorized, .provisional, .ephemeral: true
        default: false
        }
    }
}
