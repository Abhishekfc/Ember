import SwiftUI

/// A filled, rounded text field in the app's style. Pass `isSecure` for passwords; a show/hide
/// eye is added automatically.
struct EmigoTextField: View {
    let placeholder: LocalizedStringResource
    @Binding var text: String
    var isSecure = false
    var keyboard: UIKeyboardType = .default
    var contentType: UITextContentType?
    var capitalization: TextInputAutocapitalization = .never
    var submitLabel: SubmitLabel = .done
    /// Fixed text shown before what's typed, such as "@" for usernames.
    var prefix: String?
    /// Focus the field shortly after the screen appears, so the keyboard is already up.
    var autofocus = false
    /// A taller, roomier field with bigger text, for screens that ask one question at a time.
    var isLarge = false
    var onSubmit: () -> Void = {}

    @Environment(\.theme) private var theme
    @FocusState private var isFocused: Bool
    @State private var isRevealed = false

    var body: some View {
        HStack(spacing: Spacing.xs) {
            if let prefix {
                Text(prefix).foregroundStyle(theme.colors.muted)
            }

            field
                .focused($isFocused)
                .textInputAutocapitalization(capitalization)
                .autocorrectionDisabled()
                .keyboardType(keyboard)
                .textContentType(contentType)
                .submitLabel(submitLabel)
                .onSubmit(onSubmit)
                .font(.system(size: isLarge ? 17 : 15))
                .foregroundStyle(theme.colors.cream)
                .tint(theme.colors.accent)

            if isSecure {
                Button {
                    isRevealed.toggle()
                } label: {
                    Image(systemName: isRevealed ? "eye.slash" : "eye")
                        .foregroundStyle(theme.colors.muted)
                        .frame(width: 32, height: 32)
                }
                .accessibilityLabel(isRevealed ? Text(Strings.Password.hide) : Text(Strings.Password.show))
            }
        }
        .padding(.horizontal, Spacing.m)
        .frame(height: isLarge ? Size.largeFieldHeight : Size.fieldHeight)
        .background(theme.colors.panel, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .strokeBorder(
                    isFocused ? theme.colors.accent.opacity(0.7) : (isLarge ? theme.colors.border : .clear),
                    lineWidth: isFocused ? 1.5 : 1
                )
        }
        .animation(.easeOut(duration: 0.15), value: isFocused)
        .contentShape(Rectangle())
        .onTapGesture { isFocused = true }
        .task {
            guard autofocus else { return }
            // Waits out the screen's push animation; focusing earlier makes the keyboard stutter.
            try? await Task.sleep(for: .milliseconds(450))
            isFocused = true
        }
    }

    @ViewBuilder
    private var field: some View {
        if isSecure && !isRevealed {
            SecureField(text: $text, prompt: prompt) { EmptyView() }
        } else {
            TextField(text: $text, prompt: prompt) { EmptyView() }
        }
    }

    private var cornerRadius: CGFloat { isLarge ? Radius.largeField : Radius.field }

    private var prompt: Text {
        Text(placeholder).foregroundColor(theme.colors.mutedDim)
    }
}
