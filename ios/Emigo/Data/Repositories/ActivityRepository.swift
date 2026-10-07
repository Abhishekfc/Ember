import Foundation

/// The activity feed behind the bell on Home.
@MainActor
final class ActivityRepository {
    private let api: APIClient

    /// Each page of activity is remembered separately, for the cache window. Nothing the app does
    /// changes it by itself, so only the time limit, pull-to-refresh and sign-out clear it.
    private struct PageKey: Hashable {
        let offset: Int
        let limit: Int
    }

    private let activityCache = TtlCache<PageKey, Page<ActivityEvent>>()
    private let activityFlight = SingleFlight<String, Page<ActivityEvent>>()

    init(api: APIClient) {
        self.api = api
    }

    /// Forgets the remembered activity. Called on sign-out.
    func clearCache() {
        activityCache.removeAll()
    }

    func events(forceRefresh: Bool = false, offset: Int = 0, limit: Int = 30) async throws -> Page<ActivityEvent> {
        let key = PageKey(offset: offset, limit: limit)
        if !forceRefresh, let cached = activityCache.value(for: key) { return cached }
        let page = try await activityFlight.run("\(forceRefresh ? "refresh" : "read")-\(offset)-\(limit)") { [api] in
            try await api.send(.activity(refresh: forceRefresh, offset: offset, limit: limit))
        }
        activityCache.store(page, for: key)
        return page
    }

    /// When the person last opened Activity; anything newer counts as new.
    func lastSeenAt() async throws -> Date? {
        try await api.send(.activityLastSeen()).lastSeenAt
    }

    func markSeen() async {
        _ = try? await api.send(.markActivitySeen())
    }
}
