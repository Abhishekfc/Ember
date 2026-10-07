import SwiftUI
import UIKit

/// How heavy the display font is. Both display fonts are variable fonts whose weight axis covers 300...700.
enum DisplayWeight: CGFloat {
    case light = 300
    case regular = 400
    case medium = 500
    case semibold = 600
    case bold = 700
}

/// The type system. Body text and controls use the system font (SF Pro), so the app reads as
/// native on iPhone; big titles and numbers use the theme's display font (Space Grotesk or
/// Fraunces); the wordmark uses Courgette. Falls back to the system font if a bundled font ever
/// fails to load.
enum EmigoFont {
    private static let wordmarkFontName = "Courgette-Regular"
    /// OpenType variation tag for the weight axis: 'wght'.
    private static let weightAxisTag = NSNumber(value: 0x7767_6874)

    static func displayUIFont(size: CGFloat, weight: DisplayWeight, family: DisplayFontFamily = .spaceGrotesk) -> UIFont {
        guard let base = UIFont(name: family.baseFontName, size: size) else {
            return .systemFont(ofSize: size, weight: weight.systemWeight)
        }
        let variation = [UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [weightAxisTag: NSNumber(value: Double(weight.rawValue))]]
        return UIFont(descriptor: base.fontDescriptor.addingAttributes(variation), size: size)
    }

    static func display(size: CGFloat, weight: DisplayWeight = .bold, family: DisplayFontFamily = .spaceGrotesk) -> Font {
        Font(displayUIFont(size: size, weight: weight, family: family) as CTFont)
    }

    static func wordmark(size: CGFloat) -> Font {
        if UIFont(name: wordmarkFontName, size: size) != nil {
            return .custom(wordmarkFontName, fixedSize: size)
        }
        return .system(size: size, weight: .bold, design: .rounded)
    }
}

private extension DisplayWeight {
    var systemWeight: UIFont.Weight {
        switch self {
        case .light: .light
        case .regular: .regular
        case .medium: .medium
        case .semibold: .semibold
        case .bold: .bold
        }
    }
}

/// Applies the current theme's display font at a size that follows the user's Dynamic Type setting.
private struct DisplayFontModifier: ViewModifier {
    @ScaledMetric private var size: CGFloat
    private let weight: DisplayWeight
    @Environment(\.theme) private var theme

    init(size: CGFloat, weight: DisplayWeight, relativeTo style: Font.TextStyle) {
        _size = ScaledMetric(wrappedValue: size, relativeTo: style)
        self.weight = weight
    }

    func body(content: Content) -> some View {
        content.font(EmigoFont.display(size: size, weight: weight, family: theme.displayFont))
    }
}

extension View {
    /// The theme's display font (big titles, numbers) that scales with Dynamic Type.
    func displayFont(_ size: CGFloat, weight: DisplayWeight = .bold, relativeTo style: Font.TextStyle = .title) -> some View {
        modifier(DisplayFontModifier(size: size, weight: weight, relativeTo: style))
    }
}
