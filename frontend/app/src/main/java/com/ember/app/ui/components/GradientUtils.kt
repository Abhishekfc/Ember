package com.ember.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
