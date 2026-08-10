package com.ember.app.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ember.app.ui.theme.EmberTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** How far content shifts down while pulling to refresh, shared by every screen that has the
 * gesture (this scaffold's tabs, plus Home's own hand-built version) so they all move by the same
 * amount rather than each hardcoding a number that could drift from the others. */
internal const val PULL_REFRESH_CONTENT_OFFSET_DP = 56

/**
 * The one root shape every bottom-nav tab (Friends, Activity, Settings) is built on: themed
 * background, a fixed [TabScreenHeader] up top, and a single scrollable [LazyColumn] underneath
 * it — never a separate `verticalScroll` Column. That divergence (Settings scrolled its whole
 * page, including the header, in one `verticalScroll` Column, while Friends/Activity kept a
 * fixed header above their own `LazyColumn`) is exactly what let Settings' header drift into the
 * scrollable region while the other two stayed put; routing all three through this one scaffold
 * makes that class of drift structurally impossible instead of a convention to remember.
 *
 * Pull-to-refresh is opt-in: pass [isRefreshing] and [onRefresh] together for a screen that has
 * something to refresh, or leave both at their defaults for one that doesn't (Settings today).
 *
 * [listState] defaults to a plain [rememberLazyListState] scoped to this composition — fine for
 * a screen that's never disposed while the user is elsewhere. A screen that can be navigated away
 * from and back to via a nested screen (Friends, whenever a friend's profile is open) needs its
 * caller to hoist that state instead and pass it in here, the same way MainActivity already
 * hoists Home's own scroll position — otherwise this scaffold's default `remember` is recreated
 * from scratch on every return, silently resetting scroll to the top even though nothing the
 * user did asked for that.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabScreenScaffold(
    title: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    trailing: @Composable (BoxScope.() -> Unit)? = null,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(start = 20.dp, end = 20.dp, bottom = LocalNavDockHeight.current + 24.dp),
    listState: LazyListState = rememberLazyListState(),
    content: LazyListScope.() -> Unit,
) {
    val colors = EmberTheme.colors
    var screenSize by remember { mutableStateOf(Size.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState).statusBarsPadding()) {
            TabScreenHeader(
                title = title,
                trailing = trailing,
            )

            if (onRefresh != null) {
                val pullRefreshState = rememberPullToRefreshState()
                // Same pull-to-refresh treatment Home uses: a plain white spinner rather than
                // Material's stock arrow-into-tinted-container shape, and the content itself
                // visibly shifting down with the pull (Material moves only the indicator, which
                // reads as nothing happening). This scaffold sits below TabScreenHeader already,
                // so the spinner's own top alignment lands in the gap under that header with no
                // header-height math needed, unlike Home's.
                //
                // While a refresh is running the target is a fixed value, not the live pull
                // distance — Material animates that distance itself the moment you let go, so an
                // eased tween aimed at it would just chase a moving target and never actually
                // slow anything down. Every other phase uses snap(): dragging has to track the
                // finger exactly, and the return to rest is already animated by Material.
                val pullOffsetFraction by animateFloatAsState(
                    targetValue = if (isRefreshing) 1f else pullRefreshState.distanceFraction.coerceAtLeast(0f),
                    animationSpec = if (isRefreshing) tween(durationMillis = 420, easing = LinearOutSlowInEasing) else snap(),
                    label = "pullOffsetFraction",
                )
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullRefreshState,
                    indicator = {
                        if (pullOffsetFraction > 0f) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                // Below the content in draw order (PullToRefreshBox otherwise
                                // paints the indicator over the list, so it visibly overlapped the
                                // top rows instead of looking tucked behind the page) — with this,
                                // it only shows in the gap the content's own translationY reveals
                                // as it's pulled down.
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 14.dp)
                                    .size(26.dp)
                                    .graphicsLayer { alpha = pullOffsetFraction.coerceIn(0f, 1f) }
                                    .zIndex(-1f),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { translationY = pullOffsetFraction * PULL_REFRESH_CONTENT_OFFSET_DP.dp.toPx() },
                        contentPadding = contentPadding,
                        content = content,
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding, content = content)
            }
        }
    }
}
