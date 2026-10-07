import SwiftUI

/// The ring around a friend's picture. A gradient sweep means they have something new for you;
/// a faint outline means you're caught up.
struct StreakRing: View {
    let isActive: Bool
    let lineWidth: CGFloat

    @Environment(\.theme) private var theme

    var body: some View {
        Circle()
            .strokeBorder(style, lineWidth: lineWidth)
    }

    private var style: AnyShapeStyle {
        if isActive {
            AnyShapeStyle(AngularGradient(
                colors: [theme.colors.accent, theme.colors.accent2, theme.colors.accent],
                center: .center
            ))
        } else {
            AnyShapeStyle(theme.colors.border)
        }
    }
}
