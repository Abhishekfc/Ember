package com.emigo.app.ui.theme

import androidx.compose.ui.graphics.Color

/** Colors that stay the same in every theme, so they live here once instead of being retyped per screen. */
object EmberFixedColors {
    /** Delete / unsend / sign-out actions. */
    val destructive = Color(0xFFB3261E)

    /** Text drawn directly over a photo, where the theme's own colors can't be trusted to contrast. */
    val onPhotoText = Color(0xFFFBF8F3)
}
