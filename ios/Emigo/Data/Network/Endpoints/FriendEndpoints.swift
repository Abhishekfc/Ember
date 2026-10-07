import Foundation

extension Endpoint where Response == Page<FriendSummary> {
    static func friends(refresh: Bool = false, offset: Int = 0, limit: Int = 30) -> Endpoint {
        Endpoint(.get, "friends", query: [
            URLQueryItem(name: "refresh", value: refresh ? "true" : "false"),
            URLQueryItem(name: "offset", value: String(offset)),
            URLQueryItem(name: "limit", value: String(limit)),
        ])
    }
}

extension Endpoint where Response == [PendingFriendRequest] {
    static func pendingFriendRequests() -> Endpoint {
        Endpoint(.get, "friends/pending")
    }
}

extension Endpoint where Response == [FriendSearchResult] {
    static func searchFriends(_ query: String) -> Endpoint {
        Endpoint(.get, "friends/search", query: [URLQueryItem(name: "q", value: query)])
    }
}

extension Endpoint where Response == PendingFriendRequest {
    static func sendFriendRequest(targetUserId: String? = nil, email: String? = nil) -> Endpoint {
        Endpoint(.post, "friends/request", body: FriendRequestBody(targetUserId: targetUserId, email: email))
    }
}

extension Endpoint where Response == FriendSummary {
    static func acceptFriendRequest(friendshipId: String) -> Endpoint {
        Endpoint(.post, "friends/accept", body: FriendAcceptBody(friendshipId: friendshipId))
    }

    static func pinFriend(friendshipId: String) -> Endpoint {
        Endpoint(.post, "friends/\(friendshipId)/pin")
    }

    static func unpinFriend(friendshipId: String) -> Endpoint {
        Endpoint(.delete, "friends/\(friendshipId)/pin")
    }

    static func restoreStreak(friendshipId: String) -> Endpoint {
        Endpoint(.post, "friends/\(friendshipId)/streak/restore")
    }
}

extension Endpoint where Response == [RecipientList] {
    static func recipientLists() -> Endpoint {
        Endpoint(.get, "recipient-lists")
    }
}

extension Endpoint where Response == RecipientList {
    static func createRecipientList(name: String, friendIds: [String]) -> Endpoint {
        Endpoint(.post, "recipient-lists", body: CreateRecipientListBody(name: name, friendIds: friendIds))
    }
}

extension Endpoint where Response == EmptyResponse {
    static func removeFriend(friendshipId: String) -> Endpoint {
        Endpoint(.delete, "friends/\(friendshipId)")
    }

    static func deleteRecipientList(_ listId: String) -> Endpoint {
        Endpoint(.delete, "recipient-lists/\(listId)")
    }
}

extension Endpoint where Response == Page<ActivityEvent> {
    static func activity(refresh: Bool = false, offset: Int = 0, limit: Int = 30) -> Endpoint {
        Endpoint(.get, "activity", query: [
            URLQueryItem(name: "refresh", value: refresh ? "true" : "false"),
            URLQueryItem(name: "offset", value: String(offset)),
            URLQueryItem(name: "limit", value: String(limit)),
        ])
    }
}

extension Endpoint where Response == ActivityLastSeen {
    static func activityLastSeen() -> Endpoint {
        Endpoint(.get, "activity/seen")
    }

    static func markActivitySeen() -> Endpoint {
        Endpoint(.post, "activity/seen")
    }
}
