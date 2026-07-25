package com.ember.app.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val GRID_COLUMNS = 4
private const val GRID_ROWS = 4
private val GRID_GAP = 10.dp

/** A plain, abstracted phone-home-screen silhouette — a grid of small app-icon placeholders with
 * one bigger card overlapping the top-right of the grid, standing in for "a friend's photo,
 * live on your home screen" — the same structural idea other apps in this space use for their
 * own first-impression screen, but drawn flat in Ember's own cream-on-panel language instead of
 * a literal illustrated device with real icons: no photography, no per-app branding, no ambient
 * glow, solid color blocks only. The big card is genuinely bigger than a grid cell (not just a
 * same-size cell in a different color) — that size difference, plus its cream ring, is what
 * reads as "this one is the point, everything else is just the rest of your apps." */
@Composable
fun PhoneHomeMockup(modifier: Modifier = Modifier) {
    val colors = AuthPalette
    val outlineShape = RoundedCornerShape(40.dp)

    // The outline's own fade-in, independent of whatever entrance animation the caller wraps
    // this in (slide, scale, ...) — tying the border's color to a transform on a rotated shape
    // was what made it look like it was "growing in" from one corner instead of just appearing.
    // This animates on its own timeline and only ever touches color/alpha, never position.
    var borderVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { borderVisible = true }
    val borderAlpha by animateFloatAsState(
        targetValue = if (borderVisible) 1f else 0f,
        animationSpec = tween(450, easing = FastOutSlowInEasing),
        label = "mockupBorderAlpha",
    )

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(0.5f)
            // A slight tilt — like a photo resting on a table, not a perfectly axis-aligned
            // diagram — is what keeps this feeling like a candid object instead of a spec sheet.
            .graphicsLayer { rotationZ = -4f }
            .clip(outlineShape)
            // colors.border is a very faint (8%-opacity) hairline elsewhere in the app — too
            // faint to read as a bold outline at any width, hence colors.muted instead.
            .border(5.dp, colors.muted.copy(alpha = borderAlpha), outlineShape)
            .padding(18.dp),
    ) {
        val gridWidth = maxWidth
        val cellSize: Dp = (gridWidth - GRID_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val bigCardSize = cellSize * 2 + GRID_GAP

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                    repeat(GRID_ROWS) {
                        Row(horizontalArrangement = Arrangement.spacedBy(GRID_GAP)) {
                            repeat(GRID_COLUMNS) {
                                // No per-cell border — sixteen individually bordered boxes were
                                // the actual cause of the choppy entrance animation (that many
                                // separate border draws every frame during the slide/fade-in adds
                                // up), not the animation itself. A flat fill reads clearly enough
                                // against the outlined phone shape around it.
                                Box(
                                    modifier = Modifier
                                        .size(cellSize)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.panel),
                                )
                            }
                        }
                    }
                }

                // Overlaps the grid rather than occupying its own cell — genuinely bigger than a
                // regular icon, not just recolored, which is what makes it read as "the one that
                // matters" instead of one more icon among sixteen.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = cellSize + GRID_GAP)
                        .size(bigCardSize)
                        .clip(RoundedCornerShape(22.dp))
                        .background(colors.accentFill)
                        .border(4.dp, colors.accentFill, RoundedCornerShape(22.dp)),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(90.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.mutedDim.copy(alpha = 0.5f)),
            )
        }
    }
}
