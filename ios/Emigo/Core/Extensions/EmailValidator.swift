import Foundation

/// A quick check that a string looks like an email address. The server and Firebase make the real
/// decision; this only stops obvious typos before a network call.
enum EmailValidator {
    private static let pattern = #"^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$"#

    static func isValid(_ text: String) -> Bool {
        text.trimmingCharacters(in: .whitespacesAndNewlines).range(of: pattern, options: .regularExpression) != nil
    }
}
