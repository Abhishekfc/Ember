import Foundation

/// Blocking and reporting people.
@MainActor
final class SafetyRepository {
    private let api: APIClient

    init(api: APIClient) {
        self.api = api
    }

    func blockedUsers() async throws -> [BlockedUser] {
        try await api.send(.blockedUsers())
    }

    func unblock(userId: String) async throws {
        _ = try await api.send(.unblockUser(userId))
    }

    /// Blocks someone: they can't find you, ask to be friends, or send you photos. They aren't told.
    func block(userId: String) async throws {
        _ = try await api.send(.blockUser(userId))
    }

    func report(userId: String, reason: ReportReason, details: String? = nil) async throws {
        _ = try await api.send(.reportUser(userId, reason: reason, details: details))
    }
}
