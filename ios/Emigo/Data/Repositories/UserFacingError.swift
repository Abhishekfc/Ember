import Foundation

/// An error whose message is already worded for the person using the app.
struct UserFacingError: LocalizedError, Equatable {
    let message: String

    var errorDescription: String? { message }

    init(_ message: String) {
        self.message = message
    }

    init(_ resource: LocalizedStringResource) {
        self.message = String(localized: resource)
    }
}

extension Error {
    /// The message to show for this error, falling back to a generic one.
    var userMessage: String {
        if let message = (self as? LocalizedError)?.errorDescription { return message }
        return String(localized: Strings.Failure.somethingWentWrong)
    }
}
