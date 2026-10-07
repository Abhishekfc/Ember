import Foundation
import Observation

/// One day's worth of activity, under a "Today" / "Yesterday" / "Sep 29" heading.
struct ActivityDay: Identifiable {
    let day: Date
    let events: [ActivityEvent]

    var id: Date { day }
}

@MainActor
@Observable
final class ActivityViewModel {
    private(set) var events: [ActivityEvent] = []
    private(set) var lastSeenAt: Date?
    /// True once the server has told us when Activity was last opened (even if the answer is
    /// "never"). Until then the bell shows nothing, rather than counting every event as new.
    private var knowsLastSeen = false
    private(set) var hasLoaded = false
    private(set) var loadFailed = false
    private var isFetching = false

    private let repository: ActivityRepository

    init(repository: ActivityRepository) {
        self.repository = repository
    }

    /// How many events arrived since Activity was last opened: the number on the bell.
    var newCount: Int {
        guard knowsLastSeen else { return 0 }
        let since = lastSeenAt ?? .distantPast
        return events.filter { $0.createdAt > since }.count
    }

    var showsEmptyState: Bool { hasLoaded && events.isEmpty }

    var days: [ActivityDay] {
        Self.groupedByDay(events)
    }

    func load(forceRefresh: Bool = false) async {
        guard !isFetching else { return }
        isFetching = true
        defer { isFetching = false }

        async let seen = fetchLastSeen()
        do {
            let page = try await repository.events(forceRefresh: forceRefresh)
            // Wait for both answers before changing anything the bell shows. Setting the events
            // first made every one of them count as new for a moment, so the badge popped up with a
            // big number and then vanished when the "last seen" time arrived.
            apply(lastSeen: await seen)
            events = page.items
            hasLoaded = true
            loadFailed = false
        } catch is CancellationError {
            return
        } catch {
            loadFailed = true
            // Still take the "last seen" answer if it arrived, so the bell stays right.
            apply(lastSeen: await seen)
        }
    }

    /// The server's answer: a time, or no time at all (never opened). A failure is its own case,
    /// so "couldn't find out" is never mistaken for "never opened".
    private func fetchLastSeen() async -> Result<Date?, Error> {
        do {
            return .success(try await repository.lastSeenAt())
        } catch {
            return .failure(error)
        }
    }

    private func apply(lastSeen result: Result<Date?, Error>) {
        guard case .success(let date) = result else { return }
        lastSeenAt = date
        knowsLastSeen = true
    }

    /// Called when the person opens Activity: everything counts as seen from now on.
    func markSeen() async {
        lastSeenAt = Date()
        knowsLastSeen = true
        await repository.markSeen()
    }

    /// Newest day first, newest event first within a day.
    nonisolated static func groupedByDay(_ events: [ActivityEvent], calendar: Calendar = .current) -> [ActivityDay] {
        Dictionary(grouping: events) { calendar.startOfDay(for: $0.createdAt) }
            .map { ActivityDay(day: $0.key, events: $0.value.sorted { $0.createdAt > $1.createdAt }) }
            .sorted { $0.day > $1.day }
    }
}
