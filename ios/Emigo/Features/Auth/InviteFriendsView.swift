import SwiftUI

/// The last step of creating an account: invite a first friend, through the apps people already
/// use. Skipping is always one tap away; either way the person lands in the app.
struct InviteFriendsView: View {
    let model: AuthFlowViewModel

    @Environment(\.theme) private var theme
    @Environment(\.openURL) private var openURL
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hasAppeared = false
    @State private var isSharing = false
    @State private var didCopy = false

    private var message: String { InviteMessage.text(username: model.usernameDraft) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: Spacing.xs) {
                    Text(Strings.Invite.title)
                        .displayFont(34, weight: .bold, relativeTo: .largeTitle)
                        .foregroundStyle(theme.colors.cream)
                        .accessibilityAddTraits(.isHeader)
                    Text(Strings.Invite.subtitle)
                        .font(.system(size: 15))
                        .foregroundStyle(theme.colors.muted)
                }
                .appearing(order: 0, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

                sectionLabel(Strings.Invite.sectionFrom)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, Spacing.s)
                quickRow
                    .appearing(order: 1, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

                sectionLabel(Strings.Invite.sectionLink)
                    .padding(.top, Spacing.xl)
                    .padding(.bottom, Spacing.xxs)
                VStack(spacing: 0) {
                    copyRow
                    brandRow(.whatsapp, title: Text(verbatim: "WhatsApp"), subtitle: Text(Strings.Invite.whatsappSubtitle))
                    brandRow(.instagram, title: Text(Strings.Invite.instagramDMTitle), subtitle: Text(Strings.Invite.instagramDMSubtitle))
                    storyRow
                    messagesRow
                }
                .appearing(order: 2, hasAppeared: hasAppeared, reduceMotion: reduceMotion)
            }
            .padding(.horizontal, Spacing.xl)
            .padding(.top, Spacing.l)
            .padding(.bottom, Spacing.l)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) { footer }
        .background(theme.colors.background.ignoresSafeArea())
        // Nowhere sensible to go back to: the account already exists.
        .navigationBarBackButtonHidden(true)
        .navigationBarTitleDisplayMode(.inline)
        .toolbarBackground(.hidden, for: .navigationBar)
        .sheet(isPresented: $isSharing) {
            ShareSheet(text: message)
                .presentationDetents([.medium, .large])
        }
        .sensoryFeedback(.success, trigger: didCopy) { _, copied in copied }
        .onAppear { hasAppeared = true }
    }

    // MARK: - Pieces

    private func sectionLabel(_ text: LocalizedStringResource) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .bold))
            .tracking(1.1)
            .foregroundStyle(theme.colors.mutedDim)
    }

    /// The three big app icons, and "More" for everything else.
    private var quickRow: some View {
        HStack(alignment: .top) {
            ForEach(InviteTarget.allCases) { target in
                Button { target.send(message: message, openURL: openURL) { isSharing = true } } label: {
                    VStack(spacing: Spacing.xs) {
                        brandIcon(target.imageName, size: 62)
                        Text(verbatim: target.displayName)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(theme.colors.muted)
                    }
                    .frame(maxWidth: .infinity)
                }
                .buttonStyle(AuthPressStyle())
            }
            Button { isSharing = true } label: {
                VStack(spacing: Spacing.xs) {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 26, weight: .medium))
                        .foregroundStyle(theme.colors.accent)
                        .frame(width: 62, height: 62)
                    Text(Strings.Invite.more)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(theme.colors.muted)
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(AuthPressStyle())
        }
    }

    private var copyRow: some View {
        row(
            icon: glyph("doc.on.doc"),
            title: Text(Strings.Invite.copyTitle),
            subtitle: Text(didCopy ? Strings.Invite.copied : Strings.Invite.copySubtitle)
        ) {
            UIPasteboard.general.string = message
            didCopy = true
            Task {
                try? await Task.sleep(for: .seconds(2))
                didCopy = false
            }
        }
    }

    private func brandRow(_ target: InviteTarget, title: Text, subtitle: Text) -> some View {
        row(icon: brandIcon(target.imageName, size: 46), title: title, subtitle: subtitle) {
            target.send(message: message, openURL: openURL) { isSharing = true }
        }
    }

    private var storyRow: some View {
        row(
            icon: brandIcon(InviteTarget.instagram.imageName, size: 46),
            title: Text(Strings.Invite.instagramStoryTitle),
            subtitle: Text(Strings.Invite.instagramStorySubtitle)
        ) {
            UIPasteboard.general.string = message
            guard let url = URL(string: "instagram://story-camera") else { return }
            openURL(url) { accepted in
                if !accepted { isSharing = true }
            }
        }
    }

    private var messagesRow: some View {
        row(
            icon: glyph("message"),
            title: Text(Strings.Invite.messagesTitle),
            subtitle: Text(Strings.Invite.messagesSubtitle)
        ) {
            // `sms:&body=` is how a message is prefilled, with the text percent-encoded.
            let encoded = message.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            guard let url = URL(string: "sms:&body=\(encoded)") else {
                isSharing = true
                return
            }
            openURL(url) { accepted in
                if !accepted { isSharing = true }
            }
        }
    }

    /// One tappable line: an icon, what it is, what it does, and a chevron.
    private func row(icon: some View, title: Text, subtitle: Text, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: Spacing.m) {
                icon
                VStack(alignment: .leading, spacing: 2) {
                    title
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(theme.colors.cream)
                    subtitle
                        .font(.system(size: 13))
                        .foregroundStyle(theme.colors.mutedDim)
                }
                Spacer(minLength: Spacing.xs)
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(theme.colors.mutedDim)
            }
            .padding(.vertical, Spacing.s)
            .contentShape(Rectangle())
        }
        .buttonStyle(AuthPressStyle())
    }

    private func brandIcon(_ name: String, size: CGFloat) -> some View {
        Image(name)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: size * 0.23, style: .continuous))
    }

    private func glyph(_ name: String) -> some View {
        Image(systemName: name)
            .font(.system(size: 22, weight: .medium))
            .foregroundStyle(theme.colors.accent)
            .frame(width: 46, height: 46)
    }

    private var footer: some View {
        VStack(spacing: Spacing.xs) {
            AuthSecondaryButton(title: Strings.Invite.skip, action: model.finishOnboarding)
            Text(Strings.Invite.tagline)
                .font(.system(size: 12))
                .foregroundStyle(theme.colors.mutedDim)
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.top, Spacing.s)
        .padding(.bottom, Spacing.xs)
        .frame(maxWidth: .infinity)
        .background(theme.colors.backgroundBottom)
    }
}
