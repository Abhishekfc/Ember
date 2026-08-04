package com.ember.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.work.WorkInfo
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ember.app.data.remote.dto.FriendSummaryDto
import com.ember.app.ui.components.emberButtonBrush
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference

@Composable
fun CameraScreen(
    viewModel: CameraViewModel,
    onOpenRecipientPicker: () -> Unit,
    onUpgradeToGold: () -> Unit,
    onSent: () -> Unit,
    // Home's own real, measured header height (see HomeScreen's onHeaderHeightChanged) — used
    // below to size this screen's own header spacer so the camera card lands at exactly the same
    // absolute Y position on screen as Home's featured card, rather than a separately-guessed
    // constant that had no way to track Home's real layout and drifted out of alignment.
    homeHeaderHeightPx: Float,
) {
    val colors = EmberTheme.colors
    val context = LocalContext.current
    val density = LocalDensity.current
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val cardShape = RoundedCornerShape(30.dp)
    val captured = viewModel.capturedFile

    // This screen's own header row real height — measured the same way, not guessed either.
    var headerRowHeightPx by remember { mutableStateOf(0f) }

    // Reflects WorkManager's own real, persisted state for every queued send — not a second,
    // hand-maintained counter that could drift from it — so this stays correct even across a
    // process restart while photos are still waiting on connectivity. null (not emptyList()) for
    // the one frame before the Flow's first real emission lands — collectAsState's own
    // `initial = emptyList()` would otherwise look identical to "confirmed, there are zero
    // pending/succeeded sends," and the *real* first emission arriving a moment later (which,
    // once WorkManager has any send history at all, reports a nonzero succeeded count already)
    // then looked exactly like a brand new completion — this was showing "Sent" every single
    // time Camera opened, not only right after an actual send.
    val workManager = remember { WorkManager.getInstance(context.applicationContext) }
    val pendingSendInfos by produceState<List<WorkInfo>?>(initialValue = null, workManager) {
        workManager.getWorkInfosByTagFlow(PendingSendWorker.TAG_PENDING_SEND).collect { value = it }
    }
    val pendingSendCount = pendingSendInfos.orEmpty().count { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }
    val succeededSendCount = pendingSendInfos?.count { it.state == WorkInfo.State.SUCCEEDED }

    // A brief "Sent" flash the moment a queued send actually finishes — placeholder wording/
    // timing for now (a fancier version is a follow-up idea), just needs to visibly distinguish
    // "still sending" from "actually landed" rather than only ever saying "Sending…". Only ever
    // compares against a REAL previous snapshot (never null) so the first real emission this
    // screen ever sees just establishes the baseline silently, instead of being mistaken for a
    // fresh completion.
    var lastKnownSucceededCount by remember { mutableStateOf<Int?>(null) }
    var showSentBadge by remember { mutableStateOf(false) }
    LaunchedEffect(succeededSendCount) {
        val current = succeededSendCount ?: return@LaunchedEffect
        val previous = lastKnownSucceededCount
        if (previous != null && current > previous) {
            showSentBadge = true
            delay(2500)
            showSentBadge = false
        }
        lastKnownSucceededCount = current
    }

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
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Box(
                modifier = Modifier
                    // Must come before fillMaxWidth()/padding() below, not after — see this
                    // screen's own Spacer further down for why: reporting this Box's *total*
                    // height (26dp top padding included) is what lets that spacer be computed
                    // instead of guessed.
                    .onGloballyPositioned { headerRowHeightPx = it.size.height.toFloat() }
                    .fillMaxWidth()
                    .padding(top = 26.dp, start = 22.dp, end = 22.dp),
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
                        .padding(start = 5.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RecipientAvatarStack(friends = viewModel.selectedFriends, size = 26.dp, ringColor = colors.panel)
                    if (viewModel.hasPinnedSelected) {
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = null,
                            tint = colors.glow,
                            modifier = Modifier.padding(start = 7.dp).size(11.dp),
                        )
                    }
                    Text(
                        text = viewModel.recipientLabel,
                        fontFamily = PublicSansFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                    Icon(
                        Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 2.dp).size(14.dp),
                    )
                }

                SendStatusIndicator(
                    pendingSendCount = pendingSendCount,
                    showSentBadge = showSentBadge,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            // Home's card sits below three stacked header lines (wordmark, greeting, date);
            // this screen's header is a single row, so matching just the "18dp after header"
            // padding below isn't enough on its own — this spacer makes up the *real* difference
            // between Home's real measured header height and this screen's own real measured
            // header row height (both computed above), so the card lands at exactly the same
            // absolute position on screen as Home's does and feels like the same card carrying
            // through both screens. A fixed dp guess lived here before — it was calibrated once
            // and had no way to track Home's header height actually changing (a connection-error
            // line wrapping, a longer name, font scale), so it silently drifted out of alignment.
            Spacer(modifier = Modifier.height(with(density) { (homeHeaderHeightPx - headerRowHeightPx).coerceAtLeast(0f).toDp() }))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, start = 22.dp, end = 22.dp)
                    .aspectRatio(0.8f)
                    .clip(cardShape)
                    .background(Color.Black),
            ) {
                // Plain, instant swap — no fade. A Crossfade here was tried (twice: first keyed
                // on a boolean that re-read viewModel.capturedFile live inside the lambda, which
                // crashed the app on retake — both the outgoing and incoming slots saw the same
                // already-null value and both tried to mount LiveCameraStage at once, and two
                // AndroidViews can't share CameraSession's one singleton PreviewView; then keyed
                // on the File? itself instead, which fixed the crash but meant an instant-preview
                // snapshot silently getting replaced by the real photo triggered a second fade
                // through this Box's black background, reading as a flicker) and explicitly
                // rejected both times — no animation, no intermediate frame, just the real photo
                // the moment it's ready.
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

/** Wraps the AnimatedVisibility around SendStatusPill in its own composable, rather than inline
 * at the call site — Kotlin's implicit-receiver resolution otherwise reaches past the immediate
 * Box scope there and out to the enclosing Column further up this screen, resolving the bare
 * AnimatedVisibility(...) call to the ColumnScope-specific overload (which adds Column's own size
 * animation) instead of the plain one, and failing to compile since there's no real ColumnScope
 * receiver actually available at that call site. A dedicated function's body has no such
 * ambient receiver leaking in, so the plain overload resolves cleanly. */
@Composable
private fun SendStatusIndicator(pendingSendCount: Int, showSentBadge: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = pendingSendCount > 0 || showSentBadge,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
        modifier = modifier,
    ) {
        if (pendingSendCount > 0) {
            SendStatusPill(
                text = if (pendingSendCount == 1) "Sending" else "Sending $pendingSendCount",
                showSpinner = true,
            )
        } else {
            SendStatusPill(text = "Sent", showSpinner = false)
        }
    }
}

/** Small, quiet status chip sharing the header row with the recipient chip — deliberately just
 * text (plus a spinner while actually sending), not a second card competing for attention. Wording
 * is a placeholder for now (there's a fancier version planned later); this only needs to tell
 * "still sending" apart from "actually landed." */
@Composable
private fun SendStatusPill(text: String, showSpinner: Boolean) {
    val colors = EmberTheme.colors
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(colors.panel)
            .border(1.dp, colors.border, RoundedCornerShape(percent = 50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSpinner) {
            CircularProgressIndicator(color = colors.glow, strokeWidth = 2.dp, modifier = Modifier.size(12.dp))
        }
        Text(
            text = text,
            fontFamily = PublicSansFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = colors.cream,
            modifier = Modifier.padding(start = if (showSpinner) 8.dp else 0.dp),
        )
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
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { previewView })
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
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, imageCapture)
                preview = previewUseCase
                boundForLensFacing = lensFacing
                boundLifecycleOwner = WeakReference(lifecycleOwner)
            }
        }, ContextCompat.getMainExecutor(context))
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
 * An empty selection gets its own dashed "add" circle rather than silently rendering nothing,
 * so a forgotten recipient choice is visually obvious instead of invisible. [ringColor] separates
 * overlapping avatars from each other and needs to be solid, since this sits over unpredictable
 * live-camera or photo content behind it. */
@Composable
private fun RecipientAvatarStack(
    friends: List<FriendSummaryDto>,
    size: Dp,
    ringColor: Color,
) {
    if (friends.isEmpty()) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
        }
        return
    }

    val maxShown = 3
    Row {
        friends.take(maxShown).forEachIndexed { index, friend ->
            Box(
                modifier = Modifier
                    .offset(x = size * -0.35f * index)
                    .size(size)
                    .clip(CircleShape)
                    .border(1.5.dp, ringColor, CircleShape)
                    .background(ringColor),
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
                    .offset(x = size * -0.35f * maxShown)
                    .size(size)
                    .clip(CircleShape)
                    .border(1.5.dp, ringColor, CircleShape)
                    .background(ringColor),
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
                        Icon(Icons.Filled.Lock, contentDescription = "Ember Gold", tint = colors.accentText, modifier = Modifier.size(10.dp))
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
                .size(76.dp)
                .clip(CircleShape)
                .border(4.dp, colors.cream, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
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
 * Send deliberately reuses the shutter's own exact circle (76dp ring, 62dp gradient fill, same
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
                .size(76.dp)
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
                    .size(62.dp)
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
                    .background(emberButtonBrush(EmberTheme.key, colors, buttonSizePx), RoundedCornerShape(14.dp))
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
