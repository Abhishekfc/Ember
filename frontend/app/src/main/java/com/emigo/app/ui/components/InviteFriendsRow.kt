package com.emigo.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.ui.auth.InviteTarget
import com.emigo.app.ui.auth.rememberAppIcon
import com.emigo.app.ui.auth.shareInvite
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily

/** Quick "invite via Instagram/Snapchat/WhatsApp/More" row — reused wherever an account has
 * nobody to see yet (Find People's idle state, Friends' own "no friends yet" state) so inviting
 * someone who isn't on Ember yet is never more than one tap from a dead end. Reuses sign-up's own
 * share logic (real launcher icons, per-app deep links, system-chooser fallback — see
 * [InviteTarget]/[shareInvite]/[rememberAppIcon], made `internal` for this) rather than a second
 * copy of it; only the row/button styling is separate, since that flow is intentionally pinned to
 * a fixed `AuthPalette` regardless of the signed-in user's own theme, and every other screen
 * isn't. No username is threaded in here (unlike sign-up's own personalized "I'm @username"
 * message) — none of this row's callers have ready access to the current user's own profile, and
 * adding that plumbing for one word in a share message wasn't worth it; falls back to the same
 * plain message sign-up itself uses when there's no username yet. */
@Composable
fun InviteFriendsRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val inviteMessage = "Come add me on Emigo — it puts my photos right on your home screen."
    val quickTargets = remember {
        listOf(
            InviteTarget("Instagram", Icons.Filled.PhotoCamera, "com.instagram.android"),
            InviteTarget("Snapchat", Icons.Filled.PhotoCamera, "com.snapchat.android"),
            InviteTarget("WhatsApp", Icons.Filled.Chat, "com.whatsapp"),
            InviteTarget("More", Icons.Filled.MoreHoriz, null),
        )
    }

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        quickTargets.forEach { target ->
            InviteQuickButton(target = target, onClick = { shareInvite(context, inviteMessage, target.packageName) })
        }
    }
}

/** One app in the row — real launcher icon (or a plain glyph on the theme's own elevated panel
 * when that app isn't installed) with its name beneath. */
@Composable
private fun InviteQuickButton(target: InviteTarget, onClick: () -> Unit) {
    val colors = EmberTheme.colors
    val appIcon = rememberAppIcon(target.packageName)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        if (appIcon != null) {
            Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(56.dp).clip(CircleShape))
        } else {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.elevatedPanel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = target.fallbackIcon, contentDescription = null, tint = colors.glow, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            text = target.label,
            fontFamily = PublicSansFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.muted,
            maxLines = 1,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
