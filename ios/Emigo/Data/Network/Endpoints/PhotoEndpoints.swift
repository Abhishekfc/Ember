import Foundation

extension Endpoint where Response == [FeedItem] {
    static func feed(refresh: Bool) -> Endpoint {
        Endpoint(.get, "photos/feed", query: [URLQueryItem(name: "refresh", value: refresh ? "true" : "false")])
    }
}

extension Endpoint where Response == [MemoryPhoto] {
    /// `start` and `end` are instants; the caller works out calendar-month boundaries in local
    /// time, so the server never has to guess which timezone "this month" means.
    static func memories(from start: Date, to end: Date) -> Endpoint {
        Endpoint(.get, "photos/memories", query: [
            URLQueryItem(name: "start", value: ServerDate.string(from: start)),
            URLQueryItem(name: "end", value: ServerDate.string(from: end)),
        ])
    }
}

extension Endpoint where Response == [SentPhoto] {
    /// Photos sent in the last 24 hours, for the Camera outbox.
    static func sentPhotos() -> Endpoint {
        Endpoint(.get, "photos/sent")
    }
}

extension Endpoint where Response == PhotoUploadResponse {
    static func uploadPhoto(jpeg: Data, recipientIds: [String], save: Bool) -> Endpoint {
        var form = MultipartForm()
        form.addFile("file", filename: "photo.jpg", mimeType: "image/jpeg", data: jpeg)
        for id in recipientIds { form.addField("recipientIds", value: id) }
        form.addField("save", value: save ? "true" : "false")
        return Endpoint(.post, "photos", multipart: form)
    }
}

extension Endpoint where Response == EmptyResponse {
    static func markPhotoSeen(_ photoId: String) -> Endpoint {
        Endpoint(.post, "photos/\(photoId)/seen")
    }

    static func deletePhoto(_ photoId: String) -> Endpoint {
        Endpoint(.delete, "photos/\(photoId)")
    }

    static func markPhotoSaved(_ photoId: String) -> Endpoint {
        Endpoint(.post, "photos/\(photoId)/save")
    }

    static func addPhotoRecipients(_ photoId: String, recipientIds: [String]) -> Endpoint {
        Endpoint(.post, "photos/\(photoId)/recipients", body: AddPhotoRecipientsRequest(recipientIds: recipientIds))
    }
}
