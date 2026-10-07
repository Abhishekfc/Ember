import Foundation
import Observation

/// What the button on a search result should offer.
enum SearchResultAction: Equatable {
    case add
    case requested
    case accept
}

private let minimumSearchLength = 2
private let searchDebounce: Duration = .milliseconds(350)

@MainActor
@Observable
final class FindPeopleViewModel {
    var query = ""
    private(set) var results: [FriendSearchResult] = []
    private(set) var isSearching = false
    private(set) var errorMessage: String?
    /// People asked or accepted during this visit, so their button updates at once.
    private var requestedIds: Set<String> = []
    private var acceptedIds: Set<String> = []
    private var searchTask: Task<Void, Never>?

    private let repository: FriendRepository

    init(repository: FriendRepository) {
        self.repository = repository
    }

    var trimmedQuery: String { query.trimmingCharacters(in: .whitespaces) }
    var isSearchingActive: Bool { trimmedQuery.count >= minimumSearchLength }

    func action(for result: FriendSearchResult) -> SearchResultAction? {
        if acceptedIds.contains(result.userId) { return nil }
        if result.isPendingFromThem { return .accept }
        if result.requested || result.isPendingFromMe || requestedIds.contains(result.userId) { return .requested }
        return .add
    }

    /// Runs the search once typing pauses; older searches are cancelled so a slow one can't
    /// overwrite a newer answer.
    func queryChanged() {
        searchTask?.cancel()
        errorMessage = nil
        guard isSearchingActive else {
            results = []
            isSearching = false
            return
        }
        let text = trimmedQuery
        searchTask = Task {
            isSearching = true
            try? await Task.sleep(for: searchDebounce)
            guard !Task.isCancelled else { return }
            do {
                let found = try await repository.search(text)
                guard !Task.isCancelled else { return }
                results = found
            } catch is CancellationError {
                return
            } catch {
                if !Task.isCancelled { errorMessage = String(localized: Strings.Failure.search) }
            }
            if !Task.isCancelled { isSearching = false }
        }
    }

    func perform(_ action: SearchResultAction, on result: FriendSearchResult) async {
        switch action {
        case .requested:
            return
        case .add:
            do {
                try await repository.sendRequest(to: result.userId)
                requestedIds.insert(result.userId)
                Haptics.success()
            } catch is CancellationError {
                return
            } catch {
                errorMessage = String(localized: Strings.Failure.sendRequest)
                Haptics.error()
            }
        case .accept:
            guard let friendshipId = result.friendshipId else { return }
            do {
                try await repository.accept(friendshipId: friendshipId)
                acceptedIds.insert(result.userId)
                Haptics.success()
            } catch is CancellationError {
                return
            } catch {
                errorMessage = String(localized: Strings.Failure.acceptRequest)
                Haptics.error()
            }
        }
    }
}
