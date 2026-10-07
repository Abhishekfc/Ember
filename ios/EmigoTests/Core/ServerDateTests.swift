import XCTest
@testable import Emigo

final class ServerDateTests: XCTestCase {
    func testParsesWholeSeconds() throws {
        let date = try XCTUnwrap(ServerDate.parse("2026-10-06T02:40:11Z"))
        XCTAssertEqual(date.timeIntervalSince1970, 1_790_000_000 + 0, accuracy: 100_000_000) // sanity range only
        XCTAssertEqual(ServerDate.string(from: date), "2026-10-06T02:40:11Z")
    }

    func testParsesMillisecondsMicrosecondsAndNanoseconds() throws {
        let base = try XCTUnwrap(ServerDate.parse("2026-10-06T02:40:11Z"))
        for text in ["2026-10-06T02:40:11.5Z", "2026-10-06T02:40:11.605Z", "2026-10-06T02:40:11.605123Z", "2026-10-06T02:40:11.605123456Z"] {
            let parsed = try XCTUnwrap(ServerDate.parse(text), text)
            XCTAssertEqual(parsed.timeIntervalSince(base), 0.5, accuracy: 0.2, text)
        }
    }

    func testRejectsGarbage() {
        XCTAssertNil(ServerDate.parse("yesterday"))
        XCTAssertNil(ServerDate.parse(""))
    }

    func testDecodesFeedTheWayTheServerSendsIt() throws {
        let json = """
        [{"friendId":"f1","displayName":"Aditya","streak":3,"photos":[
          {"photoId":"p1","photoUrl":"https://cdn.example/p1.jpg","createdAt":"2026-10-06T02:40:11.605123Z","seen":false}]}]
        """
        let items = try JSONDecoder.emigo.decode([FeedItem].self, from: Data(json.utf8))
        XCTAssertEqual(items.count, 1)
        XCTAssertEqual(items[0].photos[0].photoId, "p1")
        XCTAssertTrue(items[0].hasUnseenPhoto)
    }

    func testProfileDefaultsMissingVerificationFlagToFalse() throws {
        let json = #"{"userId":"u","displayName":"A","username":"a","email":"a@b.co","profilePhotoUrl":null}"#
        let profile = try JSONDecoder.emigo.decode(UserProfile.self, from: Data(json.utf8))
        XCTAssertFalse(profile.emailVerificationRequired)
        XCTAssertNil(profile.createdAt)
    }

    func testUnknownActivityTypeDoesNotBreakTheList() throws {
        let json = """
        {"items":[{"type":"SOMETHING_NEW","actorId":"a","actorDisplayName":"A","message":"hi","createdAt":"2026-10-06T02:40:11Z"}],"hasMore":false}
        """
        let page = try JSONDecoder.emigo.decode(Page<ActivityEvent>.self, from: Data(json.utf8))
        XCTAssertEqual(page.items.first?.type, .unknown)
    }
}
