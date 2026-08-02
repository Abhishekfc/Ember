package com.ember.app.ui.home

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.PhotoEntryDto
import com.ember.app.ui.components.LocalNavDockHeight
import com.ember.app.ui.components.PULL_REFRESH_CONTENT_OFFSET_DP
import com.ember.app.ui.theme.CourgetteFontFamily
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far the user needs to scroll before the nav dock's Home icon has fully morphed into the
 * Memories one. Lives here (`internal`, not `private`) rather than in MainActivity, since it's
 * really describing this screen's own Memories section, not the dock itself. */
internal const val MEMORIES_REVEAL_SCROLL_DP = 220

/** How far into the top-fold/Memories dead zone (as a fraction of its own total size) a scroll
 * has to land before it commits forward to Memories, rather than snapping back to the top — see
 * the LaunchedEffect using this in HomeScreen for the full dead-zone explanation. Small on
 * purpose: a light scroll should be enough to reveal Memories, not a scroll that covers most of
 * the gap. Raise this (toward 1f) to require a more deliberate scroll instead. */
private const val MEMORIES_SNAP_TRIGGER_FRACTION = 0.15f

/** How long the very last photo in the whole carousel dwells before it's marked seen on its
 * own — see the LaunchedEffect using this in HomeScreen's carousel branch for why this one
 * specific page needs a fallback the rest of the carousel doesn't. */
private const val LAST_PHOTO_DWELL_MARK_SEEN_MS = 3000L


/** Where the page rests, as a fraction of [PULL_REFRESH_CONTENT_OFFSET_DP], while a pull-triggered
 * refresh is actually running — the fixed destination the release settle eases toward (see its
 * call site for why a fixed value, rather than the live pull distance, is what makes that easing
 * take effect at all). 1f leaves the whole offset open, so the spinner stays fully visible. */
private const val PULL_REFRESH_RESTING_FRACTION = 1f

/** Home's own page — greeting header, then whichever of loading / error / empty / the featured
 * carousel applies, with the Memories grid embedded further down the same scroll (below the
 * avatar row when there's a feed, below the empty-state message when there isn't) rather than a
 * swipe away on its own page. [isPhotoFocused]/[onToggleFocus]/[onDismissFocus] are hoisted all
 * the way up to MainActivity now, not owned here — the shared nav dock (also hoisted there, since
 * Home, Friends, Camera, Activity and Settings are all pages of one outer swipeable pager) needs
 * to blur in step with this screen's own tap-to-focus, and it sits outside whatever any individual
 * page is doing internally. [scrollState] is hoisted the same way, for the same reason: the nav
 * dock's Home icon morphs based on how far into this screen's Memories section the user has
 * scrolled (see MainActivity's homeIconProgress), and the dock can't read a ScrollState this
 * composable owned privately. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCameraClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    onProfileClick: () -> Unit,
    hazeState: HazeState,
    isPhotoFocused: Boolean,
    onToggleFocus: () -> Unit,
    onDismissFocus: () -> Unit,
    scrollState: ScrollState,
    // True whenever Home is the outer pager's settled page — i.e. this is a fresh arrival at
    // Home, not just still being composed while some other page is active. See
    // HomeViewModel.onHomeSessionStart for why this specifically is what's allowed to reveal
    // background-synced content into the visible feed.
    isActive: Boolean = true,
    // Reports headerHeightPx (below) up to MainActivity every time it's actually measured, so
    // CameraScreen's own header can size its own spacer against Home's *real* header height
    // instead of a separate guessed constant — see CameraScreen's own call site for why that
    // guess kept drifting out of sync with this screen's real layout.
    onHeaderHeightChanged: (Float) -> Unit = {},
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }

    // The header's own real height — measured, not guessed — so the featured card's bounded
    // region below (see its own call site comment) knows exactly how much room is actually left,
    // regardless of how long the greeting line runs or what font scale is in effect. Starts at 0f
    // for one frame before the first real measurement lands, same trade-off screenSize above
    // already makes.
    var headerHeightPx by remember { mutableStateOf(0f) }

    // Real window-space Y positions of the scrollable column itself and of wherever the Memories
    // section currently starts — used only to compute memoriesTopOffsetPx below (see the
    // LaunchedEffect that reads it, further down), which is how far the page needs to scroll for
    // Memories to sit flush with the top of the screen. Both live in window space (not "position
    // within the column") specifically so the math stays correct without depending on exactly how
    // Modifier.verticalScroll positions its content internally.
    var scrollColumnWindowY by remember { mutableStateOf(0f) }
    var memoriesTopWindowY by remember { mutableStateOf(0f) }

    // HomeBrandHeader's own real height — just that row, not the combined block headerHeightPx
    // above tracks — used only to park the pull-to-refresh spinner directly beneath it (see the
    // indicator's own modifier further down).
    var brandHeaderHeightPx by remember { mutableStateOf(0f) }

    // Whichever photo the featured card is currently showing — hoisted up here (rather than kept
    // local to the carousel branch below) so AmbientPhotoBackdrop, rendered from this composable's
    // own outer Box, can read it too. Only ever set while there's a real feed (see the carousel
    // branch's own LaunchedEffect); staying at whatever it last was when the feed is empty is
    // harmless since the backdrop itself is invisible whenever nothing is focused anyway.
    var currentPhotoUrl by remember { mutableStateOf<String?>(null) }

    // Leaving Home and coming back is one of the two moments background-synced content is
    // allowed to become visible (see HomeViewModel) — the other being an explicit
    // pull-to-refresh, already handled inside loadFeed itself. A no-op whenever nothing has
    // actually diverged, and harmless on the very first composition (synced == session already).
    LaunchedEffect(isActive) {
        if (isActive) viewModel.onHomeSessionStart()
    }

    // 0 = Memories grid fully invisible (top of Home), 1 = fully visible — only the grid itself
    // fades with this, never the "Memories" heading above it, which stays a plain always-visible
    // part of the page. A lambda, not a plain val: scrollState.value changes every frame of a
    // scroll, and reading it directly here (rather than lazily inside the graphicsLayer that
    // actually uses it) would force this whole composable to recompose on every one of those
    // frames instead of just cheaply redrawing.
    val memoriesRevealScrollPx = with(LocalDensity.current) { MEMORIES_REVEAL_SCROLL_DP.dp.toPx() }
    val memoriesRevealProgress: () -> Float = { (scrollState.value / memoriesRevealScrollPx).coerceIn(0f, 1f) }

    // derivedStateOf, not a plain `scrollState.value == 0` read — scrollState.value changes every
    // frame of a scroll, but this derived boolean only actually changes twice (crossing 0 in
    // either direction), so whatever reads it only recomposes on those two occasions instead of
    // every scrolled pixel. Same reasoning as memoriesRevealProgress being a lambda above.
    val isHomeAtDefaultScrollPosition by remember { derivedStateOf { scrollState.value == 0 } }

    // Where the current drag/fling started — captured fresh every time one begins (the
    // isScrollInProgress == true branch below), so the correction after it ends can judge "how
    // far did this specific gesture actually move" rather than "how far is the final position
    // from zero." That distinction is what a fixed distance-from-zero threshold got backwards:
    // scrolling up just a little from deep inside Memories still leaves the *position* well past
    // a from-zero threshold, so it kept reading as "commit forward" and pulling back into
    // Memories — the "have to scroll up a lot" this fixes. Measuring from the drag's own start
    // makes a small movement in *either* direction equally easy to commit, symmetrically.
    var memoriesDragStartPx by remember { mutableStateOf(0f) }
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            memoriesDragStartPx = scrollState.value.toFloat()
            return@LaunchedEffect
        }
        val memoriesTopOffsetPx = scrollState.value + (memoriesTopWindowY - scrollColumnWindowY)
        val current = scrollState.value.toFloat()
        // maxValue itself, not just "less than memoriesTopOffsetPx" — resting anywhere at or past
        // the true bottom of the page must never be touched by this, full stop, regardless of
        // what memoriesTopOffsetPx happens to compute to. That's the one guarantee this needs;
        // the dead-zone correction below only matters well before the bottom is ever reached.
        val atOrPastBottom = current >= scrollState.maxValue.toFloat()
        if (!atOrPastBottom && current > 0f && current < memoriesTopOffsetPx && memoriesTopOffsetPx > 0f) {
            val movedFraction = (current - memoriesDragStartPx) / memoriesTopOffsetPx
            val target = when {
                movedFraction > MEMORIES_SNAP_TRIGGER_FRACTION -> memoriesTopOffsetPx
                movedFraction < -MEMORIES_SNAP_TRIGGER_FRACTION -> 0f
                // Didn't move enough either way to count as deliberate — settle back to whichever
                // side this specific drag actually started from, not just "the nearer one," so a
                // gesture that barely moves reads as "nothing happened" rather than a random pick.
                else -> if (memoriesDragStartPx > memoriesTopOffsetPx / 2f) memoriesTopOffsetPx else 0f
            }
            // A plain ease-out tween, not the default spring animateScrollTo otherwise uses — a
            // spring's overshoot/settle reads as bouncy for a correction like this; easing
            // straight into rest (fast at first, slowing as it arrives) is what actually looks
            // like a smooth flick rather than a snap.
            scrollState.animateScrollTo(
                target.roundToInt().coerceIn(0, scrollState.maxValue),
                animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            )
        }
    }

    // True while Memories' own day-photo viewer (DayFeaturedOverlay, rendered below at this
    // screen's own top-level Box) is open — wired via onFocusChanged below so it can blur this
    // screen's own header/avatar row in lockstep, the same way isPhotoFocused already does for
    // Home's own featured card.
    var isMemoriesFocused by remember { mutableStateOf(false) }

    // Hoisted up from MemoriesGridContent so DayFeaturedOverlay can be rendered from THIS
    // composable's own outer Box (fillMaxSize of the true screen) instead of from inside Home's
    // scrollable Column, where a "centered against the full screen" overlay was positioning
    // itself against a container that's never actually bounded to the real screen size (see
    // DayFocusState's own doc comment).
    val dayFocusState = rememberDayFocusState()

    // Local copy of the same animation, driven by the hoisted boolean — stays in lockstep with
    // MainActivity's own chromeBlur (which blurs the shared nav dock) since both react to the
    // exact same state transition in the same recomposition, just kept as two instances so this
    // screen doesn't need the animated Dp threaded in as its own parameter.
    val chromeBlur by rememberFocusBlur(isPhotoFocused || isMemoriesFocused)
    // Fully hides the same chrome chromeBlur recedes — see rememberFocusFade's own doc comment
    // for why blur alone isn't enough once AmbientPhotoBackdrop is in the picture too.
    val chromeFade by rememberFocusFade(isPhotoFocused || isMemoriesFocused)

    // Home's own featured card must stay sharp while it's the thing actually in focus, but
    // should recede like everything else once Memories' day-card is what's focused instead —
    // otherwise it sat there perfectly crisp, sandwiched between an already-blurred header above
    // and an already-blurred avatar row below it.
    val cardBlurWhenMemoriesFocused by rememberFocusBlur(isMemoriesFocused)
    // Companion fade — without this, whatever part of Home's own card is scrolled into view
    // behind an open Memories day-card stayed a solid (merely blurred, never hidden) rectangle,
    // the exact same bleed-through chromeFade already fixes for the header/avatar row/grid.
    val cardFadeWhenMemoriesFocused by rememberFocusFade(isMemoriesFocused)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
    // Sits behind everything else in this Box, only actually visible once a photo is tapped —
    // either Home's own featured card, or a Memories day-card (which reports its own settled
    // photo up via DayFeaturedOverlay's onCurrentPhotoChanged below) — replacing the flat
    // background above with a wash matching whatever's currently focused.
    AmbientPhotoBackdrop(
        photoUrl = currentPhotoUrl,
        visible = isPhotoFocused || isMemoriesFocused,
        modifier = Modifier.fillMaxSize(),
    )

    // hazeSource is scoped to this Column only (header + content) — it must never wrap
    // the shared BottomNavDock (now hoisted up to MainActivity, outside every page), or the
    // blur source would include the dock's own pixels and produce a ghosting artifact.
    // verticalScroll is what lets the pull-to-refresh gesture register even though the
    // content itself fits on one screen.
    val pullRefreshState = rememberPullToRefreshState()
    // Material3's own PullToRefreshBox only moves the indicator as you pull — the content behind
    // it stays put, which read as broken ("nothing happens, just an icon appears") compared to
    // the tactile feel most apps' own pull-to-refresh has, where the whole page visibly shifts
    // down with your finger. Driving that shift by hand here — as a plain computed value, not
    // wrapped in its own animateFloatAsState. pullRefreshState.distanceFraction is already
    // smoothly animated by Material3 itself through every phase of the gesture (drag, release,
    // held open while isRefreshing is true, then settling back to 0) — layering a second,
    // independent animation on top of that fought with it instead of following it: on release,
    // this state's own value would already be easing toward its next target while a separate
    // tween chased a different target of its own, producing the "dips further down, then jumps
    // back up" stutter. A plain per-frame formula has nothing of its own to fight with; it just
    // rides Material3's existing animation directly.
    //
    // Deliberately a plain linear multiple of distanceFraction — no curve, no clamp, no easing.
    // A resistance curve was tried here and is what caused the "settles down, then jerks back up"
    // motion on release: any curve makes the offset at a deep pull (distanceFraction > 1) larger
    // than the offset at the settled refreshing position (distanceFraction == 1), so letting go
    // after a long pull always moved the page back UP before the refresh finished. Straight
    // proportionality means the position your finger left it at IS the resting position, so
    // release is a single continuous settle with nothing to bounce back from.
    //
    // The one place an animation of our own is warranted is the settle that happens when you let
    // go while a refresh is already running: Material3 pulls the page back up to its held-open
    // position at a flat, constant speed, which reads as an abrupt snap. A decelerating tween
    // over that phase only (LinearOutSlowInEasing — fast at first, easing out as it arrives) is
    // what makes it land softly instead. Every other phase uses snap(), i.e. no animation at all:
    // while dragging, the offset must track the finger exactly (anything else feels laggy), and
    // once the refresh finishes Material3 is already animating distanceFraction smoothly back to
    // zero on its own, so following it verbatim is both smoother and simpler than re-animating a
    // value that's already animated.
    // The target during that settle is a fixed constant, NOT distanceFraction. Material3 is
    // already animating distanceFraction itself down toward its resting value the moment you let
    // go — so an eased tween pointed at that value is chasing a target that moves every frame,
    // which just makes it track Material3's own timing and leaves the easing with nothing to
    // slow down (the reason a tween here appeared to change nothing at all). Aiming at a fixed
    // number instead gives the tween a stationary destination, so its full duration and
    // deceleration actually apply.
    val pullOffsetFraction by animateFloatAsState(
        targetValue = if (viewModel.isPullRefreshing) {
            PULL_REFRESH_RESTING_FRACTION
        } else {
            pullRefreshState.distanceFraction.coerceAtLeast(0f)
        },
        animationSpec = if (viewModel.isPullRefreshing) {
            tween(durationMillis = 420, easing = LinearOutSlowInEasing)
        } else {
            snap()
        },
        label = "pullOffsetFraction",
    )
    PullToRefreshBox(
        // Tracks isPullRefreshing specifically, not the general isLoading — loadFeed() also
        // runs silently in the background (e.g. right after sending a photo, so streaks and
        // the feed stay current), and that shouldn't pop this spinner in since the user never
        // actually pulled down for it.
        isRefreshing = viewModel.isPullRefreshing,
        onRefresh = { viewModel.loadFeed(isPullRefresh = true) },
        state = pullRefreshState,
        // A plain white spinner, not Material's stock indicator (an arrow that morphs into a
        // filled, tinted-container arc as you pull) — that stock shape doesn't read as part of
        // this app, and a color tint on it looked wrong against a plain empty background rather
        // than a filled container.
        indicator = {
            if (pullOffsetFraction > 0f) {
                // Parked just under HomeBrandHeader's own real measured height, not a flat guess
                // — that row is the one thing that doesn't shift down with the rest of the
                // content above, so this is where the gap it reveals actually opens up. The
                // status bar inset has to be added in separately here: this indicator is a direct
                // sibling of the content inside PullToRefreshBox's own Box, positioned relative
                // to that Box's true top edge (the screen's own top), while HomeBrandHeader lives
                // inside the content Column's statusBarsPadding() further down — without also
                // accounting for that inset here, the indicator landed a whole status-bar-height
                // too high, overlapping the header instead of sitting below it.
                val density = LocalDensity.current
                val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
                val brandHeaderHeightDp = with(density) { brandHeaderHeightPx.toDp() }
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusBarDp + brandHeaderHeightDp + 14.dp)
                        .size(26.dp)
                        .graphicsLayer { alpha = pullOffsetFraction.coerceIn(0f, 1f) },
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { scrollColumnWindowY = it.positionInWindow().y }
                .hazeSource(hazeState)
                .statusBarsPadding()
                // Disabled while either kind of focus is active — background content the user
                // can't currently interact with anyway (everything but the focused card is
                // behind FocusShield, or blurred behind Memories' own day-card) never needed to
                // keep scrolling underneath it either, and letting it scroll is what let a swipe
                // that ran out of photos in Memories' viewer leak into scrolling the grid behind it.
                .verticalScroll(scrollState, enabled = !isPhotoFocused && !isMemoriesFocused),
        ) {
            // Header + greeting blur as one contiguous block instead of three separately
            // blurred pieces — blurring each element on its own left visible hard-edged
            // rectangles floating over crisp background between them, which read as broken
            // rather than as one soft, de-emphasized backdrop.
            FocusShield(active = isPhotoFocused, onDismiss = onDismissFocus) {
            Column(
                modifier = Modifier
                    .onGloballyPositioned {
                        headerHeightPx = it.size.height.toFloat()
                        onHeaderHeightChanged(headerHeightPx)
                    }
                    .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = chromeFade },
            ) {
                // Deliberately NOT part of the translationY shift below — this row is the one
                // thing on the page that stays exactly where normal scrolling has already put it
                // while pulling to refresh, the same way it stays in place through a normal
                // scroll too (this Column isn't fixed; scrolling down to Memories carries it away
                // like everything else, it's specifically the *pull* gesture this is exempt from).
                HomeBrandHeader(
                    userName = viewModel.userName,
                    profilePhotoUrl = viewModel.profilePhotoUrl,
                    onProfileClick = onProfileClick,
                    modifier = Modifier.onGloballyPositioned { brandHeaderHeightPx = it.size.height.toFloat() },
                )

                Column(
                    // The visible "page moves down with your pull" shift, scoped to everything
                    // below the brand row above (title, subtitle, and — see the when{} block's
                    // own wrapper further down — the featured card and everything after it) so
                    // the reload spinner appears to emerge from underneath a brand row that
                    // itself stays put, rather than the whole header sliding down together with
                    // the spinner.
                    modifier = Modifier.graphicsLayer { translationY = pullOffsetFraction * PULL_REFRESH_CONTENT_OFFSET_DP.dp.toPx() },
                ) {
                    // The opening line is a live status, not a greeting — it says something true
                    // about the app's actual state right now (who's waiting to be seen) instead of
                    // the generic "Good evening, Name" every dashboard app defaults to. The personal
                    // touch moves to a small subline instead of carrying the whole header.
                    //
                    // Hierarchy, not just placement, is why hasConnectionError takes over this exact
                    // slot rather than adding a third line below it: "You're all caught up" is a
                    // freshness claim this app can no longer back up once a sync has actually failed
                    // — showing it right next to a connection warning read as the app contradicting
                    // itself in the same breath. Whichever fact is currently true gets the hero
                    // treatment; the other one doesn't get a smaller, hedged mention alongside it.
                    //
                    // "You're all caught up" is de-emphasized by fading it to partial opacity —
                    // deliberately the *only* thing that differs from the other two states (same
                    // font, size, weight, color, tracking). Font size, weight and color were all
                    // tried here first and each changed this Text's own measured height or made it
                    // read as a mismatched element; opacity is a paint-time effect that can't touch
                    // layout at all, so the featured card below never shifts position when this
                    // line's content changes — which a 19sp/25sp size swap previously caused,
                    // reading as a laggy jump the instant the last unseen photo got viewed.
                    // animateFloatAsState is what makes that fade itself smooth rather than an
                    // instant snap between the two opacities.
                    val unseenCount = viewModel.feedItems.count { viewModel.hasUnseenPhoto(it) }
                    val hasConnectionError = viewModel.errorMessage != null
                    val isCaughtUp = !hasConnectionError && unseenCount == 0
                    val headlineAlpha by animateFloatAsState(
                        targetValue = if (isCaughtUp) 0.6f else 1f,
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        label = "headlineAlpha",
                    )
                    Text(
                        text = buildAnnotatedString {
                            if (hasConnectionError) {
                                append("Couldn't connect")
                            } else if (unseenCount > 0) {
                                // The count is highlighted with the theme's own glow accent; the
                                // rest of the sentence stays plain, full-strength cream — not
                                // muted/dimmed. Muting "photo is glowing for you" was tried first
                                // and made the actual exciting part of the message read as dull
                                // grey text; a real, warm accent on the number itself is what a
                                // count actually deserves anyway. In the default Ember theme glow
                                // happens to equal cream, so the number just reads as plain
                                // cream there too — never worse than before, and a real highlight
                                // in every theme where the two tokens differ (Ember New, etc.).
                                withStyle(SpanStyle(color = colors.glow)) { append("$unseenCount") }
                                append(if (unseenCount == 1) " photo is" else " photos are")
                                append(" glowing for you")
                            } else {
                                append("You're all caught up")
                            }
                        },
                        // Plain UI font, not typography.display — same reasoning as the Memories
                        // label: this is a live status line, not a hero/name moment, and a simple
                        // sans reads cleaner and stays consistent across every theme instead of
                        // switching character (serif/script) depending which one's active. Only
                        // the font itself changes here — size, weight and tracking are untouched,
                        // after an earlier attempt that changed several of this line's properties
                        // at once ended up reading as a different, mismatched element rather than
                        // a deliberate restyle.
                        fontFamily = PublicSansFontFamily,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        color = colors.cream,
                        modifier = Modifier
                            .padding(top = 14.dp, start = 22.dp, end = 22.dp)
                            .graphicsLayer { alpha = headlineAlpha },
                    )
                    // "Tap to retry" folds into this line rather than the hero line above — the hero
                    // line already runs long enough on its own ("Couldn't connect") that adding retry
                    // text there wrapped to two lines on narrower/smaller screens, eating extra header
                    // height. This line has room for it.
                    Text(
                        text = buildAnnotatedString {
                            append(viewModel.userName?.substringBefore(" ")?.let { "Hey $it · ${viewModel.dateText}" } ?: viewModel.dateText)
                            if (hasConnectionError) {
                                append("  ·  ")
                                withStyle(SpanStyle(color = colors.glow)) { append("Tap to retry") }
                            }
                        },
                        fontFamily = typography.body,
                        fontSize = 12.5.sp,
                        color = colors.muted,
                        modifier = Modifier
                            .padding(top = 5.dp, start = 22.dp, end = 22.dp)
                            .let { if (hasConnectionError) it.clickable { viewModel.loadFeed() } else it },
                    )
                }
            }
            }

            // These status branches use fixed vertical padding instead of fillMaxSize because
            // the parent Column is now scrollable (unbounded height), where fillMaxSize
            // collapses to zero.
            //
            // Wrapped in the same pull-to-refresh shift as the title/subtitle Column above, so
            // the featured card and everything below it moves down together with them as one
            // unit while HomeBrandHeader alone stays put.
            Column(modifier = Modifier.graphicsLayer { translationY = pullOffsetFraction * PULL_REFRESH_CONTENT_OFFSET_DP.dp.toPx() }) {
            when {
                // !hasCompletedFirstSync is what keeps this scoped to "we've never gotten a real
                // answer yet" (first-ever open, nothing cached) — without it, refreshing an
                // account that's already confirmed to have zero shared photos re-enters this
                // branch too (isLoading flips true, feedItems is still empty), flashes a "your
                // photo is coming" skeleton for the refresh's duration, then resolves right back
                // to the plain empty state below the instant it completes. That reads as a
                // loading bug (something was about to appear and didn't), not a refresh.
                viewModel.isLoading && viewModel.feedItems.isEmpty() && !viewModel.hasCompletedFirstSync -> HomeSkeletonLoader(
                    modifier = Modifier.padding(top = 6.dp),
                )

                // Only blocks the whole screen when there's truly nothing cached to fall back
                // on — this used to run whenever errorMessage was set at all, which meant a
                // network blip hid an already-loaded, perfectly good cached feed behind a full
                // "couldn't connect" page instead of just showing what's already there. Once
                // there's a real feed on screen, a failed background refresh surfaces as the
                // small inline indicator in the `else` branch below instead.
                viewModel.errorMessage != null && viewModel.feedItems.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Doesn't repeat "Couldn't connect" — the header above already says
                        // that (see hasConnectionError). This is specifically the one thing that
                        // header line doesn't cover: there's nothing saved on this device yet
                        // to fall back on either.
                        Text(
                            text = "Nothing saved on this device yet",
                            fontFamily = typography.body,
                            fontSize = 13.sp,
                            color = colors.muted,
                        )
                        Text(
                            text = "Tap to retry",
                            fontFamily = typography.body,
                            fontSize = 13.sp,
                            color = colors.glow,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clickable { viewModel.loadFeed() },
                        )
                    }
                }

                viewModel.feedItems.isEmpty() -> {
                    // No one's shared anything yet — rather than a dead end, this leads straight
                    // into the (still-usable) Memories grid below, so there's always something to
                    // look at instead of just an empty message with nowhere to go. A small icon
                    // above the line, not just a stray sentence floating in empty space (see this
                    // app's own empty-state convention: icon + specific copy).
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = null,
                            tint = colors.mutedDim,
                            modifier = Modifier.size(26.dp),
                        )
                        Text(
                            text = "Once a friend shares a photo with you, it'll show up here",
                            fontFamily = typography.body,
                            fontSize = 13.sp,
                            color = colors.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    // Immediately visible (revealProgress = 1f), not the scroll-triggered fade-in
                    // the real-feed case below uses — there's no featured card/avatar row above
                    // this to scroll past, so scrollState could never reach the reveal threshold
                    // and the whole section stayed invisible forever (the bug this replaced).
                    HomeMemoriesSection(
                        viewModel = viewModel,
                        onCameraClick = onCameraClick,
                        dayFocusState = dayFocusState,
                        chromeBlur = chromeBlur,
                        chromeFade = chromeFade,
                        revealProgress = { 1f },
                        onMemoriesFocusChanged = { isMemoriesFocused = it },
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .onGloballyPositioned { memoriesTopWindowY = it.positionInWindow().y },
                    )
                }

                else -> {
                    // One continuous carousel across every friend's photos, in feed order —
                    // not a pager per friend. Swiping past someone's last photo lands you on
                    // the next friend's first, and back past a first photo lands on the
                    // previous friend's last, for free, because it's all just paging through
                    // one flattened sequence rather than something hand-chained.
                    val entries = remember(viewModel.feedItems) { buildHomeCarousel(viewModel.feedItems) }
                    val pagerState = rememberPagerState(
                        initialPage = pageIndexFor(entries, viewModel.feedItems, viewModel.selectedFriendId, viewModel),
                    ) { entries.size }
                    val avatarListState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    val density = LocalDensity.current

                    // Per-friend position is recorded immediately off the *settled* page. Marking
                    // a photo seen works differently, and deliberately lags one step behind: each
                    // time the pager settles on a new page, it's the *previous* settled entry —
                    // the one just swiped away from — that gets marked, not the one now on
                    // screen. So a photo only ever turns seen once the user has actually moved on
                    // from it, never while it's still the one showing, however they got there
                    // (swipe, avatar tap, or it being the page Home opened on). That also means
                    // the very last unseen photo in a run only clears once swiped past too — not
                    // the instant it's reached — exactly matching every other photo in that run.
                    // A version of this that instead marked the *current* page after a short dwell
                    // was tried and rejected: the dwell still marked a photo seen while the user
                    // was looking straight at it, which read as the ring/dots jumping ahead of
                    // what was actually being watched, especially for a friend's last new photo.
                    //
                    // previousEntry isn't reset when entries rebuilds (e.g. a fresh photo
                    // arriving) — it's tracking "whatever was actually on screen a moment ago",
                    // which stays meaningful regardless of why the list changed identity.
                    //
                    // lastProcessedPage guards against a real bug this had: marking a photo seen
                    // updates feedItems, which rebuilds entries — and entries is one of this
                    // effect's own keys, so that rebuild immediately re-triggers this same
                    // LaunchedEffect for the *same* settledPage. Without this guard, that retrigger
                    // would see previousEntry still pointing at the entry *just* set below and
                    // mark it too — the photo the user is still actively looking at, not the one
                    // before it — defeating the entire "only after swiping away" rule almost
                    // immediately. Only advancing previousEntry when settledPage's actual numeric
                    // value changes (a real swipe/navigation) — not merely when entries reshuffles
                    // — keeps this effect inert against its own side effect.
                    var previousEntry by remember { mutableStateOf<HomeCarouselEntry?>(null) }
                    var lastProcessedPage by remember { mutableStateOf<Int?>(null) }
                    LaunchedEffect(pagerState.settledPage, entries) {
                        val entry = entries.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
                        if (entry.friendId != viewModel.selectedFriendId) {
                            viewModel.selectFriend(entry.friendId)
                        }
                        viewModel.setPhotoIndex(entry.friendId, entry.indexWithinFriend)
                        if (lastProcessedPage != pagerState.settledPage) {
                            previousEntry?.let { viewModel.markPhotoSeen(it.friendId, it.photo.photoId) }
                            previousEntry = entry
                            lastProcessedPage = pagerState.settledPage
                        }
                    }

                    // The one gap the "mark the previous entry once you've swiped away" rule
                    // above can't cover: there's no page after the very last one to ever swipe
                    // to, so a friend whose only (or last) photo lands there — including the
                    // degenerate case of the whole feed being just one photo — would stay
                    // glowing as unseen forever, no matter how long it sits on screen. This is
                    // deliberately NOT the general dwell-based marking that was already tried and
                    // rejected for every photo (see the comment above) — it only ever applies to
                    // this one dead-end page, so every other photo still only clears once
                    // actually swiped past, exactly as before.
                    LaunchedEffect(pagerState.settledPage, entries) {
                        if (pagerState.settledPage != entries.lastIndex) return@LaunchedEffect
                        val entry = entries.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
                        delay(LAST_PHOTO_DWELL_MARK_SEEN_MS)
                        viewModel.markPhotoSeen(entry.friendId, entry.photo.photoId)
                    }

                    // The avatar row tracks the *live* page, same as the card — it should move
                    // the instant you cross into a new friend's photos, not wait for the swipe
                    // to fully settle. A settle-based trigger was tried and made the row visibly
                    // lag behind the card after every swipe; reacting live keeps the two in step.
                    val activeFriendId = entries.getOrNull(pagerState.currentPage)?.friendId
                        ?: viewModel.feedItems.first().friendId

                    // Keeps AmbientPhotoBackdrop (rendered from this screen's own outer Box, see
                    // its call site) in sync with whichever photo is on screen — driven by when
                    // scrolling actually *stops*, not by a timer. A fixed delay was tried and was
                    // wrong in both directions at once: it made a single deliberate swipe wait for
                    // a timeout that had nothing to do with the gesture, while a fast flick could
                    // still slip a change through between two swipes. Keying on isScrollInProgress
                    // instead means one swipe updates the instant it lands (the fade below is then
                    // the only thing between the swipe and the new colour), while flicking through
                    // several photos keeps scrolling continuously — so the backdrop simply holds
                    // whatever it was showing until the flick genuinely comes to rest, then
                    // switches once to wherever it landed.
                    LaunchedEffect(entries) {
                        snapshotFlow { pagerState.isScrollInProgress }
                            .collect { isScrolling ->
                                if (!isScrolling) {
                                    currentPhotoUrl = entries.getOrNull(pagerState.currentPage)?.photo?.photoUrl
                                }
                            }
                    }

                    // The avatar row is blurred while focused specifically so it recedes —
                    // letting it keep resizing/recentering underneath while blurred would pull
                    // attention right back to it. Freeze it on whichever friend was active the
                    // moment focus began, and only let it catch up — smoothly, via the same
                    // animations below, not a snap — once the tap turns focus back off.
                    val frozenFriendId = remember(isPhotoFocused) { activeFriendId }
                    val displayFriendId = if (isPhotoFocused) frozenFriendId else activeFriendId

                    LaunchedEffect(displayFriendId) {
                        val index = viewModel.feedItems.indexOfFirst { it.friendId == displayFriendId }
                        if (index < 0) return@LaunchedEffect
                        val itemWidthPx = with(density) { AVATAR_ITEM_WIDTH_DP.dp.toPx() }
                        val itemStridePx = with(density) { (AVATAR_ITEM_WIDTH_DP + AVATAR_SPACING_DP).dp.toPx() }
                        avatarListState.smoothCenterOn(index, itemStridePx, itemWidthPx)
                    }

                    // A connection error no longer gets its own line here — it now takes over the
                    // header's own hero line above (see the hasConnectionError branch there) so
                    // it doesn't sit in contradiction next to a "you're all caught up" claim the
                    // app can no longer verify. Nothing further needed in this specific spot.

                    // On a short enough viewport, the card's own aspect-ratio height (derived
                    // purely from width, with no awareness of the screen's actual height) can push
                    // the avatar row below it down into the floating nav dock's zone — the dock is
                    // a fixed overlay anchored to the true screen bottom, so whatever lands there
                    // at rest (scroll position 0) gets visually covered, with no scroll interaction
                    // able to fix that specific spot. Two earlier fixes both guessed at how much
                    // space the header/avatar row need (first via live cross-sibling measurement
                    // that didn't converge reliably, then via generous-but-still-not-generous-enough
                    // fixed dp constants that still landed too tight on at least one real device) —
                    // both were fundamentally still "hope this number is big enough" arithmetic.
                    // This instead wraps the card + avatar row in their own Column bounded to
                    // exactly the real remaining space (screenSize, minus the real measured status
                    // bar inset, minus the header's own real measured height, minus the dock's
                    // reserve), and gives the card `weight(1f, fill = false)` — Compose's own
                    // layout algorithm then measures the avatar row (a non-weighted sibling) at its
                    // real natural size FIRST and gives the card whatever's left, never more than
                    // that bound allows. This can't overflow past the dock, structurally, on any
                    // device — nothing here is a guess except the dock's own already-accepted
                    // the dock's own real measured footprint (LocalNavDockHeight), not a guess.
                    val statusBarPx = WindowInsets.statusBars.getTop(density)
                    val navDockHeightPx = with(density) { LocalNavDockHeight.current.toPx() }
                    // screenSize and headerHeightPx both start at zero for one frame before their
                    // real onSizeChanged/onGloballyPositioned callbacks land (see their own doc
                    // comments) — computing topFoldMaxHeightDp from zero-or-near-zero inputs on
                    // that first frame produces a wrong (often negative, clamped to ~0) bound, so
                    // the card + avatar row would render collapsed for a frame and then visibly
                    // snap/expand to their real size the instant the real measurements arrive.
                    // Rather than accept that as a "just one frame, should be imperceptible"
                    // trade-off, skip rendering this section entirely until both real
                    // measurements are in — it appears once, already at its correct final size,
                    // instead of appearing wrong and then correcting itself.
                    if (screenSize != Size.Zero && headerHeightPx > 0f) {
                    val topFoldMaxHeightDp = with(density) {
                        (screenSize.height - statusBarPx - headerHeightPx - navDockHeightPx).toDp()
                    }
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = topFoldMaxHeightDp)) {
                        AnimatedVisibility(
                            visible = viewModel.hasNewFeedAvailable,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(150)),
                        ) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = colors.glow)) { append("New memories") }
                                    append(" available")
                                },
                                fontFamily = typography.body,
                                fontSize = 12.5.sp,
                                color = colors.muted,
                                modifier = Modifier
                                    .padding(top = 5.dp, start = 22.dp, end = 22.dp)
                                    .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                                    .graphicsLayer { alpha = chromeFade }
                                    .clickable { viewModel.revealNewFeed() },
                            )
                        }

                        FeaturedPhotoCard(
                            entries = entries,
                            pagerState = pagerState,
                            isFocused = isPhotoFocused,
                            isAtDefaultScrollPosition = isHomeAtDefaultScrollPosition,
                            // Only actually toggles focus when Home is scrolled all the way to its
                            // default resting position — without this, tapping the card while it's
                            // only half-visible (scrolled partway down into Memories) still blurred
                            // the whole screen, which made no sense for a tap that mostly landed on
                            // Memories content, not the card itself.
                            onToggleFocus = { if (scrollState.value == 0) onToggleFocus() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 18.dp, start = FEATURED_CARD_SIDE_PADDING, end = FEATURED_CARD_SIDE_PADDING)
                                // weight(fill = false) alone is what makes this genuinely
                                // responsive to any screen size: Compose measures the avatar row
                                // below (a non-weighted sibling) at its real natural height first,
                                // then gives this card exactly whatever's left within
                                // topFoldMaxHeightDp above — never more, so it can never push the
                                // avatar row into the nav dock, on any device. A heightIn(min = ...)
                                // floor used to sit here too, which defeated that guarantee on any
                                // screen short enough that the real remaining space was less than
                                // the floor — forcing an overflow instead of a smaller card, which
                                // is exactly the cramped/broken layout this was reported on.
                                .weight(1f, fill = false)
                                .blur(cardBlurWhenMemoriesFocused, BlurredEdgeTreatment.Unbounded)
                                .graphicsLayer { alpha = cardFadeWhenMemoriesFocused },
                        )

                        FocusShield(active = isPhotoFocused, onDismiss = onDismissFocus) {
                        FriendAvatarRow(
                            viewModel = viewModel,
                            activeFriendId = displayFriendId,
                            listState = avatarListState,
                            onAvatarClick = { friendId ->
                                // Always that friend's FIRST (newest) photo, deliberately not
                                // pageIndexFor's remembered position — auto-advance walks forward
                                // through every friend's photos on its own timer, so by the time
                                // it's moved on to someone else, the friend it just left has its
                                // remembered position sitting on their LAST photo. Tapping a
                                // friend's avatar is a deliberate "show me them" action; it should
                                // never land on whatever auto-advance (or an earlier manual swipe)
                                // happened to leave behind.
                                val target = entries.indexOfFirst { it.friendId == friendId }.coerceAtLeast(0)
                                scope.launch { pagerState.animateScrollToPage(target) }
                            },
                            onAddFriendClick = onAddFriendClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp, bottom = 12.dp)
                                .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                                .graphicsLayer { alpha = chromeFade },
                        )
                        }
                    }
                    }

                    // Memories starts right where the feed ends — on the page, in the normal
                    // scroll flow, landing in the gap just above the nav dock at rest, with a
                    // little breathing room below the avatar row (matching the ~20dp rhythm used
                    // between every other section on this screen) rather than sitting flush
                    // against it. Both the label and the grid stay invisible at rest, fading in
                    // together only once the user actually scrolls up (memoriesRevealProgress).
                    HomeMemoriesSection(
                        viewModel = viewModel,
                        onCameraClick = onCameraClick,
                        dayFocusState = dayFocusState,
                        chromeBlur = chromeBlur,
                        chromeFade = chromeFade,
                        revealProgress = memoriesRevealProgress,
                        onMemoriesFocusChanged = { isMemoriesFocused = it },
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .onGloballyPositioned { memoriesTopWindowY = it.positionInWindow().y },
                    )
                }
            }
            }
        }
        }

    // Rendered from THIS screen's own outer Box (fillMaxSize of the true measured screen size,
    // via screenSize above) rather than from inside the scrollable Column above — see
    // DayFocusState's doc comment for why centering the overlay against the real screen requires
    // living outside that unbounded-height scroll container.
    dayFocusState.target?.let { target ->
        DayFeaturedOverlay(
            target = target,
            screenSize = screenSize,
            progress = dayFocusState.progress.value,
            onDismiss = { dayFocusState.isOpen = false },
            onCurrentPhotoChanged = { currentPhotoUrl = it },
        )
    }
    }
}

/** The "Memories" label + grid, bundled as one unit — [HomeScreen] renders this at two different
 * points in its status `when` (an empty feed vs. a real one), and keeping it as a single
 * reusable composable instead of two separately-maintained call sites is what stops their
 * modifiers/params from quietly drifting apart from each other, the way the old duplicated
 * version already had (blur applied inconsistently between the two). [revealProgress] controls
 * the fade-in — pass `{ 1f }` wherever this section should just be visible immediately (nothing
 * above it to scroll past first), or the real scroll-driven lambda (`memoriesRevealProgress`)
 * wherever that fade-as-you-scroll-up behavior is what's wanted. */
@Composable
private fun HomeMemoriesSection(
    viewModel: HomeViewModel,
    onCameraClick: () -> Unit,
    dayFocusState: DayFocusState,
    chromeBlur: Dp,
    chromeFade: Float,
    revealProgress: () -> Float,
    onMemoriesFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        MemoriesSectionLabel(
            modifier = Modifier
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp)
                .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer { alpha = revealProgress() * chromeFade },
        )
        MemoriesGridContent(
            memories = viewModel.memories,
            selectedMonth = viewModel.selectedMonth,
            onCameraClick = onCameraClick,
            focusState = dayFocusState,
            onFocusChanged = onMemoriesFocusChanged,
            onPreviousMonth = { viewModel.goToPreviousMonth() },
            onNextMonth = { viewModel.goToNextMonth() },
            canGoToPreviousMonth = viewModel.canGoToPreviousMonth,
            canGoToNextMonth = viewModel.canGoToNextMonth,
            isLoadingMonth = viewModel.isLoadingSelectedMonth,
            modifier = Modifier
                .fillMaxWidth()
                .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer { alpha = revealProgress() * chromeFade }
                .padding(bottom = LocalNavDockHeight.current),
        )
    }
}

/** "Memories" section label — centered, plain UI font, no discoverability affordance beside it
 * (a bobbing chevron used to sit here; removed since the label itself, centered like every other
 * section title in the app, already reads as a heading rather than something that needs a hint
 * to be noticed). */
@Composable
private fun MemoriesSectionLabel(modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        // Plain UI font, not typography.display — that face is a decorative, per-theme character
        // face (serif/script depending on theme) meant for the odd hero moment (the "Ember"
        // wordmark, a name on a profile), not a section label. A section label reads as cleaner
        // and more legible in the same simple sans every messaging/social app (WhatsApp,
        // Instagram, Snapchat) uses for its own UI chrome, same as the rest of this app's own
        // body/button text already does via PublicSansFontFamily.
        Text(
            text = "Memories",
            fontFamily = PublicSansFontFamily,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colors.cream,
        )
    }
}

/** Wraps [content] with an invisible overlay while [active] — the overlay sits in front and
 * covers it completely, so nothing nested inside (avatar taps, nav buttons, the profile chip,
 * even the avatar row's own scrolling) can still react while blurred; it's a separate sibling
 * on top rather than a modifier on the content itself, so it wins regardless of what the content
 * has its own click/scroll handling on. A plain tap anywhere on the overlay closes focus, same
 * as tapping the card does — a drag is simply absorbed with no effect. */
@Composable
private fun FocusShield(
    active: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        if (active) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
    }
}

/** Ambient, heavily-blurred backdrop in the same shade as whichever photo the featured card is
 * currently focused on — the Apple Music/Spotify "blurred album art" trick, reusing the exact
 * photo already decoded for the sharp card in front of it (Coil serves that from cache, so this
 * is a free extra composition, not a second fetch/decode) rather than computing an average color
 * by hand. [visible] gates the whole thing on focus, per how blur already works on this screen
 * (tap → things react) — the backdrop fades in/out over the flat background rather than
 * appearing instantly.
 *
 * A change to [photoUrl] starts crossfading immediately — deciding *which* photo counts as the
 * one to show is entirely upstream's job (whoever sets [photoUrl] only does so once its pager has
 * actually stopped scrolling), so anything that reaches this composable is already a change worth
 * reacting to right away. Nothing here adds any further delay before the fade begins.
 *
 * Real blur needs `RenderEffect`, only available from API 31 — below that this renders nothing
 * at all rather than an unblurred photo sitting behind everything, which would look like a bug
 * rather than a missing enhancement. */
@Composable
private fun AmbientPhotoBackdrop(photoUrl: String?, visible: Boolean, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val backdropAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "ambientBackdropVisibility",
    )
    // Stops rendering (and blurring) once fully faded out and no longer wanted — not just
    // whenever the target alpha is 0, so the fade-out animation itself still gets to play.
    if (backdropAlpha <= 0f && !visible) return

    Box(modifier = modifier.graphicsLayer { alpha = backdropAlpha }) {
        Crossfade(
            targetState = photoUrl,
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            label = "ambientBackdropPhoto",
        ) { url ->
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        // Scaled up beyond the blur radius so the soft, sampled-from-nothing edge
                        // a heavy blur leaves at the image's true border sits safely offscreen.
                        .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
                        .blur(72.dp, BlurredEdgeTreatment.Unbounded),
                )
            }
        }
        // Muted/darkened rather than a bright duplicate of the photo — reads as ambient mood
        // lighting behind real content, the same moody register the rest of this app's dark
        // theme already sits in, not a second, distracting copy of the picture itself.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    }
}

private fun daypart(): String {
    val hour = java.time.LocalDateTime.now().hour
    return when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        else -> "evening"
    }
}

/** The "Ember" wordmark + [ProfileChip], as their own row — split out from the rest of Home's
 * header (the greeting/date lines) so it can be positioned and customized independently of them.
 * At its call site in [HomeScreen] this is deliberately kept outside the scrollable/pull-to-
 * refresh area, so it behaves like a fixed top bar (Instagram's own top bar is the reference)
 * rather than scrolling or reacting to a pull gesture the way it used to when it was just the
 * first row inside that same scrollable content. */
@Composable
private fun HomeBrandHeader(
    userName: String?,
    profilePhotoUrl: String?,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp, start = 22.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Ember",
            fontFamily = CourgetteFontFamily,
            fontSize = 34.sp,
            letterSpacing = (-0.5).sp,
            // Plain neutral cream, not the theme accent — the accent color read as distracting
            // here. Size and tighter tracking alone carry the "this is the brand mark" distinction
            // instead.
            color = colors.cream,
        )
        ProfileChip(name = userName, photoUrl = profilePhotoUrl, onClick = onProfileClick)
    }
}

/** Small circular chip in the header showing the signed-in user's own profile photo (falling
 * back to their initial if they haven't set one), with an ember-orange presence dot. */
@Composable
internal fun ProfileChip(name: String?, photoUrl: String?, onClick: () -> Unit) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    // 48dp, not 40dp — Android's own minimum recommended touch target (48x48dp); the old size
    // was noticeably under that for the one control that opens your own profile.
    Box(modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                // colors.border is a translucent white/black hairline token meant for stroke
                // outlines, not a fill — reused here at higher alpha it read as a washed-out grey
                // blob instead of a themed avatar backing. Plain colors.panel is the correct fill:
                // it already resolves to the right dark-charcoal-or-bright-cream tone per theme.
                .background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                val painter = rememberAsyncImagePainter(model = photoUrl)
                val painterState by painter.state.collectAsState()
                val isLoading = when (painterState) {
                    is AsyncImagePainter.State.Loading, is AsyncImagePainter.State.Empty -> true
                    else -> false
                }
                if (isLoading) {
                    val pulseAlpha by rememberSkeletonPulse(periodMillis = 900)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = pulseAlpha }
                            .background(colors.panel),
                    )
                }
                Image(
                    painter = painter,
                    contentDescription = "Your profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = name?.firstOrNull()?.uppercase() ?: "•",
                    fontFamily = typography.display,
                    fontSize = 19.sp,
                    color = colors.cream,
                )
            }
        }
    }
}

// Must match FriendAvatarRow's actual avatar Column width and horizontalArrangement spacing —
// there's no way to derive these from the LazyRow itself without them already being visible,
// which is exactly the problem with the built-in animateScrollToItem this replaces.
/** The slot each avatar occupies. The avatar itself still grows/shrinks within it to mark the
 * active friend (see avatarSize below) — this is just the fixed box that keeps the row's own
 * layout steady while that animates. */
private const val AVATAR_DIAMETER_DP = 82

/** The non-active avatars' diameter — they animate down to this, and back up to
 * [AVATAR_DIAMETER_DP] when they become the active one. */
private const val AVATAR_INACTIVE_DIAMETER_DP = 78

/** The ring band and the gap between it and the photo. Equal by design — see their use site. */
private const val AVATAR_RING_WIDTH_DP = 3.0f
private const val AVATAR_RING_GAP_DP = 3.0f

/** Must match the real width of each avatar column below — smoothCenterOn derives its scroll
 * target from this, so a value that disagrees with the actual layout centers every avatar
 * slightly off (this was 72 while the column has always measured 84). */
private const val AVATAR_ITEM_WIDTH_DP = 88
private const val AVATAR_SPACING_DP = 4

/** A fully custom smooth-scroll to center [targetIndex], replacing `LazyListState
 * .animateScrollToItem` — that built-in animates with an opaque, un-tunable spring, and for
 * targets outside the current layout it snaps most of the way there before animating only the
 * remainder, which reads as a jump-cut rather than a glide. Computing the exact pixel delta
 * ourselves (every avatar is the same fixed width, so this doesn't need the item to already be
 * measured/visible) and driving it with our own fixed tween sidesteps both problems — the same
 * curve plays every time, regardless of how far the scroll has to travel. */
private suspend fun LazyListState.smoothCenterOn(targetIndex: Int, itemStridePx: Float, itemWidthPx: Float) {
    val viewportPx = layoutInfo.viewportSize.width.toFloat()
    if (viewportPx <= 0f) return
    val currentPx = firstVisibleItemIndex * itemStridePx + firstVisibleItemScrollOffset
    val targetPx = (targetIndex * itemStridePx + itemWidthPx / 2f) - viewportPx / 2f
    val deltaPx = targetPx - currentPx
    if (kotlin.math.abs(deltaPx) < 0.5f) return

    var applied = 0f
    scroll {
        Animatable(0f).animateTo(deltaPx, tween(320, easing = FastOutSlowInEasing)) {
            scrollBy(value - applied)
            applied = value
        }
    }
}

/** One page in the flattened cross-friend carousel — a friend's photos in feed order, newest
 * first, followed immediately by the next friend's. [isFriendsNewest] marks a friend's first
 * (newest) photo, the trigger for marking it seen. */
internal data class HomeCarouselEntry(
    val friendId: String,
    val displayName: String,
    val streak: Int,
    val photo: PhotoEntryDto,
    val isFriendsNewest: Boolean,
    /** Position within this friend's own photos (0 = newest) and how many they have — drives
     * the per-friend photo-count dots on the card, same as the old per-friend pager showed. */
    val indexWithinFriend: Int,
    val totalForFriend: Int,
)

private fun buildHomeCarousel(feedItems: List<FeedItem>): List<HomeCarouselEntry> {
    val entries = mutableListOf<HomeCarouselEntry>()
    feedItems.forEach { item ->
        val ordered = item.photos.asReversed()
        ordered.forEachIndexed { index, photo ->
            entries += HomeCarouselEntry(
                friendId = item.friendId,
                displayName = item.displayName,
                streak = item.streak,
                photo = photo,
                isFriendsNewest = index == 0,
                indexWithinFriend = index,
                totalForFriend = ordered.size,
            )
        }
    }
    return entries
}

/** Where a given friend's (remembered) position lands in the flattened carousel — used both to
 * pick the pager's start page and to jump there when an avatar is tapped directly. */
private fun pageIndexFor(
    entries: List<HomeCarouselEntry>,
    feedItems: List<FeedItem>,
    friendId: String?,
    viewModel: HomeViewModel,
): Int {
    if (entries.isEmpty()) return 0
    val targetFriendId = friendId ?: feedItems.firstOrNull()?.friendId ?: return 0
    var pagesBefore = 0
    for (item in feedItems) {
        if (item.friendId == targetFriendId) {
            val within = viewModel.photoIndexFor(targetFriendId).coerceIn(0, (item.photos.size - 1).coerceAtLeast(0))
            return (pagesBefore + within).coerceIn(0, entries.lastIndex)
        }
        pagesBefore += item.photos.size
    }
    return 0
}

/** How long the featured card dwells on one photo before auto-advancing to the next. */
private const val AUTO_ADVANCE_INTERVAL_MS = 4000L

/** How long the crossfade (photo + dot-row) takes when auto-advancing — deliberately slow and
 * gradual, unlike a manual swipe's instant dot switch (see the dot-row below). */
private const val AUTO_ADVANCE_FADE_MS = 900

/** How many pages on either side of the current one stay composed (see HorizontalPager's
 * beyondViewportPageCount below) — each composed page's AsyncImage starts its Coil request the
 * moment it's composed, not when it actually scrolls into view, so the very next photo is
 * already fetching while you're still mid-swipe on the current one rather than only starting
 * once you land on it. Kept small and deliberately singular (not a bulk preload of the whole
 * feed — that was tried and reverted, see PROJECT_CONTEXT.md, for competing with the real feed/
 * memories fetch over the network right at cold start): every extra page composed is another
 * in-flight request and another bitmap held in memory, for a photo that's rarely more than one
 * swipe away from actually being seen. */
private const val FEATURED_CARD_LOOKAHEAD_PAGES = 1

/** The large featured card — one continuous pager across every friend's photos (see
 * [buildHomeCarousel]), so swiping past someone's last photo lands on the next friend's first
 * and back past a first photo lands on the previous friend's last, without any special-casing:
 * it's all just paging through one flattened list. Memories lives further down the same screen's
 * scroll now (see [MemoriesGridContent] in [HomeScreen]), rather than one more page in this pager. */
@Composable
private fun FeaturedPhotoCard(
    entries: List<HomeCarouselEntry>,
    pagerState: PagerState,
    isFocused: Boolean,
    isAtDefaultScrollPosition: Boolean,
    onToggleFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val cardShape = RoundedCornerShape(FEATURED_CARD_CORNER_RADIUS)

    // Auto-advances to the next photo like a Stories-style card, rather than sitting still until
    // someone swipes — the crossfade below (not a slide) is deliberately unlike a manual swipe,
    // so an automatic change never reads as "someone else is swiping my phone." Paused entirely
    // while focused (tapped open) — someone deliberately holding a photo on screen to look at it
    // shouldn't have it change under them — and paused while Home is scrolled away from its
    // default position (into Memories), matching onToggleFocus's own "only while at the top"
    // gate at the call site: a card that isn't even the thing on screen anymore shouldn't keep
    // silently changing underneath whatever the user's actually looking at further down the page.
    //
    // One long-running loop, NOT a LaunchedEffect keyed on pagerState.currentPage — this effect's
    // own scrollToPage call changes that value as part of its normal operation, and keying on it
    // would make Compose cancel and restart THIS SAME effect the moment its own advance takes
    // effect, aborting the crossfade fade-out (and the outgoingPhotoUrl = null after it) partway
    // through every single time. Reading currentPage as a plain value inside the loop instead
    // detects manual swipes (the page changed while nothing here was awaiting on it) without that
    // self-cancellation problem.
    var outgoingPhotoUrl by remember { mutableStateOf<String?>(null) }
    val outgoingAlpha = remember { Animatable(0f) }
    LaunchedEffect(entries.size, isFocused, isAtDefaultScrollPosition) {
        if (entries.size <= 1 || isFocused || !isAtDefaultScrollPosition) return@LaunchedEffect
        while (true) {
            val pageBeforeWait = pagerState.currentPage
            delay(AUTO_ADVANCE_INTERVAL_MS)
            // A manual swipe (or avatar tap) already moved on during the wait — restart the
            // countdown for wherever it landed instead of also advancing from there.
            if (pagerState.currentPage != pageBeforeWait) continue
            if (pagerState.isScrollInProgress) continue
            outgoingPhotoUrl = entries[pageBeforeWait].photo.photoUrl
            outgoingAlpha.snapTo(1f)
            pagerState.scrollToPage((pageBeforeWait + 1) % entries.size)
            outgoingAlpha.animateTo(0f, tween(AUTO_ADVANCE_FADE_MS, easing = FastOutSlowInEasing))
            outgoingPhotoUrl = null
        }
    }

    // This card's pager (cycling a friend's photos) and MainActivity's own outer pager (Home,
    // Friends, Camera, Activity, Settings) are both horizontal, one nested inside the
    // other. Left alone, Compose's nested-scroll handoff between two pagers on the same axis is
    // genuinely unreliable — a drag that starts here could leave leftover velocity bubbling up to
    // the outer pager mid-gesture, which is what produced the "stops partway, showing two pages
    // at once" glitch. Rather than tune fling thresholds to paper over that, this connection
    // consumes every bit of leftover scroll/fling itself (onPost*, not onPre* — the card's own
    // pager still scrolls normally first), so nothing from a drag that starts on this card is
    // ever left for the outer pager to receive. Swiping outside the card (background, avatar
    // row, header) is unaffected and still drives the outer pager exactly as before.
    val cardNestedScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(FEATURED_CARD_ASPECT_RATIO)
            .nestedScroll(cardNestedScrollBoundary)
            .clip(cardShape)
            // The single most important surface on this whole screen — elevated, not the same
            // panel tone every plain row/chip elsewhere uses, so it visibly outranks them behind
            // whichever photo is actually loaded (this only ever peeks out at the card's own
            // edges/behind a transparent PNG — the photo itself is still the real hero).
            .background(colors.elevatedPanel)
            // A plain tap (not a swipe) toggles focus mode — no ripple, since the blur
            // transition on the rest of the screen already reads as the tap's feedback.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleFocus,
            ),
    ) {
        // BoxWithConstraints (not a plain Box) purely so the photo-count segments below know the
        // card's own real width — needed to shrink them evenly once there are enough photos that
        // they'd otherwise run past the card's edges instead of staying within its same 22dp
        // side inset every other overlay element (name/streak row) already respects.
        val cardWidth = maxWidth
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = FEATURED_CARD_LOOKAHEAD_PAGES,
        ) { page ->
            val entry = entries[page]
            // Plain AsyncImage paints nothing at all while its request is in flight — behind it
            // sits this Box's own colors.elevatedPanel background (see above), a flat, static
            // color with no indication anything is happening. That reads as broken, not loading,
            // especially the first time a photo is shown (nothing's been decoded into memory yet,
            // so this is never instant even with everything else already fixed — a slow/uncached
            // network fetch is still a real wait, just no longer a mysterious-looking one).
            // Tracking the painter's own state directly (rather than plain AsyncImage) lets this
            // card show the same pulsing skeleton animation used elsewhere in this exact file
            // (see SkeletonFeaturedCard/rememberSkeletonPulse) for as long as that wait actually
            // lasts, instead of a static rectangle that looks identical whether it's mid-load or
            // stuck.
            val painter = rememberAsyncImagePainter(model = entry.photo.photoUrl)
            // painter.state is a StateFlow<State>, not the State itself — collectAsState is what
            // actually turns "did the request finish" into something Compose recomposes on.
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

        // The outgoing photo, held statically on top and faded out — the pager underneath has
        // already jumped (instantly, no slide) to the next page by the time this starts fading,
        // so what's actually visible is a crossfade from old to new, not a slide.
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

        // Bottom scrim keeps the overlaid name/time/streak readable on any photo, and
        // the generous padding keeps them clear of the rounded corners.
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

            // Per-friend photo count, same as before the flattened carousel — how many this
            // specific friend has and which one you're on, not a position in the whole sequence.
            if (current.totalForFriend > 1) {
                // Looked up (not assumed contiguous) so each dot can show whether *that specific*
                // photo has actually been seen yet — buildHomeCarousel already lays a friend's
                // entries out in indexWithinFriend order, but finding them explicitly here doesn't
                // depend on that staying true.
                val friendEntries = remember(entries, current.friendId) {
                    entries.filter { it.friendId == current.friendId }.sortedBy { it.indexWithinFriend }
                }
                // Same 16dp width every dot already used as long as they all fit — only actually
                // shrinks (evenly, all of them together) once there are enough photos that 16dp
                // each would run past the card's own 22dp-inset edges, same margin the name/streak
                // row below already respects. Never fewer dots, never one dot a different size
                // from its neighbors — just the whole row scaling down together to still fit.
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
                        // A photo you've already swiped past is seen, full stop, the instant you
                        // land on the next one — it doesn't wait for photo.seen, which only flips
                        // once the actual markPhotoSeen mutation completes (deliberately lagged by
                        // one step, see the LaunchedEffect above). Without this position-based
                        // override, the dot for whatever you just swiped away from kept showing
                        // its old unseen color for however long that mutation took to land — a
                        // visible flash of staleness for a bar the pager has already moved past.
                        // Only dots at or ahead of where you actually are still defer to the real
                        // seen flag, since those genuinely might or might not have been viewed yet.
                        val isUnseen = index >= current.indexWithinFriend &&
                            friendEntries.getOrNull(index)?.photo?.seen == false
                        val targetDotColor = when {
                            index == current.indexWithinFriend -> Color.White
                            isUnseen -> colors.glow
                            else -> Color.White.copy(alpha = 0.35f)
                        }
                        // Instant for a manual swipe (0ms) — an animated crossfade for THAT case
                        // was tried and rejected before: swiping away still showed the previous
                        // dot's old (unseen) color for the length of the fade before it caught up,
                        // which read as lag, so the dot for whatever's now on screen must never be
                        // stale even for a moment. Auto-advance is different — outgoingPhotoUrl is
                        // non-null for exactly as long as that crossfade runs, so the dot-row fades
                        // in step with the photo instead of snapping ahead of it.
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
                    Text(
                        text = current.displayName,
                        fontFamily = typography.display,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.2).sp,
                        color = Color(0xFFFBF8F3),
                    )
                    Text(
                        text = formatRelativeTime(current.photo.createdAt),
                        fontFamily = typography.body,
                        fontSize = 12.5.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.LocalFireDepartment,
                        contentDescription = "Streak",
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

/** Cold-start loading state — shaped like the real featured card + avatar row it's about to be
 * replaced by, rather than a generic centered spinner, so the layout doesn't visibly "jump" once
 * data lands. A slow, flat opacity pulse (no gradient sweep, no glow) is the one animation —
 * matches this app's own no-glow/no-glassmorphism rule while still reading clearly as "loading,"
 * not just static gray boxes. */
@Composable
private fun HomeSkeletonLoader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SkeletonFeaturedCard()
        Row(
            modifier = Modifier
                .padding(top = 22.dp)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(5) { index -> SkeletonAvatar(staggerIndex = index) }
        }
    }
}

/** A single, shared pulse spec every skeleton element pulls from — [staggerMillis] is the only
 * thing that varies per call site, via [InfiniteRepeatableSpec]'s own `initialStartOffset`. That
 * one shared rhythm is what makes several staggered elements read as one coordinated ripple
 * instead of unrelated shapes that each happen to be animating. */
@Composable
private fun rememberSkeletonPulse(periodMillis: Int, staggerMillis: Int = 0): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    return transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(staggerMillis, StartOffsetType.FastForward),
        ),
        label = "skeletonAlpha",
    )
}

/** The featured-card placeholder — pulses slowly on its own, since it's the visual anchor of the
 * whole screen, plus a second, smaller "caption chip" in the same bottom-left spot the real
 * card's name label sits, pulsing on a short lag behind the card itself. That lag (not a second
 * independent rhythm) is what reads as one shape settling a beat after the other, rather than
 * two unrelated blinking rectangles. */
@Composable
private fun SkeletonFeaturedCard() {
    val colors = EmberTheme.colors
    val cardAlpha by rememberSkeletonPulse(periodMillis = 1100)
    val chipAlpha by rememberSkeletonPulse(periodMillis = 1100, staggerMillis = 220)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FEATURED_CARD_SIDE_PADDING)
            .aspectRatio(FEATURED_CARD_ASPECT_RATIO)
            .graphicsLayer { alpha = cardAlpha }
            .clip(RoundedCornerShape(FEATURED_CARD_CORNER_RADIUS))
            // Matches the real card's own elevatedPanel tone — otherwise the loading state
            // resolves into a visible tone-shift the instant the real photo arrives.
            .background(colors.elevatedPanel),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp)
                .width(84.dp)
                .height(14.dp)
                .graphicsLayer { alpha = chipAlpha }
                .clip(RoundedCornerShape(7.dp))
                .background(colors.mutedDim),
        )
    }
}

/** One avatar-row placeholder. [staggerIndex] delays this item's pulse relative to the ones
 * before it (see [rememberSkeletonPulse]), so the row ripples left to right instead of every
 * circle breathing in the same dead unison — the one thing that made the first version of this
 * loader feel flat. A faint always-on ring (matching the real avatar's own ring treatment) plus
 * a hair of scale riding the same pulse gives each circle a little more shape than a flat fill
 * alone, still with no gradient or glow anywhere. */
@Composable
private fun SkeletonAvatar(staggerIndex: Int) {
    val colors = EmberTheme.colors
    val alpha by rememberSkeletonPulse(periodMillis = 760, staggerMillis = staggerIndex * 110)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .graphicsLayer {
                    val scale = 0.94f + alpha * 0.06f
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(CircleShape)
                .border(1.5.dp, colors.border, CircleShape)
                .background(colors.panel),
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(36.dp)
                .height(8.dp)
                .graphicsLayer { this.alpha = alpha }
                .clip(RoundedCornerShape(4.dp))
                .background(colors.panel),
        )
    }
}

/** Horizontally scrollable friend avatars with names: the active friend is slightly larger,
 * friends with an unseen latest photo get a glow ring + glow dot, seen ones a muted ring +
 * gray dot. Ends with an Add button that opens Find People. */
@Composable
private fun FriendAvatarRow(
    viewModel: HomeViewModel,
    activeFriendId: String,
    listState: LazyListState,
    onAvatarClick: (String) -> Unit,
    onAddFriendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(AVATAR_SPACING_DP.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items(viewModel.feedItems, key = { it.friendId }) { item ->
            val isActive = item.friendId == activeFriendId
            val hasUnseen = viewModel.hasUnseenPhoto(item)
            // Drives a crossfade between the ring's two looks (see the layered Boxes below)
            // rather than a hard cut — `.background(brush)` has no built-in way to animate
            // between two different Brushes, so the glow sweep-gradient is its own layer on top
            // of an always-present muted base, faded in/out by this instead.
            // Same duration/easing as the row's own centering scroll (smoothCenterOn) so the
            // resize and the reposition read as one coordinated move, not two mismatched ones.
            val avatarSize by animateDpAsState(
                targetValue = if (isActive) AVATAR_DIAMETER_DP.dp else AVATAR_INACTIVE_DIAMETER_DP.dp,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "avatarSize",
            )
            val unseenAlpha by animateFloatAsState(
                targetValue = if (hasUnseen) 1f else 0f,
                animationSpec = tween(100, easing = FastOutSlowInEasing),
                label = "avatarRingUnseenAlpha",
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(AVATAR_ITEM_WIDTH_DP.dp),
            ) {
                Box(modifier = Modifier.size(AVATAR_DIAMETER_DP.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(avatarSize)) {
                        // Muted ring — always present as the base, so the glow layer above has
                        // something already-correct underneath to dissolve into/out of.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(colors.mutedDim, colors.mutedDim))),
                        )
                        // Glow ring — layered on top, fading in/out via unseenAlpha instead of
                        // snapping, so "just became seen" reads as a settle rather than a flicker.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = unseenAlpha }
                                .clip(CircleShape)
                                .background(Brush.sweepGradient(listOf(colors.glow, colors.glow2, colors.violet, colors.glow))),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                // Thin hairline ring plus an equally thin gap, matching the
                                // story-ring proportion this row is modelled on: the photo should
                                // occupy the large majority of the circle, with the ring reading
                                // as a delicate band around it rather than a heavy border. Both
                                // values are deliberately equal — an uneven pair reads as a
                                // mistake at this size.
                                .padding(AVATAR_RING_WIDTH_DP.dp)
                                .clip(CircleShape)
                                .background(colors.panel)
                                .padding(AVATAR_RING_GAP_DP.dp)
                                .clip(CircleShape)
                                .clickable { onAvatarClick(item.friendId) },
                        ) {
                            // Same treatment as the featured card's own photo (see its call
                            // site's own doc comment) — a plain AsyncImage paints nothing while
                            // loading, leaving this ring's flat colors.panel background showing
                            // through with no indication anything's happening. Tracking the
                            // painter's state directly lets this pulse the same way
                            // SkeletonAvatar already does for the whole-screen loading state,
                            // just for this one avatar's own in-flight request.
                            val avatarPainter = rememberAsyncImagePainter(model = item.photos.last().photoUrl)
                            val avatarPainterState by avatarPainter.state.collectAsState()
                            val isAvatarLoading = when (avatarPainterState) {
                                is AsyncImagePainter.State.Loading, is AsyncImagePainter.State.Empty -> true
                                else -> false
                            }
                            if (isAvatarLoading) {
                                val avatarPulseAlpha by rememberSkeletonPulse(periodMillis = 900)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = avatarPulseAlpha }
                                        .background(colors.panel),
                                )
                            }
                            Image(
                                painter = avatarPainter,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        }
                    }
                }
                // Streak lives on the featured card itself now, not duplicated here too —
                // just the name below each avatar.
                Text(
                    text = item.displayName.substringBefore(" "),
                    fontFamily = typography.body,
                    fontSize = 12.sp,
                    color = if (isActive) colors.cream else colors.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }

        item(key = "add-friend") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(AVATAR_ITEM_WIDTH_DP.dp),
            ) {
                val dashColor = colors.mutedDim
                Box(modifier = Modifier.size(AVATAR_DIAMETER_DP.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(AVATAR_INACTIVE_DIAMETER_DP.dp)
                            .drawBehind {
                                drawCircle(
                                    color = dashColor,
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                                    ),
                                )
                            }
                            .clip(CircleShape)
                            .clickable(onClick = onAddFriendClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Add friend",
                            tint = colors.muted,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Text(
                    text = "Add",
                    fontFamily = typography.body,
                    fontSize = 12.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}
