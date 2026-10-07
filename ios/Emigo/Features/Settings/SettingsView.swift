import SwiftUI

/// Settings, laid out like Android's: a flat list with big row titles, small section labels and a
/// Log out pill at the bottom.
struct SettingsView: View {
    let profile: UserProfile?
    let isGoldMember: Bool
    /// The name of the theme the app is wearing, shown on the Appearance row.
    let themeName: LocalizedStringResource
    @Bindable var model: SettingsViewModel
    let onOpenProfile: () -> Void
    let onOpenGold: () -> Void
    let onOpenAppearance: () -> Void
    let onOpenBlocked: () -> Void
    let onOpenOther: () -> Void
    let onLogOut: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.openURL) private var openURL
    @State private var isConfirmingLogOut = false

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Strings.Settings.title)

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    profileRow
                        .padding(.bottom, Spacing.xs)

                    goldRow

                    section(Strings.Settings.sectionPreferences)
                    notificationsRow
                    row(Strings.Settings.appearance, value: String(localized: themeName), action: onOpenAppearance)

                    section(Strings.Settings.sectionPrivacy)
                    row(Strings.Settings.blockedAccounts, action: onOpenBlocked)
                    row(Strings.Settings.privacyPolicy) { openURL(AppLinks.privacyPolicy) }

                    section(Strings.Settings.sectionSupport)
                    row(Strings.Settings.helpSupport) { openURL(AppLinks.supportMailURL(subject: String(localized: Strings.Settings.supportEmailSubject))) }
                    row(Strings.Settings.sendFeedback) { openURL(AppLinks.supportMailURL(subject: String(localized: Strings.Settings.feedbackEmailSubject))) }
                    row(Strings.Settings.aboutEmigo, value: AppConfiguration.marketingVersion, showsChevron: false)
                    row(Strings.Settings.other, action: onOpenOther)

                    logOutButton
                        .padding(.top, Spacing.xl)
                }
                .padding(.horizontal, Spacing.l)
                .padding(.bottom, Size.tabBarHeight + Spacing.xl)
            }
        }
        .task { await model.refresh() }
        .confirmationDialog(Text(Strings.Settings.logOutConfirmTitle), isPresented: $isConfirmingLogOut, titleVisibility: .visible) {
            Button(role: .destructive, action: onLogOut) { Text(Strings.Settings.logOut) }
            Button(role: .cancel) {} label: { Text(Strings.Common.cancel) }
        }
        .alert(Text(Strings.Settings.notificationsDeniedTitle), isPresented: $model.isShowingNotificationsDenied) {
            Button { openSystemSettings() } label: { Text(Strings.Settings.openSystemSettings) }
            Button(role: .cancel) {} label: { Text(Strings.Common.cancel) }
        } message: {
            Text(Strings.Settings.notificationsDeniedMessage)
        }
    }

    private func openSystemSettings() {
        if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
    }

    // MARK: - Pieces

    private var profileRow: some View {
        Button(action: onOpenProfile) {
            HStack(spacing: Spacing.m) {
                AvatarView(
                    name: profile?.displayName ?? "",
                    photoURL: profile?.profilePhotoUrl.flatMap { URL(string: $0) },
                    size: 52
                )
                VStack(alignment: .leading, spacing: 2) {
                    if let profile {
                        Text(verbatim: profile.displayName)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(theme.colors.cream)
                        Text(verbatim: "@\(profile.username)")
                            .font(.system(size: 12.5))
                            .foregroundStyle(theme.colors.muted)
                    } else {
                        Text(Strings.Settings.defaultAccountName)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(theme.colors.cream)
                    }
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(theme.colors.muted)
            }
            .padding(.vertical, Spacing.xs)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var goldRow: some View {
        Button(action: onOpenGold) {
            HStack(spacing: Spacing.s) {
                Text(Strings.Settings.gold)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(theme.colors.cream)
                Spacer(minLength: Spacing.s)
                Text(isGoldMember ? Strings.Settings.badgeGold : Strings.Settings.badgeFree)
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundStyle(isGoldMember ? theme.colors.accentText : theme.colors.muted)
                    .padding(.horizontal, Spacing.xs)
                    .padding(.vertical, 3)
                    .background(isGoldMember ? theme.colors.accent : theme.colors.panel, in: Capsule())
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(theme.colors.muted)
            }
            .frame(minHeight: 46)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    private var notificationsRow: some View {
        let isOn = Binding(
            get: { model.notificationsEnabled },
            set: { enabled in Task { await model.setNotifications(enabled) } }
        )
        return Toggle(isOn: isOn) {
            Text(Strings.Settings.notifications)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.colors.cream)
        }
        .tint(theme.colors.accent)
        .frame(minHeight: 46)
    }

    private func section(_ title: LocalizedStringResource) -> some View {
        SectionCaption(title)
            .padding(.top, Spacing.xl)
            .padding(.bottom, Spacing.xxs)
    }

    private func row(
        _ title: LocalizedStringResource,
        value: String? = nil,
        showsChevron: Bool = true,
        action: (() -> Void)? = nil
    ) -> some View {
        let content = HStack {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.colors.cream)
            Spacer(minLength: Spacing.s)
            if let value {
                Text(verbatim: value)
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)
            }
            if showsChevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(theme.colors.muted)
            }
        }
        .frame(minHeight: 46)
        .contentShape(Rectangle())

        return Group {
            if let action {
                Button(action: action) { content }.buttonStyle(.plain)
            } else {
                content
            }
        }
    }

    private var logOutButton: some View {
        Button { isConfirmingLogOut = true } label: {
            HStack(spacing: Spacing.xs) {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                Text(Strings.Settings.logOut)
            }
            .font(.system(size: 18, weight: .semibold))
            .foregroundStyle(EmigoFixedColors.destructive)
            .frame(maxWidth: .infinity)
            .frame(height: 54)
            .background(theme.colors.cream, in: Capsule())
        }
        .buttonStyle(.plain)
    }
}
