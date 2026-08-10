package com.ember.app.ui.home

import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.ember.app.R
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.PhotoEntryDto
import com.ember.app.ui.components.LocalNavDockHeight
import com.ember.app.ui.components.PULL_REFRESH_CONTENT_OFFSET_DP
import com.ember.app.ui.theme.CourgetteFontFamily
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.EmberTypography
import com.ember.app.ui.theme.PublicSansFontFamily
import com.ember.app.ui.theme.ThemeKey
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the very last photo in the whole carousel dwells before it's marked seen on its
 * own — see the LaunchedEffect using this in HomeScreen's carousel branch for why this one
 * specific page needs a fallback the rest of the carousel doesn't. */
private const val LAST_PHOTO_DWELL_MARK_SEEN_MS = 3000L


/** Where the page rests, as a fraction of [PULL_REFRESH_CONTENT_OFFSET_DP], while a pull-triggered
 * refresh is actually running — the fixed destination the release settle eases toward (see its
 * call site for why a fixed value, rather than the live pull distance, is what makes that easing
 * take effect at all). 1f leaves the whole offset open, so the spinner stays fully visible. */
private const val PULL_REFRESH_RESTING_FRACTION = 1f

/** Measures this content at its own real, natural size and paints it there — but reports zero
 * height to whatever is arranging it, so an optional status line built this way visually floats
 * over the background/whatever comes right after it instead of pushing that content down while
 * it's showing (and back up the instant it's gone). Exists specifically so the featured card's own
 * position never depends on whether any of these lines happens to be visible right now — no
 * separate reserved height for them at all, on either this screen or CameraScreen's own matching
 * header (which no longer needs to account for any of these lines, having never rendered them
 * to begin with). */
private fun Modifier.overlayNoHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, 0) { placeable.placeRelative(0, 0) }
}

/** Home's own page — greeting header, then whichever of loading / error / empty / the featured
 * carousel applies. Memories is a separate tab of its own now (see [MemoriesTabScreen]), not
 * embedded in this screen's scroll. [isPhotoFocused]/[onToggleFocus]/[onDismissFocus] are hoisted
 * all the way up to MainActivity, not owned here — the shared nav dock (also hoisted there, since
 * Home, Memories, Friends, Camera and Settings are all pages of one outer swipeable pager) needs
 * to blur in step with this screen's own tap-to-focus, and it sits outside whatever any individual
 * page is doing internally. [scrollState] is hoisted the same way, so pull-to-refresh can still
 * register even though a plain Column here would otherwise fit its content in one screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCameraClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    onProfileClick: () -> Unit,
    // Activity moved out of the bottom nav dock into a bell icon right here, next to the profile
    // avatar — see NavDestination.ACTIVITY's own doc comment for why. activityBadgeCount is the
    // exact same "events since last viewed" count MainActivity already computes for the dock's
    // own badge before this move; this button just renders it in its new spot.
    onActivityClick: () -> Unit,
    activityBadgeCount: Int,
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
    // Whether this account has actually sent anyone a photo recently (see MainActivity's own call
    // site: this reads CameraViewModel.lastSentPhotoUrl, the exact same "has an active, unsent
    // outbox photo" signal the Camera tab's own outbox button uses) — entirely independent of
    // this screen's own feed state on purpose. Sharing your own moment and viewing friends' don't
    // gate each other; this is only ever used to pick the sharing prompt's own two lines of text.
    hasSharedRecently: Boolean = false,
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

    // HomeBrandHeader's own real height — just that row, not the combined block headerHeightPx
    // above tracks — used only to park the pull-to-refresh spinner directly beneath it (see the
    // indicator's own modifier further down).
    var brandHeaderHeightPx by remember { mutableStateOf(0f) }

    // HomeViewModeToggleRow's own real height — Camera has no equivalent row before its own card,
    // so subtracting this from the card+avatar-row fold's own available height (see its own call
    // site further down) is what keeps the card landing at the same absolute screen position on
    // both screens, instead of Home's card sitting lower just because this row exists above it.
    var sharePromptHeightPx by remember { mutableStateOf(0f) }

    // Which of Home's two views is showing — read from the ViewModel (see HomeViewMode's own doc
    // comment for why local Compose state here isn't enough to survive navigating away and back).
    val homeViewMode = viewModel.homeViewMode

    // A tapped Moments grid card, grown into its own featured overlay — see MomentFocusState's
    // own doc comment for why this stays a separate, locally-hoisted state rather than reusing
    // isPhotoFocused (that one is HOME's own carousel-focus state, hoisted all the way to
    // MainActivity because the shared nav dock needs to blur in step with it; this one only ever
    // affects what's rendered inside this composable, so it has no reason to live any higher).
    val momentFocusState = remember { MomentFocusState() }
    LaunchedEffect(momentFocusState.isOpen) {
        if (momentFocusState.isOpen) {
            momentFocusState.progress.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        } else {
            momentFocusState.progress.animateTo(0f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            momentFocusState.target = null
        }
    }
    // Swiping back (or pressing back) while a moment is open closes it, the same way tapping
    // outside it does — not fall through to the system and back out of the app.
    BackHandler(enabled = momentFocusState.isOpen) { momentFocusState.isOpen = false }

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

    // derivedStateOf, not a plain `scrollState.value == 0` read — scrollState.value changes every
    // frame of a scroll, but this derived boolean only actually changes twice (crossing 0 in
    // either direction), so whatever reads it only recomposes on those two occasions instead of
    // every scrolled pixel. Still used to gate the featured card's own auto-advance/tap-to-focus
    // (see its own call site) even without Memories to scroll into any more — a pull-to-refresh
    // drag is enough on its own to justify not auto-advancing mid-gesture.
    val isHomeAtDefaultScrollPosition by remember { derivedStateOf { scrollState.value == 0 } }

    // Local copy of the same animation MainActivity's own chromeBlur (which blurs the shared nav
    // dock) drives, kept as two instances so this screen doesn't need the animated Dp threaded in
    // as its own parameter — both react to the same isPhotoFocused transition in the same
    // recomposition, so they never actually drift out of lockstep with each other.
    val chromeBlur by rememberFocusBlur(isPhotoFocused)
    // Fully hides the same chrome chromeBlur recedes — see rememberFocusFade's own doc comment
    // for why blur alone isn't enough once AmbientPhotoBackdrop is in the picture too.
    val chromeFade by rememberFocusFade(isPhotoFocused)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
    // Sits behind everything else in this Box, only actually visible once Home's own featured
    // card is tapped — replacing the flat background above with a wash matching that photo.
    AmbientPhotoBackdrop(
        photoUrl = currentPhotoUrl,
        visible = isPhotoFocused,
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
                    // Below the content in draw order (PullToRefreshBox otherwise paints the
                    // indicator over the content, so it visibly overlapped the header/card
                    // instead of looking tucked behind the page) — with this, it only shows in
                    // the gap the content's own translationY reveals as it's pulled down.
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = statusBarDp + brandHeaderHeightDp + 14.dp)
                        .size(26.dp)
                        .graphicsLayer { alpha = pullOffsetFraction.coerceIn(0f, 1f) }
                        .zIndex(-1f),
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .statusBarsPadding()
                // Disabled while either kind of focus is active — background content the user
                // can't currently interact with anyway (everything but the focused card is
                // behind FocusShield, or blurred behind Memories' own day-card) never needed to
                // keep scrolling underneath it either, and letting it scroll is what let a swipe
                // that ran out of photos in Memories' viewer leak into scrolling the grid behind it.
                .verticalScroll(scrollState, enabled = !isPhotoFocused),
        ) {
            // Header + greeting blur as one contiguous block instead of three separately
            // blurred pieces — blurring each element on its own left visible hard-edged
            // rectangles floating over crisp background between them, which read as broken
            // rather than as one soft, de-emphasized backdrop.
            FocusShield(active = isPhotoFocused, onDismiss = onDismissFocus) {
            Column(
                modifier = Modifier
                    .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }
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
                    onActivityClick = onActivityClick,
                    activityBadgeCount = activityBadgeCount,
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
                    // Deliberately minimal — no greeting, name, or date here any more. A real
                    // connectivity failure is still worth a functional message (with its own retry
                    // affordance) right in the brand row's own spot — not a greeting/status this
                    // line's minimalism is about removing, but something actionable the user
                    // otherwise has no way to know happened. The "X new photos" count itself now
                    // lives just above the featured card instead (see its own call site further
                    // down) — right next to the thing it's actually describing, rather than up
                    // here under a wordmark it has nothing to do with.
                    // overlayNoHeight(), not a conditional composable and not even a reserved-
                    // but-faded line — this floats over whatever's underneath it (the top of the
                    // fold/card region below) rather than claiming any layout space of its own, so
                    // a connection failure starting or ending never moves anything else on this
                    // screen, and CameraScreen's own header twin doesn't need to account for it at
                    // all (it never did contribute real height for Camera to match in the first
                    // place).
                    val hasConnectionError = viewModel.errorMessage != null
                    val connectionErrorAlpha by animateFloatAsState(
                        targetValue = if (hasConnectionError) 1f else 0f,
                        animationSpec = tween(220),
                        label = "connectionErrorAlpha",
                    )
                    Text(
                        text = "Couldn't connect · Tap to retry",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.muted,
                        maxLines = 1,
                        modifier = Modifier
                            .overlayNoHeight()
                            .padding(top = 10.dp, start = 22.dp, end = 22.dp)
                            .graphicsLayer { alpha = connectionErrorAlpha }
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

            // Outside the when{} below, so it renders in every state rather than only alongside a
            // real feed — it means the card beneath it starts from the same offset in every
            // branch, with this inside the feed branch only, the empty state's card sat higher
            // than the real one, and CameraScreen had no single Home position to match against.
            // Switching to MOMENTS only actually changes anything inside the real-feed branch
            // below; the other branches (loading/error/empty) render the same regardless of mode.
            HomeViewModeToggleRow(
                mode = homeViewMode,
                onModeChange = { viewModel.setHomeViewMode(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { sharePromptHeightPx = it.size.height.toFloat() }
                    // top = 20.dp, not 12 — this row sat 18dp below the header but 27dp above the
                    // card (FEATURED_CARD_TOP_GAP), a lopsided gap that read as "stuck to the
                    // header" rather than sitting evenly between the two things it separates.
                    // Still a touch less than the card's own gap, since the header is the
                    // stronger anchor of the two, but close enough now to actually read as
                    // centered in its own slot.
                    .padding(top = 20.dp, start = 22.dp, end = 22.dp)
                    .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = chromeFade },
            )

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
                    // No one's shared anything yet — the card itself (with its own "Find
                    // friends" action) is the whole story here. Memories deliberately doesn't
                    // show alongside it any more — explicitly asked for, so the empty state
                    // reads as one clear moment/action instead of a card stacked on top of an
                    // unrelated grid. Placed in the real remaining space between the header and
                    // the nav dock (same screenSize/headerHeightPx/LocalNavDockHeight measurements
                    // the real-feed branch's own topFoldMaxHeightDp bound uses below), top-aligned
                    // with a fixed gap under the divider rather than truly centered — true
                    // centering (tried first) put equal empty space above *and* below the card,
                    // which read as a large dead gap right under the divider; any slack now
                    // collects below the card instead, near the dock, rather than splitting it
                    // both places. Gated the same way that bound is: screenSize/headerHeightPx
                    // both start at zero for one frame, and placing content inside a zero-height
                    // box would put the card at the very top anyway, then visibly jump down once
                    // the real measurements land.
                    if (screenSize != Size.Zero && headerHeightPx > 0f && sharePromptHeightPx > 0f) {
                        val density = LocalDensity.current
                        val statusBarPx = WindowInsets.statusBars.getTop(density)
                        val navDockHeightPx = with(density) { LocalNavDockHeight.current.toPx() }
                        // Subtracts the share prompt too, exactly like the real-feed branch's own
                        // topFoldMaxHeightDp — that row now sits above every branch, so leaving it
                        // out here would size this fold as if it weren't there and drop the card
                        // lower than the real one.
                        val remainingHeightDp = with(density) {
                            (screenSize.height - statusBarPx - headerHeightPx - sharePromptHeightPx - navDockHeightPx).toDp()
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = remainingHeightDp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // The same constant the real card uses, rather than this branch's own
                            // separate number — that mismatch is what put the empty state's card
                            // at a different height from the real one.
                            Spacer(modifier = Modifier.height(FEATURED_CARD_TOP_GAP))

                            if (homeViewMode == HomeViewMode.MOMENTS) {
                                MomentsEmptyState(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(start = FEATURED_CARD_SIDE_PADDING, end = FEATURED_CARD_SIDE_PADDING),
                                )
                            } else {
                                HomeEmptyStateCard(
                                    onAddFriendClick = onAddFriendClick,
                                    // The card's own caption carries this rather than a second
                                    // block of text underneath it: with friends already added,
                                    // "once you're connected" is describing something that has
                                    // already happened, which is what made the screen read as if
                                    // adding a friend had changed nothing.
                                    caption = if (viewModel.friends.isNotEmpty()) {
                                        "Nothing shared yet. Their photos land here the moment they post, or you can send the first one."
                                    } else {
                                        "Once you're connected, their photos show up right here."
                                    },
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(start = FEATURED_CARD_SIDE_PADDING, end = FEATURED_CARD_SIDE_PADDING),
                                )
                            }
                        }
                    }
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
                    if (screenSize != Size.Zero && headerHeightPx > 0f && sharePromptHeightPx > 0f) {
                    val topFoldMaxHeightDp = with(density) {
                        (screenSize.height - statusBarPx - headerHeightPx - sharePromptHeightPx - navDockHeightPx).toDp()
                    }
                    // heightIn(max = ...) bounds this whole region to the real remaining space
                    // (screenSize, minus the status bar, minus the header's own real measured
                    // height, minus this share prompt's own real measured height, minus the
                    // dock's reserve).
                    //
                    // Every gap in here is a plain constant and the CARD is the one flexible
                    // element (weight(1f, fill = false)) — the exact inverse of the earlier
                    // version, which pinned the card to its natural aspect-ratio height and let
                    // two weight(1f) spacers soak up whatever was left. That older shape is what
                    // made this screen device-dependent in both of the ways reported: the gaps
                    // themselves grew on a tall phone and collapsed to zero on a short one, and
                    // once the fixed card was taller than the fold could hold there was nothing
                    // left to give, so the avatar row got pushed under the dock. Flipping which
                    // element flexes fixes both at once — spacing is now identical on every
                    // device, and the card simply renders a little smaller when a screen is too
                    // short to give it its full aspect-ratio height, which is the one thing here
                    // that can shrink without anything colliding.
                    if (homeViewMode == HomeViewMode.MOMENTS) {
                        val momentsGridBlur by rememberFocusBlur(momentFocusState.isOpen)
                        val momentsGridFade by rememberFocusFade(momentFocusState.isOpen)
                        // Its own scrollable region, not bounded the same way the carousel's fold
                        // is — a grid can hold more friends than fit on screen at once, where the
                        // carousel fold is deliberately capped to never need to scroll.
                        MomentsGrid(
                            feedItems = viewModel.feedItems,
                            onCardClick = { item, bounds ->
                                // Newest-first, matching the grid tile's own thumbnail (its last
                                // photo) — landing on page 0 opens exactly the photo that was
                                // tapped, then continuing into older photos on swipe.
                                val orderedPhotos = item.photos.asReversed()
                                momentFocusState.target = MomentFocusTarget(
                                    friendId = item.friendId,
                                    displayName = item.displayName,
                                    photos = orderedPhotos,
                                    initialPage = 0,
                                    originBounds = bounds,
                                )
                                momentFocusState.isOpen = true
                            },
                            focusedFriendId = momentFocusState.target?.friendId,
                            focusProgress = momentFocusState.progress.value,
                            modifier = Modifier
                                .height(topFoldMaxHeightDp)
                                .blur(momentsGridBlur, BlurredEdgeTreatment.Unbounded)
                                .graphicsLayer { alpha = momentsGridFade },
                            // top = FEATURED_CARD_TOP_GAP, not a separately-guessed smaller
                            // value — that's the exact gap HOME mode's own Column puts above the
                            // featured card (see its Spacer just below), and this grid needs the
                            // same one so it starts at the same place the card does instead of
                            // sitting closer to the pills above it.
                            contentPadding = PaddingValues(
                                start = 22.dp,
                                end = 22.dp,
                                top = FEATURED_CARD_TOP_GAP,
                                bottom = 4.dp,
                            ),
                        )
                    } else {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = topFoldMaxHeightDp)) {
                        Spacer(modifier = Modifier.height(FEATURED_CARD_TOP_GAP))

                        FeaturedPhotoCard(
                            entries = entries,
                            pagerState = pagerState,
                            isFocused = isPhotoFocused,
                            isAtDefaultScrollPosition = isHomeAtDefaultScrollPosition,
                            isActive = isActive,
                            // Only actually toggles focus when Home is scrolled all the way to its
                            // default resting position — without this, tapping the card while it's
                            // only half-visible (scrolled a little for the pull-to-refresh gesture)
                            // still blurred the whole screen for a tap that didn't really land on it.
                            onToggleFocus = { if (scrollState.value == 0) onToggleFocus() },
                            // The fold's only flexible child. fill = false matters: it caps the
                            // card at the space actually left over without forcing it to consume
                            // all of it, so on a normal-height phone the card still takes exactly
                            // its natural aspect-ratio height and looks unchanged — the cap only
                            // ever bites on a screen too short to fit that.
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .align(Alignment.CenterHorizontally)
                                .padding(start = FEATURED_CARD_SIDE_PADDING, end = FEATURED_CARD_SIDE_PADDING),
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
                                .padding(top = AVATAR_ROW_TOP_GAP, bottom = AVATAR_ROW_BOTTOM_GAP)
                                .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                                .graphicsLayer { alpha = chromeFade },
                        )
                        }
                    }
                    }
                    }

                }
            }
            }
        }
        }

    // Rendered from this screen's own true full-screen Box, same reasoning as Memories' own
    // overlay: a container nested inside a scrollable column measures with an unbounded height,
    // so "centered against the full device screen" from in there would be meaningless.
    momentFocusState.target?.let { target ->
        val overlayDensity = LocalDensity.current
        // The exact same top offset HOME mode's own FeaturedPhotoCard sits at — statusBar, then
        // the real measured header, then the real measured toggle row, then the fixed gap above
        // the card — rather than centering this overlay in the leftover screen space the way
        // Memories' own version does. Centering was what made it land somewhere else entirely:
        // Home's card isn't actually centered in its fold, it's pinned to the top of it by a
        // fixed Spacer, so a centered destination here could never match it. Computed from the
        // same real measurements the fold itself uses, so it can't drift out of sync with where
        // the real card actually is.
        val sidePaddingPx = with(overlayDensity) { FEATURED_CARD_SIDE_PADDING.toPx() }
        val topGapPx = with(overlayDensity) { FEATURED_CARD_TOP_GAP.toPx() }
        val statusBarPx = WindowInsets.statusBars.getTop(overlayDensity).toFloat()
        val cardWidthPx = (screenSize.width - sidePaddingPx * 2).coerceAtLeast(1f)
        val cardHeightPx = cardWidthPx / FEATURED_CARD_ASPECT_RATIO
        val destTop = statusBarPx + headerHeightPx + sharePromptHeightPx + topGapPx
        val destRect = Rect(sidePaddingPx, destTop, sidePaddingPx + cardWidthPx, destTop + cardHeightPx)

        MomentFeaturedOverlay(
            target = target,
            destRect = destRect,
            progress = momentFocusState.progress.value,
            onDismiss = { momentFocusState.isOpen = false },
        )
    }
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
internal fun AmbientPhotoBackdrop(photoUrl: String?, visible: Boolean, modifier: Modifier = Modifier) {
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

/** The "Emigo" wordmark + [ProfileChip], as their own row — split out from the rest of Home's
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
    onActivityClick: () -> Unit,
    activityBadgeCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            // top = 0.dp — sits flush right against the status bar (via the parent's own
            // statusBarsPadding()), no extra gap above it.
            .padding(top = 0.dp, start = 22.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Emigo",
            fontFamily = CourgetteFontFamily,
            fontSize = 34.sp,
            letterSpacing = (-0.5).sp,
            // Plain neutral cream, not the theme accent — the accent color read as distracting
            // here. Size and tighter tracking alone carry the "this is the brand mark" distinction
            // instead.
            color = colors.cream,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            ActivityBellButton(badgeCount = activityBadgeCount, onClick = onActivityClick)
            ProfileIconButton(
                onClick = onProfileClick,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Activity, moved here from its old bottom-nav-dock slot (see NavDestination.ACTIVITY's own doc
 * comment) — the same "bell next to your own avatar" spot notifications sit in on most apps,
 * rather than a full tab of its own. A small panel-toned circle behind the glyph now (not a bare
 * icon any more) — deliberately matches [ProfileIconButton] right next to it, so the pair of
 * top-right controls reads as one consistent unit. The numbered badge keeps the exact same pill
 * BottomNavDock's own badge already uses, on top of (not instead of) that background. */
@Composable
private fun ActivityBellButton(badgeCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors

    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 44.dp, the same as the outer touch-target Box — the background circle now fills it
        // completely rather than sitting inset within it, so the icon reads bigger without the
        // outer Box (and therefore this Row's own measured height) growing at all.
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = "Activity", tint = colors.cream, modifier = Modifier.size(24.dp))
        }
        AnimatedVisibility(
            visible = badgeCount > 0,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-2).dp, y = 4.dp),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.glow)
                    .padding(horizontal = 3.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    fontFamily = PublicSansFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentText,
                )
            }
        }
    }
}

/** A static structural twin of the real header block above ([HomeBrandHeader] alone — the
 * "Couldn't connect" line beneath it and the unseen-count banners further down both float over
 * their own surroundings via [overlayNoHeight] now rather than claiming any real layout space, so
 * neither one is part of what this twin needs to reproduce any more) that
 * [com.ember.app.ui.camera.CameraScreen] renders invisibly so its own card can reserve exactly the
 * same vertical space Home's header occupies, without depending on a value only Home's own render
 * can produce. That live cross-screen dependency (this screen reporting its measured height up to
 * MainActivity, Camera reading it back down) was the actual bug behind the card visibly landing
 * too high the first time the app opened (Camera is the app's own opening page — nothing had
 * reported a real height yet, so the read-back value started at zero) and then jumping down the
 * first time Home was ever visited (the real measurement finally arrived). Rendering this twin
 * locally inside Camera's own composition sidesteps the dependency entirely rather than patching
 * around its timing. */
@Composable
internal fun HomeHeaderHeightTwin() {
    HomeBrandHeader(
        userName = null,
        profilePhotoUrl = null,
        onProfileClick = {},
        onActivityClick = {},
        activityBadgeCount = 0,
    )
}

/** The same idea as [HomeHeaderHeightTwin], for the row below it: Home has a
 * [HomeViewModeToggleRow] between its header and its featured card, and CameraScreen has nothing
 * in that position. That difference alone is what made Camera's card sit higher than Home's
 * despite both reserving an identical header height — so Camera renders this invisibly to reserve
 * the missing space and put its own card at the exact same absolute position Home's lands at.
 *
 * Built from the real composable with placeholder values, and carrying the same padding its real
 * call site in [HomeScreen] uses, so it can't drift out of sync the way a hand-copied dp constant
 * would the first time that row's type or spacing is touched. */
@Composable
internal fun HomeViewModeToggleHeightTwin() {
    HomeViewModeToggleRow(
        mode = HomeViewMode.HOME,
        onModeChange = {},
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 22.dp, end = 22.dp),
    )
}

/** Small circular chip in the header showing the signed-in user's own profile photo (falling
 * back to their initial if they haven't set one), with an ember-orange presence dot. */
@Composable
// A plain profile glyph, not the account's own photo — deliberately matches ActivityBellButton
// right next to it (same 44dp touch target, same 36dp panel-toned background circle, same icon
// size/tint) rather than a photo avatar that reads as a visually different kind of control.
internal fun ProfileIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors

    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Person, contentDescription = "Your profile", tint = colors.cream, modifier = Modifier.size(24.dp))
        }
    }
}

// Must match FriendAvatarRow's actual avatar Column width and horizontalArrangement spacing —
// there's no way to derive these from the LazyRow itself without them already being visible,
// which is exactly the problem with the built-in animateScrollToItem this replaces.
/** The slot each avatar occupies. The avatar itself still grows/shrinks within it to mark the
 * active friend (see avatarSize below) — this is just the fixed box that keeps the row's own
 * layout steady while that animates. */
private const val AVATAR_DIAMETER_DP = 90

/** The non-active avatars' diameter — they animate down to this, and back up to
 * [AVATAR_DIAMETER_DP] when they become the active one. The two are deliberately close: the gap
 * between them is what marks the active friend, and a larger one turns a subtle emphasis into the
 * row visibly jolting on every swipe. */
private const val AVATAR_INACTIVE_DIAMETER_DP = 84

/** The ring band and the gap between it and the photo. Equal by design — see their use site. */
private const val AVATAR_RING_WIDTH_DP = 3.0f
private const val AVATAR_RING_GAP_DP = 3.0f

/** Must match the real width of each avatar column below — smoothCenterOn derives its scroll
 * target from this, so a value that disagrees with the actual layout centers every avatar
 * slightly off (this was 72 while the column has always measured 84). Tracks
 * [AVATAR_DIAMETER_DP] with a small margin, so it has to move whenever that does. */
private const val AVATAR_ITEM_WIDTH_DP = 96

/** The horizontal gap between avatars — was a cramped 4dp originally, tightly packing every
 * circle against its neighbor with no breathing room; 10dp then read as too loose, 6 still a
 * bit much. */
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

/** Home's two views: the single-friend carousel (with the avatar row below it, today's default),
 * or a grid showing every friend's own card at once. Switched via [HomeViewModeToggleRow]. Lives
 * on [HomeViewModel] (see its own `homeViewMode` property), not as local Compose state here —
 * this composable is torn down and rebuilt every time the pager scrolls Home out of view and back
 * (unlike the ViewModel, which is hoisted in MainActivity and survives that), so a plain
 * `remember` reset back to HOME on every return visit. */
internal enum class HomeViewMode { HOME, MOMENTS }

/** Two independent pill controls, not a single connected segmented control — each one owns its
 * own selected/unselected look (filled neutral chip vs. plain outline) rather than one track with
 * a sliding indicator. A fixed identical width for both, rather than each sizing to its own
 * label's natural width — "Moments" is a longer word than "Home", and letting them size
 * independently made the two visibly different sizes, which read as one primary and one
 * secondary control rather than two equal options. Sits in the exact slot the old share-prompt
 * row used to occupy — see [HomeViewModeToggleHeightTwin] for why CameraScreen still reserves an
 * identical amount of space here even though it renders neither pill itself. */
@Composable
private fun HomeViewModeToggleRow(
    mode: HomeViewMode,
    onModeChange: (HomeViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        HomeViewModePill(
            label = "Home",
            selected = mode == HomeViewMode.HOME,
            onClick = { onModeChange(HomeViewMode.HOME) },
        )
        HomeViewModePill(
            label = "Moments",
            selected = mode == HomeViewMode.MOMENTS,
            onClick = { onModeChange(HomeViewMode.MOMENTS) },
        )
    }
}

/** No icon, by design — text alone at a confident weight/size carries this rather than needing a
 * glyph to reinforce it. No theme accent color either — selected is a filled neutral chip
 * (panel, the same tone the featured card's own dot-row/badges sit against), unselected a plain
 * outline; the distinction is fill vs. outline and cream vs. muted text, not a color swap. */
@Composable
private fun HomeViewModePill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    Box(
        modifier = modifier
            .width(118.dp)
            .clip(EmberRadii.buttonShape)
            .then(
                if (selected) {
                    Modifier.background(colors.panel)
                } else {
                    Modifier.border(1.dp, colors.border, EmberRadii.buttonShape)
                },
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            // 11.dp, not 9 — a touch more substantial/confident without changing the pill's
            // width or color, the two things already settled on.
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = PublicSansFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.1).sp,
            color = if (selected) colors.cream else colors.muted,
        )
    }
}

/** A grid alternative to the single-friend carousel — one card per friend, their latest photo
 * plus name, two per row. Tapping a card is a shortcut into HOME mode already centered on that
 * friend, the same way tapping the featured card grows it — see [MomentFocusState] and
 * [MomentFeaturedOverlay] for that. */
@Composable
private fun MomentsGrid(
    feedItems: List<FeedItem>,
    onCardClick: (FeedItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    // Which card, if any, the featured overlay is currently grown out of, and how far open it
    // currently is (0 = fully closed/overlay gone, 1 = fully open). See MomentGridCard's own
    // comment for why this specific card's own label needs to know this.
    focusedFriendId: String? = null,
    focusProgress: Float = 0f,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        gridItems(feedItems, key = { it.friendId }) { item ->
            MomentGridCard(
                displayName = item.displayName,
                photoUrl = item.photos.lastOrNull()?.photoUrl,
                onClick = { bounds -> onCardClick(item, bounds) },
                // Inverse of the overlay's own opening progress, and ONLY for the one card the
                // overlay actually grew out of — every other card is unaffected (revealAlpha
                // defaults to 1, its normal resting state).
                revealAlpha = if (item.friendId == focusedFriendId) 1f - focusProgress else 1f,
            )
        }
    }
}

/** A quieter echo of the featured card's own recipe (rounded corners, bottom scrim, white name
 * label) at a much smaller size — reads as clearly related to it rather than a different kind of
 * tile, which matters since tapping one grows it into that same card (see [MomentFeaturedOverlay]).
 * elevatedPanel (not plain panel) behind the image for the same reason the featured card uses it:
 * this is the one surface on this grid, and it should visibly outrank the flat background behind
 * it. Reports its own real on-screen bounds on tap (the same [boundsInRoot] technique the Memories
 * grid's own tiles use) — the origin the featured overlay grows out of.
 *
 * [revealAlpha] is what actually fixes the "name pops in instantly" report: this tile sits
 * underneath the featured overlay the whole time it's open (the overlay has an opaque background
 * and, at every point during the transform, fully covers this tile's own position), so this
 * tile's own label was always sitting at full opacity, just hidden — invisible until the exact
 * frame the overlay is removed, at which point it's suddenly the only thing there, at full
 * strength already. There is no way to cross-fade something that was never becoming visible
 * gradually in the first place; the fix is to make this tile's own label actually fade in across
 * that same handoff, driven by the exact same progress value the overlay's own fade-out uses (see
 * the call site in MomentsGrid), so the two mathematically meet in the middle instead of one
 * cutting off before the other has faded in at all. */
@Composable
private fun MomentGridCard(
    displayName: String,
    photoUrl: String?,
    onClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    revealAlpha: Float = 1f,
) {
    val colors = EmberTheme.colors
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // No entrance fade here — every card shows immediately, full strength, the moment the grid
    // itself appears (switching tabs into Moments). revealAlpha is the only thing that ever hides
    // this label, and only for the one card an overlay is currently open on: that overlay draws
    // its own copy of this exact label (see MomentCardContent) and cross-fades it, so leaving
    // this one drawn underneath would just double it up behind an opaque card for no reason —
    // and, at the moment the overlay is removed, hand back to a label that never moved while the
    // overlay's copy had been scaling the whole time.
    val labelAlpha = revealAlpha
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .onGloballyPositioned { coordinates = it }
            .clip(RoundedCornerShape(EmberRadii.card))
            .background(colors.elevatedPanel)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { coordinates?.let { onClick(it.boundsInRoot()) } },
            ),
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .alpha(labelAlpha)
                .background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = displayName,
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/** A tapped Moments card, remembered together with the on-screen bounds of the grid tile that was
 * tapped (the origin the featured overlay grows out of, and shrinks back into on dismiss), that
 * friend's own photos newest-first, and which page to land on — always 0, since the grid tile
 * already shows the newest photo and the overlay should open on exactly what was tapped. */
private data class MomentFocusTarget(
    val friendId: String,
    val displayName: String,
    val photos: List<PhotoEntryDto>,
    val initialPage: Int,
    val originBounds: Rect,
)

/** Same shape as Memories' own [MemoryFocusState] — a hoisted target plus an open flag plus a
 * shared grow/shrink [Animatable], kept local to [HomeScreen] rather than promoted to a shared
 * type, since the two focus targets carry different data (a whole memories history vs. one
 * friend's own photos) and have no other reason to share a type. */
private class MomentFocusState {
    var target by mutableStateOf<MomentFocusTarget?>(null)
    var isOpen by mutableStateOf(false)
    val progress = Animatable(0f)
}

/** Just the photo. Deliberately only the image and nothing else: this is what goes *inside*
 * [MomentFeaturedOverlay]'s pager, so anything rendered here slides horizontally with every
 * swipe. The name and scrim belong to the card, not to an individual photo, so they live outside
 * the pager (see [MomentCardLabels]) exactly the way Home's own [FeaturedPhotoCard] arranges
 * them — having had them in here made the label and gradient slide away with each swipe. */
@Composable
private fun MomentPhotoImage(
    displayName: String,
    photo: PhotoEntryDto,
    cardWidthPx: Float,
    cardHeightPx: Float,
    context: Context,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        // Pinned to the card's final settled pixel size rather than Coil's default (which
        // follows the composable's own animating size and would re-request on every
        // grow/shrink frame).
        model = remember(photo.photoUrl, cardWidthPx, cardHeightPx) {
            ImageRequest.Builder(context)
                .data(photo.photoUrl)
                .size(cardWidthPx.roundToInt(), cardHeightPx.roundToInt())
                .build()
        },
        contentDescription = displayName,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

/** The card's scrim and name, rendered once over the pager rather than per-page. */
@Composable
private fun MomentCardLabels(
    displayName: String,
    photo: PhotoEntryDto,
    // 1f = fully open, 0f = exactly grid-tile size/position.
    progress: Float,
    // How large this card currently is relative to its fully-open size (1f = fully open, ~0.47f
    // at grid-tile size). The open-style label is scaled by this: the name is a fixed 24sp, so in
    // a box shrinking to roughly half-width it stayed physically the same size while everything
    // around it got smaller, reading as enormous by the end.
    contentScale: Float,
    typography: EmberTypography,
    modifier: Modifier = Modifier,
) {
    // Both label treatments are rendered, cross-faded against each other, rather than one fading
    // out to nothing. This is what actually removes the pop at the end of the shrink: the overlay
    // is opaque, so the real grid tile underneath is completely hidden for the whole transition —
    // its own label can't be seen arriving, and the instant the overlay is removed that label
    // appears at full strength. Fading the open-style label to zero didn't help, because the two
    // don't match anyway (24sp display font plus a time line, versus the tile's 14sp PublicSans
    // name; different paddings; different gradients), so the swap was always visible. Ending the
    // transition on a label that IS the tile's, pixel for pixel, makes removing the overlay a
    // no-op instead of a change.
    val openAlpha = progress
    val tileAlpha = 1f - progress

    Box(modifier = modifier.fillMaxSize()) {
        // The open card's full-height scrim.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(openAlpha)
                .background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)),
                ),
        )

        // The grid tile's own scrim — a short band hugging the bottom, deliberately not the same
        // shape or opacity as the one above, which is exactly why it has to be cross-faded in
        // rather than reusing the open card's.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .alpha(tileAlpha)
                .background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Same size/font/weight/padding as MomentGridCard's own label, so at progress 0 this
            // is indistinguishable from the tile the overlay is about to hand off to.
            Text(
                text = displayName,
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                // Anchored to the bottom-left corner it already sits in, so shrinking pulls it
                // toward that corner (where the tile's own label lives) rather than toward the
                // middle of the card. Placed before padding() so the padding scales with it too —
                // otherwise the text would shrink while its inset from the edges stayed fixed,
                // which looks just as wrong as not scaling at all.
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    transformOrigin = TransformOrigin(0f, 1f)
                }
                .alpha(openAlpha)
                .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
        ) {
            Text(
                text = displayName,
                fontFamily = typography.display,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                text = formatRelativeTime(photo.createdAt),
                fontFamily = typography.body,
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** A friend's photo, grown out of the Moments grid tile that was tapped, into the exact same card
 * recipe Home's own [FeaturedPhotoCard] and Memories' own `MemoryFeaturedOverlay` already use —
 * same corner radius, same aspect ratio, same container-transform grow/shrink, same bottom-scrim
 * name label. This is the "complete different viewing experience" Moments needed instead of
 * bouncing back to HOME mode on every tap: opening a friend's moment now stays inside Moments,
 * dismissing back into the grid tile it came from rather than switching pages. Deliberately reuses
 * this app's one existing "grow a tile into a featured card" language rather than inventing a
 * fourth look — that shared language is what makes each individual use of it read as premium
 * rather than improvised. */
@Composable
private fun MomentFeaturedOverlay(
    target: MomentFocusTarget,
    destRect: Rect,
    progress: Float,
    onDismiss: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val context = LocalContext.current

    // Landing on the tapped photo, but continuing into that friend's older photos on swipe rather
    // than stopping dead at the one tile that was tapped.
    val pagerState = rememberPagerState(initialPage = target.initialPage) { target.photos.size }
    // Whichever photo the pager has actually settled on — the label sits outside the pager, so it
    // reads this rather than being handed a page.
    val currentPhoto = target.photos.getOrElse(pagerState.currentPage) { target.photos[target.initialPage] }

    // Same reasoning as FeaturedPhotoCard's own boundary: this pager and the grid behind it are
    // both potential recipients of a drag that runs out of pages to turn — without this, swiping
    // past this friend's first/last photo lets that drag bubble down into the grid's own scroll.
    val cardNestedScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val cardWidthPx = destRect.width
        val cardHeightPx = destRect.height
        val currentRect = lerp(target.originBounds, destRect, progress)
        val cardCornerRadius = lerp(EmberRadii.card, FEATURED_CARD_CORNER_RADIUS, progress)
        val cardShape = RoundedCornerShape(cardCornerRadius)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Box(
            modifier = Modifier
                // One measure-and-place pass in raw pixels, not a separate .offset{}+.size(dp) —
                // rounding each independently is a much bigger fraction of the box's size right at
                // the tile-sized end of the animation, and reads as a jitter there.
                .layout { measurable, _ ->
                    val widthPx = currentRect.width.roundToInt().coerceAtLeast(0)
                    val heightPx = currentRect.height.roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
                    layout(widthPx, heightPx) {
                        placeable.placeRelative(currentRect.left.roundToInt(), currentRect.top.roundToInt())
                    }
                }
                .nestedScroll(cardNestedScrollBoundary)
                .clip(cardShape)
                .background(colors.elevatedPanel)
                // Tapping the open photo closes it, matching Home's own featured card.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            // How big the card currently is relative to fully-open — drives the label's own
            // scale so the text physically shrinks along with the container instead of sitting
            // inside it at a fixed 24sp (which is what made it look enormous by the end of the
            // shrink, then snap to the tile's 14sp in a single frame).
            val contentScale = if (destRect.width > 0f) currentRect.width / destRect.width else 1f


            // The tapped grid tile always shows this friend's NEWEST photo (target.photos[0]) —
            // if you swipe to an older one and then dismiss, the pager is still sitting on that
            // older photo right up until the instant this whole overlay is removed, at which
            // point the real tile underneath (always showing the newest) suddenly replaces it —
            // a hard content swap, not a shrink. This crossfades to the newest photo's own
            // image+scrim+label *during* the shrink instead, so by the time the box disappears it
            // already looks identical to the tile it's handing off to. Only rendered at all once
            // you've actually swiped away from the newest photo — most opens never touch this.
            val isOnNewestPhoto = pagerState.currentPage == 0
            val closingCrossfadeAlpha = if (isOnNewestPhoto) 0f else (1f - progress) * (1f - progress)

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                MomentPhotoImage(
                    displayName = target.displayName,
                    photo = target.photos[page],
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    context = context,
                )
            }

            if (closingCrossfadeAlpha > 0f) {
                MomentPhotoImage(
                    displayName = target.displayName,
                    photo = target.photos[0],
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    context = context,
                    modifier = Modifier.alpha(closingCrossfadeAlpha),
                )
            }

            // Outside the pager — the name and scrim belong to the card, not to any one photo, so
            // they stay put while the photos slide underneath them. Same arrangement Home's own
            // FeaturedPhotoCard uses.
            MomentCardLabels(
                displayName = target.displayName,
                photo = currentPhoto,
                progress = progress,
                contentScale = contentScale,
                typography = typography,
            )
        }
    }
}

/** The large featured card — one continuous pager across every friend's photos (see
 * [buildHomeCarousel]), so swiping past someone's last photo lands on the next friend's first
 * and back past a first photo lands on the previous friend's last, without any special-casing:
 * it's all just paging through one flattened list. Memories is its own bottom-nav tab (see
 * [MemoriesTabScreen]), not part of this screen at all any more. */
@Composable
private fun FeaturedPhotoCard(
    entries: List<HomeCarouselEntry>,
    pagerState: PagerState,
    isFocused: Boolean,
    isAtDefaultScrollPosition: Boolean,
    // Whether Home is the page actually on screen right now — gates the auto-advance timer below,
    // which otherwise keeps cycling this card while the user is on a different tab entirely.
    isActive: Boolean,
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

    // isActive is in here, and in the guard below, for a reason that isn't obvious: without it
    // this timer keeps running while Home isn't even the page on screen. Swipe to Friends, and
    // four seconds later this quietly advances the card you're not looking at — and, worse, does
    // it through a 900ms crossfade that paints the outgoing photo at full opacity. Come back
    // mid-crossfade and you land on the previous photo dissolving into the current one, which is
    // exactly the instant flicker-on-return this looked like. A card nobody can see has no reason
    // to be advancing at all.
    LaunchedEffect(entries.size, isFocused, isAtDefaultScrollPosition, isActive) {
        if (entries.size <= 1 || isFocused || !isAtDefaultScrollPosition || !isActive) {
            // Never leave a half-finished crossfade behind when this stops — otherwise the
            // outgoing photo stays painted over the real one until some later advance clears it.
            outgoingPhotoUrl = null
            outgoingAlpha.snapTo(0f)
            return@LaunchedEffect
        }
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
        // Deliberately NOT fillMaxWidth() before aspectRatio. fillMaxWidth pins minWidth to
        // maxWidth, which leaves aspectRatio no room to satisfy a bounded max *height* — it can
        // only ever derive height from width, so on a screen too short for that height the card
        // overflowed its parent instead of fitting. Without it, aspectRatio derives from width
        // when that fits (every normal-height phone — identical result to before) and falls back
        // to deriving width from the available height when it doesn't, which is what lets the
        // card shrink proportionally on a short screen rather than pushing the avatar row out.
        modifier = modifier
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

            // Null for a friend's newest photo (it never expires, see HomeCarouselEntry — no
            // countdown needed). For an older one, this is when its own 24-hour grace period
            // actually runs out: 24 hours after the photo that superseded it arrived, not from
            // this photo's own send time — see buildHomeCarousel's isFriendsNewest/
            // indexWithinFriend ordering (0 = newest) for why the "successor" lookup goes to
            // indexWithinFriend - 1 rather than + 1.
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
                    // A photo still in its 24-hour grace period gets a small countdown instead of
                    // the usual relative-time text — the newest photo needs no explanation (it's
                    // just "there"), but a photo that's actually about to disappear should say so,
                    // rather than reading identically to one that isn't going anywhere.
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
}

/** Shown in place of the featured card when this account has no friends yet, or has friends but
 * has never received a photo from one ([feedItems.isEmpty()] covers both — see this composable's
 * own call site). Same shape/slot the real featured card occupies
 * ([FEATURED_CARD_ASPECT_RATIO]/[FEATURED_CARD_CORNER_RADIUS]) — Memories doesn't render
 * alongside this any more (removed per explicit feedback), so there's no cramping concern that
 * would call for a shorter card, and this reads as "the card that's normally here, standing in
 * for one" instead of a dead end.
 * `home_empty_backdrop` is a cropped still of `frontend/assets/homeEmptyStateCollage.png` —
 * blurred and darkened here in code (not baked into the asset) so both stay tunable. A bottom-
 * weighted gradient, not a flat scrim, is what actually fixes "the image isn't visible" — a flat
 * 45%-black wash on top of an already-dark night-time photo collage crushed it to near-black
 * everywhere; fading from fully transparent at the top to dark only where the text actually sits
 * is the same treatment [FeaturedPhotoCard]'s own bottom scrim already uses. */
/** Moments with nothing in it yet. Shows the shape of what the grid will be, four empty cards
 * in the same two-column layout with the same corners and surface the real ones use, so the pill
 * visibly switches to a different view instead of repeating Home's card. The name strip is drawn
 * on each placeholder too, since that is the part that makes them read as friend cards rather
 * than generic boxes. */
@Composable
private fun MomentsEmptyState(modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(2) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(EmberRadii.card))
                                .background(colors.panel),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                                    .width(52.dp)
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.mutedDim.copy(alpha = 0.45f)),
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "A card for every friend",
            fontFamily = typography.body,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.cream,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
        Text(
            // Says what will be here, not what to do with it. The earlier version ended on "tap
            // any card to open it full size", which is an instruction for cards that don't exist
            // yet on the one screen where there is nothing to tap.
            text = "Once your friends start sharing, their newest photo shows up here.",
            fontFamily = typography.body,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 16.dp, end = 16.dp),
        )
    }
}

@Composable
private fun HomeEmptyStateCard(
    onAddFriendClick: () -> Unit,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val cardShape = RoundedCornerShape(FEATURED_CARD_CORNER_RADIUS)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(FEATURED_CARD_ASPECT_RATIO)
                .clip(cardShape)
                .background(colors.elevatedPanel),
        ) {
            Image(
                painter = painterResource(R.drawable.home_empty_backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(8.dp, BlurredEdgeTreatment.Unbounded),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 26.dp),
            ) {
                Text(
                    text = "Add friends to start\nsharing moments",
                    fontFamily = typography.display,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 29.sp,
                    color = Color.White,
                )
                Row(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White)
                        .clickable(onClick = onAddFriendClick)
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Find friends",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                }
            }
        }
        Text(
            text = caption,
            fontFamily = typography.body,
            fontSize = 12.5.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
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
    // FeedItem itself carries no profile-photo field (see HomeViewModel.friends' own doc comment
    // for why) — this is the one lookup every avatar below reads from instead.
    val profilePhotoByFriendId = remember(viewModel.friends) {
        viewModel.friends.associateBy({ it.friendId }, { it.profilePhotoUrl })
    }

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
                        // Citrus's own glow2/violet are both a muddy orange (see its theme
                        // definition), so the usual multi-stop sweep just looked like a smeared
                        // yellow-to-orange ring rather than a clean color — a plain solid yellow
                        // reads better there than forcing that theme through the same gradient
                        // every other theme's more distinct glow/glow2/violet trio actually suits.
                        val streakRingBrush = if (EmberTheme.key == ThemeKey.CITRUS) {
                            Brush.linearGradient(listOf(colors.glow, colors.glow))
                        } else {
                            Brush.sweepGradient(listOf(colors.glow, colors.glow2, colors.violet, colors.glow))
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = unseenAlpha }
                                .clip(CircleShape)
                                .background(streakRingBrush),
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
                            // This friend's own account profile photo — not item.photos.last(),
                            // their most recently *sent* content photo. Showing whatever they just
                            // shared here (a screenshot, a receipt, anything) made this ring change
                            // identity along with their feed instead of staying a stable "this is
                            // them" marker, which is what a friends list's own avatar should be.
                            val profilePhotoUrl = profilePhotoByFriendId[item.friendId]
                            if (profilePhotoUrl != null) {
                                // Same treatment as the featured card's own photo (see its call
                                // site's own doc comment) — a plain AsyncImage paints nothing while
                                // loading, leaving this ring's flat colors.panel background showing
                                // through with no indication anything's happening. Tracking the
                                // painter's state directly lets this pulse the same way
                                // SkeletonAvatar already does for the whole-screen loading state,
                                // just for this one avatar's own in-flight request.
                                val avatarPainter = rememberAsyncImagePainter(model = profilePhotoUrl)
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
                            } else {
                                // No profile photo set — same "first initial" fallback every other
                                // avatar-less spot in this app (ProfileIconButton, RecipientAvatarStack)
                                // already uses, rather than silently falling back to their content
                                // photo again.
                                Box(modifier = Modifier.fillMaxSize().background(colors.panel), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = item.displayName.firstOrNull()?.uppercase() ?: "•",
                                        fontFamily = typography.display,
                                        fontSize = 19.sp,
                                        color = colors.cream,
                                    )
                                }
                            }
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
