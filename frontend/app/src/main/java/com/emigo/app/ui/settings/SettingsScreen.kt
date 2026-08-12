package com.emigo.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.emigo.app.ui.components.LocalNavDockHeight
import com.emigo.app.ui.components.TabScreenScaffold
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import com.emigo.app.ui.theme.ThemeKey
import dev.chrisbanes.haze.HazeState

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
    currentAppIcon: AppIconKey,
    isGoldMember: Boolean,
    widgetBadge: String,
    notificationsEnabled: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    onCameraClick: () -> Unit,
    onProfileClick: () -> Unit,
    onThemeClick: () -> Unit,
    onAppIconClick: () -> Unit,
    onGoldClick: () -> Unit,
    onWidgetClick: () -> Unit,
    onBlockedUsersClick: () -> Unit,
    // Delete account no longer lives on this screen at all — see OtherSettingsScreen, reached
    // through this one plain "Other" row, for why: it's rare and serious enough that it shouldn't
    // sit visible (and tappable) among the routine rows every time Settings opens.
    onOtherClick: () -> Unit,
    onSignOut: () -> Unit,
    hazeState: HazeState,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val context = LocalContext.current

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "—"
    }

    // No pull-to-refresh — nothing on this screen is fetched from the network, so there's
    // nothing for a pull gesture to refresh (isRefreshing/onRefresh both left at their defaults).
    TabScreenScaffold(
        title = "Settings",
        hazeState = hazeState,
        // Clears the floating nav dock rather than just the system nav bar behind it — same
        // reserve Home's own scrollable content uses, so Log out is reachable on every device,
        // not just tall ones. Plus a bit of extra breathing room on top of the dock's own real
        // height, so Log out doesn't sit right at the very edge of it.
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = LocalNavDockHeight.current + 24.dp),
    ) {
        item(key = "profile") {
            // The one identity element every comparable app leads Settings with — tapping your
            // own name/photo to edit your profile.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                            modifier = Modifier.size(56.dp).clip(CircleShape),
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
        }

        item(key = "gold") {
            FlatSettingsRow(
                label = "Emigo Gold",
                badge = if (isGoldMember) "Gold" else "Free",
                onClick = onGoldClick,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        item(key = "preferences-header") {
            SectionLabel(text = "Preferences", modifier = Modifier.padding(top = 22.dp, bottom = 2.dp))
        }
        item(key = "notifications") {
            Row(
                // Same explicit floor as FlatSettingsRow below — without it, this row's real
                // content height was governed by the Switch (~32dp) while every other row's was
                // governed by a plain icon (~20dp), so rows ended up visibly different heights
                // once the old icon-badge circle (which happened to be tall enough to mask that
                // difference) was removed.
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Notifications",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.cream,
                    modifier = Modifier.weight(1f),
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
        }
        item(key = "appearance") {
            FlatSettingsRow("Appearance", currentTheme.displayName, onThemeClick)
        }
        item(key = "app-icon") {
            FlatSettingsRow("App icon", currentAppIcon.displayName, onAppIconClick)
        }
        item(key = "widget") {
            // widgetBadge reads "Anyone" (the default every account starts with) or a friend
            // count once a Gold subscriber has chosen who to feature — see WidgetSettingsScreen,
            // which is where choosing anyone at all is actually gated.
            FlatSettingsRow("Widget", widgetBadge, onWidgetClick)
        }

        item(key = "privacy-header") {
            SectionLabel(text = "Privacy", modifier = Modifier.padding(top = 22.dp, bottom = 2.dp))
        }
        item(key = "blocked") {
            FlatSettingsRow("Blocked accounts", null, onBlockedUsersClick)
        }

        item(key = "support-header") {
            SectionLabel(text = "Support", modifier = Modifier.padding(top = 22.dp, bottom = 2.dp))
        }
        item(key = "help") {
            FlatSettingsRow("Help & support", null, { openSupportEmail(context, "Emigo support") })
        }
        item(key = "feedback") {
            FlatSettingsRow("Send feedback", null, { openSupportEmail(context, "Emigo feedback") })
        }
        item(key = "about") {
            FlatSettingsRow("About Emigo", versionName, null)
        }

        item(key = "other") {
            // Deliberately generic — what's actually inside (Delete account) isn't named here at
            // all, so it's not sitting in front of the user every time this screen opens.
            FlatSettingsRow("Other", null, onOtherClick)
        }

        item(key = "logout") {
            // The one loud element on an otherwise quiet screen — a light pill rather than
            // Ember's usual gradient-glow button, because signing out isn't a positive action
            // worth the same visual weight as "send a photo". Deliberately a full capsule (28dp
            // radius on a ~52dp-tall row), not the app's standard card radius.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp)
                    .background(colors.cream, RoundedCornerShape(28.dp))
                    .clickable(onClick = onSignOut)
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = Color(0xFFB3261E), modifier = Modifier.size(16.dp))
                Text(
                    text = "Log out",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFB3261E),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FlatSettingsRow(
    label: String,
    badge: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = PublicSansFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.cream,
            modifier = Modifier.weight(1f),
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

/** Shared with MyProfileScreen (`internal`, not `private`) — same section-header treatment
 * rather than a second near-identical composable defined there. */
@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
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

// Delete account moved to OtherSettingsScreen.kt entirely — see that file for
// DeleteAccountDestructiveColor and the DeleteAccountDialog itself.
