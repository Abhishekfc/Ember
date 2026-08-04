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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.data.remote.dto.FriendSearchResultDto
import com.ember.app.ui.components.NestedScreenHeader
import com.ember.app.ui.components.emberButtonBrush
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

@Composable
fun FindPeopleScreen(
    viewModel: FindPeopleViewModel,
    onBack: () -> Unit,
    onResultClick: (FriendSearchResultDto) -> Unit = {},
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val searchShape = RoundedCornerShape(14.dp)
    val isActiveSearch = viewModel.query.isNotEmpty()

    // viewModel survives across visits to this screen (it's keyed the same every time in
    // MainActivity, not recreated), so its last results linger in memory too — stale the moment
    // a relationship changes elsewhere (removing a friend, accepting/declining/sending a request
    // from that person's own profile page) since none of those write paths touch this screen's
    // state. Re-running whatever search was already typed on every fresh visit is what actually
    // picks up that change, rather than waiting for the next keystroke that may never come.
    LaunchedEffect(Unit) {
        if (viewModel.query.isNotBlank()) {
            viewModel.onQueryChange(viewModel.query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
    ) {
        NestedScreenHeader(onBack = onBack, title = "Find people")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, searchShape)
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

                else -> LazyColumn(contentPadding = PaddingValues(bottom = 20.dp)) {
                    items(viewModel.results, key = { it.userId }) { result ->
                        FindPeopleRow(
                            result,
                            onClick = { onResultClick(result) },
                            onAdd = { viewModel.sendRequest(result.userId) },
                            onCancel = { result.friendshipId?.let { viewModel.cancelRequest(it) } },
                        )
                    }
                }
            }
        }
    }
}

// Flat, straight on the screen's own background — same language as the Friends list, not a
// bordered card per row.
@Composable
private fun FindPeopleRow(
    result: FriendSearchResultDto,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Search results carry no photo URL at all today (FriendSearchResult doesn't fetch one) —
        // a plain "dark circle + initial letter" identity, same as everywhere else in Friends.
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(colors.elevatedPanel),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = result.displayName.firstOrNull()?.uppercase() ?: "•",
                fontFamily = typography.display,
                fontSize = 18.sp,
                color = colors.cream,
            )
        }
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(text = result.displayName, fontFamily = PublicSansFontFamily, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colors.cream)
            Text(
                text = "@${result.username}",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        if (result.requested) {
            // Only a request *this* user sent is theirs to cancel — one they received, or an
            // already-accepted friendship showing up in search, both still read "Requested" here
            // but aren't interactive (accepting/removing those has its own dedicated place:
            // the Friends tab itself).
            Row(
                modifier = Modifier
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .let { if (result.isPendingFromMe) it.clickable(onClick = onCancel) else it }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "Requested", fontFamily = PublicSansFontFamily, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = colors.mutedDim)
                if (result.isPendingFromMe) {
                    Icon(
                        Icons.Rounded.Cancel,
                        contentDescription = "Cancel request",
                        tint = colors.mutedDim,
                        modifier = Modifier.padding(start = 6.dp).size(12.dp),
                    )
                }
            }
        } else {
            val buttonSizePx = Size(80f, 30f)
            Row(
                modifier = Modifier
                    .background(emberButtonBrush(EmberTheme.key, colors, buttonSizePx), RoundedCornerShape(14.dp))
                    .clickable(onClick = onAdd)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(12.dp))
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
