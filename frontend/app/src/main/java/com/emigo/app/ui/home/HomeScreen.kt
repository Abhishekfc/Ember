package com.emigo.app.ui.home

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.emigo.app.R
import com.emigo.app.ui.components.LocalNavDockHeight
import com.emigo.app.ui.components.PULL_REFRESH_CONTENT_OFFSET_DP
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the last photo in the carousel stays before it's marked seen automatically (see the
 * LaunchedEffect that uses this in the carousel branch for why only this page needs a fallback). */
private const val LAST_PHOTO_DWELL_MARK_SEEN_MS = 3000L


/** Where the page rests, as a fraction of [PULL_REFRESH_CONTENT_OFFSET_DP], while a pull-triggered
 * refresh runs. A fixed target for the release easing (see pullOffsetFraction). 1f keeps the
 * spinner fully visible. */
private const val PULL_REFRESH_RESTING_FRACTION = 1f

/** Measures at natural size but reports zero height, so an optional status line floats over what
 * follows instead of pushing it down. Keeps the featured card's position independent of whether
 * any such line is showing. */
private fun Modifier.overlayNoHeight(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, 0) { placeable.placeRelative(0, 0) }
}

/** Home page: header, then the loading / error / empty state or the featured carousel. Memories is
 * its own tab (see [MemoriesTabScreen]). [isPhotoFocused], [onToggleFocus] and [onDismissFocus]
 * live in MainActivity because the shared nav dock must blur in step with tap-to-focus.
 * [scrollState] is hoisted so pull-to-refresh registers even when the content fits on screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCameraClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    onProfileClick: () -> Unit,
    // Bell icon next to the profile avatar; activityBadgeCount is MainActivity's "events since
    // last viewed" count.
    onActivityClick: () -> Unit,
    activityBadgeCount: Int,
    hazeState: HazeState,
    isPhotoFocused: Boolean,
    onToggleFocus: () -> Unit,
    onDismissFocus: () -> Unit,
    scrollState: ScrollState,
    // True when Home is the pager's settled page, i.e. a fresh arrival rather than just being
    // composed while another page is active. Only then may background-synced content become
    // visible (see HomeViewModel.onHomeSessionStart).
    isActive: Boolean = true,
    // Whether this account sent a photo recently (CameraViewModel.lastSentPhotoUrl, the same signal
    // as the Camera outbox button). Independent of the feed on purpose; it only picks the share
    // prompt's text.
    hasSharedRecently: Boolean = false,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }

    // Measured header height, so the featured card's region knows how much room is left at any
    // font scale. 0f for one frame until the first measurement lands.
    var headerHeightPx by remember { mutableStateOf(0f) }

    // Height of just the brand row; the pull-to-refresh spinner parks directly beneath it.
    var brandHeaderHeightPx by remember { mutableStateOf(0f) }

    // Height of the Home/Moments toggle row. Subtracted from the card's available height so the
    // card lands at the same screen position as on Camera, which has no such row.
    var sharePromptHeightPx by remember { mutableStateOf(0f) }

    // Collapse state of the Home/Moments pill: 0f expanded, -sharePromptHeightPx collapsed.
    // Driven by nested scroll because the pill is a sibling of the Moments grid, not part of it.
    // Same approach as Material3's collapsing app bar: claim scroll before the grid consumes it,
    // so one drag collapses the pill and scrolls the grid together.
    var pillHeightOffsetPx by remember { mutableStateOf(0f) }
    val pillCollapseFraction = if (sharePromptHeightPx > 0f) -pillHeightOffsetPx / sharePromptHeightPx else 0f
    val pillCollapseNestedScrollConnection = remember {
        object : NestedScrollConnection {
            // Collapses only (scrolling down through content). Pre-scroll runs before the grid
            // consumes anything, like a collapsing header.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Read from the ViewModel, not a captured val: this object is remembered once and
                // would freeze on the first mode. MOMENTS only; HOME content doesn't scroll (see
                // topFoldMaxHeightDp), so this must ignore the pull-to-refresh drag there.
                if (viewModel.homeViewMode != HomeViewMode.MOMENTS) return Offset.Zero
                if (sharePromptHeightPx <= 0f || available.y >= 0f) return Offset.Zero
                val previousOffset = pillHeightOffsetPx
                pillHeightOffsetPx = (previousOffset + available.y).coerceIn(-sharePromptHeightPx, 0f)
                return Offset(0f, pillHeightOffsetPx - previousOffset)
            }

            // Re-expands only (scrolling back up), from whatever the grid couldn't consume: the
            // grid scrolls back to its top first, then the leftover reopens the pill. Handling
            // both directions in onPreScroll would reopen the pill at the first bit of scroll
            // back toward the top, before the grid had moved.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (viewModel.homeViewMode != HomeViewMode.MOMENTS) return Offset.Zero
                if (sharePromptHeightPx <= 0f || available.y <= 0f) return Offset.Zero
                val previousOffset = pillHeightOffsetPx
                pillHeightOffsetPx = (previousOffset + available.y).coerceIn(-sharePromptHeightPx, 0f)
                return Offset(0f, pillHeightOffsetPx - previousOffset)
            }
        }
    }

    // Roomy or compact spacing for the card+avatar fold, from real available space (see
    // homeFoldMetricsFor). Computed once here because the fold layout and the avatar-centering
    // maths must agree, or every avatar ends up mis-centered.
    val foldMetrics = run {
        val foldDensity = LocalDensity.current
        val statusBarTopPx = WindowInsets.statusBars.getTop(foldDensity)
        val dockHeightPx = with(foldDensity) { LocalNavDockHeight.current.toPx() }

        // Both start at zero until their size callbacks land a frame later. Deciding from a zero
        // header picks the roomy scale on a device that can't afford it, then visibly corrects
        // itself. Home is rebuilt on every return from a nested screen, so the view-mode pill
        // appeared large and shrank each time. So only decide once the inputs are known, and until
        // then reuse the last measured answer (kept on the ViewModel, which outlives this).
        val measurementsReady = screenSize != Size.Zero && headerHeightPx > 0f
        val measured = if (measurementsReady) {
            with(foldDensity) {
                homeFoldMetricsFor(
                    // Does NOT subtract sharePromptHeightPx: the pill size computed here determines
                    // that measurement, so including it would be circular. homeFoldMetricsFor
                    // accounts for the row at a fixed size instead (see its doc comment).
                    availableHeight = (
                        screenSize.height - statusBarTopPx - headerHeightPx - dockHeightPx
                        ).toDp(),
                    screenWidth = screenSize.width.toDp(),
                )
            }
        } else {
            null
        }

        if (measured != null && measured != viewModel.foldMetrics) {
            // A side effect rather than a write during composition; runs after the frame and only
            // when the answer changed, so it can't loop.
            SideEffect { viewModel.setFoldMetrics(measured) }
        }

        // Roomy scale only on the first frame of a cold start; later rebuilds use the remembered answer.
        measured ?: viewModel.foldMetrics ?: HomeFoldRoomy
    }

    // Read from the ViewModel so the mode survives navigating away and back (see HomeViewMode).
    val homeViewMode = viewModel.homeViewMode

    // Switching mode resets the pill to expanded. Otherwise it could stay collapsed with nothing
    // left to scroll, since HOME content doesn't scroll far enough to re-expand it.
    LaunchedEffect(homeViewMode) { pillHeightOffsetPx = 0f }

    // A tapped Moments card grown into a featured overlay. Kept apart from isPhotoFocused, which
    // is HOME's carousel focus and lives in MainActivity for the nav dock blur; this only affects
    // what's drawn here.
    val momentFocusState = remember { MomentFocusState() }
    LaunchedEffect(momentFocusState.isOpen) {
        if (momentFocusState.isOpen) {
            momentFocusState.progress.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        } else {
            momentFocusState.progress.animateTo(0f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            momentFocusState.target = null
        }
    }
    // Back closes an open moment, like tapping outside it, instead of leaving the app.
    BackHandler(enabled = momentFocusState.isOpen) { momentFocusState.isOpen = false }

    // Photo shown by the featured card, hoisted so AmbientPhotoBackdrop (in the outer Box) can read
    // it. Only set while there's a feed; a stale value is harmless since the backdrop is invisible
    // when nothing is focused.
    var currentPhotoUrl by remember { mutableStateOf<String?>(null) }

    // Returning to Home is one of two moments background-synced content may become visible (the
    // other is pull-to-refresh, handled in loadFeed). No-op if nothing has diverged.
    LaunchedEffect(isActive) {
        if (isActive) viewModel.onHomeSessionStart()
    }

    // derivedStateOf so readers recompose only when crossing 0, not on every scrolled pixel. Gates
    // the featured card's auto-advance and tap-to-focus: a pull-to-refresh drag shouldn't
    // auto-advance it mid-gesture.
    val isHomeAtDefaultScrollPosition by remember { derivedStateOf { scrollState.value == 0 } }

    // Local copy of the animation MainActivity drives for the nav dock blur, so the animated value
    // needn't be passed down. Both react to the same isPhotoFocused change.
    val chromeBlur by rememberFocusBlur(isPhotoFocused)
    // Fully hides the chrome that chromeBlur recedes (see rememberFocusFade for why blur alone
    // isn't enough with AmbientPhotoBackdrop).
    val chromeFade by rememberFocusFade(isPhotoFocused)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
    // Behind everything; visible only once the featured card is tapped, replacing the flat
    // background with a wash matching that photo.
    AmbientPhotoBackdrop(
        photoUrl = currentPhotoUrl,
        visible = isPhotoFocused,
        modifier = Modifier.fillMaxSize(),
    )

    // hazeSource covers only this Column (header + content), never the BottomNavDock, or the blur
    // source would include the dock's own pixels and ghost. verticalScroll lets pull-to-refresh
    // register even though the content fits on one screen.
    val pullRefreshState = rememberPullToRefreshState()
    // Material3's PullToRefreshBox moves only the indicator, which felt broken. Here the whole
    // page shifts down with the finger, as a plain per-frame multiple of distanceFraction:
    // - Material3 already animates distanceFraction through every phase; a second animation on
    //   top fought it and caused a dip-then-jump on release.
    // - No resistance curve: a curve made the offset at a deep pull larger than at the settled
    //   refreshing position, so releasing moved the page back up. Linear means wherever the
    //   finger leaves it is the resting position.
    // The one animation of our own is the settle on release while a refresh is running.
    // Material3 would pull the page up at constant speed (an abrupt snap), so a decelerating tween
    // (LinearOutSlowInEasing) softens it. Its target is the fixed PULL_REFRESH_RESTING_FRACTION,
    // not distanceFraction: that value moves every frame, which would leave the easing nothing to
    // slow down. Every other phase uses snap(), since dragging must track the finger exactly and
    // Material3 already animates the return to zero.
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
        // A plain white spinner; Material's stock arrow-to-arc indicator doesn't fit this app and
        // its tint looked wrong on a plain background.
        indicator = {
            if (pullOffsetFraction > 0f) {
                // Parked just under the brand row's measured height: that row is the one thing that
                // doesn't shift down with the content, so the gap opens up right below it. The
                // status bar inset is added separately because this indicator is positioned from
                // the PullToRefreshBox's top edge (the screen top), while the brand row sits inside
                // the content Column's statusBarsPadding(). Without it the indicator landed a
                // status bar too high, over the header.
                val density = LocalDensity.current
                val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
                val brandHeaderHeightDp = with(density) { brandHeaderHeightPx.toDp() }
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    // Drawn below the content (PullToRefreshBox normally paints it on top), so it
                    // only shows in the gap revealed as the content's translationY moves down.
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
                // Disabled while either kind of focus is active: the content behind a focused card
                // isn't interactive anyway, and letting it scroll let a swipe that ran out of
                // photos in Memories' viewer leak into the grid behind it.
                //
                // nestedScroll must come before verticalScroll so the pill gets first claim on the
                // scroll delta to collapse itself before the rest scrolls the page.
                .nestedScroll(pillCollapseNestedScrollConnection)
                // Not gated on homeViewMode == MOMENTS: that also disabled pull-to-refresh on HOME,
                // because PullToRefreshBox needs an enabled scrollable descendant to detect the
                // pull. The HOME-only guard lives in pillCollapseNestedScrollConnection instead.
                .verticalScroll(scrollState, enabled = !isPhotoFocused),
        ) {
            // Header and greeting blur as one block; blurring each piece separately left
            // hard-edged rectangles over the crisp background.
            // Active for both kinds of focus (isPhotoFocused for HOME's viewer,
            // momentFocusState.isOpen for the MOMENTS viewer). Without the second, the pill above
            // stayed tappable and a tap on "Home" switched screens mid-focus.
            FocusShield(
                active = isPhotoFocused || momentFocusState.isOpen,
                onDismiss = { if (momentFocusState.isOpen) momentFocusState.isOpen = false else onDismissFocus() },
            ) {
            Column(
                modifier = Modifier
                    .onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }
                    .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = chromeFade },
            ) {
                // Not part of the translationY shift below: while pulling to refresh this row stays
                // where scrolling put it (it still scrolls away normally).
                HomeBrandHeader(
                    userName = viewModel.userName,
                    profilePhotoUrl = viewModel.profilePhotoUrl,
                    onProfileClick = onProfileClick,
                    onActivityClick = onActivityClick,
                    activityBadgeCount = activityBadgeCount,
                    modifier = Modifier.onGloballyPositioned { brandHeaderHeightPx = it.size.height.toFloat() },
                )

                Column(
                    // The "page moves down with your pull" shift. Covers everything below the brand
                    // row so the spinner appears to emerge from under a row that stays put.
                    modifier = Modifier.graphicsLayer { translationY = pullOffsetFraction * PULL_REFRESH_CONTENT_OFFSET_DP.dp.toPx() },
                ) {
                    // Minimal on purpose: no greeting, name or date. A real connectivity failure still
                    // gets an actionable, tappable message here. The "X new photos" count sits above
                    // the featured card instead, next to what it describes.
                    // overlayNoHeight() so the message floats over what's below and takes no layout
                    // space: a failure starting or ending never moves anything, and CameraScreen's
                    // matching header needn't account for it.
                    val hasConnectionError = viewModel.errorMessage != null
                    val connectionErrorAlpha by animateFloatAsState(
                        targetValue = if (hasConnectionError) 1f else 0f,
                        animationSpec = tween(220),
                        label = "connectionErrorAlpha",
                    )
                    Text(
                        text = stringResource(R.string.home_connect_error),
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

            // Status branches use fixed vertical padding instead of fillMaxSize: the parent Column
            // scrolls (unbounded height), where fillMaxSize collapses to zero.
            //
            // Same pull-to-refresh shift as the Column above, so the featured card and everything
            // below move down as one unit while the brand row stays put.
            //
            // The pill has its own FocusShield so it can't switch HOME/MOMENTS while a photo is
            // focused. Scoped to the pill only: wrapping the card/pager too swallowed the pager's
            // horizontal swipes into the shield's dismiss overlay and broke swiping between photos
            // while one was focused.
            Column(modifier = Modifier.graphicsLayer { translationY = pullOffsetFraction * PULL_REFRESH_CONTENT_OFFSET_DP.dp.toPx() }) {

            // Outside the when{} so it renders in every state. Otherwise the card starts at a
            // different offset per branch (the empty state's card sat higher than the real one)
            // and CameraScreen has no single Home position to match. Switching to MOMENTS only
            // changes the real-feed branch; loading/error/empty look the same in both modes.
            // Collapses and fades as pillCollapseNestedScrollConnection reports scroll. This outer
            // Box shrinks (letting the grid grow into the space, see topFoldMaxHeightDp) while the
            // pill inside keeps its natural height via onGloballyPositioned. clipToBounds hides
            // overflow mid-collapse.
            val pillCollapseDensity = LocalDensity.current
            FocusShield(
                active = isPhotoFocused || momentFocusState.isOpen,
                onDismiss = { if (momentFocusState.isOpen) momentFocusState.isOpen = false else onDismissFocus() },
            ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (sharePromptHeightPx > 0f) {
                            Modifier.height(with(pillCollapseDensity) { (sharePromptHeightPx * (1f - pillCollapseFraction)).toDp() })
                        } else {
                            Modifier
                        },
                    )
                    .clipToBounds(),
            ) {
            HomeViewModeToggleRow(
                mode = homeViewMode,
                onModeChange = { viewModel.setHomeViewMode(it) },
                metrics = foldMetrics,
                modifier = Modifier
                    .fillMaxWidth()
                    // Always measures at natural height, ignoring the wrapping Box's shrinking
                    // constraint. Otherwise the row got squeezed, which fed into
                    // sharePromptHeightPx, which both that Box's height and topFoldMaxHeightDp are
                    // computed from: the measurement, collapse math and grid bound chased each
                    // other every frame. That loop, not the nested-scroll math, made the grid
                    // jitter on scroll.
                    .wrapContentHeight(unbounded = true, align = Alignment.Top)
                    .onGloballyPositioned { sharePromptHeightPx = it.size.height.toFloat() }
                    // Sits between header and card, tighter than the card's gap since the header is the
                    // stronger anchor. Half of toggleTopGap because HomeBrandHeader already has
                    // bottom = 12.dp; the full value stacked into too big a gap. Keep in sync with
                    // HomeViewModeToggleHeightTwin.
                    .padding(top = foldMetrics.toggleTopGap / 2, start = 22.dp, end = 22.dp)
                    .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer {
                        // Shifted up by the amount the Box shrank, so with clipToBounds the pill
                        // slides up behind the fixed header instead of shrinking in place.
                        translationY = -(pillCollapseFraction * sharePromptHeightPx)
                        alpha = chromeFade * (1f - pillCollapseFraction)
                    },
            )
            }
            }

            when {
                // !hasCompletedFirstSync limits this to "never got a real answer yet" (first open,
                // nothing cached). Without it, refreshing an account with zero shared photos
                // re-entered this branch and flashed a "photo coming" skeleton before returning to
                // the empty state, which read as a loading bug.
                viewModel.isLoading && viewModel.feedItems.isEmpty() && !viewModel.hasCompletedFirstSync -> HomeSkeletonLoader(
                    modifier = Modifier.padding(top = 6.dp),
                )

                // No branch for "failed to load and nothing cached" on purpose. One used to replace
                // Home with "Nothing saved on this device yet · Tap to retry", wrong twice over: the
                // account may well have photos, and it swapped the screen for a momentary blip.
                // Being offline isn't a different screen.
                //
                // A connection problem is reported once, by the tappable line in the header
                // (hasConnectionError). A failure falls through to the empty state below, and the
                // real feed replaces it on the next success.
                viewModel.feedItems.isEmpty() -> {
                    // No shared photos yet: the card (with its "Find friends" action) is the whole
                    // story, and Memories is deliberately not shown beside it. Placed in the real
                    // space between header and nav dock (same measurements as topFoldMaxHeightDp),
                    // top-aligned with a fixed gap; centering left a large dead gap under the
                    // divider. Gated on the measurements because they start at zero for one frame,
                    // which would place the card at the top and then jump it down.
                    if (screenSize != Size.Zero && headerHeightPx > 0f && sharePromptHeightPx > 0f) {
                        val density = LocalDensity.current
                        val statusBarPx = WindowInsets.statusBars.getTop(density)
                        val navDockHeightPx = with(density) { LocalNavDockHeight.current.toPx() }
                        // Subtracts the share prompt too, like topFoldMaxHeightDp: that row sits above
                        // every branch, and omitting it would drop the card lower than the real one.
                        val remainingHeightDp = with(density) {
                            (screenSize.height - statusBarPx - headerHeightPx - sharePromptHeightPx - navDockHeightPx).toDp()
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth().heightIn(max = remainingHeightDp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Same constant as the real card; a separate number here put the empty
                            // state's card at a different height.
                            Spacer(modifier = Modifier.height(FEATURED_CARD_TOP_GAP))

                            if (homeViewMode == HomeViewMode.MOMENTS) {
                                MomentsEmptyState(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(start = featuredCardSidePadding(), end = featuredCardSidePadding()),
                                )
                            } else {
                                HomeEmptyStateCard(
                                    onAddFriendClick = onAddFriendClick,
                                    // The card's caption carries this instead of a second text block.
                                    // With friends already added, "once you're connected" would
                                    // describe something already done and make adding a friend seem
                                    // to change nothing.
                                    caption = stringResource(
                                        if (viewModel.friends.isNotEmpty()) R.string.home_card_empty_with_friends else R.string.home_card_empty_no_friends,
                                    ),
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(start = featuredCardSidePadding(), end = featuredCardSidePadding()),
                                )
                            }
                        }
                    }
                }

                else -> {
                    // One continuous carousel over every friend's photos in feed order, not a pager
                    // per friend: swiping past someone's last photo lands on the next friend's first.
                    val entries = remember(viewModel.feedItems) { buildHomeCarousel(viewModel.feedItems) }
                    val pagerState = rememberPagerState(
                        initialPage = pageIndexFor(entries, viewModel.feedItems, viewModel.selectedFriendId, viewModel),
                    ) { entries.size }
                    val avatarListState = rememberLazyListState()
                    val scope = rememberCoroutineScope()
                    val density = LocalDensity.current

                    // Per-friend position is recorded from the settled page. "Seen" lags one step:
                    // when the pager settles on a new page, the PREVIOUS entry is marked, so a photo
                    // turns seen only after the user moves on from it (swipe, avatar tap, or the page
                    // Home opened on). Marking the current page after a short dwell was tried and
                    // rejected: the ring and dots jumped ahead of what was being watched.
                    //
                    // previousEntry isn't reset when entries rebuilds (e.g. a new photo arrives); it
                    // tracks what was on screen a moment ago.
                    //
                    // lastProcessedPage guards a real bug: marking a photo seen updates feedItems,
                    // which rebuilds entries, which is a key of this effect, so it re-ran for the
                    // same settledPage, saw previousEntry pointing at the entry just set, and marked
                    // the photo the user was still viewing. previousEntry now advances only when
                    // settledPage's number changes, not when entries reshuffles.
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

                    // The gap in the rule above: there's no page after the last one to swipe to, so a
                    // friend whose only or last photo lands there would glow as unseen forever
                    // (including a feed of one photo). This dwell-based marking applies only to that
                    // dead-end page; every other photo still clears only once swiped past.
                    LaunchedEffect(pagerState.settledPage, entries) {
                        if (pagerState.settledPage != entries.lastIndex) return@LaunchedEffect
                        val entry = entries.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
                        delay(LAST_PHOTO_DWELL_MARK_SEEN_MS)
                        viewModel.markPhotoSeen(entry.friendId, entry.photo.photoId)
                    }

                    // The avatar row follows the live page so it moves as soon as you cross into
                    // another friend's photos. A settle-based trigger made it lag behind the card.
                    val activeFriendId = entries.getOrNull(pagerState.currentPage)?.friendId
                        ?: viewModel.feedItems.first().friendId

                    // Keeps AmbientPhotoBackdrop in sync with the photo on screen, updating when
                    // scrolling stops rather than on a timer. A fixed delay was wrong both ways: a
                    // single swipe waited for an unrelated timeout, and a fast flick could still
                    // slip a change through. With isScrollInProgress, one swipe updates as soon as
                    // it lands, and a flick through several photos holds the backdrop until it rests.
                    LaunchedEffect(entries) {
                        snapshotFlow { pagerState.isScrollInProgress }
                            .collect { isScrolling ->
                                if (!isScrolling) {
                                    currentPhotoUrl = entries.getOrNull(pagerState.currentPage)?.photo?.photoUrl
                                }
                            }
                    }

                    // The avatar row is blurred while focused so it recedes; letting it keep resizing
                    // underneath would pull attention back. Freeze it on the friend active when focus
                    // began and let it catch up smoothly (same animations, no snap) once focus ends.
                    val frozenFriendId = remember(isPhotoFocused) { activeFriendId }
                    val displayFriendId = if (isPhotoFocused) frozenFriendId else activeFriendId

                    LaunchedEffect(displayFriendId) {
                        val index = viewModel.feedItems.indexOfFirst { it.friendId == displayFriendId }
                        if (index < 0) return@LaunchedEffect
                        // Uses the same foldMetrics the row renders with; a mismatch centers every
                        // avatar slightly off (see AVATAR_ITEM_WIDTH_DP).
                        val itemWidthPx = with(density) { foldMetrics.avatarItemWidth.toPx() }
                        val itemStridePx = with(density) { (foldMetrics.avatarItemWidth + AVATAR_SPACING_DP.dp).toPx() }
                        avatarListState.smoothCenterOn(index, itemStridePx, itemWidthPx)
                    }

                    // On a short screen the card's aspect-ratio height (from width alone) could push
                    // the avatar row into the floating nav dock's zone, where nothing can scroll it
                    // clear. Earlier fixes guessed how much room the header and row need (live
                    // measurement that didn't converge, then fixed dp constants still too tight on
                    // a real device). This instead bounds the card + avatar row Column to the real
                    // remaining space (screen height minus measured status bar, header and the
                    // dock's LocalNavDockHeight) and gives the card weight(1f, fill = false), so
                    // Compose measures the avatar row first and gives the card what's left. It
                    // can't overflow past the dock on any device.
                    val statusBarPx = WindowInsets.statusBars.getTop(density)
                    val navDockHeightPx = with(density) { LocalNavDockHeight.current.toPx() }
                    // screenSize and headerHeightPx start at zero for one frame. Computing
                    // topFoldMaxHeightDp from them gives a wrong (often negative) bound, so the card
                    // and avatar row would render collapsed for a frame and then snap open. Skip
                    // rendering until the measurements are in, so it appears once at its final size.
                    if (screenSize != Size.Zero && headerHeightPx > 0f && sharePromptHeightPx > 0f) {
                    // Scaled by (1 - pillCollapseFraction), not the pill's full height: as the pill
                    // collapses, this fold (and the Moments grid bounded to it) grows to reclaim
                    // the space instead of leaving a dead gap.
                    val topFoldMaxHeightDp = with(density) {
                        (screenSize.height - statusBarPx - headerHeightPx - sharePromptHeightPx * (1f - pillCollapseFraction) - navDockHeightPx).toDp()
                    }
                    // heightIn(max = ...) bounds the region to the real remaining space (screen height
                    // minus status bar, header, this pill and the dock's reserve).
                    //
                    // Gaps are constants and the CARD is the one flexible element (weight(1f,
                    // fill = false)). The earlier version pinned the card to its aspect-ratio height
                    // and let two weight(1f) spacers absorb the rest, which made the screen
                    // device-dependent: gaps grew on tall phones and vanished on short ones, and once
                    // the card outgrew the fold the avatar row went under the dock. Now spacing is
                    // identical on every device and the card shrinks a little on a short screen,
                    // the one thing that can shrink without collisions.
                    if (homeViewMode == HomeViewMode.MOMENTS) {
                        val momentsGridBlur by rememberFocusBlur(momentFocusState.isOpen)
                        val momentsGridFade by rememberFocusFade(momentFocusState.isOpen)
                        // Its own scrollable region, not bounded like the carousel fold: a grid can hold
                        // more friends than fit on screen, while the carousel fold never scrolls.
                        MomentsGrid(
                            feedItems = viewModel.feedItems,
                            onCardClick = { item, bounds ->
                                // Newest first, matching the tile's thumbnail (its last photo): page 0
                                // opens the tapped photo and swiping continues into older ones.
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
                            // FEATURED_CARD_TOP_GAP is the gap HOME mode puts above the featured card
                            // (see its Spacer below); using it makes the grid start in the same place.
                            contentPadding = PaddingValues(
                                start = 22.dp,
                                end = 22.dp,
                                top = FEATURED_CARD_TOP_GAP,
                                bottom = 4.dp,
                            ),
                        )
                    } else {
                    CompositionLocalProvider(LocalHomeFoldMetrics provides foldMetrics) {
                    Column(modifier = Modifier.fillMaxWidth().heightIn(max = topFoldMaxHeightDp)) {
                        Spacer(modifier = Modifier.height(foldMetrics.cardTopGap))

                        FeaturedPhotoCard(
                            entries = entries,
                            pagerState = pagerState,
                            isFocused = isPhotoFocused,
                            isAtDefaultScrollPosition = isHomeAtDefaultScrollPosition,
                            isActive = isActive,
                            // Toggles focus only at the default scroll position; otherwise a tap on a
                            // half-visible card (scrolled a little by pull-to-refresh) blurred the
                            // screen for a tap that didn't land on it.
                            onToggleFocus = { if (scrollState.value == 0) onToggleFocus() },
                            // The fold's only flexible child. fill = false caps the card at the
                            // leftover space without forcing it to use all of it, so on a normal phone
                            // it keeps its natural aspect-ratio height; the cap only bites on a
                            // screen too short for that.
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .align(Alignment.CenterHorizontally)
                                .padding(start = featuredCardSidePadding(), end = featuredCardSidePadding()),
                        )

                        FocusShield(active = isPhotoFocused, onDismiss = onDismissFocus) {
                        FriendAvatarRow(
                            viewModel = viewModel,
                            activeFriendId = displayFriendId,
                            listState = avatarListState,
                            onAvatarClick = { friendId ->
                                // Always that friend's FIRST (newest) photo, not pageIndexFor's remembered
                                // position: auto-advance walks through every friend's photos, so a friend
                                // it already left has its remembered position on their LAST photo. Tapping
                                // an avatar means "show me them", so it shouldn't land where auto-advance
                                // or an earlier swipe left off.
                                val target = entries.indexOfFirst { it.friendId == friendId }.coerceAtLeast(0)
                                scope.launch { pagerState.animateScrollToPage(target) }
                            },
                            onAddFriendClick = onAddFriendClick,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = foldMetrics.avatarRowTopGap, bottom = foldMetrics.avatarRowBottomGap)
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
        }

    // Rendered from the screen's full-size Box, like Memories' overlay: inside a scrollable column
    // the height is unbounded, so "centered on the device screen" would be meaningless there.
    momentFocusState.target?.let { target ->
        val overlayDensity = LocalDensity.current
        // The same top offset HOME mode's FeaturedPhotoCard sits at (status bar, header, toggle
        // row, fixed gap), not centered like Memories' version. Home's card is pinned to the top
        // of its fold by a fixed Spacer, so a centered destination could never match. Uses the
        // same measurements as the fold, so it can't drift.
        val sidePaddingPx = with(overlayDensity) { featuredCardSidePadding().toPx() }
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


/** Wraps [content] with an invisible overlay while [active], so nothing inside (avatar taps, nav
 * buttons, profile chip, even the avatar row's scrolling) reacts while blurred. It's a sibling on
 * top rather than a modifier, so it wins over any click or scroll handling the content has. A tap
 * anywhere closes focus, like tapping the card; a drag is absorbed. */
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

/** A heavily blurred backdrop in the shade of the photo the featured card is focused on (the
 * "blurred album art" look). It reuses the photo already decoded for the sharp card (Coil serves
 * it from cache) instead of computing an average color. [visible] fades it in and out over the
 * flat background on focus.
 *
 * A change to [photoUrl] crossfades immediately; whoever sets it only does so once the pager has
 * stopped scrolling, so no extra delay is needed here.
 *
 * Real blur needs RenderEffect (API 31+). Below that this renders nothing, since an unblurred
 * photo behind everything would look like a bug. */
@Composable
internal fun AmbientPhotoBackdrop(photoUrl: String?, visible: Boolean, modifier: Modifier = Modifier) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val backdropAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(360, easing = FastOutSlowInEasing),
        label = "ambientBackdropVisibility",
    )
    // Stop rendering once fully faded out and unwanted (not whenever alpha is 0), so the fade-out
    // still plays.
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
                        // Scaled past the blur radius so the soft edge a heavy blur leaves at the
                        // image border sits offscreen.
                        .graphicsLayer { scaleX = 1.15f; scaleY = 1.15f }
                        .blur(72.dp, BlurredEdgeTreatment.Unbounded),
                )
            }
        }
        // Darkened so it reads as ambient lighting, not a second copy of the photo.
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
    }
}
