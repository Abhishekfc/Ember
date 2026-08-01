package com.ember.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.ember.app.ui.components.NestedScreenHeader
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

@Composable
fun MyProfileScreen(
    viewModel: MyProfileViewModel,
    onClose: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val context = LocalContext.current

    var showNameDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    // Non-null while a just-picked photo is being cropped, before it's ever uploaded — the
    // gallery hands back whatever aspect ratio the original photo was, and every other profile
    // photo picker (WhatsApp, Instagram, Telegram) makes you confirm a square crop before
    // accepting it, rather than silently using the untouched original.
    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pendingCropUri = uri
    }

    val cropUri = pendingCropUri
    if (cropUri != null) {
        PhotoCropScreen(
            imageUri = cropUri,
            onCancel = { pendingCropUri = null },
            onCropped = { file ->
                pendingCropUri = null
                viewModel.uploadPhoto(file)
            },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        NestedScreenHeader(onBack = onClose)

        // Centered avatar + name + username, no "Profile" title above it any more — the avatar
        // and name together already say what this page is, the same way a contact card doesn't
        // need its own label repeating "this is a contact."
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(colors.elevatedPanel)
                    .clickable {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) {
                val photoUrl = viewModel.profile?.profilePhotoUrl
                if (photoUrl != null) {
                    AsyncImage(
                        model = photoUrl,
                        contentDescription = "Your profile photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = viewModel.profile?.displayName?.firstOrNull()?.uppercase() ?: "•",
                        fontFamily = typography.display,
                        fontSize = 36.sp,
                        color = colors.cream,
                    )
                }

                if (viewModel.isUploadingPhoto) {
                    Box(
                        modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.glow, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                }
            }

            // Bold italic display font — the one place on this screen that reads like a proper
            // signature/nameplate rather than a settings label, same spirit as the reference this
            // redesign follows.
            Text(
                text = viewModel.profile?.displayName.orEmpty(),
                fontFamily = typography.display,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                color = colors.cream,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = viewModel.profile?.username?.let { "@$it" }.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 13.5.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        // Grouped card of editable fields — tapping a row opens the exact same popups the old
        // inline name/username rows did (see NameEditDialog/UsernameEditDialog below); this is
        // just a different, more scannable entry point into the same editing flow, plus a
        // dedicated row for the photo instead of only the avatar itself being tappable. No
        // "Avatar" row — Ember has no separate Bitmoji-style avatar feature, just the one photo.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp)
                .clip(EmberRadii.dialogShape)
                .background(colors.panel)
                .border(1.dp, colors.border, EmberRadii.dialogShape),
        ) {
            ProfileFieldRow(label = "Name") {
                viewModel.openNameEditor()
                showNameDialog = true
            }
            HorizontalDivider(color = colors.border, thickness = 1.dp, modifier = Modifier.padding(horizontal = 18.dp))
            ProfileFieldRow(label = "Username") {
                viewModel.openUsernameEditor()
                showUsernameDialog = true
            }
            HorizontalDivider(color = colors.border, thickness = 1.dp, modifier = Modifier.padding(horizontal = 18.dp))
            ProfileFieldRow(label = "Profile picture") {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }

        // Deliberately not styled like the card above — this is account reference info, not
        // part of "what friends see," so it stays quiet and separate rather than implying it's
        // one more editable identity field.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = "Not editable", tint = colors.mutedDim, modifier = Modifier.size(12.dp))
            Text(
                text = viewModel.profile?.email.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                color = colors.mutedDim,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 11.5.sp,
                color = colors.glow2,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }

    if (showNameDialog) {
        NameEditDialog(
            viewModel = viewModel,
            onDismiss = { showNameDialog = false },
        )
    }
    if (showUsernameDialog) {
        UsernameEditDialog(
            viewModel = viewModel,
            onDismiss = { showUsernameDialog = false },
        )
    }
}

/** One row of the grouped fields card — label only, no icon and no current-value preview,
 * since the avatar/name/username above already show the live values. Matches the reference
 * design's plain settings-list look rather than this app's usual icon-badged rows (see
 * FlatSettingsRow in SettingsScreen.kt), which read as too busy for a 3-row card this small. */
@Composable
private fun ProfileFieldRow(label: String, onClick: () -> Unit) {
    val colors = EmberTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = PublicSansFontFamily,
            fontSize = 15.sp,
            color = colors.cream,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.mutedDim,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Shared chrome for both popups — sized by content rather than the platform's default (narrower)
 * dialog width. Internal, not private — reused as-is by FriendProfileScreen's own Block-confirm
 * and Report dialogs, rather than a second near-identical shell hand-copied there.
 *
 * `panel`, not `overlayPanel` — overlayPanel is the *lightest* tier in the theme's surface
 * ladder, which read as an oddly light popup against the rest of this dark theme. panel is the
 * genuinely dark tone every other surface here actually wants by default. */
@Composable
internal fun EditDialogShell(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(EmberRadii.dialogShape)
                .background(colors.panel)
                .padding(20.dp),
        ) {
            Text(text = title, fontFamily = typography.display, fontSize = 18.sp, color = colors.cream)
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun DialogTextField(value: String, onValueChange: (String) -> Unit, prefix: String? = null) {
    val colors = EmberTheme.colors
    val shape = RoundedCornerShape(14.dp)

    // Filled with plain panel now that the dialog card behind it is overlayPanel (a different,
    // lower tier) — reads as a recessed input, the same relationship a text field usually has to
    // its surrounding surface, rather than needing a border alone to carry the whole boundary.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panel, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) {
            Text(text = prefix, fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.mutedDim)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.cream),
            cursorBrush = SolidColor(colors.glow),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DialogActions(
    canSave: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = EmberTheme.colors
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
                .clickable(onClick = onCancel)
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = "Cancel", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.muted)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (canSave) colors.glow else colors.panel)
                .clickable(enabled = canSave && !isSaving, onClick = onSave)
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), color = colors.accentText, strokeWidth = 2.dp)
            } else {
                Text(
                    text = "Save",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) colors.accentText else colors.mutedDim,
                )
            }
        }
    }
}

@Composable
private fun NameEditDialog(viewModel: MyProfileViewModel, onDismiss: () -> Unit) {
    val colors = EmberTheme.colors
    EditDialogShell(title = "Your name", onDismiss = onDismiss) {
        DialogTextField(value = viewModel.nameDraft, onValueChange = viewModel::onNameDraftChange)
        if (viewModel.nameError != null) {
            Text(
                text = viewModel.nameError.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 11.5.sp,
                color = colors.glow2,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        DialogActions(
            canSave = viewModel.nameDraft.isNotBlank(),
            isSaving = viewModel.isSavingName,
            onCancel = onDismiss,
            onSave = { viewModel.saveName(onSaved = onDismiss) },
        )
    }
}

@Composable
private fun UsernameEditDialog(viewModel: MyProfileViewModel, onDismiss: () -> Unit) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val check = viewModel.usernameCheck
    val unchanged = viewModel.usernameDraft == viewModel.profile?.username

    EditDialogShell(title = "Your username", onDismiss = onDismiss) {
        Box {
            DialogTextField(value = viewModel.usernameDraft, onValueChange = viewModel::onUsernameDraftChange, prefix = "@")
            if (check == UsernameCheckState.Checking) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(14.dp),
                    color = colors.mutedDim,
                    strokeWidth = 2.dp,
                )
            } else if (check == UsernameCheckState.Available && !unchanged) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Available",
                    tint = colors.glow,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(16.dp),
                )
            }
        }

        when {
            unchanged -> {}
            check is UsernameCheckState.Available -> {
                Text(
                    text = "Available",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.glow,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            check is UsernameCheckState.Taken -> {
                Text(
                    text = "Already taken",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 11.5.sp,
                    color = colors.glow2,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (check.suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        check.suggestions.forEach { suggestion ->
                            // Same faint warm tint the nav dock uses for its active tab — the
                            // established "this is glowing/tappable" signal in this app, rather
                            // than a plain neutral chip.
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.glow.copy(alpha = 0.1f))
                                    .border(1.dp, colors.glow.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .clickable { viewModel.pickSuggestion(suggestion) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(text = "@$suggestion", fontFamily = PublicSansFontFamily, fontSize = 12.sp, color = colors.cream)
                            }
                        }
                    }
                }
            }
            else -> {}
        }

        if (viewModel.usernameError != null) {
            Text(
                text = viewModel.usernameError.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 11.5.sp,
                color = colors.glow2,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        DialogActions(
            canSave = viewModel.usernameDraft.length >= 3 && (unchanged || check is UsernameCheckState.Available),
            isSaving = viewModel.isSavingUsername,
            onCancel = onDismiss,
            onSave = { viewModel.saveUsername(onSaved = onDismiss) },
        )
    }
}
