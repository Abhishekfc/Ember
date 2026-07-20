package com.ember.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import java.io.File
import java.io.FileOutputStream

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onClose: () -> Unit,
    onOpenRecipientPicker: () -> Unit,
    onUpgradeToGold: () -> Unit,
    onSent: () -> Unit,
) {
    val colors = EmberTheme.colors
    val context = LocalContext.current
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val cardShape = RoundedCornerShape(30.dp)
    val captured = viewModel.capturedFile

    // Reviewing a shot? Back retakes instead of leaving the camera.
    BackHandler(enabled = captured != null) { viewModel.discardCapture() }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val file = File(context.cacheDir, "ember_pick_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            viewModel.onPhotoCaptured(file)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp, start = 22.dp, end = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(percent = 50))
                        .clickable(onClick = onOpenRecipientPicker)
                        .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (viewModel.hasPinnedSelected) {
                        Icon(Icons.Filled.PushPin, contentDescription = null, tint = colors.glow, modifier = Modifier.size(11.dp))
                    }
                    Text(
                        text = "Sending to ",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 12.sp,
                        color = colors.cream,
                        modifier = Modifier.padding(start = 5.dp),
                    )
                    Text(
                        text = viewModel.recipientLabel,
                        fontFamily = PublicSansFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.cream,
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = colors.cream.copy(alpha = 0.7f),
                        modifier = Modifier.size(15.dp),
                    )
                }

                Text(
                    text = "Close",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 12.sp,
                    color = colors.cream.copy(alpha = 0.85f),
                    modifier = Modifier.clickable(onClick = onClose),
                )
            }

            // Home's card sits below three stacked header lines (wordmark, greeting, date);
            // this screen's header is a single row, so matching just the "18dp after header"
            // padding isn't enough — this extra spacer makes up the difference in header height
            // so the card lands at the same absolute position on screen as Home's does, and
            // feels like the same card carrying through both screens rather than two layouts
            // that happen to share a corner radius.
            Spacer(modifier = Modifier.height(64.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, start = 22.dp, end = 22.dp)
                    .aspectRatio(0.8f)
                    .shadow(20.dp, cardShape, ambientColor = colors.glow.copy(alpha = 0.35f), spotColor = colors.glow.copy(alpha = 0.35f))
                    .clip(cardShape)
                    .background(Color.Black),
            ) {
                if (captured != null) {
                    CapturedPreview(viewModel = viewModel, file = captured)
                } else {
                    LiveCameraStage()
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            ) {
                if (captured != null) {
                    PreviewControls(viewModel = viewModel, onSent = onSent)
                } else {
                    CaptureControls(
                        viewModel = viewModel,
                        onPickFromGallery = {
                            viewModel.onGalleryClick {
                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            }
                        },
                    )
                }

                if (viewModel.errorMessage != null) {
                    Text(
                        text = viewModel.errorMessage.orEmpty(),
                        fontFamily = PublicSansFontFamily,
                        fontSize = 12.sp,
                        color = colors.glow2,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 14.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }
        }

        if (viewModel.isSending) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.glow)
                    Text(
                        text = "Sending…",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.sp,
                        color = Color.White,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        if (viewModel.showGoldUpsell) {
            GoldUpsellOverlay(
                onDismiss = viewModel::dismissGoldUpsell,
                onUpgrade = {
                    viewModel.dismissGoldUpsell()
                    onUpgradeToGold()
                },
            )
        }
    }
}

/** Live viewfinder inside the card. */
@Composable
private fun LiveCameraStage() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PreviewView(ctx) },
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val selector = CameraSelector.Builder().requireLensFacing(CameraSession.lensFacing).build()
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, CameraSession.imageCapture)
                    }
                }, ContextCompat.getMainExecutor(context))
            },
        )
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Ember needs camera access to take photos.",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/** Holds camera state that must survive the capture->preview->retake round trip and be shared
 * between the viewfinder and the shutter button without threading it through every composable.
 * lensFacing is Compose state so the viewfinder rebinds when the flip button changes it. */
private object CameraSession {
    var lensFacing by mutableStateOf(CameraSelector.LENS_FACING_BACK)
    val imageCapture: ImageCapture = ImageCapture.Builder().build()
}

/** A small circular icon button with a translucent backing — used for gallery/flip so they read
 * as clean tappable controls instead of bare floating glyphs. */
@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    badge: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = EmberTheme.colors
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(colors.panel)
                .border(1.dp, colors.border, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = contentDescription, tint = colors.cream, modifier = Modifier.size(20.dp))
        }
        if (badge != null) {
            Box(modifier = Modifier.align(Alignment.TopEnd)) { badge() }
        }
    }
}

/** Gallery / shutter / flip row shown while the viewfinder is live. */
@Composable
private fun CaptureControls(
    viewModel: CameraViewModel,
    onPickFromGallery: () -> Unit,
) {
    val colors = EmberTheme.colors
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 34.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.Filled.Image,
            contentDescription = "Pick from gallery",
            onClick = onPickFromGallery,
            badge = if (!viewModel.isGoldMember) {
                {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(colors.glow)
                            .border(2.dp, Color(0xFF0E0B16), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = "Ember Gold", tint = colors.accentText, modifier = Modifier.size(10.dp))
                    }
                }
            } else null,
        )

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .border(4.dp, colors.cream, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(colors.glow, colors.glow2)))
                    .clickable(enabled = !viewModel.isSending) {
                        capturePhoto(context, viewModel)
                    },
            )
        }

        RoundIconButton(
            icon = Icons.Filled.Cameraswitch,
            contentDescription = "Flip camera",
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

/** The captured shot with a Snapchat-style caption overlay, drawn at the same height fraction
 * the caption gets baked into the sent image. */
@Composable
private fun CapturedPreview(viewModel: CameraViewModel, file: File) {
    val colors = EmberTheme.colors
    var isEditingCaption by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Fraction 0..1 from top -> vertical bias -1..1
    val captionAlignment = BiasAlignment(0f, CAPTION_Y_FRACTION * 2f - 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = file,
            contentDescription = "Captured photo",
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
                Icons.Filled.TextFields,
                contentDescription = "Add text",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { isEditingCaption = true }
                    .padding(7.dp),
            )
        }
    }
}

/** Retake / send row shown while reviewing a captured shot. */
@Composable
private fun PreviewControls(viewModel: CameraViewModel, onSent: () -> Unit) {
    val colors = EmberTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.clickable(enabled = !viewModel.isSending, onClick = viewModel::discardCapture),
        ) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Retake",
                tint = colors.cream,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colors.panel)
                    .border(1.dp, colors.border, CircleShape)
                    .padding(11.dp),
            )
            Text(
                text = "Retake",
                fontFamily = PublicSansFontFamily,
                fontSize = 11.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Row(
            modifier = Modifier
                .padding(start = 34.dp)
                .width(190.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(colors.glow, colors.glow2)))
                .clickable(enabled = !viewModel.isSending) { viewModel.sendCaptured(onSent) }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Send",
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.accentText,
            )
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = null,
                tint = colors.accentText,
                modifier = Modifier.padding(start = 8.dp).size(15.dp),
            )
        }
    }
}

/** Compact paywall shown when a free account taps the gallery button. */
@Composable
private fun GoldUpsellOverlay(onDismiss: () -> Unit, onUpgrade: () -> Unit) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val panelShape = RoundedCornerShape(24.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(horizontal = 36.dp)
                .clickable(enabled = false) {} // absorb taps so they don't fall through to dismiss
                .background(colors.panel, panelShape)
                .border(1.dp, colors.border, panelShape)
                .padding(horizontal = 26.dp, vertical = 28.dp),
        ) {
            val badgeSizePx = Size(56f, 56f)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), badgeSizePx), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(26.dp))
            }
            Text(
                text = "Ember Gold",
                fontFamily = typography.display,
                fontSize = 19.sp,
                color = colors.cream,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Sending photos from your gallery is an Ember Gold perk",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )

            val buttonSizePx = Size(240f, 48f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx), RoundedCornerShape(14.dp))
                    .clickable(onClick = onUpgrade)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Get Ember Gold",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentText,
                )
            }
            Text(
                text = "Maybe later",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                color = colors.mutedDim,
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clickable(onClick = onDismiss),
            )
        }
    }
}

private fun capturePhoto(context: Context, viewModel: CameraViewModel) {
    val file = File(context.cacheDir, "ember_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
    CameraSession.imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                viewModel.onPhotoCaptured(file)
            }

            override fun onError(exception: ImageCaptureException) {
                viewModel.captureFailed(exception.message ?: "Couldn't capture photo")
            }
        },
    )
}
