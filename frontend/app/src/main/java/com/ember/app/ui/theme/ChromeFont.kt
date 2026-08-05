@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.ember.app.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.ember.app.R

/**
 * Public Sans — used for UI chrome (nav bar, buttons, form fields) that should stay visually
 * consistent no matter which theme is active. Distinct from [EmberTypography], which is
 * per-theme and used for expressive content (greetings, friend names, theme picker rows).
 */
val PublicSansFontFamily = FontFamily(
    Font(R.font.public_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.public_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.public_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.public_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Courgette — the cursive "Emigo" wordmark on the Home tab only. Fixed font, but its color
 *  follows the active theme's text color rather than a fixed brand color. */
val CourgetteFontFamily = FontFamily(Font(R.font.courgette, FontWeight.Normal))
