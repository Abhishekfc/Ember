package com.ember.app.ui.camera

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.R
import com.ember.app.data.remote.dto.SentPhotoDto
import com.ember.app.ui.components.NestedScreenHeader
import com.ember.app.ui.home.FEATURED_CARD_ASPECT_RATIO
import com.ember.app.ui.home.formatRelativeTime
import com.ember.app.ui.home.formatRemainingTime
import com.ember.app.ui.profile.EditDialogShell
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit

// Must match PhotoService.PHOTO_GRACE_PERIOD_HOURS on the backend — same duplication HomeViewModel
// already carries for its own client-side re-derivation of this exact rule.
private const val UNSEND_WINDOW_HOURS = 24L

// Same dark red every other destructive confirm in the app uses (Settings/RecipientPicker/
// Memories' own delete dialogs) — kept as its own local constant rather than a shared one, matching
// that same established convention.
private val UnsendDestructiveColor = Color(0xFFB3261E)

/** This account's own outbox — recent, unsaved sends still within their unsend window (see
 * SentPhotosViewModel/PhotoService.getRecentSent). Reached from Camera's own outbox button;
 * tapping a photo opens it full-screen with Unsend in its top-right menu. */
@Composable
fun SentPhotosScreen(
    viewModel: SentPhotosViewModel,
    onBack: () -> Unit,
) {
    val colors = EmberTheme.colors
    var screenSize by remember { mutableStateOf(Size.Zero) }
    var selectedPhoto by remember { mutableStateOf<SentPhotoDto?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // viewModel is a plain Activity-scoped instance (see MainActivity's own viewModel() call for
    // this screen) reused across every visit within a session, not recreated per-visit — without
    // this, reopening the outbox later in the same session would keep showing whatever [load]
    // last returned, even though the whole point of its 24h window is that it keeps moving.
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
    ) {
        NestedScreenHeader(onBack = onBack, title = "Sent")
        Text(
            text = "Photos you've sent in the last 24 hours. Tap one to unsend it.",
            fontFamily = PublicSansFontFamily,
            fontSize = 12.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )

        Box(modifier = Modifier.weight(1f).padding(top = 18.dp)) {
            when {
                viewModel.isLoading && viewModel.photos.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.glow)
                }

                viewModel.photos.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nothing sent in the last 24 hours.",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.sp,
                        color = colors.muted,
                    )
                }

                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 10.dp),
                ) {
                    items(viewModel.photos, key = { it.photoId }) { photo ->
                        AsyncImage(
                            model = photo.photoUrl,
                            contentDescription = "Sent ${formatRelativeTime(photo.createdAt)}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .aspectRatio(FEATURED_CARD_ASPECT_RATIO)
                                .clip(RoundedCornerShape(EmberRadii.image))
                                .clickable { selectedPhoto = photo },
                        )
                    }
                }
            }
        }
    }

    selectedPhoto?.let { photo ->
        SentPhotoViewer(
            photo = photo,
            isUnsending = viewModel.unsendingPhotoId == photo.photoId,
            onDismiss = { selectedPhoto = null },
            onUnsend = {
                coroutineScope.launch {
                    viewModel.unsend(photo.photoId).onSuccess { selectedPhoto = null }
                }
            },
        )
    }
}

/** Full-screen view of one outbox photo, with Unsend in its top-right menu — same [DropdownMenu]
 * this app already uses for Memories' own day-photo overlay (safe here regardless of that other
 * screen's own blur-related positioning history, since this screen has no blurred/graphicsLayer
 * ancestor for the menu's anchor to sit under). Tapping the photo itself dismisses, same as
 * Memories' own grown card. */
@Composable
private fun SentPhotoViewer(
    photo: SentPhotoDto,
    isUnsending: Boolean,
    onDismiss: () -> Unit,
    onUnsend: () -> Unit,
) {
    val colors = EmberTheme.colors
    var menuExpanded by remember { mutableStateOf(false) }
    var showUnsendConfirm by remember { mutableStateOf(false) }

    val expiresAt = remember(photo.createdAt) {
        runCatching { Instant.parse(photo.createdAt).plus(UNSEND_WINDOW_HOURS, ChronoUnit.HOURS) }.getOrNull()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            AsyncImage(
                model = photo.photoUrl,
                contentDescription = "Sent photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 16.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            Box {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(enabled = !isUnsending) { menuExpanded = true },
                    contentAlignment = Alignment.Center,
                ) {
                    if (isUnsending) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = colors.panel,
                    shape = EmberRadii.dialogShape,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, colors.border),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Unsend",
                                fontFamily = PublicSansFontFamily,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = UnsendDestructiveColor,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_undo),
                                contentDescription = null,
                                tint = UnsendDestructiveColor,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            showUnsendConfirm = true
                        },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
        ) {
            Text(text = "Sent ${formatRelativeTime(photo.createdAt)}", fontFamily = PublicSansFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (expiresAt != null) {
                Text(
                    text = "${formatRemainingTime(expiresAt)} to unsend",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }

    if (showUnsendConfirm) {
        UnsendConfirmDialog(
            isUnsending = isUnsending,
            onDismiss = { if (!isUnsending) showUnsendConfirm = false },
            onConfirm = onUnsend,
        )
    }
}

/** Same shell every other destructive confirm in the app uses (see MemoriesScreen's own
 * DeleteMemoryConfirmDialog) — plain "This can't be undone.", no dash, dark red confirm button.
 * Stays open through the request (spinner on the confirm button) rather than closing
 * optimistically, since a real deletion failing (most commonly the 24h window having just closed)
 * needs to visibly fail, not silently pretend to have worked. */
@Composable
private fun UnsendConfirmDialog(isUnsending: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = EmberTheme.colors
    EditDialogShell(title = "Unsend this photo?", onDismiss = onDismiss) {
        Text(
            text = "This can't be undone. It'll be removed from their feed too.",
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
                    .clip(EmberRadii.buttonShape)
                    .background(colors.panel)
                    .clickable(enabled = !isUnsending, onClick = onDismiss)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = "Cancel", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.muted)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(EmberRadii.buttonShape)
                    .background(UnsendDestructiveColor)
                    .clickable(enabled = !isUnsending, onClick = onConfirm)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isUnsending) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(text = "Unsend", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
