import Foundation

/// The most friends one request asks for. Large enough that "all my friends" is one call.
let allFriendsLimit = 500

@MainActor
final class FriendRepository {
    private let api: APIClient

    /// Each page of the friend list is remembered separately (by where it starts and how long it
    /// is). Pull-to-refresh and anything that changes who your friends are bypass or clear it.
    private struct PageKey: Hashable {
        let offset: Int
        let limit: Int
    }

    private let friendsCache = TtlCache<PageKey, Page<FriendSummary>>()
    private let friendsFlight = SingleFlight<String, Page<FriendSummary>>()

    init(api: APIClient) {
        self.api = api
    }

    /// Forgets the remembered friend lists. Called on sign-out.
    func clearCache() {
        friendsCache.removeAll()
    }

    func friends(forceRefresh: Bool = false, offset: Int = 0, limit: Int = 30) async throws -> Page<FriendSummary> {
        let key = PageKey(offset: offset, limit: limit)
        if !forceRefresh, let cached = friendsCache.value(for: key) { return cached }
        let page = try await friendsFlight.run("\(forceRefresh ? "refresh" : "read")-\(offset)-\(limit)") { [api] in
            try await api.send(.friends(refresh: forceRefresh, offset: offset, limit: limit))
        }
        friendsCache.store(page, for: key)
        return page
    }

    func pendingRequests() async throws -> [PendingFriendRequest] {
        try await api.send(.pendingFriendRequests())
    }

    func search(_ query: String) async throws -> [FriendSearchResult] {
        try await api.send(.searchFriends(query))
    }

    func sendRequest(to userId: String) async throws {
        try await sendRequest(toUser: userId)
    }

    /// Accepts a friend request; returns the new friend.
    @discardableResult
    func accept(friendshipId: String) async throws -> FriendSummary {
        let friend = try await api.send(.acceptFriendRequest(friendshipId: friendshipId))
        friendsCache.removeAll()
        return friend
    }

    /// Pins or unpins a friend as your partner; returns the friend as it now is.
    func setPinned(friendshipId: String, pinned: Bool) async throws -> FriendSummary {
        let friend = try await api.send(pinned ? .pinFriend(friendshipId: friendshipId) : .unpinFriend(friendshipId: friendshipId))
        friendsCache.removeAll()
        return friend
    }

    /// Asks someone to be friends; returns the pending request.
    @discardableResult
    func sendRequest(toUser userId: String) async throws -> PendingFriendRequest {
        try await api.send(.sendFriendRequest(targetUserId: userId))
    }

    func recipientLists() async throws -> [RecipientList] {
        try await api.send(.recipientLists())
    }

    func createRecipientList(name: String, friendIds: [String]) async throws -> RecipientList {
        try await api.send(.createRecipientList(name: name, friendIds: friendIds))
    }

    func deleteRecipientList(_ id: String) async throws {
        _ = try await api.send(.deleteRecipientList(id))
    }

    /// Declining a request, cancelling one, and unfriending are all "remove this friendship".
    func remove(friendshipId: String) async throws {
        _ = try await api.send(.removeFriend(friendshipId: friendshipId))
        friendsCache.removeAll()
    }
}
