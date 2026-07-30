package com.ember.app.ui.friends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.ReportReason
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.profile.EditDialogShell
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

/** Same coral used by the existing "Remove friend" destructive action below — one consistent
 * "this is a leaving/negative action" color across the whole profile screen, not a second one
 * invented just for Block/Report. */
private val DestructiveColor = Color(0xFFE8756C)

/** One profile layout for every person — an existing friend, someone who's sent a pending
 * request, and a stranger found via search all share the exact same header (avatar, name,
 * username, streak) with no banner/hero image. Only the action area at the bottom differs: Send
 * photo/Pin/Remove for a friend, Accept/Decline for a pending request, and for a search result —
 * Add if there's no relationship yet, Accept/Decline if they already requested this user, a
 * cancelable Requested state if this user already requested them, and nothing at all if they're
 * already friends (that has its own dedicated actions, reached from the Friends tab instead).
 *
 * Visual language is deliberately closer to Instagram/Snapchat's own profile screens than the
 * rest of Ember's more illustrated UI: a plain centered header, one quiet chip instead of a
 * card for the one piece of data that matters (the streak), a hairline rule separating identity
 * from action, and a strict two-tier button hierarchy (one solid, one outline). The only thing
 * that still marks this as *Ember* rather than a generic clone is the Fraunces serif on the
 * name — every other label uses the plain UI face. */
@Composable
fun FriendProfileScreen(
    viewModel: FriendProfileViewModel,
    onBack: () -> Unit,
    onSendPhotoClick: () -> Unit,
    onPinChanged: (FriendSummaryDto) -> Unit,
    onRemoved: () -> Unit,
    onAccepted: (FriendSummaryDto) -> Unit,
    onRejected: () -> Unit,
    onBlocked: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val subject = viewModel.subject
    val streak = (subject as? ProfileSubject.Friend)?.summary?.streak ?: 0
    val pillShape = EmberRadii.buttonShape

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Flat, icon-only back control — no capsule behind it. A boxed button here would be
            // one more panel competing with the profile itself for attention.
            Box(
                modifier = Modifier.size(40.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, contentDescription = "Back", tint = colors.cream, modifier = Modifier.size(20.dp))
            }

            // Same flat, icon-only treatment as the back control on the other side — Block/Report
            // are safety actions relevant to any subject (friend, pending request, or a stranger
            // found via search), not gated on relationship state the way Pin/Remove are.
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier.size(40.dp).clickable { showOverflowMenu = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = colors.cream, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(
                    expanded = showOverflowMenu,
                    onDismissRequest = { showOverflowMenu = false },
                    // Unstyled, this falls back to Material3's own default light surface — jars
                    // badly against Ember's dark theme. `panel`, not `overlayPanel` — panel is the
                    // darker of the two. tonalElevation = 0.dp is the part that actually matters:
                    // Material3 automatically blends a lightening tint on top of containerColor
                    // based on elevation (its own dark-theme "closer to light source" convention),
                    // so leaving this at its default silently washed out `panel` back toward the
                    // same pale gray as before — setting it to 0 is what makes the color actually
                    // render as specified instead of tinted lighter. A shadow alone doesn't read
                    // on a pure black background either (nothing for it to cast onto), so a thin
                    // border is what actually gives this floating card its edge.
                    containerColor = colors.panel,
                    shape = EmberRadii.dialogShape,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Block",
                                fontFamily = PublicSansFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = DestructiveColor,
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Block, contentDescription = null, tint = DestructiveColor, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showOverflowMenu = false
                            showBlockConfirm = true
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    HorizontalDivider(color = colors.border, thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Report",
                                fontFamily = PublicSansFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.cream,
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Flag, contentDescription = null, tint = colors.cream, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showOverflowMenu = false
                            showReportDialog = true
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(streakRingBrush(colors, streak))
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(colors.elevatedPanel)
                    .padding(3.dp)
                    .clip(CircleShape)
                    .background(colors.elevatedPanel),
                contentAlignment = Alignment.Center,
            ) {
                if (subject.profilePhotoUrl != null) {
                    AsyncImage(
                        model = subject.profilePhotoUrl,
                        contentDescription = subject.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = subject.displayName.firstOrNull()?.uppercase() ?: "•",
                        fontFamily = typography.display,
                        fontSize = 34.sp,
                        color = colors.cream,
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = subject.displayName, fontFamily = typography.display, fontSize = 24.sp, color = colors.cream)
            Text(
                text = "@${subject.username}",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 3.dp),
            )
            if (streak > 0) {
                // A quiet pill, not a card — the one piece of data a profile needs to carry here,
                // given the same weight a bio would get elsewhere and no more.
                Row(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .background(colors.elevatedPanel, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = colors.glow, modifier = Modifier.size(13.dp))
                    Text(
                        text = "$streak day streak",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.glow,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                }
            }
        }

        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 11.5.sp,
                color = colors.glow2,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
        }

        HorizontalDivider(color = colors.border, thickness = 1.dp, modifier = Modifier.padding(top = 26.dp))

        when (subject) {
            is ProfileSubject.Friend -> FriendActions(
                viewModel = viewModel,
                friend = subject.summary,
                pillShape = pillShape,
                onSendPhotoClick = onSendPhotoClick,
                onPinChanged = onPinChanged,
                onRemoved = onRemoved,
            )

            is ProfileSubject.PendingRequest -> PendingRequestActions(
                viewModel = viewModel,
                pillShape = pillShape,
                onAccepted = onAccepted,
                onRejected = onRejected,
            )

            is ProfileSubject.SearchResult -> SearchResultActions(
                viewModel = viewModel,
                result = subject.result,
                pillShape = pillShape,
                onAccepted = onAccepted,
                onRejected = onRejected,
            )
        }
    }

    if (showBlockConfirm) {
        BlockConfirmDialog(
            displayName = subject.displayName,
            isBlocking = viewModel.isBlocking,
            onDismiss = { showBlockConfirm = false },
            onConfirm = { viewModel.blockUser(onBlocked) },
        )
    }

    if (showReportDialog) {
        ReportUserDialog(
            isReporting = viewModel.isReporting,
            reportSubmitted = viewModel.reportSubmitted,
            onDismiss = {
                showReportDialog = false
                viewModel.dismissReportConfirmation()
            },
            onSubmit = { reason -> viewModel.reportUser(reason) },
        )
    }
}

/** Same shell MyProfileScreen's own edit dialogs use (EditDialogShell), so this reads as the same
 * kind of popup rather than a visually separate "safety feature" dialog bolted on. Text/button
 * color leans on [DestructiveColor] — the same coral the existing "Remove friend" action already
 * uses — since blocking is the same kind of "leaving/negative" action. */
@Composable
private fun BlockConfirmDialog(
    displayName: String,
    isBlocking: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = EmberTheme.colors
    EditDialogShell(title = "Block $displayName?", onDismiss = onDismiss) {
        Text(
            text = "They won't be able to find your profile, send you friend requests, or send you photos. This won't notify them.",
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
                    .clickable(enabled = !isBlocking, onClick = onDismiss)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = "Cancel", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.muted)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DestructiveColor)
                    .clickable(enabled = !isBlocking, onClick = onConfirm)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isBlocking) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(text = "Block", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

private val REPORT_REASON_LABELS = listOf(
    ReportReason.SPAM to "Spam",
    ReportReason.HARASSMENT to "Harassment or bullying",
    ReportReason.INAPPROPRIATE_CONTENT to "Inappropriate content",
    ReportReason.FAKE_ACCOUNT to "Fake account",
    ReportReason.OTHER to "Other",
)

/** Stays open through a successful submit (showing a brief confirmation in place of the reason
 * list) rather than closing immediately — reporting is a one-shot action with no visible effect
 * anywhere else on screen, so without this there'd be no feedback at all that it actually went
 * through. */
@Composable
private fun ReportUserDialog(
    isReporting: Boolean,
    reportSubmitted: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (ReportReason) -> Unit,
) {
    val colors = EmberTheme.colors
    var selectedReason by remember { mutableStateOf<ReportReason?>(null) }

    EditDialogShell(title = if (reportSubmitted) "Report submitted" else "Report this account", onDismiss = onDismiss) {
        if (reportSubmitted) {
            Text(
                text = "Thanks — we'll look into it.",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                color = colors.muted,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.glow)
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = "Done", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = colors.accentText)
            }
        } else {
            Text(
                text = "What's wrong with this account?",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                color = colors.muted,
            )
            Column(modifier = Modifier.padding(top = 12.dp)) {
                REPORT_REASON_LABELS.forEach { (reason, label) ->
                    val isSelected = selectedReason == reason
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) colors.glow.copy(alpha = 0.14f) else Color.Transparent)
                            .clickable { selectedReason = reason }
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, if (isSelected) colors.glow else colors.border, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(colors.glow))
                            }
                        }
                        Text(
                            text = label,
                            fontFamily = PublicSansFontFamily,
                            fontSize = 13.5.sp,
                            color = colors.cream,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
            }
            val canSubmit = selectedReason != null && !isReporting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (canSubmit) colors.glow else colors.panel)
                    .clickable(enabled = canSubmit) { selectedReason?.let(onSubmit) }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isReporting) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), color = colors.accentText, strokeWidth = 2.dp)
                } else {
                    Text(
                        text = "Submit report",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSubmit) colors.accentText else colors.mutedDim,
                    )
                }
            }
        }
    }
}

@Composable
private fun FriendActions(
    viewModel: FriendProfileViewModel,
    friend: FriendSummaryDto,
    pillShape: RoundedCornerShape,
    onSendPhotoClick: () -> Unit,
    onPinChanged: (FriendSummaryDto) -> Unit,
    onRemoved: () -> Unit,
) {
    val colors = EmberTheme.colors

    val buttonSizePx = Size(300f, 52f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), pillShape)
            .clickable(onClick = onSendPhotoClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
        Text(
            text = "Send a photo",
            fontFamily = PublicSansFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.accentText,
            modifier = Modifier.padding(start = 8.dp),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(colors.elevatedPanel, pillShape)
            .border(1.dp, if (friend.pinnedByMe) colors.glow else colors.border, pillShape)
            .clickable(enabled = !viewModel.isUpdatingPin) { viewModel.togglePin(onPinChanged) }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewModel.isUpdatingPin) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.glow, strokeWidth = 2.dp)
        } else {
            Icon(
                Icons.Rounded.PushPin,
                contentDescription = null,
                tint = if (friend.pinnedByMe) colors.glow else colors.cream,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = if (friend.pinnedByMe) "Pinned as partner" else "Pin as partner",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (friend.pinnedByMe) colors.glow else colors.cream,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }

    Text(
        text = if (viewModel.isRemoving) "Removing…" else "Remove friend",
        fontFamily = PublicSansFontFamily,
        fontSize = 12.5.sp,
        color = Color(0xFFE8756C),
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 26.dp)
            .clickable(enabled = !viewModel.isRemoving) { viewModel.removeFriend(onRemoved) },
    )
}

@Composable
private fun PendingRequestActions(
    viewModel: FriendProfileViewModel,
    pillShape: RoundedCornerShape,
    onAccepted: (FriendSummaryDto) -> Unit,
    onRejected: () -> Unit,
) {
    val colors = EmberTheme.colors
    val busy = viewModel.isAccepting || viewModel.isRejecting

    val buttonSizePx = Size(300f, 52f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), pillShape)
            .clickable(enabled = !busy) { viewModel.acceptRequest(onAccepted) }
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewModel.isAccepting) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.accentText, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.TaskAlt, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
            Text(
                text = "Accept",
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentText,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .background(colors.elevatedPanel, pillShape)
            .border(1.dp, colors.border, pillShape)
            .clickable(enabled = !busy) { viewModel.rejectRequest(onRejected) }
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewModel.isRejecting) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.cream, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.Cancel, contentDescription = null, tint = colors.cream, modifier = Modifier.size(14.dp))
            Text(
                text = "Decline",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.cream,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }
}

/** A search result's action area depends on the relationship this user already has (if any) with
 * the found person — reuses [PendingRequestActions] as-is for the "they already requested this
 * user" case rather than a second copy of the same Accept/Decline pair. */
@Composable
private fun SearchResultActions(
    viewModel: FriendProfileViewModel,
    result: com.ember.app.data.remote.dto.FriendSearchResultDto,
    pillShape: RoundedCornerShape,
    onAccepted: (FriendSummaryDto) -> Unit,
    onRejected: () -> Unit,
) {
    when {
        result.isPendingFromThem -> PendingRequestActions(
            viewModel = viewModel,
            pillShape = pillShape,
            onAccepted = onAccepted,
            onRejected = onRejected,
        )

        result.isPendingFromMe -> RequestedActions(viewModel = viewModel, pillShape = pillShape)

        // Already friends, found again via search — that relationship's own actions
        // (Send photo/Pin/Remove) live on the Friends-tab entry point instead.
        result.requested -> Unit

        else -> AddActions(viewModel = viewModel, pillShape = pillShape)
    }
}

@Composable
private fun AddActions(viewModel: FriendProfileViewModel, pillShape: RoundedCornerShape) {
    val colors = EmberTheme.colors

    val buttonSizePx = Size(300f, 52f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), pillShape)
            .clickable(enabled = !viewModel.isSendingRequest, onClick = viewModel::sendRequest)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewModel.isSendingRequest) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.accentText, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
            Text(
                text = "Add",
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentText,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun RequestedActions(viewModel: FriendProfileViewModel, pillShape: RoundedCornerShape) {
    val colors = EmberTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .background(colors.elevatedPanel, pillShape)
            .border(1.dp, colors.border, pillShape)
            .clickable(enabled = !viewModel.isCancelling, onClick = viewModel::cancelRequest)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (viewModel.isCancelling) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = colors.cream, strokeWidth = 2.dp)
        } else {
            Icon(Icons.Rounded.Cancel, contentDescription = null, tint = colors.mutedDim, modifier = Modifier.size(14.dp))
            Text(
                text = "Cancel request",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = colors.mutedDim,
                modifier = Modifier.padding(start = 7.dp),
            )
        }
    }
}

/** Same streak-intensity ring language as the friends list (`streakRingBrush` in
 * FriendsScreen.kt) — unlit at 0, warming through a single glow color, full sweep-gradient
 * blaze at 7+. Kept as its own small copy here rather than exported cross-file for a handful
 * of lines. */
private fun streakRingBrush(colors: com.ember.app.ui.theme.EmberColors, streak: Int): Brush = when {
    streak >= 7 -> Brush.sweepGradient(listOf(colors.glow, colors.glow2, colors.violet, colors.glow))
    streak >= 3 -> Brush.linearGradient(listOf(colors.glow, colors.glow2))
    streak >= 1 -> Brush.linearGradient(listOf(colors.glow.copy(alpha = 0.8f), colors.glow.copy(alpha = 0.8f)))
    else -> Brush.linearGradient(listOf(colors.border, colors.border))
}
