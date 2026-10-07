import Foundation
import Observation

/// Which theme the whole app is wearing. The root of the app reads `activeTheme` and hands it to
/// every screen, so changing it here repaints everything at once.
///
/// A theme can be *applied* (chosen and remembered) or just *previewed* (worn while someone looks
/// at the Appearance screen, forgotten when they leave). Gold-only themes can be previewed by
/// anyone but only applied by Gold members; if a saved Gold theme meets an account that isn't Gold
/// (the subscription ended) the app quietly wears the default theme instead.
@MainActor
@Observable
final class ThemeStore {
    @ObservationIgnored private let store: SessionStore
    @ObservationIgnored private let subscription: SubscriptionRepository

    private var chosenKey: ThemeKey
    private var previewKey: ThemeKey?

    init(store: SessionStore, subscription: SubscriptionRepository) {
        self.store = store
        self.subscription = subscription
        chosenKey = store.selectedTheme.flatMap(ThemeKey.init(rawValue:)) ?? .defaultKey
    }

    /// The theme the app is wearing right now: the preview if there is one, else the applied theme.
    var activeTheme: EmigoTheme { .theme(for: previewKey ?? appliedKey) }

    /// The theme the person has applied, as far as they are allowed to have it.
    var appliedKey: ThemeKey {
        chosenKey.isLocked && !subscription.isGoldMember ? .defaultKey : chosenKey
    }

    /// Whether `key` is one this account can apply.
    func canApply(_ key: ThemeKey) -> Bool {
        !key.isLocked || subscription.isGoldMember
    }

    /// Wears `key` until `endPreview()`, without remembering it.
    func preview(_ key: ThemeKey) {
        previewKey = key == appliedKey ? nil : key
    }

    func endPreview() {
        previewKey = nil
    }

    /// Chooses `key` for good. Does nothing for a Gold theme on an account that isn't Gold.
    func apply(_ key: ThemeKey) {
        guard canApply(key) else { return }
        chosenKey = key
        previewKey = nil
        store.selectedTheme = key.rawValue
    }

    /// Back to the default, for when someone signs out. (The saved choice is cleared with the session.)
    func reset() {
        chosenKey = .defaultKey
        previewKey = nil
    }
}
