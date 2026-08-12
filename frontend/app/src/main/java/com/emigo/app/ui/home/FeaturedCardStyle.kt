package com.emigo.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
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
/** The inset used *within* the card — currently just the photo-count dot row, which has to stay
 * clear of the rounded corners. A fixed dp because it's measured against the card's own edges, not
 * the screen's; the card's position on screen is [featuredCardSidePadding] instead. */
internal val FEATURED_CARD_SIDE_PADDING = 18.dp

/**
 * How much of the screen's width sits either side of the featured card, as a fraction rather than
 * a fixed dp.
 *
 * A fixed dp inset makes the card a slightly different physical size depending on the device's
 * density: 18dp is 54px at 480dpi but only 46px at 408dpi, so the same phone rendered a card ~1.5%
 * wider after a Display size change. A fraction is invariant — 5% of the screen is the same number
 * of real pixels no matter what density that screen reports — so the card comes out at exactly the
 * same physical dimensions everywhere, which is the whole point of it being the screen's one hero
 * element. 0.05 is the value that reproduces the original 18dp on a 360dp-wide device, so nothing
 * moves on the most common screen.
 */
internal const val FEATURED_CARD_SIDE_INSET_FRACTION = 0.05f

/** [FEATURED_CARD_SIDE_INSET_FRACTION] resolved against a known width — for callers that already
 * have the screen width in hand (or are not composable). */
internal fun featuredCardSidePaddingFor(screenWidth: Dp): Dp = screenWidth * FEATURED_CARD_SIDE_INSET_FRACTION

/** [FEATURED_CARD_SIDE_INSET_FRACTION] resolved against the current window. Every place that
 * positions a featured card — Home, Camera, Memories' day overlay, the loading skeleton — must use
 * this same value, or those cards stop landing in the same place as each other. */
@Composable
internal fun featuredCardSidePadding(): Dp =
    featuredCardSidePaddingFor(LocalConfiguration.current.screenWidthDp.dp)

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
internal val AVATAR_ROW_TOP_GAP = 20.dp
internal val AVATAR_ROW_BOTTOM_GAP = 12.dp

/** Roughly how much taller one avatar column is than the circle itself — the name label beneath it
 * plus its own top padding. Only ever used to *predict* the fold's height requirement in
 * [homeFoldMetricsFor] before anything is laid out, never to size the row, so being a few dp out
 * only nudges where the two scales change over, never what either one renders as. */
private val AVATAR_LABEL_BLOCK = 24.dp

/**
 * The spacings and avatar sizes for Home's card + avatar fold, as one set so the two scales can't
 * drift apart in pieces.
 *
 * Two of these exist ([HomeFoldRoomy], [HomeFoldCompact]) because a fixed set cannot be right
 * everywhere: the featured card holds a fixed [FEATURED_CARD_ASPECT_RATIO], so when the fold is
 * too short for a full-width card the card derives its width from the available *height* instead
 * and renders narrower — which shows up as dead space down both sides, worst on exactly the
 * devices with the least room to spare. Tightening the gaps and avatars buys back the height the
 * card needs to stay full width.
 */
internal data class HomeFoldMetrics(
    val toggleTopGap: Dp,
    val pillWidth: Dp,
    val pillVerticalPadding: Dp,
    val cardTopGap: Dp,
    val avatarRowTopGap: Dp,
    val avatarRowBottomGap: Dp,
    val avatarDiameter: Dp,
    val avatarInactiveDiameter: Dp,
    val avatarItemWidth: Dp,
)

/** The original scale, used wherever there's genuinely room for it. */
internal val HomeFoldRoomy = HomeFoldMetrics(
    toggleTopGap = 20.dp,
    pillWidth = 118.dp,
    pillVerticalPadding = 11.dp,
    cardTopGap = FEATURED_CARD_TOP_GAP,
    avatarRowTopGap = AVATAR_ROW_TOP_GAP,
    avatarRowBottomGap = AVATAR_ROW_BOTTOM_GAP,
    avatarDiameter = 90.dp,
    avatarInactiveDiameter = 84.dp,
    avatarItemWidth = 96.dp,
)

/** The tighter scale for folds that can't fit a full-width card at [HomeFoldRoomy]. Every value is
 * reduced proportionally rather than one being crushed, so the fold reads as the same design at a
 * smaller scale instead of a different, more cramped one. */
internal val HomeFoldCompact = HomeFoldMetrics(
    toggleTopGap = 12.dp,
    pillWidth = 104.dp,
    pillVerticalPadding = 8.dp,
    cardTopGap = 16.dp,
    avatarRowTopGap = 14.dp,
    avatarRowBottomGap = 10.dp,
    avatarDiameter = 74.dp,
    avatarInactiveDiameter = 69.dp,
    avatarItemWidth = 80.dp,
)

/** The Home/Moments toggle row's whole vertical footprint at [HomeFoldRoomy] — its top gap plus a
 * pill's own height. Like [AVATAR_LABEL_BLOCK] this only ever *predicts* the requirement, and it
 * is deliberately the roomy figure regardless of which scale ends up being used: see
 * [homeFoldMetricsFor] for why that constant-ness is what keeps the choice stable. */
private val TOGGLE_ROW_BLOCK_ROOMY = 62.dp

/**
 * Picks the scale by asking the only question that actually matters: *does a full-width card fit
 * here?* — rather than testing the screen against a hardcoded size threshold.
 *
 * A threshold would have to be guessed, would sit arbitrarily close to real devices, and would
 * still be wrong for the cases that caused this in the first place — the same phone reports
 * different space depending on the user's Display size setting, and 3-button navigation costs
 * ~24dp more than gesture navigation. Deriving it from the real available height covers all of
 * that with no device list to maintain.
 *
 * [availableHeight] must be the screen minus only the chrome this function has no influence over —
 * status bar, the header, and the nav dock — and specifically **not** minus the measured toggle
 * row, even though that row sits inside the same space. That row's height depends on the pill size
 * this returns, so feeding its measurement back in would make the input depend on the output: the
 * roomy scale produces a taller row, which shrinks the input, which selects the compact scale,
 * which produces a shorter row, which selects roomy again — a layout that never settles. Instead
 * the row is accounted for on the requirement side at its fixed [TOGGLE_ROW_BLOCK_ROOMY] size, so
 * the comparison is between two quantities that this function cannot move.
 *
 * [screenWidth] is the full width the card would span.
 */
internal fun homeFoldMetricsFor(availableHeight: Dp, screenWidth: Dp): HomeFoldMetrics {
    val fullWidthCardHeight = (screenWidth - featuredCardSidePaddingFor(screenWidth) * 2) / FEATURED_CARD_ASPECT_RATIO
    val roomyRequirement = with(HomeFoldRoomy) {
        TOGGLE_ROW_BLOCK_ROOMY + cardTopGap + fullWidthCardHeight + avatarRowTopGap +
            avatarDiameter + AVATAR_LABEL_BLOCK + avatarRowBottomGap
    }
    return if (availableHeight >= roomyRequirement) HomeFoldRoomy else HomeFoldCompact
}

/** Home's current fold scale, so the avatar row can read it without every composable between here
 * and it having to forward a parameter — the same reason [com.emigo.app.ui.components.LocalNavDockHeight]
 * exists. Defaults to [HomeFoldRoomy] so anything composed outside Home's own provider (previews,
 * the Camera screen's height twins) keeps the original sizing. */
internal val LocalHomeFoldMetrics = compositionLocalOf { HomeFoldRoomy }
