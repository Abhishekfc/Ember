package com.ember.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PersonAdd
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.data.remote.dto.ActivityEventDto
import com.ember.app.data.remote.dto.ActivityEventType
import com.ember.app.ui.components.BottomNavDock
import com.ember.app.ui.components.GlowPhotoTile
import com.ember.app.ui.components.NavDestination
import com.ember.app.ui.home.formatRelativeTime
import com.ember.app.ui.theme.EmberTheme
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onNavigate: (NavDestination) -> Unit,
    onCameraClick: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val hazeState = rememberHazeState()
    val rowShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize().hazeSource(hazeState)) {
            Column(modifier = Modifier.padding(top = 68.dp, start = 20.dp, end = 20.dp)) {
                Text(text = "Activity", fontFamily = typography.display, fontSize = 22.sp, color = colors.cream)
                Text(
                    text = "What's happened recently",
                    fontFamily = typography.body,
                    fontSize = 12.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            when {
                viewModel.isLoading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.glow)
                }

                viewModel.errorMessage != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = viewModel.errorMessage.orEmpty(), fontFamily = typography.body, fontSize = 13.sp, color = colors.muted)
                }

                viewModel.events.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Nothing new — check back later.",
                        fontFamily = typography.body,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 110.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(viewModel.events) { event ->
                        ActivityRow(event, shape = rowShape)
                    }
                }
            }
        }

        BottomNavDock(
            active = NavDestination.ACTIVITY,
            onNavigate = onNavigate,
            onCameraClick = onCameraClick,
            modifier = Modifier.align(Alignment.BottomCenter),
            hazeState = hazeState,
        )
    }
}

private fun iconFor(type: ActivityEventType): ImageVector = when (type) {
    ActivityEventType.PHOTO_RECEIVED -> Icons.Filled.Image
    ActivityEventType.STREAK_EXPIRING -> Icons.Filled.LocalFireDepartment
    ActivityEventType.REQUEST_ACCEPTED -> Icons.Filled.Check
    ActivityEventType.REQUEST_INCOMING -> Icons.Filled.PersonAdd
}

@Composable
private fun ActivityRow(event: ActivityEventDto, shape: RoundedCornerShape) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, shape)
            .border(1.dp, if (event.warn) colors.glow.copy(alpha = 0.35f) else colors.border, shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            GlowPhotoTile(size = 42.dp, seed = event.actorId.hashCode())
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .background(if (event.warn) colors.glow else colors.panel, CircleShape)
                    .border(1.5.dp, colors.panel, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconFor(event.type),
                    contentDescription = null,
                    tint = if (event.warn) colors.accentText else colors.glow,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text = event.message, fontFamily = typography.body, fontSize = 12.5.sp, color = colors.cream, lineHeight = 17.sp)
            Text(
                text = formatRelativeTime(event.createdAt),
                fontFamily = typography.body,
                fontSize = 10.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
