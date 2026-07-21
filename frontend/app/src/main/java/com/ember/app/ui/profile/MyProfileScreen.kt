package com.ember.app.ui.profile

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import java.io.File
import java.io.FileOutputStream

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

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val file = File(context.cacheDir, "ember_profile_photo_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            viewModel.uploadPhoto(file)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .padding(horizontal = 20.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(top = 32.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.panel)
                .border(1.dp, colors.border, CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.cream, modifier = Modifier.size(18.dp))
        }

        Text(
            text = "Profile",
            fontFamily = typography.display,
            fontSize = 26.sp,
            color = colors.cream,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = "This is what friends see",
            fontFamily = typography.body,
            fontSize = 12.5.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )

        // Not an avatar-plus-settings-rows layout — the same photo card every friend's send
        // appears in on Home and Friends (same shape, shadow, bottom scrim), just standing for
        // you instead of a moment someone sent. Name and username live directly on the card
        // and are each independently tappable, the way the card itself already carries that
        // information for everyone else's photos.
        val cardShape = RoundedCornerShape(30.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp)
                .aspectRatio(0.8f)
                .shadow(20.dp, cardShape, ambientColor = colors.glow.copy(alpha = 0.35f), spotColor = colors.glow.copy(alpha = 0.35f))
                .clip(cardShape)
                .background(colors.panel)
                .clickable {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
        ) {
            val photoUrl = viewModel.profile?.profilePhotoUrl
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = "Your profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.radialGradient(listOf(colors.glow.copy(alpha = 0.28f), colors.panel))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = viewModel.profile?.displayName?.firstOrNull()?.uppercase() ?: "•",
                        fontFamily = typography.display,
                        fontSize = 56.sp,
                        color = colors.cream,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))),
            )

            if (viewModel.isUploadingPhoto) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.glow, strokeWidth = 2.5.dp)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.panel.copy(alpha = 0.7f))
                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable {
                        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = "Change photo", tint = Color.White, modifier = Modifier.size(17.dp))
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 22.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        viewModel.openNameEditor()
                        showNameDialog = true
                    },
                ) {
                    Text(
                        text = viewModel.profile?.displayName.orEmpty(),
                        fontFamily = typography.display,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFFBF8F3),
                    )
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Change name",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 8.dp).size(15.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .clickable {
                            viewModel.openUsernameEditor()
                            showUsernameDialog = true
                        },
                ) {
                    Text(
                        text = viewModel.profile?.username?.let { "@$it" }.orEmpty(),
                        fontFamily = typography.body,
                        fontSize = 13.5.sp,
                        color = Color.White.copy(alpha = 0.75f),
                    )
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Change username",
                        tint = Color.White.copy(alpha = 0.55f),
                        modifier = Modifier.padding(start = 6.dp).size(12.dp),
                    )
                }
            }
        }

        // Deliberately not styled like the card above — this is account reference info, not
        // part of "what friends see," so it stays quiet and separate rather than implying it's
        // one more editable identity field.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 4.dp),
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

/** Shared chrome for both popups — dark panel card, corner radius and border matching the rest
 * of the app's cards, sized by content rather than the platform's default (narrower) dialog
 * width. */
@Composable
private fun EditDialogShell(
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
                .clip(RoundedCornerShape(24.dp))
                .background(colors.panel)
                .border(1.dp, colors.border, RoundedCornerShape(24.dp))
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

    // No fill — the dialog card behind it is already colors.panel, so a field using that same
    // color would have no visible boundary at all; the border alone is enough definition here
    // without guessing a fixed pixel size for a gradient brush that isn't meant for this.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, colors.border, shape)
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
