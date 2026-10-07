import Foundation
import OSLog

/// Supplies the current signed-in user's Firebase ID token. Returns nil when nobody is signed in.
protocol AccessTokenProviding: AnyObject {
    func idToken() async -> String?
}

/// The one place HTTP happens. Attaches the Firebase token to every request, turns status codes
/// and transport failures into `APIError`, and tells the app when the session has died.
final class APIClient {
    private let baseURL: URL
    private let session: URLSession
    private let tokenProvider: AccessTokenProviding
    private let logger = Logger(subsystem: "com.emigo.app", category: "api")

    /// Called when an authenticated request comes back 401: the token was rejected, or the account
    /// no longer exists. The app signs the user out instead of leaving them on a screen whose
    /// requests will never succeed again.
    var onSessionExpired: (@MainActor () -> Void)?

    init(baseURL: URL, tokenProvider: AccessTokenProviding, session: URLSession = APIClient.makeSession()) {
        self.baseURL = baseURL
        self.tokenProvider = tokenProvider
        self.session = session
    }

    /// Photo uploads can take a while (the server stores the file and fans out notifications before
    /// answering), so reads and writes get a generous minute rather than the 10-second default.
    static func makeSession(protocolClasses: [AnyClass]? = nil) -> URLSession {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 60
        configuration.timeoutIntervalForResource = 120
        configuration.waitsForConnectivity = false
        configuration.urlCache = nil
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        if let protocolClasses { configuration.protocolClasses = protocolClasses }
        return URLSession(configuration: configuration)
    }

    func send<Response: Decodable>(_ endpoint: Endpoint<Response>) async throws -> Response {
        let data = try await perform(endpoint)
        if Response.self == EmptyResponse.self, let empty = EmptyResponse() as? Response {
            return empty
        }
        do {
            return try JSONDecoder.emigo.decode(Response.self, from: data)
        } catch {
            logger.error("Decoding \(endpoint.path, privacy: .public) failed: \(String(describing: error), privacy: .public)")
            throw APIError.decoding(error)
        }
    }

    // MARK: - Internals

    private func perform<Response>(_ endpoint: Endpoint<Response>) async throws -> Data {
        let (request, hadToken) = try await makeRequest(for: endpoint)
        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch let error as URLError {
            if error.code == .cancelled { throw CancellationError() }
            throw APIError.network(error)
        }
        guard let http = response as? HTTPURLResponse else { throw APIError.server(status: 0, message: nil) }

        switch http.statusCode {
        case 200..<300:
            return data
        case 401:
            if hadToken && !endpoint.handlesUnauthorizedItself {
                await MainActor.run { onSessionExpired?() }
            }
            throw APIError.unauthorized
        default:
            let message = (try? JSONDecoder.emigo.decode(ErrorResponse.self, from: data))?.message
            throw APIError.server(status: http.statusCode, message: message)
        }
    }

    private func makeRequest<Response>(for endpoint: Endpoint<Response>) async throws -> (URLRequest, Bool) {
        guard var components = URLComponents(url: baseURL.appendingPathComponent(endpoint.path), resolvingAgainstBaseURL: false) else {
            throw APIError.invalidURL
        }
        if !endpoint.query.isEmpty { components.queryItems = endpoint.query }
        guard let url = components.url else { throw APIError.invalidURL }

        var request = URLRequest(url: url)
        request.httpMethod = endpoint.method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Accept")

        if let multipart = endpoint.multipart {
            let boundary = "emigo-\(UUID().uuidString)"
            request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
            request.httpBody = multipart.encoded(boundary: boundary)
        } else if let body = endpoint.body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = body
        }

        let token = await tokenProvider.idToken()
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        return (request, token != nil)
    }
}
