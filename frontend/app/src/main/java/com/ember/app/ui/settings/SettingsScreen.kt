package com.ember.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.font.FontWeight
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

/** Every settings row — plain chevron rows and the Notifications toggle row alike — reserves
 * this same minimum height, so a trailing Switch (whose own touch target is taller than a
 * chevron icon) can't make its row read as bigger than its siblings. */
private val SETTINGS_ROW_MIN_HEIGHT = 48.dp

/** Where "Help & Support" and "Send Feedback" open an email composer to — there's no dedicated
 * support inbox or feedback form yet, so both point at the same address for now. */
private const val SUPPORT_EMAIL = "abhisheksir6280@gmail.com"

private fun openSupportEmail(context: android.content.Context, subject: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    runCatching { context.startActivity(intent) }
}

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
    val panelShape = RoundedCornerShape(22.dp)

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    val goldRows = listOf(
        SettingsRow(Icons.Filled.AutoAwesome, "Ember Gold", "Free", onGoldClick),
    )
    val preferenceRows = listOf(
        SettingsRow(Icons.Filled.Palette, "Appearance", currentTheme.displayName, onThemeClick),
        // No widget exists yet — listed so the setting isn't a surprise omission from a
        // familiar app's settings screen, but it can't do anything until one's actually built.
        SettingsRow(Icons.Filled.Widgets, "Widget", "Soon", null),
    )
    val supportRows = listOf(
        SettingsRow(Icons.AutoMirrored.Filled.HelpOutline, "Help & support", null, { openSupportEmail(context, "Ember support") }),
        SettingsRow(Icons.AutoMirrored.Filled.Send, "Send feedback", null, { openSupportEmail(context, "Ember feedback") }),
        SettingsRow(Icons.Filled.Info, "About Ember", versionName, null),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState).statusBarsPadding()) {
            // Centered title, no leading back control — unlike the reference this was modeled
            // on, Settings here is a bottom-nav tab (swipe between Home/Friends/.../Settings),
            // not a screen pushed on top of one you'd back out of, so a back arrow would have
            // nothing real to do.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 22.dp), contentAlignment = Alignment.Center) {
                Text(text = "Settings", fontFamily = PublicSansFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.cream)
            }

            // The one identity element every comparable app leads Settings with — tapping your
            // own name/photo to edit your profile — which this screen had no path to at all
            // before (only Home's small header chip could get you there).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .background(colors.panel, panelShape)
                    .clickable(onClick = onProfileClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.border.copy(alpha = 0.4f)),
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
                        fontWeight = FontWeight.SemiBold,
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

            // Ember Gold gets its own card, standing apart from the plain preference/social/
            // support groups below — the one promotional row on the screen.
            SettingsGroup(rows = goldRows, panelShape = panelShape, modifier = Modifier.padding(top = 14.dp))

            SectionLabel(text = "Preferences", modifier = Modifier.padding(top = 20.dp, start = 24.dp, bottom = 6.dp))
            SettingsGroup(rows = preferenceRows, panelShape = panelShape) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = SETTINGS_ROW_MIN_HEIGHT).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Notifications, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                    Text(
                        text = "Notifications",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.cream,
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    // Switch's default touch target padding (48dp tall) is what made this row
                    // read as noticeably bigger than its plain chevron-row siblings — shrinking
                    // it to the switch's own intrinsic size brings the row back to the same
                    // height as every other row in this list.
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = onNotificationsChange,
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = colors.glow,
                                checkedThumbColor = colors.cream,
                                checkedBorderColor = colors.glow,
                                uncheckedTrackColor = colors.border,
                                uncheckedThumbColor = colors.cream,
                                uncheckedBorderColor = colors.border,
                            ),
                        )
                    }
                }
            }

            SectionLabel(text = "Support", modifier = Modifier.padding(top = 20.dp, start = 24.dp, bottom = 6.dp))
            SettingsGroup(rows = supportRows, panelShape = panelShape)

            // The one loud element on an otherwise quiet screen — a light pill rather than
            // Ember's usual gradient-glow button, because signing out isn't a positive action
            // worth the same visual weight as "send a photo".
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 22.dp)
                    .background(colors.cream, RoundedCornerShape(28.dp))
                    .clickable(onClick = onSignOut)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = Color(0xFFE8756C), modifier = Modifier.size(16.dp))
                Text(
                    text = "Log out",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE8756C),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

    }
}

/** One plain panel per section, no border/dividers — the reference this was modeled on
 * separates rows purely by card membership and the gap between cards, not hairlines, so
 * [SettingsGroup] leaves that gap to its own top padding at the call site instead of drawing
 * lines between rows. [extraContent] lets a group lead with something that isn't a plain
 * label/chevron row (here: the Notifications toggle) while still sharing the same panel chrome
 * as the rows that follow it. */
@Composable
private fun SettingsGroup(
    rows: List<SettingsRow>,
    panelShape: RoundedCornerShape,
    modifier: Modifier = Modifier,
    extraContent: (@Composable () -> Unit)? = null,
) {
    val colors = EmberTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .background(colors.panel, panelShape),
    ) {
        extraContent?.invoke()

        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = SETTINGS_ROW_MIN_HEIGHT)
                    .let { if (row.onClick != null) it.clickable(onClick = row.onClick) else it }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(row.icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                Text(
                    text = row.label,
                    fontFamily = PublicSansFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
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
        }
    }
}

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
