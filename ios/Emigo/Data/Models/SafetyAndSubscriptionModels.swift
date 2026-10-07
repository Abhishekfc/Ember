import Foundation

struct BlockedUser: Codable, Hashable, Identifiable {
    let userId: String
    let displayName: String
    let username: String
    let profilePhotoUrl: String?
    let blockedAt: Date

    var id: String { userId }
}

enum ReportReason: String, Codable, CaseIterable {
    case spam = "SPAM"
    case harassment = "HARASSMENT"
    case inappropriateContent = "INAPPROPRIATE_CONTENT"
    case fakeAccount = "FAKE_ACCOUNT"
    case other = "OTHER"
}

struct ReportUserRequest: Encodable {
    let reason: ReportReason
    var details: String?
}

struct SubscriptionStatus: Decodable, Hashable {
    let status: String
    let plan: String?
    let expiresAt: Date?

    var isActive: Bool { status == "ACTIVE" }
}

/// The error body the backend sends with a failed request.
struct ErrorResponse: Decodable {
    let status: Int?
    let error: String?
    let message: String?
}
