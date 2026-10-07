import AVFoundation
import SwiftUI
import UIKit

/// The live picture from the camera, with pinch to zoom and tap to focus. UIKit underneath
/// because that is what `AVCaptureVideoPreviewLayer` and precise touch handling need.
struct CameraPreview: UIViewRepresentable {
    let session: AVCaptureSession
    /// Holds the picture still, on the frame that was showing. Used the moment the shutter is
    /// pressed, so the photo seems to be taken at once.
    var isFrozen = false
    var onPinchBegan: () -> Void
    var onPinchChanged: (CGFloat) -> Void
    /// Called with the tap's spot in the camera's own coordinates, and in the view's.
    var onTap: (_ devicePoint: CGPoint, _ viewPoint: CGPoint) -> Void

    func makeUIView(context: Context) -> PreviewView {
        let view = PreviewView()
        view.previewLayer.session = session
        view.previewLayer.videoGravity = .resizeAspectFill
        view.backgroundColor = .black
        view.addGestureRecognizer(UIPinchGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.pinch(_:))))
        view.addGestureRecognizer(UITapGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.tap(_:))))
        context.coordinator.view = view
        return view
    }

    func updateUIView(_ view: PreviewView, context: Context) {
        context.coordinator.parent = self
        view.previewLayer.connection?.isEnabled = !isFrozen
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class PreviewView: UIView {
        override class var layerClass: AnyClass { AVCaptureVideoPreviewLayer.self }
        var previewLayer: AVCaptureVideoPreviewLayer { layer as! AVCaptureVideoPreviewLayer }
    }

    final class Coordinator: NSObject {
        var parent: CameraPreview
        weak var view: PreviewView?

        init(parent: CameraPreview) { self.parent = parent }

        @objc func pinch(_ recognizer: UIPinchGestureRecognizer) {
            switch recognizer.state {
            case .began: parent.onPinchBegan()
            case .changed: parent.onPinchChanged(recognizer.scale)
            default: break
            }
        }

        @objc func tap(_ recognizer: UITapGestureRecognizer) {
            guard let view else { return }
            let point = recognizer.location(in: view)
            parent.onTap(view.previewLayer.captureDevicePointConverted(fromLayerPoint: point), point)
        }
    }
}
