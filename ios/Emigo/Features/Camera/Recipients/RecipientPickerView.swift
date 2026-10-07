import SwiftUI

/// "Send to": tick the friends a photo goes to, with quick groups (Recent, Everyone, your own
/// lists) at the top. Opens from the pill in the camera header.
struct RecipientPickerView: View {
    @Bindable var model: RecipientPickerViewModel
    /// Called with the chosen friends when the person taps Continue.
    let onDone: ([String]) -> Void
    let onFindFriends: () -> Void

    @Environment(\.theme) private var theme
    @State private var isNamingList = false
    @State private var newListName = ""
    @State private var listPendingDelete: RecipientList?

    var body: some View {
        VStack(spacing: 0) {
            ScrollView {
                VStack(alignment: .leading, spacing: Spacing.m) {
                    SearchField(placeholder: Strings.Friends.searchHint, text: $model.searchQuery)
                    if !model.friends.isEmpty { badges }
                    content
                }
                .padding(.horizontal, Spacing.l)
                .padding(.top, Spacing.xs)
                .padding(.bottom, Spacing.l)
            }
            .scrollDismissesKeyboard(.interactively)

            if !model.friends.isEmpty {
                EmigoButton(title: Strings.Common.continue, isEnabled: !model.selectedFriendIds.isEmpty) {
                    onDone(model.selectionInOrder)
                }
                .padding(.horizontal, Spacing.l)
                .padding(.vertical, Spacing.s)
            }
        }
        .background(theme.colors.background.ignoresSafeArea())
        .navigationTitle(Text(Strings.Recipients.title))
        .navigationBarTitleDisplayMode(.inline)
        .task { await model.load() }
        .alert(Text(Strings.Recipients.createList), isPresented: $isNamingList) {
            TextField(text: $newListName, prompt: Text(Strings.Recipients.nameListHint)) { EmptyView() }
            Button { Task { await model.createList(named: newListName); newListName = "" } } label: { Text(Strings.Common.save) }
            Button(role: .cancel) { newListName = "" } label: { Text(Strings.Common.cancel) }
        }
        .confirmationDialog(
            Text(String(format: String(localized: Strings.Recipients.deleteListTitle), listPendingDelete?.name ?? "")),
            isPresented: Binding(get: { listPendingDelete != nil }, set: { if !$0 { listPendingDelete = nil } }),
            titleVisibility: .visible
        ) {
            Button(role: .destructive) {
                if let list = listPendingDelete { Task { await model.deleteList(list.id) } }
                listPendingDelete = nil
            } label: { Text(Strings.Common.delete) }
            Button(role: .cancel) { listPendingDelete = nil } label: { Text(Strings.Common.cancel) }
        }
        .alert(model.errorMessage ?? "", isPresented: Binding(
            get: { model.errorMessage != nil && !model.friends.isEmpty },
            set: { if !$0 { model.dismissError() } }
        )) {
            Button(role: .cancel) {} label: { Text(Strings.Common.close) }
        }
    }

    // MARK: - Quick groups

    private var badges: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: Spacing.xs) {
                if !model.recentIds.isEmpty {
                    badge(Strings.Recipients.badgeRecent, isOn: model.activeFilterId == RecipientPickerViewModel.recentBadgeId) {
                        model.selectRecent()
                    }
                }
                badge(Strings.Recipients.badgeEveryone, isOn: model.activeFilterId == RecipientPickerViewModel.everyoneBadgeId) {
                    model.selectEveryone()
                }
                ForEach(model.customLists) { list in
                    badge(verbatim: list.name, isOn: model.activeFilterId == list.id) { model.selectList(list) }
                        .contextMenu {
                            Button(role: .destructive) { listPendingDelete = list } label: {
                                Label { Text(Strings.Common.delete) } icon: { Image(systemName: "trash") }
                            }
                        }
                }
                if !model.selectedFriendIds.isEmpty {
                    Button { isNamingList = true } label: {
                        Label { Text(Strings.Recipients.createList) } icon: { Image(systemName: "plus") }
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(theme.colors.accent)
                            .padding(.horizontal, Spacing.s)
                            .frame(height: 34)
                            .overlay(Capsule().strokeBorder(theme.colors.accent.opacity(0.5), lineWidth: 1))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private func badge(_ title: LocalizedStringResource, isOn: Bool, action: @escaping () -> Void) -> some View {
        badgeLabel(Text(title), isOn: isOn, action: action)
    }

    private func badge(verbatim title: String, isOn: Bool, action: @escaping () -> Void) -> some View {
        badgeLabel(Text(verbatim: title), isOn: isOn, action: action)
    }

    private func badgeLabel(_ title: Text, isOn: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            title
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(isOn ? theme.colors.accentText : theme.colors.cream)
                .padding(.horizontal, Spacing.s)
                .frame(height: 34)
                .background {
                    if isOn { Capsule().fill(theme.colors.accent) } else { Capsule().strokeBorder(theme.colors.border, lineWidth: 1) }
                }
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: isOn)
    }

    // MARK: - Friends

    @ViewBuilder
    private var content: some View {
        if model.isLoading {
            ProgressView().frame(maxWidth: .infinity).padding(.top, Spacing.huge)
        } else if model.friends.isEmpty {
            VStack(spacing: Spacing.m) {
                Text(Strings.Recipients.addFriendsFirst)
                    .font(.system(size: 14))
                    .foregroundStyle(theme.colors.muted)
                    .multilineTextAlignment(.center)
                EmigoButton(title: Strings.Recipients.findFriends, kind: .secondary, action: onFindFriends)
                    .frame(maxWidth: 220)
            }
            .frame(maxWidth: .infinity)
            .padding(.top, Spacing.huge)
        } else if model.visibleFriends.isEmpty {
            Text(model.searchQuery.isEmpty
                 ? String(localized: Strings.Recipients.emptyList)
                 : String(format: String(localized: Strings.Friends.noMatch), model.searchQuery))
                .font(.system(size: 13))
                .foregroundStyle(theme.colors.muted)
                .frame(maxWidth: .infinity)
                .padding(.top, Spacing.xl)
        } else {
            LazyVStack(spacing: Spacing.xs) {
                ForEach(model.visibleFriends) { friend in
                    row(friend)
                }
            }
        }
    }

    private func row(_ friend: FriendSummary) -> some View {
        let isSelected = model.selectedFriendIds.contains(friend.friendId)
        return Button {
            model.toggle(friend.friendId)
        } label: {
            HStack(spacing: Spacing.m) {
                AvatarView(name: friend.displayName, photoURL: friend.profilePhotoUrl.flatMap { URL(string: $0) }, size: 48)
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 5) {
                        Text(verbatim: friend.displayName)
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(theme.colors.cream)
                        if friend.pinnedByMe {
                            Image(systemName: "pin.fill").font(.system(size: 10)).foregroundStyle(theme.colors.accent)
                        }
                    }
                    Text(verbatim: "@\(friend.username)")
                        .font(.system(size: 12))
                        .foregroundStyle(theme.colors.muted)
                }
                Spacer(minLength: Spacing.xs)
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 24))
                    .foregroundStyle(isSelected ? theme.colors.accent : theme.colors.mutedDim)
                    .contentTransition(.symbolEffect(.replace))
            }
            .padding(.vertical, 6)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: isSelected)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
