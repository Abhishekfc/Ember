import XCTest
@testable import Emigo

final class FeedRulesTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func photo(_ id: String, hoursAgo: Double, seen: Bool = false) -> PhotoEntry {
        PhotoEntry(photoId: id, photoUrl: "https://cdn.example/\(id).jpg", createdAt: now.addingTimeInterval(-hoursAgo * 3600), seen: seen)
    }

    private func item(_ photos: [PhotoEntry], id: String = "f", streak: Int = 1) -> FeedItem {
        FeedItem(friendId: id, displayName: "Friend \(id)", photos: photos, streak: streak)
    }

    // MARK: Expiry

    func testAnOlderPhotoStaysFor24HoursAfterItIsReplaced() {
        // The newer photo arrived 1 hour ago, so the older one has 23 hours left.
        let result = droppingExpiredPhotos([item([photo("old", hoursAgo: 72), photo("new", hoursAgo: 1)])], now: now)
        XCTAssertEqual(result.first?.photos.map(\.photoId), ["old", "new"])
    }

    func testAnOlderPhotoGoesOnceItsReplacementIsMoreThan24HoursOld() {
        let result = droppingExpiredPhotos([item([photo("old", hoursAgo: 72), photo("new", hoursAgo: 30)])], now: now)
        XCTAssertEqual(result.first?.photos.map(\.photoId), ["new"])
    }

    func testTheNewestPhotoNeverExpires() {
        let result = droppingExpiredPhotos([item([photo("only", hoursAgo: 24 * 40)])], now: now)
        XCTAssertEqual(result.first?.photos.map(\.photoId), ["only"])
    }

    func testAFriendWithNoPhotosIsDropped() {
        XCTAssertTrue(droppingExpiredPhotos([item([])], now: now).isEmpty)
    }

    // MARK: The deck

    func testTheDeckWalksFriendsInOrderEachNewestFirst() {
        let deck = buildHomeCarousel([
            item([photo("a1", hoursAgo: 5), photo("a2", hoursAgo: 3), photo("a3", hoursAgo: 1)], id: "a"),
            item([photo("b1", hoursAgo: 2)], id: "b"),
        ])
        XCTAssertEqual(deck.map(\.photo.photoId), ["a3", "a2", "a1", "b1"])
        XCTAssertEqual(deck.map(\.friendId), ["a", "a", "a", "b"])
        XCTAssertEqual(deck.map(\.indexWithinFriend), [0, 1, 2, 0])
        XCTAssertEqual(deck.map(\.totalForFriend), [3, 3, 3, 1])
        XCTAssertEqual(deck.map(\.isFriendsNewest), [true, false, false, true])
    }

    func testAnOlderPhotoExpires24HoursAfterTheOneThatReplacedIt() throws {
        let deck = buildHomeCarousel([item([photo("old", hoursAgo: 10), photo("new", hoursAgo: 2)], id: "a")])
        let old = try XCTUnwrap(deck.first { $0.photo.photoId == "old" })
        let new = try XCTUnwrap(deck.first { $0.photo.photoId == "new" })

        XCTAssertNil(expiryDate(of: new, in: deck))
        let expiry = try XCTUnwrap(expiryDate(of: old, in: deck))
        XCTAssertEqual(expiry.timeIntervalSince(new.photo.createdAt), photoGracePeriod, accuracy: 0.001)
    }

    func testRemainingTimeNeverGoesNegative() {
        XCTAssertEqual(RelativeTime.remaining(until: now.addingTimeInterval(-60), now: now), "expiring")
        XCTAssertEqual(RelativeTime.remaining(until: now.addingTimeInterval(45 * 60), now: now), "45m left")
        XCTAssertEqual(RelativeTime.remaining(until: now.addingTimeInterval(18 * 3600 + 120), now: now), "18h left")
    }
}

final class MemoriesGroupingTests: XCTestCase {
    private func memory(_ id: String, _ iso: String) -> MemoryPhoto {
        MemoryPhoto(photoId: id, photoUrl: "https://cdn.example/\(id).jpg", createdAt: ServerDate.parse(iso)!)
    }

    func testGroupsByMonthNewestFirst() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let months = MemoriesViewModel.groupedByMonth([
            memory("aug", "2026-08-30T12:00:00Z"),
            memory("sep1", "2026-09-03T12:00:00Z"),
            memory("sep2", "2026-09-22T12:00:00Z"),
        ], calendar: calendar)

        XCTAssertEqual(months.count, 2)
        XCTAssertEqual(months[0].photos.map(\.photoId), ["sep2", "sep1"])
        XCTAssertEqual(months[1].photos.map(\.photoId), ["aug"])
        XCTAssertGreaterThan(months[0].start, months[1].start)
    }

    func testNoPhotosMeansNoMonths() {
        XCTAssertTrue(MemoriesViewModel.groupedByMonth([]).isEmpty)
    }
}

final class ActivityGroupingTests: XCTestCase {
    private func event(_ iso: String) throws -> ActivityEvent {
        let json = """
        {"type":"PHOTO_RECEIVED","actorId":"a","actorDisplayName":"A","message":"A sent you a photo","createdAt":"\(iso)"}
        """
        return try JSONDecoder.emigo.decode(ActivityEvent.self, from: Data(json.utf8))
    }

    func testGroupsByDayNewestFirst() throws {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        let days = ActivityViewModel.groupedByDay([
            try event("2026-09-29T09:00:00Z"),
            try event("2026-10-01T09:00:00Z"),
            try event("2026-10-01T18:00:00Z"),
        ], calendar: calendar)

        XCTAssertEqual(days.count, 2)
        XCTAssertEqual(days[0].events.count, 2)
        XCTAssertGreaterThan(days[0].events[0].createdAt, days[0].events[1].createdAt)
        XCTAssertGreaterThan(days[0].day, days[1].day)
    }
}
