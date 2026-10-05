package com.emigo.app.ui.camera

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.R
import com.emigo.app.ui.components.LocalNavDockHeight
import com.emigo.app.ui.components.emberButtonBrush
import com.emigo.app.ui.home.AVATAR_ROW_TOP_GAP
import com.emigo.app.ui.home.FEATURED_CARD_ASPECT_RATIO
import com.emigo.app.ui.home.HomeHeaderHeightTwin
import com.emigo.app.ui.home.HomeViewModeToggleHeightTwin
import com.emigo.app.ui.home.featuredCardSidePadding
import com.emigo.app.ui.home.homeFoldMetricsFor
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import java.io.File
import java.io.FileOutputStream

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
    // Measured header height, as Home measures its own, so the fold below is bounded by the real
    // remaining space instead of a guess.
    var headerHeightPx by remember { mutableStateOf(0f) }
    val cardShape = RoundedCornerShape(30.dp)
    val captured = viewModel.capturedFile

    // Reviewing a shot? Back retakes instead of leaving the camera.
    BackHandler(enabled = captured != null) { viewModel.discardCapture() }

    // Guards launch() itself: the system picker takes a moment to appear, and a fast double or
    // triple tap in that gap called launch() once per tap, stacking picker instances on the back
    // stack. Reset when the picker returns (picked or cancelled).
    var galleryPickerInFlight by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        galleryPickerInFlight = false
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
        // No .navigationBarsPadding(): LocalNavDockHeight below already includes the system
        // nav-bar inset (see HomeScreen's identical note), so adding it would double-count and
        // skew topFoldMaxHeightDp.
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            // An overlay Box, not a sequential Column item, sizing to its tallest child. The header
            // row below is tuned to measure the same height as the invisible twin, i.e. Home's real
            // header, not the reverse (Home's header staying near the status bar matters more than
            // this row's top padding).
            Box(modifier = Modifier.onGloballyPositioned { headerHeightPx = it.size.height.toFloat() }) {
                // Invisible twin of Home's header (see its doc in HomeHeader.kt). Its natural height
                // (alpha is draw-time, not layout) reserves the same vertical space as Home's real
                // header, computed locally instead of read back from Home's render. That older
                // approach made the card jump: Camera is the opening page, so the first frame read
                // zero, then jumped when Home was first visited and reported its real height.
                Box(modifier = Modifier.alpha(0f)) { HomeHeaderHeightTwin() }

                Box(
                    modifier = Modifier
                        // matchParentSize(), not fillMaxWidth() plus hand-tuned top padding: this
                        // measures against the twin's already-decided size instead of contributing
                        // to it, so the row can't make the header taller than Home's. The old
                        // version added 6dp to a 54dp chip for 60dp, tuned to a 60dp Home header;
                        // Home's became 44dp when its icons were resized, nothing tied the numbers
                        // together, and Camera's card silently sat ~16dp lower. The twin is now the
                        // single source of this height.
                        .matchParentSize()
                        .padding(start = 22.dp, end = 22.dp),
                ) {
                    // Real faces, not a text label, so who this goes to is recognizable at a
                    // glance. The only recipient control on screen (live and post-capture).
                    // Centered and compact, leaving both corners free (send status on the right
                    // today).
                    //
                    // No close button: Camera is a page of the main pager (swipe to Home like any
                    // tab), and discarding an abandoned capture on the way out is automatic (see
                    // MainActivity's settledPage effect).
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(percent = 50))
                            .clickable(onClick = onOpenRecipientPicker)
                            // 5dp vertical, not 10: with the 34dp avatar stack the pill is 44dp,
                            // fitting the header height the twin defines. At 10dp it was 54dp and
                            // overflowed that slot, since matchParentSize() no longer stretches the
                            // row.
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

                    // Always present (unlike the bookmark, which appears only while reviewing); opens
                    // past sends, independent of what's being framed or reviewed.
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

            // Bounds the card and controls to the real space below the header: screen height minus
            // status bar, this header and the dock's reserve (LocalNavDockHeight, the value
            // HomeScreen's topFoldMaxHeightDp subtracts). Bounding to the whole rest of the Column
            // put the card far lower than Home's: without the dock subtraction the space ran to the
            // true screen bottom (the dock is invisible here but still real estate Home's math
            // excludes). Gated on both measurements like HomeScreen, to avoid a wrong-then-corrected
            // flash.
            if (screenSize != Size.Zero && headerHeightPx > 0f) {
                val statusBarPx = WindowInsets.statusBars.getTop(density)
                val navDockHeightPx = with(density) { LocalNavDockHeight.current.toPx() }
                val topFoldMaxHeightDp = with(density) {
                    (screenSize.height - statusBarPx - headerHeightPx - navDockHeightPx).toDp()
                }
                // The scale Home picks for its fold. The inputs match by construction
                // (homeFoldMetricsFor excludes the toggle row from available height, which is what
                // topFoldMaxHeightDp is here), so both screens resolve the same metrics and their
                // cards stay the same size in the same place.
                val foldMetrics = homeFoldMetricsFor(
                    availableHeight = topFoldMaxHeightDp,
                    screenWidth = with(density) { screenSize.width.toDp() },
                )
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = topFoldMaxHeightDp)) {
                    // Home has a HomeViewModeToggleRow between header and card; this screen has
                    // nothing there. That row alone made Camera's card sit higher than Home's
                    // despite matching header heights, so reserve it the same invisible-twin way
                    // instead of copying a dp number that would drift.
                    Box(modifier = Modifier.alpha(0f)) { HomeViewModeToggleHeightTwin(foldMetrics) }

                    // Fixed gap, then the card, mirroring HomeScreen's fold. Both screens once split
                    // leftover space with weight(1f) spacers, which made gaps device-dependent and
                    // put the cards at different heights when leftovers differed. A constant on both
                    // pins the card to the same absolute position.
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
                        // LiveCameraStage is unconditional; CapturedPreview draws as an opaque overlay on
                        // top instead of replacing it, so sending a photo (captured back to null) no
                        // longer disposes and recreates LiveCameraStage's AndroidView, which forced the
                        // camera surface to reattach and flash black. This was tried once in a larger
                        // change that also switched the preview's rendering mode, which caused choppy
                        // pager flings; the mode switch was reverted and this overlay piece reapplied
                        // alone. A Crossfade was tried twice and rejected: first keyed on a boolean
                        // re-read live inside the lambda, which crashed on retake (both slots saw the
                        // already-null value and each mounted a second LiveCameraStage, but two
                        // AndroidViews can't share CameraSession's one PreviewView); then keyed on the
                        // File?, which fixed the crash but made an instant-preview snapshot replaced by
                        // the real photo fade through this Box's black background, reading as flicker.
                        // With one never-duplicated LiveCameraStage the overlay can hit neither, and
                        // CapturedPreview draws the real photo the instant it's ready, with no
                        // animation of its own.
                        LiveCameraStage(isReviewing = captured != null)
                        if (captured != null) {
                            CapturedPreview(viewModel = viewModel, file = captured)
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        // Same gap Home puts between its card and the avatar row, so what follows the
                        // card starts at the same offset on both screens.
                        modifier = Modifier.fillMaxWidth().padding(top = AVATAR_ROW_TOP_GAP),
                    ) {
                        Crossfade(targetState = captured != null, animationSpec = tween(220), label = "cameraControlsStage") { isReviewing ->
                            if (isReviewing) {
                                PreviewControls(viewModel = viewModel, onSent = onSent)
                            } else {
                                CaptureControls(
                                    viewModel = viewModel,
                                    onPickFromGallery = {
                                        if (!galleryPickerInFlight) {
                                            viewModel.onGalleryClick {
                                                galleryPickerInFlight = true
                                                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                            }
                                        }
                                    },
                                )
                            }
                        }

                        // New-user only, gone for good after the first swipe (see
                        // CameraViewModel.showSwipeHint). captured == null because the point is that
                        // the rest of the app is a swipe away, which doesn't apply mid-review.
                        AnimatedVisibility(
                            visible = viewModel.showSwipeHint && captured == null,
                            exit = fadeOut(tween(220)),
                        ) {
                            // AVATAR_ROW_TOP_GAP, the same named gap Home uses below its featured card,
                            // rather than a new magic number no other spacing agrees with.
                            SwipeHint(modifier = Modifier.padding(top = AVATAR_ROW_TOP_GAP))
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
                text = stringResource(R.string.gold_title),
                fontFamily = typography.display,
                fontSize = 19.sp,
                color = colors.cream,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.camera_gold_perk),
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
                    text = stringResource(R.string.camera_get_gold),
                    fontFamily = PublicSansFontFamily,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accentText,
                )
            }
            Text(
                text = stringResource(R.string.camera_maybe_later),
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
