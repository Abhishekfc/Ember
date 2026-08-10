package com.ember.app.ui.profile

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
import com.ember.app.ui.auth.AuthPalette
import com.ember.app.ui.components.NestedScreenHeader
import com.ember.app.ui.settings.DeleteAccountDestructiveColor
import com.ember.app.ui.settings.SectionLabel
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
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
    // Same pattern Home's own MomentFocusState and Memories' DayFocusState already use for their
    // "focused overlay" — without this, the system back gesture skips past the open avatar
    // viewer straight to closing the whole Profile screen instead of just the overlay on top.
    BackHandler(enabled = showFullScreenAvatar) { showFullScreenAvatar = false }
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
                        // Opens the photo full size — changing it lives in its own dedicated
                        // "Profile picture" row below, so tapping the photo itself is purely to
                        // look at it, the same split every comparable app (WhatsApp, Instagram)
                        // makes between "view" and "edit" on an avatar.
                        .clickable { showFullScreenAvatar = true },
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

            // Flat, straight on the screen's own background — no panel/card/dividers, matching the
            // rest of the app's own established design language (see Settings/Friends: "clarity
            // comes from generous spacing and one consistent accent per row, not a card boundary").
            // This used to be a bordered panel card, a deliberate one-off that had drifted from
            // every other screen's look — that's the "old design" this replaces.
            SectionLabel(text = "Account", modifier = Modifier.padding(top = 28.dp, bottom = 2.dp))
            FlatProfileRow(label = "Name") {
                viewModel.openNameEditor()
                showNameDialog = true
            }
            FlatProfileRow(label = "Username") {
                viewModel.openUsernameEditor()
                showUsernameDialog = true
            }
            FlatProfileRow(label = "Change password") {
                viewModel.openPasswordEditor()
                showPasswordDialog = true
            }
            FlatProfileRow(label = "Profile picture") {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }

            // Deliberately not styled like the rows above — this is account reference info, not
            // part of "what friends see," so it stays quiet and separate rather than implying it's
            // one more editable identity field.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp),
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

/** Tapping the avatar shows it big, the way WhatsApp/Instagram/Telegram all do — a dim scrim plus
 * the photo itself at a large size, tap anywhere to dismiss. No crop/edit affordance here at all;
 * that's the separate "Profile picture" row's job, so this stays a pure "look at it" view. */
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
                    contentDescription = "Your profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(text = initial, fontFamily = typography.display, fontSize = 88.sp, color = colors.cream)
            }
        }
    }
}

/** One flat account row — same 56dp-floor, no-card language every other screen's list rows use
 * now (see FlatSettingsRow in SettingsScreen.kt). Label only, no value preview: the avatar/name/
 * username above already show the live values, and password never previews its own value. */
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

/** Every color these dialogs use — hand-picked hex values, not a reference into any theme's
 * tonal ladder, fixed or otherwise. [AuthPalette] was tried first (same fixed palette the login
 * flow uses) and was still too bright/washed for a modal floating over arbitrary photo content:
 * its `elevatedPanel` ultimately traces back to Ember New's own theme definition, proportionally
 * derived from that theme's `panel`, so it inherits whatever that theme happens to be tuned to
 * rather than being an independent choice. These values answer to nothing but this screen. */
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

/** Shared chrome for every popup on this screen — sized by content rather than the platform's
 * default (narrower) dialog width. Internal, not private — reused as-is by FriendProfileScreen's
 * own Block-confirm and Report dialogs, rather than a second near-identical shell hand-copied
 * there.
 *
 * Colors come from [DialogPalette] above — fixed hex values, not [EmberTheme] or even the
 * fixed-but-still-theme-derived [AuthPalette]. Editing
 * your name/username/password is a small return to that same front-door identity surface, and
 * the live theme's own `elevatedPanel`/`panel` tiers turned out to sit almost on top of each
 * other in at least one theme, rendering the whole dialog as a flat gray blob with no contrast
 * between card, fields, and buttons — a fixed, known-good palette sidesteps that entirely rather
 * than trying to patch every theme's tonal ladder to cooperate.
 *
 * `elevatedPanel`, one tier up from the plain `panel` fields sit on inside it — a modal is the
 * single most prominent surface on screen the moment it's open. Fades and scales in from 92% on
 * first composition rather than snapping into place — `Dialog` itself has no built-in transition,
 * so this is done by hand the same way [AuthPhoneFrame]'s own border fade-in is: animate on
 * entry, touch nothing on the way out. */
@Composable
internal fun EditDialogShell(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    // A transient banner near the top of the dialog window, not inline text inside the card —
    // errors here are momentary ("that password's wrong"), not a permanent fact about the form,
    // so they get a toast that appears and clears itself rather than layout that sits there
    // until the next successful edit.
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
        // Compose's Dialog opens its own separate Android Window with its own view hierarchy —
        // MainActivity's own autofill-exclusion fix (see its own doc comment, applied once to
        // the Activity's root ComposeView) never reaches it, which is why typing into these
        // password fields still triggered Google Password Manager's "Save password?" prompt
        // after the dialog closed. Same fix, applied to *this* window's own root view instead.
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

/** A solid white pill with black text — not the plain colored text every other status message on
 * this screen uses, since this one has to read clearly floating over an arbitrary photo/backdrop
 * behind the dialog's scrim rather than sitting on a known dark surface. Purely a display: it has
 * no way to clear the message itself (the ViewModel owns that state), so the caller is
 * responsible for nulling it out after a beat — see each dialog's own
 * `LaunchedEffect(errorNonce) { delay(...); clearXError() }`. Pure position slide down on arrival
 * and back up on the way out — no fade paired with it. Fade-plus-slide together was what read as
 * the pill shrinking (fading and moving at once tricks the eye into seeing it get smaller); a
 * slide on its own has no size or opacity component, so it just moves, cleanly, both ways. */
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

    // Filled a shade lighter than the card behind it — reads as a recessed input, the same
    // relationship a text field usually has to its surrounding surface. The focus border is a
    // plain white outline, not the brand yellow — a focus ring is functional (shows which field
    // is active), not a brand moment, so it stays neutral like everything else in this palette.
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

/** Same recessed-field treatment as [DialogTextField], obscured by default with a trailing
 * show/hide toggle — the standard password-field pattern every major app uses, rather than a
 * permanently-masked field with no way to double check what was typed. */
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
            contentDescription = if (visible) "Hide password" else "Show password",
            tint = colors.mutedDim,
            modifier = Modifier
                .padding(start = 10.dp)
                .size(18.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { visible = !visible },
        )
    }
}

/** A small status badge — icon plus tinted pill, not plain colored text — for a field's live
 * validation state (username availability today). Reads as a proper status indicator rather than
 * a stray line of colored text. */
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

/** Cancel stays a plain text tap — no box, no border — so the one filled, solid-color control on
 * screen is unambiguously the actual action; a boxed Cancel next to a boxed Save reads as two
 * options of equal weight, when they're not. Save fills solid white/dark-text rather than any
 * accent color — a neutral, high-contrast pill is the "billion-dollar app" primary-button
 * convention (iOS action sheets, most polished onboarding flows), where the accent color is spent
 * elsewhere (badges, live states) rather than on every button. */
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
            Text(text = "Cancel", fontFamily = PublicSansFontFamily, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.muted)
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
                    text = "Save",
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
    // Keyed on the nonce, not the error string — two failures in a row can carry the identical
    // message, and keying on that would silently fail to restart this timer on the second one
    // (see nameErrorNonce's own doc comment in MyProfileViewModel).
    LaunchedEffect(viewModel.nameErrorNonce) {
        if (viewModel.nameError != null) {
            delay(2600)
            viewModel.clearNameError()
        }
    }
    EditDialogShell(
        title = "Your name",
        subtitle = "This is the name your friends will see.",
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

    // Keyed on the nonce, not the error string — see nameErrorNonce's doc comment.
    LaunchedEffect(viewModel.usernameErrorNonce) {
        if (viewModel.usernameError != null) {
            delay(2600)
            viewModel.clearUsernameError()
        }
    }

    EditDialogShell(
        title = "Your username",
        subtitle = "Used to find and tag you around the app.",
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
                    contentDescription = "Available",
                    tint = colors.fieldFocus,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 14.dp).size(16.dp),
                )
            }
        }

        when {
            unchanged -> {}
            check is UsernameCheckState.Available -> {
                StatusPill(
                    text = "Available",
                    icon = Icons.Filled.Check,
                    tint = colors.fieldFocus,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            check is UsernameCheckState.Taken -> {
                // The app's one other established fixed danger red, reused here rather than
                // inventing a second one — a distinct color from the "Available" state's yellow,
                // which the two states need to actually look different from each other.
                StatusPill(
                    text = "Already taken",
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
                            // Same faint warm tint the nav dock uses for its active tab — the
                            // established "this is glowing/tappable" signal in this app, rather
                            // than a plain neutral chip. Bold label to match the identical chip
                            // in the signup flow's own username step (RegisterUsernameStep) —
                            // this one was missing the weight, the one visible difference between
                            // the two.
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

    // Google Password Manager's save-prompt heuristic fires on the window closing while a
    // password-shaped field held a value — it has no idea whether the change actually succeeded.
    // A wrong-current-password error (dialog stays open) followed by Cancel, or Cancel/tap-
    // outside/back on their own, all count as "closed" to Autofill just as much as a real
    // success does. `cancel()` explicitly tells it not to offer a save for this session; it's a
    // safe no-op if no session is active. Routed through every non-success exit (this becomes
    // both EditDialogShell's onDismiss — covering tap-outside and back — and DialogActions'
    // onCancel below) so the prompt only ever has a chance to fire from the one real success
    // path, which deliberately calls the *original* onDismiss instead of this one.
    val cancelAndDismiss: () -> Unit = {
        autofillManager?.cancel()
        onDismiss()
    }

    // Keyed on the nonce, not the error string — see nameErrorNonce's doc comment. This is the
    // dialog most likely to actually hit the collision (retrying an identical wrong password).
    LaunchedEffect(viewModel.passwordErrorNonce) {
        if (viewModel.passwordError != null) {
            delay(2600)
            viewModel.clearPasswordError()
        }
    }
    EditDialogShell(
        title = "Change password",
        subtitle = "Use at least 8 characters. You'll stay signed in on this device.",
        onDismiss = cancelAndDismiss,
        errorToast = viewModel.passwordError,
    ) {
        PasswordDialogTextField(
            value = viewModel.currentPasswordDraft,
            onValueChange = viewModel::onCurrentPasswordDraftChange,
            placeholder = "Current password",
        )
        Spacer(modifier = Modifier.height(10.dp))
        PasswordDialogTextField(
            value = viewModel.newPasswordDraft,
            onValueChange = viewModel::onNewPasswordDraftChange,
            placeholder = "New password",
        )
        Spacer(modifier = Modifier.height(10.dp))
        PasswordDialogTextField(
            value = viewModel.confirmPasswordDraft,
            onValueChange = viewModel::onConfirmPasswordDraftChange,
            placeholder = "Confirm new password",
        )

        DialogActions(
            canSave = viewModel.currentPasswordDraft.isNotEmpty() &&
                viewModel.newPasswordDraft.length >= 8 &&
                viewModel.confirmPasswordDraft.isNotEmpty(),
            isSaving = viewModel.isSavingPassword,
            onCancel = cancelAndDismiss,
            // Name/username changes are self-evidently confirmed — the new value shows up on
            // screen the instant the dialog closes. A password change has nothing visible to
            // show for itself, so without an explicit confirmation here there's no way to tell
            // the save actually landed versus silently doing nothing.
            onSave = {
                viewModel.savePassword(onSaved = {
                    Toast.makeText(context, "Password changed", Toast.LENGTH_SHORT).show()
                    onDismiss()
                })
            },
        )
    }
}
