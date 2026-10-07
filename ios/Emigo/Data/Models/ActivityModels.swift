import Foundation

enum ActivityEventType: String, Codable {
    case photoReceived = "PHOTO_RECEIVED"
    case streakExpiring = "STREAK_EXPIRING"
    case streakBroken = "STREAK_BROKEN"
    case requestAccepted = "REQUEST_ACCEPTED"
    case requestIncoming = "REQUEST_INCOMING"
    /// A type a newer server sends that this build doesn't know yet. Shown as a plain row rather
    /// than failing the whole activity list.
    case unknown

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = ActivityEventType(rawValue: raw) ?? .unknown
    }
}

struct ActivityEvent: Codable, Hashable, Identifiable {
    let type: ActivityEventType
    let actorId: String
    let actorDisplayName: String
    let actorProfilePhotoUrl: String?
    let message: String
    let createdAt: Date
    let warn: Bool
    let photoUrl: String?

    var id: String { "\(type.rawValue)-\(actorId)-\(createdAt.timeIntervalSince1970)" }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        type = try container.decode(ActivityEventType.self, forKey: .type)
        actorId = try container.decode(String.self, forKey: .actorId)
        actorDisplayName = try container.decode(String.self, forKey: .actorDisplayName)
        actorProfilePhotoUrl = try container.decodeIfPresent(String.self, forKey: .actorProfilePhotoUrl)
        message = try container.decode(String.self, forKey: .message)
        createdAt = try container.decode(Date.self, forKey: .createdAt)
        warn = try container.decodeIfPresent(Bool.self, forKey: .warn) ?? false
        photoUrl = try container.decodeIfPresent(String.self, forKey: .photoUrl)
    }
}

struct ActivityLastSeen: Decodable, Hashable {
    let lastSeenAt: Date?
}
