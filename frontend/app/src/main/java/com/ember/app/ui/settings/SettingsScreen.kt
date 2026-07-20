package com.ember.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.ui.components.BottomNavDock
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import com.ember.app.ui.theme.ThemeKey
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private data class SettingsRow(
    val icon: ImageVector,
    val label: String,
    val badge: String?,
    val onClick: () -> Unit,
)

@Composable
fun SettingsScreen(
    currentTheme: ThemeKey,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onCameraClick: () -> Unit,
    onThemeClick: () -> Unit,
    onGoldClick: () -> Unit,
    onSignOut: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val hazeState = rememberHazeState()
    val panelShape = RoundedCornerShape(18.dp)

    val rows = listOf(
        SettingsRow(Icons.Filled.Palette, "Theme", currentTheme.displayName, onThemeClick),
        SettingsRow(Icons.Filled.AutoAwesome, "Ember Gold", "Free", onGoldClick),
        SettingsRow(Icons.Filled.People, "Friends", null) { onNavigate(NavDestination.FRIENDS) },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            Text(
                text = "Settings",
                fontFamily = typography.display,
                fontSize = 22.sp,
                color = colors.cream,
                modifier = Modifier.padding(top = 68.dp, start = 20.dp, end = 20.dp, bottom = 22.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(colors.panel, panelShape)
                    .border(1.dp, colors.border, panelShape),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = colors.glow, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Notifications",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.5.sp,
                        color = colors.cream,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = onNotificationsChange,
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = colors.glow,
                            checkedThumbColor = colors.accentText,
                            uncheckedTrackColor = colors.border,
                            uncheckedThumbColor = colors.mutedDim,
                            uncheckedBorderColor = colors.border,
                        ),
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.border))

                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = row.onClick)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(row.icon, contentDescription = null, tint = colors.glow, modifier = Modifier.size(16.dp))
                        Text(
                            text = row.label,
                            fontFamily = PublicSansFontFamily,
                            fontSize = 13.5.sp,
                            color = colors.cream,
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                        if (row.badge != null) {
                            Text(text = row.badge, fontFamily = PublicSansFontFamily, fontSize = 11.5.sp, color = colors.mutedDim)
                        }
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = colors.mutedDim,
                            modifier = Modifier.padding(start = 8.dp).size(15.dp),
                        )
                    }
                    if (index < rows.lastIndex) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.border))
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp)
                    .background(colors.panel, panelShape)
                    .border(1.dp, colors.border, panelShape)
                    .clickable(onClick = onSignOut)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFE8756C), modifier = Modifier.size(16.dp))
                Text(
                    text = "Sign out",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 13.5.sp,
                    color = Color(0xFFE8756C),
                    modifier = Modifier.padding(start = 10.dp),
                )
            }
        }

        BottomNavDock(
            active = NavDestination.SETTINGS,
            onNavigate = onNavigate,
            onCameraClick = onCameraClick,
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
        )
    }
}
