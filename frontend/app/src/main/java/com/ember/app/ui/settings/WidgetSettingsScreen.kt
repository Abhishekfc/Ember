package com.ember.app.ui.settings

import androidx.compose.foundation.background
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
import com.ember.app.ui.camera.QuickSelectLink
import com.ember.app.ui.camera.RecipientRow
import com.ember.app.ui.components.NestedScreenHeader
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

/** Lets a Gold subscriber choose which friends' photos always show on the home-screen widget,
 * instead of the default "whoever sent the most recent photo" every account starts with.
 * Deliberately the same stage-then-confirm shape as ThemeScreen and RecipientPickerScreen:
 * browsing/toggling friends here is always free (a locked-out account can still see and try the
 * picker), the Gold gate is only on the Save button actually persisting a non-empty choice —
 * see [WidgetSettingsViewModel.save]'s own doc comment. */
@Composable
fun WidgetSettingsScreen(
    viewModel: WidgetSettingsViewModel,
    onClose: () -> Unit,
    onUpgradeToGold: () -> Unit,
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
        NestedScreenHeader(onBack = onClose, title = "Widget")
        Text(
            text = "Choose whose photos always show on your home-screen widget",
            fontFamily = typography.body,
            fontSize = 12.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )

        Row(modifier = Modifier.padding(top = 18.dp)) {
            QuickSelectLink(
                label = "Anyone (most recent)",
                active = viewModel.selectedFriendIds.isEmpty(),
                onClick = viewModel::clearSelection,
            )
        }

        Box(modifier = Modifier.weight(1f).padding(top = 18.dp)) {
            when {
                viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.glow)
                }

                viewModel.friends.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Add friends first to feature them on your widget.",
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

        val needsUpgrade = viewModel.selectedFriendIds.isNotEmpty() && !viewModel.isGoldMember
        val buttonSizePx = Size(300f, 52f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), EmberRadii.buttonShape)
                .clickable {
                    if (needsUpgrade) {
                        onUpgradeToGold()
                    } else {
                        viewModel.save()
                        onClose()
                    }
                }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (needsUpgrade) "Get Ember Gold" else "Save",
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentText,
            )
        }
    }
}
