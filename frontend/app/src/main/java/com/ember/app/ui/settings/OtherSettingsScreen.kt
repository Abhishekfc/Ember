package com.ember.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ember.app.ui.components.NestedScreenHeader
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import kotlinx.coroutines.launch

/** A catch-all landing spot for settings rare/serious enough that they shouldn't sit directly on
 * the main Settings list — right now just Delete account, kept one tap further away rather than
 * visible (and tappable) among the routine rows every time Settings opens. */
@Composable
fun OtherSettingsScreen(onClose: () -> Unit, onDeleteAccount: suspend () -> Result<Unit>, onAccountDeleted: () -> Unit) {
    val colors = EmberTheme.colors
    var screenSize by remember { mutableStateOf(Size.Zero) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 26.dp),
    ) {
        NestedScreenHeader(onBack = onClose, title = "Other")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(top = 8.dp)
                .clickable { showDeleteAccountDialog = true }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = DeleteAccountDestructiveColor, modifier = Modifier.size(20.dp))
            Text(
                text = "Delete account",
                fontFamily = PublicSansFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = DeleteAccountDestructiveColor,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
    }

    if (showDeleteAccountDialog) {
        DeleteAccountDialog(
            onDismiss = { showDeleteAccountDialog = false },
            onDeleteAccount = onDeleteAccount,
            onAccountDeleted = onAccountDeleted,
        )
    }
}

// Same dark red already used for "Log out" on the main Settings screen, not the lighter coral
// other confirm dialogs in the app use — two different destructive-red tones would read as
// inconsistent. `internal`, not `private` — MyProfileScreen reuses this exact red for its
// username-taken status pill rather than picking a second, differently-tuned red.
internal val DeleteAccountDestructiveColor = Color(0xFFB3261E)
private const val DELETE_CONFIRM_WORD = "delete"

/** Gated on literally typing the word "delete" (case-insensitive), not just tapping a button —
 * this is the one action in the entire app that can't be undone, so it gets a harder-to-fumble
 * confirmation than the tap-a-red-button pattern [BlockConfirmDialog]-style dialogs elsewhere use
 * for merely-annoying-if-wrong actions. Stays open through the request (showing its own spinner/
 * error inline) rather than closing optimistically, since a failed delete must be visibly a
 * failure, not silently pretend to have worked. */
@Composable
private fun DeleteAccountDialog(
    onDismiss: () -> Unit,
    onDeleteAccount: suspend () -> Result<Unit>,
    onAccountDeleted: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val scope = rememberCoroutineScope()
    var typedText by remember { mutableStateOf("") }
    var isDeleting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val canDelete = typedText.trim().equals(DELETE_CONFIRM_WORD, ignoreCase = true) && !isDeleting

    // A custom shell instead of the shared EditDialogShell other dialogs use — that one fills
    // with colors.panel, which under a dark theme like Citrus is a visibly lighter gray
    // (0xFF353535) than the screen's own near-black backdrop (0xFF111111). Every other dialog in
    // the app already reads fine at that panel tone; this one specifically needed to match the
    // actual screen behind it rather than stand out as a lighter gray box.
    Dialog(onDismissRequest = { if (!isDeleting) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(EmberRadii.dialogShape)
                .background(colors.background.baseColor())
                .padding(20.dp),
        ) {
            Text(text = "Delete account?", fontFamily = typography.display, fontSize = 18.sp, color = colors.cream)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "This can't be undone. Your photos and friends will be deleted too.",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = "Type \"delete\" to confirm",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            val fieldShape = RoundedCornerShape(14.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .background(colors.panel, fieldShape)
                    .border(1.dp, colors.border, fieldShape)
                    .padding(horizontal = 14.dp, vertical = 13.dp),
            ) {
                BasicTextField(
                    value = typedText,
                    onValueChange = { errorMessage = null; typedText = it },
                    enabled = !isDeleting,
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.cream),
                    cursorBrush = SolidColor(DeleteAccountDestructiveColor),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    fontFamily = PublicSansFontFamily,
                    fontSize = 12.sp,
                    color = DeleteAccountDestructiveColor,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
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
                        .clickable(enabled = !isDeleting, onClick = onDismiss)
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(text = "Cancel", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.muted)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        // Simple/plain until "delete" is actually typed, then turns red — the color
                        // change itself is the signal that it's now live, not something to hide.
                        .background(if (canDelete) DeleteAccountDestructiveColor else colors.panel)
                        .clickable(enabled = canDelete) {
                            isDeleting = true
                            errorMessage = null
                            scope.launch {
                                onDeleteAccount().fold(
                                    onSuccess = { onAccountDeleted() },
                                    onFailure = {
                                        isDeleting = false
                                        errorMessage = it.message ?: "Couldn't delete your account"
                                    },
                                )
                            }
                        }
                        .padding(vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Delete",
                            fontFamily = PublicSansFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canDelete) Color.White else colors.mutedDim,
                        )
                    }
                }
            }
        }
    }
}
