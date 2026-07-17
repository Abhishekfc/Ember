package com.ember.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.ui.components.BottomNavDock
import com.ember.app.ui.components.GlowPhotoTile
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.theme.CourgetteFontFamily
import com.ember.app.ui.theme.EmberTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigate: (NavDestination) -> Unit,
    onCameraClick: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val hazeState = rememberHazeState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        // hazeSource is scoped to this Column only (header + content) — it must never wrap
        // the BottomNavDock sibling below, or the blur source would include the dock's own
        // pixels and produce a ghosting artifact.
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Ember",
                    fontFamily = CourgetteFontFamily,
                    fontSize = 28.sp,
                    color = colors.cream,
                )
            }

            Column(modifier = Modifier.padding(top = 24.dp, start = 20.dp, end = 20.dp)) {
                Text(
                    text = viewModel.greeting,
                    fontFamily = typography.display,
                    fontSize = 22.sp,
                    color = colors.cream,
                )
                Text(
                    text = "${viewModel.dateText} · Latest from your friends",
                    fontFamily = typography.body,
                    fontSize = 12.5.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            when {
                viewModel.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.glow)
                }

                viewModel.errorMessage != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = viewModel.errorMessage.orEmpty(),
                            fontFamily = typography.body,
                            fontSize = 13.sp,
                            color = colors.muted,
                        )
                        Text(
                            text = "Tap to retry",
                            fontFamily = typography.body,
                            fontSize = 13.sp,
                            color = colors.glow,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clickable { viewModel.loadFeed() },
                        )
                    }
                }

                viewModel.feedItems.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No photos yet — once a friend sends you one, it'll glow right here.",
                        fontFamily = typography.body,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(viewModel.feedItems, key = { it.friendId }) { item ->
                        val seed = viewModel.feedItems.indexOf(item)
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            GlowPhotoTile(
                                size = maxWidth,
                                seed = seed,
                                name = item.displayName,
                                time = formatRelativeTime(item.createdAt),
                                photoUrl = item.photoUrl,
                            )
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.Black.copy(alpha = 0.4f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = colors.glow, modifier = Modifier.size(11.dp))
                                Text(
                                    text = "${item.streak}",
                                    fontFamily = typography.body,
                                    fontSize = 10.5.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(start = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        BottomNavDock(
            active = NavDestination.HOME,
            onNavigate = onNavigate,
            onCameraClick = onCameraClick,
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
        )
    }
}
