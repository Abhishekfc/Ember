import Foundation
import Observation

@MainActor
@Observable
final class BlockedAccountsViewModel {
    private(set) var blocked: [BlockedUser] = []
    private(set) var hasLoaded = false
    private(set) var errorMessage: String?

    private let repository: SafetyRepository

    init(repository: SafetyRepository) {
        self.repository = repository
    }

    var showsEmptyState: Bool { hasLoaded && blocked.isEmpty }

    func load() async {
        do {
            blocked = try await repository.blockedUsers()
            hasLoaded = true
        } catch is CancellationError {
            return
        } catch {
            errorMessage = error.userMessage
        }
    }

    func unblock(_ user: BlockedUser) async {
        do {
            try await repository.unblock(userId: user.userId)
            blocked.removeAll { $0.userId == user.userId }
            Haptics.success()
        } catch is CancellationError {
            return
        } catch {
            errorMessage = String(localized: Strings.Failure.unblock)
            Haptics.error()
        }
    }
}
