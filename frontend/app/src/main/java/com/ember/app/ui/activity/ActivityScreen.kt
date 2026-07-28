package com.ember.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.TaskAlt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.data.remote.dto.ActivityEventDto
import com.ember.app.data.remote.dto.ActivityEventType
import com.ember.app.ui.home.formatRelativeTime
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onCameraClick: () -> Unit,
    onNavigateToFriends: () -> Unit,
    hazeState: HazeState,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }

    val groups = remember(viewModel.events) { groupByDay(viewModel.events) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState).statusBarsPadding()) {
            // Centered, plain-weight title — same header treatment as Settings, rather than the
            // left-aligned display-font title/subtitle Friends and Home use. The per-day group
            // labels below (Today/Yesterday/...) already carry the "what's new" information the
            // old subtitle summarized, so there's no separate count line to keep here either.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 22.dp), contentAlignment = Alignment.Center) {
                Text(text = "Activity", fontFamily = PublicSansFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.cream)
            }

            val pullRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                // Only true once there's already content on screen — the cold-start load is
                // covered by the full-screen spinner instead, so the pull indicator doesn't
                // animate in from the top on every app launch.
                isRefreshing = viewModel.isLoading && viewModel.events.isNotEmpty(),
                onRefresh = { viewModel.loadActivity(isPullRefresh = true) },
                state = pullRefreshState,
                // Recolored to the theme's own panel/glow tokens — see HomeScreen's identical
                // indicator for why (the stock Material scheme doesn't read as part of this app).
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullRefreshState,
                        isRefreshing = viewModel.isLoading && viewModel.events.isNotEmpty(),
                        containerColor = colors.panel,
                        color = colors.glow,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    viewModel.isLoading && viewModel.events.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = colors.glow)
                    }

                    viewModel.errorMessage != null && viewModel.events.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = viewModel.errorMessage.orEmpty(),
                                fontFamily = typography.body,
                                fontSize = 13.sp,
                                color = colors.muted,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = "Tap to retry",
                                fontFamily = typography.body,
                                fontSize = 13.sp,
                                color = colors.glow,
                                modifier = Modifier.padding(top = 12.dp).clickable { viewModel.loadActivity() },
                            )
                        }
                    }

                    viewModel.events.isEmpty() -> EmptyActivityState(modifier = Modifier.fillMaxSize().padding(32.dp))

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 0.dp, bottom = 110.dp),
                    ) {
                        groups.forEach { (label, events) ->
                            // Same section-label treatment as Settings — title case, no letter
                            // spacing. No extra start inset — ActivityRow is flat now (no card,
                            // no padding of its own beyond the LazyColumn's contentPadding), so
                            // this already lines up with the avatar directly below it.
                            item(key = "header-$label") {
                                SectionLabel(text = label, modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                            }
                            item(key = "group-$label") {
                                ActivityGroup(events = events, onNavigateToFriends = onNavigateToFriends)
                            }
                        }

                        // Composed only once the user has actually scrolled near the end
                        // (LazyColumn doesn't compose items far outside the viewport), which is
                        // what triggers the fetch — not a fixed scroll-position threshold.
                        if (viewModel.hasMore) {
                            item(key = "load-more") {
                                LaunchedEffect(Unit) { viewModel.loadMoreActivity() }
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

/** Buckets events into day-labelled groups (Today / Yesterday / weekday / date), preserving the
 * backend's newest-first order both across and within groups. Grouping earns its place here
 * because the content genuinely is chronological — unlike a numbered list on a page where order
 * is just decoration, when something happened is real information on an activity log. */
private fun groupByDay(events: List<ActivityEventDto>): List<Pair<String, List<ActivityEventDto>>> =
    events.groupBy { dayLabel(it.createdAt) }.toList()

private fun dayLabel(isoInstant: String): String {
    val zone = ZoneId.systemDefault()
    val then = runCatching { Instant.parse(isoInstant) }.getOrNull()?.atZone(zone)?.toLocalDate() ?: return "Earlier"
    val today = LocalDate.now(zone)
    return when {
        then == today -> "Today"
        then == today.minusDays(1) -> "Yesterday"
        then.isAfter(today.minusDays(7)) -> then.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        else -> "${then.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${then.dayOfMonth}"
    }
}

private fun iconFor(type: ActivityEventType): ImageVector = when (type) {
    ActivityEventType.PHOTO_RECEIVED -> Icons.Rounded.PhotoLibrary
    ActivityEventType.STREAK_EXPIRING -> Icons.Rounded.LocalFireDepartment
    ActivityEventType.REQUEST_ACCEPTED -> Icons.Rounded.TaskAlt
    ActivityEventType.REQUEST_INCOMING -> Icons.Rounded.PersonAdd
}

/** No panel behind the day's events — flat, straight on the screen's own background, same
 * language as the Friends list. Separation between events is spacing (see [ActivityRow]'s own
 * padding), and between days it's [SectionLabel]'s day heading, never a card boundary or a
 * divider line. */
@Composable
private fun ActivityGroup(events: List<ActivityEventDto>, onNavigateToFriends: () -> Unit) {
    Column {
        events.forEach { event ->
            ActivityRow(
                event = event,
                // Only an incoming request has anywhere useful to go — the actual accept action
                // lives on the Friends tab (see its own "Added you" row), this just gets you there.
                onClick = if (event.type == ActivityEventType.REQUEST_INCOMING) onNavigateToFriends else null,
            )
        }
    }
}

/** Two real images carry the row instead of one abstract placeholder: the actor's actual
 * profile photo (who), and — only for a received photo — a thumbnail of the photo itself
 * (what), so a burst of sends from one friend collapses into a single "sent you 3 photos" row
 * (see ActivityService) rather than three identical-looking entries. Urgent events (streak
 * about to lapse) get a warm badge and warm-tinted time so they're what your eye catches first
 * while scanning. */
@Composable
private fun ActivityRow(event: ActivityEventDto, onClick: (() -> Unit)? = null) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val badgeTint = if (event.warn) colors.glow else colors.violet
    val badgeIconTint = if (event.warn) colors.accentText else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    // A quiet, consistent circle to sit on the flat background — same role
                    // elevatedPanel plays for Friends' own avatar fallback.
                    .background(colors.elevatedPanel),
                contentAlignment = Alignment.Center,
            ) {
                if (event.actorProfilePhotoUrl != null) {
                    AsyncImage(
                        model = event.actorProfilePhotoUrl,
                        contentDescription = event.actorDisplayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = event.actorDisplayName.firstOrNull()?.uppercase() ?: "•",
                        fontFamily = typography.display,
                        fontSize = 18.sp,
                        color = colors.cream,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(19.dp)
                    .clip(CircleShape)
                    // One tier further than the avatar behind it — same "outranks its
                    // neighbor" cascade as everywhere else this ladder is used.
                    .background(colors.overlayPanel)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(badgeTint),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = iconFor(event.type), contentDescription = null, tint = badgeIconTint, modifier = Modifier.size(10.dp))
            }
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = event.message,
                fontFamily = typography.body,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.cream,
                lineHeight = 19.sp,
                maxLines = 2,
            )
            Text(
                text = formatRelativeTime(event.createdAt),
                fontFamily = typography.body,
                fontSize = 11.sp,
                fontWeight = if (event.warn) FontWeight.SemiBold else FontWeight.Normal,
                color = if (event.warn) colors.glow else colors.mutedDim,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (event.photoUrl != null) {
            AsyncImage(
                model = event.photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
    }
}

/** Fills the space with a direct explanation and next step, rather than a stray line of grey
 * text floating in the middle of the screen. */
@Composable
private fun EmptyActivityState(modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(colors.glow.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.NotificationsNone, contentDescription = null, tint = colors.glow, modifier = Modifier.size(30.dp))
        }
        Text(
            text = "Nothing here yet",
            fontFamily = typography.display,
            fontSize = 19.sp,
            color = colors.cream,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = "Send or receive a photo and it'll show up here",
            fontFamily = typography.body,
            fontSize = 12.5.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Same label treatment as Settings' own section headers — title case, no letter-spacing,
 * sitting above a plain panel rather than the old uppercase/tracked-out caption. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = colors.mutedDim,
        modifier = modifier,
    )
}
