import SwiftUI

/// Apple's Liquid Glass, with a frosted fallback for iPhones older than iOS 26. Glass is used only
/// on the controls that float above content (the tab bar and its icons, the close button over a
/// photo); cards, buttons and screens stay flat.
extension View {
    /// Draws the system's Liquid Glass behind this view, in `shape`.
    /// - Parameters:
    ///   - tint: Colors the glass, as Apple does for a prominent button.
    ///   - interactive: Makes the glass react to touch (a slight lift and shimmer).
    @ViewBuilder
    func liquidGlass<S: InsettableShape>(in shape: S, tint: Color? = nil, interactive: Bool = false) -> some View {
        if #available(iOS 26.0, *) {
            glassEffect(makeGlass(tint: tint, interactive: interactive), in: shape)
        } else {
            frostedFallback(in: shape, tint: tint)
        }
    }

    private func frostedFallback<S: InsettableShape>(in shape: S, tint: Color?) -> some View {
        background {
            if let tint {
                shape.fill(tint.opacity(0.9))
            } else {
                shape.fill(.ultraThinMaterial)
            }
        }
        .overlay { shape.strokeBorder(Color.white.opacity(0.12), lineWidth: 0.5) }
    }
}

@available(iOS 26.0, *)
private func makeGlass(tint: Color?, interactive: Bool) -> Glass {
    var glass = Glass.regular
    if let tint { glass = glass.tint(tint) }
    if interactive { glass = glass.interactive() }
    return glass
}

/// Groups several glass shapes so they blend and morph together, as Apple's own bars do. On
/// iOS 26 and later this is a `GlassEffectContainer`; earlier it is a plain container.
struct GlassGroup<Content: View>: View {
    var spacing: CGFloat = 12
    @ViewBuilder var content: Content

    var body: some View {
        if #available(iOS 26.0, *) {
            GlassEffectContainer(spacing: spacing) { content }
        } else {
            content
        }
    }
}
