import Foundation
import Observation

@MainActor
@Observable
final class FriendsViewModel {
    private(set) var friends: [FriendSummary] = []
    private(set) var requests: [PendingFriendRequest] = []
    private(set) var hasLoaded = false
    private(set) var loadFailed = false
    private(set) var errorMessage: String?
    var query = ""
    private var isFetching = false

    private let repository: FriendRepository

    init(repository: FriendRepository) {
        self.repository = repository
    }

    // MARK: - Reading

    /// The friend pinned as "your Emigo" partner, shown large at the top.
    var pinned: FriendSummary? { friends.first { $0.pinnedByMe } }
    var showsPinned: Bool { trimmedQuery.isEmpty && pinned != nil }
    var pendingRequestCount: Int { requests.count }
    var showsEmptyState: Bool { hasLoaded && friends.isEmpty && requests.isEmpty }

    private var trimmedQuery: String { query.trimmingCharacters(in: .whitespaces).lowercased() }

    /// The "My friends" list: everyone except the pinned partner, or whoever matches the search.
    var visibleFriends: [FriendSummary] {
        guard !trimmedQuery.isEmpty else { return friends.filter { !$0.pinnedByMe } }
        return friends.filter {
            $0.displayName.lowercased().contains(trimmedQuery) || $0.username.lowercased().contains(trimmedQuery)
        }
    }

    // MARK: - Actions

    func load(forceRefresh: Bool = false) async {
        guard !isFetching else { return }
        isFetching = true
        defer { isFetching = false }

        async let pending = try? repository.pendingRequests()
        do {
            friends = try await repository.friends(forceRefresh: forceRefresh, limit: allFriendsLimit).items
            hasLoaded = true
            loadFailed = false
        } catch is CancellationError {
            return
        } catch {
            loadFailed = true
        }
        if let found = await pending { requests = found }
    }

    func accept(_ request: PendingFriendRequest) async {
        do {
            try await repository.accept(friendshipId: request.friendshipId)
            requests.removeAll { $0.friendshipId == request.friendshipId }
            Haptics.success()
            await load(forceRefresh: true)
        } catch is CancellationError {
            return
        } catch {
            errorMessage = String(localized: Strings.Failure.acceptRequest)
            Haptics.error()
        }
    }

    func decline(_ request: PendingFriendRequest) async {
        do {
            try await repository.remove(friendshipId: request.friendshipId)
            requests.removeAll { $0.friendshipId == request.friendshipId }
        } catch is CancellationError {
            return
        } catch {
            errorMessage = String(localized: Strings.Failure.declineRequest)
            Haptics.error()
        }
    }

    func dismissError() {
        errorMessage = nil
    }
}
