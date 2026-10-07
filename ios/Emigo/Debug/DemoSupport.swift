#if DEBUG
import Foundation
import UIKit

// Sample data for running the app without an account or a network. Compiled into Debug builds
// only. Launch with the `-EmigoDemo` argument to use it.

extension AppEnvironment {
    static func demo() -> AppEnvironment {
        let suite = "emigo.demo"
        let defaults = UserDefaults(suiteName: suite) ?? .standard
        defaults.removePersistentDomain(forName: suite)
        return AppEnvironment(
            identity: DemoIdentityProvider(),
            baseURL: URL(string: "https://demo.emigo.invalid/")!,
            sessionStore: SessionStore(defaults: defaults),
            urlSession: APIClient.makeSession(protocolClasses: [DemoURLProtocol.self]),
            imageLoader: ImageLoader(syntheticProvider: { DemoImages.image(for: $0) }),
            outboxDirectory: FileManager.default.temporaryDirectory.appendingPathComponent("EmigoDemoOutbox-\(UUID().uuidString)")
        )
    }
}

/// A signed-in stand-in. Signing out really does sign out, so the sign-out path can be seen.
@MainActor
final class DemoIdentityProvider: IdentityProvider {
    private var isSignedIn = true

    var hasSession: Bool { isSignedIn }
    var uid: String? { isSignedIn ? "demo-uid" : nil }
    var email: String? { isSignedIn ? "demo@emigo.live" : nil }
    var displayName: String? { isSignedIn ? "Abhishek" : nil }
    var isEmailVerified: Bool { true }

    func signIn(email: String, password: String) async throws { isSignedIn = true }
    func createAccount(email: String, password: String) async throws { isSignedIn = true }
    func signOut() throws { isSignedIn = false }
    func sendEmailVerification() async throws {}
    func sendPasswordReset(to email: String) async throws {}
    func changePassword(current: String, new: String) async throws {}
    func reload() async throws {}
    func refreshIDToken() async throws {}
    func idToken() async -> String? { isSignedIn ? "demo-token" : nil }
}

/// Answers the app's requests from `DemoData`, as if the server had.
final class DemoURLProtocol: URLProtocol {
    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        let (status, body) = DemoData.response(for: request)
        let response = HTTPURLResponse(
            url: request.url ?? URL(string: "https://demo.emigo.invalid/")!,
            statusCode: status,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

enum DemoData {
    static func response(for request: URLRequest) -> (Int, Data) {
        let path = request.url?.path ?? ""
        switch (request.httpMethod ?? "GET", path) {
        case ("GET", "/users/me"): return ok(profile)
        case ("GET", "/photos/feed"): return ok(feed)
        case ("GET", "/friends"): return ok(["items": friends, "hasMore": false])
        case ("GET", "/friends/pending"): return ok(pendingRequests)
        case ("GET", "/friends/search"): return ok(searchResults)
        case ("GET", "/photos/memories"): return ok(memories)
        case ("GET", "/activity"): return ok(["items": activity, "hasMore": false])
        case ("GET", "/activity/seen"), ("POST", "/activity/seen"): return ok(["lastSeenAt": iso(minutesAgo: 60 * 20)])
        case ("GET", "/users/blocked"): return ok(blockedUsers)
        case ("GET", "/photos/sent"): return ok(sentPhotos)
        case ("POST", "/photos"):
            return ok(["photoId": "demo-\(UUID().uuidString)", "url": "demo://photo/9", "createdAt": iso(minutesAgo: 0), "recipientIds": [String](), "saved": false])
        case ("GET", "/subscription/status"): return ok(["status": "ACTIVE", "plan": "GOLD"])
        case ("GET", "/recipient-lists"): return ok([Any]())
        case ("POST", _) where path.hasSuffix("/seen"): return (204, Data())
        default: return (404, Data(#"{"status":404,"error":"Not Found","message":"No demo data"}"#.utf8))
        }
    }

    private static func ok(_ object: Any) -> (Int, Data) {
        (200, (try? JSONSerialization.data(withJSONObject: object)) ?? Data())
    }

    private static func iso(minutesAgo: Double) -> String {
        ServerDate.string(from: Date().addingTimeInterval(-minutesAgo * 60))
    }

    private static let profile: [String: Any] = [
        "userId": "demo-me",
        "displayName": "Abhishek",
        "username": "abhishek",
        "email": "demo@emigo.live",
        "profilePhotoUrl": "demo://avatar/me",
        "createdAt": iso(minutesAgo: 60 * 24 * 120),
        "emailVerificationRequired": false,
    ]

    private static let people: [(id: String, name: String, username: String, streak: Int)] = [
        ("f-bhoomi", "Bhoomiza Sharma", "hey_bh00mi", 12),
        ("f-aditya", "Aditya Rao", "aditya", 3),
        ("f-margot", "Margot Lin", "margot", 41),
        ("f-brandie", "Brandie Cole", "brandie", 0),
        ("f-prashant", "Prashant Sharma", "prashant", 7),
    ]

    private static var feed: [[String: Any]] {
        let photoCounts = [3, 1, 2, 1, 1]
        return people.enumerated().map { index, person in
            // Oldest first, as the server sends them.
            let photos: [[String: Any]] = (0..<photoCounts[index]).map { photoIndex in
                let age = Double(48 + (photoCounts[index] - 1 - photoIndex) * 190 + index * 35)
                return [
                    "photoId": "p-\(person.id)-\(photoIndex)",
                    "photoUrl": "demo://photo/\(index * 7 + photoIndex)",
                    "createdAt": iso(minutesAgo: age),
                    "seen": index >= 3,
                ]
            }
            return ["friendId": person.id, "displayName": person.name, "photos": photos, "streak": person.streak]
        }
    }

    private static var friends: [[String: Any]] {
        people.enumerated().map { index, person in
            var friend: [String: Any] = [
                "friendshipId": "fs-\(person.id)",
                "friendId": person.id,
                "displayName": person.name,
                "username": person.username,
                "profilePhotoUrl": "demo://avatar/\(index)",
                "pinnedByMe": index == 2,
                "pinnedByThem": false,
                "lastActivityBySelf": index % 2 == 1,
                "streak": person.streak,
            ]
            friend["lastActivityAt"] = iso(minutesAgo: Double(48 + index * 190))
            return friend
        }
    }

    private static var pendingRequests: [[String: Any]] {
        [[
            "friendshipId": "fs-new",
            "requesterId": "f-new",
            "displayName": "Lora Hart",
            "username": "lora",
            "profilePhotoUrl": "demo://avatar/3",
            "createdAt": iso(minutesAgo: 90),
        ]]
    }

    private static var searchResults: [[String: Any]] {
        [
            ["userId": "s1", "displayName": "Katie Moore", "username": "katie", "requested": false, "isPendingFromMe": false, "isPendingFromThem": false],
            ["userId": "s2", "displayName": "Kris Dale", "username": "kris", "requested": true, "isPendingFromMe": true, "isPendingFromThem": false],
        ]
    }

    private static var sentPhotos: [[String: Any]] {
        (0..<4).map { index in
            ["photoId": "sent-\(index)", "photoUrl": "demo://photo/\(20 + index)", "createdAt": iso(minutesAgo: Double(index) * 190 + 40)]
        }
    }

    private static var blockedUsers: [[String: Any]] {
        [["userId": "b1", "displayName": "Sam Wilder", "username": "sam", "profilePhotoUrl": "demo://avatar/5", "blockedAt": iso(minutesAgo: 60 * 24 * 9)]]
    }

    private static var activity: [[String: Any]] {
        let rows: [(String, String, String, Double, Int)] = [
            ("PHOTO_RECEIVED", "f-bhoomi", "Bhoomiza Sharma sent you a photo", 15 * 60, 0),
            ("PHOTO_RECEIVED", "f-aditya", "Aditya Rao sent you a photo", 40 * 60, 1),
            ("REQUEST_INCOMING", "f-new", "Lora Hart wants to be friends", 90, 3),
            ("PHOTO_RECEIVED", "f-margot", "Margot Lin sent you a photo", 70 * 60, 2),
            ("PHOTO_RECEIVED", "f-brandie", "Brandie Cole sent you 2 photos", 9 * 24 * 60, 3),
        ]
        return rows.map { type, actor, message, ago, seed in
            var event: [String: Any] = [
                "type": type, "actorId": actor, "actorDisplayName": message.components(separatedBy: " sent").first ?? "Friend",
                "actorProfilePhotoUrl": "demo://avatar/\(seed)", "message": message, "createdAt": iso(minutesAgo: ago), "warn": false,
            ]
            if type == "PHOTO_RECEIVED" { event["photoUrl"] = "demo://photo/\(seed * 5 + 2)" }
            return event
        }
    }

    private static var memories: [[String: Any]] {
        (0..<17).map { index in
            [
                "photoId": "m-\(index)",
                "photoUrl": "demo://photo/\(40 + index)",
                "createdAt": iso(minutesAgo: Double(index) * 60 * 24 * 4.5 + 300),
            ]
        }
    }
}

/// Draws a soft, colourful picture for each `demo://` address, standing in for real photos.
enum DemoImages {
    static func image(for url: URL) -> UIImage? {
        guard url.scheme == "demo" else { return nil }
        let seed = Int(url.lastPathComponent) ?? abs(url.absoluteString.hashValue % 97)
        let isAvatar = url.host == "avatar"
        let size = isAvatar ? CGSize(width: 240, height: 240) : CGSize(width: 800, height: 1000)
        let palettes: [(UInt32, UInt32)] = [
            (0xF5D90A, 0xFF7A1A), (0x5DADE2, 0x7B61FF), (0xFF7A9E, 0xFFB86B),
            (0x4FD1A5, 0x1F6F8B), (0xB28DFF, 0x2D2A6B), (0xFFB347, 0xC2185B),
        ]
        let palette = palettes[seed % palettes.count]
        return UIGraphicsImageRenderer(size: size).image { context in
            let colors = [palette.0, palette.1].map { hex in
                UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255, blue: CGFloat(hex & 0xFF) / 255, alpha: 1).cgColor
            }
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: [0, 1])!
            context.cgContext.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: size.width, y: size.height), options: [])
            UIColor.white.withAlphaComponent(0.22).setFill()
            let radius = size.width * (0.18 + CGFloat(seed % 4) * 0.05)
            context.cgContext.fillEllipse(in: CGRect(x: size.width * 0.55, y: size.height * 0.18, width: radius * 2, height: radius * 2))
            UIColor.black.withAlphaComponent(0.18).setFill()
            context.cgContext.fillEllipse(in: CGRect(x: -radius * 0.6, y: size.height * 0.62, width: radius * 2.4, height: radius * 2.4))
        }
    }
}
#endif
