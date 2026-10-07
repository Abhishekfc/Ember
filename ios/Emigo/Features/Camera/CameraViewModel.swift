import Foundation
import Observation
import UIKit

/// What the Sent button in the camera header is showing.
enum SendAnimState: Equatable {
    case idle
    /// A photo is on its way: the outline travels around the button.
    case sending
    /// It arrived: the button fills with a tick, then goes back to normal.
    case complete
}

private let sendCompleteHold: Duration = .milliseconds(1200)

/// Everything behind the Camera tab: who a photo goes to, the photo just taken, its caption, and
/// handing it to the send queue. Port of Android's `CameraViewModel`.
@MainActor
@Observable
final class CameraViewModel {
    let engine = CameraEngine()

    // Who it goes to
    private(set) var friends: [FriendSummary] = []
    private(set) var selectedRecipientIds: [String] = []

    // The photo just taken or picked
    private(set) var captured: CapturedPhoto?
    /// The photo as it will be sent (upright, cropped to 4 : 5), for showing in the card.
    private(set) var previewImage: UIImage?
    var captionText = ""
    private(set) var isSaved = false
    private(set) var isSavingToMemories = false
    private(set) var isQueuingSend = false
    /// True from the moment the shutter is pressed until the photo is on screen. The live picture
    /// stays still for that whole time, so the photo seems to be taken at once.
    var isTakingPhoto = false

    // The Sent button
    private(set) var sendAnimState: SendAnimState = .idle
    private(set) var lastSentPhotoURL: URL?

    // Gold
    private(set) var isGoldMember: Bool
    var showGoldUpsell = false

    private(set) var errorMessage: String?

    /// The queued upload that already carries "save to Memories", if the person tapped save first.
    /// Sending afterwards adds the recipients to that same upload instead of uploading twice.
    private var savedJobId: UUID?
    private var sendJobId: UUID?

    private let friendRepository: FriendRepository
    private let photoRepository: PhotoRepository
    private let subscription: SubscriptionRepository
    private let queue: PendingSendQueue
    private let store: SessionStore

    init(
        friends: FriendRepository,
        photos: PhotoRepository,
        subscription: SubscriptionRepository,
        queue: PendingSendQueue,
        store: SessionStore
    ) {
        friendRepository = friends
        photoRepository = photos
        self.subscription = subscription
        self.queue = queue
        self.store = store
        isGoldMember = subscription.isGoldMemberCached
    }

    // MARK: - Reading

    /// The chosen friends, in the order of the friends list.
    var selectedFriends: [FriendSummary] {
        let chosen = Set(selectedRecipientIds)
        return friends.filter { chosen.contains($0.friendId) }
    }

    var hasPinnedSelected: Bool { selectedFriends.contains { $0.pinnedByMe } }
    var hasRecipients: Bool { !selectedRecipientIds.isEmpty }
    var isReviewing: Bool { captured != nil && previewImage != nil }

    // MARK: - Loading

    /// Loads friends (and the Gold answer) when the camera comes up.
    func refresh() async {
        async let gold = subscription.refreshIsGoldMember()
        if let page = try? await friendRepository.friends(limit: allFriendsLimit) {
            applyFriends(page.items)
        }
        isGoldMember = await gold
        await refreshLastSentPhoto()
    }

    /// The friends the app already knows, such as Home's list.
    func provideFriends(_ list: [FriendSummary]) {
        if friends.isEmpty { applyFriends(list) }
    }

    private func applyFriends(_ list: [FriendSummary]) {
        friends = list
        if selectedRecipientIds.isEmpty {
            // Whoever is pinned, otherwise whoever the last photo went to.
            let pinned = list.filter(\.pinnedByMe).map(\.friendId)
            let known = Set(list.map(\.friendId))
            selectedRecipientIds = pinned.isEmpty ? store.lastRecipientIds.filter { known.contains($0) } : pinned
        } else {
            let known = Set(list.map(\.friendId))
            selectedRecipientIds = selectedRecipientIds.filter { known.contains($0) }
        }
    }

    func setSelectedRecipients(_ ids: [String]) {
        selectedRecipientIds = ids
    }

    func refreshLastSentPhoto() async {
        if let sent = try? await photoRepository.sentPhotos() {
            lastSentPhotoURL = sent.first?.url
        }
    }

    // MARK: - Gallery (a Gold perk)

    func galleryTapped(open picker: () -> Void) {
        if isGoldMember { picker() } else { showGoldUpsell = true }
    }

    // MARK: - Capture

    func didCapture(_ photo: CapturedPhoto) async {
        let data = photo.jpeg
        let mirrored = photo.isFrontCamera
        let image = await Task.detached(priority: .userInitiated) {
            PhotoBaker.preparedImage(from: data, mirrored: mirrored, maxPixelSide: 1600)
        }.value
        guard let image else {
            errorMessage = String(localized: Strings.Camera.captureFailed)
            return
        }
        resetCapture()
        captured = photo
        previewImage = image
        errorMessage = nil
    }

    func captureFailed() {
        errorMessage = String(localized: Strings.Camera.captureFailed)
    }

    func dismissError() {
        errorMessage = nil
    }

    func discardCapture() {
        resetCapture()
    }

    private func resetCapture() {
        captured = nil
        previewImage = nil
        captionText = ""
        isSaved = false
        isSavingToMemories = false
        savedJobId = nil
    }

    // MARK: - Send and save

    func sendCaptured() async {
        guard !isQueuingSend, let captured else { return }
        guard hasRecipients else {
            errorMessage = String(localized: Strings.Camera.errorSelectFriend)
            return
        }
        isQueuingSend = true
        errorMessage = nil
        sendAnimState = .sending
        let recipients = selectedRecipientIds

        if let parent = savedJobId {
            sendJobId = queue.enqueueAddRecipients(after: parent, recipientIds: recipients)
        } else {
            guard let jpeg = await baked(captured) else {
                failQueuing(String(localized: Strings.Camera.errorQueue))
                return
            }
            do {
                sendJobId = try queue.enqueueUpload(jpeg: jpeg, recipientIds: recipients, save: false)
            } catch {
                failQueuing(String(localized: Strings.Camera.errorQueue))
                return
            }
        }
        store.lastRecipientIds = recipients
        resetCapture()
        isQueuingSend = false
        Haptics.success()
    }

    func saveToMemories() async {
        guard !isSaved, !isSavingToMemories, let captured else { return }
        isSavingToMemories = true
        errorMessage = nil
        guard let jpeg = await baked(captured) else {
            errorMessage = String(localized: Strings.Camera.errorSave)
            isSavingToMemories = false
            return
        }
        do {
            savedJobId = try queue.enqueueUpload(jpeg: jpeg, recipientIds: [], save: true)
            isSaved = true
            Haptics.success()
        } catch {
            errorMessage = String(localized: Strings.Camera.errorSave)
        }
        isSavingToMemories = false
    }

    private func baked(_ photo: CapturedPhoto) async -> Data? {
        let data = photo.jpeg
        let caption = captionText
        let mirrored = photo.isFrontCamera
        return await Task.detached(priority: .userInitiated) {
            PhotoBaker.bakedJPEG(from: data, caption: caption, mirrored: mirrored)
        }.value
    }

    private func failQueuing(_ message: String) {
        errorMessage = message
        isQueuingSend = false
        sendAnimState = .idle
    }

    // MARK: - After sending

    /// The send queue finished a job; if it was this photo's upload, show the tick on Sent.
    func jobFinished(_ id: UUID) {
        guard id == sendJobId, sendAnimState == .sending else { return }
        sendAnimState = .complete
        Task {
            await refreshLastSentPhoto()
            try? await Task.sleep(for: sendCompleteHold)
            sendAnimState = .idle
        }
    }
}
