import XCTest
@testable import Emigo

final class EmailValidatorTests: XCTestCase {
    func testAcceptsNormalAddresses() {
        for email in ["a@b.co", "first.last+tag@example.com", " spaced@example.org "] {
            XCTAssertTrue(EmailValidator.isValid(email), email)
        }
    }

    func testRejectsMalformedAddresses() {
        for email in ["", "plain", "a@b", "@b.com", "a@.com", "a b@c.com"] {
            XCTAssertFalse(EmailValidator.isValid(email), email)
        }
    }
}

final class RelativeTimeTests: XCTestCase {
    private let now = Date(timeIntervalSince1970: 1_800_000_000)

    private func text(secondsAgo: TimeInterval) -> String {
        RelativeTime.short(since: now.addingTimeInterval(-secondsAgo), now: now)
    }

    func testWording() {
        XCTAssertEqual(text(secondsAgo: 20), "just now")
        XCTAssertEqual(text(secondsAgo: 5 * 60), "5m ago")
        XCTAssertEqual(text(secondsAgo: 59 * 60), "59m ago")
        XCTAssertEqual(text(secondsAgo: 60 * 60), "1h ago")
        XCTAssertEqual(text(secondsAgo: 23 * 3600), "23h ago")
        XCTAssertEqual(text(secondsAgo: 30 * 3600), "yesterday")
        XCTAssertEqual(text(secondsAgo: 3 * 86400), "3d ago")
    }

    func testAFutureDateReadsAsJustNow() {
        XCTAssertEqual(text(secondsAgo: -300), "just now")
    }
}

final class ArraySafeSubscriptTests: XCTestCase {
    func testOutOfRangeIsNil() {
        XCTAssertEqual([1, 2, 3][safe: 1], 2)
        XCTAssertNil([1, 2, 3][safe: 3])
        XCTAssertNil([Int]()[safe: 0])
        XCTAssertNil([1][safe: -1])
    }
}
