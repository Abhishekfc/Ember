package com.emigo.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import com.emigo.app.R
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.ThemeKey

// Must match FriendAvatarRow's avatar column width and spacing: they can't be read from the
// LazyRow until its items are visible, which is the problem with the built-in animateScrollToItem
// this replaces. Avatar diameters and column width live in HomeFoldMetrics (see FeaturedCardStyle)
// because they scale with the fold: a stock 360dp phone can't fit a full-width card beside 90dp
// avatars, and the card was silently shrinking. HomeFoldRoomy holds the old fixed values.
//
// The column width must match what the row renders, since smoothCenterOn derives its scroll target
// from it (it was once 72 while the column measured 84, centering every avatar off). Both now read
// the same HomeFoldMetrics.

/** The ring band and the gap between it and the photo. Equal by design — see their use site. */
private const val AVATAR_RING_WIDTH_DP = 3.0f
private const val AVATAR_RING_GAP_DP = 3.0f

/** The horizontal gap between avatars. */
internal const val AVATAR_SPACING_DP = 4

/** Smooth-scrolls to center [targetIndex], replacing `LazyListState.animateScrollToItem`: that uses
 * an un-tunable spring and, for targets outside the current layout, snaps most of the way before
 * animating the rest (a jump-cut). Every avatar has the same fixed width, so the exact pixel delta
 * can be computed without the item being measured, and a fixed tween plays the same curve however
 * far it travels. */
internal suspend fun LazyListState.smoothCenterOn(targetIndex: Int, itemStridePx: Float, itemWidthPx: Float) {
    val viewportPx = layoutInfo.viewportSize.width.toFloat()
    if (viewportPx <= 0f) return
    val currentPx = firstVisibleItemIndex * itemStridePx + firstVisibleItemScrollOffset
    val targetPx = (targetIndex * itemStridePx + itemWidthPx / 2f) - viewportPx / 2f
    val deltaPx = targetPx - currentPx
    if (kotlin.math.abs(deltaPx) < 0.5f) return

    var applied = 0f
    scroll {
        Animatable(0f).animateTo(deltaPx, tween(320, easing = FastOutSlowInEasing)) {
            scrollBy(value - applied)
            applied = value
        }
    }
}

/** Horizontally scrollable friend avatars with names: the active friend is slightly larger,
 * friends with an unseen latest photo get a glow ring + glow dot, seen ones a muted ring +
 * gray dot. Ends with an Add button that opens Find People. */
@Composable
internal fun FriendAvatarRow(
    viewModel: HomeViewModel,
    activeFriendId: String,
    listState: LazyListState,
    onAvatarClick: (String) -> Unit,
    onAddFriendClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    // The fold's scale (see homeFoldMetricsFor); avatars shrink with the gaps on a device that
    // can't otherwise fit a full-width card.
    val foldMetrics = LocalHomeFoldMetrics.current
    // FeedItem has no profile photo field (see HomeViewModel.friends); this lookup supplies it.
    val profilePhotoByFriendId = remember(viewModel.friends) {
        viewModel.friends.associateBy({ it.friendId }, { it.profilePhotoUrl })
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(AVATAR_SPACING_DP.dp),
        verticalAlignment = Alignment.Top,
    ) {
        items(viewModel.feedItems, key = { it.friendId }) { item ->
            val isActive = item.friendId == activeFriendId
            val hasUnseen = viewModel.hasUnseenPhoto(item)
            // Drives a crossfade between the ring's two looks (see the layered Boxes below):
            // .background(brush) can't animate between Brushes, so the glow sweep gradient is its
            // own layer over an always-present muted base, faded in and out. Same duration and
            // easing as the centering scroll (smoothCenterOn) so resize and reposition read as
            // one move.
            val avatarSize by animateDpAsState(
                targetValue = if (isActive) foldMetrics.avatarDiameter else foldMetrics.avatarInactiveDiameter,
                animationSpec = tween(320, easing = FastOutSlowInEasing),
                label = "avatarSize",
            )
            val unseenAlpha by animateFloatAsState(
                targetValue = if (hasUnseen) 1f else 0f,
                animationSpec = tween(100, easing = FastOutSlowInEasing),
                label = "avatarRingUnseenAlpha",
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(foldMetrics.avatarItemWidth),
            ) {
                Box(modifier = Modifier.size(foldMetrics.avatarDiameter), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(avatarSize)) {
                        // Muted ring, always present as the base for the glow layer to fade over.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(colors.mutedDim, colors.mutedDim))),
                        )
                        // Glow ring on top, faded by unseenAlpha so "just became seen" settles instead
                        // of flickering. Citrus's glow2 and violet are both a muddy orange (see its
                        // theme), so the usual sweep looked like a smeared ring there; a solid
                        // yellow reads better than forcing it through the gradient the other
                        // themes' more distinct trio suits.
                        val streakRingBrush = if (EmberTheme.key == ThemeKey.CITRUS) {
                            Brush.linearGradient(listOf(colors.glow, colors.glow))
                        } else {
                            Brush.sweepGradient(listOf(colors.glow, colors.glow2, colors.violet, colors.glow))
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .graphicsLayer { alpha = unseenAlpha }
                                .clip(CircleShape)
                                .background(streakRingBrush),
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                // A thin ring plus an equally thin gap (the story-ring proportion): the
                                // photo fills most of the circle and the ring reads as a delicate
                                // band. Equal on purpose; an uneven pair looks like a mistake here.
                                .padding(AVATAR_RING_WIDTH_DP.dp)
                                .clip(CircleShape)
                                .background(colors.panel)
                                .padding(AVATAR_RING_GAP_DP.dp)
                                .clip(CircleShape)
                                .clickable { onAvatarClick(item.friendId) },
                        ) {
                            // The friend's profile photo, not item.photos.last() (their latest sent
                            // photo). Showing whatever they just shared (a screenshot, a receipt)
                            // made the ring change identity with their feed instead of being a
                            // stable "this is them" marker.
                            val profilePhotoUrl = profilePhotoByFriendId[item.friendId]
                            if (profilePhotoUrl != null) {
                                // Same as the featured card's photo: plain AsyncImage paints nothing
                                // while loading, leaving the flat panel background with no sign
                                // anything is happening. Tracking the painter's state lets this
                                // pulse like SkeletonAvatar does for the whole-screen loader.
                                val avatarPainter = rememberAsyncImagePainter(model = profilePhotoUrl)
                                val avatarPainterState by avatarPainter.state.collectAsState()
                                val isAvatarLoading = when (avatarPainterState) {
                                    is AsyncImagePainter.State.Loading, is AsyncImagePainter.State.Empty -> true
                                    else -> false
                                }
                                if (isAvatarLoading) {
                                    val avatarPulseAlpha by rememberSkeletonPulse(periodMillis = 900)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = avatarPulseAlpha }
                                            .background(colors.panel),
                                    )
                                }
                                Image(
                                    painter = avatarPainter,
                                    contentDescription = item.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                                )
                            } else {
                                // No profile photo: the first-initial fallback used elsewhere
                                // (ProfileIconButton, RecipientAvatarStack), not their content photo.
                                Box(modifier = Modifier.fillMaxSize().background(colors.panel), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = item.displayName.firstOrNull()?.uppercase() ?: "•",
                                        fontFamily = typography.display,
                                        fontSize = 19.sp,
                                        color = colors.cream,
                                    )
                                }
                            }
                        }
                    }
                }
                // The streak shows on the featured card, not here; just the name below each avatar.
                Text(
                    text = item.displayName.substringBefore(" "),
                    fontFamily = typography.body,
                    fontSize = 12.sp,
                    color = if (isActive) colors.cream else colors.muted,
                    maxLines = 1,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }

        item(key = "add-friend") {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(foldMetrics.avatarItemWidth),
            ) {
                val dashColor = colors.mutedDim
                Box(modifier = Modifier.size(foldMetrics.avatarDiameter), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(foldMetrics.avatarInactiveDiameter)
                            .drawBehind {
                                drawCircle(
                                    color = dashColor,
                                    style = Stroke(
                                        width = 1.5.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                                    ),
                                )
                            }
                            .clip(CircleShape)
                            .clickable(onClick = onAddFriendClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.home_add_friend_description),
                            tint = colors.muted,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.common_add),
                    fontFamily = typography.body,
                    fontSize = 12.sp,
                    color = colors.muted,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
        }
    }
}
