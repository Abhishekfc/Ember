import SwiftUI

/// The pill-shaped main button every sign-in and sign-up step ends on: the app's yellow when it
/// can be pressed, a quiet dark pill when it can't. Shrinks a little under the finger.
struct AuthPrimaryButton: View {
    let title: LocalizedStringResource
    var isEnabled = true
    var isLoading = false
    let action: () -> Void

    @Environment(\.theme) private var theme
    @State private var tapCount = 0

    private var canPress: Bool { isEnabled && !isLoading }

    var body: some View {
        Button {
            tapCount += 1
            action()
        } label: {
            ZStack {
                Text(title)
                    .font(.system(size: 17, weight: .bold))
                    .opacity(isLoading ? 0 : 1)
                if isLoading {
                    ProgressView().tint(theme.colors.accentText)
                }
            }
            .foregroundStyle(isEnabled ? theme.colors.accentText : theme.colors.mutedDim)
            .frame(maxWidth: .infinity)
            .frame(height: 56)
            .background(isEnabled ? theme.colors.accent : theme.colors.panel, in: Capsule())
        }
        .buttonStyle(AuthPressStyle())
        .disabled(!canPress)
        .animation(.easeOut(duration: 0.18), value: isEnabled)
        .sensoryFeedback(.impact(weight: .light), trigger: tapCount)
    }
}

/// The quieter button beside a main one: an outline, no fill.
struct AuthSecondaryButton: View {
    let label: Text
    var isEnabled = true
    var isLoading = false
    let action: () -> Void

    @Environment(\.theme) private var theme

    init(title: LocalizedStringResource, isEnabled: Bool = true, isLoading: Bool = false, action: @escaping () -> Void) {
        label = Text(title)
        self.isEnabled = isEnabled
        self.isLoading = isLoading
        self.action = action
    }

    /// For text that isn't a fixed phrase, such as a countdown.
    init(verbatim text: String, isEnabled: Bool = true, isLoading: Bool = false, action: @escaping () -> Void) {
        label = Text(verbatim: text)
        self.isEnabled = isEnabled
        self.isLoading = isLoading
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            ZStack {
                label
                    .font(.system(size: 16, weight: .semibold).monospacedDigit())
                    .opacity(isLoading ? 0 : 1)
                if isLoading {
                    ProgressView().tint(theme.colors.cream)
                }
            }
            .foregroundStyle(isEnabled ? theme.colors.cream : theme.colors.mutedDim)
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .overlay(Capsule().strokeBorder(theme.colors.cream.opacity(isEnabled ? 0.22 : 0.10), lineWidth: 1))
            .contentShape(Capsule())
        }
        .buttonStyle(AuthPressStyle())
        .disabled(!isEnabled || isLoading)
    }
}

/// Shrinks very slightly while pressed, the way native iOS controls respond to a finger.
struct AuthPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
            .animation(.spring(duration: 0.25, bounce: 0.3), value: configuration.isPressed)
    }
}

/// "Step 2 of 4" as a row of small bars: the steps done and the current one in the accent, the
/// ones to come dim. The current bar is the longest.
struct StepProgress: View {
    let current: Int
    let total: Int

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: 6) {
            ForEach(1...total, id: \.self) { step in
                Capsule()
                    .fill(step <= current ? theme.colors.accent : theme.colors.cream.opacity(0.18))
                    .frame(width: step == current ? 26 : 8, height: 5)
            }
        }
        .animation(.spring(duration: 0.4, bounce: 0.25), value: current)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: "\(current) / \(total)"))
    }
}
