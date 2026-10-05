package com.emigo.app.ui.home

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.emigo.app.R
import com.emigo.app.data.remote.dto.FeedItem
import com.emigo.app.data.remote.dto.PhotoEntryDto
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.EmberTypography
import com.emigo.app.ui.theme.PublicSansFontFamily
import kotlin.math.roundToInt

/** A grid alternative to the carousel: one card per friend (latest photo and name), two per row.
 * Tapping one opens it as a featured overlay (see [MomentFocusState], [MomentFeaturedOverlay]). */
@Composable
internal fun MomentsGrid(
    feedItems: List<FeedItem>,
    onCardClick: (FeedItem, Rect) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    // The card the featured overlay grows out of, and how open it is (0 closed, 1 open). See
    // MomentGridCard for why that card's label needs it.
    focusedFriendId: String? = null,
    focusProgress: Float = 0f,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        gridItems(feedItems, key = { it.friendId }) { item ->
            MomentGridCard(
                displayName = item.displayName,
                photoUrl = item.photos.lastOrNull()?.photoUrl,
                onClick = { bounds -> onCardClick(item, bounds) },
                // Inverse of the overlay's opening progress, for the one card it grew out of only;
                // every other card stays at 1.
                revealAlpha = if (item.friendId == focusedFriendId) 1f - focusProgress else 1f,
            )
        }
    }
}

/** A smaller echo of the featured card (rounded corners, bottom scrim, white name) so it reads as
 * related, since tapping it grows into that card ([MomentFeaturedOverlay]). Uses elevatedPanel like
 * the featured card so it outranks the flat background. Reports its on-screen bounds on tap
 * (boundsInRoot, as the Memories tiles do) as the overlay's origin.
 *
 * [revealAlpha] fixes the label "popping in": this tile sits under the opaque overlay while it's
 * open, so its label was at full opacity but hidden, then suddenly visible once the overlay was
 * removed, and nothing could cross-fade it. The label now fades in across the handoff, driven by
 * the same progress as the overlay's fade-out, so the two meet in the middle. */
@Composable
private fun MomentGridCard(
    displayName: String,
    photoUrl: String?,
    onClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    revealAlpha: Float = 1f,
) {
    val colors = EmberTheme.colors
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    // No entrance fade: cards show immediately when the grid appears. revealAlpha only hides the
    // label of the one card an overlay is open on. The overlay draws its own copy (see
    // MomentCardContent) and cross-fades it, so keeping this one drawn would double it up and then
    // hand back to a label that never moved.
    val labelAlpha = revealAlpha
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
            .onGloballyPositioned { coordinates = it }
            .clip(RoundedCornerShape(EmberRadii.card))
            .background(colors.elevatedPanel)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { coordinates?.let { onClick(it.boundsInRoot()) } },
            ),
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .alpha(labelAlpha)
                .background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = displayName,
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

/** A tapped Moments card: the grid tile's on-screen bounds (where the overlay grows from and
 * shrinks back to), that friend's photos newest first, and the start page, always 0 since the tile
 * already shows the newest photo. */
internal data class MomentFocusTarget(
    val friendId: String,
    val displayName: String,
    val photos: List<PhotoEntryDto>,
    val initialPage: Int,
    val originBounds: Rect,
)

/** Same shape as Memories' [MemoryFocusState] (target, open flag, shared grow/shrink
 * [Animatable]), kept local because the two targets carry different data. */
internal class MomentFocusState {
    var target by mutableStateOf<MomentFocusTarget?>(null)
    var isOpen by mutableStateOf(false)
    val progress = Animatable(0f)
}

/** Just the photo. It goes inside [MomentFeaturedOverlay]'s pager, so anything here slides with
 * every swipe; the name and scrim belong to the card, so they live outside the pager
 * ([MomentCardLabels]), as in [FeaturedPhotoCard]. */
@Composable
private fun MomentPhotoImage(
    displayName: String,
    photo: PhotoEntryDto,
    cardWidthPx: Float,
    cardHeightPx: Float,
    context: Context,
    modifier: Modifier = Modifier,
) {
    AsyncImage(
        // Pinned to the card's final pixel size; Coil's default follows the animating size and
        // would re-request on every grow/shrink frame.
        model = remember(photo.photoUrl, cardWidthPx, cardHeightPx) {
            ImageRequest.Builder(context)
                .data(photo.photoUrl)
                .size(cardWidthPx.roundToInt(), cardHeightPx.roundToInt())
                .build()
        },
        contentDescription = displayName,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

/** The card's scrim and name, rendered once over the pager rather than per-page. */
@Composable
private fun MomentCardLabels(
    displayName: String,
    photo: PhotoEntryDto,
    // 1f = fully open, 0f = grid-tile size and position.
    progress: Float,
    // Card size relative to fully open (1f open, ~0.47f at tile size). Scales the open-style
    // label: its fixed 24sp name stayed the same size in a box shrinking to half width and ended
    // up looking enormous.
    contentScale: Float,
    typography: EmberTypography,
    modifier: Modifier = Modifier,
) {
    // Both label styles are rendered and cross-faded, instead of fading one to nothing. The
    // overlay is opaque, so the grid tile underneath (and its label) is hidden for the whole
    // transition and would appear at full strength the instant the overlay is removed. Fading the
    // open-style label out didn't help because the two differ (24sp display font plus a time line
    // vs the tile's 14sp name, different padding and gradients), so the swap stayed visible.
    // Ending on a label identical to the tile's makes removing the overlay a no-op.
    val openAlpha = progress
    val tileAlpha = 1f - progress

    Box(modifier = modifier.fillMaxSize()) {
        // The open card's full-height scrim.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(openAlpha)
                .background(
                    Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f)),
                ),
        )

        // The grid tile's own scrim: a short band at the bottom, different in shape and opacity
        // from the one above, hence the cross-fade instead of reusing it.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .alpha(tileAlpha)
                .background(
                    Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))),
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // Same size, font, weight and padding as MomentGridCard's label, so at progress 0 it
            // matches the tile exactly.
            Text(
                text = displayName,
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                // Anchored bottom-left so shrinking pulls it toward the tile's label corner. Before
                // padding() so the padding scales too; otherwise the text would shrink while its
                // inset stayed fixed.
                .graphicsLayer {
                    scaleX = contentScale
                    scaleY = contentScale
                    transformOrigin = TransformOrigin(0f, 1f)
                }
                .alpha(openAlpha)
                .padding(start = 22.dp, end = 22.dp, bottom = 20.dp),
        ) {
            Text(
                text = displayName,
                fontFamily = typography.display,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
            )
            Text(
                text = formatRelativeTime(photo.createdAt),
                fontFamily = typography.body,
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** A friend's photo grown out of the tapped Moments tile into the same card as [FeaturedPhotoCard]
 * and Memories' `MemoryFeaturedOverlay` (corner radius, aspect ratio, grow/shrink, bottom-scrim
 * name). Opening a moment stays inside Moments and dismisses back into its tile, reusing the app's
 * one "grow a tile into a card" pattern. */
@Composable
internal fun MomentFeaturedOverlay(
    target: MomentFocusTarget,
    destRect: Rect,
    progress: Float,
    onDismiss: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val context = LocalContext.current

    // Starts on the tapped photo; swiping continues into that friend's older photos.
    val pagerState = rememberPagerState(initialPage = target.initialPage) { target.photos.size }
    // The page the pager settled on; the label sits outside the pager, so it reads this.
    val currentPhoto = target.photos.getOrElse(pagerState.currentPage) { target.photos[target.initialPage] }

    // Same as FeaturedPhotoCard's boundary: a drag that runs out of pages would otherwise bubble
    // into the grid's scroll behind this pager.
    val cardNestedScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val cardWidthPx = destRect.width
        val cardHeightPx = destRect.height
        val currentRect = lerp(target.originBounds, destRect, progress)
        val cardCornerRadius = lerp(EmberRadii.card, FEATURED_CARD_CORNER_RADIUS, progress)
        val cardShape = RoundedCornerShape(cardCornerRadius)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Box(
            modifier = Modifier
                // One measure-and-place pass in raw pixels, not .offset{} plus .size(dp): rounding
                // each separately is a big fraction of the box at tile size and reads as jitter.
                .layout { measurable, _ ->
                    val widthPx = currentRect.width.roundToInt().coerceAtLeast(0)
                    val heightPx = currentRect.height.roundToInt().coerceAtLeast(0)
                    val placeable = measurable.measure(Constraints.fixed(widthPx, heightPx))
                    layout(widthPx, heightPx) {
                        placeable.placeRelative(currentRect.left.roundToInt(), currentRect.top.roundToInt())
                    }
                }
                .nestedScroll(cardNestedScrollBoundary)
                .clip(cardShape)
                .background(colors.elevatedPanel)
                // Tapping the open photo closes it, matching Home's own featured card.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            // Card size relative to fully open. Scales the label so the text shrinks with the
            // container instead of staying 24sp and then snapping to the tile's 14sp.
            val contentScale = if (destRect.width > 0f) currentRect.width / destRect.width else 1f


            // The grid tile always shows this friend's NEWEST photo (target.photos[0]). If you swiped
            // to an older one, the pager stays on it until the overlay is removed, and the tile then
            // replaces it suddenly (a hard swap, not a shrink). So this crossfades to the newest
            // photo's image, scrim and label during the shrink, and it already matches the tile when
            // removed. Only rendered after swiping away from the newest photo.
            val isOnNewestPhoto = pagerState.currentPage == 0
            val closingCrossfadeAlpha = if (isOnNewestPhoto) 0f else (1f - progress) * (1f - progress)

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                MomentPhotoImage(
                    displayName = target.displayName,
                    photo = target.photos[page],
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    context = context,
                )
            }

            if (closingCrossfadeAlpha > 0f) {
                MomentPhotoImage(
                    displayName = target.displayName,
                    photo = target.photos[0],
                    cardWidthPx = cardWidthPx,
                    cardHeightPx = cardHeightPx,
                    context = context,
                    modifier = Modifier.alpha(closingCrossfadeAlpha),
                )
            }

            // Outside the pager: the name and scrim belong to the card, not one photo, so they
            // stay put while photos slide (as in FeaturedPhotoCard).
            MomentCardLabels(
                displayName = target.displayName,
                photo = currentPhoto,
                progress = progress,
                contentScale = contentScale,
                typography = typography,
            )
        }
    }
}

/** Moments with nothing in it yet: four empty cards in the grid's two-column layout, with the real
 * cards' corners and surface, so the pill visibly switches to a different view instead of
 * repeating Home's card. Each placeholder has the name strip, which makes them read as friend
 * cards. */
@Composable
internal fun MomentsEmptyState(modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            repeat(2) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.8f)
                                .clip(RoundedCornerShape(EmberRadii.card))
                                .background(colors.panel),
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                                    .width(52.dp)
                                    .height(9.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(colors.mutedDim.copy(alpha = 0.45f)),
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.home_card_for_every_friend),
            fontFamily = typography.body,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.cream,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        )
        Text(
            // Says what will be here, not what to do with it; "tap any card" would instruct on
            // cards that don't exist yet.
            text = stringResource(R.string.home_card_once_sharing),
            fontFamily = typography.body,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, start = 16.dp, end = 16.dp),
        )
    }
}
