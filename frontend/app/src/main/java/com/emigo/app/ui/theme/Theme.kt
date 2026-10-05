@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.emigo.app.ui.theme

import androidx.annotation.StringRes
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
import com.emigo.app.R

/**
 * Every theme Emigo ships. Cream, Dusk, Blaze, Noir and Ember are free; Aurora, Cyber, Botanica
 * and Frost are Emigo Gold.
 *
 * Never rename a constant: [ThemePreferenceStore] saves a theme by `name`, so a rename would reset
 * every device that had it selected back to [DEFAULT]. Only the display label (the string
 * resource) may change, which is why EMBER shows as "Cream" and CITRUS as "Ember".
 */
enum class ThemeKey(@StringRes val displayNameRes: Int, val locked: Boolean) {
    EMBER(R.string.theme_name_cream, locked = false),
    // Same background, panel and nav dock as EMBER; only the accent colors and the camera and
    // featured-card fill differ (see emberNewDefinition).
    EMBER_NEW(R.string.theme_name_dusk, locked = false),
    // The original warm-orange/violet look, kept as its own free theme after EMBER became the
    // cream and black look.
    BLAZE(R.string.theme_name_blaze, locked = false),
    NOIR(R.string.theme_name_noir, locked = false),
    AURORA(R.string.theme_name_aurora, locked = true),
    CYBER(R.string.theme_name_cyber, locked = true),
    BOTANICA(R.string.theme_name_botanica, locked = true),
    // The app's default (see [DEFAULT]), so it must stay free: the theme every new account opens
    // in can't be one they're locked out of.
    CITRUS(R.string.theme_name_ember, locked = false),
    // Added from a supplied accent color (a pale icy blue), not from the original design reference.
    FROST(R.string.theme_name_frost, locked = true),
    ;

    companion object {
        /** The one place the default theme is decided. Every fallback (new account, signed-out
         * device, lapsed subscription) reads this instead of naming a theme.
         *
         * Must be an unlocked theme: ThemeViewModel's lapsed-subscription guard falls back to this
         * value, so a locked default would be overridden immediately. */
        val DEFAULT = CITRUS
    }
}

/** A theme's screen backdrop, resolved against the draw size. The design reference also had a
 * `panelBg`; it was never used, because screens read the flat surface ladder below instead. */
sealed interface EmberBackground {
    data class Linear(val colors: List<Color>) : EmberBackground
    data class Radial(val colors: List<Color>, val centerXFraction: Float, val centerYFraction: Float) : EmberBackground

    /**
     * A real image as the backdrop instead of a gradient. [EmberAppTheme] draws it once at the app
     * root, so it stays fixed behind everything and one decode is shared by the whole app.
     *
     * [asBrush] is transparent for this variant on purpose: every screen paints
     * `colors.background.asBrush(...)` across itself, and an opaque color there would cover the
     * image. [base] is what the root fills behind the image, so any area it doesn't cover (a
     * different aspect ratio) still matches the theme.
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
     * The single flat color this backdrop is built around: a gradient's first stop, or
     * [ImageBacked.base]. Use it for surfaces that must be opaque in the backdrop's tone, since
     * [asBrush] is a gradient for some themes and transparent for image-backed ones (see
     * Memories' card).
     */
    fun baseColor(): Color = when (this) {
        is Linear -> colors.first()
        is Radial -> colors.first()
        is ImageBacked -> base
    }
}

/** A theme's surface ladder, from background to foreground:
 *
 * [background] (the screen backdrop) → [surface] (search bars, input fields: apart from the
 * background but not a card) → [panel] (the standard card, row and chip tone) → [elevatedPanel]
 * (a card that must outrank its siblings) → [overlayPanel] (dialogs, sheets, the nav dock).
 *
 * [surface], [elevatedPanel] and [overlayPanel] are derived (see [deriveSurfaceLadder]); each
 * theme hand-tunes only [background] and [panel]. */
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

// Variable font; each entry pins the wght axis. internal because the auth flow (AuthPalette)
// deliberately pins its own typography to this family and reuses this instance.
internal val FrauncesFontFamily = FontFamily(
    Font(R.font.fraunces, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.fraunces, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.fraunces, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.fraunces, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Variable font; weights 400-700 used.
internal val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Variable font; weights 500-700 used.
private val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.space_grotesk, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.space_grotesk, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val DmSerifDisplayFontFamily = FontFamily(
    Font(R.font.dm_serif_display, FontWeight.Normal),
)

// The icon-matched look: cream (sampled from the app icon) on neutral charcoal, no purple and no
// gradient. glow and glow2 are identical on purpose: every shared button blends between them, so
// an identical pair renders a flat fill without a "gradient theme" special case in each component.
// The background is flat too (both stops the same). panel, surface, elevated and overlay are all
// derived from it by deriveSurfaceLadder, so the tonal gaps hold without separate tuning.
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
        // The streak ring's third color at 7+ streak: a dark neutral instead of purple, so the
        // sweep stays inside this theme's cream and black.
        violet = Color(0xFF2A2A2A),
        accentText = Color(0xFF17150F),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = FrauncesFontFamily, body = InterFontFamily),
)

// Reuses emberLadder instead of a fresh deriveSurfaceLadder call: that function nudges
// elevatedPanel and overlayPanel toward the accent, and the nav dock (overlayPanel) must not shift
// toward this theme's purple and blue. Only the accent colors differ from EMBER.
private val emberNewDefinition = EmberThemeDefinition(
    key = ThemeKey.EMBER_NEW,
    colors = EmberColors(
        background = EmberBackground.Radial(listOf(emberBackgroundBase, emberBackgroundBase), 0.20f, 0.0f),
        surface = emberLadder.surface,
        panel = emberPanel,
        // Dark like EMBER's. A near-white value was tried for one surface, but elevatedPanel also
        // fills avatar placeholders and secondary buttons (Pin as partner, Decline, Cancel
        // request); `cream` is white in this theme, so their labels became white on white.
        elevatedPanel = emberLadder.elevatedPanel,
        overlayPanel = emberLadder.overlayPanel,
        // Pure white instead of EMBER's warm cream.
        cream = Color(0xFFFFFFFF),
        // Neutral grays: EMBER's warm taupe still read as cream in placeholder text and disabled
        // states even after `cream` itself became white.
        muted = Color(0xFFA3A3AA),
        mutedDim = Color(0xFF69696F).ensureLightnessGap(emberPanel, minGap = 0.26f, awayFromWhite = false),
        // Purple, blue, green. Used only where every theme's accent trio appears (avatar ring
        // sweep, streak icon, CTA fills, shutter), never the background or the nav dock.
        glow = Color(0xFF7B61FF),
        glow2 = Color(0xFF5DADE2),
        violet = Color(0xFF7ED8B3),
        accentText = Color(0xFFFFFFFF),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = FrauncesFontFamily, body = InterFontFamily),
)

// The original warm-orange/violet look, unchanged. The themes below keep their original depth
// (only Ember got the brighter background): the background is unchanged and the panel was widened
// for tonal separation.
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

// Original depth, like Blaze: Noir's monochrome "luxury" look relies on real darkness.
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

// Original depth, like Blaze.
private val auroraBackgroundBase = Color(0xFF0A100F)
private val auroraPanel = Color(0xFF2B3633)
private val auroraLadder = deriveSurfaceLadder(auroraBackgroundBase, auroraPanel, accent = Color(0xFF4FE3C1))
private val auroraDefinition = EmberThemeDefinition(
    key = ThemeKey.AURORA,
    colors = EmberColors(
        // Image backdrop, see EmberBackground.ImageBacked.
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

// Original depth, like Blaze.
private val cyberBackgroundBase = Color(0xFF0E0B14)
private val cyberPanel = Color(0xFF342D3A)
private val cyberLadder = deriveSurfaceLadder(cyberBackgroundBase, cyberPanel, accent = Color(0xFFFF2EC4))
private val cyberDefinition = EmberThemeDefinition(
    key = ThemeKey.CYBER,
    colors = EmberColors(
        // Image backdrop, see EmberBackground.ImageBacked.
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

// Original depth, like Blaze.
private val botanicaBackgroundBase = Color(0xFF0C110D)
private val botanicaPanel = Color(0xFF2F362F)
private val botanicaLadder = deriveSurfaceLadder(botanicaBackgroundBase, botanicaPanel, accent = Color(0xFFC9A15A))
private val botanicaDefinition = EmberThemeDefinition(
    key = ThemeKey.BOTANICA,
    colors = EmberColors(
        // Image backdrop, see EmberBackground.ImageBacked.
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

// Original depth, like Blaze.
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

// Built around a pale icy-blue accent (#CCE7FF), a cold counterpart to Aurora's teal-green. The
// accent is close to white, so glow2 is a deeper, more saturated blue: the streak ring's gradient
// sweep needs real range between its two stops. Background and panel lean saturated blue so the
// theme reads as "icy", not just dark with a blue accent. Original depth, like Blaze.
private val frostBackgroundBase = Color(0xFF0B121B)
private val frostPanel = Color(0xFF24384A)
private val frostLadder = deriveSurfaceLadder(frostBackgroundBase, frostPanel, accent = Color(0xFFCCE7FF))
private val frostDefinition = EmberThemeDefinition(
    key = ThemeKey.FROST,
    colors = EmberColors(
        // Image backdrop, see EmberBackground.ImageBacked.
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
    staticCompositionLocalOf { emberThemeDefinition(ThemeKey.DEFAULT) }

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
    // No LocalOverscrollFactory override: a custom rubber-band stretch, and later a wrapper that
    // softened flick bounce, were both tried and reverted. Removing the wrapper showed it was
    // the cause of a black flash when swiping to or from the Camera tab on a real device (it runs
    // on every scroll frame app-wide, including the tab pager). Leave the default.
    CompositionLocalProvider(LocalEmberThemeDefinition provides definition) {
        val background = definition.colors.background
        // The Box is unconditional, with content() always in the same spot, even for themes with
        // no image. Wrapping only the image-backed case broke theme switching: a theme with a
        // different background kind changed the composition around content(), tearing down the
        // whole app and losing remembered state (the open screen, the picker's staged choice).
        // With the same structure for every theme, switching only changes colors.
        Box(modifier = Modifier.fillMaxSize()) {
            if (background is EmberBackground.ImageBacked) {
                // At the root so it can't scroll, shift, or redraw per screen. Crop fills the
                // device whatever the image's aspect ratio, with [base] under anything Crop misses.
                Box(modifier = Modifier.fillMaxSize().background(background.base)) {
                    // AsyncImage, not painterResource: painterResource decodes on the main thread
                    // during composition, which blocked the app's first frames on image-backed
                    // themes (the background painted, then everything else a beat later). Coil
                    // decodes off-thread and caches, so content renders at normal speed and the
                    // image arrives over [base], which already covers the screen meanwhile.
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
