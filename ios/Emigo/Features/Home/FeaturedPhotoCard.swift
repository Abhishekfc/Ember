import SwiftUI

/// The big photo card on Home. It swipes through one deck of photos across all friends; the row of
/// avatars underneath follows along.
struct FeaturedPhotoCard: View {
    @Bindable var model: HomeViewModel

    @Environment(\.theme) private var theme

    private var indexBinding: Binding<Int> {
        Binding(get: { model.currentIndex }, set: { model.pageChanged(to: $0) })
    }

    var body: some View {
        TabView(selection: indexBinding) {
            ForEach(Array(model.entries.enumerated()), id: \.element.id) { index, entry in
                RemoteImage(url: entry.photo.url, pointSize: 700)
                    .tag(index)
            }
        }
        .tabViewStyle(.page(indexDisplayMode: .never))
        .overlay { scrim }
        .overlay(alignment: .top) { progressBars }
        .overlay(alignment: .bottom) { footer }
        .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        .sensoryFeedback(.selection, trigger: model.currentIndex)
        .animation(.easeInOut(duration: 0.3), value: model.currentIndex)
    }

    /// Darkens the bottom of the photo so the time and streak stay readable on any picture.
    private var scrim: some View {
        LinearGradient(
            stops: [.init(color: .clear, location: 0.55), .init(color: .black.opacity(0.65), location: 1)],
            startPoint: .top,
            endPoint: .bottom
        )
        .allowsHitTesting(false)
    }

    /// One small dash per photo of this friend, centred at the top like Android's: 16 wide, 3 high,
    /// shrinking only when there are too many to fit. White for the current photo, the accent for
    /// ones you haven't seen yet.
    @ViewBuilder
    private var progressBars: some View {
        if let current = model.currentEntry, current.totalForFriend > 1 {
            let friendEntries = model.entries
                .filter { $0.friendId == current.friendId }
                .sorted { $0.indexWithinFriend < $1.indexWithinFriend }
            GeometryReader { proxy in
                let count = CGFloat(friendEntries.count)
                let spacing: CGFloat = 4
                let available = proxy.size.width - 2 * 18
                let natural = 16 * count + spacing * (count - 1)
                let dashWidth = natural <= available ? 16 : max(3, (available - spacing * (count - 1)) / count)
                HStack(spacing: spacing) {
                    ForEach(friendEntries) { entry in
                        Capsule()
                            .fill(barColor(for: entry, current: current))
                            .frame(width: dashWidth, height: 3)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 12)
            }
            .allowsHitTesting(false)
            .animation(.easeOut(duration: 0.2), value: current.id)
        }
    }

    private func barColor(for entry: HomeCarouselEntry, current: HomeCarouselEntry) -> Color {
        if entry.indexWithinFriend == current.indexWithinFriend { return .white }
        if entry.indexWithinFriend >= current.indexWithinFriend, !entry.photo.seen { return theme.colors.accent }
        return Color.white.opacity(0.35)
    }

    @ViewBuilder
    private var footer: some View {
        if let current = model.currentEntry {
            HStack(alignment: .bottom) {
                timeLabel(for: current)
                Spacer()
                if current.streak >= 1 {
                    HStack(spacing: 5) {
                        Image(systemName: "flame.fill")
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(theme.colors.accent)
                        Text(verbatim: "\(current.streak)")
                            .font(.system(size: 16, weight: .bold).monospacedDigit())
                            .foregroundStyle(.white)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(Text(String(format: String(localized: Strings.Home.streakDays), current.streak)))
                }
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.bottom, Spacing.l)
            .allowsHitTesting(false)
        }
    }

    @ViewBuilder
    private func timeLabel(for entry: HomeCarouselEntry) -> some View {
        if let expiry = expiryDate(of: entry, in: model.entries) {
            HStack(spacing: 5) {
                Image(systemName: "clock")
                    .font(.system(size: 14))
                Text(verbatim: RelativeTime.remaining(until: expiry))
                    .font(.system(size: 14))
            }
            .foregroundStyle(Color.white.opacity(0.85))
        } else {
            Text(verbatim: RelativeTime.short(since: entry.photo.createdAt))
                .font(.system(size: 14))
                .foregroundStyle(Color.white.opacity(0.85))
        }
    }
}
