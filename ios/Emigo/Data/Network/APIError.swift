import Foundation

/// Everything that can go wrong talking to the backend, in terms the rest of the app can act on.
enum APIError: LocalizedError {
    /// The server rejected the sign-in token (HTTP 401).
    case unauthorized
    /// The server answered with an error status. `message` is the server's own wording when it sent one.
    case server(status: Int, message: String?)
    /// The request never completed: offline, timed out, server unreachable.
    case network(URLError)
    /// The server answered but the body wasn't what this build expects.
    case decoding(Error)
    case invalidURL

    var errorDescription: String? {
        switch self {
        case .unauthorized:
            String(localized: Strings.Failure.sessionExpired)
        case .server(_, let message):
            message ?? String(localized: Strings.Failure.somethingWentWrong)
        case .network:
            String(localized: Strings.Failure.noConnection)
        case .decoding, .invalidURL:
            String(localized: Strings.Failure.somethingWentWrong)
        }
    }

    var statusCode: Int? {
        if case .server(let status, _) = self { return status }
        if case .unauthorized = self { return 401 }
        return nil
    }
}
