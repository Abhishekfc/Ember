import SwiftUI

/// Every color a screen is allowed to use. Screens read these from the environment
/// (`@Environment(\.theme)`), never hard-code a hex value, so a new theme is one new definition.
struct EmigoColors {
    let backgroundTop: Color
    let backgroundBottom: Color
    /// Some themes lay a picture over their background colour.
    let backgroundImageName: String?
    let surface: Color
    let panel: Color
    let elevatedPanel: Color
    let overlayPanel: Color
    /// Primary text and icons.
    let cream: Color
    /// Secondary text.
    let muted: Color
    /// Tertiary text, hints and disabled controls.
    let mutedDim: Color
    /// The brand accent: primary buttons, the camera button, active streak rings.
    let accent: Color
    /// The second accent the streak ring's gradient sweeps to.
    let accent2: Color
    /// Text and icons drawn on top of `accent`.
    let accentText: Color
    let border: Color
    /// The quiet ring around a profile picture when there's nothing new to see.
    let avatarRing: Color
    let isLight: Bool

    /// The theme's backdrop as a view: its gradient, with its picture over that if it has one.
    var background: ThemeBackgroundView {
        ThemeBackgroundView(top: backgroundTop, bottom: backgroundBottom, imageName: backgroundImageName)
    }
}

/// A theme's backdrop. Fills whatever space it is given.
struct ThemeBackgroundView: View {
    let top: Color
    let bottom: Color
    let imageName: String?

    var body: some View {
        ZStack {
            LinearGradient(colors: [top, bottom], startPoint: .top, endPoint: .bottom)
            if let imageName {
                // A clear base takes exactly the space offered; the picture fills it and is cut
                // off at the edges, so it can never make its container bigger.
                Color.clear
                    .overlay { Image(imageName).resizable().scaledToFill() }
                    .clipped()
            }
        }
        .accessibilityHidden(true)
    }
}

/// Colors that stay the same in every theme.
enum EmigoFixedColors {
    /// Delete, unsend and sign-out actions.
    static let destructive = Color(.sRGB, red: 0xB3 / 255, green: 0x26 / 255, blue: 0x1E / 255, opacity: 1)
    /// Text on a `destructive` button.
    static let onDestructive = Color.white
    /// Text drawn directly over a photo, where the theme's own colors can't be trusted to contrast.
    static let onPhotoText = Color(.sRGB, red: 0xFB / 255, green: 0xF8 / 255, blue: 0xF3 / 255, opacity: 1)
    /// A flat dark chip behind text that sits on a photo.
    static let photoChip = Color.black.opacity(0.55)
    /// Inline error messages. Brighter than `destructive` so it stays readable on dark surfaces.
    static let errorText = Color(.sRGB, red: 0xFF / 255, green: 0x6B / 255, blue: 0x6B / 255, opacity: 1)
}

/// The font used for big titles and numbers in a theme.
enum DisplayFontFamily {
    case spaceGrotesk
    case fraunces

    /// A font name inside the family that the system can look up; its weight is then set exactly.
    var baseFontName: String {
        switch self {
        case .spaceGrotesk: "SpaceGrotesk-Light"
        case .fraunces: "Fraunces-Regular"
        }
    }
}

struct EmigoTheme {
    let key: ThemeKey
    let colors: EmigoColors
    let displayFont: DisplayFontFamily
}

/// Every theme Emigo has, the same nine as Android. Never rename a case: the raw value is what is
/// saved on the device. `ember` shows to people as "Cream" and `citrus` as "Ember", as on Android.
enum ThemeKey: String, CaseIterable, Identifiable {
    case ember = "EMBER"
    case emberNew = "EMBER_NEW"
    case blaze = "BLAZE"
    case noir = "NOIR"
    case aurora = "AURORA"
    case cyber = "CYBER"
    case botanica = "BOTANICA"
    case citrus = "CITRUS"
    case frost = "FROST"

    var id: String { rawValue }

    /// The theme everyone starts with.
    static let defaultKey = ThemeKey.citrus

    /// Gold-only themes.
    var isLocked: Bool {
        switch self {
        case .aurora, .cyber, .botanica, .frost: true
        default: false
        }
    }

    var displayName: LocalizedStringResource {
        switch self {
        case .ember: Strings.Theme.nameCream
        case .emberNew: Strings.Theme.nameDusk
        case .blaze: Strings.Theme.nameBlaze
        case .noir: Strings.Theme.nameNoir
        case .aurora: Strings.Theme.nameAurora
        case .cyber: Strings.Theme.nameCyber
        case .botanica: Strings.Theme.nameBotanica
        case .citrus: Strings.Theme.nameEmber
        case .frost: Strings.Theme.nameFrost
        }
    }

    /// The order they're shown in: the default first.
    static var displayOrder: [ThemeKey] { [.citrus] + allCases.filter { $0 != .citrus } }
}

extension EmigoTheme {
    static let `default`: EmigoTheme = theme(for: .defaultKey)

    /// The look of the sign-in and sign-up screens: always this, whichever theme someone chose,
    /// because it is the first thing anyone sees, before an account or a choice exists. The same
    /// as Android's: Dusk's serif and neutrals with the app icon's yellow as the one accent.
    static let onboarding: EmigoTheme = make(
        .emberNew, background: 0x000000, panel: 0x1C1C1E, cream: 0xFFFFFF, muted: 0xA3A3AA, mutedDim: 0x69696F,
        accent: 0xFFFB0A, accent2: 0xFFFB0A, accentText: 0x1A1A1A, borderOpacity: 0.10, display: .fraunces
    )

    /// Every theme, built once; looking one up is free, so screens can ask for any of them.
    private static let all: [ThemeKey: EmigoTheme] = Dictionary(
        uniqueKeysWithValues: ThemeKey.allCases.map { ($0, build($0)) }
    )

    static func theme(for key: ThemeKey) -> EmigoTheme {
        guard let theme = all[key] else { preconditionFailure("No theme is defined for \(key)") }
        return theme
    }

    private static func build(_ key: ThemeKey) -> EmigoTheme {
        switch key {
        case .ember:
            make(key, background: 0x121212, panel: 0x424242, cream: 0xEDEAE0, muted: 0xA8A399, mutedDim: 0x6E6A61,
                 accent: 0xEDEAE0, accent2: 0xEDEAE0, accentText: 0x17150F, display: .fraunces)
        case .emberNew:
            make(key, background: 0x121212, panel: 0x424242, ladderAccent: 0xEDEAE0, cream: 0xFFFFFF, muted: 0xA3A3AA,
                 mutedDim: 0x69696F, accent: 0x7B61FF, accent2: 0x5DADE2, accentText: 0xFFFFFF, display: .fraunces)
        case .blaze:
            make(key, background: 0x121212, panel: 0x313038, cream: 0xFBF8F3, muted: 0x9B93B8, mutedDim: 0x6B6488,
                 accent: 0xFFA94D, accent2: 0xFF8A5C, accentText: 0x1A1408, display: .fraunces)
        case .noir:
            make(key, background: 0x121212, panel: 0x2E2E2E, cream: 0xF5F5F5, muted: 0x9A9A9A, mutedDim: 0x666666,
                 accent: 0xF5F5F5, accent2: 0xC9C9C9, accentText: 0x0A0A0A, borderOpacity: 0.10, display: .spaceGrotesk)
        case .aurora:
            make(key, background: 0x0A100F, image: "Theme-aurora", panel: 0x2B3633, cream: 0xE8FBF6, muted: 0x7FA8A3,
                 mutedDim: 0x4C6E6A, accent: 0x4FE3C1, accent2: 0x5CC8FF, accentText: 0x04211E, display: .spaceGrotesk)
        case .cyber:
            make(key, background: 0x0E0B14, image: "Theme-cyber", panel: 0x342D3A, cream: 0xF3E8FF, muted: 0x8A72B8,
                 mutedDim: 0x5A4880, accent: 0xFF2EC4, accent2: 0x7B2FFF, accentText: 0x0A0014, display: .spaceGrotesk)
        case .botanica:
            make(key, background: 0x0C110D, image: "Theme-botanica", panel: 0x2F362F, cream: 0xF0EAD8, muted: 0x8FA894,
                 mutedDim: 0x587060, accent: 0xC9A15A, accent2: 0x8FBF7A, accentText: 0x14251C, display: .fraunces)
        case .citrus:
            // Pure black on iPhone (Android's is a very dark grey); photos and the glass bars stand out.
            make(key, background: 0x000000, panel: 0x353535, cream: 0xFFFDF5, muted: 0x9A9A9A, mutedDim: 0x5C5C5C,
                 accent: 0xF5D90A, accent2: 0xFF7A1A, accentText: 0x141400, display: .spaceGrotesk)
        case .frost:
            make(key, background: 0x0B121B, image: "Theme-frost", panel: 0x24384A, cream: 0xF2F9FF, muted: 0x8FB4D6,
                 mutedDim: 0x52708C, accent: 0xCCE7FF, accent2: 0x4F9FE6, accentText: 0x031320, borderOpacity: 0.10, display: .spaceGrotesk)
        }
    }

    /// Builds a theme from the handful of colors that define it; the in-between surfaces are worked
    /// out from the background and panel (see `deriveSurfaceLadder`).
    private static func make(
        _ key: ThemeKey,
        background: UInt32,
        image: String? = nil,
        panel: UInt32,
        ladderAccent: UInt32? = nil,
        cream: UInt32,
        muted: UInt32,
        mutedDim: UInt32,
        accent: UInt32,
        accent2: UInt32,
        accentText: UInt32,
        borderOpacity: Double = 0.08,
        display: DisplayFontFamily
    ) -> EmigoTheme {
        let backgroundBase = RGBColor(hex: background)
        let panelColor = RGBColor(hex: panel)
        let ladder = deriveSurfaceLadder(
            backgroundBase: backgroundBase,
            panel: panelColor,
            accent: RGBColor(hex: ladderAccent ?? accent)
        )
        return EmigoTheme(
            key: key,
            colors: EmigoColors(
                backgroundTop: backgroundBase.color,
                backgroundBottom: backgroundBase.color,
                backgroundImageName: image,
                surface: ladder.surface.color,
                panel: panelColor.color,
                elevatedPanel: ladder.elevatedPanel.color,
                overlayPanel: ladder.overlayPanel.color,
                cream: RGBColor(hex: cream).color,
                muted: RGBColor(hex: muted).color,
                mutedDim: RGBColor(hex: mutedDim)
                    .ensuringLightnessGap(from: panelColor, minGap: 0.26, awayFromWhite: false).color,
                accent: RGBColor(hex: accent).color,
                accent2: RGBColor(hex: accent2).color,
                accentText: RGBColor(hex: accentText).color,
                border: Color.white.opacity(borderOpacity),
                avatarRing: Color.white.opacity(0.16),
                isLight: false
            ),
            displayFont: display
        )
    }
}

private struct ThemeEnvironmentKey: EnvironmentKey {
    static let defaultValue: EmigoTheme = .default
}

extension EnvironmentValues {
    var theme: EmigoTheme {
        get { self[ThemeEnvironmentKey.self] }
        set { self[ThemeEnvironmentKey.self] = newValue }
    }
}
