import SwiftUI

/// The app's buttons. One component, three weights, so every screen's actions look and feel alike.
struct EmigoButton: View {
    enum Kind {
        /// The one main action on a screen: filled with the brand accent.
        case primary
        /// A quieter alternative: a flat panel.
        case secondary
        /// Text only.
        case plain
        /// Like `secondary`, but for actions that remove something.
        case destructive
    }

    let title: LocalizedStringResource
    var kind: Kind = .primary
    var isLoading = false
    var isEnabled = true
    let action: () -> Void

    @Environment(\.theme) private var theme
    @State private var tapCount = 0

    var body: some View {
        Button {
            tapCount += 1
            action()
        } label: {
            ZStack {
                Text(title)
                    .font(.system(size: 15, weight: .bold))
                    .opacity(isLoading ? 0 : 1)
                if isLoading {
                    ProgressView().tint(foreground)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: kind == .plain ? nil : Size.buttonHeight)
            .padding(.vertical, kind == .plain ? Spacing.xs : 0)
        }
        .buttonStyle(PressableButtonStyle(
            foreground: foreground,
            background: background,
            cornerRadius: Radius.button
        ))
        .disabled(!isEnabled || isLoading)
        .opacity(isEnabled ? 1 : 0.4)
        .animation(.easeOut(duration: 0.15), value: isEnabled)
        .sensoryFeedback(.impact(weight: .light), trigger: tapCount)
    }

    private var foreground: Color {
        switch kind {
        case .primary: theme.colors.accentText
        case .secondary, .plain: theme.colors.cream
        case .destructive: EmigoFixedColors.errorText
        }
    }

    private var background: Color {
        switch kind {
        case .primary: theme.colors.accent
        case .secondary, .destructive: theme.colors.panel
        case .plain: .clear
        }
    }
}

/// Shrinks very slightly while pressed, the way native iOS controls respond to a finger.
struct PressableButtonStyle: ButtonStyle {
    var foreground: Color
    var background: Color
    var cornerRadius: CGFloat

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .foregroundStyle(foreground)
            .background(background, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
