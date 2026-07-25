package com.ember.app.ui.auth

import androidx.compose.ui.graphics.Color
import com.ember.app.ui.theme.EmberColors
import com.ember.app.ui.theme.EmberTypography
import com.ember.app.ui.theme.ThemeKey
import com.ember.app.ui.theme.emberThemeDefinition

/** Auth/onboarding's own fixed look — always the **Ember** theme's colors/typography
 * specifically, regardless of whatever theme a signed-in session elsewhere on the same device
 * has selected (Noir, Blaze, Aurora, ...). These are the very first screens anyone sees, before
 * an account — and therefore any theme preference — even exists, so they can't reasonably shift
 * based on a preference that doesn't apply yet; every new or returning person sees the same
 * front door no matter what.
 *
 * Resolved straight from [emberThemeDefinition] rather than a hand-copied snapshot of Ember's
 * hex values — if Ember's own palette ever changes again, this stays correct automatically
 * instead of quietly drifting out of sync the way a duplicated copy would. */
private val emberDefinition = emberThemeDefinition(ThemeKey.EMBER)

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
    val glow get() = colors.glow
    val glow2 get() = colors.glow2
    val accentText get() = colors.accentText
    val border get() = colors.border

    // Chosen directly (via the WelcomeStep Preview) rather than derived — a light warm cream,
    // close to [cream] itself but distinct enough to pick out. Used for every solid accent block
    // on these screens (the primary button, the mockup's highlighted card), which is why button
    // text pairs it with dark [accentText], not light [cream] text.
    val accentFill = Color(0xFFF1E9D2)

    val display get() = typography.display
    val body get() = typography.body
}
