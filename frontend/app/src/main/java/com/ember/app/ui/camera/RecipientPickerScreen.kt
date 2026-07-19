package com.ember.app.ui.camera

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.ui.components.GlowPhotoTile
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

@Composable
fun RecipientPickerScreen(
    viewModel: RecipientPickerViewModel,
    onClose: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val rowShape = RoundedCornerShape(16.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .padding(top = 60.dp, start = 20.dp, end = 20.dp, bottom = 26.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Send to", fontFamily = typography.display, fontSize = 20.sp, color = colors.cream)
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = colors.mutedDim,
                modifier = Modifier.size(18.dp).clickable(onClick = onClose),
            )
        }
        Text(
            text = "Pick one person, select several, or pin a partner so photos always go to just them",
            fontFamily = PublicSansFontFamily,
            fontSize = 12.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            when {
                viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.glow)
                }

                viewModel.friends.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Add friends first to send them photos.",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(viewModel.friends, key = { it.friendId }) { friend ->
                        RecipientRow(
                            friend = friend,
                            isSelected = friend.friendId in viewModel.selectedFriendIds,
                            shape = rowShape,
                            onClick = { viewModel.toggleSelected(friend.friendId) },
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .background(colors.violet.copy(alpha = 0x1F / 255f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.PushPin, contentDescription = null, tint = colors.violet, modifier = Modifier.size(14.dp))
            Text(
                text = "Pinning a friend makes them your default recipient — good for couples who only want photos going to each other.",
                fontFamily = PublicSansFontFamily,
                fontSize = 11.sp,
                color = colors.muted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        val buttonSizePx = Size(300f, 52f)
        val recipientCount = viewModel.selectedFriendIds.size
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), RoundedCornerShape(16.dp))
                .clickable(enabled = recipientCount > 0) { onConfirm(viewModel.selectedFriendIds) }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (recipientCount == 0) "Select someone to continue" else "Continue",
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentText,
            )
        }
    }
}

@Composable
private fun RecipientRow(
    friend: FriendSummaryDto,
    isSelected: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
) {
    val colors = EmberTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, shape)
            .border(if (isSelected) 1.5.dp else 1.dp, if (isSelected) colors.glow else colors.border, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowPhotoTile(size = 42.dp, seed = friend.friendId.hashCode(), photoUrl = friend.profilePhotoUrl)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = friend.displayName, fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.cream)
                if (friend.pinnedByMe) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Pinned",
                        tint = colors.glow,
                        modifier = Modifier.padding(start = 5.dp).size(11.dp),
                    )
                }
            }
            Text(
                text = if (friend.pinnedByMe) "Pinned partner" else "Tap to select",
                fontFamily = PublicSansFontFamily,
                fontSize = 10.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(if (isSelected) colors.glow else Color.Transparent, CircleShape)
                .border(if (isSelected) 0.dp else 1.5.dp, colors.mutedDim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.accentText, modifier = Modifier.size(13.dp))
            }
        }
    }
}
