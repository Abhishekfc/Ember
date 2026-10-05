package com.emigo.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.R
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily

/** Stands in for the featured card when there are no friends yet or none has sent a photo
 * (feedItems.isEmpty() covers both). Same shape and slot as the real card
 * ([FEATURED_CARD_ASPECT_RATIO], [FEATURED_CARD_CORNER_RADIUS]), so it reads as "the card that's
 * normally here". `home_empty_backdrop` is a crop of `frontend/assets/homeEmptyStateCollage.png`,
 * blurred and darkened in code so both stay tunable. The scrim is a bottom-weighted gradient, not
 * flat: a flat 45% black wash crushed the dark collage to near-black, so it fades from transparent
 * at the top to dark only behind the text, like [FeaturedPhotoCard]'s scrim. */
@Composable
internal fun HomeEmptyStateCard(
    onAddFriendClick: () -> Unit,
    caption: String,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val cardShape = RoundedCornerShape(FEATURED_CARD_CORNER_RADIUS)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(FEATURED_CARD_ASPECT_RATIO)
                .clip(cardShape)
                .background(colors.elevatedPanel),
        ) {
            Image(
                painter = painterResource(R.drawable.home_empty_backdrop),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(8.dp, BlurredEdgeTreatment.Unbounded),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 26.dp),
            ) {
                Text(
                    text = stringResource(R.string.home_add_friends_prompt),
                    fontFamily = typography.display,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 29.sp,
                    color = Color.White,
                )
                Row(
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.White)
                        .clickable(onClick = onAddFriendClick)
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.recipients_find_friends),
                        fontFamily = PublicSansFontFamily,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                }
            }
        }
        Text(
            text = caption,
            fontFamily = typography.body,
            fontSize = 12.5.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        )
    }
}

/** Cold-start loading state shaped like the featured card and avatar row it will become, so the
 * layout doesn't jump when data lands. One slow, flat opacity pulse (no gradient sweep or glow,
 * per the app's no-glow rule). */
@Composable
internal fun HomeSkeletonLoader(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        SkeletonFeaturedCard()
        Row(
            modifier = Modifier
                .padding(top = 22.dp)
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            repeat(5) { index -> SkeletonAvatar(staggerIndex = index) }
        }
    }
}

/** The shared pulse every skeleton element uses; only [staggerMillis] varies per call site (via
 * `initialStartOffset`), so staggered elements read as one coordinated ripple. */
@Composable
internal fun rememberSkeletonPulse(periodMillis: Int, staggerMillis: Int = 0): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeletonPulse")
    return transition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMillis, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(staggerMillis, StartOffsetType.FastForward),
        ),
        label = "skeletonAlpha",
    )
}

/** The featured-card placeholder: pulses slowly, plus a smaller caption chip where the real name
 * label sits, pulsing a beat behind so it reads as one shape settling after the other. */
@Composable
private fun SkeletonFeaturedCard() {
    val colors = EmberTheme.colors
    val cardAlpha by rememberSkeletonPulse(periodMillis = 1100)
    val chipAlpha by rememberSkeletonPulse(periodMillis = 1100, staggerMillis = 220)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = featuredCardSidePadding())
            .aspectRatio(FEATURED_CARD_ASPECT_RATIO)
            .graphicsLayer { alpha = cardAlpha }
            .clip(RoundedCornerShape(FEATURED_CARD_CORNER_RADIUS))
            // Same elevatedPanel tone as the real card, so there's no tone shift when the photo arrives.
            .background(colors.elevatedPanel),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp)
                .width(84.dp)
                .height(14.dp)
                .graphicsLayer { alpha = chipAlpha }
                .clip(RoundedCornerShape(7.dp))
                .background(colors.mutedDim),
        )
    }
}

/** One avatar-row placeholder. [staggerIndex] delays its pulse (see [rememberSkeletonPulse]) so
 * the row ripples left to right instead of breathing in unison. A faint ring (like the real
 * avatar's) and a touch of scale on the same pulse add shape to a flat fill, still with no
 * gradient or glow. */
@Composable
private fun SkeletonAvatar(staggerIndex: Int) {
    val colors = EmberTheme.colors
    val alpha by rememberSkeletonPulse(periodMillis = 760, staggerMillis = staggerIndex * 110)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .graphicsLayer {
                    val scale = 0.94f + alpha * 0.06f
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(CircleShape)
                .border(1.5.dp, colors.border, CircleShape)
                .background(colors.panel),
        )
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .width(36.dp)
                .height(8.dp)
                .graphicsLayer { this.alpha = alpha }
                .clip(RoundedCornerShape(4.dp))
                .background(colors.panel),
        )
    }
}
