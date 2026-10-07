import SwiftUI

/// The title at the top of a tab: centred, in the brand display font, with room for one round
/// button on the right (like Android's "add friend").
struct ScreenHeader<Trailing: View>: View {
    let title: LocalizedStringResource
    @ViewBuilder var trailing: Trailing

    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            Text(title)
                .displayFont(19, weight: .bold, relativeTo: .title3)
                .foregroundStyle(theme.colors.cream)
                .accessibilityAddTraits(.isHeader)
            HStack {
                Spacer()
                trailing
            }
        }
        .padding(.horizontal, Spacing.l)
        .frame(height: 40)
        .padding(.bottom, Spacing.xs)
    }
}

extension ScreenHeader where Trailing == EmptyView {
    init(title: LocalizedStringResource) {
        self.init(title: title) { EmptyView() }
    }
}

/// A small muted label above a group of rows ("Your Emigo", "Preferences").
struct SectionCaption: View {
    let text: Text

    @Environment(\.theme) private var theme

    init(_ resource: LocalizedStringResource) {
        text = Text(resource)
    }

    init(verbatim string: String) {
        text = Text(verbatim: string)
    }

    var body: some View {
        text
            .font(.system(size: 12.5, weight: .medium))
            .foregroundStyle(theme.colors.muted)
    }
}

/// A centred icon, title and optional line of detail, for screens with nothing to show yet.
struct EmptyPlaceholder: View {
    let symbol: String
    let title: LocalizedStringResource
    let detail: LocalizedStringResource?

    @Environment(\.theme) private var theme

    var body: some View {
        VStack(spacing: Spacing.s) {
            Image(systemName: symbol)
                .font(.system(size: 38))
                .foregroundStyle(theme.colors.mutedDim)
                .accessibilityHidden(true)
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.colors.cream)
            if let detail {
                Text(detail)
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)
                    .multilineTextAlignment(.center)
            }
        }
        .padding(.horizontal, Spacing.xxl)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// The search box used on Friends and Find people: a rounded panel with a magnifier.
struct SearchField: View {
    let placeholder: LocalizedStringResource
    @Binding var text: String
    var autofocus = false

    @Environment(\.theme) private var theme
    @FocusState private var isFocused: Bool

    var body: some View {
        HStack(spacing: Spacing.xs) {
            Image(systemName: "magnifyingglass")
                .foregroundStyle(theme.colors.muted)
            TextField(text: $text, prompt: Text(placeholder).foregroundColor(theme.colors.mutedDim)) { EmptyView() }
                .focused($isFocused)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .font(.system(size: 14))
                .foregroundStyle(theme.colors.cream)
                .tint(theme.colors.accent)
            if !text.isEmpty {
                Button { text = "" } label: {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(theme.colors.mutedDim)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, Spacing.s)
        .frame(height: 42)
        .background(theme.colors.panel.opacity(0.7), in: RoundedRectangle(cornerRadius: Radius.button, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: Radius.button, style: .continuous)
                .strokeBorder(isFocused ? theme.colors.accent.opacity(0.6) : theme.colors.border, lineWidth: 1)
        }
        .contentShape(Rectangle())
        .onTapGesture { isFocused = true }
        .task {
            guard autofocus else { return }
            try? await Task.sleep(for: .milliseconds(450))
            isFocused = true
        }
    }
}
