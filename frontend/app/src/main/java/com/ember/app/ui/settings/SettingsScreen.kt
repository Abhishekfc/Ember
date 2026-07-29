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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Article
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Feedback
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Widgets
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

/** Flat, straight on the screen's own background — no panel behind any row. Same language as
 * the Friends list: clarity comes from generous spacing and one consistent, quiet color accent
 * (see [SettingsIconBadge]) per row, not from a card boundary. */
@Composable
fun SettingsScreen(
    displayName: String?,
    username: String?,
    profilePhotoUrl: String?,
    currentTheme: ThemeKey,
    isGoldMember: Boolean,
    widgetBadge: String,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onCameraClick: () -> Unit,
    onProfileClick: () -> Unit,
    onThemeClick: () -> Unit,
    onGoldClick: () -> Unit,
    onWidgetClick: () -> Unit,
    onSignOut: () -> Unit,
    hazeState: HazeState,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val context = LocalContext.current
    var screenSize by remember { mutableStateOf(Size.Zero) }

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState).statusBarsPadding()) {
            // Centered, plain title — Settings is a bottom-nav tab (swipe between Home/Friends/
            // .../Settings), not a screen pushed on top of one you'd back out of, so no back
            // arrow.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 26.dp), contentAlignment = Alignment.Center) {
                Text(text = "Settings", fontFamily = PublicSansFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colors.cream)
            }

            // The one identity element every comparable app leads Settings with — tapping your
            // own name/photo to edit your profile.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .clickable(onClick = onProfileClick)
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.glow.copy(alpha = 0.16f)),
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
                            fontSize = 20.sp,
                            color = colors.glow,
                        )
                    }
                }
                Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        text = displayName ?: "Your account",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.cream,
                    )
                    if (username != null) {
                        Text(
                            text = "@$username",
                            fontFamily = PublicSansFontFamily,
                            fontSize = 12.5.sp,
                            color = colors.mutedDim,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.mutedDim, modifier = Modifier.size(16.dp))
            }

            FlatSettingsRow(
                icon = Icons.Rounded.AutoAwesome,
                label = "Ember Gold",
                badge = if (isGoldMember) "Gold" else "Free",
                onClick = onGoldClick,
                modifier = Modifier.padding(top = 8.dp),
            )

            SectionLabel(text = "Preferences", modifier = Modifier.padding(top = 22.dp, start = 24.dp, bottom = 2.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsIconBadge(Icons.Rounded.NotificationsNone)
                Text(
                    text = "Notifications",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.cream,
                    modifier = Modifier.padding(start = 14.dp).weight(1f),
                )
                // Switch's default touch target (48dp) is taller than this row's own content —
                // stripped to its intrinsic size so it doesn't force the row taller than its
                // plain-icon siblings above/below.
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
            FlatSettingsRow(Icons.Rounded.Palette, "Appearance", currentTheme.displayName, onThemeClick)
            // widgetBadge reads "Anyone" (the default every account starts with) or a friend
            // count once a Gold subscriber has chosen who to feature — see WidgetSettingsScreen,
            // which is where choosing anyone at all is actually gated.
            FlatSettingsRow(Icons.Rounded.Widgets, "Widget", widgetBadge, onWidgetClick)

            SectionLabel(text = "Support", modifier = Modifier.padding(top = 22.dp, start = 24.dp, bottom = 2.dp))
            FlatSettingsRow(Icons.AutoMirrored.Rounded.HelpOutline, "Help & support", null, { openSupportEmail(context, "Ember support") })
            FlatSettingsRow(Icons.Rounded.Feedback, "Send feedback", null, { openSupportEmail(context, "Ember feedback") })
            FlatSettingsRow(Icons.Rounded.Article, "About Ember", versionName, null)

            // The one loud element on an otherwise quiet screen — a light pill rather than
            // Ember's usual gradient-glow button, because signing out isn't a positive action
            // worth the same visual weight as "send a photo". Deliberately a full capsule (28dp
            // radius on a ~52dp-tall row), not the app's standard card radius.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(top = 26.dp)
                    .background(colors.cream, RoundedCornerShape(28.dp))
                    .clickable(onClick = onSignOut)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = Color(0xFFE8756C), modifier = Modifier.size(16.dp))
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

/** Small tinted circle behind every row's leading icon — one consistent, quiet touch of the
 * theme's own accent color instead of a plain grey glyph floating on its own, without needing a
 * card behind the whole row to read as "considered." Reused verbatim by every row so the accent
 * stays the same weight everywhere, rather than any one row reading as more important than its
 * neighbor purely because of its color. */
@Composable
private fun SettingsIconBadge(icon: ImageVector) {
    val colors = EmberTheme.colors
    Box(
        modifier = Modifier.size(34.dp).clip(CircleShape).background(colors.glow.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.glow, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun FlatSettingsRow(
    icon: ImageVector,
    label: String,
    badge: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIconBadge(icon)
        Text(
            text = label,
            fontFamily = PublicSansFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.cream,
            modifier = Modifier.padding(start = 14.dp).weight(1f),
        )
        if (badge != null) {
            Text(text = badge, fontFamily = PublicSansFontFamily, fontSize = 12.sp, color = colors.mutedDim)
        }
        if (onClick != null) {
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.mutedDim,
                modifier = Modifier.padding(start = 8.dp).size(15.dp),
            )
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
