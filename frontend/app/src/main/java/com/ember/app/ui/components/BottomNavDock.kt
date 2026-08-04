package com.ember.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ember.app.ui.theme.EmberAppTheme
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.ThemeKey
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** A generous guess for the one frame before the dock's own real [onSizeChanged] below has
 * landed — every screen that needs to keep content clear of the floating dock should read
 * [LocalNavDockHeight] instead of this directly. Internal (not private) purely so MainActivity
 * can seed [LocalNavDockHeight]'s hoisted state with the same starting value, rather than a
 * second copy of this same magic number living in a different file. */
internal val FALLBACK_NAV_DOCK_HEIGHT_DP = 140.dp

/** The dock's own true rendered height on THIS device — footprint and real system nav-bar inset
 * both included, never a guessed constant. One fixed dp number can't cover every OEM's actual
 * navigationBarsPadding() value (gesture nav vs. 3-button nav vs. taller custom skins all
 * differ), which is exactly what still left the Settings screen's Log out button partly covered
 * by the dock on at least one real device even after a generous fixed reserve. Provided once
 * near the root (see MainActivity, wired from this composable's own onSizeChanged below), read
 * wherever a screen needs to keep content clear of the dock (Settings' trailing spacer, Home's
 * Memories grid padding + top-fold height clamp, Memories' day-card centering). */
val LocalNavDockHeight = compositionLocalOf { FALLBACK_NAV_DOCK_HEIGHT_DP }

enum class NavDestination(val label: String) {
    HOME("Home"),
    FRIENDS("Friends"),
    ACTIVITY("Activity"),
    SETTINGS("Settings"),
}

/**
 * A minimal glassmorphism nav pill: Home, Friends, Camera, Activity, Settings in a single row
 * with even spacing between every element. Real backdrop blur via [hazeState]. Theme is
 * deliberately not a nav destination — it's reachable only from within Settings.
 *
 * IMPORTANT: [hazeState]'s `hazeSource` must be scoped to the scrolling content ONLY (e.g. the
 * screen's Column/LazyColumn), never to a shared ancestor Box that also contains this dock —
 * if the source region includes this effect node as a descendant, the captured layer can include
 * the dock's own stale pixels and produce a ghosting/misalignment artifact.
 */
@Composable
fun BottomNavDock(
    active: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    // 0 = plain Home icon, 1 = fully morphed into the Memories/image icon — driven live by how
    // far Home has been scrolled into its own inline Memories section, not an independent
    // animation, so the icon tracks the scroll itself rather than snapping once it settles.
    // Every other screen just leaves this at the default (0), showing a plain Home icon same
    // as before.
    //
    // A provider, not a plain Float — the caller's scroll position changes every frame of a
    // drag, and reading it directly at the call site would make THIS COMPOSABLE's whole
    // recomposition scope depend on it, forcing the entire dock (plus whatever shares its
    // recomposition scope one level up, alongside the pager itself) to recompose 60 times a
    // second during every scroll. Reading it lazily inside HomeMemoriesNavItem's graphicsLayer
    // (a draw-phase callback) keeps that to a cheap per-frame redraw of two small icons instead.
    homeIconProgress: () -> Float = { 0f },
    // Small dot badges — Friends for a pending incoming friend request, Activity for activity
    // that's happened since the tab was last actually viewed (see MainActivity's own doc comments
    // on where each of these is computed; this composable just renders whatever it's handed).
    showFriendsBadge: Boolean = false,
    showActivityBadge: Boolean = false,
    // Reports this Box's own real laid-out height — after navigationBarsPadding() and this
    // Box's own bottom inset are both applied — so callers can feed it into LocalNavDockHeight
    // instead of a guessed dp constant. Fires once per real layout pass, essentially only once
    // per device/orientation in practice.
    onHeightMeasured: (Dp) -> Unit = {},
) {
    val colors = EmberTheme.colors
    val density = LocalDensity.current
    val dockShape = RoundedCornerShape(percent = 50)

    Box(
        // onSizeChanged has to come BEFORE navigationBarsPadding()/padding() below, not after —
        // a modifier placed after padding in the chain only observes the already-shrunk content
        // size, not the padding that was applied around it. Placed last (a real bug, since
        // fixed), it silently reported just the inner Row's own height (~74dp), missing the
        // system nav-bar inset and this Box's own 26dp bottom padding entirely — a far smaller
        // number than the fixed dp guess it was meant to replace, which is exactly what made
        // content on Home/Settings/Memories start overlapping the dock instead of clearing it.
        modifier = modifier
            .onSizeChanged { onHeightMeasured(with(density) { it.height.toDp() }) }
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 26.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, dockShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(dockShape)
                .let { base ->
                    if (hazeState != null) {
                        base.hazeEffect(hazeState) {
                            style = HazeStyle(
                                backgroundColor = if (colors.isLight) Color.White else Color.Black,
                                tints = listOf(HazeTint(if (colors.isLight) Color.White.copy(alpha = 0.5f) else colors.panel.copy(alpha = 0.6f))),
                                blurRadius = 26.dp,
                                noiseFactor = 0.12f,
                            )
                        }
                    } else {
                        // overlayPanel, not plain panel — this dock is the one genuinely floating
                        // element sitting permanently above every screen's content, the same tier
                        // a dialog or bottom sheet would use.
                        base.background(colors.overlayPanel)
                    }
                }


                // Without this, only the individual icons themselves are actually clickable —
                // a tap landing in the gaps between them (or the pill's own padding) falls
                // straight through to whatever content is scrolled underneath at those same
                // coordinates, since the dock only visually sits on top, it doesn't otherwise
                // block touches from reaching what's behind it. A no-op clickable across the
                // whole pill absorbs every tap here; the icons' own clickables underneath still
                // win first for taps actually on them.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HomeMemoriesNavItem(active, homeIconProgress, onNavigate)
            NavItem(NavDestination.FRIENDS, Icons.Filled.People, active, onNavigate, showBadge = showFriendsBadge)
            CameraButton(onCameraClick)
            NavItem(NavDestination.ACTIVITY, Icons.Filled.Notifications, active, onNavigate, showBadge = showActivityBadge)
            NavItem(NavDestination.SETTINGS, Icons.Filled.Settings, active, onNavigate)
        }
    }
}

/** The one bold, warm element in an otherwise monochrome row — every other accent (the active
 * tab, the border) was pulled back to neutral specifically so this button is the single thing
 * carrying brand color, rather than splitting that signature across two competing spots. Sized
 * as the dock's primary action (44dp+ touch target, same accessibility floor as every other
 * control here) rather than a small icon that happened to get a gradient. */
@Composable
private fun CameraButton(onCameraClick: () -> Unit) {
    val colors = EmberTheme.colors
    val buttonSizePx = with(LocalDensity.current) { Size(54.dp.toPx(), 54.dp.toPx()) }

    Box(
        modifier = Modifier
            .size(54.dp)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(CircleShape)
            .background(emberButtonBrush(EmberTheme.key, colors, buttonSizePx))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCameraClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = "Camera",
            tint = colors.accentText,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** The Home slot specifically: Memories lives inline inside Home's own scrollable content now
 * (see HomeScreen), so this icon morphs between a house and a calendar glyph as [progress] moves
 * from 0 (top of Home) to 1 (scrolled into Memories) — two crossfaded icons rather than a hard
 * swap, so the icon tracks the live scroll the same way the content itself does, not a snap once
 * it settles. Calendar, not a generic image/gallery icon, since Memories itself is a month grid
 * of days, not a plain photo library. */
@Composable
private fun HomeMemoriesNavItem(
    active: NavDestination,
    progress: () -> Float,
    onNavigate: (NavDestination) -> Unit,
) {
    val colors = EmberTheme.colors
    val isActive = active == NavDestination.HOME

    val tint by animateColorAsState(
        targetValue = if (isActive) colors.cream else colors.muted,
        animationSpec = tween(200),
        label = "homeMemoriesNavItemTint",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNavigate(NavDestination.HOME) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Home,
            contentDescription = "Home",
            tint = tint,
            modifier = Modifier.size(23.dp).graphicsLayer { alpha = 1f - progress() },
        )
        Icon(
            imageVector = Icons.Filled.CalendarMonth,
            contentDescription = "Memories",
            tint = tint,
            modifier = Modifier.size(23.dp).graphicsLayer { alpha = progress() },
        )
    }
}

/** No background wash behind the active icon — that would be a second, competing accent on top
 * of the camera button's gradient. Every icon is always the solid/filled glyph now (bolder,
 * heavier-looking than the old outlined-vs-filled pairing) — the active state reads through
 * color and contrast alone: full-strength `cream` versus `muted`, the same neutral pairing the
 * rest of the app already uses for primary-vs-secondary text. */
@Composable
private fun NavItem(
    destination: NavDestination,
    icon: ImageVector,
    active: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    showBadge: Boolean = false,
) {
    val colors = EmberTheme.colors
    val isActive = destination == active

    val tint by animateColorAsState(
        targetValue = if (isActive) colors.cream else colors.muted,
        animationSpec = tween(200),
        label = "navItemTint",
    )

    Box(
        // 44dp is the accessibility touch-target floor — the previous 38dp-wide, 8dp-padded
        // box rendered at only 36dp tall, under that minimum.
        //
        // Deliberately no .clip(CircleShape) here (indication = null means there's no ripple for
        // it to bound anyway) — the badge below sits at this Box's TopEnd corner, and a corner
        // falls outside a circle inscribed in the same bounds, so that clip was silently slicing
        // the badge down to the thin crescent that was the whole bug report.
        modifier = Modifier
            .size(44.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNavigate(destination) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = destination.label,
            tint = tint,
            modifier = Modifier.size(23.dp),
        )
        if (showBadge) {
            // Plain flat dot now — no cutout-ring border around it any more.
            //
            // Aligned to this Box's own TopEnd, but this Box is the full 44dp touch target, not
            // the 23dp icon glyph centered inside it — a plain TopEnd (or a small outward offset)
            // put the dot near the touch target's own corner, visibly far from the icon it's
            // meant to badge. The offset below instead pulls it in to sit right against the
            // icon's own top-right corner. Bigger too (12dp, was 9dp) — small enough to still read
            // as a dot, not so small it looked like a soft glowing pinpoint rather than a solid one.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-7).dp, y = 7.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(colors.glow),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101E)
@Composable
private fun BottomNavDockPreview() {
    EmberAppTheme(themeKey = ThemeKey.EMBER) {
        BottomNavDock(active = NavDestination.HOME, onNavigate = {}, onCameraClick = {})
    }
}
