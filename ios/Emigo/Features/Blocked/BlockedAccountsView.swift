import SwiftUI

struct BlockedAccountsView: View {
    let model: BlockedAccountsViewModel

    @Environment(\.theme) private var theme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                Text(Strings.Blocked.subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)

                if model.showsEmptyState {
                    Text(Strings.Blocked.empty)
                        .font(.system(size: 14))
                        .foregroundStyle(theme.colors.muted)
                        .frame(maxWidth: .infinity)
                        .padding(.top, Spacing.huge)
                }

                ForEach(model.blocked) { user in
                    HStack(spacing: Spacing.m) {
                        RingedAvatar(name: user.displayName, photoURL: user.profilePhotoUrl.flatMap { URL(string: $0) }, size: 52)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(verbatim: user.displayName)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundStyle(theme.colors.cream)
                            Text(verbatim: "@\(user.username)")
                                .font(.system(size: 13))
                                .foregroundStyle(theme.colors.muted)
                        }
                        Spacer(minLength: Spacing.xs)
                        Button { Task { await model.unblock(user) } } label: {
                            Text(Strings.Blocked.unblock)
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(theme.colors.cream)
                                .padding(.horizontal, Spacing.m)
                                .frame(height: 32)
                                .background(theme.colors.panel, in: Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, Spacing.l)
            .padding(.top, Spacing.xs)
            .padding(.bottom, Spacing.xl)
        }
        .background(theme.colors.background.ignoresSafeArea())
        .navigationTitle(Text(Strings.Blocked.title))
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.load() }
    }
}
