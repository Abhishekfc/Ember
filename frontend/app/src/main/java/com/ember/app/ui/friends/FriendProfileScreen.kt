package com.ember.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

/** One profile layout for every person — an existing friend and someone who's only sent a
 * pending request share the exact same header (avatar, name, username, streak). Only the action
 * area at the bottom differs: Send photo/Pin/Remove for a friend, Accept/Decline for a pending
 * request.
 *
 * Visual language is deliberately closer to Instagram/Snapchat's own profile screens than the
 * rest of Ember's more illustrated UI: a plain centered header, one quiet chip instead of a
 * card for the one piece of data that matters (the streak), a hairline rule separating identity
 * from action, and a strict two-tier button hierarchy (one solid, one outline). The only thing
 * that still marks this as *Ember* rather than a generic clone is the Fraunces serif on the
 * name — every other label uses the plain UI face. */
@Composable
fun FriendProfileScreen(
    viewModel: FriendProfileViewModel,
    onBack: () -> Unit,
    onSendPhotoClick: () -> Unit,
    onRemoved: () -> Unit,
    onAccepted: () -> Unit,
    onRejected: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val subject = viewModel.subject
    val streak = (subject as? ProfileSubject.Friend)?.summary?.streak ?: 0
    val pillShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
    ) {
        // Flat, icon-only back control — no capsule behind it. A boxed button here would be
        // one more panel competing with the profile itself for attention.
        Box(
            modifier = Modifier
                .padding(top = 20.dp)
                .size(40.dp)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.cream, modifier = Modifier.size(20.dp))
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(streakRingBrush(colors, streak))
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(colors.panel)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(colors.panel),
                contentAlignment = Alignment.Center,
            ) {
                if (subject.profilePhotoUrl != null) {
                    AsyncImage(
                        model = subject.profilePhotoUrl,
                        contentDescription = subject.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = subject.displayName.firstOrNull()?.uppercase() ?: "•",
                        fontFamily = typography.display,
                        fontSize = 34.sp,
                        color = colors.cream,
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = subject.displayName, fontFamily = typography.display, fontSize = 22.sp, color = colors.cream)
            Text(
                text = "@${subject.username}",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (streak > 0) {
                // A quiet pill, not a card — the one piece of data a profile needs to carry here,
                // given the same weight a bio would get elsewhere and no more.
                Row(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .background(colors.panel, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = colors.glow, modifier = Modifier.size(13.dp))
                    Text(
                        text = "$streak day streak",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.glow,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }

        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 11.5.sp,
                color = colors.glow2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }

        HorizontalDivider(color = colors.border.copy(alpha = 0.4f), thickness = 1.dp, modifier = Modifier.padding(top = 26.dp))

        when (subject) {
            is ProfileSubject.Friend -> FriendActions(
                viewModel = viewModel,
                friend = subject.summary,
                pillShape = pillShape,
                onSendPhotoClick = onSendPhotoClick,
                onRemoved = onRemoved,
            )

            is ProfileSubject.PendingRequest -> PendingRequestActions(
                viewModel = viewModel,
                pillShape = pillShape,
                onAccepted = onAccepted,
                onRejected = onRejected,
            )
        }
    }
}

@Composable
private fun FriendActions(
    viewModel: FriendProfileViewModel,
    friend: com.ember.app.data.remote.dto.FriendSummaryDto,
    pillShape: RoundedCornerShape,
    onSendPhotoClick: () -> Unit,
    onRemoved: () -> Unit,
) {
    val colors = EmberTheme.colors

    val buttonSizePx = Size(300f, 52f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), pillShape)
            .clickable(onClick = onSendPhotoClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
        Text(
            text = "Send a photo",
            fontFamily = PublicSansFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accentText,
            modifier = Modifier.padding(start = 8.dp),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(colors.panel, pillShape)
            .border(1.dp, if (friend.pinnedByMe) colors.glow else colors.border, pillShape)
            .clickable(enabled = !viewModel.isUpdatingPin, onClick = viewModel::togglePin)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewModel.isUpdatingPin) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.glow, strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Filled.PushPin,
                contentDescription = null,
                tint = if (friend.pinnedByMe) colors.glow else colors.cream,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (friend.pinnedByMe) "Pinned as partner" else "Pin as partner",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (friend.pinnedByMe) colors.glow else colors.cream,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }

    Text(
        text = if (viewModel.isRemoving) "Removing…" else "Remove friend",
        fontFamily = PublicSansFontFamily,
        fontSize = 12.5.sp,
        color = Color(0xFFE8756C),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp)
            .clickable(enabled = !viewModel.isRemoving) { viewModel.removeFriend(onRemoved) },
    )
}

@Composable
private fun PendingRequestActions(
    viewModel: FriendProfileViewModel,
    pillShape: RoundedCornerShape,
    onAccepted: () -> Unit,
    onRejected: () -> Unit,
) {
    val colors = EmberTheme.colors
    val busy = viewModel.isAccepting || viewModel.isRejecting

    val buttonSizePx = Size(300f, 52f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), pillShape)
            .clickable(enabled = !busy) { viewModel.acceptRequest(onAccepted) }
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewModel.isAccepting) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.accentText, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
            Text(
                text = "Accept",
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentText,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(colors.panel, pillShape)
            .border(1.dp, colors.border, pillShape)
            .clickable(enabled = !busy) { viewModel.rejectRequest(onRejected) }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewModel.isRejecting) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.cream, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Filled.Close, contentDescription = null, tint = colors.cream, modifier = Modifier.size(14.dp))
            Text(
                text = "Decline",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.cream,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }
}

/** Same streak-intensity ring language as the friends list (`streakRingBrush` in
 * FriendsScreen.kt) — unlit at 0, warming through a single glow color, full sweep-gradient
 * blaze at 7+. Kept as its own small copy here rather than exported cross-file for a handful
 * of lines. */
private fun streakRingBrush(colors: com.ember.app.ui.theme.EmberColors, streak: Int): Brush = when {
    streak >= 7 -> Brush.sweepGradient(listOf(colors.glow, colors.glow2, colors.violet, colors.glow))
    streak >= 3 -> Brush.linearGradient(listOf(colors.glow, colors.glow2))
    streak >= 1 -> Brush.linearGradient(listOf(colors.glow.copy(alpha = 0.8f), colors.glow.copy(alpha = 0.8f)))
    else -> Brush.linearGradient(listOf(colors.border, colors.border))
}
