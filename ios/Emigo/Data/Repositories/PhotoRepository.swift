import Foundation

/// Photos: the Home feed, Memories, and sending.
@MainActor
final class PhotoRepository {
    private let api: APIClient

    /// The feed is asked for on every return to the app and every visit to Home; within the cache
    /// window the last answer is reused. Pull-to-refresh always asks the server (and refills this).
    private let feedCache = TtlCache<String, [FeedItem]>()
    private let feedFlight = SingleFlight<String, [FeedItem]>()
    private static let feedKey = "feed"

    init(api: APIClient) {
        self.api = api
    }

    /// Forgets the remembered feed. Called on sign-out, so the next account never sees the
    /// previous one's photos.
    func clearCache() {
        feedCache.removeAll()
    }

    func feed(forceRefresh: Bool = false) async throws -> [FeedItem] {
        if !forceRefresh, let cached = feedCache.value(for: Self.feedKey) { return cached }
        // A forced refresh never joins a normal request already on its way, which might be
        // answered from the server's cache.
        let items = try await feedFlight.run(forceRefresh ? "feed-refresh" : Self.feedKey) { [api] in
            try await api.send(.feed(refresh: forceRefresh))
        }
        feedCache.store(items, for: Self.feedKey)
        return items
    }

    func memories(from start: Date, to end: Date) async throws -> [MemoryPhoto] {
        try await api.send(.memories(from: start, to: end))
    }

    /// Photos sent in the last 24 hours, newest first.
    func sentPhotos() async throws -> [SentPhoto] {
        try await api.send(.sentPhotos())
    }

    func markSeen(_ photoId: String) async {
        let marked = (try? await api.send(.markPhotoSeen(photoId))) != nil
        // Otherwise a refresh in the next 30 seconds could bring back the feed from before this
        // photo was marked, and show it as new again.
        if marked { feedCache.removeAll() }
    }

    /// Removes a photo: from Memories, or (for a sent photo) from the friends it was sent to.
    func delete(_ photoId: String) async throws {
        _ = try await api.send(.deletePhoto(photoId))
    }

    func upload(jpeg: Data, recipientIds: [String], save: Bool) async throws -> PhotoUploadResponse {
        try await api.send(.uploadPhoto(jpeg: jpeg, recipientIds: recipientIds, save: save))
    }

    func markSaved(_ photoId: String) async throws {
        _ = try await api.send(.markPhotoSaved(photoId))
    }

    func addRecipients(_ photoId: String, recipientIds: [String]) async throws {
        _ = try await api.send(.addPhotoRecipients(photoId, recipientIds: recipientIds))
    }
}
