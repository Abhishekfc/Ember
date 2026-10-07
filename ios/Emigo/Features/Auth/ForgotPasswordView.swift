import SwiftUI

struct ForgotPasswordView: View {
    @Bindable var model: AuthFlowViewModel

    @Environment(\.theme) private var theme

    var body: some View {
        AuthStepScaffold(
            title: Strings.Reset.title,
            subtitle: Strings.Reset.description,
            buttonTitle: Strings.Reset.sendButton,
            isButtonEnabled: model.isForgotPasswordEmailValid,
            isLoading: model.isSendingReset,
            onButton: model.sendPasswordReset
        ) {
            EmigoTextField(
                placeholder: Strings.Reset.emailHint,
                text: $model.forgotPasswordEmail,
                keyboard: .emailAddress,
                contentType: .emailAddress,
                autofocus: true,
                isLarge: true,
                onSubmit: model.sendPasswordReset
            )

            if model.passwordResetSent {
                Label {
                    Text(Strings.Reset.sent)
                } icon: {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(theme.colors.accent)
                }
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(theme.colors.cream)
                .transition(.opacity)
            }
        }
        .onChange(of: model.forgotPasswordEmail) { model.fieldEdited() }
        .animation(.easeOut(duration: 0.2), value: model.passwordResetSent)
    }
}
