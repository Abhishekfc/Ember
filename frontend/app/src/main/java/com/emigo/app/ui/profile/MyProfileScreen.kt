package com.emigo.app.ui.profile

import android.net.Uri
import android.view.View
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.emigo.app.R
import com.emigo.app.ui.auth.AuthPalette
import com.emigo.app.ui.components.NestedScreenHeader
import com.emigo.app.ui.settings.DeleteAccountDestructiveColor
import com.emigo.app.ui.settings.SectionLabel
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import kotlinx.coroutines.delay

@Composable
fun MyProfileScreen(
    viewModel: MyProfileViewModel,
    onClose: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }

    var showNameDialog by remember { mutableStateOf(false) }
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showFullScreenAvatar by remember { mutableStateOf(false) }
    // Same pattern as Home's MomentFocusState and Memories' MemoryFocusState: without it, the
    // system back gesture skips the open avatar viewer and closes the whole Profile screen.
    BackHandler(enabled = showFullScreenAvatar) { showFullScreenAvatar = false }
    // Non-null while a just-picked photo is being cropped, before it is uploaded. The gallery
    // returns the photo in its original aspect ratio; other apps (WhatsApp, Instagram, Telegram)
    // make you confirm a square crop first, so this does too.
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

    Box(modifier = Modifier.fillMaxSize()) {
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

            // Centered avatar, name and username, with no "Profile" title: they already say what
            // this page is.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(colors.elevatedPanel)
                        // Opens the photo full size. Changing it is the separate "Profile
                        // picture" row below, the same view/edit split WhatsApp and Instagram use.
                        .clickable { showFullScreenAvatar = true },
                    contentAlignment = Alignment.Center,
                ) {
                    val photoUrl = viewModel.profile?.profilePhotoUrl
                    if (photoUrl != null) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = stringResource(R.string.settings_profile_photo_description),
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

                // Bold italic display font: the one place on this screen that reads like a
                // nameplate rather than a settings label.
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

            // Flat rows straight on the screen background, with no card or dividers, like Settings
            // and Friends: clarity comes from spacing and one accent per row, not a card boundary.
            SectionLabel(text = stringResource(R.string.profile_section_account), modifier = Modifier.padding(top = 28.dp, bottom = 2.dp))
            FlatProfileRow(label = stringResource(R.string.profile_row_name)) {
                viewModel.openNameEditor()
                showNameDialog = true
            }
            FlatProfileRow(label = stringResource(R.string.profile_row_username)) {
                viewModel.openUsernameEditor()
                showUsernameDialog = true
            }
            FlatProfileRow(label = stringResource(R.string.profile_password_title)) {
                viewModel.openPasswordEditor()
                showPasswordDialog = true
            }
            FlatProfileRow(label = stringResource(R.string.profile_row_picture)) {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }

            // Deliberately not styled like the rows above: this is account reference info, not
            // part of what friends see, so it stays quiet instead of looking like another
            // editable field.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.profile_not_editable_description), tint = colors.mutedDim, modifier = Modifier.size(12.dp))
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

        AnimatedVisibility(
            visible = showFullScreenAvatar,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.85f, animationSpec = tween(220)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.9f, animationSpec = tween(150)),
        ) {
            AvatarFullScreenViewer(
                photoUrl = viewModel.profile?.profilePhotoUrl,
                initial = viewModel.profile?.displayName?.firstOrNull()?.uppercase() ?: "•",
                onDismiss = { showFullScreenAvatar = false },
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
    if (showPasswordDialog) {
        PasswordChangeDialog(
            viewModel = viewModel,
            onDismiss = { showPasswordDialog = false },
        )
    }
}

/** The avatar shown big, as WhatsApp, Instagram and Telegram do: a dim scrim, the photo large, tap
 * anywhere to dismiss. No crop or edit here; that is the separate "Profile picture" row's job. */
@Composable
private fun AvatarFullScreenViewer(photoUrl: String?, initial: String, onDismiss: () -> Unit) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(colors.elevatedPanel),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUrl != null) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = stringResource(R.string.settings_profile_photo_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(text = initial, fontFamily = typography.display, fontSize = 88.sp, color = colors.cream)
            }
        }
    }
}

/** One flat account row, with the same 56dp minimum height and no card as every other list row
 * (see FlatSettingsRow in SettingsScreen.kt). Label only, no value preview: the avatar, name and
 * username above already show the live values, and a password never previews its value. */
@Composable
private fun FlatProfileRow(label: String, onClick: () -> Unit) {
    val colors = EmberTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = PublicSansFontFamily,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.cream,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.mutedDim,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** Every color these dialogs use: fixed hex values, independent of any theme's surface ladder.
 * [AuthPalette] was tried first and was still too bright for a modal floating over arbitrary photo
 * content, because its `elevatedPanel` is derived from a theme's `panel` and inherits whatever that
 * theme is tuned to. These values answer to nothing but this screen. */
private object DialogPalette {
    val card = Color(0xFF19181A)
    val cardBorder = Color(0x1AFFFFFF)
    val field = Color(0xFF242226)
    val fieldFocus = Color(0xFFFFFB0A)
    val cream = Color(0xFFF3EFE6)
    val muted = Color(0xFFA39D9B)
    val mutedDim = Color(0xFF6E696C)
    val danger = Color(0xFFE0574C)
    val onLight = Color(0xFF16151A)
}

/** Shared chrome for every popup on this screen, sized by content instead of the platform's
 * narrower default dialog width. Internal because FriendProfileScreen reuses it for its Block and
 * Report dialogs instead of copying a second shell.
 *
 * Colors come from [DialogPalette], not [EmberTheme] or [AuthPalette]. The live theme's
 * `elevatedPanel` and `panel` sat almost on top of each other in at least one theme, rendering the
 * whole dialog as a flat gray blob with no contrast between card, fields and buttons; a fixed,
 * known-good palette avoids patching every theme's ladder.
 *
 * `Dialog` has no built-in transition, so it fades and scales in from 92% on first composition by
 * hand (the same way [AuthPhoneFrame]'s border fade-in works) and does nothing on the way out. */
@Composable
internal fun EditDialogShell(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    // A transient banner near the top of the dialog window, not inline text in the card. These
    // errors are momentary ("that password's wrong"), so they clear themselves instead of sitting
    // in the layout until the next successful edit.
    errorToast: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = DialogPalette

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entryProgress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(240, easing = FastOutSlowInEasing),
        label = "dialogEntry",
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // A Compose Dialog opens its own Android Window with its own view hierarchy, so
        // MainActivity's autofill exclusion (applied once to the Activity's root ComposeView) never
        // reaches it. That is why typing into these password fields still triggered Google
        // Password Manager's "Save password?" prompt after the dialog closed. The same fix is
        // applied here to this window's root view.
        val dialogView = LocalView.current
        LaunchedEffect(dialogView) {
            dialogView.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer {
                        alpha = entryProgress
                        scaleX = 0.92f + entryProgress * 0.08f
                        scaleY = 0.92f + entryProgress * 0.08f
                    }
                    .clip(EmberRadii.dialogShape)
                    .background(colors.card)
                    .border(1.dp, colors.cardBorder, EmberRadii.dialogShape)
                    .padding(22.dp),
            ) {
                Text(text = title, fontFamily = AuthPalette.display, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = colors.cream)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontFamily = PublicSansFontFamily,
                        fontSize = 12.5.sp,
                        color = colors.muted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                content()
            }

            DialogTopToast(
                message = errorToast,
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 28.dp),
            )
        }
    }
}

/** A solid white pill with black text, not the plain colored text other status messages use,
 * because it has to read clearly over an arbitrary photo or backdrop behind the dialog's scrim.
 * Display only: the ViewModel owns the message, so the caller must clear it after a beat (see each
 * dialog's `LaunchedEffect(errorNonce) { delay(...); clearXError() }`).
 *
 * It only slides down on arrival and back up on exit, with no fade. Fade plus slide read as the
 * pill shrinking, because fading and moving at once tricks the eye into seeing it get smaller. */
@Composable
private fun DialogTopToast(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = slideInVertically(initialOffsetY = { -it / 2 }),
        exit = slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .shadow(elevation = 10.dp, shape = RoundedCornerShape(50), ambientColor = Color.Black, spotColor = Color.Black)
                .clip(RoundedCornerShape(50))
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 11.dp),
        ) {
            Text(
                text = message.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = DialogPalette.onLight,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DialogTextField(value: String, onValueChange: (String) -> Unit, prefix: String? = null) {
    val colors = DialogPalette
    val shape = RoundedCornerShape(14.dp)
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderAlpha by animateFloatAsState(if (isFocused) 1f else 0f, label = "fieldFocusBorder")

    // Filled a shade lighter than the card, so it reads as a recessed input. The focus border is a
    // plain white outline, not the brand yellow: a focus ring is functional (shows which field is
    // active), not a brand moment.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.field, shape)
            .border(1.2.dp, Color.White.copy(alpha = borderAlpha * 0.3f), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) {
            Text(text = prefix, fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.mutedDim)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.cream),
            cursorBrush = SolidColor(colors.fieldFocus),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            interactionSource = interactionSource,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Same recessed field as [DialogTextField], masked by default with a trailing show/hide toggle, so
 * what was typed can be double-checked. */
@Composable
private fun PasswordDialogTextField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    val colors = DialogPalette
    val shape = RoundedCornerShape(14.dp)
    var visible by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderAlpha by animateFloatAsState(if (isFocused) 1f else 0f, label = "passwordFieldFocusBorder")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.field, shape)
            .border(1.2.dp, Color.White.copy(alpha = borderAlpha * 0.3f), shape)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = placeholder, fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.mutedDim)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.cream),
                cursorBrush = SolidColor(colors.fieldFocus),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                interactionSource = interactionSource,
            )
        }
        Icon(
            imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
            contentDescription = stringResource(if (visible) R.string.auth_hide_password else R.string.auth_show_password),
            tint = colors.mutedDim,
            modifier = Modifier
                .padding(start = 10.dp)
                .size(18.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { visible = !visible },
        )
    }
}

/** A small status badge (icon on a tinted pill) for a field's live validation state, username
 * availability today. */
@Composable
private fun StatusPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(
            text = text,
            fontFamily = PublicSansFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

/** Cancel is plain text, with no box or border, so the one filled control is unambiguously the
 * action: a boxed Cancel beside a boxed Save reads as two options of equal weight. Save is a solid
 * white pill with dark text instead of an accent color, a neutral high-contrast primary button;
 * accent colors are kept for badges and live states. */
@Composable
private fun DialogActions(
    canSave: Boolean,
    isSaving: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    val colors = DialogPalette
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .clickable(onClick = onCancel)
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(text = stringResource(R.string.common_cancel), fontFamily = PublicSansFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
        }
        Row(
            modifier = Modifier
                .weight(1.4f)
                .clip(RoundedCornerShape(14.dp))
                .background(if (canSave) Color.White else colors.field)
                .clickable(enabled = canSave && !isSaving, onClick = onSave)
                .padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            if (isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(15.dp), color = colors.onLight, strokeWidth = 2.dp)
            } else {
                Text(
                    text = stringResource(R.string.common_save),
                    fontFamily = PublicSansFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) colors.onLight else colors.mutedDim,
                )
            }
        }
    }
}

@Composable
private fun NameEditDialog(viewModel: MyProfileViewModel, onDismiss: () -> Unit) {
    // Keyed on the nonce, not the error string: two failures in a row can carry the identical
    // message, and keying on that would fail to restart this timer on the second one (see
    // nameErrorNonce in MyProfileViewModel).
    LaunchedEffect(viewModel.nameErrorNonce) {
        if (viewModel.nameError != null) {
            delay(2600)
            viewModel.clearNameError()
        }
    }
    EditDialogShell(
        title = stringResource(R.string.profile_name_title),
        subtitle = stringResource(R.string.profile_name_subtitle),
        onDismiss = onDismiss,
        errorToast = viewModel.nameError,
    ) {
        DialogTextField(value = viewModel.nameDraft, onValueChange = viewModel::onNameDraftChange)
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
    val colors = DialogPalette
    val check = viewModel.usernameCheck
    val unchanged = viewModel.usernameDraft == viewModel.profile?.username

    // Keyed on the nonce, not the error string; see nameErrorNonce.
    LaunchedEffect(viewModel.usernameErrorNonce) {
        if (viewModel.usernameError != null) {
            delay(2600)
            viewModel.clearUsernameError()
        }
    }

    EditDialogShell(
        title = stringResource(R.string.profile_username_title),
        subtitle = stringResource(R.string.profile_username_subtitle),
        onDismiss = onDismiss,
        errorToast = viewModel.usernameError,
    ) {
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
                    contentDescription = stringResource(R.string.profile_username_available),
                    tint = colors.fieldFocus,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(16.dp),
                )
            }
        }

        when {
            unchanged -> {}
            check is UsernameCheckState.Available -> {
                StatusPill(
                    text = stringResource(R.string.profile_username_available),
                    icon = Icons.Filled.Check,
                    tint = colors.fieldFocus,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            check is UsernameCheckState.Taken -> {
                // The app's other fixed danger red, reused instead of inventing a second. It must
                // look different from the yellow "Available" state.
                StatusPill(
                    text = stringResource(R.string.profile_username_taken),
                    icon = Icons.Filled.Close,
                    tint = DeleteAccountDestructiveColor,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (check.suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        check.suggestions.forEach { suggestion ->
                            // Same faint warm tint the nav dock uses for its active tab, the app's
                            // "glowing, tappable" signal, with a bold label to match the identical
                            // chip in the signup flow's username step (RegisterUsernameStep).
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.fieldFocus.copy(alpha = 0.1f))
                                    .border(1.dp, colors.fieldFocus.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .clickable { viewModel.pickSuggestion(suggestion) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = "@$suggestion",
                                    fontFamily = PublicSansFontFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.cream,
                                )
                            }
                        }
                    }
                }
            }
            else -> {}
        }

        DialogActions(
            canSave = viewModel.usernameDraft.length >= 3 && (unchanged || check is UsernameCheckState.Available),
            isSaving = viewModel.isSavingUsername,
            onCancel = onDismiss,
            onSave = { viewModel.saveUsername(onSaved = onDismiss) },
        )
    }
}

@Composable
private fun PasswordChangeDialog(viewModel: MyProfileViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val autofillManager = remember { context.getSystemService(AutofillManager::class.java) }

    // Google Password Manager offers to save a password when the window closes while a
    // password-shaped field held a value, with no idea whether the change succeeded. A wrong
    // current password (dialog stays open) then Cancel, or Cancel, tap-outside or back on their own,
    // all count as "closed" to Autofill just like a success. `cancel()` tells it not to offer a
    // save for this session (a safe no-op if none is active). Every non-success exit goes through
    // this (it is both EditDialogShell's onDismiss, covering tap-outside and back, and
    // DialogActions' onCancel), so the prompt can only fire from the one real success path, which
    // deliberately calls the original onDismiss instead.
    val cancelAndDismiss: () -> Unit = {
        autofillManager?.cancel()
        onDismiss()
    }

    // Keyed on the nonce, not the error string; see nameErrorNonce. This dialog is the most likely
    // to hit the collision (retrying an identical wrong password).
    LaunchedEffect(viewModel.passwordErrorNonce) {
        if (viewModel.passwordError != null) {
            delay(2600)
            viewModel.clearPasswordError()
        }
    }
    EditDialogShell(
        title = stringResource(R.string.profile_password_title),
        subtitle = stringResource(R.string.profile_password_subtitle),
        onDismiss = cancelAndDismiss,
        errorToast = viewModel.passwordError,
    ) {
        PasswordDialogTextField(
            value = viewModel.currentPasswordDraft,
            onValueChange = viewModel::onCurrentPasswordDraftChange,
            placeholder = stringResource(R.string.profile_current_password_hint),
        )
        Spacer(modifier = Modifier.height(10.dp))
        PasswordDialogTextField(
            value = viewModel.newPasswordDraft,
            onValueChange = viewModel::onNewPasswordDraftChange,
            placeholder = stringResource(R.string.profile_new_password_hint),
        )
        Spacer(modifier = Modifier.height(10.dp))
        PasswordDialogTextField(
            value = viewModel.confirmPasswordDraft,
            onValueChange = viewModel::onConfirmPasswordDraftChange,
            placeholder = stringResource(R.string.profile_confirm_password_hint),
        )

        val passwordChangedMessage = stringResource(R.string.profile_password_changed)
        DialogActions(
            canSave = viewModel.currentPasswordDraft.isNotEmpty() &&
                viewModel.newPasswordDraft.length >= 8 &&
                viewModel.confirmPasswordDraft.isNotEmpty(),
            isSaving = viewModel.isSavingPassword,
            onCancel = cancelAndDismiss,
            // A new name or username shows on screen the moment the dialog closes. A password
            // change has nothing visible to show, so this explicit confirmation is the only way to
            // tell the save landed.
            onSave = {
                viewModel.savePassword(onSaved = {
                    Toast.makeText(context, passwordChangedMessage, Toast.LENGTH_SHORT).show()
                    onDismiss()
                })
            },
        )
    }
}
