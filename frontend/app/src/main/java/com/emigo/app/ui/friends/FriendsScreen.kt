package com.emigo.app.ui.friends

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.emigo.app.R
import com.emigo.app.data.remote.dto.FriendSummaryDto
import com.emigo.app.data.remote.dto.PendingFriendRequestDto
import com.emigo.app.ui.components.TabScreenScaffold
import com.emigo.app.ui.home.formatRelativeTime
import com.emigo.app.ui.theme.EmberFixedColors
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import dev.chrisbanes.haze.HazeState

// How close to a streak's deadline counts as "at risk". Must match the backend's
// STREAK_AT_RISK_THRESHOLD_HOURS (StreakCalculator.kt); otherwise this screen and ActivityService's
// STREAK_EXPIRING event would disagree about when "soon" starts.
private const val STREAK_AT_RISK_THRESHOLD_SECONDS = 4 * 60 * 60L

/** A friend's ring warms up with their streak instead of a number doing all the work: 0 is unlit,
 * low streaks glow one color, longer ones become a full ember-to-violet blaze. Mirrors Home's
 * "unseen photo" ring, here meaning how much the friendship is glowing. */
private fun streakRingBrush(colors: com.emigo.app.ui.theme.EmberColors, streak: Int): Brush = when {
    streak >= 7 -> Brush.sweepGradient(listOf(colors.glow, colors.glow2, colors.violet, colors.glow))
    streak >= 3 -> Brush.linearGradient(listOf(colors.glow, colors.glow2))
    streak >= 1 -> Brush.linearGradient(listOf(colors.glow.copy(alpha = 0.8f), colors.glow.copy(alpha = 0.8f)))
    else -> Brush.linearGradient(listOf(colors.border, colors.border))
}


@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onCameraClick: () -> Unit,
    onFindPeopleClick: () -> Unit,
    onFriendClick: (FriendSummaryDto) -> Unit,
    onPendingRequestClick: (PendingFriendRequestDto) -> Unit,
    onUpgradeToGold: () -> Unit,
    hazeState: HazeState,
    // Defaults to a local state. MainActivity passes a hoisted one so the scroll position survives
    // opening a friend's profile and coming back (same reasoning as Home's hoisted scroll state).
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val searchShape = RoundedCornerShape(16.dp)
    val isSearching = viewModel.searchQuery.isNotBlank()
    val pinnedPartner = viewModel.friends.firstOrNull { it.pinnedByMe }

    TabScreenScaffold(
        title = stringResource(R.string.friends_title),
        hazeState = hazeState,
        trailing = {
            // Same panel-toned 44dp circle as Home's header icons (ActivityBellButton,
            // ProfileIconButton). TabScreenHeader sizes its row to the taller of the title and this
            // control, so the row grows to fit it.
            Box(
                modifier = Modifier.size(44.dp).clickable(onClick = onFindPeopleClick),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.panel),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.PersonAdd, contentDescription = stringResource(R.string.friends_find_people), tint = colors.cream, modifier = Modifier.size(24.dp))
                }
            }
        },
        // Strictly the manual pull gesture, not isLoading: isLoading is also true for the automatic
        // load on every app start. With a warm cache, content is already showing by then, so keying
        // on isLoading showed a refresh nobody asked for.
        isRefreshing = viewModel.isPullRefreshing,
        onRefresh = { viewModel.loadFriends(isPullRefresh = true) },
        listState = listState,
    ) {
        // The search bar is the scaffold's first list item, so it scrolls away with the list
        // instead of staying pinned. Hidden with zero friends: an input with no possible results
        // read as broken, not empty.
        if (viewModel.friends.isNotEmpty()) item(key = "search") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
                    // A quiet in-between tone, not the card panel: an input should sit apart from
                    // the background without competing with cards.
                    .background(colors.surface, searchShape)
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.friends_search_icon_description), tint = colors.mutedDim, modifier = Modifier.size(16.dp))
                Box(modifier = Modifier.padding(start = 10.dp).fillMaxWidth()) {
                    if (viewModel.searchQuery.isEmpty()) {
                        Text(text = stringResource(R.string.friends_search_hint), fontFamily = typography.body, fontSize = 13.5.sp, color = colors.mutedDim)
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
        }

        when {
            viewModel.isLoading && viewModel.filteredFriends.isEmpty() -> item(key = "loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.glow)
                }
            }

            viewModel.errorMessage != null && viewModel.filteredFriends.isEmpty() -> item(key = "error") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = viewModel.errorMessage.orEmpty(),
                        fontFamily = typography.body,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }
            }

            viewModel.filteredFriends.isEmpty() && viewModel.pendingRequests.isEmpty() -> item(key = "empty") {
                // The empty-state text sits near the top; the search-miss line gets more room
                // below the search bar.
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = if (isSearching) 64.dp else 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isSearching) {
                            Text(
                                text = stringResource(R.string.friends_no_match, viewModel.searchQuery),
                                fontFamily = typography.body,
                                fontSize = 13.sp,
                                color = colors.muted,
                            )
                        }
                        if (!isSearching) {
                            // Plain text only: no invite row, no icons. The search bar is already
                            // hidden in this state (see item("search")), so this is the one thing
                            // on screen.
                            Text(
                                text = stringResource(R.string.friends_empty),
                                fontFamily = typography.body,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.mutedDim,
                            )
                        }
                    }
                }
            }

            else -> {
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
                        SectionLabel(text = stringResource(R.string.friends_section_requests, viewModel.pendingRequests.size))
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
                        SectionLabel(text = stringResource(R.string.friends_section_my_friends))
                    }
                }

                // The pinned friend already has the hero card above (hidden while searching, when
                // results still include them), so exclude them here or they'd render twice.
                val friendRows = if (!isSearching && pinnedPartner != null) {
                    viewModel.filteredFriends.filterNot { it.friendshipId == pinnedPartner.friendshipId }
                } else {
                    viewModel.filteredFriends
                }
                    // More than one friend can be pinned (pinning one never unpins another) and the
                    // hero features only the first, so other pinned friends must still stand out
                    // here. Within and below that: most recent activity first. lastActivityAt is
                    // ISO-8601, so plain descending string order is chronological; null falls back
                    // to "" (the oldest), so those friends land last in their group.
                    .sortedWith(
                        compareByDescending<FriendSummaryDto> { it.pinnedByMe }
                            .thenByDescending { it.lastActivityAt ?: "" },
                    )
                items(friendRows, key = { it.friendshipId }) { friend ->
                    FriendRow(
                        friend = friend,
                        onClick = { onFriendClick(friend) },
                        isRestoring = friend.friendshipId in viewModel.restoringStreakFriendshipIds,
                        onRestoreStreakClick = {
                            // A client-side fast path only (same check as WidgetSettingsScreen's
                            // upgrade button). The server re-checks Gold itself (see
                            // FriendService.restoreStreak).
                            if (viewModel.isGoldMember) {
                                viewModel.restoreStreak(friend.friendshipId)
                            } else {
                                onUpgradeToGold()
                            }
                        },
                    )
                }

                // Search filters what's already loaded, so there's nothing to page in while
                // searching; the sentinel appears only for the plain list. It composes only once
                // the user scrolls near the end (LazyColumn skips far-offscreen items), which is
                // what triggers the fetch, not a fixed scroll threshold.
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

/** Same label style as Settings' section headers: title case, no letter-spacing, plain UI font. */
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

/** "Your Emigo": the pinned person, shown in the same card style Home uses for a sent photo (shape,
 * shadow, bottom scrim). The repetition is deliberate: it says this is the same kind of glow,
 * standing for a relationship instead of a single photo. */
@Composable
private fun PinnedPartnerHero(friend: FriendSummaryDto, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val cardShape = RoundedCornerShape(28.dp)

    Column(modifier = modifier) {
        SectionLabel(text = stringResource(R.string.friends_section_your_emigo))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                // 1:1: the profile photo behind this card is always a square crop (see
                // PhotoCropScreen), so a landscape card would crop it a second time.
                .aspectRatio(1f)
                .clip(cardShape)
                // Elevated, not the row panel tone: the one card meant to outrank its siblings.
                .background(colors.elevatedPanel)
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
                // Same no-photo fallback as ActivityScreen's ActivityRow: an initial on a flat
                // panel, so a friend without a profile photo still reads as them.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.elevatedPanel),
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
                        color = EmberFixedColors.onPhotoText,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                        Icon(Icons.Rounded.PushPin, contentDescription = null, tint = colors.glow, modifier = Modifier.size(11.dp))
                        Text(
                            text = stringResource(R.string.friends_pinned_partner),
                            fontFamily = typography.body,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                if (friend.streak > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = stringResource(R.string.friends_streak_description), tint = colors.glow, modifier = Modifier.size(18.dp))
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

/** A ring avatar colored by streak intensity, used wherever a friend's identity should show how
 * much the friendship is glowing. */
@Composable
internal fun StreakAvatar(photoUrl: String?, displayName: String, streak: Int, size: Dp) {
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
            // Same no-photo fallback as PinnedPartnerHero. Elevated, not colors.border (a stroke
            // token that read as washed-out grey) or colors.panel (this sits in a panel-toned row
            // and would blend in).
            Box(modifier = Modifier.fillMaxSize().background(colors.elevatedPanel), contentAlignment = Alignment.Center) {
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

/** One tap target that opens the requester's profile, where accept and decline now live (the same
 * profile screen everyone gets), not on the chip. */
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
                // Same no-photo fallback as PinnedPartnerHero.
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

// No card background: flat on the screen background, like Snapchat's chat list. Clarity comes from
// bold names, generous row height and meaningful color, not a panel behind each row. Rows are
// separated by spacing (the vertical padding below), never a divider line.
@Composable
private fun FriendRow(
    friend: FriendSummaryDto,
    onClick: () -> Unit,
    isRestoring: Boolean,
    onRestoreStreakClick: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    // The same "streak = warmth" signature the avatar rings carry (see streakRingBrush), extended
    // to the status line. Three states: a live streak glows; a broken one (shared before, but not
    // recently enough to keep it) reads as a normal, legible status; no history at all gets the
    // dimmest tone. A broken streak shouldn't fade to that placeholder tone, since "we used to have
    // a streak" is a real fact.
    val hasHistory = friend.lastActivityAt != null
    val statusColor = when {
        friend.streak > 0 -> colors.glow
        hasHistory -> colors.muted
        else -> colors.mutedDim
    }

    // Evaluated against the device clock, not a flag the server set at fetch time (see
    // FriendSummaryDto): a row rendered from LocalListCache while offline can be long past the
    // fetch that produced these deadlines.
    val nowEpochSeconds = System.currentTimeMillis() / 1000
    val isStreakAtRisk = friend.streakDeadlineEpochSeconds?.let { deadline ->
        deadline > nowEpochSeconds && deadline - nowEpochSeconds <= STREAK_AT_RISK_THRESHOLD_SECONDS
    } ?: false
    val isStreakRestoreAvailable = friend.streakRestoreDeadlineEpochSeconds?.let { it > nowEpochSeconds } ?: false

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreakAvatar(photoUrl = friend.profilePhotoUrl, displayName = friend.displayName, streak = friend.streak, size = 54.dp)
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = friend.displayName,
                    fontFamily = typography.body,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.cream,
                )
                if (friend.pinnedByMe) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = stringResource(R.string.friends_pinned_description),
                        tint = colors.glow,
                        modifier = Modifier.padding(start = 5.dp).size(11.dp),
                    )
                }
            }
            Text(
                // Direction-aware: lastActivityBySelf says whether the latest exchange was sent by
                // this account or by the friend (same wording as the Friend Profile screen).
                text = friend.lastActivityAt?.let {
                    stringResource(
                        if (friend.lastActivityBySelf == true) R.string.friends_you_sent else R.string.friends_sent_to_you,
                        formatRelativeTime(it),
                    )
                } ?: stringResource(R.string.friends_no_photos_yet),
                fontFamily = typography.body,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Normal,
                color = statusColor,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        when {
            // A broken streak with a live restore window replaces the flame with a pill in the
            // app's accent (colors.glow, the flame's yellow) with colors.accentText on top, the
            // same pairing as the app's other filled buttons (AddActions' "Add",
            // FriendProfileScreen's "Done"). An orange was tried first and looked wrong because it
            // wasn't the app's one accent. Small, so it doesn't outshout the friend's name.
            isStreakRestoreAvailable -> {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(colors.glow)
                        .clickable(enabled = !isRestoring, onClick = onRestoreStreakClick)
                        .padding(horizontal = 11.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(modifier = Modifier.size(11.dp), color = colors.accentText, strokeWidth = 1.5.dp)
                    } else {
                        Text(
                            text = stringResource(R.string.friends_restore_streak),
                            fontFamily = typography.body,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentText,
                        )
                    }
                }
            }
            // Still alive but not kept up today: the same "about to lapse" window ActivityService's
            // STREAK_EXPIRING event fires for, shown here so it's visible without opening Activity.
            isStreakAtRisk -> {
                Icon(
                    Icons.Rounded.HourglassBottom,
                    contentDescription = stringResource(R.string.friends_streak_expiring_description),
                    tint = colors.glow2,
                    modifier = Modifier.size(16.dp),
                )
            }
            // A 0 streak isn't worth showing; only show one once it's going.
            friend.streak > 0 -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = stringResource(R.string.friends_streak_description), tint = colors.glow, modifier = Modifier.size(14.dp))
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
}
