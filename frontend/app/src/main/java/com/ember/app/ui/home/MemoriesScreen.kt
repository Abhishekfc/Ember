package com.ember.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ember.app.data.remote.dto.MemoryPhotoDto
import com.ember.app.ui.theme.EmberTheme
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Calendar-style column count — a week per row, so a month reads as a compact block (~5 rows)
 * rather than a handful of oversized tiles sprawling down the whole screen. */
private const val GRID_COLUMNS = 7

/** Every month is padded (see [MemoryGridCell.Filler]) to exactly this many rows — the longest
 * possible month (31 days) plus the current month's own "add a photo" tile is 32 cells, which
 * already needs 5 rows, so this comfortably covers every month without ever needing 6. Fixed
 * across every month deliberately: without this, a 28-day month rendered 4 rows tall and a
 * 31-day one 5 rows tall, so switching between them resized the grid and shifted Home's own
 * scroll content around it — the whole point of padding to a constant row count is that
 * switching months never changes this composable's total height, so Home's scroll position
 * never moves out from under whoever's looking at it. */
private const val GRID_ROWS = 5

internal sealed interface MemoryGridCell {
    object AddTile : MemoryGridCell
    object Filler : MemoryGridCell
    data class EmptyDay(val date: LocalDate) : MemoryGridCell
    data class DayGroup(val date: LocalDate, val photos: List<MemoryPhotoDto>) : MemoryGridCell
}

/** One photo within a month's flattened carousel, in the same order the grid itself reads (see
 * [buildMonthCarousel]) — swiping continues from one day into the next within the same month,
 * the same way Home's featured card continues from one friend into the next, rather than
 * stopping dead at the tapped day's own boundary. Internal (not private) since [DayFocusTarget]
 * — now internal so HomeScreen can render [DayFeaturedOverlay] itself — exposes a list of these. */
internal data class MonthCarouselEntry(
    val day: LocalDate,
    val photo: MemoryPhotoDto,
    val indexWithinDay: Int,
    val totalForDay: Int,
)

/** Flattens every [MemoryGridCell.DayGroup] in [cells] (skipping [MemoryGridCell.AddTile]/
 * [MemoryGridCell.EmptyDay], which have no photos) into one ordered list, in the exact order the
 * grid itself lays them out — ascending day-of-month for the current month, most-recent-first
 * for past months, whatever that month's own `cells` construction already decided. */
private fun buildMonthCarousel(cells: List<MemoryGridCell>): List<MonthCarouselEntry> =
    cells.filterIsInstance<MemoryGridCell.DayGroup>().flatMap { group ->
        group.photos.mapIndexed { index, photo ->
            MonthCarouselEntry(day = group.date, photo = photo, indexWithinDay = index, totalForDay = group.photos.size)
        }
    }

/** A tapped day, remembered together with the on-screen bounds of the grid tile that was tapped
 * (the origin the featured card grows out of, and shrinks back into on dismiss), the tapped
 * month's full flattened photo list, and which page within it corresponds to the day actually
 * tapped. Internal (not private) — [DayFeaturedOverlay] now renders from HomeScreen's own
 * top-level Box (see [DayFocusState]), not from inside this file's [MemoriesGridContent]. */
internal data class DayFocusTarget(
    val day: MemoryGridCell.DayGroup,
    val originBounds: Rect,
    val monthEntries: List<MonthCarouselEntry>,
    val initialPage: Int,
)

/** Hoisted out of [MemoriesGridContent] so the actual [DayFeaturedOverlay] can be rendered by
 * HomeScreen at its own true full-screen Box instead — that Box sits inside Home's scrollable
 * Column, which (like any scrollable container) measures its children with an unbounded height
 * constraint, so a "centered against the full device screen" overlay rendered there was
 * positioning itself against a container that was never actually the full screen to begin with.
 * Rendering the overlay from Home's real, bounded-size outer Box instead sidesteps that entirely,
 * rather than trying to patch the coordinate math further. */
internal class DayFocusState {
    var target by mutableStateOf<DayFocusTarget?>(null)
    var isOpen by mutableStateOf(false)
    val progress = Animatable(0f)
}

@Composable
internal fun rememberDayFocusState(): DayFocusState = remember { DayFocusState() }

/** A month-grouped photo grid, one box per *day* rather than per photo — several photos from the
 * same day collapse into a single tile (most recent as the thumbnail, a small count badge if
 * there's more than one); tapping grows that tile into a full featured card, the same
 * "featured, everything else recedes" feel Home's own card uses, rather than a separate tile per
 * photo. Shows exactly one month at a time (see [HomeViewModel.selectedMonth]) instead of every
 * month stacked in one long scroll — prev/next arrows next to the month header (see
 * [onPreviousMonth]/[onNextMonth]) browse history instead. Always a *complete* calendar, an empty
 * placeholder box for every day that has nothing, whether or not this is the real current month
 * — the "add a photo" tile is the one thing still specific to the current month, since there's
 * no "add a photo to a past month" action an empty box there could invite. [onFocusChanged] lets
 * a caller blur its OWN surrounding chrome (a header above this grid, say) in lockstep with this
 * grid's internal blur, since this composable can only blur content inside itself.
 *
 * A plain [Column], not a [androidx.compose.foundation.lazy.LazyColumn] — this is always
 * embedded inside Home's own single scrollable Column (see HomeScreen), and Compose doesn't
 * allow an unbounded-height lazy list nested inside another scrollable container (the "vertically
 * scrollable component measured with an infinity maximum height constraint" crash). One month is
 * small enough (~5-6 rows) that composing it eagerly instead of virtualizing is a non-issue. */
@Composable
internal fun MemoriesGridContent(
    memories: List<MemoryPhotoDto>,
    selectedMonth: YearMonth,
    onCameraClick: () -> Unit,
    focusState: DayFocusState,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    canGoToPreviousMonth: Boolean = true,
    canGoToNextMonth: Boolean = true,
    isLoadingMonth: Boolean = false,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography

    val isOpen = focusState.isOpen
    val progress = focusState.progress

    LaunchedEffect(isOpen) {
        onFocusChanged(isOpen)
        if (isOpen) {
            progress.animateTo(1f, animationSpec = tween(320, easing = FastOutSlowInEasing))
        } else {
            progress.animateTo(0f, animationSpec = tween(220, easing = FastOutSlowInEasing))
            focusState.target = null
        }
    }

    // Swiping back (or pressing back) while a day is open should close the featured card, the
    // same way tapping outside it does — not fall through to the system and back out of the app.
    BackHandler(enabled = isOpen) { focusState.isOpen = false }

    val gridBlur by rememberFocusBlur(isOpen)

    val isCurrentMonth = selectedMonth == YearMonth.now()

    // Memoized rather than recomputed on every recomposition.
    val byDay: Map<LocalDate, List<MemoryPhotoDto>> = remember(memories) {
        memories
            .mapNotNull { memory ->
                val date = runCatching {
                    Instant.parse(memory.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                }.getOrNull()
                date?.let { it to memory }
            }
            .groupBy({ it.first }, { it.second })
    }

    val cells: List<MemoryGridCell> = remember(byDay, selectedMonth) {
        val today = LocalDate.now()
        val dayCells = (1..selectedMonth.lengthOfMonth()).map { day ->
            val date = selectedMonth.atDay(day)
            val photosForDay = byDay[date]
            when {
                photosForDay != null -> MemoryGridCell.DayGroup(date, photosForDay)
                // The "add a photo" tile sits on today's own square in the calendar now, not as
                // a leading tile prepended before day 1 — it only takes over today's spot while
                // nothing's been posted there yet; once it has, today reads like any other day.
                isCurrentMonth && date == today -> MemoryGridCell.AddTile
                else -> MemoryGridCell.EmptyDay(date)
            }
        }
        val targetCount = GRID_ROWS * GRID_COLUMNS
        dayCells + List((targetCount - dayCells.size).coerceAtLeast(0)) { MemoryGridCell.Filler }
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
                .blur(gridBlur, BlurredEdgeTreatment.Unbounded),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MonthNavArrow(pointsLeft = true, enabled = canGoToPreviousMonth, onClick = onPreviousMonth)
                    Text(
                        text = "${selectedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${selectedMonth.year}",
                        fontFamily = typography.body,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = colors.cream,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    MonthNavArrow(pointsLeft = false, enabled = canGoToNextMonth, onClick = onNextMonth)
                }
                if (memories.isNotEmpty()) {
                    Text(
                        text = "${memories.size} photo${if (memories.size == 1) "" else "s"}",
                        fontFamily = typography.body,
                        fontSize = 12.sp,
                        color = colors.mutedDim,
                    )
                }
            }

            if (isLoadingMonth) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.glow, modifier = Modifier.size(20.dp))
                }
            } else {
                cells.chunked(GRID_COLUMNS).forEach { rowCells ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        rowCells.forEach { cell ->
                            val isFocused = cell is MemoryGridCell.DayGroup && focusState.target?.day?.date == cell.date
                            MemoryCell(
                                cell = cell,
                                isFocused = isFocused,
                                onCameraClick = onCameraClick,
                                onDayClick = { group, bounds ->
                                    val monthEntries = buildMonthCarousel(cells)
                                    val initialPage = monthEntries.indexOfFirst { it.day == group.date }.coerceAtLeast(0)
                                    focusState.target = DayFocusTarget(group, bounds, monthEntries, initialPage)
                                    focusState.isOpen = true
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(GRID_COLUMNS - rowCells.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthNavArrow(pointsLeft: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = EmberTheme.colors
    Icon(
        imageVector = if (pointsLeft) Icons.Filled.KeyboardArrowLeft else Icons.Filled.KeyboardArrowRight,
        contentDescription = if (pointsLeft) "Previous month" else "Next month",
        tint = if (enabled) colors.cream else colors.mutedDim.copy(alpha = 0.4f),
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
private fun MemoryCell(
    cell: MemoryGridCell,
    onCameraClick: () -> Unit,
    onDayClick: (MemoryGridCell.DayGroup, Rect) -> Unit,
    modifier: Modifier = Modifier,
    isFocused: Boolean = false,
) {
    val colors = EmberTheme.colors
    val shape = RoundedCornerShape(10.dp)

    when (cell) {
        // Purely a spacer keeping every month's grid the same fixed row count (see GRID_ROWS) —
        // no border/background, unlike EmptyDay, since it isn't representing a real day at all.
        is MemoryGridCell.Filler -> Box(modifier = modifier.aspectRatio(0.85f))

        is MemoryGridCell.AddTile -> Box(
            modifier = modifier
                .aspectRatio(0.85f)
                .drawBehind {
                    drawRoundRect(
                        color = colors.glow.copy(alpha = 0.55f),
                        style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                    )
                }
                .clip(shape)
                .clickable(onClick = onCameraClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Take a photo", tint = colors.glow, modifier = Modifier.size(14.dp))
        }

        // A day with nothing posted reads as a quiet placeholder dot, not an outlined box
        // pretending to be a photo slot — only today (see MemoryGridCell.AddTile above) is
        // actually actionable, so every other empty day shouldn't compete visually with it.
        is MemoryGridCell.EmptyDay -> Box(
            modifier = modifier.aspectRatio(0.85f),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.border),
            )
        }

        is MemoryGridCell.DayGroup -> {
            var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
            Box(
                modifier = modifier
                    .aspectRatio(0.85f)
                    .onGloballyPositioned { coordinates = it }
                    .clip(shape)
                    .background(colors.panel)
                    .border(1.dp, colors.border, shape)
                    .clickable { coordinates?.let { onDayClick(cell, it.boundsInRoot()) } },
            ) {
                AsyncImage(
                    model = cell.photos.first().photoUrl,
                    contentDescription = "Your photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (cell.photos.size > 1) {
                    AnimatedVisibility(
                        visible = !isFocused,
                        modifier = Modifier.align(Alignment.BottomEnd),
                        enter = fadeIn(tween(200)),
                        exit = fadeOut(tween(150)),
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        ) {
                            Text(
                                text = "${cell.photos.size}",
                                fontFamily = EmberTheme.typography.body,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A day's photos, grown out of the grid tile that was tapped into the exact same card recipe
 * Home's own [FeaturedPhotoCard] uses — rounded glow-shadowed card, bottom gradient scrim, top
 * dot-row when there's more than one photo that day — via a container-transform: the card
 * animates from the tapped tile's actual on-screen position/size ([DayFocusTarget.originBounds])
 * to a centered full-width position, and shrinks back into that same spot on dismiss, rather than
 * a flat full-screen takeover. Rendered by the caller (HomeScreen) at its own true full-screen
 * Box — [DayFocusTarget.originBounds] is already captured via `boundsInRoot()`, i.e. relative to
 * the same compose root that Box sits at, so it needs no further coordinate conversion here.
 * [screenSize] is that same Box's real measured size in px (see HomeScreen's own `onSizeChanged`),
 * used to center the destination within the safe area below the status bar and above the floating
 * nav dock. */
@Composable
internal fun DayFeaturedOverlay(
    target: DayFocusTarget,
    screenSize: Size,
    progress: Float,
    onDismiss: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val density = LocalDensity.current
    val context = LocalContext.current
    // Pages through the whole month's flattened photo list (see buildMonthCarousel), not just
    // the tapped day's own photos — landing on the tapped day's first photo, but continuing into
    // neighboring days instead of stopping dead at that one day's boundary.
    val pagerState = rememberPagerState(initialPage = target.initialPage) { target.monthEntries.size }
    // Whichever entry the pager is actually settled/settling on right now — drives the date
    // label and per-day dot-row, so both track wherever swiping has actually taken you rather
    // than staying frozen on the originally-tapped day.
    val currentEntry = target.monthEntries.getOrElse(pagerState.currentPage) { target.monthEntries[target.initialPage] }

    // Same reasoning as Home's FeaturedPhotoCard: this pager and whatever's behind it (the
    // Memories grid's own scroll, and beyond that Home's outer scroll/pager) are both potential
    // recipients of a drag that runs out of pages to turn — without this, swiping past the first/
    // last photo in the month lets that drag bubble down into the grid or the page underneath
    // instead of just stopping. Consuming every bit of leftover scroll/fling here (onPost*, not
    // onPre* — the pager itself still scrolls normally first) means nothing from a drag that
    // starts on this card is ever left for whatever's behind it to receive.
    val cardNestedScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Centered within the SAFE usable area of the true screen (below the status bar, above
        // the floating nav dock) — this Box is HomeScreen's own top-level Box, which has a real,
        // bounded size (screenSize), not a container nested inside Home's scrollable column
        // (which measures its children with an unbounded height, making "center against the
        // full screen" from in there meaningless). No root-position subtraction is needed either:
        // originBounds and this Box both live at the same compose root, so originBounds is
        // already in the right coordinate space as-is.
        val sidePaddingPx = with(density) { FEATURED_CARD_SIDE_PADDING.toPx() }
        val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
        // True edge-to-edge means screenSize now includes the real gesture-bar strip at the
        // bottom too (previously the system reserved that space outside the window, so this
        // term didn't exist) — NAV_DOCK_RESERVE_DP alone no longer accounts for it.
        val navigationBarPx = WindowInsets.navigationBars.getBottom(density).toFloat()
        val navDockReservePx = with(density) { NAV_DOCK_RESERVE_DP.dp.toPx() }
        val usableHeightPx = (screenSize.height - statusBarPx - navigationBarPx - navDockReservePx).coerceAtLeast(1f)
        val cardWidthPx = (screenSize.width - sidePaddingPx * 2).coerceAtLeast(1f)
        val cardHeightPx = cardWidthPx / FEATURED_CARD_ASPECT_RATIO
        val destLeft = sidePaddingPx
        val destTop = statusBarPx + (usableHeightPx - cardHeightPx) / 2f
        val destRect = Rect(destLeft, destTop, destLeft + cardWidthPx, destTop + cardHeightPx)

        val currentRect = lerp(target.originBounds, destRect, progress)

        // The grid tile underneath uses 10.dp corners; the fully-open card uses Home's own
        // FEATURED_CARD_CORNER_RADIUS. Interpolating this, instead of jumping straight to the
        // open card's fixed radius, is what makes the swap back to the real tile underneath (once
        // this overlay is removed at the very end of the close animation) seamless instead of a
        // visible pop. (A matching animated border was tried here too, but a hard 1px edge
        // amplifies sub-pixel rounding in the animated size/offset far more than the photo itself
        // does, and reintroduced the jitter it was meant to fix.)
        val cardCornerRadius = lerp(10.dp, FEATURED_CARD_CORNER_RADIUS, progress)
        val cardShape = RoundedCornerShape(cardCornerRadius)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f * progress))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Box(
            modifier = Modifier
                // A separate .offset{} (raw pixels) + .size(dp, dp) (converted to dp, then back to
                // pixels by the layout system) round independently of each other every frame — at
                // the tiny end of the shrink-back-into-the-grid animation, that mismatch is a much
                // bigger fraction of the box's size and reads as a jitter. Measuring and placing in
                // one pass, in raw pixels throughout, rounds exactly once, consistently.
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
                .background(colors.panel)
                // A plain tap anywhere on the open photo closes it, matching Home's own featured
                // card (there, tapping the card is exactly what toggles it back out of focus) —
                // this used to be a deliberate no-op, which read as the photo just not responding.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val photo = target.monthEntries[page].photo
                AsyncImage(
                    // The same photo is already showing small in the grid tile this card grew
                    // out of, and this Box's own size keeps animating during the grow/shrink
                    // transition — pin the decode to the card's final, settled pixel size rather
                    // than Coil's default (which follows the composable's size and would end up
                    // re-requesting on every animation frame, or reusing the tiny grid decode).
                    // Fixed to the destination size, not the native photo resolution, so this
                    // doesn't decode a full multi-megapixel original just to show it at ~950px.
                    model = remember(photo.photoUrl, cardWidthPx, cardHeightPx) {
                        ImageRequest.Builder(context)
                            .data(photo.photoUrl)
                            .size(cardWidthPx.roundToInt(), cardHeightPx.roundToInt())
                            .build()
                    },
                    contentDescription = "Your photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The real grid tile has no bottom scrim at all — fading this in/out with
                    // progress, the same way the dot-row and date text below already do, keeps it
                    // absent at the tile-sized end of the transition instead of a full-strength
                    // gradient popping in against a tile that never had one.
                    .alpha(progress)
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
            )

            // Per-day photo count, same as before flattening to a month-wide carousel — how many
            // *that specific day* has and which one's showing, not a position in the whole month.
            if (currentEntry.totalForDay > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .alpha(progress),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    repeat(currentEntry.totalForDay) { index ->
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (index == currentEntry.indexWithinDay) Color.White else Color.White.copy(alpha = 0.35f)),
                        )
                    }
                }
            }

            Text(
                text = "${currentEntry.day.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentEntry.day.dayOfMonth}",
                fontFamily = typography.display,
                fontSize = 24.sp,
                color = Color(0xFFFBF8F3),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dp, bottom = 22.dp)
                    .alpha(progress),
            )
        }
    }
}
