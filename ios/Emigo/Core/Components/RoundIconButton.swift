import SwiftUI

/// A round Liquid Glass button holding one SF Symbol (the bell, the profile, "add friend"). An
/// optional count shows as a small badge on its corner.
struct RoundIconButton: View {
    let symbol: String
    let label: LocalizedStringResource
    var badgeCount = 0
    let action: () -> Void

    @Environment(\.theme) private var theme
    @State private var tapCount = 0

    var body: some View {
        Button {
            tapCount += 1
            action()
        } label: {
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(theme.colors.cream)
                .frame(width: 36, height: 36)
                .liquidGlass(in: Circle(), interactive: true)
        }
        .buttonStyle(.plain)
        .overlay(alignment: .topTrailing) {
            if badgeCount > 0 {
                Text(verbatim: badgeCount > 99 ? "99+" : "\(badgeCount)")
                    .font(.system(size: 11, weight: .bold).monospacedDigit())
                    .foregroundStyle(theme.colors.accentText)
                    .padding(.horizontal, 5)
                    .frame(minWidth: 18, minHeight: 18)
                    .background(theme.colors.accent, in: Capsule())
                    .offset(x: 4, y: -4)
                    .allowsHitTesting(false)
            }
        }
        .sensoryFeedback(.impact(weight: .light), trigger: tapCount)
        .accessibilityLabel(Text(label))
    }
}
