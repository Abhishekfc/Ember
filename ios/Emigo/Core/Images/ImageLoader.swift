import ImageIO
import SwiftUI
import UIKit

/// Downloads photos, shrinks them to the size actually needed, and remembers them in memory (and,
/// through `URLCache`, on disk). Identical requests that arrive together share one download.
actor ImageLoader {
    static let shared = ImageLoader()

    /// Lets a build supply images without a network, such as the debug demo mode.
    typealias SyntheticProvider = @Sendable (URL) -> UIImage?

    private let session: URLSession
    private let syntheticProvider: SyntheticProvider?
    private let memoryCache = NSCache<NSString, UIImage>()
    private var inFlight: [String: Task<UIImage?, Never>] = [:]

    init(syntheticProvider: SyntheticProvider? = nil) {
        let configuration = URLSessionConfiguration.default
        configuration.urlCache = URLCache(memoryCapacity: 32 * 1024 * 1024, diskCapacity: 256 * 1024 * 1024)
        configuration.requestCachePolicy = .returnCacheDataElseLoad
        configuration.timeoutIntervalForRequest = 30
        session = URLSession(configuration: configuration)
        self.syntheticProvider = syntheticProvider
        memoryCache.totalCostLimit = 96 * 1024 * 1024
    }

    /// The photo at `url`, scaled so its longest side is at most `maxPixelSize` pixels.
    func image(for url: URL, maxPixelSize: CGFloat) async -> UIImage? {
        let key = "\(url.absoluteString)#\(Int(maxPixelSize))"
        if let cached = memoryCache.object(forKey: key as NSString) { return cached }
        if let running = inFlight[key] { return await running.value }

        let task = Task<UIImage?, Never> { await self.load(url, maxPixelSize: maxPixelSize) }
        inFlight[key] = task
        let image = await task.value
        inFlight[key] = nil
        if let image {
            memoryCache.setObject(image, forKey: key as NSString, cost: Int(image.size.width * image.size.height * image.scale * image.scale * 4))
        }
        return image
    }

    private func load(_ url: URL, maxPixelSize: CGFloat) async -> UIImage? {
        if let synthetic = syntheticProvider?(url) { return synthetic }
        guard let (data, response) = try? await session.data(from: url),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return await Task.detached(priority: .userInitiated) {
            Self.downsample(data, maxPixelSize: maxPixelSize)
        }.value
    }

    /// Decodes straight to the target size, so a 12-megapixel photo never sits in memory at full size.
    private nonisolated static func downsample(_ data: Data, maxPixelSize: CGFloat) -> UIImage? {
        guard let source = CGImageSourceCreateWithData(data as CFData, [kCGImageSourceShouldCache: false] as CFDictionary) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
        ]
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

private struct ImageLoaderKey: EnvironmentKey {
    static let defaultValue = ImageLoader.shared
}

extension EnvironmentValues {
    var imageLoader: ImageLoader {
        get { self[ImageLoaderKey.self] }
        set { self[ImageLoaderKey.self] = newValue }
    }
}
