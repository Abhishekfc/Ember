import Foundation
import Observation

/// The things you can do from a profile.
enum ProfileAction: Equatable {
    case pin
    case remove
    case accept
    case reject
    case sendRequest
    case cancelRequest
    case block
    case report
}

/// Port of Android's `FriendProfileViewModel`.
@MainActor
@Observable
final class FriendProfileViewModel {
    private(set) var subject: ProfileSubject
    /// Which action is running, so just its button shows a spinner.
    private(set) var running: ProfileAction?
    private(set) var errorMessage: String?
    private(set) var reportSubmitted = false

    private let friends: FriendRepository
    private let safety: SafetyRepository

    init(subject: ProfileSubject, friends: FriendRepository, safety: SafetyRepository) {
        self.subject = subject
        self.friends = friends
        self.safety = safety
    }

    var isBusy: Bool { running != nil }

    /// A search result for someone you're already friends with has to be looked up to become a
    /// real friend profile, with their streak and pin.
    func resolveExistingFriend() async {
        guard case .searchResult(let result) = subject,
              result.requested, !result.isPendingFromMe, !result.isPendingFromThem else { return }
        if let page = try? await friends.friends(limit: allFriendsLimit),
           let match = page.items.first(where: { $0.friendId == result.userId }) {
            subject = .friend(match)
        }
    }

    // MARK: - Actions (each returns whether it worked)

    func togglePin() async -> Bool {
        guard case .friend(let friend) = subject else { return false }
        return await perform(.pin, failure: Strings.Failure.pin) {
            subject = .friend(try await friends.setPinned(friendshipId: friend.friendshipId, pinned: !friend.pinnedByMe))
        }
    }

    func removeFriend() async -> Bool {
        guard let friendshipId = subject.friendshipId else { return false }
        return await perform(.remove, failure: Strings.Failure.removeFriend) {
            try await friends.remove(friendshipId: friendshipId)
        }
    }

    /// Accepting turns the profile into a friend's, in place.
    func acceptRequest() async -> Bool {
        guard let friendshipId = subject.friendshipId else { return false }
        return await perform(.accept, failure: Strings.Failure.acceptRequest) {
            subject = .friend(try await friends.accept(friendshipId: friendshipId))
        }
    }

    func rejectRequest() async -> Bool {
        guard let friendshipId = subject.friendshipId else { return false }
        return await perform(.reject, failure: Strings.Failure.declineRequest) {
            try await friends.remove(friendshipId: friendshipId)
        }
    }

    func sendRequest() async -> Bool {
        guard case .searchResult(let result) = subject else { return false }
        return await perform(.sendRequest, failure: Strings.Failure.sendRequest) {
            let sent = try await friends.sendRequest(toUser: result.userId)
            subject = .searchResult(Self.result(result, requested: true, friendshipId: sent.friendshipId, pendingFromMe: true))
        }
    }

    func cancelRequest() async -> Bool {
        guard case .searchResult(let result) = subject, let friendshipId = result.friendshipId else { return false }
        return await perform(.cancelRequest, failure: Strings.Failure.cancelRequest) {
            try await friends.remove(friendshipId: friendshipId)
            subject = .searchResult(Self.result(result, requested: false, friendshipId: nil, pendingFromMe: false))
        }
    }

    func blockUser() async -> Bool {
        let userId = subject.userId
        return await perform(.block, failure: Strings.Failure.block) {
            try await safety.block(userId: userId)
        }
    }

    func reportUser(reason: ReportReason) async -> Bool {
        let userId = subject.userId
        let sent = await perform(.report, failure: Strings.Failure.report) {
            try await safety.report(userId: userId, reason: reason)
        }
        reportSubmitted = sent
        return sent
    }

    func dismissReportConfirmation() {
        reportSubmitted = false
    }

    func dismissError() {
        errorMessage = nil
    }

    // MARK: - Internals

    private func perform(_ action: ProfileAction, failure: LocalizedStringResource, _ work: () async throws -> Void) async -> Bool {
        guard running == nil else { return false }
        running = action
        errorMessage = nil
        defer { running = nil }
        do {
            try await work()
            return true
        } catch is CancellationError {
            return false
        } catch {
            errorMessage = (error as? APIError)?.errorDescription ?? String(localized: failure)
            Haptics.error()
            return false
        }
    }

    /// A copy of a search result with the request state changed.
    private static func result(_ old: FriendSearchResult, requested: Bool, friendshipId: String?, pendingFromMe: Bool) -> FriendSearchResult {
        FriendSearchResult(
            userId: old.userId,
            displayName: old.displayName,
            username: old.username,
            requested: requested,
            friendshipId: friendshipId,
            isPendingFromMe: pendingFromMe,
            isPendingFromThem: old.isPendingFromThem
        )
    }
}
