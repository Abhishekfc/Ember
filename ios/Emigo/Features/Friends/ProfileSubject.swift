import Foundation

/// Whose profile is open, and how you know them. A profile looks and acts differently for a
/// friend, for someone who has asked to be your friend, and for someone found in a search.
enum ProfileSubject: Hashable {
    case friend(FriendSummary)
    case pendingRequest(PendingFriendRequest)
    case searchResult(FriendSearchResult)

    var userId: String {
        switch self {
        case .friend(let friend): friend.friendId
        case .pendingRequest(let request): request.requesterId
        case .searchResult(let result): result.userId
        }
    }

    /// The link between you and them, once there is one (a request or a friendship).
    var friendshipId: String? {
        switch self {
        case .friend(let friend): friend.friendshipId
        case .pendingRequest(let request): request.friendshipId
        case .searchResult(let result): result.friendshipId
        }
    }

    var displayName: String {
        switch self {
        case .friend(let friend): friend.displayName
        case .pendingRequest(let request): request.displayName
        case .searchResult(let result): result.displayName
        }
    }

    var username: String {
        switch self {
        case .friend(let friend): friend.username
        case .pendingRequest(let request): request.username
        case .searchResult(let result): result.username
        }
    }

    /// Search results don't carry a picture.
    var profilePhotoURL: URL? {
        switch self {
        case .friend(let friend): friend.profilePhotoUrl.flatMap { URL(string: $0) }
        case .pendingRequest(let request): request.profilePhotoUrl.flatMap { URL(string: $0) }
        case .searchResult: nil
        }
    }

    var friend: FriendSummary? {
        if case .friend(let friend) = self { return friend }
        return nil
    }
}
