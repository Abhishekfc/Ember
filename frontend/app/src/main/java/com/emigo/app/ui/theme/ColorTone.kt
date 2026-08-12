package com.emigo.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlin.math.max
import kotlin.math.min

/** A color's hue/saturation/lightness — hue as a fraction of the full wheel (0f..1f, not
 * degrees), saturation and lightness each 0f..1f. The common currency every derived tonal step
 * in [deriveSurfaceLadder] is built from, since stepping *lightness* alone while holding hue and
 * saturation fixed is what keeps a theme's own color identity intact while still separating its
 * surfaces. */
internal data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

internal fun Color.toHsl(): Hsl {
    val maxC = max(red, max(green, blue))
    val minC = min(red, min(green, blue))
    val lightness = (maxC + minC) / 2f
    if (maxC == minC) return Hsl(hue = 0f, saturation = 0f, lightness = lightness)
    val d = maxC - minC
    val saturation = if (lightness > 0.5f) d / (2f - maxC - minC) else d / (maxC + minC)
    val hue = when (maxC) {
        red -> ((green - blue) / d + (if (green < blue) 6f else 0f))
        green -> (blue - red) / d + 2f
        else -> (red - green) / d + 4f
    } / 6f
    return Hsl(hue, saturation, lightness)
}

internal fun Hsl.toColor(alpha: Float = 1f): Color {
    if (saturation == 0f) return Color(lightness, lightness, lightness, alpha)
    fun component(shift: Float): Float {
        var t = (hue + shift) % 1f
        if (t < 0f) t += 1f
        val q = if (lightness < 0.5f) lightness * (1f + saturation) else lightness + saturation - lightness * saturation
        val p = 2f * lightness - q
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
    return Color(component(1f / 3f), component(0f), component(-1f / 3f), alpha)
}

/** The tonal steps every theme is missing today, on top of its existing hand-tuned `background`
 * and `panel` — a plain [surface] between the two, and [elevatedPanel]/[overlayPanel] above
 * `panel` for content that needs to visibly outrank a normal card (a hero card among plain rows,
 * a dialog or bottom sheet floating over everything else). */
internal data class SurfaceLadder(
    val surface: Color,
    val elevatedPanel: Color,
    val overlayPanel: Color,
)

/** Derives the rest of a theme's surface ladder from two anchors every theme already hand-tunes —
 * `backgroundBase` (the background gradient's own inner stop) and `panel` — rather than hand-
 * authoring two more hex constants per theme (20 more numbers to keep in sync across 10 themes).
 *
 * [surface] sits at a fixed 45% of the way from background to panel — simple linear interpolation,
 * safely bounded between the two by construction.
 *
 * The two elevated tiers step *up* from there. A fixed-percent step (e.g. "+8% lightness") is
 * right for a dark theme's panel, which sits around 20% lightness with 80 points of headroom left
 * before white — but every light theme here already has its panel above 90%, where 8 more points
 * would blow straight through white and clip flat. [stepUp] instead takes a fraction of whatever
 * headroom is actually left, capped at that same "+8% on a dark theme" ceiling — on a dark theme
 * there's always more than enough headroom to hit the cap every time, so it behaves like a plain
 * fixed step; on a light theme it automatically compresses to whatever small room remains instead
 * of overshooting.
 *
 * That compression is also why the two elevated tiers lean a few percent toward the theme's own
 * [accent] hue: once a near-white theme's panel has almost nowhere left to go lighter, that faint
 * warmth is what still reads as "this surface outranks the one below it," the same cue premium
 * light-mode UIs reach for over pure luminance. */
internal fun deriveSurfaceLadder(backgroundBase: Color, panel: Color, accent: Color): SurfaceLadder {
    val bg = backgroundBase.toHsl()
    val pnl = panel.toHsl()

    val surface = bg.copy(lightness = bg.lightness + (pnl.lightness - bg.lightness) * 0.45f)

    fun stepUp(lightness: Float, cap: Float, fraction: Float): Float {
        val roomLeft = 1f - lightness
        return lightness + min(roomLeft * fraction, cap)
    }

    val elevatedLightness = stepUp(pnl.lightness, cap = 0.08f, fraction = 0.6f)
    val overlayLightness = stepUp(elevatedLightness, cap = 0.07f, fraction = 0.6f)

    val elevated = lerp(pnl.copy(lightness = elevatedLightness).toColor(), accent, 0.05f)
    val overlay = lerp(pnl.copy(lightness = overlayLightness).toColor(), accent, 0.09f)

    return SurfaceLadder(surface = surface.toColor(), elevatedPanel = elevated, overlayPanel = overlay)
}

/** Guarantees this color keeps at least [minGap] of lightness separation from [reference] —
 * [awayFromWhite] true nudges it *darker* if it's drifted too close (the direction "soft
 * secondary text" needs on a light theme, where recessive means closer to black), false nudges it
 * *lighter* (secondary text on a dark theme, where recessive means closer to white). Hue and
 * saturation are held fixed either way.
 *
 * Exists because a text color hand-picked once against a specific surface tone silently stops
 * being legible the moment that surface tone changes — [EmberColors.mutedDim] was tuned against
 * each theme's original `panel`, and widening `panel` away from `background` (see
 * [deriveSurfaceLadder]) quietly ate into that contrast on every dark theme, since nothing had
 * re-checked the two against each other. A floor enforced here can't drift out of sync the same
 * way a second independent hex constant could. */
internal fun Color.ensureLightnessGap(reference: Color, minGap: Float, awayFromWhite: Boolean): Color {
    val self = toHsl()
    val refLightness = reference.toHsl().lightness
    val target = if (awayFromWhite) refLightness - minGap else refLightness + minGap
    val adjusted = if (awayFromWhite) min(self.lightness, target) else max(self.lightness, target)
    return self.copy(lightness = adjusted.coerceIn(0f, 1f)).toColor()
}
