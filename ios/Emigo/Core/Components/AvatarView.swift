import SwiftUI

/// A round profile picture, or the person's initial on a flat tile when they have none.
struct AvatarView: View {
    let name: String
    let photoURL: URL?
    let size: CGFloat

    @Environment(\.theme) private var theme

    var body: some View {
        Group {
            if let photoURL {
                RemoteImage(url: photoURL, pointSize: size)
            } else {
                ZStack {
                    theme.colors.elevatedPanel
                    Text(initial)
                        .displayFont(size * 0.42, weight: .semibold, relativeTo: .body)
                        .foregroundStyle(theme.colors.cream)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(Circle())
        .accessibilityHidden(true)
    }

    private var initial: String {
        name.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? ""
    }
}
