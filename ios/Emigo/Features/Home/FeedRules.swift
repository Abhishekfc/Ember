import Foundation

/// How long a friend's earlier photo stays visible after a newer one replaces it.
let photoGracePeriod: TimeInterval = 24 * 60 * 60

/// Drops photos that have outlived their time on Home. A friend's newest photo always stays; an
/// older one stays only until 24 hours after the photo that replaced it arrived. Friends left with
/// nothing are dropped. Port of Android's `dropExpiredPhotos`.
func droppingExpiredPhotos(_ items: [FeedItem], now: Date = Date()) -> [FeedItem] {
    let cutoff = now.addingTimeInterval(-photoGracePeriod)
    return items.compactMap { item in
        let photos = item.photos
        let fresh = photos.enumerated().compactMap { index, photo -> PhotoEntry? in
            if index == photos.count - 1 { return photo }
            return photos[index + 1].createdAt > cutoff ? photo : nil
        }
        guard !fresh.isEmpty else { return nil }
        var copy = item
        copy.photos = fresh
        return copy
    }
}

/// One card in Home's deck: a single photo, plus where it sits among that friend's photos.
struct HomeCarouselEntry: Identifiable, Equatable {
    let friendId: String
    let displayName: String
    let streak: Int
    let photo: PhotoEntry
    /// True for the friend's most recent photo; older ones are on a 24-hour countdown.
    let isFriendsNewest: Bool
    let indexWithinFriend: Int
    let totalForFriend: Int

    var id: String { photo.photoId }
}

/// Flattens the feed into one deck: friends in feed order, each friend's photos newest first.
/// Swiping the card walks this deck, so the friend row can follow along.
func buildHomeCarousel(_ items: [FeedItem]) -> [HomeCarouselEntry] {
    items.flatMap { item -> [HomeCarouselEntry] in
        let newestFirst = item.photos.reversed()
        return newestFirst.enumerated().map { index, photo in
            HomeCarouselEntry(
                friendId: item.friendId,
                displayName: item.displayName,
                streak: item.streak,
                photo: photo,
                isFriendsNewest: index == 0,
                indexWithinFriend: index,
                totalForFriend: newestFirst.count
            )
        }
    }
}

/// When a photo that has been replaced disappears: 24 hours after the photo that replaced it.
/// Nil for a friend's newest photo.
func expiryDate(of entry: HomeCarouselEntry, in entries: [HomeCarouselEntry]) -> Date? {
    guard !entry.isFriendsNewest else { return nil }
    let successor = entries.first { $0.friendId == entry.friendId && $0.indexWithinFriend == entry.indexWithinFriend - 1 }
    return successor?.photo.createdAt.addingTimeInterval(photoGracePeriod)
}
