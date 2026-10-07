import SwiftUI

/// "Check your inbox": a new account has ten minutes to confirm its email. A ring around the
/// envelope runs down with the time left; the person can say they've done it, resend the email
/// after a short wait, or start over once time is up.
struct VerifyEmailView: View {
    @Bindable var model: AuthFlowViewModel
    let onStartOver: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hasAppeared = false

    var body: some View {
        TimelineView(.periodic(from: .now, by: 1)) { context in
            let remaining = max(0, Int(model.verificationDeadline.timeIntervalSince(context.date)))
            let resendWait = max(0, Int(model.resendAvailableAt.timeIntervalSince(context.date)))
            content(secondsRemaining: remaining, resendWaitSeconds: resendWait)
        }
        .background(theme.colors.background.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .onAppear { hasAppeared = true }
        // Coming back from the Mail app is the moment the link has most likely just been tapped.
        .onChange(of: scenePhase) { _, phase in
            if phase == .active, model.verificationDeadline > .now { model.confirmEmailVerified() }
        }
    }

    private func content(secondsRemaining: Int, resendWaitSeconds: Int) -> some View {
        let hasExpired = secondsRemaining == 0
        return VStack(spacing: 0) {
            Spacer()

            countdownRing(secondsRemaining: secondsRemaining, hasExpired: hasExpired)
                .appearing(order: 0, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

            VStack(spacing: Spacing.xs) {
                Text(hasExpired ? Strings.Verify.failedTitle : Strings.Verify.title)
                    .displayFont(34, weight: .bold, relativeTo: .largeTitle)
                    .foregroundStyle(theme.colors.cream)
                    .multilineTextAlignment(.center)
                    .accessibilityAddTraits(.isHeader)
                Text(hasExpired ? Strings.Verify.expiredDetail : Strings.Verify.sentDetail)
                    .font(.system(size: 15))
                    .foregroundStyle(theme.colors.muted)
                    .multilineTextAlignment(.center)
            }
            .padding(.top, Spacing.xl)
            .appearing(order: 1, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

            Text(verbatim: model.verificationEmail)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(hasExpired ? theme.colors.mutedDim : theme.colors.cream)
                .multilineTextAlignment(.center)
                .frame(maxWidth: .infinity)
                .padding(.horizontal, Spacing.m)
                .padding(.vertical, Spacing.s)
                .background(theme.colors.panel, in: RoundedRectangle(cornerRadius: Radius.largeField, style: .continuous))
                .overlay(RoundedRectangle(cornerRadius: Radius.largeField, style: .continuous).strokeBorder(theme.colors.border, lineWidth: 1))
                .padding(.top, Spacing.l)
                .appearing(order: 2, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

            if !hasExpired {
                messages
                    .padding(.top, Spacing.m)
            }

            Spacer()

            VStack(spacing: Spacing.xs) {
                AuthPrimaryButton(
                    title: hasExpired ? Strings.Verify.startOver : Strings.Verify.doneButton,
                    isLoading: model.isCheckingVerification,
                    action: hasExpired ? onStartOver : model.confirmEmailVerified
                )
                if !hasExpired {
                    resendButton(waitSeconds: resendWaitSeconds)
                }
            }
            .appearing(order: 3, hasAppeared: hasAppeared, reduceMotion: reduceMotion)
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.bottom, Spacing.m)
        .frame(maxWidth: .infinity)
    }

    /// An envelope inside a ring that empties as the ten minutes run out, with the time under it.
    private func countdownRing(secondsRemaining: Int, hasExpired: Bool) -> some View {
        let fraction = min(1, max(0, Double(secondsRemaining) / emailVerificationGracePeriod))
        return ZStack {
            Circle()
                .stroke(theme.colors.panel, lineWidth: 6)
            Circle()
                .trim(from: 0, to: fraction)
                .stroke(theme.colors.accent, style: StrokeStyle(lineWidth: 6, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: Spacing.xs) {
                Image(systemName: hasExpired ? "envelope.badge.shield.half.filled" : "envelope.open.fill")
                    .font(.system(size: 40))
                    .foregroundStyle(hasExpired ? theme.colors.mutedDim : theme.colors.accent)
                Text(hasExpired ? String(localized: Strings.Verify.expiredTitle) : clock(secondsRemaining))
                    .font(.system(size: 15, weight: .semibold).monospacedDigit())
                    .foregroundStyle(hasExpired ? theme.colors.mutedDim : theme.colors.muted)
            }
        }
        .frame(width: 168, height: 168)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(hasExpired ? String(localized: Strings.Verify.expiredTitle) : countdownText(secondsRemaining)))
    }

    @ViewBuilder
    private var messages: some View {
        if let error = model.verificationCheckError {
            Text(error)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(EmigoFixedColors.errorText)
                .multilineTextAlignment(.center)
        }
        if let message = model.verificationResendMessage {
            Text(message)
                .font(.system(size: 14))
                .foregroundStyle(theme.colors.muted)
                .multilineTextAlignment(.center)
        }
    }

    @ViewBuilder
    private func resendButton(waitSeconds: Int) -> some View {
        if waitSeconds > 0 {
            AuthSecondaryButton(
                verbatim: String(format: String(localized: Strings.Verify.resendIn), waitSeconds / 60, waitSeconds % 60),
                isEnabled: false
            ) {}
        } else {
            AuthSecondaryButton(title: Strings.Verify.resend, isLoading: model.isResendingVerification, action: model.resendVerificationEmail)
        }
    }

    private func clock(_ seconds: Int) -> String {
        String(format: "%d:%02d", seconds / 60, seconds % 60)
    }

    private func countdownText(_ seconds: Int) -> String {
        String(format: String(localized: Strings.Verify.expiresIn), seconds / 60, seconds % 60)
    }
}
