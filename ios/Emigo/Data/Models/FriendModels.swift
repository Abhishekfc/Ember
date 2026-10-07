import Foundation

struct FriendSummary: Codable, Hashable, Identifiable {
    let friendshipId: String
    let friendId: String
    let displayName: String
    let username: String
    let profilePhotoUrl: String?
    let pinnedByMe: Bool
    let pinnedByThem: Bool
    let lastActivityAt: Date?
    let lastActivityBySelf: Bool?
    let streak: Int
    let streakDeadlineEpochSeconds: Int64?
    let streakRestoreDeadlineEpochSeconds: Int64?

    var id: String { friendshipId }
}

struct PendingFriendRequest: Codable, Hashable, Identifiable {
    let friendshipId: String
    let requesterId: String
    let displayName: String
    let username: String
    let profilePhotoUrl: String?
    let createdAt: Date

    var id: String { friendshipId }
}

struct FriendSearchResult: Codable, Hashable, Identifiable {
    let userId: String
    let displayName: String
    let username: String
    let requested: Bool
    let friendshipId: String?
    let isPendingFromMe: Bool
    let isPendingFromThem: Bool

    var id: String { userId }

    init(
        userId: String,
        displayName: String,
        username: String,
        requested: Bool,
        friendshipId: String? = nil,
        isPendingFromMe: Bool = false,
        isPendingFromThem: Bool = false
    ) {
        self.userId = userId
        self.displayName = displayName
        self.username = username
        self.requested = requested
        self.friendshipId = friendshipId
        self.isPendingFromMe = isPendingFromMe
        self.isPendingFromThem = isPendingFromThem
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        userId = try container.decode(String.self, forKey: .userId)
        displayName = try container.decode(String.self, forKey: .displayName)
        username = try container.decode(String.self, forKey: .username)
        requested = try container.decode(Bool.self, forKey: .requested)
        friendshipId = try container.decodeIfPresent(String.self, forKey: .friendshipId)
        isPendingFromMe = try container.decodeIfPresent(Bool.self, forKey: .isPendingFromMe) ?? false
        isPendingFromThem = try container.decodeIfPresent(Bool.self, forKey: .isPendingFromThem) ?? false
    }
}

struct FriendRequestBody: Encodable {
    var targetUserId: String?
    var email: String?
}

struct FriendAcceptBody: Encodable {
    let friendshipId: String
}

struct RecipientList: Codable, Hashable, Identifiable {
    let id: String
    let name: String
    let friendIds: [String]
    let createdAt: Date
}

struct CreateRecipientListBody: Encodable {
    let name: String
    let friendIds: [String]
}

/// A page of results from an endpoint that supports `offset`/`limit`.
struct Page<Item: Decodable>: Decodable {
    let items: [Item]
    let hasMore: Bool
}
