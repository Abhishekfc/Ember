package com.emigo.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import com.emigo.app.ui.theme.EmberColors
import com.emigo.app.ui.theme.ThemeKey
import kotlin.math.cos
import kotlin.math.sin

/** Builds a linear gradient along the same direction a CSS `linear-gradient(angleDeg, ...)` would use. */
fun cssAngleGradient(angleDegrees: Float, colors: List<Color>, size: Size): Brush {
    val radians = Math.toRadians(angleDegrees.toDouble())
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    val half = Offset(size.width, size.height).getDistance() / 2f
    val center = Offset(size.width / 2f, size.height / 2f)
    return Brush.linearGradient(
        colors = colors,
        start = center - Offset(dx, dy) * half,
        end = center + Offset(dx, dy) * half,
    )
}

/**
 * Every primary button/pill across the app fills with the same glow → glow2 sweep — except
 * Citrus, which asked for a flat, complete yellow instead of any gradient, in the exact shade its
 * own notification toggle already uses for "on" (`colors.glow`). Centralized here rather than an
 * `if (key == CITRUS)` at each of the dozen-plus call sites, so this is the one place that
 * decision lives.
 */
fun emberButtonBrush(key: ThemeKey, colors: EmberColors, size: Size, angleDegrees: Float = 160f): Brush =
    if (key == ThemeKey.CITRUS) SolidColor(colors.glow) else cssAngleGradient(angleDegrees, listOf(colors.glow, colors.glow2), size)

/** Same rule as the sized overload above, for the handful of buttons that fill with a plain
 * [Brush.linearGradient] (auto-fit to the draw bounds) instead of [cssAngleGradient]. */
fun emberButtonBrush(key: ThemeKey, colors: EmberColors): Brush =
    if (key == ThemeKey.CITRUS) SolidColor(colors.glow) else Brush.linearGradient(listOf(colors.glow, colors.glow2))
