package com.emigo.app.ui.camera

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.R
import com.emigo.app.ads.ADS_PER_GALLERY_PHOTO
import com.emigo.app.ads.MAX_GALLERY_UNLOCKS_PER_DAY
import com.emigo.app.ui.components.EmberBottomSheet
import com.emigo.app.ui.components.SheetMessage
import com.emigo.app.ui.components.SheetPalette
import com.emigo.app.ui.components.SheetPrimaryButton
import com.emigo.app.ui.components.SheetSecondaryButton
import com.emigo.app.ui.components.SheetTextButton
import com.emigo.app.ui.components.SheetTitle
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily

/** What someone without Emigo Gold sees when they tap the gallery button: watch two short ads to
 * send one photo, or get Gold to send as many as they like. [adsWatched] counts the ads done so
 * far toward the current photo. Once [isLimitReached] (all of today's free photos used), no ad can
 * help, so the sheet says so up front and offers only Gold. */
@Composable
fun GalleryAdSheet(
    adsWatched: Int,
    isWatching: Boolean,
    isLimitReached: Boolean,
    onWatchAd: () -> Unit,
    onGetGold: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = EmberTheme.colors
    EmberBottomSheet(
        onDismiss = onDismiss,
        header = {
            Spacer(modifier = Modifier.height(10.dp))
            Icon(
                imageVector = Icons.Rounded.PhotoLibrary,
                contentDescription = null,
                tint = colors.glow,
                modifier = Modifier.size(40.dp),
            )
            if (isLimitReached) {
                SheetTitle(
                    text = pluralStringResource(R.plurals.gallery_limit_title, MAX_GALLERY_UNLOCKS_PER_DAY, MAX_GALLERY_UNLOCKS_PER_DAY),
                    modifier = Modifier.padding(top = 14.dp),
                )
                SheetMessage(text = stringResource(R.string.gallery_limit_message), modifier = Modifier.padding(top = 8.dp))
            } else {
                SheetTitle(text = stringResource(R.string.gallery_sheet_title), modifier = Modifier.padding(top = 14.dp))
                SheetMessage(text = stringResource(R.string.gallery_sheet_message), modifier = Modifier.padding(top = 8.dp))
                AdProgress(watched = adsWatched, modifier = Modifier.padding(top = 20.dp))
            }
        },
        actions = { dismiss ->
            if (isLimitReached) {
                SheetPrimaryButton(
                    text = stringResource(R.string.camera_get_gold),
                    icon = Icons.Rounded.WorkspacePremium,
                    onClick = onGetGold,
                )
            } else {
                SheetPrimaryButton(
                    text = stringResource(if (adsWatched == 0) R.string.gallery_sheet_watch_ads else R.string.gallery_sheet_watch_next),
                    icon = Icons.Rounded.PlayArrow,
                    isLoading = isWatching,
                    onClick = onWatchAd,
                )
                Spacer(modifier = Modifier.height(10.dp))
                SheetSecondaryButton(
                    text = stringResource(R.string.camera_get_gold),
                    icon = Icons.Rounded.WorkspacePremium,
                    enabled = !isWatching,
                    onClick = onGetGold,
                )
            }
            SheetTextButton(text = stringResource(R.string.camera_maybe_later), enabled = !isWatching, onClick = dismiss)
        },
    )
}

/** One dot per ad needed, filling in as each is watched, with a quiet count underneath. */
@Composable
private fun AdProgress(watched: Int, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(ADS_PER_GALLERY_PHOTO) { index ->
                val fill by animateColorAsState(
                    targetValue = if (index < watched) colors.glow else SheetPalette.track,
                    label = "adDot",
                )
                Spacer(modifier = Modifier.size(9.dp).background(fill, CircleShape))
            }
        }
        Text(
            text = stringResource(R.string.gallery_sheet_progress, watched, ADS_PER_GALLERY_PHOTO),
            fontFamily = PublicSansFontFamily,
            fontSize = 13.sp,
            color = SheetPalette.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
