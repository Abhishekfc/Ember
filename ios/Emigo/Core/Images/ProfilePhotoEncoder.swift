import ImageIO
import UIKit

/// Prepares a picked picture for use as a profile photo: upright, cropped to a centred square and
/// shrunk, so uploads are small and every profile picture is the same shape.
enum ProfilePhotoEncoder {
    static let outputSide: CGFloat = 1024

    static func jpegData(from data: Data, side: CGFloat = outputSide, quality: CGFloat = 0.85) -> Data? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true, // applies the photo's rotation
            kCGImageSourceThumbnailMaxPixelSize: side * 2,
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }

        let shortSide = min(cgImage.width, cgImage.height)
        let crop = CGRect(
            x: (cgImage.width - shortSide) / 2,
            y: (cgImage.height - shortSide) / 2,
            width: shortSide,
            height: shortSide
        )
        guard let square = cgImage.cropping(to: crop) else { return nil }

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let size = CGSize(width: side, height: side)
        return UIGraphicsImageRenderer(size: size, format: format)
            .jpegData(withCompressionQuality: quality) { _ in
                UIImage(cgImage: square).draw(in: CGRect(origin: .zero, size: size))
            }
    }
}
