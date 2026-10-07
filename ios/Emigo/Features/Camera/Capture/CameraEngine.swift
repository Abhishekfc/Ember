import AVFoundation
import Observation
import UIKit

/// A photo as the camera produced it, before cropping and captions.
struct CapturedPhoto: Equatable {
    let jpeg: Data
    /// Selfies are mirrored when the photo is prepared, so the result matches the preview.
    let isFrontCamera: Bool
}

enum CameraAvailability: Equatable {
    /// Permission hasn't been asked for yet.
    case unknown
    case authorized
    /// The person said no (or the device is restricted); they can allow it in Settings.
    case denied
    /// Allowed, but this device has no camera (the simulator).
    case unavailable
}

enum CameraError: Error {
    case unavailable
    case captureFailed
}

/// The live camera: front and back, flash, pinch zoom, tap to focus, and taking the photo. The
/// slow, thread-bound AVFoundation work happens on its own queue (`CaptureHardware`); this class
/// is what the screen watches.
@MainActor
@Observable
final class CameraEngine {
    private(set) var availability: CameraAvailability
    private(set) var position: AVCaptureDevice.Position = .back
    private(set) var isFlashOn = false
    /// Only the back camera has a flash.
    private(set) var canUseFlash = false
    private(set) var zoomFactor: CGFloat = 1
    private(set) var isCapturing = false
    /// False while the camera is starting or flipping. The first moments can be dark or washed out
    /// while it settles, which looks like a blink, so the screen keeps the picture covered until
    /// this turns true.
    private(set) var isPreviewLive = false

    var session: AVCaptureSession { hardware.session }

    private let hardware = CaptureHardware()
    private var zoomAtPinchStart: CGFloat = 1
    /// Whether the screen wants the camera on right now, so a start that finishes late, after the
    /// person has already left, doesn't uncover anything.
    private var wantsRunning = false

    init() {
        availability = Self.currentAvailability()
    }

    // MARK: - Permission

    static func currentAvailability() -> CameraAvailability {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized: CaptureHardware.hasCamera ? .authorized : .unavailable
        case .notDetermined: .unknown
        case .denied, .restricted: .denied
        @unknown default: .denied
        }
    }

    func requestAccessIfNeeded() async {
        if AVCaptureDevice.authorizationStatus(for: .video) == .notDetermined {
            _ = await AVCaptureDevice.requestAccess(for: .video)
        }
        availability = Self.currentAvailability()
    }

    // MARK: - Running

    /// Starts the live preview. Called when the Camera tab comes to the front.
    func start() {
        guard availability == .authorized else { return }
        wantsRunning = true
        let position = position
        hardware.queue.async { [weak self, hardware] in
            do {
                try hardware.prepare(position: position)
                if !hardware.session.isRunning { hardware.session.startRunning() }
            } catch {
                return
            }
            Task { @MainActor in self?.didStart() }
        }
    }

    /// Sets the camera up without turning it on (so no camera light), so that opening the Camera
    /// tab only has to start it. Safe to call any time; does nothing until camera access is allowed.
    func prepare() {
        guard availability == .authorized else { return }
        let position = position
        hardware.queue.async { [hardware] in
            try? hardware.prepare(position: position)
        }
    }

    private func didStart() {
        canUseFlash = position == .back && hardware.hasFlash
        zoomFactor = 1
        revealPreviewWhenSteady()
    }

    /// Lets the camera settle (exposure and focus) before showing its picture.
    private func revealPreviewWhenSteady() {
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(150))
            if wantsRunning { isPreviewLive = true }
        }
    }

    /// Stops the live preview, so the camera light goes off when the tab isn't showing.
    func stop() {
        wantsRunning = false
        isPreviewLive = false
        hardware.queue.async { [hardware] in
            if hardware.session.isRunning { hardware.session.stopRunning() }
        }
    }

    func flip() {
        let next: AVCaptureDevice.Position = position == .back ? .front : .back
        position = next
        isFlashOn = false
        isPreviewLive = false
        hardware.queue.async { [weak self, hardware] in
            try? hardware.configure(position: next)
            Task { @MainActor in self?.didStart() }
        }
    }

    func toggleFlash() {
        guard canUseFlash else { return }
        isFlashOn.toggle()
    }

    // MARK: - Zoom and focus

    func beginPinch() {
        zoomAtPinchStart = zoomFactor
    }

    func updatePinch(scale: CGFloat) {
        let target = zoomAtPinchStart * scale
        zoomFactor = hardware.setZoom(target)
    }

    /// `devicePoint` is in the camera's own 0...1 coordinates.
    func focus(atDevicePoint devicePoint: CGPoint) {
        hardware.focus(at: devicePoint)
    }

    // MARK: - Taking the photo

    func capture() async throws -> CapturedPhoto {
        isCapturing = true
        defer { isCapturing = false }
        let isFront = position == .front
        let flash = isFlashOn && canUseFlash
        if availability == .unavailable {
            #if DEBUG
            // No camera (the simulator): hand back a made-up picture so the rest of the flow can be tried.
            return CapturedPhoto(jpeg: SimulatedCamera.jpeg(), isFrontCamera: false)
            #else
            throw CameraError.unavailable
            #endif
        }
        let data: Data = try await withCheckedThrowingContinuation { continuation in
            hardware.capture(flash: flash) { result in continuation.resume(with: result) }
        }
        return CapturedPhoto(jpeg: data, isFrontCamera: isFront)
    }
}

/// Everything that touches AVFoundation directly. Its methods run on `queue`, never the main thread.
private final class CaptureHardware: NSObject, AVCapturePhotoCaptureDelegate, @unchecked Sendable {
    let session = AVCaptureSession()
    let queue = DispatchQueue(label: "com.emigo.camera")
    private let output = AVCapturePhotoOutput()
    private var input: AVCaptureDeviceInput?
    private var device: AVCaptureDevice?
    private var completion: ((Result<Data, Error>) -> Void)?
    /// Which camera the session is set up for. Only touched on `queue`, so setting up early and
    /// starting can never overlap and set the camera up twice.
    private var configuredPosition: AVCaptureDevice.Position?

    static var hasCamera: Bool {
        AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) != nil
    }

    var hasFlash: Bool { device?.hasFlash ?? false }

    /// Sets the session up for `position` unless it already is.
    func prepare(position: AVCaptureDevice.Position) throws {
        if configuredPosition == position { return }
        try configure(position: position)
    }

    func configure(position: AVCaptureDevice.Position) throws {
        session.beginConfiguration()
        defer { session.commitConfiguration() }
        session.sessionPreset = .photo

        if let input { session.removeInput(input) }
        guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: position) else {
            throw CameraError.unavailable
        }
        let newInput = try AVCaptureDeviceInput(device: device)
        guard session.canAddInput(newInput) else { throw CameraError.unavailable }
        session.addInput(newInput)
        input = newInput
        self.device = device

        if !session.outputs.contains(output) {
            guard session.canAddOutput(output) else { throw CameraError.unavailable }
            session.addOutput(output)
        }
        if let connection = output.connection(with: .video) {
            if connection.isVideoRotationAngleSupported(90) { connection.videoRotationAngle = 90 }
            // The preview mirrors selfies; make the photo match what the person saw.
            if connection.isVideoMirroringSupported {
                connection.automaticallyAdjustsVideoMirroring = false
                connection.isVideoMirrored = position == .front
            }
        }
        configuredPosition = position
    }

    /// Returns the zoom actually applied, kept between 1x and 8x.
    func setZoom(_ requested: CGFloat) -> CGFloat {
        guard let device else { return 1 }
        let clamped = min(max(requested, 1), min(device.activeFormat.videoMaxZoomFactor, 8))
        queue.async {
            guard (try? device.lockForConfiguration()) != nil else { return }
            device.videoZoomFactor = clamped
            device.unlockForConfiguration()
        }
        return clamped
    }

    func focus(at point: CGPoint) {
        queue.async { [device] in
            guard let device, (try? device.lockForConfiguration()) != nil else { return }
            if device.isFocusPointOfInterestSupported {
                device.focusPointOfInterest = point
                device.focusMode = .autoFocus
            }
            if device.isExposurePointOfInterestSupported {
                device.exposurePointOfInterest = point
                device.exposureMode = .autoExpose
            }
            device.unlockForConfiguration()
        }
    }

    func capture(flash: Bool, completion: @escaping (Result<Data, Error>) -> Void) {
        queue.async {
            let settings = AVCapturePhotoSettings(format: [AVVideoCodecKey: AVVideoCodecType.jpeg])
            // Speed over the last bit of processing: the photo is sent at 1440 pixels anyway, and
            // this is what lets the shutter fire right away, as in Snapchat.
            settings.photoQualityPrioritization = .speed
            if self.device?.hasFlash == true { settings.flashMode = flash ? .on : .off }
            self.completion = completion
            self.output.capturePhoto(with: settings, delegate: self)
        }
    }

    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        queue.async {
            let callback = self.completion
            self.completion = nil
            if let error {
                callback?(.failure(error))
            } else if let data = photo.fileDataRepresentation() {
                callback?(.success(data))
            } else {
                callback?(.failure(CameraError.captureFailed))
            }
        }
    }
}

#if DEBUG
/// A stand-in picture for devices with no camera, so the capture flow can be tried in the simulator.
enum SimulatedCamera {
    static func jpeg() -> Data {
        let size = CGSize(width: 1200, height: 1500)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.jpegData(withCompressionQuality: 0.9) { context in
            let colors = [UIColor(red: 0.98, green: 0.55, blue: 0.36, alpha: 1).cgColor, UIColor(red: 0.36, green: 0.52, blue: 0.95, alpha: 1).cgColor]
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors as CFArray, locations: [0, 1])!
            context.cgContext.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: size.width, y: size.height), options: [])
            UIColor.white.withAlphaComponent(0.25).setFill()
            context.cgContext.fillEllipse(in: CGRect(x: 640, y: 240, width: 460, height: 460))
        }
    }
}
#endif
