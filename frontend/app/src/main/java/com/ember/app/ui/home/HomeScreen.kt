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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.data.remote.dto.FeedItem
import com.ember.app.ui.components.BottomNavDock
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.theme.CourgetteFontFamily
import com.ember.app.ui.theme.EmberTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

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
        // pixels and produce a ghosting artifact.
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
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

            Text(
                text = buildAnnotatedString {
                    append("Good ${daypart()}")
                    val name = viewModel.userName?.substringBefore(" ")
                    if (!name.isNullOrBlank()) {
                        append(", ")
                        withStyle(SpanStyle(color = colors.glow)) { append(name) }
                    }
                },
                fontFamily = typography.display,
                fontSize = 24.sp,
                color = colors.cream,
                modifier = Modifier.padding(top = 24.dp, start = 22.dp, end = 22.dp),
            )
            Row(
                modifier = Modifier.padding(top = 5.dp, start = 22.dp, end = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = viewModel.dateText,
                    fontFamily = typography.body,
                    fontSize = 12.5.sp,
                    color = colors.muted,
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(3.dp)
                        .clip(CircleShape)
                        .background(colors.mutedDim),
                )
                Text(
                    text = "Latest from friends",
                    fontFamily = typography.body,
                    fontSize = 12.5.sp,
                    color = colors.muted,
                )
            }

            when {
                viewModel.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.glow)
                }

                viewModel.errorMessage != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
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

                viewModel.feedItems.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No photos yet — once a friend sends you one, it'll glow right here.",
                        fontFamily = typography.body,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }

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
                            .padding(top = 20.dp),
                    )

                    Text(
                        text = "‹  Swipe left or right to view friends  ›",
                        fontFamily = typography.body,
                        fontSize = 12.sp,
                        color = colors.mutedDim,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 16.dp),
                    )
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
            val pagerState = rememberPagerState(
                initialPage = viewModel.photoIndexFor(friend.friendId).coerceIn(0, friend.photos.lastIndex),
            ) { friend.photos.size }

            LaunchedEffect(pagerState.currentPage) {
                viewModel.setPhotoIndex(friend.friendId, pagerState.currentPage)
                if (pagerState.currentPage == friend.photos.lastIndex) {
                    viewModel.markLatestSeen(friend.friendId, friend.photos.last().photoId)
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
                        model = friend.photos[page].photoUrl,
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

                if (friend.photos.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        friend.photos.forEachIndexed { index, _ ->
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
                            text = formatRelativeTime(friend.photos[pagerState.currentPage].createdAt),
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
