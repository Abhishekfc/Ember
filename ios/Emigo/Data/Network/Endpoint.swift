import Foundation

enum HTTPMethod: String {
    case get = "GET"
    case post = "POST"
    case patch = "PATCH"
    case delete = "DELETE"
}

/// A response with no body (HTTP 204 or an ignored body).
struct EmptyResponse: Decodable {
    init() {}
}

/// One backend call: where it goes and what comes back. The `Response` type is what lets
/// `APIClient.send` return the right model without a cast. The calls themselves live in
/// `Endpoints/` as static factories, one file per area, mirroring Android's `EmberApi`.
struct Endpoint<Response: Decodable> {
    let method: HTTPMethod
    let path: String
    var query: [URLQueryItem] = []
    var body: Data?
    var multipart: MultipartForm?
    /// True for the two calls whose 401 is an expected answer their caller already handles, so it
    /// must not count as "the session died". See `APIClient`.
    var handlesUnauthorizedItself = false

    init(
        _ method: HTTPMethod,
        _ path: String,
        query: [URLQueryItem] = [],
        body: (any Encodable)? = nil,
        multipart: MultipartForm? = nil,
        handlesUnauthorizedItself: Bool = false
    ) {
        self.method = method
        self.path = path
        self.query = query
        self.body = body.flatMap { try? JSONEncoder.emigo.encode(AnyEncodable($0)) }
        self.multipart = multipart
        self.handlesUnauthorizedItself = handlesUnauthorizedItself
    }
}

/// Lets an `any Encodable` be handed to `JSONEncoder`.
private struct AnyEncodable: Encodable {
    private let encodeValue: (Encoder) throws -> Void

    init(_ value: any Encodable) {
        encodeValue = { try value.encode(to: $0) }
    }

    func encode(to encoder: Encoder) throws {
        try encodeValue(encoder)
    }
}

/// A `multipart/form-data` body, used for photo uploads.
struct MultipartForm {
    struct Part {
        let name: String
        let filename: String?
        let mimeType: String?
        let data: Data
    }

    private(set) var parts: [Part] = []

    mutating func addField(_ name: String, value: String) {
        parts.append(Part(name: name, filename: nil, mimeType: nil, data: Data(value.utf8)))
    }

    mutating func addFile(_ name: String, filename: String, mimeType: String, data: Data) {
        parts.append(Part(name: name, filename: filename, mimeType: mimeType, data: data))
    }

    func encoded(boundary: String) -> Data {
        var body = Data()
        for part in parts {
            body.append(Data("--\(boundary)\r\n".utf8))
            var disposition = "Content-Disposition: form-data; name=\"\(part.name)\""
            if let filename = part.filename { disposition += "; filename=\"\(filename)\"" }
            body.append(Data("\(disposition)\r\n".utf8))
            if let mimeType = part.mimeType { body.append(Data("Content-Type: \(mimeType)\r\n".utf8)) }
            body.append(Data("\r\n".utf8))
            body.append(part.data)
            body.append(Data("\r\n".utf8))
        }
        body.append(Data("--\(boundary)--\r\n".utf8))
        return body
    }
}
