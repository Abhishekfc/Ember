import SwiftUI

/// Flat placeholders in the shape of Home while the first load is still running. Laid out exactly
/// like the real card and friend row (same sizes, same positions), so nothing moves when the real
/// ones arrive.
struct HomeSkeleton: View {
    @Environment(\.theme) private var theme

    var body: some View {
        GeometryReader { proxy in
            let gap = Spacing.m
            let fullWidth = proxy.size.width - 2 * Size.cardSidePadding
            let cardHeight = max(min(fullWidth / Size.cardAspectRatio, proxy.size.height - Size.friendRowHeight - gap), 0)
            let cardWidth = cardHeight * Size.cardAspectRatio

            VStack(spacing: gap) {
                RoundedRectangle(cornerRadius: 30, style: .continuous)
                    .fill(theme.colors.panel)
                    .frame(width: cardWidth, height: cardHeight)

                HStack(alignment: .top, spacing: 4) {
                    ForEach(0..<4, id: \.self) { _ in
                        // The same cell as a real friend: 84 wide, the avatar centred in a
                        // 76-point slot, then the name underneath.
                        VStack(spacing: 7) {
                            Circle().fill(theme.colors.panel)
                                .frame(width: Size.avatarInactive, height: Size.avatarInactive)
                                .frame(height: Size.avatarActive)
                            Capsule().fill(theme.colors.panel)
                                .frame(width: 38, height: 9)
                                .frame(height: 16)
                        }
                        .frame(width: 84)
                    }
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, Spacing.l)
                .frame(height: Size.friendRowHeight, alignment: .top)

                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
        }
        .accessibilityHidden(true)
    }
}

/// Shown when nobody has shared anything: either no friends yet, or friends who haven't posted.
/// Like Android's, it stands in for what is normally there: in Home a card the size and shape of
/// the photo card, in Moments a grid of empty tiles.
struct HomeEmptyState: View {
    let mode: HomeMode
    let hasFriends: Bool
    let onOpenFindPeople: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hasAppeared = false
    @State private var isDrifting = false
    @State private var tapCount = 0

    var body: some View {
        Group {
            switch mode {
            case .home:
                homeCard
            case .moments:
                momentsTiles
            }
        }
        .onAppear {
            hasAppeared = true
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 14).repeatForever(autoreverses: true)) { isDrifting = true }
        }
    }

    // MARK: - Home

    /// The photo card's own size (18-point margins, 4 : 5), shrinking on a short phone, with its
    /// caption under it.
    private var homeCard: some View {
        GeometryReader { proxy in
            let captionBlock: CGFloat = 64
            let fullWidth = proxy.size.width - 2 * Size.cardSidePadding
            let cardHeight = max(min(fullWidth / Size.cardAspectRatio, proxy.size.height - captionBlock), 0)
            let cardWidth = cardHeight * Size.cardAspectRatio

            VStack(spacing: 0) {
                card
                    .frame(width: cardWidth, height: cardHeight)
                    .appearing(order: 0, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

                Text(hasFriends ? Strings.Home.emptyWithFriends : Strings.Home.emptyNoFriends)
                    .font(.system(size: 12.5))
                    .foregroundStyle(theme.colors.muted)
                    .multilineTextAlignment(.center)
                    .frame(width: cardWidth)
                    .padding(.top, 14)
                    .appearing(order: 3, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
        }
    }

    /// A blurred collage, darkening toward the bottom where the prompt and the button sit.
    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: 30, style: .continuous)
        return Color.clear
            .overlay {
                Image("Home-empty-backdrop")
                    .resizable()
                    .scaledToFill()
                    .blur(radius: 8)
                    // Flattened once, so the slow drift below only moves a finished picture.
                    .drawingGroup()
                    // A little larger than the card, so the blur never fades out at its edges, and
                    // easing slowly in and out so the card feels alive without drawing the eye.
                    .scaleEffect(isDrifting ? 1.16 : 1.08)
            }
            .overlay {
                LinearGradient(
                    stops: [
                        .init(color: .clear, location: 0),
                        .init(color: .clear, location: 0.45),
                        .init(color: .black.opacity(0.85), location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .overlay(alignment: .bottomLeading) {
                VStack(alignment: .leading, spacing: 18) {
                    Text(Strings.Home.addFriendsPrompt)
                        .displayFont(25, weight: .bold, relativeTo: .title)
                        .lineSpacing(3)
                        .foregroundStyle(Color.white)
                        .fixedSize(horizontal: false, vertical: true)
                        .appearing(order: 1, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

                    Button {
                        tapCount += 1
                        onOpenFindPeople()
                    } label: {
                        HStack(spacing: 8) {
                            Text(Strings.Recipients.findFriends)
                                .font(.system(size: 15, weight: .bold))
                            Image(systemName: "arrow.right")
                                .font(.system(size: 13, weight: .bold))
                        }
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                    }
                    .buttonStyle(PressableButtonStyle(foreground: .black, background: .white, cornerRadius: 24))
                    .sensoryFeedback(.impact(weight: .light), trigger: tapCount)
                    .appearing(order: 2, hasAppeared: hasAppeared, reduceMotion: reduceMotion)
                }
                .padding(.horizontal, 24)
                .padding(.bottom, 26)
            }
            .background(theme.colors.elevatedPanel)
            .clipShape(shape)
            // A fine light edge, so the card reads as a crisp object against the black.
            .overlay { shape.strokeBorder(Color.white.opacity(0.10), lineWidth: 1) }
    }

    // MARK: - Moments

    /// Four empty tiles, each with a name strip, so it reads as "a card for every friend".
    private var momentsTiles: some View {
        VStack(spacing: 14) {
            ForEach(0..<2, id: \.self) { row in
                HStack(spacing: 14) {
                    ForEach(0..<2, id: \.self) { column in
                        RoundedRectangle(cornerRadius: 26, style: .continuous)
                            .fill(theme.colors.panel)
                            .aspectRatio(0.8, contentMode: .fit)
                            .overlay(alignment: .bottomLeading) {
                                Capsule()
                                    .fill(theme.colors.mutedDim.opacity(0.45))
                                    .frame(width: 52, height: 9)
                                    .padding(12)
                            }
                            .overlay { RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(theme.colors.border, lineWidth: 1) }
                            .appearing(order: row * 2 + column, hasAppeared: hasAppeared, reduceMotion: reduceMotion)
                    }
                }
            }

            Text(Strings.Home.cardForEveryFriend)
                .font(.system(size: 12.5))
                .foregroundStyle(theme.colors.muted)
                .padding(.top, 2)
                .appearing(order: 4, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, Spacing.l)
        .accessibilityElement(children: .combine)
    }
}
