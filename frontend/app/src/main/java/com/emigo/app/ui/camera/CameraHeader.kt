package com.emigo.app.ui.camera

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.emigo.app.R
import com.emigo.app.data.remote.dto.FriendSummaryDto
import com.emigo.app.ui.home.FEATURED_CARD_ASPECT_RATIO
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily

/** Shares the header row with the recipient chip, where the old send-status pill sat. Saving is
 * now this screen's own action (see CameraViewModel.saveToMemories), not a status reported
 * afterward, so it reads as a control (bookmark, tap to save): outline when unsaved, filled with a
 * brief spinner while queuing, solid once saved. A plain glyph with no background circle, like the
 * other bare icon buttons here. */
@Composable
internal fun SaveToMemoriesButton(viewModel: CameraViewModel, modifier: Modifier = Modifier) {
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
            contentDescription = stringResource(if (viewModel.isSaved) R.string.camera_saved_to_memories else R.string.camera_save_to_memories),
            tint = Color.White,
            modifier = Modifier.size(28.dp),
        )
        if (viewModel.isSavingToMemories) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        }
    }
}

/** Camera's outbox: opens a screen of recent sends, mainly so one can be unsent (see
 * SentPhotosScreen). Shows [lastSentPhotoUrl] (the most recent unsaved send, refreshed by
 * [CameraViewModel.refreshLastSentPhoto]) cropped to the app's photo-card proportions
 * ([FEATURED_CARD_ASPECT_RATIO]), a real thumbnail instead of a generic glyph. With no recent send
 * (or the fetch hasn't landed) it falls back to an empty outline; the 2.2dp border, thicker than a
 * hairline, keeps it from vanishing at this size.
 *
 * Its animation mirrors [CameraViewModel.sendAnimState]: idle thumbnail, then a bright segment
 * tracing the border (corners included) while the background upload is in flight, then filled
 * solid with a checkmark once it lands, then back to the updated thumbnail. The segment is a
 * [PathMeasure] walk along the same rounded-rect outline the border draws, by distance along the
 * path. A rotating [Brush.sweepGradient] was tried first and looked like a distorted shape spinning
 * behind the icon, because angle versus arc length isn't constant on a non-square rounded rect;
 * walking the real path reads as one line tracing the edge. */
@Composable
internal fun OutboxButton(sendAnimState: SendAnimState, lastSentPhotoUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val density = LocalDensity.current
    val shape = RoundedCornerShape(4.dp)
    val cardWidth = 20.dp
    val cardHeight = cardWidth / FEATURED_CARD_ASPECT_RATIO
    val borderWidth = 2.2.dp

    // Built once from the shape's fixed size; the traveling segment walks this exact path, so it
    // can't drift from the border drawn behind it.
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
    val sentPhotosDescription = stringResource(R.string.camera_sent_photos_description)

    Box(
        modifier = modifier
            .size(40.dp)
            .semantics { contentDescription = sentPhotosDescription }
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
                        // Wraps to the path's start: two pieces stitched into one draw so the line
                        // doesn't visibly break at the seam.
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

/** A small overlapping stack of real recipient avatars, up to [maxShown] and then a "+N" circle,
 * used in the header (pre-capture) and the send row (post-capture) so "who this goes to" shows the
 * same faces in both. An empty selection shows a plain "add" glyph instead of nothing, so a
 * forgotten recipient choice is obvious. [ringColor] is the solid backdrop behind each circle
 * (needed over unpredictable camera or photo content); the white stroke on top is what visibly
 * separates overlapping avatars. */
@Composable
internal fun RecipientAvatarStack(
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
    // A Box, not a Row: a Row reserves each child's full width even though the offsets below make
    // them overlap, leaving a wide dead gap of reserved but invisible space before the label. Box
    // children don't push each other; each circle starts at (0,0) and only its x-offset moves it,
    // so the container's width can equal the visible footprint.
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
