package com.emigo.app.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.emigo.app.R
import com.emigo.app.ui.theme.PublicSansFontFamily
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Live viewfinder inside the card. Stays mounted while [isReviewing] a just-sent photo (see the
 * call site for why). [isReviewing] only stops this composable's pinch-to-zoom from acting on a
 * two-finger pinch over the reviewed photo, which would otherwise silently change the hidden live
 * camera's zoom. */
@Composable
internal fun LiveCameraStage(isReviewing: Boolean = false) {
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

    // Re-read the real permission state each time the screen returns to the foreground. The
    // snapshot above is taken once, and Camera is the opening page, so it composes before the
    // startup permission dialog (see MainActivity) is answered. Granting there doesn't reach this
    // screen's own launcher, so its cached `false` stood until the process restarted: the "blank
    // camera until you reopen the app" symptom. Keying on the lifecycle covers every route a grant
    // can arrive by: the startup dialog, this screen's prompt, or system settings.
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
        // The PreviewView and its binding live in CameraSession, not in remember{} on this
        // composition (see its doc: Camera is a pager page, disposed and recomposed on every scroll
        // away and back). bindIfNeeded is keyed on lensFacing so a flip is picked up at once, but
        // no-ops if that lens is already bound, instead of re-fetching the provider and rebinding on
        // every re-entry.
        val previewView = remember { CameraSession.previewViewFor(context) }
        LaunchedEffect(CameraSession.lensFacing) {
            CameraSession.bindIfNeeded(context, lifecycleOwner)
        }

        // Transient zoom readout like "1.8x": shown when a pinch changes the zoom, hidden shortly
        // after the fingers stop, as in real camera apps.
        var displayedZoomRatio by remember { mutableStateOf<Float?>(null) }
        var hideZoomIndicatorJob by remember { mutableStateOf<Job?>(null) }
        val coroutineScope = rememberCoroutineScope()

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    // Consumes only a genuine two-finger pinch. The old detectTransformGestures
                    // reported and consumed pan even from a single pointer, swallowing every swipe
                    // meant for the outer tab pager once it landed on the live camera. Waiting for a
                    // second pointer lets one-finger swipes fall through to the pager, as on other
                    // tabs.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.size >= 2 && !isReviewing) {
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

            // Front cameras don't carry a usable flash on this device (hasFlashUnit() alone wasn't
            // reliable). Gated on boundLensFacing, not lensFacing: lensFacing flips the instant the
            // flip button is tapped, before the rebind finishes, so the icon changed a beat before
            // the preview. boundLensFacing updates only once the new camera is bound, so the two
            // change together.
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
                    // The standard bolt glyph for flash, not a literal flashlight.
                    Icon(
                        imageVector = if (CameraSession.torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        contentDescription = stringResource(if (CameraSession.torchEnabled) R.string.camera_flash_off else R.string.camera_flash_on),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.camera_permission_needed),
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

/** Holds camera state that must survive the capture -> preview -> retake round trip and be shared
 * between the viewfinder and shutter button without threading it through every composable.
 * lensFacing is Compose state so the viewfinder rebinds when the flip button changes it.
 *
 * Also holds the [PreviewView] and which lens it's bound for. Camera is a pager page, disposed and
 * recomposed on every scroll away and back; with the view and binding scoped to that composition,
 * the full ProcessCameraProvider fetch + bindToLifecycle sequence (the source of the "black screen
 * for a few seconds" delay) reran on every re-entry. Reusing the view and rebinding only when the
 * lens changes means re-entering just reattaches an already-live preview. */
internal object CameraSession {
    var lensFacing by mutableStateOf(CameraSelector.LENS_FACING_BACK)

    // Bounding resolution or JPEG quality (to speed up encoding) was tried and rejected: the user
    // wants full capture quality, even if CameraX picks a high default resolution. What stays:
    // CAPTURE_MODE_MINIMIZE_LATENCY (CameraX's default, stated explicitly so a future default change
    // can't slow it down) and flash forced off; neither changes how the photo looks. Sensor readout,
    // JPEG encode and any autofocus/auto-exposure convergence are camera hardware and driver latency,
    // which app code can't remove and which varies by device.
    val imageCapture: ImageCapture = ImageCapture.Builder()
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
        .setFlashMode(ImageCapture.FLASH_MODE_OFF)
        .build()

    var previewView: PreviewView? = null
        private set
    private var boundForLensFacing: Int? = null

    // The bound Camera handle (bindToLifecycle's return value), where CameraX exposes zoom control
    // (cameraControl.setZoomRatio) and bounds (cameraInfo.zoomState). Nulled on every rebind and
    // re-set when the new bind completes, so a pinch mid-rebind (e.g. right after flipping lenses)
    // sees a consistent camera, not the one being replaced.
    var camera: Camera? = null
        private set

    // Compose state for which lens is actually visible now, distinct from [lensFacing] (flips the
    // instant the flip button is tapped) and from bindToLifecycle's return (that registers the
    // pipeline, but the sensor takes a beat to warm up and deliver its first frame; setting it there
    // changed visibly before the viewfinder did). It's set when previewStreamState reaches
    // STREAMING, CameraX's signal that frames are painting the surface, so the flash button changes
    // in step with what's on screen.
    var boundLensFacing by mutableStateOf<Int?>(null)
        private set

    // Which lens the latest bind was for. The previewStreamState observer (attached once) reads it
    // on STREAMING, since that callback doesn't say which lens just started.
    private var pendingLensFacing: Int? = null
    private var streamStateObserverAttached = false

    // Torch is its own on/off state, not read back from CameraX: enableTorch is fire-and-forget, so
    // this is the only source of truth for the button. Reset to off on every rebind, since a freshly
    // bound camera starts with its torch off.
    var torchEnabled by mutableStateOf(false)
        private set

    fun toggleTorch() {
        val cam = camera ?: return
        val next = !torchEnabled
        cam.cameraControl.enableTorch(next)
        torchEnabled = next
    }

    // The live Preview use case, kept so its surface provider can be reattached without a full
    // rebind (see bindIfNeeded's early return: the difference between a working viewfinder and a
    // black one).
    private var preview: Preview? = null

    // CameraX auto-unbinds bindToLifecycle's registration when the bound LifecycleOwner reaches
    // DESTROYED, which happens to the Activity on any config change the manifest doesn't declare
    // (dark/light toggle, font scale, foldable resize; only screenOrientation is declared). Tracking
    // lens alone left this object saying "already bound" for an owner CameraX had silently dropped,
    // giving a permanently black viewfinder until the user flipped the lens or restarted. A weak
    // reference so this object never keeps a destroyed Activity alive.
    private var boundLifecycleOwner: WeakReference<LifecycleOwner>? = null

    fun previewViewFor(context: Context): PreviewView =
        previewView ?: PreviewView(context.applicationContext).also { previewView = it }

    fun bindIfNeeded(context: Context, lifecycleOwner: LifecycleOwner) {
        val view = previewView ?: return
        if (boundForLensFacing == lensFacing && boundLifecycleOwner?.get() === lifecycleOwner) {
            // Already bound to this lens and lifecycle, so no rebind, but the surface must be
            // reattached. Camera is a pager page: disposing and recomposing it detaches and
            // reattaches the reused PreviewView, which tears down the surface the bound Preview was
            // rendering into. Returning without doing anything (the old behavior) left a live camera
            // drawing into a surface that no longer existed: a black viewfinder that recovered only
            // by flipping the lens, which forced the full rebind below. Re-setting the provider makes
            // CameraX issue a fresh surface request against the reattached view.
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

    // Attached once (guarded by streamStateObserverAttached), not per bind: observeForever needs no
    // LifecycleOwner and this object outlives any composition, so one observer for its whole life
    // is right instead of piling up a new one per rebind.
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
