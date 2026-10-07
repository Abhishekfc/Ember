import SwiftUI

struct HomeView: View {
    @Bindable var model: HomeViewModel
    let activityBadge: Int
    let onOpenCamera: () -> Void
    let onOpenProfile: () -> Void
    let onOpenActivity: () -> Void
    let onOpenFindPeople: () -> Void

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            header
            HomeModeToggle(mode: $model.mode)
                .padding(.top, Spacing.m)
            content
                .padding(.top, Spacing.l)
        }
        .task { await model.load() }
    }

    /// The same size as Android's: a small logo and two 36-point round buttons, tucked right
    /// under the status bar.
    private var header: some View {
        HStack(spacing: Spacing.xs) {
            Text(verbatim: "Emigo")
                .font(EmigoFont.wordmark(size: 28))
                .foregroundStyle(theme.colors.cream)
            Spacer()
            RoundIconButton(symbol: "bell.fill", label: Strings.Home.activity, badgeCount: activityBadge, action: onOpenActivity)
            RoundIconButton(symbol: "person.fill", label: Strings.Home.profile, action: onOpenProfile)
        }
        .padding(.horizontal, Spacing.m)
        .frame(height: 36)
    }

    /// The photo card at Android's size: 18-point side margins and a 4 : 5 shape. On a short
    /// phone it shrinks, keeping that shape, so the friend row still fits above the dock.
    private var homeDeck: some View {
        GeometryReader { proxy in
            let gap = Spacing.m
            let fullWidth = proxy.size.width - 2 * Size.cardSidePadding
            let cardHeight = max(min(fullWidth / Size.cardAspectRatio, proxy.size.height - Size.friendRowHeight - gap), 0)
            let cardWidth = cardHeight * Size.cardAspectRatio
            VStack(spacing: gap) {
                FeaturedPhotoCard(model: model)
                    .frame(width: cardWidth, height: cardHeight)
                FriendAvatarRow(model: model, onAddFriend: onOpenFindPeople)
                    .frame(height: Size.friendRowHeight)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
        }
    }

    /// Fills the space between the pills and the dock. The photo deck doesn't scroll: the card
    /// takes whatever room is left. Only the Moments grid scrolls.
    @ViewBuilder
    private var content: some View {
        if model.showsSkeleton {
            HomeSkeleton()
        } else if model.showsConnectError {
            Button {
                Task { await model.load(forceRefresh: true) }
            } label: {
                Text(Strings.Home.connectError)
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .buttonStyle(.plain)
        } else if model.showsEmptyState {
            HomeEmptyState(mode: model.mode, hasFriends: model.hasFriends, onOpenFindPeople: onOpenFindPeople)
        } else {
            switch model.mode {
            case .home:
                homeDeck
            case .moments:
                ScrollView {
                    MomentsGrid(model: model)
                        .padding(.bottom, Size.tabBarHeight + Spacing.xl)
                }
                .refreshable { await model.load(forceRefresh: true) }
            }
        }
    }
}

/// "Home | Moments": two pills. The chosen one is a Liquid Glass capsule that slides across.
struct HomeModeToggle: View {
    @Binding var mode: HomeMode

    @Environment(\.theme) private var theme
    @Namespace private var selection

    var body: some View {
        HStack(spacing: 9) {
            pill(.home, title: Strings.Home.modeHome)
            pill(.moments, title: Strings.Home.modeMoments)
        }
        .sensoryFeedback(.selection, trigger: mode)
    }

    private func pill(_ value: HomeMode, title: LocalizedStringResource) -> some View {
        let isSelected = mode == value
        return Button {
            withAnimation(.spring(duration: 0.35, bounce: 0.2)) { mode = value }
        } label: {
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(isSelected ? theme.colors.cream : theme.colors.muted)
                .frame(width: 97, height: 31)
                .background {
                    if isSelected {
                        // A plain dark fill, like Android's. (Glass here blurred the label.)
                        Capsule().fill(Color.white.opacity(0.12))
                            .matchedGeometryEffect(id: "pill", in: selection)
                    } else {
                        Capsule().strokeBorder(theme.colors.border, lineWidth: 1)
                    }
                }
                .contentShape(Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
