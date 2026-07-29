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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.PushPin
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.EmberRadii
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
    val pinnedFriend = viewModel.friends.firstOrNull { it.pinnedByMe }
    val allFriendIds = remember(viewModel.friends) { viewModel.friends.map { it.friendId }.toSet() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 32.dp, start = 20.dp, end = 20.dp, bottom = 26.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "Send to", fontFamily = typography.display, fontSize = 22.sp, color = colors.cream)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.elevatedPanel)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = colors.cream, modifier = Modifier.size(16.dp))
            }
        }

        // Quiet inline text, not bordered chips — the two shortcuts worth a single tap, stated
        // as plainly as possible rather than given their own visual weight on a screen whose
        // only real job is a short list of people.
        if (pinnedFriend != null) {
            Row(modifier = Modifier.padding(top = 18.dp)) {
                QuickSelectLink(
                    label = "Everyone",
                    active = viewModel.selectedFriendIds == allFriendIds,
                    onClick = { viewModel.setSelection(allFriendIds) },
                )
                Text(text = "  ·  ", fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.mutedDim)
                QuickSelectLink(
                    label = "${pinnedFriend.displayName} only",
                    active = viewModel.selectedFriendIds == setOf(pinnedFriend.friendId),
                    onClick = { viewModel.setSelection(setOf(pinnedFriend.friendId)) },
                )
            }
        }

        Box(modifier = Modifier.weight(1f).padding(top = 18.dp)) {
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
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(viewModel.friends, key = { it.friendId }) { friend ->
                        RecipientRow(
                            friend = friend,
                            isSelected = friend.friendId in viewModel.selectedFriendIds,
                            onClick = { viewModel.toggleSelected(friend.friendId) },
                        )
                    }
                }
            }
        }

        val buttonSizePx = Size(300f, 52f)
        val recipientCount = viewModel.selectedFriendIds.size
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), EmberRadii.buttonShape)
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
internal fun QuickSelectLink(label: String, active: Boolean, onClick: () -> Unit) {
    val colors = EmberTheme.colors
    Text(
        text = label,
        fontFamily = PublicSansFontFamily,
        fontSize = 13.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        color = if (active) colors.glow else colors.muted,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A real circular avatar — photo if the friend has one, a bold initial if not — same fallback
 * language as the header's ProfileChip and every other "this is a person" spot in the app,
 * rather than the photo-card-styled tile this screen used before: a flat gradient square with
 * no initial reads as an anonymous colored block, not a face, and every friend without a photo
 * looked identical. Selection is carried entirely by the row's own tint/border; there's no
 * separate checkbox competing for attention next to it. */
// Flat baseline (no card, no border) — same language as Friends' own list — but selection is a
// real functional state here (this screen's whole job is picking who gets the photo), so it
// still earns a visible, deliberate treatment: a glow-tinted background and border rather than
// disappearing into the same flatness as an unselected row.
@Composable
internal fun RecipientRow(
    friend: FriendSummaryDto,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = EmberTheme.colors
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (isSelected) {
                    it.background(colors.glow.copy(alpha = 0.12f), shape).border(1.dp, colors.glow, shape)
                } else {
                    it
                }
            }
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.elevatedPanel),
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
                    fontFamily = EmberTheme.typography.display,
                    fontSize = 18.sp,
                    color = colors.cream,
                )
            }
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = friend.displayName, fontFamily = PublicSansFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.cream)
                if (friend.pinnedByMe) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        tint = colors.glow,
                        modifier = Modifier.padding(start = 5.dp).size(11.dp),
                    )
                }
            }
            Text(
                text = "@${friend.username}",
                fontFamily = PublicSansFontFamily,
                fontSize = 11.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
