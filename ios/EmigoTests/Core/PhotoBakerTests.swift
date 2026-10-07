import XCTest
@testable import Emigo

final class PhotoBakerTests: XCTestCase {
    /// A picture with a red left half and a blue right half.
    private func twoToneJPEG(width: Int, height: Int) -> Data {
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: {
            let format = UIGraphicsImageRendererFormat()
            format.scale = 1
            return format
        }())
        return renderer.jpegData(withCompressionQuality: 1) { context in
            UIColor.red.setFill()
            context.fill(CGRect(x: 0, y: 0, width: width / 2, height: height))
            UIColor.blue.setFill()
            context.fill(CGRect(x: width / 2, y: 0, width: width - width / 2, height: height))
        }
    }

    /// The colour of the pixel at (x, y).
    private func pixel(of image: UIImage, x: Int, y: Int) -> (r: Int, g: Int, b: Int) {
        var bytes = [UInt8](repeating: 0, count: 4)
        let context = CGContext(
            data: &bytes, width: 1, height: 1, bitsPerComponent: 8, bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )!
        let cgImage = image.cgImage!
        // Slide the picture so the pixel we want lands on the single pixel of this tiny canvas.
        context.draw(cgImage, in: CGRect(x: -x, y: -(cgImage.height - 1 - y), width: cgImage.width, height: cgImage.height))
        return (Int(bytes[0]), Int(bytes[1]), Int(bytes[2]))
    }

    func testAWidePictureIsCroppedToFourByFive() throws {
        let image = try XCTUnwrap(PhotoBaker.preparedImage(from: twoToneJPEG(width: 600, height: 300), mirrored: false))
        XCTAssertEqual(image.size.width / image.size.height, 0.8, accuracy: 0.01)
        XCTAssertEqual(image.size.height, 300)
    }

    func testATallPictureIsCroppedToFourByFive() throws {
        let image = try XCTUnwrap(PhotoBaker.preparedImage(from: twoToneJPEG(width: 300, height: 900), mirrored: false))
        XCTAssertEqual(image.size.width / image.size.height, 0.8, accuracy: 0.01)
        XCTAssertEqual(image.size.width, 300)
    }

    func testAPictureAlreadyFourByFiveIsLeftAlone() {
        let rect = PhotoBaker.cropRect(width: 800, height: 1000)
        XCTAssertEqual(rect, CGRect(x: 0, y: 0, width: 800, height: 1000))
    }

    func testMirroringSwapsLeftAndRight() throws {
        let source = twoToneJPEG(width: 400, height: 500)
        let normal = try XCTUnwrap(PhotoBaker.preparedImage(from: source, mirrored: false))
        let mirrored = try XCTUnwrap(PhotoBaker.preparedImage(from: source, mirrored: true))

        let normalLeft = pixel(of: normal, x: 10, y: 250)
        let mirroredLeft = pixel(of: mirrored, x: 10, y: 250)
        XCTAssertGreaterThan(normalLeft.r, 200, "unmirrored: the left side stays red")
        XCTAssertGreaterThan(mirroredLeft.b, 200, "mirrored: the left side becomes blue")
    }

    func testTheBigSideIsCappedSoUploadsStaySmall() throws {
        let image = try XCTUnwrap(PhotoBaker.preparedImage(from: twoToneJPEG(width: 3000, height: 4000), mirrored: false, maxPixelSide: 1000))
        XCTAssertLessThanOrEqual(max(image.size.width, image.size.height), 1000)
    }

    func testAPhotoIsSentAtTheServersSizeLimitAtMost() throws {
        // The server only leaves a JPEG alone if its longest side is 2000 pixels or less; sending
        // more would make it re-encode every photo a second time and lose quality.
        XCTAssertLessThanOrEqual(PhotoBaker.maxPixelSide, 2000)

        let sent = try XCTUnwrap(PhotoBaker.bakedJPEG(from: twoToneJPEG(width: 3000, height: 4000), caption: "", mirrored: false))
        let image = try XCTUnwrap(UIImage(data: sent))
        XCTAssertLessThanOrEqual(max(image.size.width, image.size.height), 2000)
        XCTAssertEqual(image.size.width / image.size.height, 0.8, accuracy: 0.01, "still 4 : 5")
    }

    func testPhotosAreNotCompressedHarderThanTheLookTheUserApproved() {
        // 1440 pixels at quality 80 was tried and looked visibly soft.
        XCTAssertGreaterThanOrEqual(PhotoBaker.maxPixelSide, 2000)
        XCTAssertGreaterThanOrEqual(PhotoBaker.jpegQuality, 0.9)
    }

    func testACaptionKeepsTheSizeAndChangesThePicture() throws {
        let source = twoToneJPEG(width: 800, height: 1000)
        let plain = try XCTUnwrap(PhotoBaker.bakedJPEG(from: source, caption: "", mirrored: false))
        let captioned = try XCTUnwrap(PhotoBaker.bakedJPEG(from: source, caption: "Hello there", mirrored: false))

        let plainImage = try XCTUnwrap(UIImage(data: plain))
        let captionedImage = try XCTUnwrap(UIImage(data: captioned))
        XCTAssertEqual(plainImage.size, captionedImage.size)
        XCTAssertNotEqual(plain, captioned)

        // The bar is dark: a pixel on the caption's row is darker than the same pixel without it.
        let y = Int(1000 * PhotoBaker.captionCenterFraction)
        XCTAssertLessThan(pixel(of: captionedImage, x: 5, y: y).r, pixel(of: plainImage, x: 5, y: y).r)
    }

    func testABlankCaptionIsTreatedAsNone() throws {
        let source = twoToneJPEG(width: 400, height: 500)
        let a = try XCTUnwrap(PhotoBaker.bakedJPEG(from: source, caption: "   \n ", mirrored: false))
        let b = try XCTUnwrap(PhotoBaker.bakedJPEG(from: source, caption: "", mirrored: false))
        XCTAssertEqual(a, b)
    }

    func testGarbageIsRejected() {
        XCTAssertNil(PhotoBaker.preparedImage(from: Data([1, 2, 3]), mirrored: false))
        XCTAssertNil(PhotoBaker.bakedJPEG(from: Data([1, 2, 3]), caption: "hi", mirrored: false))
    }
}
