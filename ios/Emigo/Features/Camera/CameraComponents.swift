import SwiftUI

// The small pieces the camera screen is built from.

/// The big round shutter: a cream ring around a yellow button that squeezes when pressed.
struct ShutterButton: View {
    let isEnabled: Bool
    let action: () -> Void

    @Environment(\.theme) private var theme
    @State private var tapCount = 0

    var body: some View {
        Button {
            tapCount += 1
            action()
        } label: {
            ZStack {
                Circle().strokeBorder(theme.colors.cream, lineWidth: 4)
                Circle().fill(theme.colors.accent).padding(7)
            }
            .frame(width: 84, height: 84)
        }
        .buttonStyle(SqueezeButtonStyle(scale: 0.9))
        .disabled(!isEnabled)
        .sensoryFeedback(.impact(weight: .medium), trigger: tapCount)
        .accessibilityLabel(Text(Strings.Camera.takePhoto))
    }
}

/// The send button: the same ring, with a paper plane. Dimmed until someone is chosen.
struct SendPhotoButton: View {
    let canSend: Bool
    let isSending: Bool
    let action: () -> Void

    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            ZStack {
                Circle().strokeBorder(theme.colors.cream, lineWidth: 4)
                Circle().fill(canSend ? theme.colors.accent : theme.colors.border).padding(7)
                if isSending {
                    ProgressView().tint(theme.colors.accentText)
                } else {
                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 24, weight: .semibold))
                        .foregroundStyle(canSend ? theme.colors.accentText : theme.colors.mutedDim)
                        .offset(x: -1, y: 1)
                }
            }
            .frame(width: 84, height: 84)
        }
        .buttonStyle(SqueezeButtonStyle(scale: 0.92))
        .disabled(isSending || !canSend)
        .accessibilityLabel(Text(Strings.Camera.send))
    }
}

/// Shrinks a little while pressed, with a quick spring back.
struct SqueezeButtonStyle: ButtonStyle {
    var scale: CGFloat = 0.92

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? scale : 1)
            .animation(.spring(duration: 0.25, bounce: 0.4), value: configuration.isPressed)
    }
}

/// A plain white glyph on its own (gallery, flip, retake): no circle behind it.
struct CameraGlyphButton: View {
    let symbol: String
    let label: LocalizedStringResource
    var caption: LocalizedStringResource?
    let action: () -> Void

    @Environment(\.theme) private var theme
    @State private var tapCount = 0

    var body: some View {
        Button {
            tapCount += 1
            action()
        } label: {
            VStack(spacing: 6) {
                Image(systemName: symbol)
                    .font(.system(size: 26, weight: .regular))
                    .foregroundStyle(.white)
                if let caption {
                    Text(caption)
                        .font(.system(size: 11))
                        .foregroundStyle(theme.colors.muted)
                }
            }
            .frame(width: 56, height: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(SqueezeButtonStyle(scale: 0.85))
        .sensoryFeedback(.impact(weight: .light), trigger: tapCount)
        .accessibilityLabel(Text(label))
    }
}

/// Top-left of the camera header: a tiny card outline showing the last photo sent. While a photo
/// is on its way a bright stroke travels around it; when it lands the card fills with a tick.
struct OutboxButton: View {
    let state: SendAnimState
    let lastSentURL: URL?
    let action: () -> Void

    @Environment(\.theme) private var theme

    private let cardWidth: CGFloat = 20
    private var cardHeight: CGFloat { cardWidth / PhotoBaker.aspectRatio }
    private let lineWidth: CGFloat = 2.2

    var body: some View {
        Button(action: action) {
            ZStack {
                card
                if state == .sending { travellingStroke }
                if state == .complete { completeFill }
            }
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(Strings.Camera.sentPhotos))
    }

    private var outline: RoundedRectangle { RoundedRectangle(cornerRadius: 4, style: .continuous) }

    private var card: some View {
        ZStack {
            if let lastSentURL {
                RemoteImage(url: lastSentURL, pointSize: 60)
                    .frame(width: cardWidth, height: cardHeight)
                    .clipShape(outline)
            }
            outline
                .strokeBorder(Color.white.opacity(state == .sending ? 0.35 : 0.9), lineWidth: lineWidth)
                .frame(width: cardWidth, height: cardHeight)
        }
        .animation(.easeOut(duration: 0.2), value: state)
    }

    private var travellingStroke: some View {
        TimelineView(.animation) { context in
            let progress = context.date.timeIntervalSinceReferenceDate
                .truncatingRemainder(dividingBy: 1.4) / 1.4
            let length = 0.24
            let end = progress + length
            ZStack {
                outline.trim(from: progress, to: min(end, 1))
                    .stroke(theme.colors.accent, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                if end > 1 {
                    outline.trim(from: 0, to: end - 1)
                        .stroke(theme.colors.accent, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                }
            }
            .frame(width: cardWidth, height: cardHeight)
        }
    }

    private var completeFill: some View {
        outline.fill(theme.colors.accent)
            .frame(width: cardWidth, height: cardHeight)
            .overlay {
                Image(systemName: "checkmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(theme.colors.accentText)
            }
            .transition(.scale.combined(with: .opacity))
    }
}

/// One or two overlapping profile pictures (and a count) for the friends a photo is going to.
struct RecipientAvatarStack: View {
    let friends: [FriendSummary]
    let size: CGFloat

    @Environment(\.theme) private var theme

    private let maxShown = 2
    private let overlap: CGFloat = 0.65

    var body: some View {
        if friends.isEmpty {
            Image(systemName: "person.badge.plus")
                .font(.system(size: size * 0.5))
                .foregroundStyle(.white)
                .frame(width: size, height: size)
        } else {
            let shown = Array(friends.prefix(maxShown))
            let extra = friends.count - shown.count
            let circles = shown.count + (extra > 0 ? 1 : 0)
            ZStack(alignment: .leading) {
                ForEach(Array(shown.enumerated()), id: \.element.id) { index, friend in
                    AvatarView(name: friend.displayName, photoURL: friend.profilePhotoUrl.flatMap { URL(string: $0) }, size: size)
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.85), lineWidth: 1.5))
                        .offset(x: size * overlap * CGFloat(index))
                        .zIndex(Double(-index))
                }
                if extra > 0 {
                    Text(verbatim: "+\(extra)")
                        .font(.system(size: size * 0.38, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: size, height: size)
                        .background(theme.colors.panel, in: Circle())
                        .overlay(Circle().strokeBorder(Color.white.opacity(0.85), lineWidth: 1.5))
                        .offset(x: size * overlap * CGFloat(shown.count))
                }
            }
            .frame(width: size * (1 + CGFloat(circles - 1) * overlap), height: size, alignment: .leading)
        }
    }
}

/// The glass pill in the middle of the header: who the next photo goes to. Tap to change.
struct RecipientChip: View {
    let friends: [FriendSummary]
    let hasPinned: Bool
    let action: () -> Void

    @Environment(\.theme) private var theme

    var body: some View {
        Button(action: action) {
            HStack(spacing: 9) {
                RecipientAvatarStack(friends: friends, size: 28)
                if hasPinned {
                    Image(systemName: "pin.fill")
                        .font(.system(size: 12))
                        .foregroundStyle(theme.colors.accent)
                }
                Text(Strings.Friends.title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.white)
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.7))
            }
            .padding(.leading, 8)
            .padding(.trailing, 14)
            .frame(height: 38)
            .liquidGlass(in: Capsule(), interactive: true)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(Strings.Camera.chooseRecipients))
    }
}

/// Shown instead of the live picture when the camera can't be used.
struct CameraUnavailableView: View {
    enum Reason { case denied, noCamera }
    let reason: Reason

    @Environment(\.theme) private var theme
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: Spacing.m) {
            Image(systemName: reason == .denied ? "camera.fill" : "camera.metering.unknown")
                .font(.system(size: 34))
                .foregroundStyle(theme.colors.mutedDim)
            Text(reason == .denied ? Strings.Camera.permissionNeeded : Strings.Camera.noCamera)
                .font(.system(size: 14))
                .foregroundStyle(theme.colors.muted)
                .multilineTextAlignment(.center)
            if reason == .denied {
                EmigoButton(title: Strings.Camera.openSettings, kind: .secondary) {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                }
                .frame(maxWidth: 200)
            }
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// A small "1.8×" shown briefly while pinching to zoom.
struct ZoomBadge: View {
    let factor: CGFloat

    var body: some View {
        Text(verbatim: String(format: "%.1f×", factor))
            .font(.system(size: 13, weight: .semibold).monospacedDigit())
            .foregroundStyle(.white)
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color.black.opacity(0.5), in: Capsule())
    }
}

/// The square that appears where you tap to focus, then fades.
struct FocusRing: View {
    @Environment(\.theme) private var theme

    var body: some View {
        RoundedRectangle(cornerRadius: 6, style: .continuous)
            .strokeBorder(theme.colors.accent, lineWidth: 1.5)
            .frame(width: 72, height: 72)
    }
}

/// Explains why gallery photos need Emigo Gold.
struct GoldUpsellSheet: View {
    @Environment(\.theme) private var theme
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: Spacing.m) {
            Image(systemName: "sparkles")
                .font(.system(size: 26, weight: .semibold))
                .foregroundStyle(theme.colors.accentText)
                .frame(width: 56, height: 56)
                .background(theme.colors.accent, in: Circle())

            Text(Strings.Camera.goldTitle)
                .displayFont(20, weight: .bold, relativeTo: .title3)
                .foregroundStyle(theme.colors.cream)

            Text(Strings.Camera.goldPerk)
                .font(.system(size: 14))
                .foregroundStyle(theme.colors.muted)
                .multilineTextAlignment(.center)

            Text(Strings.Camera.goldNotOnIPhone)
                .font(.system(size: 12))
                .foregroundStyle(theme.colors.mutedDim)
                .multilineTextAlignment(.center)

            EmigoButton(title: Strings.Camera.maybeLater, kind: .secondary) { dismiss() }
                .padding(.top, Spacing.xs)
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity)
        .background(theme.colors.backgroundTop.ignoresSafeArea())
        .presentationDetents([.height(340)])
        .presentationDragIndicator(.visible)
    }
}
