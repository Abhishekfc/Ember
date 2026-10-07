import Foundation

struct UserProfile: Codable, Hashable, Identifiable {
    let userId: String
    let displayName: String
    let username: String
    let email: String
    let profilePhotoUrl: String?
    let createdAt: Date?
    /// True while the account still has to verify its email. Only the server knows the deadline,
    /// so it alone decides (see `AuthRepository`).
    let emailVerificationRequired: Bool

    var id: String { userId }

    init(
        userId: String,
        displayName: String,
        username: String,
        email: String,
        profilePhotoUrl: String? = nil,
        createdAt: Date? = nil,
        emailVerificationRequired: Bool = false
    ) {
        self.userId = userId
        self.displayName = displayName
        self.username = username
        self.email = email
        self.profilePhotoUrl = profilePhotoUrl
        self.createdAt = createdAt
        self.emailVerificationRequired = emailVerificationRequired
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        userId = try container.decode(String.self, forKey: .userId)
        displayName = try container.decode(String.self, forKey: .displayName)
        username = try container.decode(String.self, forKey: .username)
        email = try container.decode(String.self, forKey: .email)
        profilePhotoUrl = try container.decodeIfPresent(String.self, forKey: .profilePhotoUrl)
        createdAt = try container.decodeIfPresent(Date.self, forKey: .createdAt)
        emailVerificationRequired = try container.decodeIfPresent(Bool.self, forKey: .emailVerificationRequired) ?? false
    }
}

struct CompleteProfileRequest: Encodable {
    let displayName: String
    let username: String
}

struct UpdateProfileRequest: Encodable {
    var displayName: String?
    var username: String?
}

struct UsernameAvailability: Decodable, Hashable {
    let available: Bool
    let suggestions: [String]

    init(available: Bool, suggestions: [String] = []) {
        self.available = available
        self.suggestions = suggestions
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        available = try container.decode(Bool.self, forKey: .available)
        suggestions = try container.decodeIfPresent([String].self, forKey: .suggestions) ?? []
    }

    private enum CodingKeys: String, CodingKey { case available, suggestions }
}

struct EmailAvailability: Decodable, Hashable {
    let available: Bool
}

struct UsernameLoginLookup: Decodable, Hashable {
    /// The email behind a username, or nil when no account has that username.
    let email: String?
}

struct DeviceTokenRequest: Encodable {
    let fcmToken: String
}
