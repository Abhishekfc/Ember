import Foundation
import Observation

/// The Notifications switch on the Settings page. It reads "on" only when the person wants
/// notifications and iOS allows them; turning it on asks iOS the first time, and if iOS has
/// already said no, explains where to change that.
@MainActor
@Observable
final class SettingsViewModel {
    private(set) var notificationsEnabled = false
    /// Set when the person turned notifications on but iOS won't allow them.
    var isShowingNotificationsDenied = false

    private let store: SessionStore
    private let authorizer: NotificationAuthorizing

    /// Pass `authorizer` only in tests; the app asks iOS itself.
    init(store: SessionStore, authorizer: NotificationAuthorizing? = nil) {
        self.store = store
        self.authorizer = authorizer ?? SystemNotificationAuthorizer()
    }

    /// Reads the current state. Called each time the page appears, since the person may have
    /// changed the permission in the iPhone's own Settings in the meantime.
    func refresh() async {
        let isAllowed = await authorizer.isAllowed()
        notificationsEnabled = store.notificationsEnabled && isAllowed
    }

    func setNotifications(_ enabled: Bool) async {
        guard enabled else {
            store.notificationsEnabled = false
            notificationsEnabled = false
            return
        }
        if await authorizer.requestPermission() {
            store.notificationsEnabled = true
            notificationsEnabled = true
        } else {
            notificationsEnabled = false
            isShowingNotificationsDenied = true
        }
    }
}
