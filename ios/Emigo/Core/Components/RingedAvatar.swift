import SwiftUI

/// A profile picture inside a ring and a thin gap, like Android's friend row: the ring is quiet
/// grey normally and the brand accent when there is something new to see.
struct RingedAvatar: View {
    let name: String
    let photoURL: URL?
    let size: CGFloat
    var isHighlighted = false

    private let ringWidth: CGFloat = 2
    private let gapWidth: CGFloat = 2

    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            Circle().fill(isHighlighted ? theme.colors.accent : theme.colors.avatarRing)
            Circle().fill(theme.colors.backgroundTop).padding(ringWidth)
            AvatarView(name: name, photoURL: photoURL, size: size - 2 * (ringWidth + gapWidth))
        }
        .frame(width: size, height: size)
        .animation(.easeOut(duration: 0.15), value: isHighlighted)
    }
}
