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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import com.ember.app.ui.theme.ThemeKey
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

private data class SettingsRow(
    val icon: ImageVector,
    val label: String,
    val badge: String?,
    val onClick: (() -> Unit)?,
)

@Composable
fun SettingsScreen(
    displayName: String?,
    username: String?,
    profilePhotoUrl: String?,
    currentTheme: ThemeKey,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onCameraClick: () -> Unit,
    onProfileClick: () -> Unit,
    onThemeClick: () -> Unit,
    onGoldClick: () -> Unit,
    onSignOut: () -> Unit,
    hazeState: HazeState,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val context = LocalContext.current
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val panelShape = RoundedCornerShape(18.dp)

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    val preferenceRows = listOf(
        SettingsRow(Icons.Filled.Palette, "Theme", currentTheme.displayName, onThemeClick),
    )
    val membershipRows = listOf(
        SettingsRow(Icons.Filled.AutoAwesome, "Ember Gold", "Free", onGoldClick),
    )
    val aboutRows = listOf(
        SettingsRow(Icons.Filled.Info, "Version", versionName, null),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            // fontSize matches Friends'/Activity's own page title exactly (26sp) — this was
            // 22sp, noticeably smaller than either, which is what made Settings read as
            // misaligned with the rest of the app when swiping directly between tabs even
            // though its top/side padding already matched them.
            Text(
                text = "Settings",
                fontFamily = typography.display,
                fontSize = 26.sp,
                color = colors.cream,
                modifier = Modifier.padding(top = 32.dp, start = 20.dp, end = 20.dp, bottom = 22.dp),
            )

            // The one identity element every comparable app leads Settings with — tapping your
            // own name/photo to edit your profile — which this screen had no path to at all
            // before (only Home's small header chip could get you there).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(colors.panel, panelShape)
                    .border(1.dp, colors.border, panelShape)
                    .clickable(onClick = onProfileClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(colors.panel)
                        .border(1.dp, colors.border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (profilePhotoUrl != null) {
                        AsyncImage(
                            model = profilePhotoUrl,
                            contentDescription = "Your profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = displayName?.firstOrNull()?.uppercase() ?: "•",
                            fontFamily = typography.display,
                            fontSize = 19.sp,
                            color = colors.cream,
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        text = displayName ?: "Your account",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 15.sp,
                        color = colors.cream,
                    )
                    if (username != null) {
                        Text(
                            text = "@$username",
                            fontFamily = PublicSansFontFamily,
                            fontSize = 12.sp,
                            color = colors.mutedDim,
                            modifier = Modifier.padding(top = 1.dp),
                        )
                    }
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.mutedDim, modifier = Modifier.size(16.dp))
            }

            SectionLabel(text = "PREFERENCES", modifier = Modifier.padding(top = 24.dp, start = 24.dp, bottom = 6.dp))
            SettingsGroup(rows = preferenceRows, panelShape = panelShape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
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
            }

            SectionLabel(text = "MEMBERSHIP", modifier = Modifier.padding(top = 20.dp, start = 24.dp, bottom = 6.dp))
            SettingsGroup(rows = membershipRows, panelShape = panelShape)

            SectionLabel(text = "ABOUT", modifier = Modifier.padding(top = 20.dp, start = 24.dp, bottom = 6.dp))
            SettingsGroup(rows = aboutRows, panelShape = panelShape)

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

    }
}

/** One bordered panel per section, rows separated by hairlines — the same grouped-list shape
 * used everywhere a settings-style row appears in this app, so Preferences/Membership/About
 * read as one consistent system rather than three different treatments. [extraContent] lets a
 * group lead with something that isn't a plain label/chevron row (here: the Notifications
 * toggle) while still sharing the same panel/divider chrome as the rows that follow it. */
@Composable
private fun SettingsGroup(
    rows: List<SettingsRow>,
    panelShape: RoundedCornerShape,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val colors = EmberTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(colors.panel, panelShape)
            .border(1.dp, colors.border, panelShape),
    ) {
        extraContent?.invoke()

        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (row.onClick != null) it.clickable(onClick = row.onClick) else it }
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
                if (row.onClick != null) {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.mutedDim,
                        modifier = Modifier.padding(start = 8.dp).size(15.dp),
                    )
                }
            }
            if (index < rows.lastIndex) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(colors.border))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    Text(
        text = text,
        fontFamily = typography.body,
        fontSize = 11.5.sp,
        letterSpacing = 0.8.sp,
        color = colors.mutedDim,
        modifier = modifier,
    )
}
