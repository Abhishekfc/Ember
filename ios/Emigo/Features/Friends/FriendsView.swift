import SwiftUI

struct FriendsView: View {
    @Bindable var model: FriendsViewModel
    let onOpenFindPeople: () -> Void
    let onOpenProfile: (ProfileSubject) -> Void

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: 0) {
            ScreenHeader(title: Strings.Friends.title) {
                RoundIconButton(symbol: "person.badge.plus", label: Strings.Friends.findPeople, action: onOpenFindPeople)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    SearchField(placeholder: Strings.Friends.searchHint, text: $model.query)

                    if !model.requests.isEmpty && model.query.isEmpty {
                        requests
                    }
                    if model.showsPinned, let pinned = model.pinned {
                        SectionCaption(Strings.Friends.sectionYourEmigo)
                        Button { onOpenProfile(.friend(pinned)) } label: {
                            PinnedPartnerCard(friend: pinned)
                        }
                        .buttonStyle(.plain)
                    }
                    list
                }
                .padding(.horizontal, Spacing.l)
                .padding(.bottom, Size.tabBarHeight + Spacing.xl)
            }
            .scrollDismissesKeyboard(.interactively)
            .refreshable { await model.load(forceRefresh: true) }
            .overlay {
                if model.showsEmptyState {
                    EmptyPlaceholder(symbol: "person.2.fill", title: Strings.Friends.empty, detail: nil)
                }
            }
        }
        .task { await model.load() }
        .alert(model.errorMessage ?? "", isPresented: Binding(
            get: { model.errorMessage != nil },
            set: { if !$0 { model.dismissError() } }
        )) {
            Button(role: .cancel) {} label: { Text(Strings.Common.close) }
        }
    }

    // MARK: - Sections

    private var requests: some View {
        VStack(alignment: .leading, spacing: Spacing.s) {
            SectionCaption(verbatim: String(format: String(localized: Strings.Friends.sectionRequests), model.requests.count))
            ForEach(model.requests) { request in
                HStack(spacing: Spacing.s) {
                    Button { onOpenProfile(.pendingRequest(request)) } label: {
                        HStack(spacing: Spacing.s) {
                            RingedAvatar(name: request.displayName, photoURL: request.profilePhotoUrl.flatMap { URL(string: $0) }, size: 48)
                            VStack(alignment: .leading, spacing: 2) {
                                Text(verbatim: request.displayName)
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundStyle(theme.colors.cream)
                                Text(verbatim: "@\(request.username)")
                                    .font(.system(size: 12.5))
                                    .foregroundStyle(theme.colors.muted)
                            }
                            Spacer(minLength: 0)
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    Spacer(minLength: Spacing.xs)
                    Button { Task { await model.decline(request) } } label: {
                        Text(Strings.Friends.decline)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(theme.colors.muted)
                            .padding(.horizontal, Spacing.xs)
                            .frame(height: 32)
                    }
                    .buttonStyle(.plain)
                    Button { Task { await model.accept(request) } } label: {
                        Text(Strings.Friends.accept)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(theme.colors.accentText)
                            .padding(.horizontal, Spacing.m)
                            .frame(height: 32)
                            .background(theme.colors.accent, in: Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private var list: some View {
        if model.visibleFriends.isEmpty {
            if !model.query.isEmpty {
                Text(String(format: String(localized: Strings.Friends.noMatch), model.query))
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.top, Spacing.xl)
            }
        } else {
            SectionCaption(Strings.Friends.sectionMyFriends)
                .padding(.top, Spacing.xs)
            LazyVStack(alignment: .leading, spacing: Spacing.l) {
                ForEach(model.visibleFriends) { friend in
                    Button { onOpenProfile(.friend(friend)) } label: {
                        FriendRow(friend: friend)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}

/// The pinned friend as a big square card: their picture, name, and your streak together.
private struct PinnedPartnerCard: View {
    let friend: FriendSummary

    @Environment(\.theme) private var theme

    var body: some View {
        RemoteImage(url: friend.profilePhotoUrl.flatMap { URL(string: $0) }, pointSize: 700)
            .aspectRatio(1, contentMode: .fit)
            .overlay {
                LinearGradient(
                    stops: [.init(color: .clear, location: 0.55), .init(color: .black.opacity(0.65), location: 1)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .overlay(alignment: .bottomLeading) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(verbatim: friend.displayName)
                        .displayFont(22, weight: .medium, relativeTo: .title2)
                    HStack(spacing: 6) {
                        Image(systemName: "pin.fill").foregroundStyle(theme.colors.accent)
                        Text(Strings.Friends.pinnedPartner)
                    }
                    .font(.system(size: 12))
                }
                .foregroundStyle(EmigoFixedColors.onPhotoText)
                .padding(Spacing.l)
            }
            .overlay(alignment: .bottomTrailing) {
                if friend.streak > 0 {
                    HStack(spacing: 5) {
                        Image(systemName: "flame.fill").foregroundStyle(theme.colors.accent)
                        Text(verbatim: "\(friend.streak)").font(.system(size: 15, weight: .semibold).monospacedDigit())
                    }
                    .foregroundStyle(.white)
                    .padding(Spacing.l)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Radius.card, style: .continuous))
            .accessibilityElement(children: .combine)
    }
}

private struct FriendRow: View {
    let friend: FriendSummary

    @Environment(\.theme) private var theme

    var body: some View {
        HStack(spacing: Spacing.m) {
            RingedAvatar(name: friend.displayName, photoURL: friend.profilePhotoUrl.flatMap { URL(string: $0) }, size: 56)

            VStack(alignment: .leading, spacing: 3) {
                Text(verbatim: friend.displayName)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(theme.colors.cream)
                Text(verbatim: subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)
            }
            Spacer(minLength: 0)
        }
        .accessibilityElement(children: .combine)
    }

    private var subtitle: String {
        guard let last = friend.lastActivityAt else { return String(localized: Strings.Friends.noPhotosYet) }
        let when = RelativeTime.short(since: last)
        let format = friend.lastActivityBySelf == true ? Strings.Friends.youSent : Strings.Friends.sentToYou
        return String(format: String(localized: format), when)
    }
}
