package com.ember.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.layout.onSizeChanged
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
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.data.remote.dto.PhotoEntryDto
import com.ember.app.ui.theme.CourgetteFontFamily
import com.ember.app.ui.theme.EmberTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Roughly the floating nav dock's own visual footprint (button size + its internal padding +
 * its inset from the true screen bottom) — kept as one named constant, reused everywhere content
 * needs to clear it, rather than a fixed padding number copied at each call site. Internal (not
 * private) so MemoriesScreen's day-featured overlay can reserve the same space when centering
 * itself against the full screen. */
internal const val NAV_DOCK_RESERVE_DP = 110

/** How far the user needs to scroll before the nav dock's Home icon has fully morphed into the
 * Memories one. Lives here (`internal`, not `private`) rather than in MainActivity, since it's
 * really describing this screen's own Memories section, not the dock itself. */
internal const val MEMORIES_REVEAL_SCROLL_DP = 220

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
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }

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

    // Home's own featured card must stay sharp while it's the thing actually in focus, but
    // should recede like everything else once Memories' day-card is what's focused instead —
    // otherwise it sat there perfectly crisp, sandwiched between an already-blurred header above
    // and an already-blurred avatar row below it.
    val cardBlurWhenMemoriesFocused by rememberFocusBlur(isMemoriesFocused)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
    // hazeSource is scoped to this Column only (header + content) — it must never wrap
    // the shared BottomNavDock (now hoisted up to MainActivity, outside every page), or the
    // blur source would include the dock's own pixels and produce a ghosting artifact.
    // verticalScroll is what lets the pull-to-refresh gesture register even though the
    // content itself fits on one screen.
    PullToRefreshBox(
        // Tracks isPullRefreshing specifically, not the general isLoading — loadFeed() also
        // runs silently in the background (e.g. right after sending a photo, so streaks and
        // the feed stay current), and that shouldn't pop this spinner in since the user never
        // actually pulled down for it.
        isRefreshing = viewModel.isPullRefreshing,
        onRefresh = { viewModel.loadFeed(isPullRefresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
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
                modifier = Modifier.blur(chromeBlur, BlurredEdgeTreatment.Unbounded),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 26.dp, start = 22.dp, end = 22.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Ember",
                        fontFamily = CourgetteFontFamily,
                        fontSize = 30.sp,
                        color = colors.cream,
                    )
                    ProfileChip(
                        name = viewModel.userName,
                        photoUrl = viewModel.profilePhotoUrl,
                        onClick = onProfileClick,
                    )
                }

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
                val unseenCount = viewModel.feedItems.count { viewModel.hasUnseenPhoto(it) }
                val hasConnectionError = viewModel.errorMessage != null
                Text(
                    text = buildAnnotatedString {
                        if (hasConnectionError) {
                            append("Couldn't connect")
                        } else if (unseenCount > 0) {
                            withStyle(SpanStyle(color = colors.glow)) { append("$unseenCount") }
                            append(if (unseenCount == 1) " photo is" else " photos are")
                            append(" glowing for you")
                        } else {
                            append("You're all caught up")
                        }
                    },
                    fontFamily = typography.display,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                    color = colors.cream,
                    modifier = Modifier.padding(top = 24.dp, start = 22.dp, end = 22.dp),
                )
                // "Tap to retry" folds into this existing line rather than adding a new one —
                // same reasoning as above, just the other half of it: the identity/date line was
                // never in conflict with a connection error, so it's the natural place for the
                // one new fact (there's a way to fix this) that error state actually adds.
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

            // These status branches use fixed vertical padding instead of fillMaxSize because
            // the parent Column is now scrollable (unbounded height), where fillMaxSize
            // collapses to zero.
            when {
                viewModel.isLoading && viewModel.feedItems.isEmpty() -> Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 200.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.glow)
                }

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
                        revealProgress = { 1f },
                        onMemoriesFocusChanged = { isMemoriesFocused = it },
                        modifier = Modifier.padding(top = 6.dp),
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

                    // The avatar row tracks the *live* page, same as the card — it should move
                    // the instant you cross into a new friend's photos, not wait for the swipe
                    // to fully settle. A settle-based trigger was tried and made the row visibly
                    // lag behind the card after every swipe; reacting live keeps the two in step.
                    val activeFriendId = entries.getOrNull(pagerState.currentPage)?.friendId
                        ?: viewModel.feedItems.first().friendId

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

                    // Measured (not guessed) so the very first screenful — before any scroll —
                    // shows only the card and avatar row, cleanly, with nothing from Memories
                    // peeking in underneath the floating nav dock. A version of this that
                    // measured the card/avatar row and stretched a spacer to fill the exact
                    // remaining screen height was tried and reverted — it "worked" in the sense
                    // that Memories always started right at the fold, but on any screen where the
                    // card didn't already fill most of it, that turned into a large dead gap of
                    // empty background, which read as more broken than the original crowding did.
                    // A modest fixed gap doesn't chase device-height precision, but doesn't
                    // produce a gap that scales into that kind of empty space either.
                    // Plain text lines, flush with the "Hey Abhishek · date" line directly above
                    // them (same 22dp start, same 5dp line gap the greeting-to-date transition
                    // already uses) — no icon/dot leading them, since that offset the text start
                    // away from every other line in this header block and read as crooked rather
                    // than a clean continuation of it. Reads as a natural third line of the
                    // header, not a separate inserted element.
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
                                .clickable { viewModel.revealNewFeed() },
                        )
                    }

                    // A connection error no longer gets its own line here — it now takes over the
                    // header's own hero line above (see the hasConnectionError branch there) so
                    // it doesn't sit in contradiction next to a "you're all caught up" claim the
                    // app can no longer verify. Nothing further needed in this specific spot.

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
                            .blur(cardBlurWhenMemoriesFocused, BlurredEdgeTreatment.Unbounded),
                    )

                    FocusShield(active = isPhotoFocused, onDismiss = onDismissFocus) {
                    FriendAvatarRow(
                        viewModel = viewModel,
                        activeFriendId = displayFriendId,
                        listState = avatarListState,
                        onAvatarClick = { friendId ->
                            // Always that friend's FIRST (newest) photo, deliberately not
                            // pageIndexFor's remembered position — auto-advance walks forward
                            // through every friend's photos on its own timer, so by the time it's
                            // moved on to someone else, the friend it just left has its remembered
                            // position sitting on their LAST photo. Tapping a friend's avatar is a
                            // deliberate "show me them" action; it should never land on whatever
                            // auto-advance (or an earlier manual swipe) happened to leave behind.
                            val target = entries.indexOfFirst { it.friendId == friendId }.coerceAtLeast(0)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        },
                        onAddFriendClick = onAddFriendClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, bottom = 12.dp)
                            .blur(chromeBlur, BlurredEdgeTreatment.Unbounded),
                    )
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
                        revealProgress = memoriesRevealProgress,
                        onMemoriesFocusChanged = { isMemoriesFocused = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
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
    revealProgress: () -> Float,
    onMemoriesFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        MemoriesSectionLabel(
            modifier = Modifier
                .padding(start = 22.dp, end = 22.dp, bottom = 14.dp)
                .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer { alpha = revealProgress() },
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
                .graphicsLayer { alpha = revealProgress() }
                .padding(bottom = NAV_DOCK_RESERVE_DP.dp),
        )
    }
}

/** "Memories" label with a small, slowly bobbing chevron beside it — the discoverability cue
 * that there's a scrollable section below, sitting in the normal content flow (not a floating
 * overlay) so it reads as part of the page rather than a separate control. The bob is the one
 * deliberately animated detail here; everything else about this label is static. */
@Composable
private fun MemoriesSectionLabel(modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    val infiniteTransition = rememberInfiniteTransition(label = "memoriesChevronBob")
    val bobFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "memoriesChevronBobFraction",
    )

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Memories",
            fontFamily = typography.display,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.2).sp,
            color = colors.cream,
        )
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.muted,
            modifier = Modifier
                .padding(start = 2.dp)
                .size(20.dp)
                .graphicsLayer { translationY = bobFraction * 4.dp.toPx() },
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

private fun daypart(): String {
    val hour = java.time.LocalDateTime.now().hour
    return when {
        hour < 12 -> "morning"
        hour < 17 -> "afternoon"
        else -> "evening"
    }
}

/** Small circular chip in the header showing the signed-in user's own profile photo (falling
 * back to their initial if they haven't set one), with an ember-orange presence dot. */
@Composable
internal fun ProfileChip(name: String?, photoUrl: String?, onClick: () -> Unit) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Box(modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Your profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = name?.firstOrNull()?.uppercase() ?: "•",
                    fontFamily = typography.display,
                    fontSize = 17.sp,
                    color = colors.cream,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(12.dp)
                .clip(CircleShape)
                .background(colors.glow),
        )
    }
}

// Must match FriendAvatarRow's actual avatar Column width and horizontalArrangement spacing —
// there's no way to derive these from the LazyRow itself without them already being visible,
// which is exactly the problem with the built-in animateScrollToItem this replaces.
private const val AVATAR_ITEM_WIDTH_DP = 72
private const val AVATAR_SPACING_DP = 18

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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(FEATURED_CARD_ASPECT_RATIO)
            .nestedScroll(cardNestedScrollBoundary)
            .shadow(20.dp, cardShape, ambientColor = colors.glow.copy(alpha = 0.35f), spotColor = colors.glow.copy(alpha = 0.35f))
            .clip(cardShape)
            .background(colors.panel)
            // A plain tap (not a swipe) toggles focus mode — no ripple, since the blur
            // transition on the rest of the screen already reads as the tap's feedback.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleFocus,
            ),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            val entry = entries[page]
            AsyncImage(
                model = entry.photo.photoUrl,
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
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(current.totalForFriend) { index ->
                        val isUnseen = friendEntries.getOrNull(index)?.photo?.seen == false
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
                                .width(16.dp)
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
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items(viewModel.feedItems, key = { it.friendId }) { item ->
            val isActive = item.friendId == activeFriendId
            val hasUnseen = viewModel.hasUnseenPhoto(item)
            // Same duration/easing as the row's own centering scroll (smoothCenterOn) so the
            // resize and the reposition read as one coordinated move, not two mismatched ones.
            val avatarSize by animateDpAsState(
                targetValue = if (isActive) 66.dp else 58.dp,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "avatarSize",
            )
            // Drives a crossfade between the ring's two looks (see the layered Boxes below)
            // rather than a hard cut — `.background(brush)` has no built-in way to animate
            // between two different Brushes, so the glow sweep-gradient is its own layer on top
            // of an always-present muted base, faded in/out by this instead.
            val unseenAlpha by animateFloatAsState(
                targetValue = if (hasUnseen) 1f else 0f,
                animationSpec = tween(100, easing = FastOutSlowInEasing),
                label = "avatarRingUnseenAlpha",
            )
            val dotColor by animateColorAsState(
                targetValue = if (hasUnseen) colors.glow else colors.mutedDim,
                animationSpec = tween(100, easing = FastOutSlowInEasing),
                label = "avatarDotColor",
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(72.dp),
            ) {
                Box(modifier = Modifier.size(66.dp), contentAlignment = Alignment.Center) {
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
                                .padding(2.5.dp)
                                .clip(CircleShape)
                                .background(colors.panel)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .clickable { onAvatarClick(item.friendId) },
                        ) {
                            AsyncImage(
                                model = item.photos.last().photoUrl,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(colors.panel)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
                Text(
                    text = item.displayName.substringBefore(" "),
                    fontFamily = typography.body,
                    fontSize = 12.sp,
                    color = if (isActive) colors.cream else colors.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 7.dp),
                )
                // Same "hide a zero streak" rule as the Friends list — a badge with nothing
                // to say isn't worth showing.
                if (item.streak > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 1.dp)) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = colors.glow,
                            modifier = Modifier.size(11.dp),
                        )
                        Text(
                            text = "${item.streak}",
                            fontFamily = typography.body,
                            fontSize = 10.5.sp,
                            color = colors.glow,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
        }

        item(key = "add-friend") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(72.dp),
            ) {
                val dashColor = colors.mutedDim
                Box(modifier = Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
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
                            modifier = Modifier.size(22.dp),
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
