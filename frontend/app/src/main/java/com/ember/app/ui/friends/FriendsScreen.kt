package com.ember.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.PendingFriendRequestDto
import com.ember.app.ui.home.formatRelativeTime
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

/** Signature device for this screen: a friend's ring literally warms up with their streak,
 * rather than a numeric badge doing all the work — 0 is unlit, low streaks glow one colour,
 * longer ones become a full ember-to-violet blaze. Mirrors the ring language Home already
 * uses for "unseen photo", repurposed here to mean "how much this friendship is glowing". */
private fun streakRingBrush(colors: com.ember.app.ui.theme.EmberColors, streak: Int): Brush = when {
    streak >= 7 -> Brush.sweepGradient(listOf(colors.glow, colors.glow2, colors.violet, colors.glow))
    streak >= 3 -> Brush.linearGradient(listOf(colors.glow, colors.glow2))
    streak >= 1 -> Brush.linearGradient(listOf(colors.glow.copy(alpha = 0.8f), colors.glow.copy(alpha = 0.8f)))
    else -> Brush.linearGradient(listOf(colors.border, colors.border))
}

/** Rounds only the true top/bottom edges of the friends list so consecutive rows read as one
 * seamless panel — same grouped-card language as Settings — while each row stays its own
 * LazyColumn item (no loss of per-row virtualization for a list that can run to hundreds of
 * friends, unlike Settings' handful of fixed rows). */
private fun groupRowShape(index: Int, lastIndex: Int): RoundedCornerShape {
    val corner = 22.dp
    return when {
        lastIndex <= 0 -> RoundedCornerShape(corner)
        index == 0 -> RoundedCornerShape(topStart = corner, topEnd = corner, bottomStart = 0.dp, bottomEnd = 0.dp)
        index == lastIndex -> RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = corner, bottomEnd = corner)
        else -> RoundedCornerShape(0.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onCameraClick: () -> Unit,
    onFindPeopleClick: () -> Unit,
    onFriendClick: (FriendSummaryDto) -> Unit,
    onPendingRequestClick: (PendingFriendRequestDto) -> Unit,
    hazeState: HazeState,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val searchShape = RoundedCornerShape(16.dp)
    val isSearching = viewModel.searchQuery.isNotBlank()
    val pinnedPartner = viewModel.friends.firstOrNull { it.pinnedByMe }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState).statusBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, start = 20.dp, end = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(text = "Friends", fontFamily = typography.display, fontSize = 26.sp, color = colors.cream)
                    Text(
                        text = if (viewModel.friends.isEmpty()) {
                            "Nobody yet"
                        } else if (viewModel.friends.size == 1) {
                            "1 person glows with you"
                        } else {
                            "${viewModel.friends.size} people glow with you"
                        },
                        fontFamily = typography.body,
                        fontSize = 12.5.sp,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                // Plain panel + neutral icon, not a colored bubble — same quieter chrome as
                // Settings, which keeps color for things that carry real meaning (a streak, a
                // toggle) rather than spending it on navigation affordances.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(colors.panel, CircleShape)
                        .clickable(onClick = onFindPeopleClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Find people", tint = colors.cream, modifier = Modifier.size(19.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, start = 20.dp, end = 20.dp)
                    .background(colors.panel, searchShape)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Search", tint = colors.mutedDim, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.padding(start = 10.dp).fillMaxWidth()) {
                    if (viewModel.searchQuery.isEmpty()) {
                        Text(text = "Search friends", fontFamily = typography.body, fontSize = 13.5.sp, color = colors.mutedDim)
                    }
                    BasicTextField(
                        value = viewModel.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = typography.body, fontSize = 13.5.sp, color = colors.cream),
                        cursorBrush = SolidColor(colors.glow),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            val pullRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                // Only true once there's already content on screen — the cold-start load is
                // covered by the full-screen spinner instead, so the pull indicator doesn't
                // animate in from the top on every app launch.
                isRefreshing = viewModel.isLoading && (viewModel.filteredFriends.isNotEmpty() || viewModel.pendingRequests.isNotEmpty()),
                onRefresh = { viewModel.loadFriends(isPullRefresh = true) },
                state = pullRefreshState,
                // Recolored to the theme's own panel/glow tokens — see HomeScreen's identical
                // indicator for why (the stock Material scheme doesn't read as part of this app).
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullRefreshState,
                        isRefreshing = viewModel.isLoading && (viewModel.filteredFriends.isNotEmpty() || viewModel.pendingRequests.isNotEmpty()),
                        containerColor = colors.panel,
                        color = colors.glow,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    viewModel.isLoading && viewModel.filteredFriends.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.glow)
                    }

                    viewModel.errorMessage != null && viewModel.filteredFriends.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = viewModel.errorMessage.orEmpty(),
                            fontFamily = typography.body,
                            fontSize = 13.sp,
                            color = colors.muted,
                        )
                    }

                    viewModel.filteredFriends.isEmpty() && viewModel.pendingRequests.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (isSearching) "No one matches \"${viewModel.searchQuery}\"" else "No friends yet",
                                fontFamily = typography.body,
                                fontSize = 13.sp,
                                color = colors.muted,
                            )
                            if (!isSearching) {
                                Text(
                                    text = "Tap + to find people to glow with",
                                    fontFamily = typography.body,
                                    fontSize = 12.sp,
                                    color = colors.mutedDim,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 110.dp),
                        // No global gap — the friends list below draws as one seamless panel
                        // (each row's own corner-rounding does the grouping instead of spacing),
                        // same as Settings' grouped cards. Sections that do need a gap add their
                        // own top/bottom padding below.
                    ) {
                        if (!isSearching && pinnedPartner != null) {
                            item(key = "hero") {
                                PinnedPartnerHero(
                                    friend = pinnedPartner,
                                    onClick = { onFriendClick(pinnedPartner) },
                                    modifier = Modifier.padding(bottom = 18.dp),
                                )
                            }
                        }

                        if (!isSearching && viewModel.pendingRequests.isNotEmpty()) {
                            item(key = "requests-header") {
                                SectionLabel(text = "Added you · ${viewModel.pendingRequests.size}")
                            }
                            item(key = "requests-row") {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    contentPadding = PaddingValues(bottom = 18.dp),
                                ) {
                                    items(viewModel.pendingRequests, key = { it.friendshipId }) { request ->
                                        PendingRequestChip(
                                            request = request,
                                            onClick = { onPendingRequestClick(request) },
                                        )
                                    }
                                }
                            }
                        }

                        if (!isSearching && (pinnedPartner != null || viewModel.pendingRequests.isNotEmpty())) {
                            item(key = "friends-header") {
                                SectionLabel(text = "My friends")
                            }
                        }

                        val lastIndex = viewModel.filteredFriends.lastIndex
                        itemsIndexed(viewModel.filteredFriends, key = { _, f -> f.friendshipId }) { index, friend ->
                            FriendRow(friend, shape = groupRowShape(index, lastIndex), onClick = { onFriendClick(friend) })
                        }

                        // Search operates only over what's already loaded (client-side
                        // filtering), so there's nothing to page in while searching — the
                        // sentinel only appears for the plain, unfiltered list. Composed only
                        // once the user has actually scrolled near the end (LazyColumn doesn't
                        // compose items far outside the viewport), which is what triggers the
                        // fetch — not a fixed scroll-position threshold.
                        if (!isSearching && viewModel.hasMore) {
                            item(key = "load-more") {
                                LaunchedEffect(Unit) { viewModel.loadMoreFriends() }
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = colors.glow, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Same label treatment as Settings' own section headers — title case, no letter-spacing,
 * plain UI font, instead of the old uppercase/tracked-out caption. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors

    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.mutedDim,
        modifier = modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

/** "Your Ember" — the person you've pinned, spotlighted in the exact card language Home uses
 * for a sent photo (same shape, shadow, bottom scrim). The repetition is deliberate: it tells
 * you this is the same kind of glow, just standing for a relationship instead of a single photo. */
@Composable
private fun PinnedPartnerHero(friend: FriendSummaryDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val cardShape = RoundedCornerShape(28.dp)

    Column(modifier = modifier) {
        SectionLabel(text = "Your ember")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                // 1:1, not the old 1.55 landscape ratio — the profile photo behind this card is
                // always a square crop now (see PhotoCropScreen), so a landscape card was
                // cropping a second time on top of an already-deliberate square, for no reason.
                .aspectRatio(1f)
                .clip(cardShape)
                .background(colors.panel)
                .clickable(onClick = onClick),
        ) {
            if (friend.profilePhotoUrl != null) {
                AsyncImage(
                    model = friend.profilePhotoUrl,
                    contentDescription = friend.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // Same "no photo yet" identity as ActivityScreen's ActivityRow — an initial
                // letter, not just an empty placeholder, so a friend without a profile photo
                // still reads as *them* rather than as a broken/missing image.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(colors.glow.copy(alpha = 0.28f), colors.panel))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = friend.displayName.firstOrNull()?.uppercase() ?: "•",
                        fontFamily = typography.display,
                        fontSize = 40.sp,
                        color = colors.cream,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.4f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.68f))),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = friend.displayName,
                        fontFamily = typography.display,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFBF8F3),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        Icon(Icons.Filled.PushPin, contentDescription = null, tint = colors.glow, modifier = Modifier.size(11.dp))
                        Text(
                            text = "Pinned partner",
                            fontFamily = typography.body,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (friend.streak > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Streak", tint = colors.glow, modifier = Modifier.size(18.dp))
                        Text(
                            text = "${friend.streak}",
                            fontFamily = typography.body,
                            fontSize = 15.sp,
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

/** Circular ring avatar whose colour is the streak-intensity signature — used everywhere a
 * friend's identity needs to carry that "how much is this glowing" information at a glance. */
@Composable
private fun StreakAvatar(photoUrl: String?, displayName: String, streak: Int, size: Dp) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val ringWidth = if (streak > 0) 2.5.dp else 1.5.dp

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(streakRingBrush(colors, streak))
            .padding(ringWidth)
            .clip(CircleShape)
            .background(colors.panel)
            .padding(2.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Same "no photo yet" identity as ActivityScreen's ActivityRow — see PinnedPartnerHero
            // above for the fuller reasoning.
            Box(modifier = Modifier.fillMaxSize().background(colors.border.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "•",
                    fontFamily = typography.display,
                    fontSize = (size.value * 0.4f).sp,
                    color = colors.cream,
                )
            }
        }
    }
}

/** A single tap target that opens the requester's profile page — accepting/declining lives
 * there now (same profile screen every person gets), not inline on this chip. */
@Composable
private fun PendingRequestChip(
    request: PendingFriendRequestDto,
    onClick: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(68.dp).clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            if (request.profilePhotoUrl != null) {
                AsyncImage(
                    model = request.profilePhotoUrl,
                    contentDescription = request.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                // Same "no photo yet" identity as ActivityScreen's ActivityRow — see
                // PinnedPartnerHero's own comment above for the fuller reasoning.
                Text(
                    text = request.displayName.firstOrNull()?.uppercase() ?: "•",
                    fontFamily = typography.display,
                    fontSize = 20.sp,
                    color = colors.cream,
                )
            }
        }
        Text(
            text = request.displayName,
            fontFamily = typography.body,
            fontSize = 11.5.sp,
            color = colors.cream,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun FriendRow(friend: FriendSummaryDto, shape: RoundedCornerShape, onClick: () -> Unit) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreakAvatar(photoUrl = friend.profilePhotoUrl, displayName = friend.displayName, streak = friend.streak, size = 52.dp)
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = friend.displayName,
                    fontFamily = typography.body,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.cream,
                )
                if (friend.pinnedByMe) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = colors.glow,
                        modifier = Modifier.padding(start = 5.dp).size(11.dp),
                    )
                }
            }
            Text(
                text = friend.lastActivityAt?.let { "Last sent ${formatRelativeTime(it)}" } ?: "No photos yet",
                fontFamily = typography.body,
                fontSize = 12.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // A "0" streak isn't an achievement worth displaying — only show once a friend
        // actually has one going.
        if (friend.streak > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = "Streak", tint = colors.glow, modifier = Modifier.size(14.dp))
                Text(
                    text = "${friend.streak}",
                    fontFamily = typography.body,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.glow,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
