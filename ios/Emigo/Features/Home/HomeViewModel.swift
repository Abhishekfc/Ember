import Foundation
import Observation

enum HomeMode: Hashable {
    case home
    case moments
}

@MainActor
@Observable
final class HomeViewModel {
    private(set) var feedItems: [FeedItem] = []
    /// The deck the big card swipes through (see `buildHomeCarousel`).
    private(set) var entries: [HomeCarouselEntry] = []
    private(set) var profile: UserProfile?
    private(set) var isLoading = true
    private(set) var loadFailed = false
    private(set) var hasLoadedOnce = false

    var mode: HomeMode = .home
    /// Which card of `entries` is showing.
    private(set) var currentIndex = 0
    /// Which of a friend's photos was last showing, so tapping them again returns to it.
    private var photoOffsetByFriend: [String: Int] = [:]
    private var friendPhotoURLs: [String: URL] = [:]
    private var hasAnyFriends = false
    private var isFetching = false

    private let photos: PhotoRepository
    private let users: UserRepository
    private let friends: FriendRepository

    init(photos: PhotoRepository, users: UserRepository, friends: FriendRepository) {
        self.photos = photos
        self.users = users
        self.friends = friends
        #if DEBUG
        // `-EmigoHomeMode moments` opens Home on the Moments grid, for screenshots.
        if UserDefaults.standard.string(forKey: "EmigoHomeMode") == "moments" { mode = .moments }
        #endif
    }

    // MARK: - Reading

    var currentEntry: HomeCarouselEntry? { entries[safe: currentIndex] }
    var activeFriendId: String? { currentEntry?.friendId }
    var hasFriends: Bool { hasAnyFriends }
    var showsEmptyState: Bool { hasLoadedOnce && entries.isEmpty }
    var showsConnectError: Bool { loadFailed && entries.isEmpty }
    var showsSkeleton: Bool { !hasLoadedOnce && !loadFailed }

    func profilePhotoURL(for friendId: String) -> URL? {
        friendPhotoURLs[friendId]
    }

    func hasUnseenPhoto(friendId: String) -> Bool {
        feedItems.first { $0.friendId == friendId }?.hasUnseenPhoto ?? false
    }

    /// Every photo in the feed, newest first, for the Moments grid.
    var moments: [HomeCarouselEntry] {
        entries.sorted { $0.photo.createdAt > $1.photo.createdAt }
    }

    // MARK: - Actions

    /// The profile was edited elsewhere (name, username or picture); keep Home in step.
    func applyProfile(_ updated: UserProfile) {
        profile = updated
    }

    /// Jumps the deck to a friend, at the photo of theirs that was last showing.
    func select(friendId: String) {
        let theirs = entries.indices.filter { entries[$0].friendId == friendId }
        guard let first = theirs.first else { return }
        let offset = min(photoOffsetByFriend[friendId] ?? 0, theirs.count - 1)
        show(index: first + offset)
    }

    /// Called as the card is swiped.
    func pageChanged(to index: Int) {
        show(index: index)
    }

    /// Opens one photo from the Moments grid on Home.
    func showOnHome(photoId: String) {
        guard let index = entries.firstIndex(where: { $0.photo.photoId == photoId }) else { return }
        mode = .home
        show(index: index)
    }

    func load(forceRefresh: Bool = false) async {
        guard !isFetching else { return }
        isFetching = true
        isLoading = true
        defer {
            isFetching = false
            isLoading = false
        }

        // The profile and friend list only decorate the feed, so a failure there never fails Home.
        async let friendsPage = try? friends.friends(forceRefresh: forceRefresh, limit: allFriendsLimit)
        async let myProfile = try? users.myProfile(forceRefresh: forceRefresh)

        do {
            apply(feed: droppingExpiredPhotos(try await photos.feed(forceRefresh: forceRefresh)))
            loadFailed = false
        } catch is CancellationError {
            return
        } catch {
            loadFailed = true
        }

        if let page = await friendsPage {
            hasAnyFriends = !page.items.isEmpty
            friendPhotoURLs = Dictionary(
                page.items.compactMap { friend in
                    friend.profilePhotoUrl.flatMap { URL(string: $0) }.map { (friend.friendId, $0) }
                },
                uniquingKeysWith: { first, _ in first }
            )
        }
        if let me = await myProfile { profile = me }
        hasLoadedOnce = hasLoadedOnce || !loadFailed
    }

    // MARK: - Internals

    private func apply(feed items: [FeedItem]) {
        let previousFriend = activeFriendId
        feedItems = items
        entries = buildHomeCarousel(items)
        // Stay on the same friend if they're still there, else go back to the first card.
        if let previousFriend, entries.contains(where: { $0.friendId == previousFriend }) {
            select(friendId: previousFriend)
        } else {
            show(index: 0)
        }
    }

    private func show(index: Int) {
        guard !entries.isEmpty else {
            currentIndex = 0
            return
        }
        currentIndex = min(max(index, 0), entries.count - 1)
        let entry = entries[currentIndex]
        photoOffsetByFriend[entry.friendId] = entry.indexWithinFriend
        markSeen(entry)
    }

    /// Marks a photo seen on screen at once, then tells the server in the background.
    private func markSeen(_ entry: HomeCarouselEntry) {
        guard let itemIndex = feedItems.firstIndex(where: { $0.friendId == entry.friendId }),
              let photoIndex = feedItems[itemIndex].photos.firstIndex(where: { $0.photoId == entry.photo.photoId }),
              !feedItems[itemIndex].photos[photoIndex].seen else { return }
        feedItems[itemIndex].photos[photoIndex].seen = true
        entries = buildHomeCarousel(feedItems)
        let photos = photos
        let photoId = entry.photo.photoId
        Task { await photos.markSeen(photoId) }
    }
}

extension Array {
    subscript(safe index: Int) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}
