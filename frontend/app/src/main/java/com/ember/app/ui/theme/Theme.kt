@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.ember.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ember.app.R

/**
 * Every theme Ember ships, in the exact order and lock state defined by the
 * THEMES object in ember-complete-app.jsx. Ember and Noir are free; the rest
 * are Ember Gold (locked behind subscription).
 */
enum class ThemeKey(val displayName: String, val locked: Boolean) {
    EMBER("Ember", locked = false),
    NOIR("Noir", locked = false),
    AURORA("Aurora", locked = true),
    POLAROID("Polaroid", locked = true),
    SUNROOM("Sunroom", locked = true),
    CYBER("Cyber", locked = true),
    BOTANICA("Botanica", locked = true),
    CITRUS("Citrus", locked = true),
}

/** Mirrors the JSX theme's `bg`/`panelBg` CSS gradient strings, resolved lazily against draw size. */
sealed interface EmberBackground {
    data class Linear(val colors: List<Color>) : EmberBackground
    data class Radial(val colors: List<Color>, val centerXFraction: Float, val centerYFraction: Float) : EmberBackground

    fun asBrush(size: Size): Brush = when (this) {
        is Linear -> Brush.verticalGradient(colors)
        is Radial -> Brush.radialGradient(
            colors = colors,
            center = Offset(size.width * centerXFraction, size.height * centerYFraction),
            radius = maxOf(size.width, size.height).coerceAtLeast(1f),
        )
    }
}

/** One-to-one with every color field on a JSX theme object. */
data class EmberColors(
    val background: EmberBackground,
    val panelBackground: EmberBackground,
    val panel: Color,
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
private val FrauncesFontFamily = FontFamily(
    Font(R.font.fraunces, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.fraunces, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.fraunces, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.fraunces, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

// Inter is a variable font (weights 400-700 used).
private val InterFontFamily = FontFamily(
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

private val emberDefinition = EmberThemeDefinition(
    key = ThemeKey.EMBER,
    colors = EmberColors(
        background = EmberBackground.Radial(listOf(Color(0xFF0E0D14), Color(0xFF09080D)), 0.20f, 0.0f),
        panelBackground = EmberBackground.Radial(listOf(Color(0xFF2C2650), Color(0xFF0D0B18)), 0.30f, 0.0f),
        // Clearly lighter than either background stop (not sandwiched between them) — a radial
        // background's brightness varies by position on screen, so a panel color tuned to only
        // sit "between" the two stops can end up blending into whichever part of the gradient a
        // given card happens to land on. Comfortably brighter than the lightest stop keeps panel
        // edges legible regardless of where on screen they scroll to.
        panel = Color(0xFF242329),
        cream = Color(0xFFFBF8F3),
        muted = Color(0xFF9B93B8),
        mutedDim = Color(0xFF6B6488),
        glow = Color(0xFFFFA94D),
        glow2 = Color(0xFFFF8A5C),
        violet = Color(0xFF8B7FF2),
        accentText = Color(0xFF1A1408),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = FrauncesFontFamily, body = InterFontFamily),
)

private val noirDefinition = EmberThemeDefinition(
    key = ThemeKey.NOIR,
    colors = EmberColors(
        background = EmberBackground.Linear(listOf(Color(0xFF0A0A0A), Color(0xFF000000))),
        panelBackground = EmberBackground.Linear(listOf(Color(0xFF141414), Color(0xFF0A0A0A))),
        panel = Color(0xFF1A1A1A),
        cream = Color(0xFFF5F5F5),
        muted = Color(0xFF9A9A9A),
        mutedDim = Color(0xFF666666),
        glow = Color(0xFFF5F5F5),
        glow2 = Color(0xFFC9C9C9),
        violet = Color(0xFFC9C9C9),
        accentText = Color(0xFF0A0A0A),
        border = whiteBorder(0.1f),
        isLight = false,
    ),
    typography = EmberTypography(display = SpaceGroteskFontFamily, body = InterFontFamily),
)

private val auroraDefinition = EmberThemeDefinition(
    key = ThemeKey.AURORA,
    colors = EmberColors(
        background = EmberBackground.Radial(listOf(Color(0xFF0A100F), Color(0xFF070B0A)), 0.25f, 0.0f),
        panelBackground = EmberBackground.Radial(listOf(Color(0xFF123F42), Color(0xFF061417)), 0.30f, 0.0f),
        panel = Color(0xFF202826),
        cream = Color(0xFFE8FBF6),
        muted = Color(0xFF7FA8A3),
        mutedDim = Color(0xFF4C6E6A),
        glow = Color(0xFF4FE3C1),
        glow2 = Color(0xFF5CC8FF),
        violet = Color(0xFF5CC8FF),
        accentText = Color(0xFF04211E),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = SpaceGroteskFontFamily, body = InterFontFamily),
)

private val polaroidDefinition = EmberThemeDefinition(
    key = ThemeKey.POLAROID,
    colors = EmberColors(
        background = EmberBackground.Linear(listOf(Color(0xFFF2E9D8), Color(0xFFE8DBC0))),
        panelBackground = EmberBackground.Linear(listOf(Color(0xFFFAF4E6), Color(0xFFEFE3CB))),
        panel = Color(0xFFFBF4E4),
        cream = Color(0xFF2E2418),
        muted = Color(0xFF8A7A5E),
        mutedDim = Color(0xFFA99871),
        glow = Color(0xFFC1502E),
        glow2 = Color(0xFFD97F3E),
        violet = Color(0xFFD97F3E),
        accentText = Color(0xFFFBF4E4),
        border = blackBorder(0.08f),
        isLight = true,
    ),
    typography = EmberTypography(display = DmSerifDisplayFontFamily, body = InterFontFamily),
)

// Redesigned per user feedback ("too bright", palette "not good") — the original was a near-white
// pale-pink background with two near-identical pale peach accents (glow2 and violet were
// literally the same color, so the streak ring's ember-to-violet "blaze" never actually showed a
// violet). Deeper, warmer cream reads as sunlit wood/linen rather than a glary near-white; a
// richer terracotta + gold pair gives real contrast against that cream instead of pale-on-pale;
// a genuine dusty plum gives the high-streak blaze an actual third hue to sweep through.
private val sunroomDefinition = EmberThemeDefinition(
    key = ThemeKey.SUNROOM,
    colors = EmberColors(
        background = EmberBackground.Linear(listOf(Color(0xFFF7E9D7), Color(0xFFF0D9BE))),
        panelBackground = EmberBackground.Linear(listOf(Color(0xFFFCF3E6), Color(0xFFF6E7D2))),
        panel = Color(0xFFFFFDF8),
        cream = Color(0xFF3A281F),
        muted = Color(0xFFA0806C),
        mutedDim = Color(0xFFC2A084),
        glow = Color(0xFFD46A3D),
        glow2 = Color(0xFFE8A94F),
        violet = Color(0xFF9B6B8C),
        accentText = Color(0xFFFFF8F0),
        border = blackBorder(0.09f),
        isLight = true,
    ),
    typography = EmberTypography(display = FrauncesFontFamily, body = InterFontFamily),
)

private val cyberDefinition = EmberThemeDefinition(
    key = ThemeKey.CYBER,
    colors = EmberColors(
        background = EmberBackground.Radial(listOf(Color(0xFF0E0B14), Color(0xFF08060D)), 0.25f, 0.0f),
        panelBackground = EmberBackground.Radial(listOf(Color(0xFF22103D), Color(0xFF05020B)), 0.30f, 0.0f),
        panel = Color(0xFF252029),
        cream = Color(0xFFF3E8FF),
        muted = Color(0xFF8A72B8),
        mutedDim = Color(0xFF5A4880),
        glow = Color(0xFFFF2EC4),
        glow2 = Color(0xFF7B2FFF),
        violet = Color(0xFF7B2FFF),
        accentText = Color(0xFF0A0014),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = SpaceGroteskFontFamily, body = InterFontFamily),
)

private val botanicaDefinition = EmberThemeDefinition(
    key = ThemeKey.BOTANICA,
    colors = EmberColors(
        background = EmberBackground.Radial(listOf(Color(0xFF0C110D), Color(0xFF080B09)), 0.20f, 0.0f),
        panelBackground = EmberBackground.Radial(listOf(Color(0xFF204536), Color(0xFF0A1712)), 0.30f, 0.0f),
        panel = Color(0xFF232823),
        cream = Color(0xFFF0EAD8),
        muted = Color(0xFF8FA894),
        mutedDim = Color(0xFF587060),
        glow = Color(0xFFC9A15A),
        glow2 = Color(0xFF8FBF7A),
        violet = Color(0xFF8FBF7A),
        accentText = Color(0xFF14251C),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = FrauncesFontFamily, body = InterFontFamily),
)

private val citrusDefinition = EmberThemeDefinition(
    key = ThemeKey.CITRUS,
    colors = EmberColors(
        background = EmberBackground.Linear(listOf(Color(0xFF111111), Color(0xFF000000))),
        panelBackground = EmberBackground.Linear(listOf(Color(0xFF1A1A1A), Color(0xFF0D0D0D))),
        panel = Color(0xFF1C1C1C),
        cream = Color(0xFFFFFDF5),
        muted = Color(0xFF9A9A9A),
        mutedDim = Color(0xFF5C5C5C),
        glow = Color(0xFFF5D90A),
        glow2 = Color(0xFFFF7A1A),
        violet = Color(0xFFFF7A1A),
        accentText = Color(0xFF141400),
        border = whiteBorder(0.08f),
        isLight = false,
    ),
    typography = EmberTypography(display = SpaceGroteskFontFamily, body = InterFontFamily),
)

fun emberThemeDefinition(key: ThemeKey): EmberThemeDefinition = when (key) {
    ThemeKey.EMBER -> emberDefinition
    ThemeKey.NOIR -> noirDefinition
    ThemeKey.AURORA -> auroraDefinition
    ThemeKey.POLAROID -> polaroidDefinition
    ThemeKey.SUNROOM -> sunroomDefinition
    ThemeKey.CYBER -> cyberDefinition
    ThemeKey.BOTANICA -> botanicaDefinition
    ThemeKey.CITRUS -> citrusDefinition
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
    CompositionLocalProvider(LocalEmberThemeDefinition provides emberThemeDefinition(themeKey)) {
        content()
    }
}
