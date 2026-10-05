package com.emigo.app.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.emigo.app.R
import com.emigo.app.ui.theme.EmberTheme
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay

/** How long the featured card dwells on one photo before auto-advancing to the next. */
private const val AUTO_ADVANCE_INTERVAL_MS = 4000L

/** Crossfade duration (photo and dot row) when auto-advancing; deliberately slow, unlike a manual
 * swipe's instant dot switch. */
private const val AUTO_ADVANCE_FADE_MS = 900

/** Pages kept composed on either side of the current one (HorizontalPager's
 * beyondViewportPageCount). A composed page's AsyncImage starts its Coil request immediately, so
 * the next photo is already loading mid-swipe. Kept at one: a bulk preload of the whole feed was
 * tried and reverted (see PROJECT_CONTEXT.md) for competing with the feed and memories fetch at
 * cold start, and every extra page is another in-flight request and bitmap in memory. */
private const val FEATURED_CARD_LOOKAHEAD_PAGES = 1

/** The large featured card: one pager across every friend's photos (see [buildHomeCarousel]), so
 * swiping past someone's last photo lands on the next friend's first with no special-casing.
 * Memories is its own tab ([MemoriesTabScreen]). */
@Composable
internal fun FeaturedPhotoCard(
    entries: List<HomeCarouselEntry>,
    pagerState: PagerState,
    isFocused: Boolean,
    isAtDefaultScrollPosition: Boolean,
    // Whether Home is the page on screen; gates the auto-advance timer, which would otherwise keep
    // cycling while the user is on another tab.
    isActive: Boolean,
    onToggleFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val cardShape = RoundedCornerShape(FEATURED_CARD_CORNER_RADIUS)

    // Auto-advances like a Stories card instead of waiting for a swipe. It crossfades rather than
    // slides so an automatic change never reads as someone else swiping your phone. Paused while
    // focused (someone holding a photo to look at it) and while Home is scrolled off its default
    // position, matching onToggleFocus's "only at the top" gate.
    //
    // One long-running loop, NOT a LaunchedEffect keyed on pagerState.currentPage: this effect's own
    // scrollToPage changes that value, so keying on it would cancel and restart this same effect as
    // soon as its advance takes effect, aborting the fade-out (and the outgoingPhotoUrl = null
    // after it) every time. Reading currentPage as a plain value inside the loop detects manual
    // swipes (the page changed while nothing here was awaiting) without self-cancelling.
    var outgoingPhotoUrl by remember { mutableStateOf<String?>(null) }
    val outgoingAlpha = remember { Animatable(0f) }

    // isActive is a key and a guard because otherwise the timer keeps running while Home isn't on
    // screen: swipe to Friends and four seconds later it advances the hidden card through a 900ms
    // crossfade that paints the outgoing photo at full opacity. Returning mid-crossfade showed the
    // previous photo dissolving into the current one, the flicker-on-return bug.
    LaunchedEffect(entries.size, isFocused, isAtDefaultScrollPosition, isActive) {
        if (entries.size <= 1 || isFocused || !isAtDefaultScrollPosition || !isActive) {
            // Never leave a half-finished crossfade behind, or the outgoing photo stays painted
            // over the real one.
            outgoingPhotoUrl = null
            outgoingAlpha.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            val pageBeforeWait = pagerState.currentPage
            delay(AUTO_ADVANCE_INTERVAL_MS)
            // A manual swipe or avatar tap moved on during the wait: restart the countdown from
            // there instead of also advancing.
            if (pagerState.currentPage != pageBeforeWait) continue
            if (pagerState.isScrollInProgress) continue
            outgoingPhotoUrl = entries[pageBeforeWait].photo.photoUrl
            outgoingAlpha.snapTo(1f)
            pagerState.scrollToPage((pageBeforeWait + 1) % entries.size)
            outgoingAlpha.animateTo(0f, tween(AUTO_ADVANCE_FADE_MS, easing = FastOutSlowInEasing))
            outgoingPhotoUrl = null
        }
    }

    // This pager and MainActivity's outer pager are both horizontal and nested. Compose's
    // nested-scroll handoff between two same-axis pagers is unreliable: leftover velocity from a
    // drag here could reach the outer pager mid-gesture and cause the "stops partway, showing two
    // pages" glitch. Rather than tune fling thresholds, this connection consumes all leftover
    // scroll and fling itself (onPost*, so the card's pager still scrolls first). Swiping outside
    // the card still drives the outer pager.
    val cardNestedScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    BoxWithConstraints(
        // NOT fillMaxWidth() before aspectRatio: it pins minWidth to maxWidth, leaving aspectRatio
        // unable to satisfy a bounded max height, so on a short screen the card overflowed its
        // parent. Without it, aspectRatio derives height from width when that fits (every normal
        // phone, same as before) and otherwise derives width from the available height, so the card
        // shrinks proportionally instead of pushing the avatar row out.
        modifier = modifier
            .aspectRatio(FEATURED_CARD_ASPECT_RATIO)
            .nestedScroll(cardNestedScrollBoundary)
            .clip(cardShape)
            // Elevated, not the plain panel tone of rows and chips, so it outranks them. Only
            // visible at the edges or behind a transparent PNG; the photo is the hero.
            .background(colors.elevatedPanel)
            // A plain tap (not a swipe) toggles focus. No ripple; the screen's blur is the feedback.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleFocus,
            ),
    ) {
        // BoxWithConstraints (not a plain Box) so the photo-count segments know the card width and
        // can shrink evenly instead of running past the 22dp side inset the other overlays respect.
        val cardWidth = maxWidth
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = FEATURED_CARD_LOOKAHEAD_PAGES,
        ) { page ->
            val entry = entries[page]
            // Plain AsyncImage paints nothing while loading, leaving the flat elevatedPanel
            // background, which reads as broken rather than loading (a slow, uncached fetch is a
            // real wait, especially the first time a photo shows). Tracking the painter's state
            // lets the card show the same pulsing skeleton used elsewhere in this file
            // (SkeletonFeaturedCard, rememberSkeletonPulse) for as long as the wait lasts.
            val painter = rememberAsyncImagePainter(model = entry.photo.photoUrl)
            // painter.state is a StateFlow, so collectAsState is what makes "request finished"
            // trigger recomposition.
            val painterState by painter.state.collectAsState()
            val isLoading = when (painterState) {
                is AsyncImagePainter.State.Loading, is AsyncImagePainter.State.Empty -> true
                else -> false
            }
            if (isLoading) {
                val pulseAlpha by rememberSkeletonPulse(periodMillis = 1100)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = pulseAlpha }
                        .background(colors.elevatedPanel),
                )
            }
            Image(
                painter = painter,
                contentDescription = entry.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The outgoing photo, held on top and faded out. The pager underneath has already jumped to
        // the next page (no slide), so the visible effect is a crossfade.
        outgoingPhotoUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = outgoingAlpha.value },
            )
        }

        // Bottom scrim keeps the name, time and streak readable on any photo; the padding keeps
        // them clear of the rounded corners.
        run {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
            )

            val current = entries.getOrNull(pagerState.currentPage) ?: entries.first()

            // Null for a friend's newest photo (it never expires, see HomeCarouselEntry). For an
            // older one, this is when its 24-hour grace period ends: 24 hours after the photo that
            // superseded it arrived, not after its own send time. See buildHomeCarousel
            // (indexWithinFriend 0 = newest) for why the successor is indexWithinFriend - 1.
            val expiresAt = remember(entries, current.friendId, current.indexWithinFriend) {
                if (current.isFriendsNewest) {
                    null
                } else {
                    val friendEntries = entries.filter { it.friendId == current.friendId }.sortedBy { it.indexWithinFriend }
                    val successor = friendEntries.getOrNull(current.indexWithinFriend - 1)
                    successor?.let { runCatching { Instant.parse(it.photo.createdAt) }.getOrNull() }
                        ?.plus(24, ChronoUnit.HOURS)
                }
            }

            // Per-friend photo count: how many this friend has and which one you're on, not a
            // position in the whole sequence.
            if (current.totalForFriend > 1) {
                // Looked up explicitly (not assumed contiguous) so each dot can show whether that
                // photo has been seen; doesn't rely on buildHomeCarousel's ordering.
                val friendEntries = remember(entries, current.friendId) {
                    entries.filter { it.friendId == current.friendId }.sortedBy { it.indexWithinFriend }
                }
                // Each dot is 16dp while they all fit. Once they wouldn't fit within the card's 22dp
                // side inset (same margin as the name/streak row), all dots shrink evenly. Never
                // fewer dots or mismatched sizes.
                val dotCount = current.totalForFriend
                val availableWidth = cardWidth - FEATURED_CARD_SIDE_PADDING * 2
                val naturalWidth = FEATURED_CARD_DOT_WIDTH * dotCount + FEATURED_CARD_DOT_SPACING * (dotCount - 1)
                val dotWidth = if (naturalWidth <= availableWidth) {
                    FEATURED_CARD_DOT_WIDTH
                } else {
                    ((availableWidth - FEATURED_CARD_DOT_SPACING * (dotCount - 1)) / dotCount)
                        .coerceAtLeast(FEATURED_CARD_DOT_MIN_WIDTH)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(FEATURED_CARD_DOT_SPACING),
                ) {
                    repeat(dotCount) { index ->
                        // A photo you've swiped past is seen the instant you land on the next one; it
                        // doesn't wait for photo.seen, which flips only after the (deliberately
                        // lagged) markPhotoSeen call completes. Without this position override, the
                        // dot you just left showed its old unseen color for a visible flash. Dots at
                        // or ahead of your position still use the real seen flag.
                        val isUnseen = index >= current.indexWithinFriend &&
                            friendEntries.getOrNull(index)?.photo?.seen == false
                        val targetDotColor = when {
                            index == current.indexWithinFriend -> Color.White
                            isUnseen -> colors.glow
                            else -> Color.White.copy(alpha = 0.35f)
                        }
                        // Instant (0ms) for a manual swipe: an animated crossfade was tried and rejected
                        // because the previous dot kept its old unseen color during the fade, which
                        // read as lag; the dot for the photo on screen must never be stale.
                        // Auto-advance differs: outgoingPhotoUrl is non-null exactly while its
                        // crossfade runs, so the dot row fades in step with the photo instead of
                        // snapping ahead.
                        val dotColor by animateColorAsState(
                            targetValue = targetDotColor,
                            animationSpec = tween(if (outgoingPhotoUrl != null) AUTO_ADVANCE_FADE_MS else 0),
                            label = "featuredCardDotColor",
                        )
                        Box(
                            modifier = Modifier
                                .width(dotWidth)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(dotColor),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    // A photo in its 24-hour grace period shows a countdown instead of relative time:
                    // one about to disappear should say so.
                    if (expiresAt != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_clock_fading),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = formatRemainingTime(expiresAt),
                                fontFamily = typography.body,
                                fontSize = 12.5.sp,
                                color = Color.White.copy(alpha = 0.75f),
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    } else {
                        Text(
                            text = formatRelativeTime(current.photo.createdAt),
                            fontFamily = typography.body,
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (current.streak >= 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            contentDescription = stringResource(R.string.friends_streak_description),
                            tint = colors.glow,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "${current.streak}",
                            fontFamily = typography.body,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        }
    }
}
