package com.ember.app.ui.auth

import androidx.compose.ui.graphics.Color
import com.ember.app.ui.theme.EmberColors
import com.ember.app.ui.theme.EmberTypography
import com.ember.app.ui.theme.ThemeKey
import com.ember.app.ui.theme.emberThemeDefinition

/** Auth/onboarding's own fixed look — always the **Ember New** theme's colors/typography
 * specifically, regardless of whatever theme a signed-in session elsewhere on the same device
 * has selected (Noir, Blaze, Aurora, ...). These are the very first screens anyone sees, before
 * an account — and therefore any theme preference — even exists, so they can't reasonably shift
 * based on a preference that doesn't apply yet; every new or returning person sees the same
 * front door no matter what.
 *
 * Resolved straight from [emberThemeDefinition] rather than a hand-copied snapshot of the hex
 * values — if Ember New's own palette ever changes again, this stays correct automatically
 * instead of quietly drifting out of sync the way a duplicated copy would. */
private val emberDefinition = emberThemeDefinition(ThemeKey.EMBER_NEW)

object AuthPalette {
    val colors: EmberColors = emberDefinition.colors
    val typography: EmberTypography = emberDefinition.typography

    // Flat passthroughs so call sites keep writing AuthPalette.cream / AuthPalette.glow / etc.,
    // the same shape as before, rather than AuthPalette.colors.cream everywhere.
    val background get() = colors.background
    val panel get() = colors.panel
    val cream get() = colors.cream
    val muted get() = colors.muted
    val mutedDim get() = colors.mutedDim
    // Deliberately NOT colors.glow/colors.glow2 — those are Ember New's own purple→blue accent
    // trio, still used exactly as before by every signed-in screen with that theme selected.
    // Onboarding needed its own yellow (matching the app's own launcher icon) without recoloring
    // Ember New itself for existing users, so these are fixed constants rather than passthroughs.
    // Both the same single yellow — one consistent accent throughout onboarding, not two
    // different shades — matching the launcher icon's own color as closely as possible.
    val glow = Color(0xFFFFFB0A)
    val glow2 = Color(0xFFFFFB0A)
    // Also overridden, not forwarded: Ember New's own accentText is white, meant to sit on that
    // theme's purple/blue buttons — white text on this bright a yellow reads as washed-out, low
    // contrast, and the app's own icon already establishes black-on-yellow as this brand's
    // pairing for exactly this color, not white-on-yellow.
    val accentText = Color(0xFF1A1A1A)
    val border get() = colors.border

    // Was a hardcoded light cream, then the theme's own purple accent — now this screen's own
    // yellow (see [glow] above), still paired with white accentText.
    val accentFill = glow

    val display get() = typography.display
    val body get() = typography.body
}
