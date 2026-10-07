import Foundation
import Observation

/// How long after sending a photo can still be taken back.
let unsendWindow: TimeInterval = 24 * 60 * 60

@MainActor
@Observable
final class SentPhotosViewModel {
    private(set) var photos: [SentPhoto] = []
    private(set) var isLoading = true
    private(set) var errorMessage: String?
    private(set) var unsendingPhotoId: String?

    private let repository: PhotoRepository

    init(repository: PhotoRepository) {
        self.repository = repository
    }

    var showsEmptyState: Bool { !isLoading && photos.isEmpty && errorMessage == nil }

    func load() async {
        do {
            photos = try await repository.sentPhotos()
            errorMessage = nil
        } catch is CancellationError {
            return
        } catch {
            errorMessage = String(localized: Strings.Failure.loadSent)
        }
        isLoading = false
    }

    /// Takes the photo back: it disappears from the friends' feeds too. Returns whether it worked.
    func unsend(_ photo: SentPhoto) async -> Bool {
        unsendingPhotoId = photo.photoId
        defer { unsendingPhotoId = nil }
        do {
            try await repository.delete(photo.photoId)
            photos.removeAll { $0.photoId == photo.photoId }
            Haptics.success()
            return true
        } catch {
            errorMessage = String(localized: Strings.Failure.unsend)
            Haptics.error()
            return false
        }
    }

    func dismissError() {
        errorMessage = nil
    }

    /// When a sent photo can no longer be taken back.
    nonisolated static func unsendDeadline(for photo: SentPhoto) -> Date {
        photo.createdAt.addingTimeInterval(unsendWindow)
    }
}
