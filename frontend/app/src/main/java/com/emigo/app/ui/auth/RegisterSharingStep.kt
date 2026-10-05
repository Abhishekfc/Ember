package com.emigo.app.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.R
import com.emigo.app.ui.theme.PublicSansFontFamily

/** Last sign-up step: invite a first friend, since an account with no friends has nothing to see.
 * A quick row of the most-used apps, then a labelled list for the rest (icons: [rememberAppIcon]).
 */
@Composable
internal fun RegisterSharingStep(viewModel: LoginViewModel, onAuthenticated: () -> Unit) {
    val colors = AuthPalette
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val username = viewModel.usernameDraft.trim()
    val inviteMessage = stringResource(
        if (username.isEmpty()) R.string.invite_message else R.string.invite_message_with_username,
        username,
    )

    val instagramLabel = stringResource(R.string.invite_target_instagram)
    val snapchatLabel = stringResource(R.string.invite_target_snapchat)
    val messagesLabel = stringResource(R.string.invite_target_messages)
    val moreLabel = stringResource(R.string.invite_target_more)
    val quickTargets = remember(instagramLabel, snapchatLabel, messagesLabel, moreLabel) {
        listOf(
            InviteTarget(instagramLabel, Icons.Filled.PhotoCamera, "com.instagram.android", R.drawable.ic_invite_instagram),
            InviteTarget(snapchatLabel, Icons.Filled.PhotoCamera, "com.snapchat.android", R.drawable.ic_invite_snapchat),
            InviteTarget(messagesLabel, Icons.Filled.Sms, null),
            InviteTarget(moreLabel, Icons.Filled.MoreHoriz, null),
        )
    }

    // Fixed back button, scrollable middle, fixed bottom action. With everything in one scrolling
    // column, "skip" fell off-screen on short devices and finishing sign-up meant finding it.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 24.dp, end = 24.dp)) {
            AuthBackButton(onClick = viewModel::goBack)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            // Same heading style as every other step.
            Text(
                text = stringResource(R.string.invite_first_friend_title),
                fontFamily = AuthPalette.display,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.cream,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            Text(
                text = stringResource(R.string.invite_first_friend_subtitle),
                fontFamily = PublicSansFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SectionLabel(text = stringResource(R.string.invite_section_from), modifier = Modifier.padding(top = 32.dp, bottom = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                quickTargets.forEach { target ->
                    QuickInviteButton(
                        target = target,
                        onClick = { shareInvite(context, inviteMessage, target.packageName) },
                    )
                }
            }

            SectionLabel(text = stringResource(R.string.invite_section_link), modifier = Modifier.padding(top = 34.dp, bottom = 6.dp))

            InviteListRow(
                title = stringResource(R.string.invite_copy_link_title),
                subtitle = stringResource(R.string.invite_copy_link_subtitle),
                packageName = null,
                fallbackIcon = Icons.Rounded.Link,
                onClick = { clipboard.setText(AnnotatedString(inviteMessage)) },
            )
            InviteListRow(
                title = stringResource(R.string.invite_target_whatsapp),
                subtitle = stringResource(R.string.invite_whatsapp_subtitle),
                packageName = "com.whatsapp",
                fallbackIcon = Icons.Filled.Chat,
                onClick = { shareInvite(context, inviteMessage, "com.whatsapp") },
                drawableResId = R.drawable.ic_invite_whatsapp,
            )
            InviteListRow(
                title = stringResource(R.string.invite_instagram_dm_title),
                subtitle = stringResource(R.string.invite_instagram_dm_subtitle),
                packageName = "com.instagram.android",
                fallbackIcon = Icons.Filled.PhotoCamera,
                onClick = {
                    clipboard.setText(AnnotatedString(inviteMessage))
                    openInstagram(context, "instagram://direct-inbox", inviteMessage)
                },
                drawableResId = R.drawable.ic_invite_instagram,
            )
            InviteListRow(
                title = stringResource(R.string.invite_instagram_story_title),
                subtitle = stringResource(R.string.invite_instagram_story_subtitle),
                packageName = "com.instagram.android",
                fallbackIcon = Icons.Filled.PhotoCamera,
                onClick = {
                    clipboard.setText(AnnotatedString(inviteMessage))
                    openInstagram(context, "instagram://story-camera", inviteMessage)
                },
                drawableResId = R.drawable.ic_invite_instagram,
            )
            InviteListRow(
                title = stringResource(R.string.invite_target_telegram),
                subtitle = stringResource(R.string.invite_telegram_subtitle),
                packageName = "org.telegram.messenger",
                fallbackIcon = Icons.AutoMirrored.Filled.Send,
                onClick = { shareInvite(context, inviteMessage, "org.telegram.messenger") },
            )
            InviteListRow(
                title = stringResource(R.string.invite_target_messages),
                subtitle = stringResource(R.string.invite_sms_subtitle),
                packageName = null,
                fallbackIcon = Icons.Filled.Sms,
                onClick = { shareInvite(context, inviteMessage, null) },
            )

            // Keeps the last row from touching the fixed footer when the list scrolls to the end.
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Fixed footer outside the scroll, so it's always visible. A real button, not styled text.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            AuthSecondaryButton(
                text = stringResource(R.string.invite_skip),
                onClick = onAuthenticated,
            )

            // Deliberately dim and small so it reads as a closing line, not a second button.
            Text(
                text = stringResource(R.string.invite_tagline),
                fontFamily = PublicSansFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = colors.mutedDim.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp),
            )
        }
    }
}

/** The small uppercase group heading above each block of invite options. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        color = AuthPalette.mutedDim,
        modifier = modifier,
    )
}

/** One app in the quick row: a large icon with its name beneath. */
@Composable
private fun QuickInviteButton(target: InviteTarget, onClick: () -> Unit) {
    val colors = AuthPalette
    // Only looked up when there's no bundled artwork (see rememberAppIcon).
    val appIcon = if (target.drawableResId == null) rememberAppIcon(target.packageName) else null

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        InviteIcon(appIcon = appIcon, drawableResId = target.drawableResId, fallbackIcon = target.fallbackIcon, size = 62.dp, glyphSize = 26.dp)
        Text(
            text = target.label,
            fontFamily = PublicSansFontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = colors.muted,
            maxLines = 1,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** One row of the labelled share list — icon, what it is, what it does, and a chevron. */
@Composable
private fun InviteListRow(
    title: String,
    subtitle: String,
    packageName: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    drawableResId: Int? = null,
) {
    val colors = AuthPalette
    // Only looked up when there's no bundled artwork (see rememberAppIcon).
    val appIcon = if (drawableResId == null) rememberAppIcon(packageName) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InviteIcon(appIcon = appIcon, drawableResId = drawableResId, fallbackIcon = fallbackIcon, size = 46.dp, glyphSize = 21.dp)
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = title,
                fontFamily = PublicSansFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.cream,
            )
            Text(
                text = subtitle,
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.mutedDim,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Bundled artwork if the target has it ([InviteTarget.drawableResId]), else the installed app's
 * launcher icon, else a plain glyph. */
@Composable
private fun InviteIcon(
    appIcon: ImageBitmap?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    glyphSize: androidx.compose.ui.unit.Dp,
    drawableResId: Int? = null,
) {
    val colors = AuthPalette
    if (drawableResId != null) {
        // Already the brand's own artwork and colors — no tint, and nothing drawn behind it.
        Image(
            painter = androidx.compose.ui.res.painterResource(drawableResId),
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    } else if (appIcon != null) {
        // Already the brand's own artwork and colors — no tint, and nothing drawn behind it.
        Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = fallbackIcon, contentDescription = null, tint = colors.glow, modifier = Modifier.size(glyphSize))
        }
    }
}
