package com.ember.app.ui.friends

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.ui.components.BottomNavDock
import com.ember.app.ui.components.GlowPhotoTile
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.home.formatRelativeTime
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun FriendsScreen(
    viewModel: FriendsViewModel,
    onNavigate: (NavDestination) -> Unit,
    onCameraClick: () -> Unit,
    onFindPeopleClick: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val hazeState = rememberHazeState()
    val rowShape = RoundedCornerShape(20.dp)
    val searchShape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 68.dp, start = 20.dp, end = 20.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Friends",
                    fontFamily = typography.display,
                    fontSize = 22.sp,
                    color = colors.cream,
                )
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(colors.glow.copy(alpha = 0x22 / 255f), CircleShape)
                        .clickable(onClick = onFindPeopleClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Find people", tint = colors.glow, modifier = Modifier.size(17.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                    .background(colors.panel, searchShape)
                    .border(1.dp, colors.border, searchShape)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colors.mutedDim, modifier = Modifier.size(15.dp))
                Box(modifier = Modifier.padding(start = 9.dp).fillMaxWidth()) {
                    if (viewModel.searchQuery.isEmpty()) {
                        Text(text = "Search friends", fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.mutedDim)
                    }
                    BasicTextField(
                        value = viewModel.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.cream),
                        cursorBrush = SolidColor(colors.glow),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when {
                viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.glow)
                }

                viewModel.errorMessage != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = viewModel.errorMessage.orEmpty(),
                        fontFamily = typography.body,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }

                viewModel.filteredFriends.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No friends yet — tap + to find people.",
                        fontFamily = typography.body,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(viewModel.filteredFriends) { index, friend ->
                        FriendRow(friend, seed = index, shape = rowShape)
                    }
                }
            }
        }

        BottomNavDock(
            active = NavDestination.FRIENDS,
            onNavigate = onNavigate,
            onCameraClick = onCameraClick,
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
        )
    }
}

@Composable
private fun FriendRow(friend: FriendSummaryDto, seed: Int, shape: RoundedCornerShape) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, shape)
            .border(1.dp, colors.border, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowPhotoTile(size = 56.dp, seed = seed)
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(text = friend.displayName, fontFamily = typography.body, fontSize = 14.5.sp, color = colors.cream)
            Text(
                text = friend.lastActivityAt?.let { "last sent ${formatRelativeTime(it)}" } ?: "no photos yet",
                fontFamily = typography.body,
                fontSize = 11.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = colors.glow, modifier = Modifier.size(13.dp))
            Text(
                text = "${friend.streak}",
                fontFamily = typography.body,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.glow,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
