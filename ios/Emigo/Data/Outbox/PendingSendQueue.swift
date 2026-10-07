import Foundation
import Network
import Observation
import OSLog
import UIKit

/// What the queue needs from the server. `PhotoRepository` provides the real one; tests use a fake.
@MainActor
protocol PendingSendTransport {
    /// Uploads a photo and returns the server's id for it.
    func sendUpload(jpeg: Data, recipientIds: [String], save: Bool) async throws -> String
    func sendMarkSaved(photoId: String) async throws
    func sendAddRecipients(photoId: String, recipientIds: [String]) async throws
}

extension PhotoRepository: PendingSendTransport {
    func sendUpload(jpeg: Data, recipientIds: [String], save: Bool) async throws -> String {
        let response = try await upload(jpeg: jpeg, recipientIds: recipientIds, save: save)
        return response.photoId
    }

    func sendMarkSaved(photoId: String) async throws {
        try await markSaved(photoId)
    }

    func sendAddRecipients(photoId: String, recipientIds: [String]) async throws {
        try await addRecipients(photoId, recipientIds: recipientIds)
    }
}

/// One thing waiting to reach the server.
struct PendingJob: Codable, Equatable, Identifiable {
    enum Kind: Codable, Equatable {
        /// Upload the photo saved in `fileName`.
        case upload(fileName: String, recipientIds: [String], save: Bool)
        /// Mark an already-uploaded photo (the upload job `parent`) as saved to Memories.
        case markSaved(parent: UUID)
        /// Send an already-uploaded photo to more people.
        case addRecipients(parent: UUID, recipientIds: [String])
    }

    let id: UUID
    var kind: Kind
    var attempts = 0
    var nextAttemptAt = Date.distantPast

    var parent: UUID? {
        switch kind {
        case .upload: nil
        case .markSaved(let parent), .addRecipients(let parent, _): parent
        }
    }
}

/// Photos waiting to be sent, kept on disk so they survive the app closing, and tried again with
/// growing waits until they get through. Port of Android's `PendingSendWorker`.
///
/// A photo is first copied into the queue's folder, so tapping Send always succeeds at once, even
/// with no signal. Jobs run one at a time, in order. A job that depends on an upload (save it to
/// Memories, send it to more people) waits for that upload and then uses the server's id for it.
@MainActor
@Observable
final class PendingSendQueue {
    static let maxAttempts = 8
    private static let maxBackoff: TimeInterval = 3600
    private static let baseBackoff: TimeInterval = 30
    /// How long an uploaded photo's id is remembered for jobs that might still follow it.
    private static let uploadedIdLifetime: TimeInterval = 2 * 24 * 3600

    private(set) var jobs: [PendingJob] = []
    /// Called after each job finishes for good, with that job's id.
    var onJobFinished: ((UUID) -> Void)?

    private struct UploadedRef: Codable {
        let photoId: String
        let at: Date
    }

    private struct SavedState: Codable {
        var jobs: [PendingJob]
        var uploaded: [UUID: UploadedRef]
    }

    private var uploaded: [UUID: UploadedRef] = [:]
    private let directory: URL
    private let transport: PendingSendTransport
    private let now: () -> Date
    private var processingTask: Task<Void, Never>?
    private var wakeTask: Task<Void, Never>?
    private let monitor = NWPathMonitor()
    private let logger = Logger(subsystem: "com.emigo.app", category: "outbox")

    init(directory: URL, transport: PendingSendTransport, now: @escaping () -> Date = Date.init) {
        self.directory = directory
        self.transport = transport
        self.now = now
        try? FileManager.default.createDirectory(at: photosDirectory, withIntermediateDirectories: true)
        load()
    }

    /// The standard place: the app's own support folder, which the system never clears.
    nonisolated static func defaultDirectory() -> URL {
        let support = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        return support.appendingPathComponent("Outbox", isDirectory: true)
    }

    /// True while anything is still waiting to be sent.
    var isBusy: Bool { !jobs.isEmpty }

    func isPending(_ id: UUID) -> Bool { jobs.contains { $0.id == id } }

    // MARK: - Adding work

    /// Queues a photo. Returns at once; the upload happens in the background.
    @discardableResult
    func enqueueUpload(jpeg: Data, recipientIds: [String], save: Bool) throws -> UUID {
        let id = UUID()
        let fileName = "\(id.uuidString).jpg"
        try jpeg.write(to: photosDirectory.appendingPathComponent(fileName), options: .atomic)
        append(PendingJob(id: id, kind: .upload(fileName: fileName, recipientIds: recipientIds, save: save)))
        return id
    }

    @discardableResult
    func enqueueMarkSaved(after parent: UUID) -> UUID {
        let id = UUID()
        append(PendingJob(id: id, kind: .markSaved(parent: parent)))
        return id
    }

    @discardableResult
    func enqueueAddRecipients(after parent: UUID, recipientIds: [String]) -> UUID {
        let id = UUID()
        append(PendingJob(id: id, kind: .addRecipients(parent: parent, recipientIds: recipientIds)))
        return id
    }

    // MARK: - Running

    /// Starts sending whatever is waiting, and keeps watching for the network to come back.
    func start() {
        monitor.pathUpdateHandler = { [weak self] path in
            guard path.status == .satisfied else { return }
            Task { @MainActor in self?.retryNow() }
        }
        monitor.start(queue: DispatchQueue(label: "emigo.outbox.network"))
        kick()
    }

    /// Forgets everything waiting. Used on sign-out, so one person's photos can never be sent as
    /// the next person who signs in.
    func clear() {
        wakeTask?.cancel()
        for job in jobs {
            if case .upload(let fileName, _, _) = job.kind {
                try? FileManager.default.removeItem(at: photosDirectory.appendingPathComponent(fileName))
            }
        }
        jobs.removeAll()
        uploaded.removeAll()
        persist()
    }

    /// Tries everything again right now, ignoring the waits. Used when the network returns or the
    /// app comes back to the front.
    func retryNow() {
        for index in jobs.indices { jobs[index].nextAttemptAt = .distantPast }
        kick()
    }

    private func append(_ job: PendingJob) {
        jobs.append(job)
        persist()
        kick()
    }

    private func kick() {
        guard processingTask == nil else { return }
        processingTask = Task { [weak self] in
            await self?.processAll()
            self?.processingTask = nil
            self?.scheduleWake()
        }
    }

    private func processAll() async {
        guard !jobs.isEmpty else { return }
        // Keep running for a while if the person leaves the app mid-send.
        var background = UIBackgroundTaskIdentifier.invalid
        background = UIApplication.shared.beginBackgroundTask(withName: "emigo.outbox") {
            UIApplication.shared.endBackgroundTask(background)
            background = .invalid
        }
        defer { if background != .invalid { UIApplication.shared.endBackgroundTask(background) } }

        while let job = nextRunnableJob() {
            await run(job)
        }
    }

    /// The first job whose time has come and whose upload (if it depends on one) has finished.
    /// A job whose upload was given up on can never run, so it is dropped here.
    private func nextRunnableJob() -> PendingJob? {
        purgeOldUploadedRefs()
        for job in jobs where job.nextAttemptAt <= now() {
            guard let parent = job.parent else { return job }
            if uploaded[parent] != nil { return job }
            if jobs.contains(where: { $0.id == parent }) { continue }
            logger.notice("Dropping a job whose upload is gone")
            remove(job.id)
        }
        return nil
    }

    private func run(_ job: PendingJob) async {
        do {
            switch job.kind {
            case .upload(let fileName, let recipientIds, let save):
                let url = photosDirectory.appendingPathComponent(fileName)
                guard let jpeg = try? Data(contentsOf: url) else {
                    logger.warning("Queued photo is missing; giving up on it")
                    remove(job.id)
                    return
                }
                let photoId = try await transport.sendUpload(jpeg: jpeg, recipientIds: recipientIds, save: save)
                uploaded[job.id] = UploadedRef(photoId: photoId, at: now())
                try? FileManager.default.removeItem(at: url)
            case .markSaved(let parent):
                guard let ref = uploaded[parent] else { return }
                try await transport.sendMarkSaved(photoId: ref.photoId)
            case .addRecipients(let parent, let recipientIds):
                guard let ref = uploaded[parent] else { return }
                try await transport.sendAddRecipients(photoId: ref.photoId, recipientIds: recipientIds)
            }
            remove(job.id)
            onJobFinished?(job.id)
        } catch is CancellationError {
            return
        } catch {
            handleFailure(of: job, error: error)
        }
    }

    private func handleFailure(of job: PendingJob, error: Error) {
        guard let index = jobs.firstIndex(where: { $0.id == job.id }) else { return }
        if isPermanent(error) || jobs[index].attempts + 1 >= Self.maxAttempts {
            logger.warning("Giving up on a queued send: \(String(describing: error), privacy: .public)")
            remove(job.id)
            return
        }
        jobs[index].attempts += 1
        let wait = min(Self.baseBackoff * pow(2, Double(jobs[index].attempts - 1)), Self.maxBackoff)
        jobs[index].nextAttemptAt = now().addingTimeInterval(wait)
        persist()
    }

    /// Errors that trying again can't fix: the server refused the request itself, or the session
    /// is gone. (Being offline, a timeout or a server hiccup are worth retrying.)
    private func isPermanent(_ error: Error) -> Bool {
        guard let api = error as? APIError else { return false }
        switch api {
        case .unauthorized, .decoding, .invalidURL:
            return true
        case .server(let status, _):
            return (400..<500).contains(status) && status != 408 && status != 429
        case .network:
            return false
        }
    }

    private func remove(_ id: UUID) {
        if let job = jobs.first(where: { $0.id == id }), case .upload(let fileName, _, _) = job.kind {
            try? FileManager.default.removeItem(at: photosDirectory.appendingPathComponent(fileName))
        }
        jobs.removeAll { $0.id == id }
        persist()
    }

    /// Wakes the queue when the next waiting job is due.
    private func scheduleWake() {
        wakeTask?.cancel()
        guard let earliest = jobs.map(\.nextAttemptAt).min() else { return }
        let delay = max(1, earliest.timeIntervalSince(now()))
        wakeTask = Task { [weak self] in
            try? await Task.sleep(for: .seconds(delay))
            guard !Task.isCancelled else { return }
            self?.kick()
        }
    }

    // MARK: - Saving to disk

    private var photosDirectory: URL { directory.appendingPathComponent("photos", isDirectory: true) }
    private var stateURL: URL { directory.appendingPathComponent("queue.json") }

    private func persist() {
        let state = SavedState(jobs: jobs, uploaded: uploaded)
        if let data = try? JSONEncoder().encode(state) {
            try? data.write(to: stateURL, options: .atomic)
        }
    }

    private func load() {
        guard let data = try? Data(contentsOf: stateURL),
              let state = try? JSONDecoder().decode(SavedState.self, from: data) else { return }
        jobs = state.jobs
        uploaded = state.uploaded
    }

    private func purgeOldUploadedRefs() {
        let cutoff = now().addingTimeInterval(-Self.uploadedIdLifetime)
        let stale = uploaded.filter { $0.value.at < cutoff }.map(\.key)
        guard !stale.isEmpty else { return }
        for key in stale { uploaded[key] = nil }
        persist()
    }
}
