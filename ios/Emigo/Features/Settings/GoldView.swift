import SwiftUI

/// What Emigo Gold gives, and whether this account has it. Gold is bought and managed through
/// Google Play, so on iPhone this page shows the status and sends members to manage it there;
/// buying on iPhone isn't available yet.
///
/// Like Android's, this page has its own look (warm dark background, gold accents) instead of
/// following the chosen theme, so it reads as "gold" whichever theme is on.
struct GoldView: View {
    let isGoldMember: Bool
    /// Whose name goes on the membership card.
    let profile: UserProfile?

    @Environment(\.openURL) private var openURL
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hasAppeared = false

    /// The page's pictures are Apple's own emoji, the same ones the keyboard shows.
    private struct Perk: Identifiable {
        let emoji: String
        let title: LocalizedStringResource
        let detail: LocalizedStringResource
        var id: String { emoji }
    }

    private let perks = [
        Perk(emoji: "🔄", title: Strings.Gold.perkRestoreStreakTitle, detail: Strings.Gold.perkRestoreStreakDetail),
        Perk(emoji: "🎨", title: Strings.Gold.perkThemesTitle, detail: Strings.Gold.perkThemesDetail),
        Perk(emoji: "🖼️", title: Strings.Gold.perkGalleryTitle, detail: Strings.Gold.perkGalleryDetail),
        Perk(emoji: "📱", title: Strings.Gold.perkWidgetTitle, detail: Strings.Gold.perkWidgetDetail),
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                hero
                    .appearing(order: 0, hasAppeared: hasAppeared, reduceMotion: reduceMotion)
                    .padding(.bottom, Spacing.xl)

                if isGoldMember {
                    membershipCard
                        .appearing(order: 1, hasAppeared: hasAppeared, reduceMotion: reduceMotion)
                        .padding(.bottom, Spacing.m)
                }

                perkList
            }
            .padding(.horizontal, Spacing.l)
            .padding(.top, Spacing.xs)
            .padding(.bottom, Spacing.l)
        }
        .scrollBounceBehavior(.basedOnSize)
        .background(backdrop.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) { footer }
        // The big title below is this page's heading, so the bar above stays empty and clear.
        .toolbarBackground(.hidden, for: .navigationBar)
        .navigationBarTitleDisplayMode(.inline)
        .onAppear { hasAppeared = true }
    }

    // MARK: - Pieces

    /// The warm gradient, with a very large, very faint flame cropped into the top corner.
    private var backdrop: some View {
        ZStack {
            GoldPalette.background
            Color.clear
                .overlay(alignment: .topTrailing) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 420))
                        .foregroundStyle(GoldPalette.accentStart.opacity(0.05))
                        .offset(x: 140, y: -70)
                }
                .clipped()
        }
        .accessibilityHidden(true)
    }

    private var hero: some View {
        VStack(spacing: Spacing.xs) {
            Text(verbatim: "🔥")
                .font(.system(size: 72))
                // Pops in with a little spring when the page opens.
                .scaleEffect(hasAppeared || reduceMotion ? 1 : 0.5)
                .animation(reduceMotion ? nil : .spring(duration: 0.7, bounce: 0.45), value: hasAppeared)
                .padding(.bottom, Spacing.xs)
                .accessibilityHidden(true)

            Text(Strings.Gold.title)
                .font(EmigoFont.display(size: 34, weight: .bold))
                .foregroundStyle(GoldPalette.cream)
                .accessibilityAddTraits(.isHeader)

            Text(isGoldMember ? Strings.Gold.subtitleMember : Strings.Gold.subtitleVisitor)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(GoldPalette.muted)
                .multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)
    }

    /// Members only: a gold card with their name on it, in the proportions of a bank card.
    private var membershipCard: some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        return ZStack {
            shape.fill(GoldPalette.accentGradient)

            Color.clear
                .overlay(alignment: .bottomTrailing) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 190))
                        .foregroundStyle(GoldPalette.onAccent.opacity(0.09))
                        .offset(x: 46, y: 40)
                }
                .clipShape(shape)

            VStack(alignment: .leading, spacing: 0) {
                HStack(alignment: .firstTextBaseline) {
                    Text(Strings.Gold.title)
                        .font(EmigoFont.display(size: 19, weight: .bold))
                    Spacer()
                    Text(Strings.Gold.memberLabel)
                        .font(.system(size: 11, weight: .bold))
                        .textCase(.uppercase)
                        .tracking(1.4)
                        .opacity(0.7)
                }
                Spacer(minLength: 0)
                if let profile {
                    Text(verbatim: profile.displayName)
                        .font(.system(size: 21, weight: .bold))
                    Text(verbatim: "@\(profile.username)")
                        .font(.system(size: 13, weight: .medium))
                        .opacity(0.7)
                } else {
                    Text(Strings.Settings.defaultAccountName)
                        .font(.system(size: 21, weight: .bold))
                }
            }
            .foregroundStyle(GoldPalette.onAccent)
            .padding(Spacing.l)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        }
        .aspectRatio(1.586, contentMode: .fit)
        .accessibilityElement(children: .combine)
    }

    /// The four perks in one rounded card, separated by hairlines.
    private var perkList: some View {
        VStack(spacing: 0) {
            ForEach(Array(perks.enumerated()), id: \.element.id) { index, perk in
                perkRow(perk)
                    .appearing(order: index + 2, hasAppeared: hasAppeared, reduceMotion: reduceMotion)
                if index < perks.count - 1 {
                    Rectangle()
                        .fill(GoldPalette.cream.opacity(0.08))
                        .frame(height: 1)
                        .padding(.leading, 66)
                }
            }
        }
        .background(GoldPalette.panel, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
    }

    private func perkRow(_ perk: Perk) -> some View {
        HStack(spacing: Spacing.m) {
            Text(verbatim: perk.emoji)
                .font(.system(size: 26))
                .frame(width: 34)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 3) {
                Text(perk.title)
                    .font(.system(size: 15.5, weight: .semibold))
                    .foregroundStyle(GoldPalette.cream)
                Text(perk.detail)
                    .font(.system(size: 12.5))
                    .foregroundStyle(GoldPalette.muted)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer(minLength: 0)
            if isGoldMember {
                Image(systemName: "checkmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(GoldPalette.accentStart)
                    .accessibilityHidden(true)
            }
        }
        .padding(.horizontal, Spacing.m)
        .padding(.vertical, 17)
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }

    /// Stays at the bottom while the perks scroll behind it.
    private var footer: some View {
        VStack(spacing: 10) {
            if isGoldMember {
                GoldButton(title: Strings.Gold.manage) { openURL(AppLinks.manageSubscription) }
                Text(Strings.Gold.memberStatus)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundStyle(GoldPalette.accentStart)
                Text(Strings.Gold.captionCancelAnytime)
                    .font(.system(size: 11.5))
                    .foregroundStyle(GoldPalette.mutedDim)
            } else {
                GoldButton(title: Strings.Gold.unavailable, isEnabled: false) {}
                Text(Strings.Gold.alreadyMember)
                    .font(.system(size: 11.5))
                    .foregroundStyle(GoldPalette.mutedDim)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, Spacing.l)
        .padding(.top, Spacing.s)
        .padding(.bottom, Spacing.xs)
        .frame(maxWidth: .infinity)
        .background(GoldPalette.backgroundBottom, ignoresSafeAreaEdges: .bottom)
    }
}

/// The Gold page's own colors. Fixed on purpose: none of them come from the chosen theme.
private enum GoldPalette {
    static let backgroundTop = RGBColor(hex: 0x201306).color
    static let backgroundBottom = RGBColor(hex: 0x08060A).color
    static let panel = RGBColor(hex: 0x241A10).color
    static let cream = RGBColor(hex: 0xF5EFE3).color
    static let muted = RGBColor(hex: 0xB7AC9C).color
    static let mutedDim = RGBColor(hex: 0x7A7166).color
    static let accentStart = RGBColor(hex: 0xFFD36E).color
    static let accentEnd = RGBColor(hex: 0xFF9A3D).color
    static let onAccent = RGBColor(hex: 0x2A1B08).color

    static let background = LinearGradient(colors: [backgroundTop, backgroundBottom], startPoint: .top, endPoint: .bottom)
    static let accentGradient = LinearGradient(colors: [accentStart, accentEnd], startPoint: .topLeading, endPoint: .bottomTrailing)
}

/// The Gold page's main button: gold when it can be used, a quiet panel when it can't.
private struct GoldButton: View {
    let title: LocalizedStringResource
    var isEnabled = true
    let action: () -> Void

    @State private var tapCount = 0

    var body: some View {
        Button {
            tapCount += 1
            action()
        } label: {
            Text(title)
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(isEnabled ? GoldPalette.onAccent : GoldPalette.mutedDim)
                .frame(maxWidth: .infinity)
                .frame(height: 54)
                .background {
                    RoundedRectangle(cornerRadius: Radius.button, style: .continuous)
                        .fill(isEnabled ? AnyShapeStyle(GoldPalette.accentGradient) : AnyShapeStyle(GoldPalette.panel))
                }
        }
        .buttonStyle(GoldPressStyle())
        .disabled(!isEnabled)
        .sensoryFeedback(.impact(weight: .light), trigger: tapCount)
    }
}

/// Shrinks very slightly while pressed, like the app's other buttons.
private struct GoldPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
            .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
    }
}
