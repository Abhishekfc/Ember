package com.ember.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The one blur radius/timing every "something is focused, everything else recedes" moment on
 * Home and Memories animates to — Home's own card focus, Memories' day-card focus, and the
 * Memories grid's own internal blur are all the same visual language applied to different
 * content, so they share one definition instead of each site hardcoding its own copy that could
 * quietly drift out of sync with the others. */
private val FOCUS_BLUR_RADIUS = 16.dp
private const val FOCUS_BLUR_DURATION_MS = 260

@Composable
fun rememberFocusBlur(active: Boolean): State<Dp> = animateDpAsState(
    targetValue = if (active) FOCUS_BLUR_RADIUS else 0.dp,
    animationSpec = tween(FOCUS_BLUR_DURATION_MS, easing = FastOutSlowInEasing),
    label = "focusBlur",
)

/** Companion to [rememberFocusBlur] for the same "something is focused, everything else recedes"
 * moments, now that Home also has [AmbientPhotoBackdrop] sitting behind that receded chrome —
 * against a busy blurred-photo backdrop, a light blur alone still left header text and avatar
 * shapes faintly legible (see AmbientPhotoBackdrop's own doc comment), which read as a bug, not
 * as "receded." Fading the same chrome fully to invisible alongside its existing blur is what
 * actually reads as "gone" rather than "smudged." */
@Composable
fun rememberFocusFade(active: Boolean): State<Float> = animateFloatAsState(
    targetValue = if (active) 0f else 1f,
    animationSpec = tween(FOCUS_BLUR_DURATION_MS, easing = FastOutSlowInEasing),
    label = "focusFade",
)

/** The one "featured card" recipe both Home's own [FeaturedPhotoCard] and Memories' own
 * `DayFeaturedOverlay` grow into — same proportions and corner radius so opening a memory reads
 * as the same kind of thing as Home's own featured photo, not a lookalike built from separately
 * hand-copied numbers that happen to currently match. [FEATURED_CARD_SIDE_PADDING] is also what
 * `DayFeaturedOverlay` reserves on either side when it centers itself against the full screen. */
internal const val FEATURED_CARD_ASPECT_RATIO = 0.8f
internal val FEATURED_CARD_CORNER_RADIUS = 30.dp
internal val FEATURED_CARD_SIDE_PADDING = 18.dp

/** The per-friend photo-count segments along the top of the featured card — sized at
 * [FEATURED_CARD_DOT_WIDTH] each as long as they all fit within the card's own
 * [FEATURED_CARD_SIDE_PADDING] inset; once there are enough photos that they wouldn't, they
 * shrink evenly (never below [FEATURED_CARD_DOT_MIN_WIDTH]) rather than running past the card's
 * rounded edges. */
internal val FEATURED_CARD_DOT_WIDTH = 16.dp
internal val FEATURED_CARD_DOT_SPACING = 4.dp
internal val FEATURED_CARD_DOT_MIN_WIDTH = 3.dp

/** The fixed gap above the featured card's own top edge. Deliberately a plain constant rather
 * than a share of whatever vertical space happens to be left over: a leftover-derived gap (two
 * `weight(1f)` spacers around the card, which is what this replaced) is large on a tall phone and
 * collapses to nothing on a short one, so the same screen reads differently per device. Fixed
 * here, the gap is identical everywhere and the *card* absorbs the difference instead — see the
 * card's own `weight(1f, fill = false)` at its call site in HomeScreen. */
internal val FEATURED_CARD_TOP_GAP = 28.dp

/** The gaps around the friend avatar row beneath the card, kept as plain constants for the same
 * reason as [FEATURED_CARD_TOP_GAP] — every fixed element in that fold keeps its exact spacing on
 * every device, and only the card flexes. */
internal val AVATAR_ROW_TOP_GAP = 34.dp
internal val AVATAR_ROW_BOTTOM_GAP = 12.dp
