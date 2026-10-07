import SwiftUI

/// A plain RGB value (0...1 per channel) that the tone math below can work on without going
/// through SwiftUI's `Color`, which keeps this file testable. Port of the Android `ColorTone.kt`.
struct RGBColor: Equatable {
    var red: Double
    var green: Double
    var blue: Double

    init(red: Double, green: Double, blue: Double) {
        self.red = red
        self.green = green
        self.blue = blue
    }

    /// `0xRRGGBB`.
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }

    var color: Color { Color(.sRGB, red: red, green: green, blue: blue, opacity: 1) }

    /// Straight RGB blend. (Android blends in Oklab; at the small fractions used here the two are
    /// indistinguishable.)
    func mixed(with other: RGBColor, fraction: Double) -> RGBColor {
        RGBColor(
            red: red + (other.red - red) * fraction,
            green: green + (other.green - green) * fraction,
            blue: blue + (other.blue - blue) * fraction
        )
    }
}

struct HSL: Equatable {
    var hue: Double
    var saturation: Double
    var lightness: Double
}

extension RGBColor {
    var hsl: HSL {
        let maxC = max(red, green, blue)
        let minC = min(red, green, blue)
        let lightness = (maxC + minC) / 2
        guard maxC != minC else { return HSL(hue: 0, saturation: 0, lightness: lightness) }
        let delta = maxC - minC
        let saturation = lightness > 0.5 ? delta / (2 - maxC - minC) : delta / (maxC + minC)
        let rawHue: Double
        switch maxC {
        case red: rawHue = (green - blue) / delta + (green < blue ? 6 : 0)
        case green: rawHue = (blue - red) / delta + 2
        default: rawHue = (red - green) / delta + 4
        }
        return HSL(hue: rawHue / 6, saturation: saturation, lightness: lightness)
    }

    init(_ hsl: HSL) {
        guard hsl.saturation != 0 else {
            self.init(red: hsl.lightness, green: hsl.lightness, blue: hsl.lightness)
            return
        }
        let q = hsl.lightness < 0.5
            ? hsl.lightness * (1 + hsl.saturation)
            : hsl.lightness + hsl.saturation - hsl.lightness * hsl.saturation
        let p = 2 * hsl.lightness - q
        func component(_ shift: Double) -> Double {
            var t = (hsl.hue + shift).truncatingRemainder(dividingBy: 1)
            if t < 0 { t += 1 }
            switch t {
            case ..<(1.0 / 6): return p + (q - p) * 6 * t
            case ..<(1.0 / 2): return q
            case ..<(2.0 / 3): return p + (q - p) * (2.0 / 3 - t) * 6
            default: return p
            }
        }
        self.init(red: component(1.0 / 3), green: component(0), blue: component(-1.0 / 3))
    }

    /// Keeps at least `minGap` of lightness between this color and `reference`. Text colors picked
    /// by eye against one surface quietly stop being readable when that surface changes; this is the
    /// floor that stops it. `awayFromWhite` nudges darker (light themes), otherwise lighter.
    func ensuringLightnessGap(from reference: RGBColor, minGap: Double, awayFromWhite: Bool) -> RGBColor {
        var mine = hsl
        let referenceLightness = reference.hsl.lightness
        let target = awayFromWhite ? referenceLightness - minGap : referenceLightness + minGap
        let adjusted = awayFromWhite ? min(mine.lightness, target) : max(mine.lightness, target)
        mine.lightness = min(max(adjusted, 0), 1)
        return RGBColor(mine)
    }
}

/// The three raised surfaces every theme derives from its background and panel color, so each
/// theme only has to pick those two and an accent.
struct SurfaceLadder: Equatable {
    let surface: RGBColor
    let elevatedPanel: RGBColor
    let overlayPanel: RGBColor
}

func deriveSurfaceLadder(backgroundBase: RGBColor, panel: RGBColor, accent: RGBColor) -> SurfaceLadder {
    let background = backgroundBase.hsl
    let panelHSL = panel.hsl

    var surface = background
    surface.lightness = background.lightness + (panelHSL.lightness - background.lightness) * 0.45

    func stepUp(_ lightness: Double, cap: Double, fraction: Double) -> Double {
        lightness + min((1 - lightness) * fraction, cap)
    }
    let elevatedLightness = stepUp(panelHSL.lightness, cap: 0.08, fraction: 0.6)
    let overlayLightness = stepUp(elevatedLightness, cap: 0.07, fraction: 0.6)

    var elevated = panelHSL
    elevated.lightness = elevatedLightness
    var overlay = panelHSL
    overlay.lightness = overlayLightness

    return SurfaceLadder(
        surface: RGBColor(surface),
        elevatedPanel: RGBColor(elevated).mixed(with: accent, fraction: 0.05),
        overlayPanel: RGBColor(overlay).mixed(with: accent, fraction: 0.09)
    )
}
