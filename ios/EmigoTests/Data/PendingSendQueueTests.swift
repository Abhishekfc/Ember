import XCTest
@testable import Emigo

/// A server that does what the test tells it to.
@MainActor
private final class FakeTransport: PendingSendTransport {
    var uploadError: Error?
    var nextPhotoId = "photo-1"
    private(set) var uploads: [(bytes: Int, recipients: [String], save: Bool)] = []
    private(set) var markedSaved: [String] = []
    private(set) var addedRecipients: [(photoId: String, recipients: [String])] = []

    func sendUpload(jpeg: Data, recipientIds: [String], save: Bool) async throws -> String {
        if let uploadError { throw uploadError }
        uploads.append((jpeg.count, recipientIds, save))
        return nextPhotoId
    }

    func sendMarkSaved(photoId: String) async throws { markedSaved.append(photoId) }

    func sendAddRecipients(photoId: String, recipientIds: [String]) async throws {
        addedRecipients.append((photoId, recipientIds))
    }
}

@MainActor
final class PendingSendQueueTests: XCTestCase {
    private var directory: URL!
    private var currentTime = Date(timeIntervalSince1970: 1_800_000_000)

    override func setUp() async throws {
        directory = FileManager.default.temporaryDirectory.appendingPathComponent("EmigoQueueTests-\(UUID().uuidString)")
    }

    override func tearDown() async throws {
        try? FileManager.default.removeItem(at: directory)
    }

    private func makeQueue(_ transport: FakeTransport) -> PendingSendQueue {
        PendingSendQueue(directory: directory, transport: transport, now: { [unowned self] in self.currentTime })
    }

    private func photoFiles() -> [String] {
        (try? FileManager.default.contentsOfDirectory(atPath: directory.appendingPathComponent("photos").path)) ?? []
    }

    /// Waits for the queue to go quiet (or the condition to hold).
    private func waitUntil(_ condition: @escaping () -> Bool, timeout: TimeInterval = 3) async {
        let deadline = Date().addingTimeInterval(timeout)
        while !condition(), Date() < deadline { try? await Task.sleep(for: .milliseconds(10)) }
    }

    func testAQueuedPhotoIsUploadedThenForgotten() async throws {
        let transport = FakeTransport()
        let queue = makeQueue(transport)
        var finished: [UUID] = []
        queue.onJobFinished = { finished.append($0) }

        let id = try queue.enqueueUpload(jpeg: Data([1, 2, 3]), recipientIds: ["a", "b"], save: true)
        XCTAssertEqual(photoFiles().count, 1, "the photo is copied into the queue straight away")

        await waitUntil { !queue.isBusy }

        XCTAssertEqual(transport.uploads.count, 1)
        XCTAssertEqual(transport.uploads[0].recipients, ["a", "b"])
        XCTAssertTrue(transport.uploads[0].save)
        XCTAssertEqual(finished, [id])
        XCTAssertTrue(photoFiles().isEmpty, "the copy is deleted once it's safely uploaded")
    }

    func testAFailedUploadStaysQueuedAndIsTriedLater() async throws {
        let transport = FakeTransport()
        transport.uploadError = APIError.network(URLError(.notConnectedToInternet))
        let queue = makeQueue(transport)

        try queue.enqueueUpload(jpeg: Data([1]), recipientIds: ["a"], save: false)
        await waitUntil { queue.jobs.first?.attempts == 1 }

        XCTAssertEqual(queue.jobs.count, 1)
        XCTAssertEqual(queue.jobs[0].attempts, 1)
        XCTAssertGreaterThan(queue.jobs[0].nextAttemptAt, currentTime, "it waits before trying again")
        XCTAssertEqual(photoFiles().count, 1, "the photo is kept")
    }

    func testTheWaitsGetLongerEachTime() async throws {
        let transport = FakeTransport()
        transport.uploadError = APIError.network(URLError(.timedOut))
        let queue = makeQueue(transport)

        try queue.enqueueUpload(jpeg: Data([1]), recipientIds: ["a"], save: false)
        await waitUntil { queue.jobs.first?.attempts == 1 }
        let firstWait = queue.jobs[0].nextAttemptAt.timeIntervalSince(currentTime)

        queue.retryNow()
        await waitUntil { queue.jobs.first?.attempts == 2 }
        let secondWait = queue.jobs[0].nextAttemptAt.timeIntervalSince(currentTime)

        XCTAssertEqual(firstWait, 30, accuracy: 0.001)
        XCTAssertEqual(secondWait, 60, accuracy: 0.001)
    }

    func testAServerRefusalIsNotRetried() async throws {
        let transport = FakeTransport()
        transport.uploadError = APIError.server(status: 413, message: "Too large")
        let queue = makeQueue(transport)

        try queue.enqueueUpload(jpeg: Data([1]), recipientIds: ["a"], save: false)
        await waitUntil { !queue.isBusy }

        XCTAssertFalse(queue.isBusy, "trying again can't help, so the job is dropped")
        XCTAssertTrue(photoFiles().isEmpty)
    }

    func testAnExpiredSessionDropsTheJob() async throws {
        let transport = FakeTransport()
        transport.uploadError = APIError.unauthorized
        let queue = makeQueue(transport)

        try queue.enqueueUpload(jpeg: Data([1]), recipientIds: ["a"], save: false)
        await waitUntil { !queue.isBusy }
        XCTAssertFalse(queue.isBusy)
    }

    func testQueuedPhotosSurviveTheAppClosing() async throws {
        let transport = FakeTransport()
        transport.uploadError = APIError.network(URLError(.notConnectedToInternet))
        let first = makeQueue(transport)
        try first.enqueueUpload(jpeg: Data([9, 9]), recipientIds: ["a"], save: false)
        await waitUntil { first.jobs.first?.attempts == 1 }

        // A new launch: a fresh queue reading the same folder.
        let relaunched = makeQueue(FakeTransport())
        XCTAssertEqual(relaunched.jobs.count, 1)
        XCTAssertEqual(relaunched.jobs.first?.attempts, 1)
    }

    func testSendingAfterSavingAddsPeopleToTheSameUpload() async throws {
        let transport = FakeTransport()
        transport.nextPhotoId = "server-photo"
        let queue = makeQueue(transport)

        let upload = try queue.enqueueUpload(jpeg: Data([1]), recipientIds: [], save: true)
        queue.enqueueAddRecipients(after: upload, recipientIds: ["x", "y"])
        await waitUntil { !queue.isBusy }

        XCTAssertEqual(transport.uploads.count, 1, "the photo is uploaded once")
        XCTAssertEqual(transport.addedRecipients.count, 1)
        XCTAssertEqual(transport.addedRecipients[0].photoId, "server-photo")
        XCTAssertEqual(transport.addedRecipients[0].recipients, ["x", "y"])
    }

    func testAFollowUpIsDroppedIfItsUploadWasGivenUpOn() async throws {
        let transport = FakeTransport()
        transport.uploadError = APIError.server(status: 400, message: nil)
        let queue = makeQueue(transport)

        let upload = try queue.enqueueUpload(jpeg: Data([1]), recipientIds: [], save: true)
        queue.enqueueMarkSaved(after: upload)
        await waitUntil { !queue.isBusy }

        XCTAssertFalse(queue.isBusy)
        XCTAssertTrue(transport.markedSaved.isEmpty)
    }

    func testClearingForgetsEverything() async throws {
        let transport = FakeTransport()
        transport.uploadError = APIError.network(URLError(.notConnectedToInternet))
        let queue = makeQueue(transport)
        try queue.enqueueUpload(jpeg: Data([1]), recipientIds: ["a"], save: false)
        await waitUntil { queue.jobs.first?.attempts == 1 }

        queue.clear()

        XCTAssertFalse(queue.isBusy)
        XCTAssertTrue(photoFiles().isEmpty)
        XCTAssertEqual(makeQueue(FakeTransport()).jobs.count, 0, "and it stays forgotten after a relaunch")
    }
}
