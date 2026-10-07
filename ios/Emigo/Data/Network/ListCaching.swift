import Foundation

/// How long a list read (feed, friends, activity, your profile) is remembered on the phone. The
/// same as the server's own cache, so there is nothing fresher to gain by asking sooner.
let listCacheTTL: TimeInterval = 30

/// A small in-memory cache that forgets each answer after a fixed time. Used for the reads that
/// are asked for again and again with the same result, so a quick return to a screen doesn't send
/// the same request to the server again.
///
/// An answer is kept per exact request (for example per page of a list), so two different pages
/// are never mistaken for each other.
@MainActor
final class TtlCache<Key: Hashable, Value> {
    private struct Entry {
        let value: Value
        let expiresAt: Date
    }

    private var entries: [Key: Entry] = [:]
    private let ttl: TimeInterval
    private let now: () -> Date

    /// `now` is only replaced in tests, to move time forward without waiting.
    init(ttl: TimeInterval = listCacheTTL, now: @escaping () -> Date = Date.init) {
        self.ttl = ttl
        self.now = now
    }

    func value(for key: Key) -> Value? {
        guard let entry = entries[key] else { return nil }
        guard now() < entry.expiresAt else {
            entries[key] = nil
            return nil
        }
        return entry.value
    }

    func store(_ value: Value, for key: Key) {
        entries[key] = Entry(value: value, expiresAt: now().addingTimeInterval(ttl))
    }

    func removeAll() {
        entries.removeAll()
    }
}

/// Makes requests that arrive together share one trip to the server. While a request for a key is
/// running, anyone else asking for the same key waits for its answer instead of sending another.
/// The cache above only helps with requests that come one after another; this covers the ones that
/// come at the same moment.
@MainActor
final class SingleFlight<Key: Hashable, Value> {
    private var running: [Key: Task<Value, Error>] = [:]

    func run(_ key: Key, _ work: @escaping () async throws -> Value) async throws -> Value {
        if let existing = running[key] { return try await existing.value }
        let task = Task { try await work() }
        running[key] = task
        defer { running[key] = nil }
        return try await task.value
    }
}
