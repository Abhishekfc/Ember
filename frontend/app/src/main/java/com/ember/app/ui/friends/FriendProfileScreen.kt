package com.ember.app.ui.friends

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonRemove
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.data.remote.dto.ReportReason
import com.ember.app.ui.components.emberButtonBrush
import com.ember.app.ui.home.formatRelativeTime
import com.ember.app.ui.profile.EditDialogShell
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

/** Same coral used by the existing "Remove friend" destructive action below — one consistent
 * "this is a leaving/negative action" color across the whole profile screen, not a second one
 * invented just for Block/Report. */
private val DestructiveColor = Color(0xFFE8756C)

/** A full-bleed hero photo at the top — same structural idea Instagram/Snapchat's own profile
 * screens use — instead of the small centered circle avatar this used to be. Back and overflow
 * float directly on the photo in translucent scrim-backed circles; the streak (the one real,
 * meaningful stat this app actually has) sits as its own badge over the photo's bottom-left
 * corner, the same spot a follower count would occupy on those other apps' profiles. Everything
 * below the photo is data Ember genuinely has — no bio, location, or follower count invented to
 * fill space the reference image had real content for and this app doesn't: "Last sent" (a real,
 * already-used-elsewhere fact) stands in for the bio line instead of a placeholder. Only the
 * action area at the bottom differs by relationship: Send photo/Pin/Remove for a friend,
 * Accept/Decline for a pending request, and for a search result — Add if there's no relationship
 * yet, Accept/Decline if they already requested this user, a cancelable Requested state if this
 * user already requested them, and nothing at all if they're already friends (that has its own
 * dedicated actions, reached from the Friends tab instead). */
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
    val friend = (subject as? ProfileSubject.Friend)?.summary
    val streak = friend?.streak ?: 0
    val pillShape = EmberRadii.buttonShape

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .navigationBarsPadding(),
    ) {
        // Square, edge-to-edge, running up under the status bar — the back/overflow controls
        // below carry their own statusBarsPadding instead of the whole screen having one, which
        // is what lets the photo itself reach the very top rather than stopping short of it.
        // Rounded on the bottom two corners only — flush with the screen everywhere else, the
        // one place it isn't butting up against a real edge.
        val heroShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(heroShape)) {
            if (subject.profilePhotoUrl != null) {
                AsyncImage(
                    model = subject.profilePhotoUrl,
                    contentDescription = subject.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // No fake placeholder photo — the app's own premium accent gradient (the same
                // fill every primary button already uses) with the person's initial, so a friend
                // with no profile photo still reads as a deliberate, branded hero rather than a
                // broken image.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(emberButtonBrush(EmberTheme.key, colors, Size(screenSize.width, screenSize.width))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = subject.displayName.firstOrNull()?.uppercase() ?: "•",
                        fontFamily = typography.display,
                        fontSize = 96.sp,
                        color = colors.accentText,
                    )
                }
            }

            // Top scrim — just enough for the back/overflow circles to stay legible over a
            // bright photo, without darkening the rest of the hero.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent))),
            )
            // Bottom scrim — always present now, not just for the streak badge, since the
            // identity block (name/username/last-sent) lives directly on the photo too and
            // needs to stay legible over whatever the photo itself contains.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)))),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                HeroCircleButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Box {
                    HeroCircleButton(onClick = { showOverflowMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = Color.White, modifier = Modifier.size(20.dp))
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
                        // Only for an actual friend — nothing to unfriend for a pending request
                        // or a stranger found via search, the other two subjects this same menu
                        // can appear for.
                        if (friend != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Unfriend",
                                        fontFamily = PublicSansFontFamily,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = DestructiveColor,
                                    )
                                },
                                leadingIcon = { Icon(Icons.Rounded.PersonRemove, contentDescription = null, tint = DestructiveColor, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showOverflowMenu = false
                                    viewModel.removeFriend(onRemoved)
                                },
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                            HorizontalDivider(color = colors.border, thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))
                        }
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

            // Everything else — streak, name, username, "last sent" — stacked directly on the
            // photo itself over the scrim above, the same way the reference profile keeps its
            // whole identity block sitting on the image rather than on the plain background
            // below it. Always white/near-white text here regardless of theme: this sits over an
            // arbitrary photo, not the app's own surface, so it needs to stay legible no matter
            // which theme is active or what the photo's own colors are.
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) {
                if (streak > 0) {
                    // Sits where a follower count would on the reference profile — the one real
                    // stat this app actually has, in the same "here's the number that matters" spot.
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = colors.glow, modifier = Modifier.size(14.dp))
                        Text(
                            text = "$streak",
                            fontFamily = PublicSansFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Text(text = subject.displayName, fontFamily = typography.display, fontSize = 26.sp, color = Color.White)
                    // Real, not decorative — the same "pinned partner" state Friends' own list and
                    // the action button below both already show, just surfaced here too rather than
                    // a fabricated verified checkmark the reference had and this app has no
                    // equivalent concept for.
                    if (friend?.pinnedByMe == true) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = "Pinned as partner",
                            tint = colors.glow,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                        )
                    }
                }
                Text(
                    text = "@${subject.username}",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 3.dp),
                )
                // Stands in for a bio line — the one other real, human fact this profile has.
                // Direction-aware, not a blind "Last sent": lastActivityBySelf says whether that
                // most recent exchange was this account sending or the friend sending, so this
                // can actually say who — a plain "Last sent" left that ambiguous.
                friend?.lastActivityAt?.let { lastActivityAt ->
                    val label = if (friend.lastActivityBySelf == true) {
                        "You sent ${formatRelativeTime(lastActivityAt)}"
                    } else {
                        "Sent to you ${formatRelativeTime(lastActivityAt)}"
                    }
                    Text(
                        text = label,
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            if (viewModel.errorMessage != null) {
                Text(
                    text = viewModel.errorMessage.orEmpty(),
                    fontFamily = PublicSansFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.glow2,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                )
            }

            when (subject) {
                is ProfileSubject.Friend -> FriendActions(
                    viewModel = viewModel,
                    friend = subject.summary,
                    pillShape = pillShape,
                    onSendPhotoClick = onSendPhotoClick,
                    onPinChanged = onPinChanged,
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

/** Translucent dark circle, same treatment for both back and overflow — legible over any photo
 * regardless of its own colors, since it doesn't depend on the theme's own palette the way a
 * plain tinted glyph (this app's usual icon style) would against a bright or busy image. */
@Composable
private fun HeroCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
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
                text = "Thanks, we'll look into it.",
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
) {
    val colors = EmberTheme.colors

    val buttonSizePx = Size(300f, 52f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp)
            .background(emberButtonBrush(EmberTheme.key, colors, buttonSizePx), pillShape)
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
            .background(emberButtonBrush(EmberTheme.key, colors, buttonSizePx), pillShape)
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
            .background(emberButtonBrush(EmberTheme.key, colors, buttonSizePx), pillShape)
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
