package com.ember.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.ui.components.BottomNavDock
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.CourgetteFontFamily
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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
            // Only true once there's already content on screen to refresh — the very first
            // cold-start load is covered by the full-screen spinner below instead, so the
            // pull indicator doesn't also animate in from the top on every app launch.
            isRefreshing = viewModel.isLoading && viewModel.feedItems.isNotEmpty(),
            onRefresh = viewModel::loadFeed,
            modifier = Modifier.fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .verticalScroll(rememberScrollState()),
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

            // The opening line is a live status, not a greeting — it says something true about
            // the app's actual state right now (who's waiting to be seen) instead of the
            // generic "Good evening, Name" every dashboard app defaults to. The personal touch
            // moves to a small subline instead of carrying the whole header.
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
                    val selected = viewModel.selectedItem ?: viewModel.feedItems.first()

                    FeaturedPhotoCard(
                        viewModel = viewModel,
                        item = selected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, start = 22.dp, end = 22.dp),
                    )

                    FriendAvatarRow(
                        viewModel = viewModel,
                        selectedFriendId = selected.friendId,
                        onAddFriendClick = onAddFriendClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp, bottom = 110.dp),
                    )
                }
            }
        }
        }

        BottomNavDock(
            active = NavDestination.HOME,
            onNavigate = onNavigate,
            onCameraClick = onCameraClick,
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
        )
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

/** The large photo card for the selected friend. Swiping it pages only through that friend's
 * own photos (never to another friend); switching friends cross-fades the whole card while
 * each friend remembers their own carousel position. */
@Composable
private fun FeaturedPhotoCard(
    viewModel: HomeViewModel,
    item: FeedItem,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val cardShape = RoundedCornerShape(30.dp)

    AnimatedContent(
        targetState = item,
        contentKey = { it.friendId },
        transitionSpec = {
            (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { it / 10 })
                .togetherWith(fadeOut(tween(250)) + slideOutHorizontally(tween(250)) { -it / 10 })
        },
        label = "featuredFriend",
        modifier = modifier,
    ) { friend ->
        key(friend.friendId) {
            // Photos come back oldest-first; the carousel shows newest-first instead, so page
            // 0 is always the latest photo and swiping steps backward through the day.
            val orderedPhotos = remember(friend.photos) { friend.photos.asReversed() }
            val pagerState = rememberPagerState(
                initialPage = viewModel.photoIndexFor(friend.friendId).coerceIn(0, orderedPhotos.lastIndex),
            ) { orderedPhotos.size }

            LaunchedEffect(pagerState.currentPage) {
                viewModel.setPhotoIndex(friend.friendId, pagerState.currentPage)
                if (pagerState.currentPage == 0) {
                    viewModel.markLatestSeen(friend.friendId, orderedPhotos.first().photoId)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.8f)
                    .shadow(20.dp, cardShape, ambientColor = colors.glow.copy(alpha = 0.35f), spotColor = colors.glow.copy(alpha = 0.35f))
                    .clip(cardShape)
                    .background(colors.panel),
            ) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    AsyncImage(
                        model = orderedPhotos[page].photoUrl,
                        contentDescription = friend.displayName,
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

                if (orderedPhotos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        orderedPhotos.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .width(16.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (index == pagerState.currentPage) Color.White
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
                            text = friend.displayName,
                            fontFamily = typography.display,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFFBF8F3),
                        )
                        Text(
                            text = formatRelativeTime(orderedPhotos[pagerState.currentPage].createdAt),
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
                            text = "${friend.streak}",
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
    selectedFriendId: String,
    onAddFriendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items(viewModel.feedItems, key = { it.friendId }) { item ->
            val isActive = item.friendId == selectedFriendId
            val hasUnseen = viewModel.hasUnseenPhoto(item)
            val avatarSize by animateDpAsState(
                targetValue = if (isActive) 66.dp else 58.dp,
                animationSpec = tween(180),
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
                            .clickable { viewModel.selectFriend(item.friendId) },
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
