package com.ember.app.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * The widget-setup step of onboarding: the one screen that explains what this app actually is —
 * a photo that lives on your home screen, not another feed you have to remember to open. It sits
 * between picking a username and adding a first friend, so the payoff is understood before
 * anyone is asked to invite people to it.
 *
 * Two layers, not two separate steps: the pitch (one phone, the widget already on it, one clear
 * action), and — only if asked for — a walkthrough of the four taps Android's own launcher needs.
 * Most people never open the second layer, so it costs them nothing; the ones who do get real
 * instructions instead of being dropped into a launcher with no guidance.
 *
 * Drawn entirely in Compose from [AuthPalette]'s own tokens, matching [PhoneHomeMockup]'s
 * existing flat-silhouette language — no screenshots, no per-launcher branding, no glow. Real
 * screenshots would go stale the moment any launcher changes, and would look wrong on the many
 * Android skins that don't match whichever device they were taken on.
 */
@Composable
fun WidgetSetupStep(onAddWidget: () -> Unit) {
    val colors = AuthPalette
    var showInstructions by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // No back control, and so no reserved row for one either — the heading starts right
            // under the status bar. A back button here would only lead back to the username
            // field, which is already settled by this point, and leaving an empty strip where
            // one used to sit is worse than either having it or not.
            StaggeredEntrance(delayMillis = 60) {
                Text(
                    text = "Add the widget",
                    fontFamily = AuthPalette.display,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 34.sp,
                    color = colors.cream,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                )
            }
            StaggeredEntrance(delayMillis = 130) {
                Text(
                    text = "See their photos without opening the app.",
                    fontFamily = AuthPalette.body,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }

            // weight(1f) around the artwork, rather than a fixed gap above and below it: this
            // screen has to look settled on a short phone and a tall one alike, and the phone
            // illustration is the one element here that can absorb that difference without
            // anything looking cramped or stranded. Nothing is left floating in dead space —
            // the artwork always fills whatever the text and buttons don't use.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                // A fraction of the slot rather than all of it. fillMaxHeight() let the phone
                // grow to whatever height was going spare, which on a tall screen made it tower
                // over the heading and left a large blank area inside its own lower half (the
                // icon grid only occupies the top). Scaling it to most of the slot keeps it the
                // clear subject without dominating, and still adapts to any screen height.
                WidgetOnHomeMockup(modifier = Modifier.fillMaxHeight(0.78f))
            }

            StaggeredEntrance(delayMillis = 200) {
                AuthPrimaryButton(
                    text = "Add widget",
                    onClick = onAddWidget,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            StaggeredEntrance(delayMillis = 260) {
                Text(
                    text = "Show me how",
                    fontFamily = AuthPalette.body,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.muted,
                    modifier = Modifier
                        .padding(top = 18.dp, bottom = 24.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showInstructions = true },
                        ),
                )
            }
        }

        AnimatedVisibility(
            visible = showInstructions,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
        ) {
            WidgetInstructionsOverlay(
                onDone = {
                    showInstructions = false
                    onAddWidget()
                },
                onDismiss = { showInstructions = false },
            )
        }
    }
}

/** The four launcher taps, one per page. Swipeable rather than a stacked list — each step is a
 * single instruction paired with the thing it's describing, and showing them one at a time is
 * what keeps each illustration large enough to actually read. */
@Composable
private fun WidgetInstructionsOverlay(onDone: () -> Unit, onDismiss: () -> Unit) {
    val colors = AuthPalette
    var overlaySize by remember { mutableStateOf(Size.Zero) }
    val pagerState = rememberPagerState { WIDGET_INSTRUCTION_COUNT }
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == WIDGET_INSTRUCTION_COUNT - 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(overlaySize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "How to add it",
            fontFamily = AuthPalette.display,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 32.sp,
            color = colors.cream,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            Column(
                modifier = Modifier.fillMaxSize().padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    // Same fraction as the main screen's own mockup, so stepping into the
                    // walkthrough doesn't jump to a differently-sized phone.
                    WidgetInstructionArt(page = page, modifier = Modifier.fillMaxHeight(0.78f))
                }
                Text(
                    text = widgetInstructionCaption(page),
                    fontFamily = AuthPalette.body,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 23.sp,
                    color = colors.cream,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 22.dp),
        ) {
            repeat(WIDGET_INSTRUCTION_COUNT) { index ->
                val selected = index == pagerState.currentPage
                val dotAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.28f,
                    animationSpec = tween(200),
                    label = "instructionDotAlpha",
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer { alpha = dotAlpha }
                        .clip(CircleShape)
                        .background(colors.cream),
                )
            }
        }

        // One button that always moves forward — advancing through the pages, then finishing.
        // A separate "next" and "done" pair was the alternative and reads as more choice than
        // there actually is here; there's only ever one way on from any of these pages.
        AuthPrimaryButton(
            text = if (isLastPage) "I've added the widget" else "Next",
            onClick = {
                if (isLastPage) {
                    onDone()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
        )
    }
}

private const val WIDGET_INSTRUCTION_COUNT = 4

private fun widgetInstructionCaption(page: Int): String = when (page) {
    0 -> "Press and hold your home screen"
    1 -> "Tap Widgets"
    2 -> "Search for Emigo"
    else -> "Drag it into place"
}

/** The hero: the widget already on a home screen. Literally the same mockup the welcome screen
 * opens with — same phone, same tilt, same sample photo — so arriving here reads as the promise
 * from the first screen being cashed in, not as a different illustration of a different thing. */
@Composable
private fun WidgetOnHomeMockup(modifier: Modifier = Modifier) {
    PhoneHomeMockup(modifier = modifier)
}

/** One illustration per instruction page, drawn inside the shared [AuthPhoneFrame] so all four
 * are unmistakably the same device as the hero above. Each page shows only the single element
 * its caption is about; everything else stays a quiet placeholder, because the point of a page
 * is one tap target. */
@Composable
private fun WidgetInstructionArt(page: Int, modifier: Modifier = Modifier) {
    val colors = AuthPalette
    AuthPhoneFrame(modifier = modifier) { cell, gap ->
        when (page) {
            // Press and hold: a full grid, with a touch point resting on empty space rather
            // than on any icon.
            0 -> {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(4) { AuthPhoneAppIcon(cell) }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = cell * 4 + gap * 4)
                        .size(cell * 1.1f)
                        .clip(CircleShape)
                        .background(colors.cream.copy(alpha = 0.18f))
                        .padding(cell * 0.22f)
                        .clip(CircleShape)
                        .background(colors.cream),
                )
            }

            // Tap "Widgets": the launcher's own action chip, below a partial grid.
            1 -> {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(2) {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(4) { AuthPhoneAppIcon(cell) }
                        }
                    }
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = cell * 3 + gap * 3),
                ) {
                    Box(
                        modifier = Modifier
                            .size(cell * 1.3f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.cream),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Widgets,
                            contentDescription = null,
                            tint = colors.accentText,
                            modifier = Modifier.size(cell * 0.62f),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = gap * 0.8f)
                            .width(cell * 1.6f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(colors.muted.copy(alpha = 0.5f)),
                    )
                }
            }

            // Find Emigo: a search field with the app's own name, over the picker's preview tiles.
            2 -> Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(cell)
                        .clip(RoundedCornerShape(50))
                        .background(colors.panel)
                        .padding(horizontal = cell * 0.32f),
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(cell * 0.4f),
                    )
                    Text(
                        text = "Emigo",
                        fontFamily = AuthPalette.body,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.cream,
                        modifier = Modifier.padding(start = cell * 0.2f),
                    )
                }
                repeat(3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(2) {
                            Box(
                                modifier = Modifier
                                    .size(width = cell * 2 + gap, height = cell * 1.2f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(colors.panel),
                            )
                        }
                    }
                }
            }

            // Position and resize: the finished state — the same widget, in place, with the one
            // corner handle you would actually drag.
            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    repeat(4) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            repeat(4) { col ->
                                if (row in 1..2 && col in 2..3) {
                                    Box(modifier = Modifier.size(cell))
                                    return@repeat
                                }
                                AuthPhoneAppIcon(cell)
                            }
                        }
                    }
                }
                AuthPhoneWidget(
                    size = cell * 2 + gap,
                    modifier = Modifier.align(Alignment.TopEnd).offset(y = cell + gap),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = cell * 3 + gap * 2)
                        .size(cell * 0.42f)
                        .clip(CircleShape)
                        .background(colors.cream),
                )
            }
        }
    }
}
