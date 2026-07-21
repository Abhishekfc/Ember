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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
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

@Composable
fun FriendProfileScreen(
    viewModel: FriendProfileViewModel,
    onBack: () -> Unit,
    onSendPhotoClick: () -> Unit,
    onRemoved: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val friend = viewModel.friend
    val pillShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.panel)
                .border(1.dp, colors.border, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.cream, modifier = Modifier.size(18.dp))
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(streakRingBrush(colors, friend.streak))
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(colors.panel)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(colors.panel),
                contentAlignment = Alignment.Center,
            ) {
                if (friend.profilePhotoUrl != null) {
                    AsyncImage(
                        model = friend.profilePhotoUrl,
                        contentDescription = friend.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = friend.displayName.firstOrNull()?.uppercase() ?: "•",
                        fontFamily = typography.display,
                        fontSize = 34.sp,
                        color = colors.cream,
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = friend.displayName, fontFamily = typography.display, fontSize = 22.sp, color = colors.cream)
            Text(
                text = "@${friend.username}",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (friend.streak > 0) {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = colors.glow, modifier = Modifier.size(15.dp))
                    Text(
                        text = "${friend.streak} day streak",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.glow,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }

        val buttonSizePx = Size(300f, 52f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp)
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
                .background(if (friend.pinnedByMe) colors.glow.copy(alpha = 0x1F / 255f) else colors.panel, pillShape)
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

        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 11.5.sp,
                color = colors.glow2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Text(
            text = if (viewModel.isRemoving) "Removing…" else "Remove friend",
            fontFamily = PublicSansFontFamily,
            fontSize = 12.5.sp,
            color = Color(0xFFE8756C),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
                .clickable(enabled = !viewModel.isRemoving) { viewModel.removeFriend(onRemoved) },
        )
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
