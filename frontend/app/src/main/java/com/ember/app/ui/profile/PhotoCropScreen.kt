package com.ember.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.ui.theme.EmberTheme
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Pinch-to-zoom, drag-to-reposition profile photo cropper — always outputs a square (1:1) image,
 * matching how every other profile-picture picker (WhatsApp, Instagram, Telegram) works: a
 * circular guide on screen, a square file underneath (avatars are already shown circularly
 * everywhere in this app via clipping, so the file itself only ever needs to be square).
 *
 * Built natively in Compose rather than pulling in a third-party cropping library — this app has
 * no AppCompat theme anywhere (`Theme.Ember` descends from the plain platform Material theme),
 * and every cropping library with real traction assumes an `AppCompatActivity` host, so reaching
 * for one would mean adding a whole second theme lineage just for this one screen. A few hundred
 * lines of gesture math is less risk than that, and it stays visually consistent with the rest
 * of the app. */
@Composable
fun PhotoCropScreen(
    imageUri: Uri,
    onCancel: () -> Unit,
    onCropped: (File) -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var sourceBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(imageUri) {
        sourceBitmap = withContext(Dispatchers.IO) { decodeOrientedBitmap(context, imageUri) }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // A fixed size derived from screen width, not BoxWithConstraints — needed in both the
    // gesture handler above and the confirm button below, which don't share a common
    // constraints-scoped ancestor.
    val cropSizeDp = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp.dp - 64.dp).coerceAtMost(360.dp)
    }
    val cropSizePx = with(density) { cropSizeDp.toPx() }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val bitmap = sourceBitmap
        if (bitmap == null) {
            CircularProgressIndicator(color = colors.glow, modifier = Modifier.align(Alignment.Center))
        } else {
            // The scale that makes the image's *shorter* side exactly fill the crop window — the
            // same "fill, crop the rest" behavior ContentScale.Crop gives everywhere else in this
            // app. User pinch-zoom (scale) only ever multiplies on top of this, never below it, so
            // the crop window can never show empty space around the image.
            val baseScale = remember(bitmap, cropSizePx) { cropSizePx / min(bitmap.width, bitmap.height).toFloat() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(bitmap, cropSizePx) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            val newTotalScale = baseScale * newScale
                            val displayedW = bitmap.width * newTotalScale
                            val displayedH = bitmap.height * newTotalScale
                            val maxX = ((displayedW - cropSizePx) / 2f).coerceAtLeast(0f)
                            val maxY = ((displayedH - cropSizePx) / 2f).coerceAtLeast(0f)
                            val candidate = offset + pan
                            scale = newScale
                            offset = Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(
                            with(density) { (bitmap.width * baseScale).toDp() },
                            with(density) { (bitmap.height * baseScale).toDp() },
                        )
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
            }

            // Dimmed scrim with a clear circular window punched out of it — a visual guide only.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawBehind {
                        drawRect(Color.Black.copy(alpha = 0.55f))
                        drawCircle(color = Color.Transparent, radius = cropSizePx / 2f, center = center, blendMode = BlendMode.Clear)
                    },
            )
            Canvas(modifier = Modifier.align(Alignment.Center).size(cropSizeDp)) {
                drawCircle(color = colors.cream.copy(alpha = 0.5f), radius = size.minDimension / 2f, style = Stroke(width = 1.5.dp.toPx()))
            }
        }

        Text(
            text = "Move and pinch to adjust",
            fontFamily = typography.body,
            fontSize = 13.sp,
            color = colors.muted,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 56.dp),
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(24.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.panel.copy(alpha = 0.85f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Cancel", tint = colors.cream, modifier = Modifier.size(18.dp))
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 36.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            val confirmEnabled = sourceBitmap != null
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(if (confirmEnabled) colors.glow else colors.mutedDim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = confirmEnabled,
                        onClick = {
                            val bmp = sourceBitmap ?: return@clickable
                            val baseScale = cropSizePx / min(bmp.width, bmp.height).toFloat()
                            val cropped = cropToSquareBitmap(bmp, baseScale, scale, offset, cropSizePx)
                            onCropped(saveCroppedJpeg(context, cropped))
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = "Use photo", tint = colors.accentText, modifier = Modifier.size(26.dp))
            }
        }
    }
}

/** Maps the on-screen crop window (fixed square, [cropSizePx] wide, centered) back to pixel
 * coordinates in the original, unscaled [source] bitmap, given the current pinch [userScale] and
 * drag [offset] — then crops exactly that square out. */
private fun cropToSquareBitmap(source: Bitmap, baseScale: Float, userScale: Float, offset: Offset, cropSizePx: Float): Bitmap {
    val totalScale = baseScale * userScale
    val cropCenterXSrc = source.width / 2f - offset.x / totalScale
    val cropCenterYSrc = source.height / 2f - offset.y / totalScale
    val cropSizeSrc = cropSizePx / totalScale
    val halfSizeSrc = cropSizeSrc / 2f

    val left = (cropCenterXSrc - halfSizeSrc).roundToInt().coerceIn(0, source.width - 1)
    val top = (cropCenterYSrc - halfSizeSrc).roundToInt().coerceIn(0, source.height - 1)
    val size = cropSizeSrc.roundToInt()
        .coerceAtMost(min(source.width - left, source.height - top))
        .coerceAtLeast(1)

    return Bitmap.createBitmap(source, left, top, size, size)
}

/** Decodes [uri] downsampled to a sane max dimension (avoids OOM on a full-resolution camera
 * photo we're about to crop down to a small square anyway) and applies its EXIF rotation — the
 * gallery picker hands back the file's raw pixel data, which for most real photos is stored
 * sideways/upside-down relative to how it displays, with the correction living in EXIF metadata
 * rather than the pixels themselves. */
private fun decodeOrientedBitmap(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    // Bounds-only decode always returns null by design (the point is reading bounds.outWidth/
    // outHeight afterward, not the return value) — checking that return value for null here
    // would treat every successful read as a failure and bail out before ever reaching the real
    // decode below. Only the stream itself failing to open is a real failure.
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(uri) ?: return null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }

    val maxDimension = 2048
    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension || bounds.outHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }

    val decoded = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    } ?: return null

    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL

    val rotationDegrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotationDegrees == 0f) return decoded
    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
}

/** A square crop at zoom=1 can still come out close to [decodeOrientedBitmap]'s own 2048px cap —
 * far bigger than a profile photo is ever displayed at anywhere in this app. Downscaled to a
 * normal profile-photo size and re-compressed here, the same way [com.ember.app.ui.camera.
 * CameraViewModel] already compresses every sent photo before upload (quality 90 there; matched
 * here for one consistent choice instead of two different numbers with no reason to differ). */
private const val PROFILE_PHOTO_MAX_DIMENSION = 640

private fun saveCroppedJpeg(context: Context, bitmap: Bitmap): File {
    val output = if (bitmap.width > PROFILE_PHOTO_MAX_DIMENSION) {
        Bitmap.createScaledBitmap(bitmap, PROFILE_PHOTO_MAX_DIMENSION, PROFILE_PHOTO_MAX_DIMENSION, true)
    } else {
        bitmap
    }
    val file = File(context.cacheDir, "ember_profile_photo_${System.currentTimeMillis()}.jpg")
    FileOutputStream(file).use { out -> output.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    return file
}
