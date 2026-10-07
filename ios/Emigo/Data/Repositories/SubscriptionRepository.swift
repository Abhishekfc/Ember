import Foundation
import Observation

/// Whether the account has Emigo Gold. The subscription itself is bought and managed elsewhere
/// (Google Play on Android); the iPhone app only reads the result from the server.
@MainActor
@Observable
final class SubscriptionRepository {
    @ObservationIgnored private let api: APIClient
    @ObservationIgnored private let store: SessionStore

    /// The answer from the last check, kept up to date so screens that show it change by themselves.
    private(set) var isGoldMember: Bool

    init(api: APIClient, store: SessionStore) {
        self.api = api
        self.store = store
        isGoldMember = store.isGoldMember
    }

    /// The answer from the last check, available immediately.
    var isGoldMemberCached: Bool { isGoldMember }

    /// When the server last answered, so a quick return to the app doesn't ask again.
    @ObservationIgnored private var lastCheckedAt: Date?

    /// Asks the server, and remembers the answer. If the server can't be reached the last known
    /// answer stands, so a flaky connection never takes Gold features away. Skips the question
    /// if it was answered within the cache window, unless `force` is set.
    @discardableResult
    func refreshIsGoldMember(force: Bool = false) async -> Bool {
        if !force, let lastCheckedAt, Date().timeIntervalSince(lastCheckedAt) < listCacheTTL {
            return isGoldMember
        }
        if let status = try? await api.send(.subscriptionStatus()) {
            store.isGoldMember = status.isActive
            isGoldMember = status.isActive
            lastCheckedAt = Date()
        }
        return isGoldMember
    }

    /// Forgets the answer, for when someone signs out.
    func reset() {
        isGoldMember = false
        lastCheckedAt = nil
    }
}
