import SwiftUI

/// Settings that are rare or serious enough to sit one tap away from the main list: right now just
/// Delete account.
struct OtherSettingsView: View {
    let model: DeleteAccountViewModel
    let onAccountDeleted: () -> Void

    @Environment(\.theme) private var theme
    @State private var isShowingDeleteSheet = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Button { isShowingDeleteSheet = true } label: {
                HStack(spacing: Spacing.s) {
                    Image(systemName: "trash")
                        .font(.system(size: 17, weight: .semibold))
                        .accessibilityHidden(true)
                    Text(Strings.DeleteAccount.row)
                        .font(.system(size: 15, weight: .semibold))
                    Spacer()
                }
                .foregroundStyle(EmigoFixedColors.errorText)
                .frame(minHeight: 56)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)

            Spacer()
        }
        .padding(.horizontal, Spacing.l)
        .padding(.top, Spacing.xs)
        .background(theme.colors.background.ignoresSafeArea())
        .navigationTitle(Text(Strings.Settings.other))
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $isShowingDeleteSheet) {
            DeleteAccountSheet(model: model, onAccountDeleted: onAccountDeleted) { isShowingDeleteSheet = false }
        }
    }
}

/// The confirmation: what will be lost, a box to type "delete" in, and the two buttons. It stays
/// open while the request runs and shows any failure inline, so a failed delete is never mistaken
/// for a finished one.
private struct DeleteAccountSheet: View {
    @Bindable var model: DeleteAccountViewModel
    let onAccountDeleted: () -> Void
    let onCancel: () -> Void

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.m) {
            Text(Strings.DeleteAccount.title)
                .displayFont(20, weight: .bold, relativeTo: .title3)
                .foregroundStyle(theme.colors.cream)
                .accessibilityAddTraits(.isHeader)

            Text(Strings.DeleteAccount.warning)
                .font(.system(size: 14))
                .foregroundStyle(theme.colors.muted)

            EmigoTextField(placeholder: Strings.DeleteAccount.confirmPrompt, text: $model.typedText)
                .disabled(model.isDeleting)
                .onChange(of: model.typedText) { model.clearError() }

            if let message = model.errorMessage {
                Text(verbatim: message)
                    .font(.system(size: 13))
                    .foregroundStyle(EmigoFixedColors.errorText)
            }

            HStack(spacing: Spacing.s) {
                EmigoButton(title: Strings.Common.cancel, kind: .secondary, isEnabled: !model.isDeleting, action: onCancel)
                deleteButton
            }
            .padding(.top, Spacing.xxs)

            Spacer(minLength: 0)
        }
        .padding(Spacing.l)
        .padding(.top, Spacing.xs)
        .presentationDetents([.medium])
        .presentationBackground(theme.colors.backgroundTop)
        .interactiveDismissDisabled(model.isDeleting)
    }

    private var deleteButton: some View {
        Button {
            Task {
                if await model.delete() { onAccountDeleted() }
            }
        } label: {
            ZStack {
                Text(Strings.Common.delete)
                    .opacity(model.isDeleting ? 0 : 1)
                if model.isDeleting {
                    ProgressView().tint(EmigoFixedColors.onDestructive)
                }
            }
            .font(.system(size: 15, weight: .bold))
            .foregroundStyle(model.canDelete || model.isDeleting ? EmigoFixedColors.onDestructive : theme.colors.mutedDim)
            .frame(maxWidth: .infinity)
            .frame(height: Size.buttonHeight)
            .background(
                model.canDelete || model.isDeleting ? EmigoFixedColors.destructive : theme.colors.panel,
                in: RoundedRectangle(cornerRadius: Radius.button, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .disabled(!model.canDelete)
        .animation(.easeOut(duration: 0.15), value: model.canDelete)
    }
}
