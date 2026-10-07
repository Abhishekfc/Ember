import SwiftUI

/// The row of friends under the Home card: the one whose photo is showing is a little larger and
/// brighter, and a ring in the accent colour means a photo you haven't seen.
struct FriendAvatarRow: View {
    let model: HomeViewModel
    let onAddFriend: () -> Void

    @Environment(\.theme) private var theme

    private let itemWidth: CGFloat = 84

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 4) {
                    ForEach(model.feedItems) { item in
                        friend(item).id(item.friendId)
                    }
                    addFriend
                }
                .padding(.horizontal, Spacing.l)
            }
            .onChange(of: model.activeFriendId) { _, friendId in
                guard let friendId else { return }
                withAnimation(.easeInOut(duration: 0.32)) { proxy.scrollTo(friendId, anchor: .center) }
            }
        }
        .sensoryFeedback(.selection, trigger: model.activeFriendId)
    }

    private func friend(_ item: FeedItem) -> some View {
        let isActive = item.friendId == model.activeFriendId
        return Button {
            model.select(friendId: item.friendId)
        } label: {
            VStack(spacing: 7) {
                RingedAvatar(
                    name: item.displayName,
                    photoURL: model.profilePhotoURL(for: item.friendId),
                    size: isActive ? Size.avatarActive : Size.avatarInactive,
                    isHighlighted: model.hasUnseenPhoto(friendId: item.friendId)
                )
                .frame(height: Size.avatarActive)
                Text(verbatim: item.displayName.split(separator: " ").first.map(String.init) ?? item.displayName)
                    .font(.system(size: 13, weight: isActive ? .semibold : .regular))
                    .foregroundStyle(isActive ? theme.colors.cream : theme.colors.cream.opacity(0.85))
                    .lineLimit(1)
            }
            .frame(width: itemWidth)
            .animation(.easeInOut(duration: 0.3), value: isActive)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(verbatim: item.displayName))
        .accessibilityAddTraits(isActive ? .isSelected : [])
    }

    private var addFriend: some View {
        Button(action: onAddFriend) {
            VStack(spacing: 7) {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(theme.colors.muted)
                    .frame(width: Size.avatarInactive, height: Size.avatarInactive)
                    .overlay {
                        Circle().strokeBorder(theme.colors.mutedDim, style: StrokeStyle(lineWidth: 2, dash: [5, 5]))
                    }
                    .frame(height: Size.avatarActive)
                Text(Strings.Home.addShort)
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)
                    .lineLimit(1)
            }
            .frame(width: itemWidth)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(Strings.Home.addFriend))
    }
}
