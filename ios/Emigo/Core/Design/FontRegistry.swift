import CoreText
import Foundation
import OSLog

/// Registers the bundled brand fonts at launch. Done in code, not through `UIAppFonts`, so it does
/// not matter how Xcode lays the font files out inside the app bundle.
enum FontRegistry {
    private static let logger = Logger(subsystem: "com.emigo.app", category: "fonts")

    static func registerBundledFonts(in bundle: Bundle = .main) {
        let urls = (bundle.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? [])
            + (bundle.urls(forResourcesWithExtension: "ttf", subdirectory: "Fonts") ?? [])
        var seen = Set<URL>()
        for url in urls where seen.insert(url).inserted {
            var error: Unmanaged<CFError>?
            if !CTFontManagerRegisterFontsForURL(url as CFURL, .process, &error) {
                let message = error?.takeRetainedValue().localizedDescription ?? "unknown error"
                logger.error("Could not register \(url.lastPathComponent, privacy: .public): \(message, privacy: .public)")
            }
        }
    }
}
