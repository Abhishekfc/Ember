import XCTest
@testable import Emigo

/// Answers every request from a closure, so the client can be tested with no network.
final class StubURLProtocol: URLProtocol {
    nonisolated(unsafe) static var handler: ((URLRequest) -> (status: Int, body: Data))?
    nonisolated(unsafe) static var lastRequest: URLRequest?

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        Self.lastRequest = request
        let (status, body) = Self.handler?(request) ?? (500, Data())
        let response = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: body)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

private final class FakeTokens: AccessTokenProviding {
    var token: String?
    init(token: String?) { self.token = token }
    func idToken() async -> String? { token }
}

@MainActor
final class APIClientTests: XCTestCase {
    private func makeClient(token: String?) -> APIClient {
        APIClient(
            baseURL: URL(string: "https://api.example/")!,
            tokenProvider: FakeTokens(token: token),
            session: APIClient.makeSession(protocolClasses: [StubURLProtocol.self])
        )
    }

    private func respond(_ status: Int, _ json: String = "{}") {
        StubURLProtocol.handler = { _ in (status, Data(json.utf8)) }
    }

    override func tearDown() {
        StubURLProtocol.handler = nil
        StubURLProtocol.lastRequest = nil
    }

    func testDecodesAResponseAndSendsTheToken() async throws {
        respond(200, #"{"available":true}"#)
        let result = try await makeClient(token: "abc").send(.emailAvailabilityPublic("a@b.co"))

        XCTAssertTrue(result.available)
        XCTAssertEqual(StubURLProtocol.lastRequest?.value(forHTTPHeaderField: "Authorization"), "Bearer abc")
        XCTAssertEqual(StubURLProtocol.lastRequest?.url?.absoluteString, "https://api.example/auth/email-availability?email=a@b.co")
    }

    func testNoTokenMeansNoAuthorizationHeader() async throws {
        respond(200, #"{"available":true}"#)
        _ = try await makeClient(token: nil).send(.emailAvailabilityPublic("a@b.co"))
        XCTAssertNil(StubURLProtocol.lastRequest?.value(forHTTPHeaderField: "Authorization"))
    }

    func testA401WithATokenMeansTheSessionExpired() async {
        respond(401)
        let client = makeClient(token: "abc")
        var expired = false
        client.onSessionExpired = { expired = true }

        do { _ = try await client.send(.pendingFriendRequests()); XCTFail("should throw") } catch APIError.unauthorized {} catch { XCTFail("\(error)") }
        XCTAssertTrue(expired)
    }

    func testA401WithNoTokenIsNotASessionExpiry() async {
        respond(401)
        let client = makeClient(token: nil)
        var expired = false
        client.onSessionExpired = { expired = true }

        _ = try? await client.send(.pendingFriendRequests())
        XCTAssertFalse(expired)
    }

    func testA401FromTheProfileCheckIsTheExpectedNeedsProfileAnswer() async {
        respond(401)
        let client = makeClient(token: "abc")
        var expired = false
        client.onSessionExpired = { expired = true }

        _ = try? await client.send(.myProfile())
        XCTAssertFalse(expired, "GET users/me answers 401 for 'signed in but no profile yet'")
    }

    func testServerErrorsCarryTheServersMessage() async {
        respond(409, #"{"status":409,"error":"Conflict","message":"Username already taken"}"#)
        do {
            _ = try await makeClient(token: "abc").send(.completeProfile(displayName: "A", username: "a"))
            XCTFail("should throw")
        } catch APIError.server(let status, let message) {
            XCTAssertEqual(status, 409)
            XCTAssertEqual(message, "Username already taken")
        } catch {
            XCTFail("\(error)")
        }
    }

    func testAnEmptySuccessBodyIsFine() async throws {
        respond(204, "")
        _ = try await makeClient(token: "abc").send(.markPhotoSeen("p1"))
    }

    func testTheRequestBodyIsJSON() async throws {
        respond(200, #"{"userId":"u","displayName":"A","username":"a","email":"a@b.co","profilePhotoUrl":null}"#)
        _ = try await makeClient(token: "abc").send(.completeProfile(displayName: "Ann Lee", username: "ann"))

        let request = try XCTUnwrap(StubURLProtocol.lastRequest)
        XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Content-Type"), "application/json")
    }

    func testMultipartBodiesCarryEveryPart() {
        var form = MultipartForm()
        form.addFile("file", filename: "photo.jpg", mimeType: "image/jpeg", data: Data([1, 2, 3]))
        form.addField("recipientIds", value: "friend-1")
        form.addField("recipientIds", value: "friend-2")
        form.addField("save", value: "true")

        let text = String(decoding: form.encoded(boundary: "B"), as: UTF8.self)
        XCTAssertEqual(text.components(separatedBy: "--B\r\n").count - 1, 4)
        XCTAssertTrue(text.contains(#"name="recipientIds""#))
        XCTAssertTrue(text.contains("friend-2"))
        XCTAssertTrue(text.hasSuffix("--B--\r\n"))
    }
}
