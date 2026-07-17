package com.ember.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ember.app.ui.theme.EmberAppTheme
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.ThemeKey
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

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
) {
    val colors = EmberTheme.colors
    val dockShape = RoundedCornerShape(percent = 50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
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
                        base.background(colors.panel)
                    }
                }
                .border(1.dp, if (colors.isLight) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.16f), dockShape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem(NavDestination.HOME, Icons.Outlined.Image, Icons.Filled.Image, active, onNavigate)
            NavItem(NavDestination.FRIENDS, Icons.Outlined.People, Icons.Filled.People, active, onNavigate)
            CameraButton(onCameraClick)
            NavItem(NavDestination.ACTIVITY, Icons.Outlined.Notifications, Icons.Filled.Notifications, active, onNavigate)
            NavItem(NavDestination.SETTINGS, Icons.Outlined.Settings, Icons.Filled.Settings, active, onNavigate)
        }
    }
}

@Composable
private fun CameraButton(onCameraClick: () -> Unit) {
    val colors = EmberTheme.colors
    val buttonSizePx = with(LocalDensity.current) { Size(38.dp.toPx(), 38.dp.toPx()) }

    Box(
        modifier = Modifier
            .size(38.dp)
            .shadow(4.dp, CircleShape, ambientColor = colors.glow.copy(alpha = 0.45f), spotColor = colors.glow.copy(alpha = 0.45f))
            .clip(CircleShape)
            .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx))
            .clickable(onClick = onCameraClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PhotoCamera,
            contentDescription = "Camera",
            tint = colors.accentText,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun NavItem(
    destination: NavDestination,
    outlinedIcon: ImageVector,
    filledIcon: ImageVector,
    active: NavDestination,
    onNavigate: (NavDestination) -> Unit,
) {
    val colors = EmberTheme.colors
    val isActive = destination == active
    val dimColor = if (colors.isLight) Color.Black else Color.White

    Box(
        modifier = Modifier
            .width(38.dp)
            .clip(CircleShape)
            .background(if (isActive) colors.glow.copy(alpha = 0x1A / 255f) else Color.Transparent)
            .clickable { onNavigate(destination) }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isActive) filledIcon else outlinedIcon,
            contentDescription = destination.label,
            tint = if (isActive) colors.glow else dimColor.copy(alpha = 0.45f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101E)
@Composable
private fun BottomNavDockPreview() {
    EmberAppTheme(themeKey = ThemeKey.EMBER) {
        BottomNavDock(active = NavDestination.HOME, onNavigate = {}, onCameraClick = {})
    }
}
