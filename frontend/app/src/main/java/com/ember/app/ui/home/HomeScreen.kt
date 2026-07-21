package com.ember.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.ember.app.ui.components.BottomNavDock
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.CourgetteFontFamily
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (NavDestination) -> Unit,
    onCameraClick: () -> Unit,
    onAddFriendClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val hazeState = rememberHazeState()

    // Tapping the featured photo throws everything else out of focus so the photo itself is
    // the only sharp thing on screen — tapping again brings the rest back. Starting a swipe on
    // the card (set further down, from the pager's own scroll state) also turns focus on, so
    // browsing through photos stays distraction-free without blur flickering on and off between
    // swipes — only the tap toggles it back off.
    var isPhotoFocused by remember { mutableStateOf(false) }
    val chromeBlur by animateDpAsState(
        targetValue = if (isPhotoFocused) 16.dp else 0.dp,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "chromeBlur",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        // hazeSource is scoped to this Column only (header + content) — it must never wrap
        // the BottomNavDock sibling below, or the blur source would include the dock's own
        // pixels and produce a ghosting artifact. verticalScroll is what lets the pull-to-
        // refresh gesture register even though the content itself fits on one screen.
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
                .verticalScroll(rememberScrollState()),
        ) {
            // Header + greeting blur as one contiguous block instead of three separately
            // blurred pieces — blurring each element on its own left visible hard-edged
            // rectangles floating over crisp background between them, which read as broken
            // rather than as one soft, de-emphasized backdrop.
            FocusShield(active = isPhotoFocused, onDismiss = { isPhotoFocused = false }) {
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
                val unseenCount = viewModel.feedItems.count { viewModel.hasUnseenPhoto(it) }
                Text(
                    text = buildAnnotatedString {
                        if (unseenCount > 0) {
                            withStyle(SpanStyle(color = colors.glow)) { append("$unseenCount") }
                            append(if (unseenCount == 1) " photo is" else " photos are")
                            append(" glowing for you")
                        } else {
                            append("You're all caught up")
                        }
                    },
                    fontFamily = typography.display,
                    fontSize = 24.sp,
                    color = colors.cream,
                    modifier = Modifier.padding(top = 24.dp, start = 22.dp, end = 22.dp),
                )
                Text(
                    text = viewModel.userName?.substringBefore(" ")?.let { "Hey $it · ${viewModel.dateText}" } ?: viewModel.dateText,
                    fontFamily = typography.body,
                    fontSize = 12.5.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 5.dp, start = 22.dp, end = 22.dp),
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

                viewModel.errorMessage != null -> Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = viewModel.errorMessage.orEmpty(),
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

                viewModel.feedItems.isEmpty() -> EmptyFeedState(
                    onCameraClick = onCameraClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, start = 22.dp, end = 22.dp),
                )

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

                    // Seen-state and per-friend position are recorded off the *settled* page —
                    // you have to actually stop on a photo for it to count as seen, not just
                    // graze past it mid-drag — but that's a data-correctness choice, separate
                    // from how the avatar row visually reacts (below).
                    LaunchedEffect(pagerState.settledPage, entries) {
                        val entry = entries.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
                        if (entry.friendId != viewModel.selectedFriendId) {
                            viewModel.selectFriend(entry.friendId)
                        }
                        viewModel.setPhotoIndex(entry.friendId, entry.indexWithinFriend)
                        if (entry.isFriendsNewest) {
                            viewModel.markLatestSeen(entry.friendId, entry.photo.photoId)
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

                    FeaturedPhotoCard(
                        entries = entries,
                        pagerState = pagerState,
                        onToggleFocus = { isPhotoFocused = !isPhotoFocused },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, start = 22.dp, end = 22.dp),
                    )

                    FocusShield(active = isPhotoFocused, onDismiss = { isPhotoFocused = false }) {
                    FriendAvatarRow(
                        viewModel = viewModel,
                        activeFriendId = displayFriendId,
                        listState = avatarListState,
                        onAvatarClick = { friendId ->
                            val target = pageIndexFor(entries, viewModel.feedItems, friendId, viewModel)
                            scope.launch { pagerState.animateScrollToPage(target) }
                        },
                        onAddFriendClick = onAddFriendClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, bottom = 110.dp)
                            .blur(chromeBlur, BlurredEdgeTreatment.Unbounded),
                    )
                    }
                }
            }
        }
        }

        FocusShield(
            active = isPhotoFocused,
            onDismiss = { isPhotoFocused = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomNavDock(
                active = NavDestination.HOME,
                onNavigate = onNavigate,
                onCameraClick = onCameraClick,
                modifier = Modifier.blur(chromeBlur, BlurredEdgeTreatment.Unbounded),
                hazeState = hazeState,
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
private fun ProfileChip(name: String?, photoUrl: String?, onClick: () -> Unit) {
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
private data class HomeCarouselEntry(
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

/** The large featured card — one continuous pager across every friend's photos (see
 * [buildHomeCarousel]), so swiping past someone's last photo lands on the next friend's first
 * and back past a first photo lands on the previous friend's last, without any special-casing:
 * it's all just paging through one flattened list. */
@Composable
private fun FeaturedPhotoCard(
    entries: List<HomeCarouselEntry>,
    pagerState: PagerState,
    onToggleFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val cardShape = RoundedCornerShape(30.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .shadow(20.dp, cardShape, ambientColor = colors.glow.copy(alpha = 0.35f), spotColor = colors.glow.copy(alpha = 0.35f))
            .clip(cardShape)
            .background(colors.panel)
            // A plain tap (not a swipe) toggles focus mode — no ripple, since the blur
            // transition on the rest of the screen already reads as the tap's feedback.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onToggleFocus),
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

        // Bottom scrim keeps the overlaid name/time/streak readable on any photo, and
        // the generous padding keeps them clear of the rounded corners.
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
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(current.totalForFriend) { index ->
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (index == current.indexWithinFriend) Color.White
                                else Color.White.copy(alpha = 0.35f),
                            ),
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
                    fontWeight = FontWeight.Medium,
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

/** Fills the same footprint the featured photo card would occupy (rather than a stray line of
 * text floating in empty space) with a glowing placeholder and a direct CTA into the camera —
 * matching Locket's "the frame is always there, waiting" empty state instead of a blank screen. */
@Composable
private fun EmptyFeedState(onCameraClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val cardShape = RoundedCornerShape(30.dp)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .shadow(20.dp, cardShape, ambientColor = colors.glow.copy(alpha = 0.25f), spotColor = colors.glow.copy(alpha = 0.25f))
                .clip(cardShape)
                .background(Brush.radialGradient(listOf(colors.glow.copy(alpha = 0.22f), colors.panel)))
                .border(1.dp, colors.border, cardShape),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.glow.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = colors.glow, modifier = Modifier.size(30.dp))
                }
                Text(
                    text = "No photos yet",
                    fontFamily = typography.display,
                    fontSize = 19.sp,
                    color = colors.cream,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    text = "Once a friend sends you one, it'll glow right here",
                    fontFamily = typography.body,
                    fontSize = 12.5.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        val buttonSizePx = Size(300f, 52f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp)
                .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), RoundedCornerShape(16.dp))
                .clickable(onClick = onCameraClick)
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
            Text(
                text = "Send your first photo",
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentText,
                modifier = Modifier.padding(start = 8.dp),
            )
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

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(72.dp),
            ) {
                Box(modifier = Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(avatarSize)
                            .clip(CircleShape)
                            .background(
                                if (hasUnseen) {
                                    Brush.sweepGradient(listOf(colors.glow, colors.glow2, colors.violet, colors.glow))
                                } else {
                                    Brush.linearGradient(listOf(colors.mutedDim, colors.mutedDim))
                                },
                            )
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(colors.panel)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(if (hasUnseen) colors.glow else colors.mutedDim),
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
