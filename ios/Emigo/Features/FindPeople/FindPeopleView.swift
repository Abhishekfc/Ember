import SwiftUI

/// Search for people already on Emigo, or invite someone who isn't.
struct FindPeopleView: View {
    @Bindable var model: FindPeopleViewModel
    let myUsername: String?
    let onOpenProfile: (FriendSearchResult) -> Void

    @Environment(\.theme) private var theme
    @Environment(\.openURL) private var openURL
    @State private var isSharing = false

    var body: some View {
        ScrollView {
            VStack(spacing: Spacing.l) {
                SearchField(placeholder: Strings.FindPeople.searchHint, text: $model.query)

                if model.isSearchingActive {
                    resultsList
                } else {
                    intro
                    inviteRow
                }
            }
            .padding(.horizontal, Spacing.l)
            .padding(.top, Spacing.xs)
            .padding(.bottom, Spacing.xl)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(theme.colors.background.ignoresSafeArea())
        .navigationTitle(Text(Strings.Friends.findPeople))
        .navigationBarTitleDisplayMode(.inline)
        .onChange(of: model.query) { model.queryChanged() }
        .sheet(isPresented: $isSharing) {
            ShareSheet(text: InviteMessage.text(username: myUsername))
                .presentationDetents([.medium, .large])
        }
    }

    // MARK: - Pieces

    private var intro: some View {
        VStack(spacing: Spacing.xs) {
            Text(Strings.FindPeople.intro)
            Text(Strings.FindPeople.invitePrompt)
                .foregroundStyle(theme.colors.mutedDim)
        }
        .font(.system(size: 14))
        .foregroundStyle(theme.colors.muted)
        .multilineTextAlignment(.center)
        .padding(.top, Spacing.m)
    }

    private var inviteRow: some View {
        HStack(alignment: .top, spacing: Spacing.l) {
            ForEach(InviteTarget.allCases) { target in
                Button { invite(via: target) } label: {
                    VStack(spacing: Spacing.xs) {
                        Image(target.imageName)
                            .resizable()
                            .scaledToFit()
                            .frame(width: 58, height: 58)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        Text(verbatim: target.displayName)
                            .font(.system(size: 13))
                            .foregroundStyle(theme.colors.muted)
                    }
                }
                .buttonStyle(.plain)
            }
            Button { isSharing = true } label: {
                VStack(spacing: Spacing.xs) {
                    Image(systemName: "ellipsis")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(theme.colors.accent)
                        .frame(width: 58, height: 58)
                        .background(theme.colors.panel, in: Circle())
                    Text(Strings.FindPeople.more)
                        .font(.system(size: 13))
                        .foregroundStyle(theme.colors.muted)
                }
            }
            .buttonStyle(.plain)
        }
        .padding(.top, Spacing.xs)
    }

    @ViewBuilder
    private var resultsList: some View {
        if let message = model.errorMessage {
            Text(verbatim: message)
                .font(.system(size: 13))
                .foregroundStyle(EmigoFixedColors.errorText)
        }
        if model.isSearching && model.results.isEmpty {
            ProgressView().padding(.top, Spacing.xl)
        } else if model.results.isEmpty && model.errorMessage == nil {
            Text(String(format: String(localized: Strings.Friends.noMatch), model.trimmedQuery))
                .font(.system(size: 13))
                .foregroundStyle(theme.colors.muted)
                .padding(.top, Spacing.xl)
        } else {
            LazyVStack(spacing: Spacing.m) {
                ForEach(model.results) { result in
                    resultRow(result)
                }
            }
        }
    }

    private func resultRow(_ result: FriendSearchResult) -> some View {
        HStack(spacing: Spacing.m) {
            Button { onOpenProfile(result) } label: {
                HStack(spacing: Spacing.m) {
                    RingedAvatar(name: result.displayName, photoURL: nil, size: 52)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(verbatim: result.displayName)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(theme.colors.cream)
                        Text(verbatim: "@\(result.username)")
                            .font(.system(size: 13))
                            .foregroundStyle(theme.colors.muted)
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Spacer(minLength: Spacing.xs)
            if let action = model.action(for: result) {
                actionButton(action, for: result)
            }
        }
    }

    private func actionButton(_ action: SearchResultAction, for result: FriendSearchResult) -> some View {
        Button {
            Task { await model.perform(action, on: result) }
        } label: {
            Text(action == .requested ? Strings.FindPeople.requested : (action == .accept ? Strings.Friends.accept : Strings.FindPeople.add))
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(action == .requested ? theme.colors.muted : theme.colors.accentText)
                .padding(.horizontal, Spacing.m)
                .frame(height: 32)
                .background {
                    if action == .requested {
                        Capsule().strokeBorder(theme.colors.border, lineWidth: 1)
                    } else {
                        Capsule().fill(theme.colors.accent)
                    }
                }
        }
        .buttonStyle(.plain)
        .disabled(action == .requested)
    }

    /// Copies the invite where the app can't take it in a link, then opens the app; falls back to
    /// the system share sheet if the app isn't installed.
    private func invite(via target: InviteTarget) {
        target.send(message: InviteMessage.text(username: myUsername), openURL: openURL) { isSharing = true }
    }
}
