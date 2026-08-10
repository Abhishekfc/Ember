package com.ember.app.ui.auth

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ember.app.R

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
    AuthPhoneFrame(modifier = modifier) { cellSize, gap ->
        val bigCardSize = cellSize * 2 + gap
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            repeat(GRID_ROWS) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(GRID_COLUMNS) { col ->
                        // The big card below overlaps rows 1-2, columns 2-3 (its own
                        // offset/alignment) — skipping those cells here instead of drawing
                        // them underneath is what stops their sharper (10dp) corners from
                        // peeking out past the big card's own, more rounded (22dp) ones.
                        if (row in 1..2 && col in 2..3) {
                            Box(modifier = Modifier.size(cellSize))
                            return@repeat
                        }
                        AuthPhoneAppIcon(cellSize)
                    }
                }
            }
        }

        // Overlaps the grid rather than occupying its own cell — genuinely bigger than a
        // regular icon, not just recolored, which is what makes it read as "the one that
        // matters" instead of one more icon among sixteen. A real sample photo now,
        // not a flat color block — this is meant to read as "a friend's actual photo,
        // live on your home screen," which a solid color can only gesture at.
        AuthPhoneWidget(
            size = bigCardSize,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(y = cellSize + gap),
        )
    }
}

/** One placeholder app icon. Shared so every mockup's background grid is identical rather than
 * each one re-deciding its own corner radius and tone.
 *
 * No per-cell border — sixteen individually bordered boxes were the actual cause of the choppy
 * entrance animation (that many separate border draws every frame during the slide/fade-in adds
 * up), not the animation itself. A flat fill reads clearly enough against the outlined phone
 * shape around it. */
@Composable
internal fun AuthPhoneAppIcon(cellSize: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(cellSize)
            .clip(RoundedCornerShape(10.dp))
            .background(AuthPalette.panel),
    )
}

/** Emigo's own widget as it appears on a home screen — the sample photo, ringed in the accent.
 * Deliberately just the photo: no logo, no heart, no glyph of any kind on top of it. The whole
 * point being made is "your friend's actual photo lives here", and any mark laid over it turns
 * that into an advert for the app instead. */
@Composable
internal fun AuthPhoneWidget(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.onboarding_widget_photo),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(22.dp))
            .border(1.5.dp, AuthPalette.accentFill, RoundedCornerShape(22.dp)),
    )
}

/**
 * The phone silhouette itself — outline, tilt, grid metrics and home indicator — with no opinion
 * about what goes on its screen. Extracted so the widget-setup walkthrough can draw its own
 * illustrations inside the exact same device rather than a second, near-identical phone drawn
 * from separately-chosen numbers that would drift apart the first time either was touched.
 *
 * [content] is handed the grid cell size and gap already derived from whatever width the frame
 * ended up at, and runs in a [BoxScope] so illustrations can align things to the screen's edges.
 */
@Composable
internal fun AuthPhoneFrame(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(cellSize: Dp, gap: Dp) -> Unit,
) {
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
        val cellSize: Dp = (maxWidth - GRID_GAP * (GRID_COLUMNS - 1)) / GRID_COLUMNS

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) { content(cellSize, GRID_GAP) }


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
