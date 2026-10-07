import Foundation
import Observation

/// A month of saved photos, as one section of the Memories grid.
struct MemoryMonth: Identifiable {
    let start: Date
    let photos: [MemoryPhoto]

    var id: Date { start }
}

/// How far back Memories looks when the account's creation date isn't known.
private let memoriesFallbackYears = 5

@MainActor
@Observable
final class MemoriesViewModel {
    private(set) var months: [MemoryMonth] = []
    private(set) var hasLoaded = false
    private(set) var loadFailed = false
    private var isFetching = false

    private let photos: PhotoRepository
    private let users: UserRepository

    init(photos: PhotoRepository, users: UserRepository) {
        self.photos = photos
        self.users = users
    }

    var showsEmptyState: Bool { hasLoaded && months.isEmpty }

    func load() async {
        guard !isFetching else { return }
        isFetching = true
        defer { isFetching = false }

        let calendar = Calendar.current
        let now = Date()
        let createdAt = try? await users.myProfile().createdAt
        let earliest = calendar.date(byAdding: .year, value: -memoriesFallbackYears, to: now) ?? now
        let start = calendar.dateInterval(of: .month, for: createdAt ?? earliest)?.start ?? earliest

        do {
            let memories = try await photos.memories(from: start, to: now)
            months = Self.groupedByMonth(memories, calendar: calendar)
            hasLoaded = true
            loadFailed = false
        } catch is CancellationError {
            return
        } catch {
            loadFailed = true
        }
    }

    func delete(_ photo: MemoryPhoto) async -> Bool {
        do {
            try await photos.delete(photo.photoId)
            months = months
                .map { MemoryMonth(start: $0.start, photos: $0.photos.filter { $0.photoId != photo.photoId }) }
                .filter { !$0.photos.isEmpty }
            return true
        } catch {
            return false
        }
    }

    /// Newest month first, newest photo first within each month.
    nonisolated static func groupedByMonth(_ memories: [MemoryPhoto], calendar: Calendar = .current) -> [MemoryMonth] {
        let grouped = Dictionary(grouping: memories) { photo in
            calendar.dateInterval(of: .month, for: photo.createdAt)?.start ?? photo.createdAt
        }
        return grouped
            .map { MemoryMonth(start: $0.key, photos: $0.value.sorted { $0.createdAt > $1.createdAt }) }
            .sorted { $0.start > $1.start }
    }
}
