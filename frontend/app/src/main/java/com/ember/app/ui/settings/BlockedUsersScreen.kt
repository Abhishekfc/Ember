package com.ember.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.data.remote.dto.BlockedUserDto
import com.ember.app.ui.components.NestedScreenHeader
import com.ember.app.ui.friends.StreakAvatar
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

@Composable
fun BlockedUsersScreen(
    viewModel: BlockedUsersViewModel,
    onClose: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
    ) {
        NestedScreenHeader(onBack = onClose, title = "Blocked accounts")
        Text(
            text = "They can't find your profile, send you requests, or send you photos",
            fontFamily = typography.body,
            fontSize = 12.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )

        Box(modifier = Modifier.weight(1f).padding(top = 18.dp)) {
            when {
                viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.glow)
                }

                viewModel.blockedUsers.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nobody's blocked right now.",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(viewModel.blockedUsers, key = { it.userId }) { blocked ->
                        BlockedUserRow(
                            blocked = blocked,
                            isUnblocking = viewModel.unblockingUserId == blocked.userId,
                            onUnblock = { viewModel.unblock(blocked.userId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedUserRow(blocked: BlockedUserDto, isUnblocking: Boolean, onUnblock: () -> Unit) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // streak = 0 always — a blocked account isn't a friend any more (blocking removes the
        // friendship server-side), so there's no streak concept left to show for this row.
        StreakAvatar(photoUrl = blocked.profilePhotoUrl, displayName = blocked.displayName, streak = 0, size = 48.dp)
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(text = blocked.displayName, fontFamily = typography.body, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.cream)
            Text(
                text = "@${blocked.username}",
                fontFamily = typography.body,
                fontSize = 12.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .border(1.dp, colors.border, RoundedCornerShape(50))
                .clickable(enabled = !isUnblocking, onClick = onUnblock)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isUnblocking) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), color = colors.cream, strokeWidth = 2.dp)
            } else {
                Text(text = "Unblock", fontFamily = PublicSansFontFamily, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = colors.cream)
            }
        }
    }
}
