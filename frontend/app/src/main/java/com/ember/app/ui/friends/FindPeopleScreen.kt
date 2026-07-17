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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.PersonAdd
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
import com.ember.app.data.remote.dto.FriendSearchResultDto
import com.ember.app.ui.components.GlowPhotoTile
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

@Composable
fun FindPeopleScreen(
    viewModel: FindPeopleViewModel,
    onBack: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val searchShape = RoundedCornerShape(14.dp)
    val rowShape = RoundedCornerShape(16.dp)
    val isActiveSearch = viewModel.query.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .padding(top = 60.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onBack),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Back", tint = colors.mutedDim, modifier = Modifier.size(18.dp))
            Text(
                text = "Friends",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        Text(
            text = "Find people",
            fontFamily = typography.display,
            fontSize = 22.sp,
            color = colors.cream,
            modifier = Modifier.padding(top = 18.dp, bottom = 16.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, searchShape)
                .border(1.5.dp, if (isActiveSearch) colors.glow else colors.border, searchShape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = if (isActiveSearch) colors.glow else colors.mutedDim,
                modifier = Modifier.size(15.dp),
            )
            Box(modifier = Modifier.padding(start = 9.dp).fillMaxWidth()) {
                if (viewModel.query.isEmpty()) {
                    Text(text = "Search by name or username", fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.mutedDim)
                }
                BasicTextField(
                    value = viewModel.query,
                    onValueChange = viewModel::onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.cream),
                    cursorBrush = SolidColor(colors.glow),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(top = 20.dp)) {
            when {
                viewModel.isSearching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.glow)
                }

                viewModel.errorMessage != null -> Text(
                    text = viewModel.errorMessage.orEmpty(),
                    fontFamily = typography.body,
                    fontSize = 13.sp,
                    color = colors.muted,
                )

                !isActiveSearch -> Text(
                    text = "Search for friends by name or username.",
                    fontFamily = typography.body,
                    fontSize = 13.sp,
                    color = colors.muted,
                )

                viewModel.results.isEmpty() -> Text(
                    text = "No one found.",
                    fontFamily = typography.body,
                    fontSize = 13.sp,
                    color = colors.muted,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(viewModel.results, key = { it.userId }) { result ->
                        FindPeopleRow(result, shape = rowShape, onAdd = { viewModel.sendRequest(result.userId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FindPeopleRow(
    result: FriendSearchResultDto,
    shape: RoundedCornerShape,
    onAdd: () -> Unit,
) {
    val colors = EmberTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, shape)
            .border(1.dp, colors.border, shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlowPhotoTile(size = 44.dp, seed = result.username.hashCode())
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text = result.displayName, fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = colors.cream)
            Text(
                text = "@${result.username}",
                fontFamily = PublicSansFontFamily,
                fontSize = 11.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 1.dp),
            )
        }

        if (result.requested) {
            Box(
                modifier = Modifier
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Text(text = "Requested", fontFamily = PublicSansFontFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.mutedDim)
            }
        } else {
            val buttonSizePx = Size(80f, 30f)
            Row(
                modifier = Modifier
                    .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), RoundedCornerShape(14.dp))
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(12.dp))
                Text(
                    text = "Add",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentText,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
