import SwiftUI

/// Pick how the app looks. Tapping a theme tries it on straight away across the whole app; it only
/// stays if "Apply theme" is tapped, and goes back to the applied one when the page is left.
/// Gold-only themes can be tried by anyone; applying one asks for Emigo Gold.
struct AppearanceView: View {
    let themes: ThemeStore
    let isGoldMember: Bool
    let onGetGold: () -> Void

    @Environment(\.theme) private var theme
    /// The theme highlighted on this page: the applied one to begin with, then whichever was last tapped.
    @State private var selected: ThemeKey

    private let columns = Array(repeating: GridItem(.flexible(), spacing: Spacing.s), count: 3)

    init(themes: ThemeStore, isGoldMember: Bool, onGetGold: @escaping () -> Void) {
        self.themes = themes
        self.isGoldMember = isGoldMember
        self.onGetGold = onGetGold
        _selected = State(initialValue: themes.appliedKey)
    }

    private var needsGold: Bool { selected.isLocked && !isGoldMember }
    private var isApplied: Bool { selected == themes.appliedKey }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.l) {
                Text(Strings.Theme.subtitle)
                    .font(.system(size: 13))
                    .foregroundStyle(theme.colors.muted)

                LazyVGrid(columns: columns, spacing: Spacing.m) {
                    ForEach(ThemeKey.displayOrder) { key in
                        ThemeChip(key: key, isSelected: key == selected, isLocked: key.isLocked && !isGoldMember) {
                            select(key)
                        }
                    }
                }
            }
            .padding(.horizontal, Spacing.l)
            .padding(.top, Spacing.xs)
            .padding(.bottom, Spacing.l)
        }
        .background(theme.colors.background.ignoresSafeArea())
        .safeAreaInset(edge: .bottom, spacing: 0) {
            actionButton
                .padding(.horizontal, Spacing.l)
                .padding(.vertical, Spacing.s)
        }
        .navigationTitle(Text(Strings.Theme.title))
        .navigationBarTitleDisplayMode(.inline)
        // Coming back from the Gold page, the theme that was being tried on is worn again.
        .onAppear { withAnimation(Self.fade) { themes.preview(selected) } }
        .onDisappear { withAnimation(Self.fade) { themes.endPreview() } }
        .sensoryFeedback(.selection, trigger: selected)
    }

    private static let fade = Animation.easeInOut(duration: 0.25)

    @ViewBuilder
    private var actionButton: some View {
        if needsGold {
            EmigoButton(title: Strings.Theme.getGold, action: onGetGold)
        } else if isApplied {
            EmigoButton(title: Strings.Theme.applied, kind: .secondary, isEnabled: false) {}
        } else {
            EmigoButton(title: Strings.Theme.apply) {
                withAnimation(Self.fade) { themes.apply(selected) }
                Haptics.success()
            }
        }
    }

    private func select(_ key: ThemeKey) {
        selected = key
        withAnimation(Self.fade) { themes.preview(key) }
    }
}

/// One theme in the grid: a square showing its own background and three of its colors, with its
/// name and whether it's the default, free or Gold underneath. Drawn in its own colors, not the
/// current theme's, so each one looks like itself.
private struct ThemeChip: View {
    let key: ThemeKey
    let isSelected: Bool
    /// Gold-only and this account isn't Gold.
    let isLocked: Bool
    let action: () -> Void

    @Environment(\.theme) private var theme

    private var chipTheme: EmigoTheme { .theme(for: key) }
    private let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)

    var body: some View {
        Button(action: action) {
            VStack(spacing: Spacing.xs) {
                swatch
                VStack(spacing: 2) {
                    Text(key.displayName)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(theme.colors.cream)
                    badge
                        .font(.system(size: 11))
                        .foregroundStyle(theme.colors.muted)
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private var swatch: some View {
        ZStack(alignment: .bottomLeading) {
            chipTheme.colors.background

            HStack(spacing: 5) {
                dot(chipTheme.colors.cream)
                dot(chipTheme.colors.accent)
                dot(chipTheme.colors.accent2)
            }
            .padding(10)

            if isLocked {
                Color.black.opacity(0.5)
                Image(systemName: "lock.fill")
                    .font(.system(size: 18, weight: .semibold))
                    .foregroundStyle(chipTheme.colors.cream)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityHidden(true)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .clipShape(shape)
        .overlay {
            shape.strokeBorder(
                isSelected ? chipTheme.colors.accent : theme.colors.border,
                lineWidth: isSelected ? 2.5 : 1
            )
        }
    }

    private func dot(_ color: Color) -> some View {
        Circle().fill(color).frame(width: 9, height: 9)
    }

    private var badge: Text {
        if key == ThemeKey.defaultKey { return Text(Strings.Theme.badgeDefault) }
        return Text(key.isLocked ? Strings.Settings.badgeGold : Strings.Settings.badgeFree)
    }
}
