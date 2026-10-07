import Foundation

struct PhotoEntry: Codable, Hashable, Identifiable {
    let photoId: String
    let photoUrl: String
    let createdAt: Date
    var seen: Bool

    var id: String { photoId }
    var url: URL? { URL(string: photoUrl) }
}

/// One friend's photos in the Home feed, oldest first as the server sends them.
struct FeedItem: Codable, Hashable, Identifiable {
    let friendId: String
    let displayName: String
    var photos: [PhotoEntry]
    let streak: Int

    var id: String { friendId }
    var hasUnseenPhoto: Bool { photos.contains { !$0.seen } }
}

struct MemoryPhoto: Codable, Hashable, Identifiable {
    let photoId: String
    let photoUrl: String
    let createdAt: Date

    var id: String { photoId }
    var url: URL? { URL(string: photoUrl) }
}

struct SentPhoto: Codable, Hashable, Identifiable {
    let photoId: String
    let photoUrl: String
    let createdAt: Date

    var id: String { photoId }
    var url: URL? { URL(string: photoUrl) }
}

struct PhotoUploadResponse: Decodable, Hashable {
    let photoId: String
    let url: String
    let createdAt: Date
    let recipientIds: [String]
    let saved: Bool
}

struct AddPhotoRecipientsRequest: Encodable {
    let recipientIds: [String]
}
