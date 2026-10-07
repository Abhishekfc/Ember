import SwiftUI

/// How far through creating an account someone is, for the bars at the top.
struct AuthProgress {
    let step: Int
    let of: Int

    /// Email, password, name, username.
    static func registration(_ step: Int) -> AuthProgress { AuthProgress(step: step, of: 4) }
}

/// The layout every sign-in and sign-up step shares: a big serif title, the step's content, and
/// one main button pinned above the keyboard. The title and content rise into place as the screen
/// opens. The back button is the system's own, so the swipe-back gesture keeps working.
struct AuthStepScaffold<Content: View>: View {
    let title: LocalizedStringResource
    var subtitle: LocalizedStringResource?
    var progress: AuthProgress?
    let buttonTitle: LocalizedStringResource
    var isButtonEnabled = true
    var isLoading = false
    let onButton: () -> Void
    @ViewBuilder var content: Content

    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hasAppeared = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(title)
                        .displayFont(34, weight: .bold, relativeTo: .largeTitle)
                        .foregroundStyle(theme.colors.cream)
                        .fixedSize(horizontal: false, vertical: true)
                        .accessibilityAddTraits(.isHeader)

                    if let subtitle {
                        Text(subtitle)
                            .font(.system(size: 15))
                            .foregroundStyle(theme.colors.muted)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .appearing(order: 0, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

                VStack(alignment: .leading, spacing: Spacing.l) {
                    content
                }
                .appearing(order: 1, hasAppeared: hasAppeared, reduceMotion: reduceMotion)
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.top, Spacing.l)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .scrollDismissesKeyboard(.interactively)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            AuthPrimaryButton(title: buttonTitle, isEnabled: isButtonEnabled, isLoading: isLoading, action: onButton)
                .padding(.horizontal, Spacing.xl)
                .padding(.vertical, Spacing.s)
                .background(theme.colors.backgroundBottom)
        }
        .background(theme.colors.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .toolbar {
            if let progress {
                ToolbarItem(placement: .principal) {
                    StepProgress(current: progress.step, total: progress.of)
                }
            }
        }
        .onAppear { hasAppeared = true }
    }
}

/// An inline error under a field.
struct ErrorText: View {
    let message: String?

    var body: some View {
        if let message {
            Text(message)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(EmigoFixedColors.errorText)
                .fixedSize(horizontal: false, vertical: true)
                .transition(.opacity)
                .accessibilityLabel(message)
        }
    }
}
