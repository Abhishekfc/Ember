package com.emigo.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.R
import com.emigo.app.ui.theme.CourgetteFontFamily
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily

/** The "Emigo" wordmark plus the activity and profile buttons. Kept outside the scrollable and
 * pull-to-refresh area in [HomeScreen] so it acts like a fixed top bar. */
@Composable
internal fun HomeBrandHeader(
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
            // Matches TabScreenHeader (top = 0, bottom = 12.dp), which was built to mirror this row.
            .padding(top = 0.dp, bottom = 12.dp, start = 22.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontFamily = CourgetteFontFamily,
            fontSize = 34.sp,
            letterSpacing = (-0.5).sp,
            // Neutral cream, not the theme accent, which looked distracting here.
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

/** The activity bell next to the profile avatar, where most apps put notifications. Same
 * panel-toned circle as [ProfileIconButton] so the pair reads as one unit; the numbered badge
 * uses the same pill as BottomNavDock's. */
@Composable
private fun ActivityBellButton(badgeCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors

    Box(
        modifier = modifier
            .size(44.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Same 44.dp as the touch-target Box, so the circle fills it and the icon reads bigger
        // without growing the row.
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.home_activity_description), tint = colors.cream, modifier = Modifier.size(24.dp))
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

/** An invisible stand-in for [HomeBrandHeader] that [com.emigo.app.ui.camera.CameraScreen]
 * renders so its card reserves the same vertical space as Home's header, without depending on a
 * value only Home can produce. (The "Couldn't connect" line and unseen-count banners float via
 * [overlayNoHeight], take no space, and aren't reproduced.) The old approach, Home reporting its
 * measured height up to MainActivity for Camera to read, put the card too high on first open
 * (Camera is the opening page, so the height was still zero) and made it jump down on the first
 * visit to Home. */
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

/** Like [HomeHeaderHeightTwin], for the row below it: Home has [HomeViewModeToggleRow] between
 * header and card, Camera has nothing there, which made Camera's card sit higher. Camera renders
 * this invisibly to reserve that space. Built from the real composable with placeholder values
 * and the call site's padding, so it can't drift like a hand-copied dp constant. */
@Composable
internal fun HomeViewModeToggleHeightTwin(metrics: HomeFoldMetrics = HomeFoldRoomy) {
    // Every dimension must match Home's call site, or Camera's card lands at a different height.
    // It drifted once (Home's top gap moved 12dp to 20dp, leaving Camera's card ~8dp higher); both
    // now read the same HomeFoldMetrics.
    HomeViewModeToggleRow(
        mode = HomeViewMode.HOME,
        onModeChange = {},
        metrics = metrics,
        // Half of toggleTopGap; must match the real call site.
        modifier = Modifier.fillMaxWidth().padding(top = metrics.toggleTopGap / 2, start = 22.dp, end = 22.dp),
    )
}

/** The profile button in the header: a plain profile glyph, not the account photo. Matches
 * [ActivityBellButton] (same touch target, panel-toned circle, icon size and tint). */
@Composable
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
            Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.home_profile_description), tint = colors.cream, modifier = Modifier.size(24.dp))
        }
    }
}

/** Home's two views: the carousel with the avatar row (default), or a grid of every friend's card.
 * Switched via [HomeViewModeToggleRow]. Stored on [HomeViewModel], not as local state: this
 * composable is torn down whenever the pager scrolls Home out of view, so a plain `remember`
 * reset to HOME on every return. */
internal enum class HomeViewMode { HOME, MOMENTS }

/** Two independent pills (not a connected segmented control), each with its own selected and
 * unselected look. Both share one fixed width: sized to their own labels, "Moments" came out wider
 * than "Home" and read as primary versus secondary. Occupies the slot the old share-prompt row
 * used; CameraScreen reserves the same space (see [HomeViewModeToggleHeightTwin]). */
@Composable
internal fun HomeViewModeToggleRow(
    mode: HomeViewMode,
    onModeChange: (HomeViewMode) -> Unit,
    modifier: Modifier = Modifier,
    // Defaulted so Camera's height twin (and previews) keep the original sizing without Home's
    // measurements.
    metrics: HomeFoldMetrics = HomeFoldRoomy,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
    ) {
        HomeViewModePill(
            label = stringResource(R.string.nav_home),
            selected = mode == HomeViewMode.HOME,
            onClick = { onModeChange(HomeViewMode.HOME) },
            metrics = metrics,
        )
        HomeViewModePill(
            label = stringResource(R.string.home_mode_moments),
            selected = mode == HomeViewMode.MOMENTS,
            onClick = { onModeChange(HomeViewMode.MOMENTS) },
            metrics = metrics,
        )
    }
}

/** Text only, no icon, and no theme accent: selected is a filled neutral chip (panel, the tone the
 * featured card's dot row and badges sit on), unselected a plain outline, with cream vs muted text. */
@Composable
private fun HomeViewModePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metrics: HomeFoldMetrics = HomeFoldRoomy,
) {
    val colors = EmberTheme.colors
    Box(
        modifier = modifier
            .width(metrics.pillWidth)
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
            // Scales with the fold (11dp roomy, 8dp compact); on a tight screen this is height the
            // card needs.
            .padding(vertical = metrics.pillVerticalPadding),
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
