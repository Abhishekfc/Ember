import SwiftUI

struct LoginView: View {
    @Bindable var model: AuthFlowViewModel

    @Environment(\.theme) private var theme

    var body: some View {
        AuthStepScaffold(
            title: Strings.Login.title,
            buttonTitle: Strings.Login.button,
            isLoading: model.isLoading,
            onButton: model.submitLogin
        ) {
            VStack(spacing: Spacing.s) {
                EmigoTextField(
                    placeholder: Strings.Login.identifierHint,
                    text: $model.loginIdentifier,
                    keyboard: .emailAddress,
                    contentType: .username,
                    submitLabel: .next,
                    autofocus: true,
                    isLarge: true
                )
                EmigoTextField(
                    placeholder: Strings.Login.passwordHint,
                    text: $model.password,
                    isSecure: true,
                    contentType: .password,
                    submitLabel: .go,
                    isLarge: true,
                    onSubmit: model.submitLogin
                )
            }

            ErrorText(message: model.errorMessage)

            Button(action: model.showForgotPassword) {
                Text(Strings.Login.forgotPassword)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(theme.colors.muted)
                    .padding(.vertical, Spacing.xxs)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .onChange(of: model.loginIdentifier) { model.fieldEdited() }
        .onChange(of: model.password) { model.fieldEdited() }
        .animation(.easeOut(duration: 0.2), value: model.errorMessage)
    }
}
