package com.emigo.app.ui.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.PersonSearch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.emigo.app.R
import com.emigo.app.ui.components.EmberBottomSheet
import com.emigo.app.ui.components.SheetMessage
import com.emigo.app.ui.components.SheetPalette
import com.emigo.app.ui.components.SheetPrimaryButton
import com.emigo.app.ui.components.SheetSecondaryButton
import com.emigo.app.ui.components.SheetTextButton
import com.emigo.app.ui.components.SheetTitle
import java.io.File

/** What someone with no friends yet sees when they tap Send on a photo they just took: the photo
 * itself (so it is clear nothing is lost), and two ways forward, find a friend already on Emigo or
 * invite someone who isn't. The photo stays on the review screen behind this the whole time. */
@Composable
fun NoFriendsSheet(
    previewBitmap: Bitmap?,
    photoFile: File?,
    onFindFriends: () -> Unit,
    onInviteFriends: () -> Unit,
    onDismiss: () -> Unit,
) {
    EmberBottomSheet(
        onDismiss = onDismiss,
        header = {
            Spacer(modifier = Modifier.height(10.dp))
            PhotoThumbnail(previewBitmap = previewBitmap, photoFile = photoFile)
            SheetTitle(text = stringResource(R.string.no_friends_sheet_title), modifier = Modifier.padding(top = 22.dp))
            SheetMessage(text = stringResource(R.string.no_friends_sheet_message), modifier = Modifier.padding(top = 8.dp))
        },
        actions = { dismiss ->
            SheetPrimaryButton(
                text = stringResource(R.string.no_friends_sheet_find),
                icon = Icons.Rounded.PersonSearch,
                onClick = {
                    onDismiss()
                    onFindFriends()
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
            SheetSecondaryButton(
                text = stringResource(R.string.no_friends_sheet_invite),
                icon = Icons.Rounded.IosShare,
                onClick = onInviteFriends,
            )
            SheetTextButton(text = stringResource(R.string.camera_maybe_later), onClick = dismiss)
        },
    )
}

/** The photo as it appears on the review screen, in the same 4:5 shape and the same crop, small. The
 * already-decoded snapshot is drawn first so it is on screen at once, and the real file replaces
 * it once it has loaded (the same layering as the review screen itself). A fine light edge keeps
 * it a crisp object on the dark sheet, with no shadow or glow. */
@Composable
private fun PhotoThumbnail(previewBitmap: Bitmap?, photoFile: File?) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .width(104.dp)
            .aspectRatio(0.8f)
            .clip(shape)
            .border(1.dp, SheetPalette.hairline, shape),
    ) {
        previewBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (photoFile != null) {
            AsyncImage(
                model = remember(photoFile) { ImageRequest.Builder(context).data(photoFile).crossfade(false).build() },
                contentDescription = stringResource(R.string.no_friends_sheet_photo),
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}
