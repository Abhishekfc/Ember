@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.ember.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import coil3.compose.AsyncImage
import com.ember.app.R

/**
 * Every theme Ember ships, in the exact order and lock state defined by the
 * THEMES object in ember-complete-app.jsx. Ember and Noir are free; the rest
 * are Ember Gold (locked behind subscription).
 */
enum class ThemeKey(val displayName: String, val locked: Boolean) {
    EMBER("Ember", locked = false),
    // Same background, panel, and overlayPanel (nav dock) as EMBER, pixel-for-pixel — only the
    // accent trio (glow/glow2/violet, a user-supplied purple/blue/green gradient) and the
    // camera/featured-card fill change. See emberNewDefinition's own comment for the full reasoning.
    EMBER_NEW("Ember New", locked = false),
    // The original warm-orange/violet "Ember" look, kept as its own free theme under a new name
    // once EMBER itself became the icon-matched cream/black look instead — nothing was deleted,
    // just renamed and given its own slot alongside it.
    BLAZE("Blaze", locked = false),
    NOIR("Noir", locked = false),
    AURORA("Aurora", locked = true),
    CYBER("Cyber", locked = true),
    BOTANICA("Botanica", locked = true),
    CITRUS("Citrus", locked = true),
    // Added directly against a user-supplied accent color (#A9D7FF, a pale icy blue) rather than
    // from the ember-complete-app.jsx reference this enum's own doc comment describes — not in
    // that file, a deliberate one-off addition.
    FROST("Frost", locked = true),
}

/** Mirrors the JSX theme's `bg` CSS gradient string, resolved lazily against draw size. Its
 * `panelBg` counterpart was never actually wired into any screen — dropped in favor of the
 * flat-color surface ladder below, which is what every screen has always read from instead. */
sealed interface EmberBackground {
    data class Linear(val colors: List<Color>) : EmberBackground
    data class Radial(val colors: List<Color>, val centerXFraction: Float, val centerYFraction: Float) : EmberBackground

    /**
     * A real image as the screen backdrop rather than a gradient. Drawn once at the app root by
     * [EmberAppTheme] — not per screen — so it stays fixed behind everything and never scrolls or
     * shifts with content, and so a single decode is shared by the whole app instead of every
     * screen loading its own copy.
     *
     * [asBrush] deliberately resolves to transparent for this variant: every screen paints
     * `colors.background.asBrush(...)` across itself, and any opaque color there would cover the
     * image the root just drew. [base] is what the root fills behind the image, so any area the
     * image doesn't cover (a different aspect ratio than the device) still matches the theme
     * rather than showing through to the bare window.
     */
    data class ImageBacked(val drawableResId: Int, val base: Color) : EmberBackground

    fun asBrush(size: Size): Brush = when (this) {
        is Linear -> Brush.verticalGradient(colors)
        is Radial -> Brush.radialGradient(
            colors = colors,
            center = Offset(size.width * centerXFraction, size.height * centerYFraction),
            radius = maxOf(size.width, size.height).coerceAtLeast(1f),
        )
        is ImageBacked -> SolidColor(Color.Transparent)
    }

    /**
     * The single flat color this backdrop is built around — a gradient's own first stop, or an
     * image-backed theme's [ImageBacked.base]. For anything that needs an *opaque* surface in the
     * theme's own backdrop tone: [asBrush] can't serve that, since it's a gradient for some themes
     * and deliberately transparent for image-backed ones. Used by surfaces that have to block the
     * backdrop behind them (see Memories' own card) while still looking native to the theme rather
     * than like a grey panel dropped on top of it.
     */
    fun baseColor(): Color = when (this) {
        is Linear -> colors.first()
        is Radial -> colors.first()
        is ImageBacked -> base
    }
}

/** Every theme's tonal ladder — the surfaces a screen is actually built from, background to
 * foreground:
 *
 * [background] (the gradient screen backdrop) → [surface] (a quiet in-between layer — search
 * bars, input fields, anything that should sit apart from raw background without being a full
 * card) → [panel] (the standard card/row/chip tone nearly every screen already uses) →
 * [elevatedPanel] (for a card that needs to visibly outrank its siblings — a hero card among
 * plain rows) → [overlayPanel] (dialogs, bottom sheets, anything genuinely floating above
 * everything else).
 *
 * [surface]/[elevatedPanel]/[overlayPanel] are derived, not hand-picked (see
 * [deriveSurfaceLadder]) — each theme only hand-tunes [background] and [panel], the same two
 * anchors it always has. */
data class EmberColors(
    val background: EmberBackground,
    val surface: Color,
    val panel: Color,
    val elevatedPanel: Color,
    val overlayPanel: Color,
    val cream: Color,
    val muted: Color,
    val mutedDim: Color,
    val glow: Color,
    val glow2: Color,
    val violet: Color,
    val accentText: Color,
    val border: Color,
    val isLight: Boolean,
)

data class EmberTypography(
    val display: FontFamily,
    val body: FontFamily,
)

data class EmberThemeDefinition(
    val key: ThemeKey,
    val colors: EmberColors,
    val typography: EmberTypography,
)

private fun whiteBorder(alpha: Float) = Color(red = 1f, green = 1f, blue = 1f, alpha = alpha)
private fun blackBorder(alpha: Float) = Color(red = 0f, green = 0f, blue = 0f, alpha = alpha)

// Fraunces is a variable font (weights 400-700 used); each entry pins the wght axis.
// Bold(700) added for headline moments (Home's hero line) that need real premium weight —
// the font already supports it, this just registers it alongside the existing weights.
// internal, not private — the auth/onboarding flow (see ui/auth/AuthPalette.kt) deliberately
// pins its own typography to this family rather than reading the active EmberTheme, and reuses
// this exact FontFamily instance instead of re-declaring the same Font(...) loading a second time.
internal val FrauncesFontFamily = FontFamily(
    Font(R.font.fraunces, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.fraunces, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.fraunces, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.fraunces, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Inter is a variable font (weights 400-700 used).
internal val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Space Grotesk is a variable font (weights 500-700 used).
private val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.space_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val DmSerifDisplayFontFamily = FontFamily(
    Font(R.font.dm_serif_display, FontWeight.Normal),
)

// The icon-matched look: cream (sampled from the app icon, frontend/app Icon/emberAppIcon.png)
// and black/neutral-charcoal ONLY — no purple, no gradient. glow and glow2 are set to the exact
// same value on purpose: every shared button component blends between them
// (Brush.horizontalGradient(listOf(colors.glow, colors.glow2))), and an identical pair renders
// as a genuinely flat fill rather than needing a separate "is this a gradient theme" code path
// threaded through every one of those components.
// Exact background hex specified directly by the user (#0F0F0F) — flat, both gradient stops the
// same color, rather than the radial falloff every other theme uses. panel/surface/elevated/
// overlay are all still derived from this exact value via deriveSurfaceLadder, so the tonal
// hierarchy gap holds automatically without needing its own separate re-tuning.
private val emberBackgroundBase = Color(0xFF121212)
private val emberPanel = Color(0xFF424242)
private val emberLadder = deriveSurfaceLadder(emberBackgroundBase, emberPanel, accent = Color(0xFFEDEAE0))
private val emberDefinition = EmberThemeDefinition(
    key = ThemeKey.EMBER,
    colors = EmberColors(
        background = EmberBackground.Radial(listOf(emberBackgroundBase, emberBackgroundBase), 0.20f, 0.0f),
        surface = emberLadder.surface,
        panel = emberPanel,
        elevatedPanel = emberLadder.elevatedPanel,
        overlayPanel = emberLadder.overlayPanel,
        cream = Color(0xFFEDEAE0),
        muted = Color(0xFFA8A399),
        mutedDim = Color(0xFF6E6A61).ensureLightnessGap(emberPanel, minGap = 0.26f, awayFromWhite = false),
        glow = Color(0xFFEDEAE0),
        glow2 = Color(0xFFEDEAE0),
        // The streak ring's third "blaze" color at 7+ streak — a dark neutral rather than the
        // usual violet slot's purple, so the high-streak sweep stays inside the cream/black
        // family instead of introducing a hue that isn't part of this theme at all.
        violet = Color(0xFF2A2A2A),
        accentText = Color(0xFF17150F),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = FrauncesFontFamily, body = InterFontFamily),
)

// Reuses emberLadder directly (not a fresh deriveSurfaceLadder call) so surface/overlayPanel come
// out pixel-identical to EMBER's own — deriveSurfaceLadder nudges elevatedPanel/overlayPanel a few
// percent toward whatever accent it's given, and the whole point here is that the nav dock's own
// background (overlayPanel) must not shift toward the new purple/blue accent at all. Only the
// accent trio (glow/glow2/violet) and elevatedPanel (the camera/featured-card fill, the one
// "detail" surface actually meant to change) differ from EMBER.
private val emberNewDefinition = EmberThemeDefinition(
    key = ThemeKey.EMBER_NEW,
    colors = EmberColors(
        background = EmberBackground.Radial(listOf(emberBackgroundBase, emberBackgroundBase), 0.20f, 0.0f),
        surface = emberLadder.surface,
        panel = emberPanel,
        // Dark, same as EMBER's own — this was a near-white (0xFFF6F3E9) for a while, set to make
        // one specific surface white, but elevatedPanel isn't a one-surface token: it's what every
        // avatar placeholder circle and every secondary button (Pin as partner, Decline, Cancel
        // request) is filled with. At near-white those all turned white, and since `cream` is pure
        // white in this theme, their labels became white-on-white and vanished entirely.
        elevatedPanel = emberLadder.elevatedPanel,
        overlayPanel = emberLadder.overlayPanel,
        // Pure white, not EMBER's own warm-cream — this is the one other spot asked to move
        // away from cream specifically.
        cream = Color(0xFFFFFFFF),
        // Neutral grays, not EMBER's own warm taupe (0xFFA8A399/0xFF6E6A61) — that warmth is what
        // still read as "cream" in placeholder text and disabled-button states even after cream
        // itself became white, since it's a whole extra place the same warm bias was hiding.
        muted = Color(0xFFA3A3AA),
        mutedDim = Color(0xFF69696F).ensureLightnessGap(emberPanel, minGap = 0.26f, awayFromWhite = false),
        // User-supplied "Aurora Gradient" palette: Ember Purple -> Sky Mist -> Soft Sage. Used
        // exactly where every theme's accent trio already shows up — the avatar ring's sweep,
        // the streak icon, CTA button fills, the camera shutter's own gradient — never the
        // background or the nav dock.
        glow = Color(0xFF7B61FF),
        glow2 = Color(0xFF5DADE2),
        violet = Color(0xFF7ED8B3),
        accentText = Color(0xFFFFFFFF),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = FrauncesFontFamily, body = InterFontFamily),
)

// The original warm-orange/violet look this same slot used before EMBER became the icon-matched
// cream/black theme above — kept exactly as it was, just under its own name now instead of
// being lost.
// Kept at its original depth (only Ember got the brighter background treatment) — background
// unchanged from this theme's own original tone, panel still widened +14% for real tonal
// separation, outer gradient stop just lifted off literal pure black.
private val blazeBackgroundBase = Color(0xFF121212)
private val blazePanel = Color(0xFF313038)
private val blazeLadder = deriveSurfaceLadder(blazeBackgroundBase, blazePanel, accent = Color(0xFFFFA94D))
private val blazeDefinition = EmberThemeDefinition(
    key = ThemeKey.BLAZE,
    colors = EmberColors(
        background = EmberBackground.Radial(listOf(blazeBackgroundBase, blazeBackgroundBase), 0.20f, 0.0f),
        surface = blazeLadder.surface,
        panel = blazePanel,
        elevatedPanel = blazeLadder.elevatedPanel,
        overlayPanel = blazeLadder.overlayPanel,
        cream = Color(0xFFFBF8F3),
        muted = Color(0xFF9B93B8),
        mutedDim = Color(0xFF6B6488).ensureLightnessGap(blazePanel, minGap = 0.26f, awayFromWhite = false),
        glow = Color(0xFFFFA94D),
        glow2 = Color(0xFFFF8A5C),
        violet = Color(0xFF8B7FF2),
        accentText = Color(0xFF1A1408),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = FrauncesFontFamily, body = InterFontFamily),
)

// Kept at its original depth — see blazeDefinition's identical comment. Noir's own "luxury
// monochrome" identity leans on real darkness, not just Ember's brighter, more everyday one.
private val noirBackgroundBase = Color(0xFF121212)
private val noirPanel = Color(0xFF2E2E2E)
private val noirLadder = deriveSurfaceLadder(noirBackgroundBase, noirPanel, accent = Color(0xFFF5F5F5))
private val noirDefinition = EmberThemeDefinition(
    key = ThemeKey.NOIR,
    colors = EmberColors(
        background = EmberBackground.Linear(listOf(noirBackgroundBase, noirBackgroundBase)),
        surface = noirLadder.surface,
        panel = noirPanel,
        elevatedPanel = noirLadder.elevatedPanel,
        overlayPanel = noirLadder.overlayPanel,
        cream = Color(0xFFF5F5F5),
        muted = Color(0xFF9A9A9A),
        mutedDim = Color(0xFF666666).ensureLightnessGap(noirPanel, minGap = 0.26f, awayFromWhite = false),
        glow = Color(0xFFF5F5F5),
        glow2 = Color(0xFFC9C9C9),
        violet = Color(0xFFC9C9C9),
        accentText = Color(0xFF0A0A0A),
        border = whiteBorder(0.1f),
        isLight = false,
    ),
    typography = EmberTypography(display = SpaceGroteskFontFamily, body = InterFontFamily),
)

// Kept at its original depth — see blazeDefinition's identical comment.
private val auroraBackgroundBase = Color(0xFF0A100F)
private val auroraPanel = Color(0xFF2B3633)
private val auroraLadder = deriveSurfaceLadder(auroraBackgroundBase, auroraPanel, accent = Color(0xFF4FE3C1))
private val auroraDefinition = EmberThemeDefinition(
    key = ThemeKey.AURORA,
    colors = EmberColors(
        // Image backdrop rather than a gradient, same as Cyber — see EmberBackground.ImageBacked.
        background = EmberBackground.ImageBacked(R.drawable.aurora2_theme_background, auroraBackgroundBase),
        surface = auroraLadder.surface,
        panel = auroraPanel,
        elevatedPanel = auroraLadder.elevatedPanel,
        overlayPanel = auroraLadder.overlayPanel,
        cream = Color(0xFFE8FBF6),
        muted = Color(0xFF7FA8A3),
        mutedDim = Color(0xFF4C6E6A).ensureLightnessGap(auroraPanel, minGap = 0.26f, awayFromWhite = false),
        glow = Color(0xFF4FE3C1),
        glow2 = Color(0xFF5CC8FF),
        violet = Color(0xFF5CC8FF),
        accentText = Color(0xFF04211E),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = SpaceGroteskFontFamily, body = InterFontFamily),
)

// Kept at its original depth — see blazeDefinition's identical comment.
private val cyberBackgroundBase = Color(0xFF0E0B14)
private val cyberPanel = Color(0xFF342D3A)
private val cyberLadder = deriveSurfaceLadder(cyberBackgroundBase, cyberPanel, accent = Color(0xFFFF2EC4))
private val cyberDefinition = EmberThemeDefinition(
    key = ThemeKey.CYBER,
    colors = EmberColors(
        // The one theme with a real image backdrop rather than a gradient — see
        // EmberBackground.ImageBacked for how it's drawn (once, at the app root, fixed).
        background = EmberBackground.ImageBacked(R.drawable.cyber2_theme_background, cyberBackgroundBase),
        surface = cyberLadder.surface,
        panel = cyberPanel,
        elevatedPanel = cyberLadder.elevatedPanel,
        overlayPanel = cyberLadder.overlayPanel,
        cream = Color(0xFFF3E8FF),
        muted = Color(0xFF8A72B8),
        mutedDim = Color(0xFF5A4880).ensureLightnessGap(cyberPanel, minGap = 0.26f, awayFromWhite = false),
        glow = Color(0xFFFF2EC4),
        glow2 = Color(0xFF7B2FFF),
        violet = Color(0xFF7B2FFF),
        accentText = Color(0xFF0A0014),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = SpaceGroteskFontFamily, body = InterFontFamily),
)

// Kept at its original depth — see blazeDefinition's identical comment.
private val botanicaBackgroundBase = Color(0xFF0C110D)
private val botanicaPanel = Color(0xFF2F362F)
private val botanicaLadder = deriveSurfaceLadder(botanicaBackgroundBase, botanicaPanel, accent = Color(0xFFC9A15A))
private val botanicaDefinition = EmberThemeDefinition(
    key = ThemeKey.BOTANICA,
    colors = EmberColors(
        // Image backdrop rather than a gradient, same as Cyber/Aurora — see EmberBackground.ImageBacked.
        background = EmberBackground.ImageBacked(R.drawable.botanica_theme_background, botanicaBackgroundBase),
        surface = botanicaLadder.surface,
        panel = botanicaPanel,
        elevatedPanel = botanicaLadder.elevatedPanel,
        overlayPanel = botanicaLadder.overlayPanel,
        cream = Color(0xFFF0EAD8),
        muted = Color(0xFF8FA894),
        mutedDim = Color(0xFF587060).ensureLightnessGap(botanicaPanel, minGap = 0.26f, awayFromWhite = false),
        glow = Color(0xFFC9A15A),
        glow2 = Color(0xFF8FBF7A),
        violet = Color(0xFF8FBF7A),
        accentText = Color(0xFF14251C),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = FrauncesFontFamily, body = InterFontFamily),
)

// Kept at its original depth — see blazeDefinition's identical comment.
private val citrusBackgroundBase = Color(0xFF111111)
private val citrusPanel = Color(0xFF353535)
private val citrusLadder = deriveSurfaceLadder(citrusBackgroundBase, citrusPanel, accent = Color(0xFFF5D90A))
private val citrusDefinition = EmberThemeDefinition(
    key = ThemeKey.CITRUS,
    colors = EmberColors(
        background = EmberBackground.Linear(listOf(citrusBackgroundBase, Color(0xFF0E0E0E))),
        surface = citrusLadder.surface,
        panel = citrusPanel,
        elevatedPanel = citrusLadder.elevatedPanel,
        overlayPanel = citrusLadder.overlayPanel,
        cream = Color(0xFFFFFDF5),
        muted = Color(0xFF9A9A9A),
        mutedDim = Color(0xFF5C5C5C).ensureLightnessGap(citrusPanel, minGap = 0.26f, awayFromWhite = false),
        glow = Color(0xFFF5D90A),
        glow2 = Color(0xFFFF7A1A),
        violet = Color(0xFFFF7A1A),
        accentText = Color(0xFF141400),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = SpaceGroteskFontFamily, body = InterFontFamily),
)

// Built directly around a user-supplied accent (#CCE7FF, a very pale icy blue) rather than
// derived from anywhere else — a dark, cold-toned counterpart to Aurora's teal-green rather than
// a repeat of it. Since #CCE7FF itself is close to white, glow2 is a genuinely deeper, more
// saturated blue rather than just a slightly-darker version of the same pale tone — the streak
// ring's gradient sweep needs real range between its two stops, not two shades that read as
// almost the same color. Background/panel gradients lean a touch more saturated-blue than a
// flat navy-black too, so the theme reads as "icy," not just "dark with a blue accent."
// Kept at its original depth — see blazeDefinition's identical comment.
private val frostBackgroundBase = Color(0xFF0B121B)
private val frostPanel = Color(0xFF24384A)
private val frostLadder = deriveSurfaceLadder(frostBackgroundBase, frostPanel, accent = Color(0xFFCCE7FF))
private val frostDefinition = EmberThemeDefinition(
    key = ThemeKey.FROST,
    colors = EmberColors(
        // Image backdrop rather than a gradient, same as Cyber/Aurora — see EmberBackground.ImageBacked.
        background = EmberBackground.ImageBacked(R.drawable.frost_theme_background, frostBackgroundBase),
        surface = frostLadder.surface,
        panel = frostPanel,
        elevatedPanel = frostLadder.elevatedPanel,
        overlayPanel = frostLadder.overlayPanel,
        cream = Color(0xFFF2F9FF),
        muted = Color(0xFF8FB4D6),
        mutedDim = Color(0xFF52708C).ensureLightnessGap(frostPanel, minGap = 0.26f, awayFromWhite = false),
        glow = Color(0xFFCCE7FF),
        glow2 = Color(0xFF4F9FE6),
        violet = Color(0xFF4F9FE6),
        accentText = Color(0xFF031320),
        border = whiteBorder(0.10f),
        isLight = false,
    ),
    typography = EmberTypography(display = SpaceGroteskFontFamily, body = InterFontFamily),
)

fun emberThemeDefinition(key: ThemeKey): EmberThemeDefinition = when (key) {
    ThemeKey.EMBER -> emberDefinition
    ThemeKey.EMBER_NEW -> emberNewDefinition
    ThemeKey.BLAZE -> blazeDefinition
    ThemeKey.NOIR -> noirDefinition
    ThemeKey.AURORA -> auroraDefinition
    ThemeKey.CYBER -> cyberDefinition
    ThemeKey.BOTANICA -> botanicaDefinition
    ThemeKey.CITRUS -> citrusDefinition
    ThemeKey.FROST -> frostDefinition
}

private val LocalEmberThemeDefinition: ProvidableCompositionLocal<EmberThemeDefinition> =
    staticCompositionLocalOf { noirDefinition }

/** Pulls colors/fonts from the single active theme, the same way MaterialTheme.colorScheme does. */
object EmberTheme {
    val colors: EmberColors
        @Composable get() = LocalEmberThemeDefinition.current.colors

    val typography: EmberTypography
        @Composable get() = LocalEmberThemeDefinition.current.typography

    val key: ThemeKey
        @Composable get() = LocalEmberThemeDefinition.current.key
}

@Composable
fun EmberAppTheme(themeKey: ThemeKey, content: @Composable () -> Unit) {
    val definition = emberThemeDefinition(themeKey)
    CompositionLocalProvider(LocalEmberThemeDefinition provides definition) {
        val background = definition.colors.background
        // The Box is unconditional, and content() always sits in this one spot inside it, even
        // for themes with no image at all. Wrapping only the image-backed case was tried and
        // broke theme switching outright: selecting a theme with a different background *kind*
        // changed the shape of the composition around content(), so the entire app subtree was
        // torn down and rebuilt — throwing away every remembered state inside it, including which
        // screen was open and the picker's own staged selection. Keeping the structure identical
        // for every theme means switching only ever changes colors, never the tree.
        Box(modifier = Modifier.fillMaxSize()) {
            if (background is EmberBackground.ImageBacked) {
                // Behind every screen, at the root — so it can't scroll, shift, or be re-drawn per
                // screen. ContentScale.Crop fills the device regardless of the image's own aspect
                // ratio, with [base] underneath covering anything Crop still leaves uncovered.
                Box(modifier = Modifier.fillMaxSize().background(background.base)) {
                    // AsyncImage, not painterResource: painterResource decodes the bitmap
                    // synchronously on the main thread *during composition*, so the whole app's
                    // first frames were blocked behind that decode — the background painted, then
                    // everything else appeared a beat later, which only happened on image-backed
                    // themes. Coil decodes off the main thread and caches the result, so content
                    // composes and renders at its normal speed and the image simply arrives when
                    // it's ready, over [base] which is already covering the screen in the meantime.
                    AsyncImage(
                        model = background.drawableResId,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            content()
        }
    }
}
