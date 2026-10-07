import ImageIO
import UIKit

/// Turns a photo from the camera or the gallery into what actually gets sent: upright, cropped to
/// Emigo's 4 : 5 shape, mirrored if it was a selfie, with any caption drawn into the picture
/// itself (so it looks the same everywhere, including the widget). Port of Android's
/// `bakeCaptionIntoPhoto`.
enum PhotoBaker {
    /// Width : height of every photo on Emigo.
    static let aspectRatio: CGFloat = 0.8
    /// Where the caption bar sits, as a fraction of the photo's height from the top.
    static let captionCenterFraction: CGFloat = 0.72
    /// The longest side of what is sent. Sharp on any phone screen. Must not be above the server's
    /// own limit (`PhotoCompressionService.MAX_DIMENSION`, also 2000), or the server re-encodes
    /// every photo a second time and loses quality. Going lower (1440 pixels, quality 80) was tried
    /// and looked visibly softer, so don't.
    static let maxPixelSide: CGFloat = 2000
    /// The JPEG quality of what is sent.
    static let jpegQuality: CGFloat = 0.9

    /// The picture as it should be shown and sent: upright, mirrored if needed, cropped to 4 : 5.
    static func preparedImage(from data: Data, mirrored: Bool, maxPixelSide: CGFloat = maxPixelSide) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true, // applies the photo's rotation
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSide,
        ]
        guard let upright = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary),
              let cropped = upright.cropping(to: cropRect(width: upright.width, height: upright.height)) else { return nil }

        let size = CGSize(width: cropped.width, height: cropped.height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).image { context in
            let cg = context.cgContext
            if mirrored {
                cg.translateBy(x: size.width, y: 0)
                cg.scaleBy(x: -1, y: 1)
            }
            UIImage(cgImage: cropped).draw(in: CGRect(origin: .zero, size: size))
        }
    }

    /// The finished JPEG to send, with `caption` (if any) drawn on.
    static func bakedJPEG(from data: Data, caption: String, mirrored: Bool, quality: CGFloat = jpegQuality) -> Data? {
        guard let image = preparedImage(from: data, mirrored: mirrored) else { return nil }
        let text = caption.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty else { return image.jpegData(compressionQuality: quality) }

        let size = image.size
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        return UIGraphicsImageRenderer(size: size, format: format).jpegData(withCompressionQuality: quality) { context in
            image.draw(at: .zero)
            drawCaption(text, in: size, context: context.cgContext)
        }
    }

    /// The part of an image that is kept: the biggest centred 4 : 5 area.
    static func cropRect(width: Int, height: Int) -> CGRect {
        let w = CGFloat(width)
        let h = CGFloat(height)
        if w / h > aspectRatio {
            let newWidth = (h * aspectRatio).rounded()
            return CGRect(x: ((w - newWidth) / 2).rounded(), y: 0, width: min(max(newWidth, 1), w), height: h)
        }
        let newHeight = (w / aspectRatio).rounded()
        return CGRect(x: 0, y: ((h - newHeight) / 2).rounded(), width: w, height: min(max(newHeight, 1), h))
    }

    /// A full-width dark bar with centred white text, sized from the photo's width so a caption
    /// reads the same on any photo.
    private static func drawCaption(_ text: String, in size: CGSize, context: CGContext) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        let attributes: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: size.width * 0.045, weight: .medium),
            .foregroundColor: UIColor.white,
            .paragraphStyle: paragraph,
        ]
        let textWidth = size.width * 0.86
        let bounds = (text as NSString).boundingRect(
            with: CGSize(width: textWidth, height: .greatestFiniteMagnitude),
            options: [.usesLineFragmentOrigin, .usesFontLeading],
            attributes: attributes,
            context: nil
        )
        let textHeight = ceil(bounds.height)
        let padding = size.width * 0.03
        let barHeight = textHeight + padding * 2
        let barTop = min(max(size.height * captionCenterFraction - barHeight / 2, 0), size.height - barHeight)

        context.setFillColor(UIColor(white: 0, alpha: 150.0 / 255.0).cgColor)
        context.fill(CGRect(x: 0, y: barTop, width: size.width, height: barHeight))
        (text as NSString).draw(
            in: CGRect(x: (size.width - textWidth) / 2, y: barTop + padding, width: textWidth, height: textHeight),
            withAttributes: attributes
        )
    }
}
