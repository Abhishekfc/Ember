package com.emigo.app.ui.camera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.emigo.app.R
import com.emigo.app.ui.components.emberButtonBrush
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import java.io.File

/** The captured shot with a Snapchat-style caption overlay, drawn at the same height fraction
 * the caption gets baked into the sent image. */
@Composable
internal fun CapturedPreview(viewModel: CameraViewModel, file: File) {
    val colors = EmberTheme.colors
    val context = LocalContext.current
    var isEditingCaption by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Fraction 0..1 from top -> vertical bias -1..1
    val captionAlignment = BiasAlignment(0f, CAPTION_Y_FRACTION * 2f - 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        // The instant preview-snapshot bitmap (see CameraViewModel.previewBitmap), drawn directly: an
        // already-decoded Bitmap needs no async load, so it's on screen the same frame this
        // composable appears. It sits under the AsyncImage below as a fallback while that decodes the
        // real file; without it the Box's black background showed on both the live-to-snapshot and
        // snapshot-to-real transitions, since AsyncImage/Coil always decodes asynchronously, even for
        // a local file.
        viewModel.previewBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AsyncImage(
            // The app-wide ImageLoader (see EmberApplication.newImageLoader) enables crossfade for
            // Home's feed; here it made the just-captured photo fade in from the Box's black
            // background instead of just appearing (reported twice). crossfade(false) overrides that
            // for this image only. Still needed alongside the bitmap layer because it decodes the
            // real file with the correct EXIF orientation.
            model = remember(file) { ImageRequest.Builder(context).data(file).crossfade(false).build() },
            contentDescription = stringResource(R.string.camera_captured_photo),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (isEditingCaption) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            BasicTextField(
                value = viewModel.captionText,
                onValueChange = viewModel::onCaptionChange,
                textStyle = TextStyle(
                    fontFamily = PublicSansFontFamily,
                    fontSize = 15.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                cursorBrush = SolidColor(colors.glow),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { isEditingCaption = false }),
                modifier = Modifier
                    .align(captionAlignment)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .focusRequester(focusRequester),
            )
        } else if (viewModel.captionText.isNotBlank()) {
            Text(
                text = viewModel.captionText,
                fontFamily = PublicSansFontFamily,
                fontSize = 15.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(captionAlignment)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { isEditingCaption = true }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }

        // "Aa" toggle pinned inside the card's top-right, like Snapchat's text tool.
        if (!isEditingCaption) {
            Icon(
                Icons.Rounded.TextFields,
                contentDescription = stringResource(R.string.camera_add_text),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .clip(CircleShape)
                    .clickable { isEditingCaption = true }
                    .size(28.dp),
            )
        }
    }
}

/** Retake / send row shown while reviewing a capture. Recipients appear once, in the header chip
 * (live and preview): a second "sending to" row here read as two recipient pickers. Send stays
 * disabled if nothing is selected, so the chip is still the one place that matters.
 *
 * Send reuses the shutter's exact circle (84dp ring, 70dp gradient fill, same position) instead of
 * a differently shaped pill: the button you pressed to take the photo is the one that now sends
 * it, with a different icon. The gallery and flip slots are gone, but a same-size spacer holds
 * their place so the center circle doesn't shift when CaptureControls crossfades into this. */
@Composable
internal fun PreviewControls(
    viewModel: CameraViewModel,
    onSent: () -> Unit,
) {
    val colors = EmberTheme.colors
    val context = LocalContext.current
    val hasRecipients = viewModel.selectedFriends.isNotEmpty()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Balances the row like the gallery button's slot in CaptureControls, so the send circle sits
        // at the same x-position in both states and doesn't shift during the crossfade.
        Spacer(modifier = Modifier.size(46.dp))

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .border(4.dp, colors.cream, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Color and icon reflect only whether there's someone to send to, not whether the real
            // file has landed (viewModel.isRealCaptureReady). That guard still blocks the tap (here
            // and again inside sendCaptured()); it just isn't something the button flashes through.
            // Gating the color on it briefly painted the muted "disabled" look after every capture,
            // then snapped to the theme gradient once the real file saved: a jarring flash.
            val canSend = hasRecipients
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(
                        if (canSend) emberButtonBrush(EmberTheme.key, colors) else Brush.linearGradient(listOf(colors.border, colors.border)),
                    )
                    .clickable(enabled = !viewModel.isQueuingSend && canSend) {
                        viewModel.sendCaptured(context.applicationContext, onSent)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.camera_send),
                    tint = if (canSend) colors.accentText else colors.mutedDim,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        // Fixed to 46dp, the width of the gallery/flip RoundIconButtons in CaptureControls (and the
        // balancing Spacer above). Retake's content is narrower, and letting the Column wrap content
        // made this Row's total width differ from CaptureControls', shifting the centered send circle
        // away from where the shutter was.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(46.dp)
                .clickable(enabled = !viewModel.isQueuingSend, onClick = viewModel::discardCapture),
        ) {
            Icon(
                Icons.Rounded.Replay,
                contentDescription = stringResource(R.string.camera_retake),
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = stringResource(R.string.camera_retake),
                fontFamily = PublicSansFontFamily,
                fontSize = 11.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
