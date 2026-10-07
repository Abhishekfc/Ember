import PhotosUI
import SwiftUI

/// What the person is editing from their profile, one sheet per kind.
private enum ProfileEdit: String, Identifiable {
    case name
    case username
    case password

    var id: String { rawValue }
}

/// "Your profile": picture, name and username up top, with the account details to change below.
struct ProfileView: View {
    let model: ProfileViewModel

    @Environment(\.theme) private var theme
    @State private var editing: ProfileEdit?
    @State private var pickerItem: PhotosPickerItem?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                header
                    .padding(.top, Spacing.l)
                    .padding(.bottom, Spacing.xl)

                VStack(alignment: .leading, spacing: 0) {
                    SectionCaption(Strings.Profile.sectionAccount)
                        .padding(.bottom, Spacing.xxs)
                    row(Strings.Profile.rowName) { editing = .name }
                    row(Strings.Profile.rowUsername) { editing = .username }
                    row(Strings.Profile.rowPassword) { editing = .password }
                    PhotosPicker(selection: $pickerItem, matching: .images) {
                        rowLabel(Strings.Profile.rowPicture)
                    }
                    .buttonStyle(.plain)

                    if let error = model.photoError {
                        Text(verbatim: error)
                            .font(.system(size: 13))
                            .foregroundStyle(EmigoFixedColors.errorText)
                            .padding(.top, Spacing.xs)
                    }

                    Label {
                        Text(verbatim: model.profile.email)
                    } icon: {
                        Image(systemName: "lock.fill")
                    }
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)
                    .padding(.top, Spacing.l)
                }
                .padding(.horizontal, Spacing.l)
            }
            .padding(.bottom, Spacing.xl)
        }
        .background(theme.colors.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $editing) { edit in
            switch edit {
            case .name: EditNameSheet(model: model)
            case .username: EditUsernameSheet(model: model)
            case .password: ChangePasswordSheet(model: model)
            }
        }
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    await model.uploadPhoto(from: data)
                }
                pickerItem = nil
            }
        }
    }

    private var header: some View {
        VStack(spacing: Spacing.xs) {
            AvatarView(name: model.profile.displayName, photoURL: model.profile.profilePhotoUrl.flatMap { URL(string: $0) }, size: 100)
                .overlay {
                    if model.isUploadingPhoto {
                        Circle().fill(Color.black.opacity(0.45))
                        ProgressView().tint(.white)
                    }
                }
            Text(verbatim: model.profile.displayName)
                .displayFont(24, weight: .bold, relativeTo: .title2)
                .italic()
                .foregroundStyle(theme.colors.cream)
                .padding(.top, Spacing.xs)
            Text(verbatim: "@\(model.profile.username)")
                .font(.system(size: 14))
                .foregroundStyle(theme.colors.muted)
        }
    }

    private func row(_ title: LocalizedStringResource, action: @escaping () -> Void) -> some View {
        Button(action: action) { rowLabel(title) }
            .buttonStyle(.plain)
    }

    private func rowLabel(_ title: LocalizedStringResource) -> some View {
        HStack {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.colors.cream)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(theme.colors.muted)
        }
        .frame(minHeight: 50)
        .contentShape(Rectangle())
    }
}

// MARK: - Edit sheets

/// The layout every edit sheet shares: title, one line of explanation, fields, and a Save button.
private struct EditSheetScaffold<Content: View>: View {
    let title: LocalizedStringResource
    let subtitle: LocalizedStringResource
    var saveTitle: LocalizedStringResource = Strings.Common.save
    let isSaving: Bool
    let canSave: Bool
    let error: String?
    let onSave: () -> Void
    @ViewBuilder var content: Content

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.l) {
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(title)
                    .displayFont(20, weight: .bold, relativeTo: .title3)
                    .foregroundStyle(theme.colors.cream)
                Text(subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)
            }
            content
            ErrorText(message: error)
            Spacer(minLength: 0)
            EmigoButton(title: saveTitle, isLoading: isSaving, isEnabled: canSave, action: onSave)
        }
        .padding(Spacing.xl)
        .background(theme.colors.backgroundTop.ignoresSafeArea())
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

private struct EditNameSheet: View {
    let model: ProfileViewModel

    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var isSaving = false
    @State private var error: String?

    var body: some View {
        EditSheetScaffold(
            title: Strings.Profile.nameTitle,
            subtitle: Strings.Profile.nameSubtitle,
            isSaving: isSaving,
            canSave: !name.trimmingCharacters(in: .whitespaces).isEmpty,
            error: error,
            onSave: save
        ) {
            EmigoTextField(placeholder: Strings.Profile.rowName, text: $name, contentType: .name, capitalization: .words, autofocus: true, onSubmit: save)
        }
        .onAppear { name = model.profile.displayName }
        .onChange(of: name) { error = nil }
    }

    private func save() {
        guard !isSaving else { return }
        Task {
            isSaving = true
            defer { isSaving = false }
            let failure = await model.saveName(name)
            if let failure { error = failure.isEmpty ? nil : failure } else { dismiss() }
        }
    }
}

private struct EditUsernameSheet: View {
    let model: ProfileViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var username = ""
    @State private var check: UsernameCheck = .idle
    @State private var isSaving = false
    @State private var error: String?
    @State private var checkTask: Task<Void, Never>?

    var body: some View {
        EditSheetScaffold(
            title: Strings.Profile.usernameTitle,
            subtitle: Strings.Profile.usernameSubtitle,
            isSaving: isSaving,
            canSave: check == .available,
            error: error,
            onSave: save
        ) {
            VStack(alignment: .leading, spacing: Spacing.xs) {
                EmigoTextField(placeholder: Strings.Register.usernameHint, text: $username, contentType: .username, prefix: "@", autofocus: true, onSubmit: save)
                availability
            }
        }
        .onAppear { username = model.profile.username }
        .onChange(of: username) { usernameEdited() }
    }

    @ViewBuilder
    private var availability: some View {
        switch check {
        case .idle:
            EmptyView()
        case .checking:
            ProgressView().controlSize(.small)
        case .available:
            Label { Text(Strings.Profile.usernameAvailable) } icon: {
                Image(systemName: "checkmark.circle.fill").foregroundStyle(theme.colors.accent)
            }
            .font(.system(size: 13))
            .foregroundStyle(theme.colors.cream)
        case .taken:
            Label { Text(Strings.Profile.usernameTaken) } icon: { Image(systemName: "xmark.circle.fill") }
                .font(.system(size: 13))
                .foregroundStyle(EmigoFixedColors.errorText)
        }
    }

    /// Keeps only what a username may contain, then checks it once typing pauses.
    private func usernameEdited() {
        let filtered = String(
            username.filter { $0.isLetter || $0.isNumber || $0 == "_" || $0 == "." }.lowercased().prefix(30)
        )
        if filtered != username { username = filtered }
        error = nil
        checkTask?.cancel()
        guard filtered.count >= minimumUsernameLength, filtered != model.profile.username else {
            check = .idle
            return
        }
        checkTask = Task {
            check = .checking
            try? await Task.sleep(for: .milliseconds(400))
            guard !Task.isCancelled else { return }
            let result = await model.checkUsername(filtered)
            if !Task.isCancelled { check = result }
        }
    }

    private func save() {
        guard !isSaving, check == .available else { return }
        Task {
            isSaving = true
            defer { isSaving = false }
            let failure = await model.saveUsername(username)
            if let failure { error = failure.isEmpty ? nil : failure } else { dismiss() }
        }
    }
}

private struct ChangePasswordSheet: View {
    let model: ProfileViewModel

    @Environment(\.dismiss) private var dismiss
    @Environment(\.theme) private var theme
    @State private var current = ""
    @State private var new = ""
    @State private var confirm = ""
    @State private var isSaving = false
    @State private var error: String?
    @State private var didChange = false

    var body: some View {
        EditSheetScaffold(
            title: Strings.Profile.rowPassword,
            subtitle: Strings.Profile.passwordSubtitle,
            isSaving: isSaving,
            canSave: !current.isEmpty && !new.isEmpty && !confirm.isEmpty && !didChange,
            error: error,
            onSave: save
        ) {
            VStack(spacing: Spacing.s) {
                EmigoTextField(placeholder: Strings.Profile.currentPasswordHint, text: $current, isSecure: true, contentType: .password, submitLabel: .next, autofocus: true)
                EmigoTextField(placeholder: Strings.Profile.newPasswordHint, text: $new, isSecure: true, contentType: .newPassword, submitLabel: .next)
                EmigoTextField(placeholder: Strings.Profile.confirmPasswordHint, text: $confirm, isSecure: true, contentType: .newPassword, onSubmit: save)
                if didChange {
                    Label { Text(Strings.Profile.passwordChanged) } icon: {
                        Image(systemName: "checkmark.circle.fill").foregroundStyle(theme.colors.accent)
                    }
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.cream)
                }
            }
        }
        .onChange(of: current) { error = nil }
        .onChange(of: new) { error = nil }
        .onChange(of: confirm) { error = nil }
    }

    private func save() {
        guard !isSaving else { return }
        Task {
            isSaving = true
            defer { isSaving = false }
            let failure = await model.changePassword(current: current, new: new, confirm: confirm)
            if let failure {
                if !failure.isEmpty { error = failure; Haptics.error() }
            } else {
                didChange = true
                Haptics.success()
                try? await Task.sleep(for: .milliseconds(900))
                dismiss()
            }
        }
    }
}
