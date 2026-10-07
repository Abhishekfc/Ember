import Foundation
import Observation

/// The "Delete account" confirmation. Deleting can't be undone, so the button only works after the
/// person has typed the word "delete".
@MainActor
@Observable
final class DeleteAccountViewModel {
    /// The word to type. The same on every language, like Android's.
    static let confirmWord = "delete"

    var typedText = ""
    private(set) var isDeleting = false
    private(set) var errorMessage: String?

    private let users: UserRepository

    init(users: UserRepository) {
        self.users = users
    }

    var canDelete: Bool {
        !isDeleting
            && typedText.trimmingCharacters(in: .whitespacesAndNewlines)
                .caseInsensitiveCompare(Self.confirmWord) == .orderedSame
    }

    func clearError() {
        errorMessage = nil
    }

    /// True once the server has deleted the account; the caller then signs out. On failure the
    /// sheet stays open with a message.
    func delete() async -> Bool {
        guard canDelete else { return false }
        isDeleting = true
        errorMessage = nil
        defer { isDeleting = false }
        do {
            try await users.deleteAccount()
            return true
        } catch is CancellationError {
            return false
        } catch {
            errorMessage = String(localized: Strings.DeleteAccount.failed)
            Haptics.error()
            return false
        }
    }
}
