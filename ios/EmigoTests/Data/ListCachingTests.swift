import XCTest
@testable import Emigo

private final class TokenStub: AccessTokenProviding {
    func idToken() async -> String? { "token" }
}

/// Counts what reaches the (stubbed) server. Written from the URL loading thread, read from tests.
private final class RequestLog: @unchecked Sendable {
    private let lock = NSLock()
    private var entries: [String] = []

    func record(_ request: URLRequest) {
        lock.lock()
        entries.append("\(request.httpMethod ?? "GET") \(request.url?.path ?? "")")
        lock.unlock()
    }

    func count(of entry: String) -> Int {
        lock.lock()
        defer { lock.unlock() }
        return entries.filter { $0 == entry }.count
    }
}

@MainActor
final class TtlCacheTests: XCTestCase {
    func testAnAnswerIsKeptUntilItsTimeIsUp() {
        var clock = Date(timeIntervalSince1970: 1_000)
        let cache = TtlCache<String, Int>(ttl: 30, now: { clock })

        cache.store(7, for: "a")
        XCTAssertEqual(cache.value(for: "a"), 7)

        clock = clock.addingTimeInterval(29)
        XCTAssertEqual(cache.value(for: "a"), 7)

        clock = clock.addingTimeInterval(2)
        XCTAssertNil(cache.value(for: "a"), "31 seconds is past the 30-second limit")
    }

    func testEachKeyIsKeptSeparately() {
        let cache = TtlCache<String, Int>(ttl: 30)
        cache.store(1, for: "first page")
        XCTAssertNil(cache.value(for: "second page"))
    }

    func testRemoveAllForgetsEverything() {
        let cache = TtlCache<String, Int>(ttl: 30)
        cache.store(1, for: "a")
        cache.store(2, for: "b")
        cache.removeAll()
        XCTAssertNil(cache.value(for: "a"))
        XCTAssertNil(cache.value(for: "b"))
    }
}

@MainActor
final class SingleFlightTests: XCTestCase {
    func testRequestsAtTheSameMomentShareOneTrip() async throws {
        let flight = SingleFlight<String, Int>()
        var trips = 0
        let work: () async throws -> Int = {
            trips += 1
            try await Task.sleep(for: .milliseconds(150))
            return trips
        }

        async let first = flight.run("feed", work)
        async let second = flight.run("feed", work)
        let results = try await [first, second]

        XCTAssertEqual(trips, 1)
        XCTAssertEqual(results, [1, 1])
    }

    func testRequestsOneAfterAnotherEachMakeTheirOwnTrip() async throws {
        let flight = SingleFlight<String, Int>()
        var trips = 0
        let work: () async throws -> Int = {
            trips += 1
            return trips
        }

        _ = try await flight.run("feed", work)
        _ = try await flight.run("feed", work)

        XCTAssertEqual(trips, 2)
    }

    func testDifferentKeysDoNotShare() async throws {
        let flight = SingleFlight<String, Int>()
        var trips = 0
        let work: () async throws -> Int = {
            trips += 1
            try await Task.sleep(for: .milliseconds(100))
            return trips
        }

        async let a = flight.run("page 1", work)
        async let b = flight.run("page 2", work)
        _ = try await [a, b]

        XCTAssertEqual(trips, 2)
    }

    func testAFailureIsPassedOnAndNotRemembered() async throws {
        struct Boom: Error {}
        let flight = SingleFlight<String, Int>()
        var shouldFail = true

        do {
            _ = try await flight.run("feed") {
                if shouldFail { throw Boom() }
                return 1
            }
            XCTFail("should have thrown")
        } catch is Boom {}

        shouldFail = false
        let retried = try await flight.run("feed") { 1 }
        XCTAssertEqual(retried, 1)
    }
}

/// The repositories ask the (stubbed) server only when they have to.
@MainActor
final class RepositoryCachingTests: XCTestCase {
    private var log: RequestLog!
    private var api: APIClient!
    private var store: SessionStore!

    private let profileJSON = #"{"userId":"u1","displayName":"Ann Lee","username":"ann","email":"ann@example.com","profilePhotoUrl":null,"createdAt":"2026-10-06T10:00:00Z","emailVerificationRequired":false}"#
    private let renamedProfileJSON = #"{"userId":"u1","displayName":"Ann Smith","username":"ann","email":"ann@example.com","profilePhotoUrl":null,"createdAt":"2026-10-06T10:00:00Z","emailVerificationRequired":false}"#
    private let friendJSON = #"{"friendshipId":"fs-1","friendId":"f1","displayName":"Bo","username":"bo","profilePhotoUrl":null,"pinnedByMe":false,"pinnedByThem":false,"lastActivityAt":null,"lastActivityBySelf":null,"streak":0}"#

    override func setUp() async throws {
        let log = RequestLog()
        self.log = log
        let profile = profileJSON
        let renamed = renamedProfileJSON
        let friend = friendJSON
        StubURLProtocol.handler = { request in
            log.record(request)
            let call = "\(request.httpMethod ?? "GET") \(request.url?.path ?? "")"
            switch call {
            case "GET /photos/feed": return (200, Data("[]".utf8))
            case "GET /friends", "GET /activity": return (200, Data(#"{"items":[],"hasMore":false}"#.utf8))
            case "GET /users/me": return (200, Data(profile.utf8))
            case "PATCH /users/me": return (200, Data(renamed.utf8))
            case "POST /friends/accept": return (200, Data(friend.utf8))
            case "GET /subscription/status": return (200, Data(#"{"status":"FREE","plan":null,"expiresAt":null}"#.utf8))
            default: return (204, Data())
            }
        }
        api = APIClient(
            baseURL: URL(string: "https://api.example/")!,
            tokenProvider: TokenStub(),
            session: APIClient.makeSession(protocolClasses: [StubURLProtocol.self])
        )
        store = SessionStore(defaults: UserDefaults(suiteName: "RepositoryCachingTests-\(UUID().uuidString)")!)
    }

    override func tearDown() async throws {
        StubURLProtocol.handler = nil
    }

    // MARK: - Feed

    func testTheFeedIsAskedForOnceWithinTheCacheWindow() async throws {
        let photos = PhotoRepository(api: api)

        _ = try await photos.feed()
        _ = try await photos.feed()
        _ = try await photos.feed()

        XCTAssertEqual(log.count(of: "GET /photos/feed"), 1)
    }

    func testPullToRefreshAlwaysAsksTheServer() async throws {
        let photos = PhotoRepository(api: api)

        _ = try await photos.feed()
        _ = try await photos.feed(forceRefresh: true)
        XCTAssertEqual(log.count(of: "GET /photos/feed"), 2)

        _ = try await photos.feed()
        XCTAssertEqual(log.count(of: "GET /photos/feed"), 2, "the refresh refilled the cache")
    }

    func testTwoFeedRequestsAtTheSameMomentMakeOneTrip() async throws {
        let photos = PhotoRepository(api: api)

        async let first = photos.feed()
        async let second = photos.feed()
        _ = try await [first, second]

        XCTAssertEqual(log.count(of: "GET /photos/feed"), 1)
    }

    func testMarkingAPhotoSeenForgetsTheFeed() async throws {
        let photos = PhotoRepository(api: api)

        _ = try await photos.feed()
        await photos.markSeen("p1")
        _ = try await photos.feed()

        XCTAssertEqual(log.count(of: "GET /photos/feed"), 2, "otherwise the photo would come back as unseen")
    }

    func testSigningOutForgetsTheFeed() async throws {
        let photos = PhotoRepository(api: api)

        _ = try await photos.feed()
        photos.clearCache()
        _ = try await photos.feed()

        XCTAssertEqual(log.count(of: "GET /photos/feed"), 2)
    }

    // MARK: - Friends and activity

    func testEachPageOfFriendsIsRememberedSeparately() async throws {
        let friends = FriendRepository(api: api)

        _ = try await friends.friends(offset: 0, limit: 30)
        _ = try await friends.friends(offset: 30, limit: 30)
        XCTAssertEqual(log.count(of: "GET /friends"), 2)

        _ = try await friends.friends(offset: 0, limit: 30)
        _ = try await friends.friends(offset: 30, limit: 30)
        XCTAssertEqual(log.count(of: "GET /friends"), 2)
    }

    func testChangingWhoYourFriendsAreForgetsTheList() async throws {
        let friends = FriendRepository(api: api)

        _ = try await friends.friends()
        try await friends.accept(friendshipId: "fs-1")
        _ = try await friends.friends()

        XCTAssertEqual(log.count(of: "GET /friends"), 2)
    }

    func testActivityIsAskedForOnceWithinTheCacheWindow() async throws {
        let activity = ActivityRepository(api: api)

        _ = try await activity.events()
        _ = try await activity.events()
        XCTAssertEqual(log.count(of: "GET /activity"), 1)

        _ = try await activity.events(forceRefresh: true)
        XCTAssertEqual(log.count(of: "GET /activity"), 2)
    }

    // MARK: - Profile and Gold

    func testTheProfileIsAskedForOnceAndRenamingStoresTheNewOne() async throws {
        let users = UserRepository(api: api)

        let first = try await users.myProfile()
        _ = try await users.myProfile()
        XCTAssertEqual(log.count(of: "GET /users/me"), 1)
        XCTAssertEqual(first.displayName, "Ann Lee")

        _ = try await users.updateName("Ann Smith")
        let afterRename = try await users.myProfile()
        XCTAssertEqual(afterRename.displayName, "Ann Smith")
        XCTAssertEqual(log.count(of: "GET /users/me"), 1, "the rename already answered it")

        _ = try await users.myProfile(forceRefresh: true)
        XCTAssertEqual(log.count(of: "GET /users/me"), 2)
    }

    func testGoldStatusIsNotAskedAgainRightAway() async {
        let subscription = SubscriptionRepository(api: api, store: store)

        await subscription.refreshIsGoldMember()
        await subscription.refreshIsGoldMember()
        XCTAssertEqual(log.count(of: "GET /subscription/status"), 1)

        await subscription.refreshIsGoldMember(force: true)
        XCTAssertEqual(log.count(of: "GET /subscription/status"), 2)

        subscription.reset()
        await subscription.refreshIsGoldMember()
        XCTAssertEqual(log.count(of: "GET /subscription/status"), 3, "a new sign-in starts fresh")
    }
}
