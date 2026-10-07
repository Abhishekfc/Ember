import SwiftUI

// The four steps of creating an account, in order: email, password, name, username.

struct RegisterEmailView: View {
    @Bindable var model: AuthFlowViewModel

    @Environment(\.theme) private var theme

    var body: some View {
        AuthStepScaffold(
            title: Strings.Register.emailTitle,
            progress: .registration(1),
            buttonTitle: Strings.Common.continue,
            isButtonEnabled: model.isEmailValid,
            isLoading: model.isLoading,
            onButton: model.submitEmail
        ) {
            EmigoTextField(
                placeholder: Strings.Register.emailHint,
                text: $model.email,
                keyboard: .emailAddress,
                contentType: .emailAddress,
                autofocus: true,
                isLarge: true,
                onSubmit: model.submitEmail
            )

            ErrorText(message: model.errorMessage)

            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text(Strings.Register.legalPrefix)
                HStack(spacing: Spacing.xxs) {
                    Link(destination: AppLinks.termsOfService) { Text(Strings.Register.legalTerms).underline() }
                    Text(Strings.Register.legalAnd)
                    Link(destination: AppLinks.privacyPolicy) { Text(Strings.Register.legalPrivacy).underline() }
                }
            }
            .font(.system(size: 13))
            .foregroundStyle(theme.colors.muted)
        }
        .onChange(of: model.email) { model.fieldEdited() }
        .animation(.easeOut(duration: 0.2), value: model.errorMessage)
    }
}

struct RegisterPasswordView: View {
    @Bindable var model: AuthFlowViewModel

    @Environment(\.theme) private var theme

    var body: some View {
        AuthStepScaffold(
            title: Strings.Register.passwordTitle,
            progress: .registration(2),
            buttonTitle: Strings.Common.continue,
            isButtonEnabled: model.isPasswordValid,
            onButton: model.submitPassword
        ) {
            EmigoTextField(
                placeholder: Strings.Login.passwordHint,
                text: $model.password,
                isSecure: true,
                contentType: .newPassword,
                autofocus: true,
                isLarge: true,
                onSubmit: model.submitPassword
            )

            Label {
                Text(Strings.Register.passwordRule)
            } icon: {
                Image(systemName: model.isPasswordValid ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(model.isPasswordValid ? theme.colors.accent : theme.colors.mutedDim)
                    .contentTransition(.symbolEffect(.replace))
            }
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(model.isPasswordValid ? theme.colors.cream : theme.colors.muted)
            .animation(.easeOut(duration: 0.15), value: model.isPasswordValid)

            ErrorText(message: model.errorMessage)
        }
        .onChange(of: model.password) { model.fieldEdited() }
    }
}

struct RegisterNameView: View {
    @Bindable var model: AuthFlowViewModel

    var body: some View {
        AuthStepScaffold(
            title: Strings.Register.nameTitle,
            progress: .registration(3),
            buttonTitle: Strings.Common.continue,
            isButtonEnabled: model.isNameValid,
            onButton: model.submitName
        ) {
            VStack(spacing: Spacing.s) {
                EmigoTextField(
                    placeholder: Strings.Register.firstNameHint,
                    text: $model.firstName,
                    contentType: .givenName,
                    capitalization: .words,
                    submitLabel: .next,
                    autofocus: true,
                    isLarge: true
                )
                EmigoTextField(
                    placeholder: Strings.Register.lastNameHint,
                    text: $model.lastName,
                    contentType: .familyName,
                    capitalization: .words,
                    isLarge: true,
                    onSubmit: model.submitName
                )
            }

            ErrorText(message: model.errorMessage)
        }
        .onChange(of: model.firstName) { model.fieldEdited() }
    }
}

struct RegisterUsernameView: View {
    @Bindable var model: AuthFlowViewModel

    @Environment(\.theme) private var theme

    var body: some View {
        AuthStepScaffold(
            title: Strings.Register.usernameTitle,
            progress: .registration(4),
            buttonTitle: Strings.Register.createAccountButton,
            isButtonEnabled: model.canCreateAccount,
            isLoading: model.isLoading,
            onButton: model.submitUsername
        ) {
            EmigoTextField(
                placeholder: Strings.Register.usernameHint,
                text: $model.usernameDraft,
                contentType: .username,
                prefix: "@",
                autofocus: true,
                isLarge: true,
                onSubmit: model.submitUsername
            )

            availability

            ErrorText(message: model.errorMessage)
        }
        .onChange(of: model.usernameDraft) { model.usernameEdited() }
        .animation(.easeOut(duration: 0.2), value: model.usernameCheck)
    }

    @ViewBuilder
    private var availability: some View {
        switch model.usernameCheck {
        case .idle:
            EmptyView()
        case .checking:
            Label {
                Text(Strings.Register.usernameChecking)
            } icon: {
                ProgressView().controlSize(.small)
            }
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(theme.colors.muted)
        case .available:
            Label {
                Text(Strings.Register.usernameAvailable)
            } icon: {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundStyle(theme.colors.accent)
                    .symbolEffect(.bounce, value: model.usernameCheck)
            }
            .font(.system(size: 14, weight: .medium))
            .foregroundStyle(theme.colors.cream)
        case .taken(let suggestions):
            VStack(alignment: .leading, spacing: Spacing.s) {
                Label {
                    Text(Strings.Register.usernameTaken)
                } icon: {
                    Image(systemName: "xmark.circle.fill")
                }
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(EmigoFixedColors.errorText)

                if !suggestions.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: Spacing.xs) {
                            ForEach(suggestions, id: \.self) { name in
                                Button { model.pickSuggestion(name) } label: {
                                    Text(verbatim: "@\(name)")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(theme.colors.cream)
                                        .padding(.horizontal, Spacing.s)
                                        .padding(.vertical, 9)
                                        .background(theme.colors.accent.opacity(0.12), in: Capsule())
                                        .overlay(Capsule().strokeBorder(theme.colors.accent.opacity(0.35), lineWidth: 1))
                                }
                                .buttonStyle(AuthPressStyle())
                            }
                        }
                    }
                }
            }
        }
    }
}
