package com.emigo.app.ui.camera

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.emigo.app.R
import com.emigo.app.ui.components.emberButtonBrush
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import java.io.File
import java.io.FileOutputStream

/** A bare icon button (gallery, flip): no background circle, sized up so it still reads as a
 * control against the live feed or captured photo, like every other icon on this screen. */
@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        if (badge != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) { badge() }
        }
    }
}

/** Gallery / shutter / flip row shown while the viewfinder is live. */
@Composable
internal fun CaptureControls(
    viewModel: CameraViewModel,
    onPickFromGallery: () -> Unit,
) {
    val colors = EmberTheme.colors
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current

    // A capture (CameraX writing the JPEG) takes tens to hundreds of ms. Without its own feedback the
    // shutter looked untouched that whole time, then everything hard-cut to review at once, which
    // read as "the whole page reloads". Shrinking the shutter on press, not waiting for the
    // capture, is the standard fix: the response is immediate even though the capture isn't.
    val shutterInteractionSource = remember { MutableInteractionSource() }
    val isShutterPressed by shutterInteractionSource.collectIsPressedAsState()
    val shutterScale by animateFloatAsState(
        targetValue = if (isShutterPressed) 0.88f else 1f,
        animationSpec = tween(100),
        label = "shutterPressScale",
    )

    // Clustered tightly around the shutter, like a real camera's control strip, instead of at the
    // screen edges where they read as three unrelated buttons.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.Rounded.Image,
            contentDescription = stringResource(R.string.camera_pick_from_gallery),
            onClick = onPickFromGallery,
        )

        // The cream ring is the stationary anchor; only the gradient fill inside shrinks on press
        // (see shutterScale), like a real shutter. Scaling the ring too would make the whole
        // control shrink instead of press.
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .border(4.dp, colors.cream, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .graphicsLayer { scaleX = shutterScale; scaleY = shutterScale }
                    .clip(CircleShape)
                    .background(emberButtonBrush(EmberTheme.key, colors))
                    .clickable(
                        interactionSource = shutterInteractionSource,
                        // The scale animation above is the press feedback; a ripple would double up
                        // two cues for one tap.
                        indication = null,
                        enabled = !viewModel.isQueuingSend,
                    ) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        capturePhoto(context, viewModel)
                    },
            )
        }

        RoundIconButton(
            icon = Icons.Rounded.Cameraswitch,
            contentDescription = stringResource(R.string.camera_flip),
            onClick = {
                CameraSession.lensFacing = if (CameraSession.lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            },
        )
    }
}

/** A new user's only hint that Camera is a pager page: Memories, Home, Friends and Settings are a
 * swipe away, with nothing else (no tab label, no arrow) saying so. Two chevrons nudge outward and
 * back on a slow loop, subtle enough not to look like an ad. Gone for good after the first real
 * swipe (see CameraViewModel.showSwipeHint); this composable doesn't know why it's visible, that's
 * the caller's AnimatedVisibility. */
@Composable
internal fun SwipeHint(modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "swipeHint")
    val nudge by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "swipeHintNudge",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Icon(
            Icons.Rounded.ChevronLeft,
            contentDescription = null,
            tint = colors.mutedDim,
            modifier = Modifier.size(16.dp).graphicsLayer { translationX = -nudge * 5.dp.toPx() },
        )
        Text(
            text = stringResource(R.string.camera_swipe_hint),
            fontFamily = PublicSansFontFamily,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp,
            color = colors.mutedDim,
        )
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.mutedDim,
            modifier = Modifier.size(16.dp).graphicsLayer { translationX = nudge * 5.dp.toPx() },
        )
    }
}

private fun capturePhoto(context: Context, viewModel: CameraViewModel) {
    // Freeze the live frame and show it immediately, before the real capture completes (hardware and
    // driver latency, see CameraSession.imageCapture); that's what makes this feel instant, as in
    // mainstream camera apps. previewView.bitmap grabs the rendered frame synchronously, and the
    // bitmap itself (not a re-read of the file) is shown in CapturedPreview via a plain
    // Image(bitmap = ...), because a second AsyncImage/Coil round trip still flickered (see
    // onPreviewSnapshotCaptured). It's also written to a temp file so capturedFile/discardCapture's
    // File-based bookkeeping keeps working unchanged.
    CameraSession.previewView?.bitmap?.let { previewBitmap ->
        val snapshotFile = File(context.cacheDir, "ember_capture_preview_${System.currentTimeMillis()}.jpg")
        runCatching {
            FileOutputStream(snapshotFile).use { out -> previewBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        }.onSuccess {
            viewModel.onPreviewSnapshotCaptured(snapshotFile, previewBitmap)
        }
    }

    val file = File(context.cacheDir, "ember_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    // CameraX mirrors the front camera's preview (like a selfie mirror) but saves the capture
    // un-mirrored, so the photo came back flipped versus what you framed. ImageCapture.Metadata's
    // isReversedHorizontal only records an EXIF flag, and this app re-encodes on device when a
    // caption is baked in (and the backend compresses again), either of which drops EXIF and would
    // lose the mirror. So the flip is applied to the pixels, in the ViewModel (see onPhotoCaptured).
    val isFrontCamera = CameraSession.lensFacing == CameraSelector.LENS_FACING_FRONT
    CameraSession.imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                viewModel.onPhotoCaptured(file, isFrontCamera = isFrontCamera)
            }

            override fun onError(exception: ImageCaptureException) {
                viewModel.captureFailed(exception.message ?: context.getString(R.string.camera_capture_failed))
            }
        },
    )
}
