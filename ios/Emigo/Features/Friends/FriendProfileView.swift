import SwiftUI

/// Someone's profile: a big picture with their name, streak and last photo over it, and what you
/// can do (send them a photo, pin them, accept or add them), with block and report in a menu.
/// Same layout as Android's.
struct FriendProfileView: View {
    @Bindable var model: FriendProfileViewModel
    let onBack: () -> Void
    /// Opens the camera with this person chosen.
    let onSendPhoto: (String) -> Void
    /// Something changed that other screens show (a friendship, a pin).
    let onChanged: () -> Void
    /// Leave this screen: the person is no longer a friend, was declined or blocked.
    let onClose: () -> Void

    @Environment(\.theme) private var theme
    @State private var isConfirmingBlock = false
    @State private var isConfirmingUnfriend = false
    @State private var isReporting = false

    private var subject: ProfileSubject { model.subject }

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top
            VStack(spacing: 0) {
                // The picture is square and runs up under the status bar, so the part below the
                // status bar is a little shorter than the width.
                hero(height: max(proxy.size.width - top, 200))
                VStack(spacing: Spacing.xs) {
                    if let message = model.errorMessage {
                        Text(verbatim: message)
                            .font(.system(size: 12))
                            .foregroundStyle(EmigoFixedColors.errorText)
                            .multilineTextAlignment(.center)
                    }
                    actions
                }
                .padding(.horizontal, 18)
                .padding(.top, 18)
                Spacer(minLength: 0)
            }
        }
        .background(theme.colors.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
        .task { await model.resolveExistingFriend() }
        .animation(.easeOut(duration: 0.2), value: model.errorMessage)
        .confirmationDialog(
            Text(String(format: String(localized: Strings.FriendProfile.blockTitle), subject.displayName)),
            isPresented: $isConfirmingBlock,
            titleVisibility: .visible
        ) {
            Button(role: .destructive) { block() } label: { Text(Strings.FriendProfile.block) }
            Button(role: .cancel) {} label: { Text(Strings.Common.cancel) }
        } message: {
            Text(Strings.FriendProfile.blockWarning)
        }
        .confirmationDialog(
            Text(String(format: String(localized: Strings.FriendProfile.unfriendTitle), subject.displayName)),
            isPresented: $isConfirmingUnfriend,
            titleVisibility: .visible
        ) {
            Button(role: .destructive) { unfriend() } label: { Text(Strings.FriendProfile.unfriend) }
            Button(role: .cancel) {} label: { Text(Strings.Common.cancel) }
        } message: {
            Text(Strings.FriendProfile.unfriendWarning)
        }
        .sheet(isPresented: $isReporting, onDismiss: { model.dismissReportConfirmation() }) {
            ReportSheet(model: model)
        }
    }

    // MARK: - Hero

    private func hero(height: CGFloat) -> some View {
        ZStack {
            VStack {
                HStack {
                    heroButton(symbol: "chevron.left", label: Strings.Common.back, action: onBack)
                    Spacer()
                    overflowMenu
                }
                Spacer()
                details
            }
            .padding(.horizontal, Spacing.m)
            .padding(.top, Spacing.xs)
            .padding(.bottom, 18)
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .background {
            heroBackground
                .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: 28, bottomTrailingRadius: 28, style: .continuous))
                .ignoresSafeArea(edges: .top)
        }
    }

    private var heroBackground: some View {
        ZStack {
            if let url = subject.profilePhotoURL {
                RemoteImage(url: url, pointSize: 900)
            } else {
                LinearGradient(colors: [theme.colors.accent, theme.colors.accent2], startPoint: .topLeading, endPoint: .bottomTrailing)
                Text(verbatim: subject.displayName.first.map { String($0).uppercased() } ?? "")
                    .displayFont(96, weight: .bold, relativeTo: .largeTitle)
                    .foregroundStyle(theme.colors.accentText)
            }
            // A dark fade at the bottom keeps the name readable on any picture.
            LinearGradient(
                stops: [.init(color: .black.opacity(0.35), location: 0), .init(color: .clear, location: 0.22),
                        .init(color: .clear, location: 0.5), .init(color: .black.opacity(0.7), location: 1)],
                startPoint: .top,
                endPoint: .bottom
            )
        }
    }

    private func heroButton(symbol: String, label: LocalizedStringResource, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: symbol)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 38, height: 38)
                .liquidGlass(in: Circle(), interactive: true)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
    }

    private var overflowMenu: some View {
        Menu {
            if subject.friend != nil {
                Button(role: .destructive) { isConfirmingUnfriend = true } label: {
                    Label { Text(Strings.FriendProfile.unfriend) } icon: { Image(systemName: "person.badge.minus") }
                }
            }
            Button(role: .destructive) { isConfirmingBlock = true } label: {
                Label { Text(Strings.FriendProfile.block) } icon: { Image(systemName: "nosign") }
            }
            Button { isReporting = true } label: {
                Label { Text(Strings.FriendProfile.report) } icon: { Image(systemName: "flag") }
            }
        } label: {
            Image(systemName: "ellipsis")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: 38, height: 38)
                .liquidGlass(in: Circle(), interactive: true)
        }
        .accessibilityLabel(Text(Strings.Common.moreOptions))
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 4) {
            if let streak = subject.friend?.streak, streak > 0 {
                HStack(spacing: 5) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(theme.colors.accent)
                    Text(verbatim: "\(streak)")
                        .font(.system(size: 13, weight: .semibold).monospacedDigit())
                        .foregroundStyle(.white)
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 5)
                .background(Color.black.opacity(0.45), in: Capsule())
                .accessibilityLabel(Text(String(format: String(localized: Strings.Home.streakDays), streak)))
                .padding(.bottom, 4)
            }
            HStack(spacing: 6) {
                Text(verbatim: subject.displayName)
                    .displayFont(26, weight: .bold, relativeTo: .title)
                    .foregroundStyle(.white)
                if subject.friend?.pinnedByMe == true {
                    Image(systemName: "pin.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(theme.colors.accent)
                        .accessibilityLabel(Text(Strings.FriendProfile.pinnedAsPartner))
                }
            }
            Text(verbatim: "@\(subject.username)")
                .font(.system(size: 13))
                .foregroundStyle(.white.opacity(0.75))
            if let friend = subject.friend, let last = friend.lastActivityAt {
                let format = friend.lastActivityBySelf == true ? Strings.Friends.youSent : Strings.Friends.sentToYou
                Text(verbatim: String(format: String(localized: format), RelativeTime.short(since: last)))
                    .font(.system(size: 13))
                    .foregroundStyle(.white.opacity(0.7))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: - Actions

    @ViewBuilder
    private var actions: some View {
        switch subject {
        case .friend(let friend):
            ProfilePill(title: Strings.FriendProfile.sendPhoto, symbol: "camera.fill", style: .primary, isLoading: false) {
                onSendPhoto(friend.friendId)
            }
            ProfilePill(
                title: friend.pinnedByMe ? Strings.FriendProfile.pinnedAsPartner : Strings.FriendProfile.pinAsPartner,
                symbol: friend.pinnedByMe ? "pin.fill" : "pin",
                style: friend.pinnedByMe ? .highlighted : .outlined,
                isLoading: model.running == .pin
            ) {
                Task { if await model.togglePin() { onChanged() } }
            }
            .disabled(model.isBusy)
        case .pendingRequest:
            acceptAndDecline
        case .searchResult(let result):
            if result.isPendingFromThem {
                acceptAndDecline
            } else if result.requested || result.isPendingFromMe {
                ProfilePill(title: Strings.FriendProfile.cancelRequest, symbol: "xmark.circle", style: .outlined, isLoading: model.running == .cancelRequest) {
                    Task { if await model.cancelRequest() { onChanged() } }
                }
                .disabled(model.isBusy)
            } else {
                ProfilePill(title: Strings.FindPeople.add, symbol: "person.badge.plus", style: .primary, isLoading: model.running == .sendRequest) {
                    Task { if await model.sendRequest() { onChanged() } }
                }
                .disabled(model.isBusy)
            }
        }
    }

    @ViewBuilder
    private var acceptAndDecline: some View {
        ProfilePill(title: Strings.Friends.accept, symbol: "checkmark.circle.fill", style: .primary, isLoading: model.running == .accept) {
            Task { if await model.acceptRequest() { onChanged() } }
        }
        .disabled(model.isBusy)
        ProfilePill(title: Strings.Friends.decline, symbol: "xmark.circle", style: .outlined, isLoading: model.running == .reject) {
            Task { if await model.rejectRequest() { onChanged(); onClose() } }
        }
        .disabled(model.isBusy)
    }

    private func block() {
        Task { if await model.blockUser() { onChanged(); onClose() } }
    }

    private func unfriend() {
        Task { if await model.removeFriend() { onChanged(); onClose() } }
    }
}

/// A full-width pill for one action on a profile.
private struct ProfilePill: View {
    enum Style { case primary, outlined, highlighted }

    let title: LocalizedStringResource
    let symbol: String
    let style: Style
    let isLoading: Bool
    let action: () -> Void

    @Environment(\.theme) private var theme
    @State private var tapCount = 0

    var body: some View {
        Button {
            tapCount += 1
            action()
        } label: {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView().tint(foreground).controlSize(.small)
                } else {
                    Image(systemName: symbol).font(.system(size: 15, weight: .semibold))
                }
                Text(title).font(.system(size: 14, weight: .bold))
            }
            .foregroundStyle(foreground)
            .frame(maxWidth: .infinity)
            .frame(height: 48)
            .background {
                switch style {
                case .primary: Capsule().fill(theme.colors.accent)
                case .outlined: Capsule().strokeBorder(theme.colors.border, lineWidth: 1)
                case .highlighted: Capsule().strokeBorder(theme.colors.accent, lineWidth: 1)
                }
            }
        }
        .buttonStyle(SqueezeButtonStyle(scale: 0.97))
        .sensoryFeedback(.impact(weight: .light), trigger: tapCount)
    }

    private var foreground: Color {
        switch style {
        case .primary: theme.colors.accentText
        case .outlined: theme.colors.cream
        case .highlighted: theme.colors.accent
        }
    }
}

/// "What's wrong with this account?": pick a reason and send it. Afterwards it says thanks.
private struct ReportSheet: View {
    @Bindable var model: FriendProfileViewModel

    @Environment(\.theme) private var theme
    @Environment(\.dismiss) private var dismiss
    @State private var selected: ReportReason?

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            Text(model.reportSubmitted ? Strings.Report.submittedTitle : Strings.Report.title)
                .displayFont(20, weight: .bold, relativeTo: .title3)
                .foregroundStyle(theme.colors.cream)

            if model.reportSubmitted {
                Text(Strings.Report.thanks)
                    .font(.system(size: 14))
                    .foregroundStyle(theme.colors.muted)
                Spacer(minLength: 0)
                EmigoButton(title: Strings.Common.done) { dismiss() }
            } else {
                Text(Strings.Report.question)
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)
                VStack(spacing: 4) {
                    ForEach(ReportReason.allCases, id: \.self) { reason in
                        reasonRow(reason)
                    }
                }
                if let message = model.errorMessage {
                    Text(verbatim: message).font(.system(size: 12)).foregroundStyle(EmigoFixedColors.errorText)
                }
                Spacer(minLength: 0)
                EmigoButton(title: Strings.Report.submit, isLoading: model.running == .report, isEnabled: selected != nil) {
                    guard let selected else { return }
                    Task { _ = await model.reportUser(reason: selected) }
                }
            }
        }
        .padding(Spacing.xl)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(theme.colors.backgroundTop.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func reasonRow(_ reason: ReportReason) -> some View {
        let isSelected = selected == reason
        return Button {
            selected = reason
        } label: {
            HStack(spacing: Spacing.s) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .font(.system(size: 18))
                    .foregroundStyle(isSelected ? theme.colors.accent : theme.colors.mutedDim)
                Text(Self.label(for: reason))
                    .font(.system(size: 14))
                    .foregroundStyle(theme.colors.cream)
                Spacer()
            }
            .padding(.horizontal, Spacing.s)
            .frame(height: 44)
            .background(isSelected ? theme.colors.accent.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: isSelected)
    }

    private static func label(for reason: ReportReason) -> LocalizedStringResource {
        switch reason {
        case .spam: Strings.Report.reasonSpam
        case .harassment: Strings.Report.reasonHarassment
        case .inappropriateContent: Strings.Report.reasonInappropriate
        case .fakeAccount: Strings.Report.reasonFakeAccount
        case .other: Strings.Report.reasonOther
        }
    }
}
