import Foundation
import Observation

/// Chooses who a photo goes to. Port of Android's `RecipientPickerViewModel`.
@MainActor
@Observable
final class RecipientPickerViewModel {
    nonisolated static let recentBadgeId = "recent"
    nonisolated static let everyoneBadgeId = "everyone"

    private(set) var friends: [FriendSummary]
    private(set) var isLoading: Bool
    private(set) var errorMessage: String?
    private(set) var selectedFriendIds: Set<String>
    /// Friends picked when the screen opened are listed first; toggling doesn't reshuffle the list
    /// under the person's finger.
    private var sortSnapshot: Set<String>
    private(set) var recentIds: Set<String>
    private(set) var customLists: [RecipientList] = []
    private(set) var isMutatingLists = false
    /// Which quick filter is on: "recent", "everyone", or a custom list's id.
    private(set) var activeFilterId: String?
    var searchQuery = ""

    private let repository: FriendRepository
    private let store: SessionStore

    init(repository: FriendRepository, store: SessionStore, initialSelected: [String], initialFriends: [FriendSummary]) {
        self.repository = repository
        self.store = store
        friends = initialFriends
        isLoading = initialFriends.isEmpty
        selectedFriendIds = Set(initialSelected)
        sortSnapshot = Set(initialSelected)
        recentIds = Set(store.lastRecipientIds)
        activeFilterId = Self.matchingFilter(
            selected: Set(initialSelected), recent: Set(store.lastRecipientIds), friends: initialFriends, lists: []
        )
    }

    // MARK: - Reading

    var allFriendIds: Set<String> { Set(friends.map(\.friendId)) }

    /// The selection as an array, in the order of the friends list.
    var selectionInOrder: [String] {
        friends.map(\.friendId).filter { selectedFriendIds.contains($0) }
    }

    var visibleFriends: [FriendSummary] {
        let listIds = customLists.first { $0.id == activeFilterId }.map { Set($0.friendIds) }
        let filtered = listIds.map { ids in friends.filter { ids.contains($0.friendId) } } ?? friends
        let query = searchQuery.trimmingCharacters(in: .whitespaces)
        let searched = query.isEmpty ? filtered : filtered.filter {
            $0.displayName.localizedCaseInsensitiveContains(query) || $0.username.localizedCaseInsensitiveContains(query)
        }
        // Stable sort: the chosen ones first, everyone else keeping the server's order.
        return searched.enumerated()
            .sorted { lhs, rhs in
                let l = sortSnapshot.contains(lhs.element.friendId)
                let r = sortSnapshot.contains(rhs.element.friendId)
                return l != r ? l : lhs.offset < rhs.offset
            }
            .map(\.element)
    }

    // MARK: - Loading

    func load() async {
        async let lists = try? repository.recipientLists()
        do {
            friends = try await repository.friends(limit: allFriendsLimit).items
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            if friends.isEmpty { errorMessage = String(localized: Strings.Failure.loadFriends) }
        }
        isLoading = false
        if let found = await lists { customLists = found }
        activeFilterId = Self.matchingFilter(selected: selectedFriendIds, recent: recentIds, friends: friends, lists: customLists)
    }

    // MARK: - Choosing

    func toggle(_ friendId: String) {
        if selectedFriendIds.contains(friendId) { selectedFriendIds.remove(friendId) } else { selectedFriendIds.insert(friendId) }
        activeFilterId = activeFilterId == Self.recentBadgeId || activeFilterId == Self.everyoneBadgeId ? nil : activeFilterId
    }

    func selectRecent() {
        activeFilterId = Self.recentBadgeId
        setSelection(recentIds.intersection(allFriendIds))
    }

    func selectEveryone() {
        activeFilterId = Self.everyoneBadgeId
        setSelection(allFriendIds)
    }

    func selectList(_ list: RecipientList) {
        activeFilterId = list.id
        setSelection(Set(list.friendIds))
    }

    private func setSelection(_ ids: Set<String>) {
        selectedFriendIds = ids
        sortSnapshot = ids
    }

    // MARK: - Lists

    func createList(named name: String) async {
        let trimmed = name.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty, !selectedFriendIds.isEmpty, !isMutatingLists else { return }
        isMutatingLists = true
        defer { isMutatingLists = false }
        do {
            let created = try await repository.createRecipientList(name: trimmed, friendIds: selectionInOrder)
            customLists.append(created)
            activeFilterId = created.id
            Haptics.success()
        } catch is CancellationError {
            return
        } catch {
            errorMessage = String(localized: Strings.Failure.saveList)
        }
    }

    func deleteList(_ id: String) async {
        guard !isMutatingLists else { return }
        isMutatingLists = true
        defer { isMutatingLists = false }
        let previous = customLists
        customLists.removeAll { $0.id == id }
        if activeFilterId == id { activeFilterId = nil }
        do {
            try await repository.deleteRecipientList(id)
        } catch is CancellationError {
            return
        } catch {
            customLists = previous
            errorMessage = String(localized: Strings.Failure.deleteList)
        }
    }

    func dismissError() {
        errorMessage = nil
    }

    // MARK: - Which badge is lit

    /// If the chosen friends are exactly one of the quick groups, that group's badge is lit.
    nonisolated static func matchingFilter(
        selected: Set<String>,
        recent: Set<String>,
        friends: [FriendSummary],
        lists: [RecipientList]
    ) -> String? {
        if !recent.isEmpty, selected == recent { return recentBadgeId }
        if !friends.isEmpty, selected == Set(friends.map(\.friendId)) { return everyoneBadgeId }
        return lists.first { Set($0.friendIds) == selected }?.id
    }
}
