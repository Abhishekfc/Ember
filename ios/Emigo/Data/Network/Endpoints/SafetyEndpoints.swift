import Foundation

extension Endpoint where Response == [BlockedUser] {
    static func blockedUsers() -> Endpoint {
        Endpoint(.get, "users/blocked")
    }
}

extension Endpoint where Response == EmptyResponse {
    static func blockUser(_ userId: String) -> Endpoint {
        Endpoint(.post, "users/\(userId)/block")
    }

    static func unblockUser(_ userId: String) -> Endpoint {
        Endpoint(.delete, "users/\(userId)/block")
    }

    static func reportUser(_ userId: String, reason: ReportReason, details: String?) -> Endpoint {
        Endpoint(.post, "users/\(userId)/report", body: ReportUserRequest(reason: reason, details: details))
    }
}

extension Endpoint where Response == SubscriptionStatus {
    static func subscriptionStatus() -> Endpoint {
        Endpoint(.get, "subscription/status")
    }
}
