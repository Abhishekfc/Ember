package com.emigo.app.ui.invite

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emigo.app.R
import com.emigo.app.invite.Inviter
import com.emigo.app.ui.components.EmberBottomSheet
import com.emigo.app.ui.components.SheetMessage
import com.emigo.app.ui.components.SheetPrimaryButton
import com.emigo.app.ui.components.SheetTextButton
import com.emigo.app.ui.components.SheetTitle
import com.emigo.app.ui.theme.EmberTheme

/** Shown once to someone who just installed Emigo from a friend's invite link: add that friend with
 * one tap. The same bottom sheet as the streak and gallery ones, flat and dark. [isSending] is
 * true from the tap until the request has gone out, and keeps the sheet from being closed or
 * tapped twice meanwhile. */
@Composable
fun InvitePromptSheet(
    inviter: Inviter,
    isSending: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    EmberBottomSheet(
        onDismiss = onDismiss,
        header = {
            Spacer(modifier = Modifier.height(10.dp))
            Icon(
                imageVector = Icons.Rounded.PersonAdd,
                contentDescription = null,
                tint = EmberTheme.colors.glow,
                modifier = Modifier.size(40.dp),
            )
            SheetTitle(
                text = stringResource(R.string.invite_prompt_title, inviter.username),
                modifier = Modifier.padding(top = 14.dp),
            )
            SheetMessage(
                text = stringResource(R.string.invite_prompt_message, inviter.displayName),
                modifier = Modifier.padding(top = 8.dp),
            )
        },
        actions = { dismiss ->
            SheetPrimaryButton(
                text = stringResource(R.string.invite_prompt_add),
                icon = Icons.Rounded.PersonAdd,
                isLoading = isSending,
                onClick = onAdd,
            )
            SheetTextButton(text = stringResource(R.string.camera_maybe_later), enabled = !isSending, onClick = dismiss)
        },
    )
}
