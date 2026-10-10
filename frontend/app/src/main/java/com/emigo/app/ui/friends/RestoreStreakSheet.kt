package com.emigo.app.ui.friends

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.emigo.app.R
import com.emigo.app.ads.AdProgress
import com.emigo.app.ui.components.EmberBottomSheet
import com.emigo.app.ui.components.SheetMessage
import com.emigo.app.ui.components.SheetPrimaryButton
import com.emigo.app.ui.components.SheetSecondaryButton
import com.emigo.app.ui.components.SheetTextButton
import com.emigo.app.ui.components.SheetTitle
import com.emigo.app.ui.theme.EmberTheme

/** What someone without Emigo Gold sees when they tap "Restore streak": watch a few short ads, or
 * get Gold. [isWorking] is true from the tap until the streak is back (the ads themselves play on
 * top of everything), and keeps the sheet from being closed or tapped twice meanwhile. [progress]
 * says which ad is up ("Ad 2 of 3") once the server has said how many there are. */
@Composable
fun RestoreStreakSheet(
    friendName: String?,
    isWorking: Boolean,
    progress: AdProgress?,
    onWatchAd: () -> Unit,
    onGetGold: () -> Unit,
    onDismiss: () -> Unit,
) {
    EmberBottomSheet(
        onDismiss = onDismiss,
        header = {
            Spacer(modifier = Modifier.height(10.dp))
            Icon(
                imageVector = Icons.Rounded.LocalFireDepartment,
                contentDescription = null,
                tint = EmberTheme.colors.glow,
                modifier = Modifier.size(40.dp),
            )
            SheetTitle(text = stringResource(R.string.restore_sheet_title), modifier = Modifier.padding(top = 14.dp))
            SheetMessage(
                text = if (friendName != null) {
                    stringResource(R.string.restore_sheet_message_named, friendName)
                } else {
                    stringResource(R.string.restore_sheet_message)
                },
                modifier = Modifier.padding(top = 8.dp),
            )
        },
        actions = { dismiss ->
            SheetPrimaryButton(
                text = if (isWorking && progress != null) {
                    stringResource(R.string.restore_sheet_progress, progress.current, progress.total)
                } else {
                    stringResource(R.string.restore_sheet_watch_ad)
                },
                icon = Icons.Rounded.PlayArrow,
                isLoading = isWorking,
                onClick = onWatchAd,
            )
            Spacer(modifier = Modifier.height(10.dp))
            SheetSecondaryButton(
                text = stringResource(R.string.camera_get_gold),
                icon = Icons.Rounded.WorkspacePremium,
                enabled = !isWorking,
                onClick = onGetGold,
            )
            SheetTextButton(text = stringResource(R.string.camera_maybe_later), enabled = !isWorking, onClick = dismiss)
        },
    )
}
