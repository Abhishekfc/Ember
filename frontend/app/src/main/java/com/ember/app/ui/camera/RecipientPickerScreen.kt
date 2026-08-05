package com.ember.app.ui.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PushPin
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.RecipientListDto
import com.ember.app.ui.components.NestedScreenHeader
import com.ember.app.ui.components.emberButtonBrush
import com.ember.app.ui.profile.EditDialogShell
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
    var screenSize by remember { mutableStateOf(Size.Zero) }

    // The "+" badge's own inline name field — kept at this screen's level (not the badge row's
    // own composable) since it needs to sit as its own row below the badges, not inside them.
    var isCreatingList by remember { mutableStateOf(false) }
    var newListName by remember { mutableStateOf("") }

    // Long-pressing a saved list badge asks first — kept at this screen's level (not fired
    // straight from the badge row) so the confirmation dialog can render above everything else.
    var pendingDeleteList by remember { mutableStateOf<RecipientListDto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
    ) {
        NestedScreenHeader(onBack = onClose, title = "Send to")

        if (viewModel.friends.isNotEmpty()) {
            RecipientBadgeRow(
                viewModel = viewModel,
                isCreatingList = isCreatingList,
                onToggleCreateList = {
                    isCreatingList = !isCreatingList
                    if (!isCreatingList) newListName = ""
                },
                onRequestDeleteList = { pendingDeleteList = it },
            )
        }

        if (isCreatingList) {
            NewListBar(
                name = newListName,
                onNameChange = { newListName = it },
                canSave = newListName.isNotBlank() && viewModel.selectedFriendIds.isNotEmpty() && !viewModel.isMutatingLists,
                onSave = {
                    viewModel.createCustomList(newListName)
                    newListName = ""
                    isCreatingList = false
                },
            )
            if (viewModel.selectedFriendIds.isEmpty()) {
                Text(
                    text = "Pick who goes in it first, then save",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 11.sp,
                    color = colors.mutedDim,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
        }

        // Surfaces a failed create/delete (see RecipientPickerViewModel) — both can now fail for
        // real, over the network, unlike back when these lists were purely on-device. Shown
        // regardless of whether the creation bar is open, since a delete failure (long-press on a
        // badge) can happen independently of it.
        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 11.sp,
                color = colors.glow2,
                modifier = Modifier.padding(top = 6.dp, start = 4.dp),
            )
        }

        Box(modifier = Modifier.weight(1f).padding(top = 18.dp)) {
            when {
                // Gated on friends being empty too — a cached list from a previous session
                // (read on init, see RecipientPickerViewModel) should stay on screen while the
                // fresh fetch is in flight, not get covered by a spinner as if nothing were known.
                viewModel.isLoading && viewModel.friends.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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

                // A saved list can end up empty (every member since unfriended) without the
                // underlying friend list itself being empty — worth its own message rather than
                // silently rendering nothing where a list used to be.
                viewModel.visibleFriends.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No one in this list",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(viewModel.visibleFriends, key = { it.friendId }) { friend ->
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(emberButtonBrush(EmberTheme.key, colors, buttonSizePx), EmberRadii.buttonShape)
                // Always clickable, even at zero — this button used to require at least one
                // recipient before it would do anything, which meant deliberately deselecting
                // everyone (to correct a previous selection, not just picking for the first
                // time) had no way to actually confirm that: the only way off this screen was
                // the back button, which discards the change entirely via onClose rather than
                // onConfirm, leaving Camera still showing whatever was selected before. The
                // actual real gate against sending to nobody is CameraScreen's own send button
                // (see hasRecipients there), so this one doesn't need to duplicate it.
                .clickable { onConfirm(viewModel.selectedFriendIds) }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Continue",
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentText,
            )
        }
    }

    pendingDeleteList?.let { list ->
        DeleteListConfirmDialog(
            listName = list.name,
            onDismiss = { pendingDeleteList = null },
            onConfirm = {
                viewModel.deleteCustomList(list.id)
                pendingDeleteList = null
            },
        )
    }
}

/** Quick-select shortcuts sitting above the friend list — Recent (whoever the last real send
 * actually went to) and Everyone are always offered once there's at least one friend to pick
 * from; any lists the user has saved (see the "+" badge) follow, in the order they were created.
 * Tapping a badge both applies its selection and narrows the list below to just that group (see
 * [RecipientPickerViewModel.visibleFriends]) — these read as "show me this group", not an
 * additive filter on top of whatever else was showing. */
@Composable
private fun RecipientBadgeRow(
    viewModel: RecipientPickerViewModel,
    isCreatingList: Boolean,
    onToggleCreateList: () -> Unit,
    onRequestDeleteList: (RecipientListDto) -> Unit,
) {
    val activeFilterId = viewModel.activeFilterId
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (viewModel.recentIds.isNotEmpty()) {
            RecipientBadge(
                label = "Recent",
                active = activeFilterId == RecipientPickerViewModel.RECENT_BADGE_ID,
                onClick = viewModel::selectRecent,
            )
        }
        RecipientBadge(
            label = "Everyone",
            active = activeFilterId == RecipientPickerViewModel.EVERYONE_BADGE_ID,
            onClick = viewModel::selectEveryone,
        )
        viewModel.customLists.forEach { list ->
            RecipientBadge(
                label = list.name,
                active = activeFilterId == list.id,
                onClick = { viewModel.selectCustomList(list) },
                // Long-press asks first (see DeleteListConfirmDialog) rather than deleting
                // outright — a long-press is easy to trigger by accident (e.g. while trying to
                // scroll the badge row), so an irreversible delete needs a confirm step here even
                // though these are otherwise low-stakes personal shortcuts.
                onLongClick = { onRequestDeleteList(list) },
            )
        }
        AddListBadge(isActive = isCreatingList, onClick = onToggleCreateList)
    }
}

/** A pill-shaped quick-select shortcut. Flat, not glassy — a tinted fill only once active, plain
 * muted text otherwise, the same restrained language the rest of this screen already uses. */
@Composable
private fun RecipientBadge(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = EmberTheme.colors
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(percent = 50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (active) colors.glow.copy(alpha = 0.16f) else colors.panel)
            .let { if (active) it.border(1.dp, colors.glow, shape) else it }
            .pointerInput(onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = if (onLongClick != null) {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongClick()
                        }
                    } else {
                        null
                    },
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontFamily = PublicSansFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) colors.glow else colors.muted,
        )
    }
}

/** The "+" badge — an outlined circle (no fill) rather than a filled one, so it reads as an
 * empty slot waiting to be defined instead of a same-weight sibling of the real, populated
 * badges next to it. Swaps to a plain close glyph while the name field below is open, since
 * tapping it again is exactly what dismisses that field. */
@Composable
private fun AddListBadge(isActive: Boolean, onClick: () -> Unit) {
    val colors = EmberTheme.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (isActive) colors.glow else colors.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isActive) Icons.Rounded.Close else Icons.Rounded.Add,
            contentDescription = if (isActive) "Cancel" else "Create a list",
            tint = if (isActive) colors.glow else colors.muted,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Still used by WidgetSettingsScreen's own single "Anyone (most recent)" shortcut — that screen
 * only ever needs the one plain text link, not this screen's full badge row. */
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

/** The name field for a new custom list — saves whatever's already checked in the list below
 * under this name. Auto-focused the moment it appears, since opening it is itself the intent
 * to type a name right away. */
@Composable
private fun NewListBar(
    name: String,
    onNameChange: (String) -> Unit,
    canSave: Boolean,
    onSave: () -> Unit,
) {
    val colors = EmberTheme.colors
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.panel)
            .border(1.dp, colors.border, RoundedCornerShape(percent = 50))
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (name.isEmpty()) {
                Text(text = "Name this list", fontFamily = PublicSansFontFamily, fontSize = 14.sp, color = colors.mutedDim)
            }
            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 14.sp, color = colors.cream),
                cursorBrush = SolidColor(colors.glow),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canSave) onSave() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
        }
        Text(
            text = "Save",
            fontFamily = PublicSansFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (canSave) colors.glow else colors.mutedDim,
            modifier = Modifier
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp, end = 6.dp)
                .clickable(enabled = canSave, onClick = onSave),
        )
    }
}

/** Asks before a saved list actually goes away — long-pressing a badge is easy to trigger by
 * accident, unlike a deliberate menu action, so this needs the confirm step other quick personal
 * shortcuts elsewhere in the app skip. Same shell MyProfileScreen's own dialogs use. */
@Composable
private fun DeleteListConfirmDialog(
    listName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = EmberTheme.colors
    EditDialogShell(title = "Delete \"$listName\"?", onDismiss = onDismiss) {
        Text(
            text = "This can't be undone.",
            fontFamily = PublicSansFontFamily,
            fontSize = 13.sp,
            color = colors.muted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.panel)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = "Cancel", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.muted)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DeleteListDestructiveColor)
                    .clickable(onClick = onConfirm)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = "Delete", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// Dark red, matching the Delete Account button's own tone (SettingsScreen's
// DeleteAccountDestructiveColor) rather than the lighter coral Unfriend/Block use elsewhere —
// requested explicitly for delete actions in this app, not the softer "merely annoying" tone.
private val DeleteListDestructiveColor = Color(0xFFB3261E)

/** A real circular avatar — photo if the friend has one, a bold initial if not — same fallback
 * language as the header's ProfileChip and every other "this is a person" spot in the app. The
 * avatar itself stays completely plain either way; selection instead shows as a trailing circle
 * at the row's own right edge — empty outline when unpicked, filled with a checkmark once picked
 * — rather than something cut into the photo itself. */
@Composable
internal fun RecipientRow(
    friend: FriendSummaryDto,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = EmberTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
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
        // Always present, not just once selected — an empty ring is what makes "nothing picked
        // yet" read as its own state rather than as this row having no selection control at all.
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .size(22.dp)
                .clip(CircleShape)
                .let {
                    if (isSelected) it.background(colors.glow) else it.border(1.5.dp, colors.border, CircleShape)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = colors.accentText, modifier = Modifier.size(13.dp))
            }
        }
    }
}
