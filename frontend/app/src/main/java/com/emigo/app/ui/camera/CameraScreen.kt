package com.emigo.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.emigo.app.R
import com.emigo.app.data.remote.dto.FriendSummaryDto
import com.emigo.app.ui.components.LocalNavDockHeight
import com.emigo.app.ui.components.emberButtonBrush
import com.emigo.app.ui.home.FEATURED_CARD_ASPECT_RATIO
import com.emigo.app.ui.home.featuredCardSidePadding
import com.emigo.app.ui.home.FEATURED_CARD_TOP_GAP
import com.emigo.app.ui.home.homeFoldMetricsFor
import com.emigo.app.ui.home.AVATAR_ROW_TOP_GAP
import com.emigo.app.ui.home.HomeHeaderHeightTwin
import com.emigo.app.ui.home.HomeViewModeToggleHeightTwin
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onOpenRecipientPicker: () -> Unit,
    onUpgradeToGold: () -> Unit,
    onOpenSentPhotos: () -> Unit,
    onSent: () -> Unit,
) {
    val colors = EmberTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    var screenSize by remember { mutableStateOf(Size.Zero) }
    // This screen's own header row real height — measured the same way Home measures its own,
    // so the fold below can be bounded by the real remaining space rather than a guess.
    var headerHeightPx by remember { mutableStateOf(0f) }
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
            // No .navigationBarsPadding() here — LocalNavDockHeight below already includes the
            // real system nav-bar inset (see HomeScreen's own identical comment on its dock
            // reserve), so adding this too would double-count it and throw off topFoldMaxHeightDp.
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // An overlay Box, not a sequential Column item — sizes itself to its tallest child.
            // This screen's own header row below is deliberately tuned (its own top padding, see
            // its own comment) to measure to the exact same real height as the invisible twin —
            // matching Home's real, unmodified header height, rather than the other way around
            // (Home's own header staying near the status bar matters more than this row's own
            // top padding, which no one but this screen ever sees).
            Box(modifier = Modifier.onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }) {
                // Invisible structural twin of Home's own header block — see its own doc comment
                // in HomeScreen.kt. Its natural height (unaffected by alpha, which is a draw-time
                // property, not a layout one) is what reserves the identical vertical space Home's
                // real header occupies, computed entirely locally rather than read back from
                // Home's own live render — the previous approach, and the actual source of the
                // card jumping: Camera is the app's own opening page, so the very first frame ever
                // read that value back as zero, then jumped once Home was finally visited for the
                // first time and reported its real height.
                Box(modifier = Modifier.alpha(0f)) { HomeHeaderHeightTwin() }

                Box(
                    modifier = Modifier
                        // matchParentSize(), not fillMaxWidth() plus a hand-tuned top padding:
                        // this measures against the invisible twin's already-decided size instead
                        // of contributing to it, so this row physically cannot make the header
                        // taller than Home's however its own contents change. The previous version
                        // added 6dp on top of a 54dp chip for a 60dp total, tuned to a Home header
                        // that was 60dp at the time — Home's is 44dp since its header icons were
                        // resized, and nothing tied the two numbers together, so Camera's whole
                        // card silently sat ~16dp lower than Home's from that point on. The twin
                        // is now the single source of this height, the same way it already is for
                        // the space above it.
                        .matchParentSize()
                        .padding(start = 22.dp, end = 22.dp),
                ) {
                    // Real faces, not a text label — who this is going to should be something you
                    // can recognize at a glance, not something you have to read and parse. This is
                    // the *only* recipient control on screen (live and post-capture both). Centered
                    // (a Box, not a SpaceBetween Row pinning it to the left edge) and deliberately
                    // compact rather than stretched wide, leaving both corners genuinely free for
                    // whatever else eventually lands there — the send-status pill on the right today,
                    // more later.
                    //
                    // No close button any more — Camera is a page of the main pager (swipe to Home
                    // like any other tab), not a modal screen that needs its own explicit exit, and
                    // discarding an abandoned capture on the way out is now handled automatically
                    // (see MainActivity's own settledPage effect) rather than needing this button to
                    // be tapped for it to happen.
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(percent = 50))
                            .clickable(onClick = onOpenRecipientPicker)
                            // 5dp vertical, not 10 — with the 34dp avatar stack this makes the
                            // pill 44dp, which fits inside the header height the twin above
                            // defines. At 10dp it was 54dp and overflowed that slot now that
                            // matchParentSize() no longer lets it stretch the row to fit itself.
                            .padding(start = 12.dp, end = 20.dp, top = 5.dp, bottom = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RecipientAvatarStack(friends = viewModel.selectedFriends, size = 34.dp, ringColor = colors.panel)
                        if (viewModel.hasPinnedSelected) {
                            Icon(
                                Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = colors.glow,
                                modifier = Modifier.padding(start = 9.dp).size(14.dp),
                            )
                        }
                        Text(
                            text = viewModel.recipientLabel,
                            fontFamily = PublicSansFontFamily,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(start = 9.dp),
                        )
                        Icon(
                            Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 3.dp).size(18.dp),
                        )
                    }

                    // Always present (unlike the bookmark button, which only appears once reviewing
                    // a capture) — this opens a screen of *past* sends, independent of whatever's
                    // currently being framed or reviewed.
                    OutboxButton(
                        sendAnimState = viewModel.sendAnimState,
                        lastSentPhotoUrl = viewModel.lastSentPhotoUrl,
                        onClick = onOpenSentPhotos,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )

                    if (captured != null) {
                        SaveToMemoriesButton(
                            viewModel = viewModel,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }

            // Bounds the card+controls pair to the real remaining space below the header —
            // screenSize, minus the real measured status bar inset, minus this screen's own real
            // measured header height, minus the dock's real reserve (LocalNavDockHeight, the same
            // value HomeScreen's own topFoldMaxHeightDp subtracts) — rather than the entire rest
            // of this Column, which is what actually caused the card to land far lower than
            // Home's own: without the dock subtraction, this "remaining space" ran all the way to
            // the true bottom of the screen (the dock is invisible on this page, but still real
            // estate HomeScreen's own version of this same math excludes). Gated on both real
            // measurements being in yet, same as HomeScreen, to avoid a wrong-then-corrected flash.
            if (screenSize != Size.Zero && headerHeightPx > 0f) {
                val statusBarPx = WindowInsets.statusBars.getTop(density)
                val navDockHeightPx = with(density) { LocalNavDockHeight.current.toPx() }
                val topFoldMaxHeightDp = with(density) {
                    (screenSize.height - statusBarPx - headerHeightPx - navDockHeightPx).toDp()
                }
                // The same scale Home picks for its own fold. Its inputs are identical by
                // construction — homeFoldMetricsFor deliberately excludes the toggle row from the
                // available height, which is exactly what this screen's topFoldMaxHeightDp already
                // is — so both screens always resolve to the same metrics and their cards stay the
                // same size in the same place.
                val foldMetrics = homeFoldMetricsFor(
                    availableHeight = topFoldMaxHeightDp,
                    screenWidth = with(density) { screenSize.width.toDp() },
                )
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = topFoldMaxHeightDp)) {
                    // Home has a HomeViewModeToggleRow between its header and its card; this
                    // screen has nothing there. That one row was the entire reason Camera's card
                    // sat higher than Home's even though both already reserved a matching header
                    // height — so reserve it here too, the same invisible-twin way the header
                    // itself is handled above rather than by copying a dp number that would
                    // silently drift.
                    Box(modifier = Modifier.alpha(0f)) { HomeViewModeToggleHeightTwin(foldMetrics) }

                    // Fixed gap, then the card — mirroring HomeScreen's own fold exactly. Both
                    // screens previously split their leftover space with weight(1f) spacers, which
                    // made every gap device-dependent (large on a tall phone, collapsed on a short
                    // one) and put the two screens' cards at different heights whenever their
                    // leftover differed. A constant on both sides is what actually pins the card to
                    // the same absolute position on each.
                    Spacer(modifier = Modifier.height(foldMetrics.cardTopGap))

                    Box(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .align(Alignment.CenterHorizontally)
                            .padding(start = featuredCardSidePadding(), end = featuredCardSidePadding())
                            .aspectRatio(FEATURED_CARD_ASPECT_RATIO)
                            .clip(cardShape)
                            .background(Color.Black),
                    ) {
                        // Plain, instant swap — no fade. A Crossfade here was tried (twice: first
                        // keyed on a boolean that re-read viewModel.capturedFile live inside the
                        // lambda, which crashed the app on retake — both the outgoing and incoming
                        // slots saw the same already-null value and both tried to mount
                        // LiveCameraStage at once, and two AndroidViews can't share CameraSession's
                        // one singleton PreviewView; then keyed on the File? itself instead, which
                        // fixed the crash but meant an instant-preview snapshot silently getting
                        // replaced by the real photo triggered a second fade through this Box's
                        // black background, reading as a flicker) and explicitly rejected both
                        // times — no animation, no intermediate frame, just the real photo the
                        // moment it's ready.
                        if (captured != null) {
                            CapturedPreview(viewModel = viewModel, file = captured)
                        } else {
                            LiveCameraStage()
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // Same gap Home puts between its card and the avatar row directly beneath
                        // it, so the element following the card starts at a matching offset on
                        // both screens rather than each picking its own number.
                        modifier = Modifier.fillMaxWidth().padding(top = AVATAR_ROW_TOP_GAP),
                    ) {
                        Crossfade(targetState = captured != null, animationSpec = tween(220), label = "cameraControlsStage") { isReviewing ->
                            if (isReviewing) {
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
                        }
                    }
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

/** Shares the header row with the recipient chip, in the exact spot the old send status pill
 * used to sit — saving is now this screen's own independent action (see
 * CameraViewModel.saveToMemories), not something reported after the fact, so this reads as a
 * real control (bookmark, tap to save) rather than a passive status readout. Outline while
 * unsaved, filled with a brief spinner overlay while the save is queuing, filled and solid once
 * saved — a plain glyph with no background circle, same language every other bare icon button on
 * this screen already uses. */
@Composable
private fun SaveToMemoriesButton(viewModel: CameraViewModel, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val context = LocalContext.current
    Box(
        modifier = modifier
            .size(40.dp)
            .clickable(
                enabled = !viewModel.isSaved && !viewModel.isSavingToMemories,
                onClick = { viewModel.saveToMemories(context.applicationContext) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (viewModel.isSaved) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark_outline),
            contentDescription = if (viewModel.isSaved) "Saved to Memories" else "Save to Memories",
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
        if (viewModel.isSavingToMemories) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}

/** Camera's own outbox — opens a screen of this account's recent sends, mainly so one of them can
 * be unsent (see SentPhotosScreen). Shows [lastSentPhotoUrl] (the single most recent unsaved
 * send, refreshed by [CameraViewModel.refreshLastSentPhoto]) cropped into the same proportions
 * every photo card in the app uses ([FEATURED_CARD_ASPECT_RATIO]) — a real thumbnail, not a
 * generic glyph, so this reads as "here's what you just sent" rather than an abstract control.
 * Falls back to a small bold empty outline (no photo sent recently, or the fetch hasn't landed
 * yet) — a thicker border than a typical hairline (2.2dp) is what keeps that fallback state from
 * visually disappearing at this small a size.
 *
 * Its animation mirrors [CameraViewModel.sendAnimState]: idle thumbnail -> a short bright segment
 * tracing around the shape's own border, corners included, while the real (background, possibly
 * slow-if-offline) upload is in flight -> filled solid with a checkmark the instant it lands ->
 * back to the (now updated) thumbnail a moment later. The traveling segment is a [PathMeasure]
 * walk along the *exact same* rounded-rect outline the border draws, parameterized by distance
 * along the path rather than angle — a rotating [Brush.sweepGradient] was tried first and looked
 * like a separate, distorted shape spinning behind the icon (angle-vs-arc-length isn't constant on
 * a non-square rounded rect, especially through the corners); walking the real path is what
 * actually reads as one continuous line tracing this shape's own edge. */
@Composable
private fun OutboxButton(sendAnimState: SendAnimState, lastSentPhotoUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val density = LocalDensity.current
    val shape = RoundedCornerShape(4.dp)
    val cardWidth = 20.dp
    val cardHeight = cardWidth / FEATURED_CARD_ASPECT_RATIO
    val borderWidth = 2.2.dp

    // Built once (not re-derived every animation frame) from this shape's own fixed size — the
    // traveling segment below walks this exact path, so it can never drift from the border drawn
    // right behind it (whichever of the two border treatments below is currently showing).
    val cardOutline = remember(cardWidth, cardHeight, density) {
        with(density) {
            Path().apply { addRoundRect(RoundRect(0f, 0f, cardWidth.toPx(), cardHeight.toPx(), CornerRadius(4.dp.toPx()))) }
        }
    }
    val pathMeasure = remember(cardOutline) { PathMeasure().apply { setPath(cardOutline, true) } }
    val segmentPath = remember { Path() }

    val infiniteTransition = rememberInfiniteTransition(label = "outboxSending")
    val travelProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing)),
        label = "outboxTravelProgress",
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (sendAnimState == SendAnimState.COMPLETE) 1f else 0f,
        animationSpec = tween(220),
        label = "outboxFillAlpha",
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .semantics { contentDescription = "Sent photos" }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(width = cardWidth, height = cardHeight)) {
            if (lastSentPhotoUrl != null) {
                AsyncImage(
                    model = lastSentPhotoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .border(borderWidth, Color.White.copy(alpha = if (sendAnimState == SendAnimState.SENDING) 0.35f else 0.9f), shape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(borderWidth, Color.White.copy(alpha = if (sendAnimState == SendAnimState.SENDING) 0.35f else 0.9f), shape),
                )
            }

            if (sendAnimState == SendAnimState.SENDING) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val length = pathMeasure.length
                    val segmentLength = length * 0.24f
                    val start = travelProgress * length
                    val end = start + segmentLength
                    segmentPath.reset()
                    if (end <= length) {
                        pathMeasure.getSegment(start, end, segmentPath, true)
                    } else {
                        // Wraps back to the path's own start — two pieces stitched into one draw
                        // so the line never visibly breaks as it crosses the seam.
                        pathMeasure.getSegment(start, length, segmentPath, true)
                        pathMeasure.getSegment(0f, end - length, segmentPath, true)
                    }
                    drawPath(path = segmentPath, color = colors.glow, style = Stroke(width = borderWidth.toPx(), cap = StrokeCap.Round))
                }
            }

            if (fillAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = fillAlpha }
                        .background(colors.glow, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(14.dp))
                }
            }
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

    // Re-read the real permission state every time this screen comes back to the foreground.
    // The snapshot above is taken once, when this composable first runs — and Camera is the app's
    // opening page, so it composes *before* the startup permission dialog (see MainActivity) has
    // been answered. Granting there updates nothing here: that dialog belongs to a different
    // launcher, so this screen's own callback never fires and its cached `false` stands until the
    // process restarts, which is exactly the "blank camera until you reopen the app" symptom.
    // Keying on the lifecycle instead of on any one launcher covers every route a grant can
    // arrive by — the startup dialog, this screen's own prompt, or the user flipping it on in
    // system settings and coming back.
    val lifecycle = lifecycleOwner.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA,
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    if (hasCameraPermission) {
        // The PreviewView and its binding live in CameraSession, not in a remember{} scoped to
        // this composition — see that object's own doc comment for why (Camera is a pager page,
        // disposed and recomposed on every scroll away and back, not a screen only ever created
        // once). bindIfNeeded is still keyed on lensFacing here so a flip is picked up the
        // moment it changes, but it no-ops immediately if this lens is already bound, rather
        // than unconditionally re-fetching the provider and rebinding on every re-entry.
        val previewView = remember { CameraSession.previewViewFor(context) }
        LaunchedEffect(CameraSession.lensFacing) {
            CameraSession.bindIfNeeded(context, lifecycleOwner)
        }

        // Transient "1.8x" readout — shown the instant a pinch changes the zoom, hidden again
        // shortly after the fingers stop moving, the same fade-after-a-beat behavior every real
        // camera app's zoom readout has, rather than a permanent on-screen number nobody needs
        // to see outside the moment they're actively pinching.
        var displayedZoomRatio by remember { mutableStateOf<Float?>(null) }
        var hideZoomIndicatorJob by remember { mutableStateOf<Job?>(null) }
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    // Only ever consumes a genuine two-finger pinch — the old
                    // detectTransformGestures reported (and consumed) pan even from a single
                    // pointer, which swallowed every swipe meant for the outer tab pager the
                    // instant it landed on the live camera. Waiting for a second pointer before
                    // consuming anything means a one-finger swipe now falls through untouched to
                    // the pager underneath, same as it already does on every other tab.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2) {
                                    val gestureZoom = event.calculateZoom()
                                    val camera = CameraSession.camera
                                    val zoomState = camera?.cameraInfo?.zoomState?.value
                                    if (camera != null && zoomState != null) {
                                        val newRatio = (zoomState.zoomRatio * gestureZoom)
                                            .coerceIn(zoomState.minZoomRatio, zoomState.maxZoomRatio)
                                        camera.cameraControl.setZoomRatio(newRatio)
                                        displayedZoomRatio = newRatio
                                        hideZoomIndicatorJob?.cancel()
                                        hideZoomIndicatorJob = coroutineScope.launch {
                                            delay(900)
                                            displayedZoomRatio = null
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    },
                factory = { previewView },
            )

            displayedZoomRatio?.let { ratio ->
                Text(
                    text = "${"%.1f".format(ratio)}×",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }

            // Front cameras don't carry a usable flash on this device (hasFlashUnit() alone
            // wasn't reliable here). Gated on boundLensFacing, not lensFacing — lensFacing flips
            // the instant the flip button is tapped, well before the rebind actually finishes,
            // which made the icon vanish/appear a beat ahead of the preview itself switching
            // cameras. boundLensFacing only updates once the new camera is actually bound, the
            // same moment the preview swaps, so the two now change together.
            if (CameraSession.boundLensFacing == CameraSelector.LENS_FACING_BACK) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable(onClick = { CameraSession.toggleTorch() }),
                    contentAlignment = Alignment.Center,
                ) {
                    // Standard bolt glyph every camera app uses for flash, not a literal
                    // flashlight shape.
                    Icon(
                        imageVector = if (CameraSession.torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        contentDescription = if (CameraSession.torchEnabled) "Turn flash off" else "Turn flash on",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Emigo needs camera access to take photos.",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/** Holds camera state that must survive the capture->preview->retake round trip and be shared
 * between the viewfinder and the shutter button without threading it through every composable.
 * lensFacing is Compose state so the viewfinder rebinds when the flip button changes it.
 *
 * Also holds the actual [PreviewView] and tracks which lens it's currently bound for — Camera is
 * one page of a swipeable pager, so this screen is disposed and freshly recomposed every time
 * it's scrolled away from and back, not just the first time it's ever opened. Without keeping
 * the view + binding here instead of scoped to that composition, the full
 * ProcessCameraProvider fetch + bindToLifecycle sequence — the actual source of the "black
 * screen for a few seconds" delay — reran on every single re-entry. Reusing the same view and
 * only rebinding when the lens actually changes means re-entering just reattaches an
 * already-live preview. */
private object CameraSession {
    var lensFacing by mutableStateOf(CameraSelector.LENS_FACING_BACK)

    // Resolution/JPEG-quality bounding was tried here (to speed up encoding) and explicitly
    // rejected — the user wants full, unreduced capture quality, full stop, even though that
    // means CameraX picks its own (often high) default resolution/quality. Left in place:
    // CAPTURE_MODE_MINIMIZE_LATENCY (already CameraX's own default, stated explicitly so a future
    // CameraX version changing that default can't silently slow this back down) and flash forced
    // off — neither of those changes what the photo actually looks like, only how it's captured.
    // Sensor readout, JPEG encode of whatever resolution the camera naturally produces, and any
    // autofocus/auto-exposure convergence CameraX's pipeline still runs internally are camera
    // hardware/driver latency from here on — not something app-level code can remove, and it
    // varies by device.
    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .build()

    var previewView: PreviewView? = null
        private set
    private var boundForLensFacing: Int? = null

    // The bound Camera handle — bindToLifecycle's return value — is where CameraX actually
    // exposes zoom control (cameraControl.setZoomRatio) and the current zoom bounds/ratio
    // (cameraInfo.zoomState). Nulled out on every rebind below and re-set once the new bind
    // completes, so a pinch gesture that lands mid-rebind (e.g. right after flipping lenses)
    // sees a consistent camera rather than a stale one from the lens that's being replaced.
    var camera: Camera? = null
        private set

    // Compose state mirroring which lens is actually *visible* right now, distinct from
    // [lensFacing] above (flips the instant the flip button is tapped) and even from
    // bindToLifecycle's own return (that call registers the new camera's pipeline, but the
    // sensor still takes a real beat afterwards to warm up and deliver its first frame — setting
    // this at bindToLifecycle-return time, tried first, still changed visibly before the
    // viewfinder itself did). Set instead from previewStreamState (below) transitioning to
    // STREAMING, which is CameraX's own signal that the new camera's frames have actually started
    // painting the surface — the flash button gated on this changes in step with what's on
    // screen instead of anticipating it.
    var boundLensFacing by mutableStateOf<Int?>(null)
        private set

    // Which lens the most recent bind was *for* — previewStreamState's observer (attached once,
    // below) reads this when a STREAMING transition lands, since the LiveData callback itself
    // carries no information about which lens just finished starting up.
    private var pendingLensFacing: Int? = null
    private var streamStateObserverAttached = false

    // Torch is its own on/off state, not read back from CameraX anywhere — enableTorch is
    // fire-and-forget, so this is the only source of truth for what the button should show.
    // Reset to off on every rebind (lens flip or a fresh bind) rather than carried over, since a
    // just-bound camera always starts with its torch off and the previous camera's torch (if any)
    // was already released along with it.
    var torchEnabled by mutableStateOf(false)
        private set

    fun toggleTorch() {
        val cam = camera ?: return
        val next = !torchEnabled
        cam.cameraControl.enableTorch(next)
        torchEnabled = next
    }

    // The live Preview use case, kept so its surface provider can be reattached without a full
    // rebind — see bindIfNeeded's early-return path for why that's the difference between a
    // working viewfinder and a black one.
    private var preview: Preview? = null

    // CameraX auto-unbinds bindToLifecycle's registration the moment the bound LifecycleOwner
    // reaches DESTROYED — which happens to the Activity on any config change the manifest
    // doesn't declare (system dark/light toggle, font-scale, foldable resize; only
    // screenOrientation is declared). Tracking lens alone meant that, after such a recreation,
    // this object's state still said "already bound" for a LifecycleOwner CameraX had already
    // silently dropped — leaving a permanently black viewfinder until the user happened to flip
    // the lens (forcing a rebind) or restart the app. A weak reference (not a strong one) so
    // this object never itself keeps a destroyed Activity alive.
    private var boundLifecycleOwner: WeakReference<LifecycleOwner>? = null

    fun previewViewFor(context: Context): PreviewView =
        previewView ?: PreviewView(context.applicationContext).also { previewView = it }

    fun bindIfNeeded(context: Context, lifecycleOwner: LifecycleOwner) {
        val view = previewView ?: return
        if (boundForLensFacing == lensFacing && boundLifecycleOwner?.get() === lifecycleOwner) {
            // Already bound to this exact lens and lifecycle, so no rebind — but the surface still
            // has to be reattached. Camera is one page of a swipeable pager: this composable is
            // disposed and recomposed every time it's scrolled away from and back, which detaches
            // and reattaches the reused PreviewView, and that tears down the underlying surface
            // the bound Preview use case was rendering into. Returning here without doing anything
            // (what this used to do) left a perfectly live camera drawing into a surface that no
            // longer exists — a black viewfinder that only recovered by flipping the lens, since
            // that forced the full rebind below. Re-setting the provider makes CameraX issue a
            // fresh surface request against the reattached view, which is all that was missing.
            preview?.surfaceProvider = view.surfaceProvider
            return
        }
        ensureStreamStateObserver(view)
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val targetLensFacing = lensFacing
            val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val selector = CameraSelector.Builder().requireLensFacing(targetLensFacing).build()
            runCatching {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, imageCapture)
                preview = previewUseCase
                boundForLensFacing = targetLensFacing
                pendingLensFacing = targetLensFacing
                boundLifecycleOwner = WeakReference(lifecycleOwner)
                torchEnabled = false
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Attached once (guarded by streamStateObserverAttached) rather than per-bind — observeForever
    // needs no LifecycleOwner and this object already outlives any single composition, same as
    // [previewView]/[camera] above, so one observer for the object's whole life is correct rather
    // than accumulating a fresh one on every rebind.
    private fun ensureStreamStateObserver(view: PreviewView) {
        if (streamStateObserverAttached) return
        streamStateObserverAttached = true
        view.previewStreamState.observeForever { state ->
            val pending = pendingLensFacing
            if (state == PreviewView.StreamState.STREAMING && pending != null) {
                boundLensFacing = pending
            }
        }
    }
}

/** A bare icon button (gallery/flip) — no background circle, sized up so it still reads clearly
 * as a control on its own against whatever's behind it (live feed or captured photo), the same
 * plain-glyph-on-the-photo language every other icon on this screen uses now. */
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

/** A small overlapping stack of real recipient avatars — up to [maxShown], then a "+N" circle —
 * used both in the header (pre-capture) and the send confirmation row (post-capture) so "who
 * this is going to" is the same recognizable faces in both places, not a label you have to read.
 * An empty selection gets its own plain "add" glyph rather than silently rendering nothing,
 * so a forgotten recipient choice is visually obvious instead of invisible. [ringColor] is the
 * solid backdrop behind each circle (needed since this sits over unpredictable live-camera or
 * photo content behind it); the white stroke on top of that is the actual visible separator
 * between overlapping avatars. */
@Composable
private fun RecipientAvatarStack(
    friends: List<FriendSummaryDto>,
    size: Dp,
    ringColor: Color,
) {
    if (friends.isEmpty()) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
        return
    }

    val maxShown = 2
    // A Box, not a Row — Row reserves each child's full, non-overlapping width even though the
    // offsets below make them overlap visually, which left a wide dead gap of *reserved but
    // invisible* space between the stack and whatever sits after it (the label text). A Box's
    // children don't push each other's layout position at all; every circle is placed from the
    // same (0,0) origin and only the explicit x-offset below moves it, so this container's own
    // width can be set to exactly the visible footprint instead of the sum of every circle's
    // full width.
    val overlapFraction = 0.65f
    val circleCount = minOf(friends.size, maxShown) + if (friends.size > maxShown) 1 else 0
    val stackWidth = size * (1f + (circleCount - 1) * overlapFraction)
    Box(modifier = Modifier.width(stackWidth).height(size)) {
        friends.take(maxShown).forEachIndexed { index, friend ->
            Box(
                modifier = Modifier
                    .offset(x = size * overlapFraction * index)
                    .size(size)
                    .clip(CircleShape)
                    .background(ringColor)
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (friend.profilePhotoUrl != null) {
                    AsyncImage(
                        model = friend.profilePhotoUrl,
                        contentDescription = friend.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Text(
                        text = friend.displayName.firstOrNull()?.uppercase() ?: "•",
                        fontFamily = PublicSansFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.value * 0.4f).sp,
                        color = Color.White,
                    )
                }
            }
        }
        if (friends.size > maxShown) {
            Box(
                modifier = Modifier
                    .offset(x = size * overlapFraction * maxShown)
                    .size(size)
                    .clip(CircleShape)
                    .background(ringColor)
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "+${friends.size - maxShown}",
                    fontFamily = PublicSansFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.32f).sp,
                    color = Color.White,
                )
            }
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
    val haptics = LocalHapticFeedback.current

    // A real capture (CameraX writing the JPEG to disk) is never instant — tens to hundreds of
    // ms depending on the device. Without its own feedback, the shutter just sits there looking
    // untouched for that whole stretch, then everything hard-cuts to the reviewing state at
    // once — which is what read as "the whole page reloads" rather than "I took a photo".
    // Shrinking the shutter the instant it's pressed (not waiting on the capture itself) is the
    // standard fix every real camera app uses: the response to your tap is immediate even though
    // the capture behind it isn't.
    val shutterInteractionSource = remember { MutableInteractionSource() }
    val isShutterPressed by shutterInteractionSource.collectIsPressedAsState()
    val shutterScale by animateFloatAsState(
        targetValue = if (isShutterPressed) 0.88f else 1f,
        animationSpec = tween(100),
        label = "shutterPressScale",
    )

    // Clustered tightly around the shutter — same idea as every real camera app's control
    // strip — instead of flung to the far edges of the screen where they read as three
    // unrelated buttons rather than one control group with the shutter as its obvious center.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.Rounded.Image,
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
                        Icon(Icons.Filled.Lock, contentDescription = "Emigo Gold", tint = colors.accentText, modifier = Modifier.size(10.dp))
                    }
                }
            } else null,
        )

        // The cream ring is the stationary anchor — only the gradient fill inside it shrinks on
        // press (see shutterScale above), the same "the ring holds still, the button inside it
        // presses down" feel every real camera app's shutter has. Scaling the outer ring too
        // would just make the whole control shrink in place, which reads as the button itself
        // getting smaller rather than being pressed.
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
                        // The scale animation above already IS this button's press feedback —
                        // a ripple on top of it would double up two different "you pressed me"
                        // cues for the same tap.
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
    val context = LocalContext.current
    var isEditingCaption by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    // Fraction 0..1 from top -> vertical bias -1..1
    val captionAlignment = BiasAlignment(0f, CAPTION_Y_FRACTION * 2f - 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        // The instant preview-snapshot bitmap (see CameraViewModel.previewBitmap's own doc
        // comment), drawn directly — a plain, already-decoded Bitmap needs no async load at all,
        // so it's on screen the same frame this composable first appears. Sits underneath the
        // AsyncImage below as a fallback layer: while that request is still decoding the real
        // file, this shows through instead of this Box having nothing drawn at all (which is what
        // exposed the black background before, on *both* the live→snapshot and snapshot→real
        // transitions — AsyncImage/Coil always decodes asynchronously, even for a local file).
        viewModel.previewBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        AsyncImage(
            // The app-wide ImageLoader default (see EmberApplication.newImageLoader) turns on
            // crossfade globally, for Home's feed — here it meant the just-captured photo faded
            // in from blank over this Box's black background instead of just appearing, which is
            // exactly the "fade from black" the user's now pointed out twice. Building the
            // request explicitly with crossfade(false) overrides that default for this one image
            // only, without touching the global setting Home still relies on. Still needed (not
            // replaced by the bitmap layer above) because it's the one that handles the real
            // file's EXIF-orientation-aware decode correctly.
            model = remember(file) { ImageRequest.Builder(context).data(file).crossfade(false).build() },
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
                Icons.Rounded.TextFields,
                contentDescription = "Add text",
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

/** Retake / send row shown while reviewing a captured shot. Recipients are shown once, in the
 * header chip above (live and preview both) — repeating a second "sending to" row here read as
 * two separate recipient pickers on the same screen, not one clearer one. Send just stays
 * disabled if nothing's selected, so the header chip is still the one place that matters.
 *
 * Send deliberately reuses the shutter's own exact circle (84dp ring, 70dp gradient fill, same
 * position in the row) rather than a differently-shaped pill — the point is that the button you
 * just pressed to take the photo is the same one that now sends it, just carrying a different
 * icon, not a new control appearing out of nowhere. The gallery/flip-camera slots are gone, but a
 * matching-size spacer on the outside holds their place so the center circle doesn't visibly
 * shift when CaptureControls crossfades into this. */
@Composable
private fun PreviewControls(
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
        // Balances the row the same way the gallery button's slot did in CaptureControls, so the
        // send circle sits at the exact same x-position in both states and doesn't visibly shift
        // when the row crossfades.
        Spacer(modifier = Modifier.size(46.dp))

        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .border(4.dp, colors.cream, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            // Color/icon reflect only whether there's someone to send to — not whether the real
            // file has landed yet (viewModel.isRealCaptureReady). That real-capture guard still
            // fully blocks the tap itself (both here and, belt-and-suspenders, inside
            // sendCaptured() itself), it's just not something the button visibly flashes through:
            // gating the *color* on it too briefly painted the button the muted/gray "disabled"
            // look right after every single capture, then snapped to the theme gradient a moment
            // later once the real file saved — a jarring flash rather than the instant, premium
            // feel every other control on this screen already has.
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
                    contentDescription = "Send",
                    tint = if (canSend) colors.accentText else colors.mutedDim,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        // Fixed to the same 46dp width as the gallery/flip RoundIconButtons in CaptureControls
        // (and the balancing Spacer above) — Retake's icon+label content is narrower than that on
        // its own, and letting the Column just wrap-content made its width differ from 46dp,
        // which shifted this Row's total width vs. CaptureControls' and threw the centered send
        // circle out of alignment with where the shutter had just been.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(46.dp)
                .clickable(enabled = !viewModel.isQueuingSend, onClick = viewModel::discardCapture),
        ) {
            Icon(
                Icons.Rounded.Replay,
                contentDescription = "Retake",
                tint = Color.White,
                modifier = Modifier.size(30.dp),
            )
            Text(
                text = "Retake",
                fontFamily = PublicSansFontFamily,
                fontSize = 11.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Compact paywall shown when a free account taps the gallery button. */
@Composable
private fun GoldUpsellOverlay(onDismiss: () -> Unit, onUpgrade: () -> Unit) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

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
                .background(colors.overlayPanel, EmberRadii.dialogShape)
                .padding(horizontal = 26.dp, vertical = 28.dp),
        ) {
            val badgeSizePx = Size(56f, 56f)
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(emberButtonBrush(EmberTheme.key, colors, badgeSizePx), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.WorkspacePremium, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(26.dp))
            }
            Text(
                text = "Emigo Gold",
                fontFamily = typography.display,
                fontSize = 19.sp,
                color = colors.cream,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = "Sending photos from your gallery is an Emigo Gold perk",
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
                    .background(emberButtonBrush(EmberTheme.key, colors, buttonSizePx), RoundedCornerShape(14.dp))
                    .clickable(onClick = onUpgrade)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Get Emigo Gold",
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
    // Freezing the current live frame and showing it immediately, before the real hardware
    // capture (real, unavoidable camera hardware/driver latency — see CameraSession.imageCapture's
    // own doc comment) even completes, is what actually makes this feel instant — the same trick
    // every mainstream camera app's shutter uses. previewView.bitmap grabs the currently-rendered
    // frame synchronously; the *bitmap itself* (not a file re-read of it) is what gets shown, in
    // CapturedPreview, via a plain Image(bitmap = ...) — see onPreviewSnapshotCaptured's own doc
    // comment for why a second AsyncImage/Coil round trip here was still flickering. Still
    // written to a temp file too, purely so capturedFile/discardCapture's existing File-based
    // bookkeeping keeps working unchanged.
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
    // CameraX mirrors the *preview* for the front camera (so it behaves like a mirror, which is
    // what every selfie viewfinder does) but saves the capture un-mirrored — so the photo you got
    // back was horizontally flipped compared to the one you just framed. ImageCapture.Metadata's
    // own isReversedHorizontal only records that as an EXIF flag rather than flipping any pixels,
    // and this app re-encodes on device whenever a caption is baked in (and the backend compresses
    // again after that), either of which drops EXIF and would silently lose the mirror — so the
    // flip is applied to the pixels themselves instead, in the ViewModel. See onPhotoCaptured.
    val isFrontCamera = CameraSession.lensFacing == CameraSelector.LENS_FACING_FRONT
    CameraSession.imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                viewModel.onPhotoCaptured(file, isFrontCamera = isFrontCamera)
            }

            override fun onError(exception: ImageCaptureException) {
                viewModel.captureFailed(exception.message ?: "Couldn't capture photo")
            }
        },
    )
}
