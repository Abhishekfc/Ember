package com.ember.app.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ember.app.R
import com.ember.app.data.remote.dto.MemoryPhotoDto
import com.ember.app.ui.components.LocalNavDockHeight
import com.ember.app.ui.profile.EditDialogShell
import com.ember.app.ui.theme.EmberRadii
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Calendar-style column count — a week per row, so a month reads as a compact block (~5 rows)
 * rather than a handful of oversized tiles sprawling down the whole screen. */
private const val GRID_COLUMNS = 7

/** Corner radius of the card wrapping the month header + grid together. */
private val MEMORIES_CARD_SHAPE = RoundedCornerShape(22.dp)

/** Every month is padded (see [MemoryGridCell.Filler]) to exactly this many rows — since day 1 now
 * lands in its own real weekday column (leading blanks before it, see [buildDayCells]) rather than
 * always starting at column 0, the worst case is a 31-day month whose 1st falls on a Saturday: 6
 * leading blanks + 31 days = 37 cells, which needs 6 rows. Fixed across every month deliberately:
 * without this, a short month rendered fewer rows tall than a long one, so switching between them
 * resized the grid and shifted Home's own scroll content around it — the whole point of padding
 * to a constant row count is that switching months never changes this composable's total height,
 * so Home's scroll position never moves out from under whoever's looking at it. */
private const val GRID_ROWS = 6

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
    val density = LocalDensity.current
    // Set the first time the real grid is ever measured (see its own onGloballyPositioned
    // below) and reused for every loading spinner after that, so switching a not-yet-visited
    // month in never changes this card's height — see that Column's own doc comment.
    var measuredGridHeight by remember { mutableStateOf<Dp?>(null) }

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
    // Blur alone left month-header text and grid-tile photos faintly legible behind the opened
    // day-card, once that card started sitting on top of AmbientPhotoBackdrop's much busier wash
    // instead of the flat background — same fix, same reasoning, as HomeScreen's own chromeFade.
    val gridFade by rememberFocusFade(isOpen)

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
        // Blank cells before day 1 so it lands in its own real weekday column — Sunday first,
        // matching a real wall calendar — rather than always starting at the grid's leftmost
        // column regardless of what day the month actually begins on. DayOfWeek.value runs
        // Monday=1..Sunday=7; `% 7` turns that into Sunday=0..Saturday=6, which is exactly how
        // many empty columns should precede day 1 for each possible starting weekday.
        val leadingBlanks = selectedMonth.atDay(1).dayOfWeek.value % 7
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
        val allCells = List(leadingBlanks) { MemoryGridCell.Filler } + dayCells
        val targetCount = GRID_ROWS * GRID_COLUMNS
        allCells + List((targetCount - allCells.size).coerceAtLeast(0)) { MemoryGridCell.Filler }
    }

    // A Box, not just the Column below — the info popover (see showInfo further down) needs
    // to float on top of the grid without changing this card's own height, which a sibling
    // occupying normal Column flow can't do (that was the previous, rejected version: the
    // card visibly grew every time the popover opened). A Box's non-Column children draw on
    // top of the Column without contributing to its measured size at all.
    var showInfo by remember { mutableStateOf(false) }
    // This composable has no scroll state of its own to observe directly — it's just a fixed
    // section inside Home's outer scrollable Column. What it *can* observe is its own
    // position on screen, which that ancestor scrolling is exactly what changes. First report
    // only seeds the baseline (composing for the first time isn't a scroll); every report
    // after that whose Y actually moved means the page scrolled, and the popover — anchored
    // to a fixed spot in this card — would otherwise appear to drift along with it instead of
    // just closing the way tapping elsewhere already does.
    var lastKnownTopY by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier.onGloballyPositioned { coordinates ->
            val topY = coordinates.boundsInRoot().top
            val previous = lastKnownTopY
            if (previous != null && kotlin.math.abs(topY - previous) > 0.5f) {
                showInfo = false
            }
            lastKnownTopY = topY
        },
    ) {
        // One opaque card behind the whole section — month header and grid together. Without it
        // the grid sits directly on the screen background, which for an image-backed theme (see
        // EmberBackground.ImageBacked) means the artwork runs straight through every empty day
        // cell and the whole month reads as noise rather than a calendar. Being opaque is the
        // entire point: it stops the backdrop at its own edge.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp)
                .clip(MEMORIES_CARD_SHAPE)
                // The theme's own backdrop tone, not `surface` — surface is a derived mid-grey
                // that read as a dull slab sitting on top of the theme rather than part of it.
                // Using the backdrop's own base color keeps the card feeling native to whichever
                // theme is active, while still being fully opaque so an image backdrop stops here.
                .background(colors.background.baseColor())
                .border(1.dp, colors.border, MEMORIES_CARD_SHAPE)
                .blur(gridBlur, BlurredEdgeTreatment.Unbounded)
                .graphicsLayer { alpha = gridFade },
        ) {
        Column {
            // Header strip: one step up the tonal ladder from the grid body below it (panel is
            // lighter than surface), so the month row reads as its own band rather than floating
            // in the same field as the days — the distinction the reference design draws. Taken
            // from the ladder rather than a fixed color so it holds up across all eleven themes.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.panel)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                    MemoriesInfoButton(onClick = { showInfo = !showInfo })
                }
                if (memories.isNotEmpty()) {
                    Text(
                        text = "${memories.size} photo${if (memories.size == 1) "" else "s"}",
                        fontFamily = typography.body,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        // Full-strength text, not mutedDim — at 12sp on the header band, mutedDim
                        // was too faint to read. `cream` rather than a literal white: it's the
                        // theme's own primary text color, which is white/near-white in every dark
                        // theme and automatically flips dark on any light theme, where a
                        // hardcoded white would be invisible instead.
                        color = colors.cream,
                    )
                }
            }
            // A Box, not an if/else swap between the grid and a small centered spinner — that
            // swap used to change this card's height every time a not-yet-visited month was
            // opened for the first time (the spinner's own box is much shorter than a real
            // 6-row calendar), which shifted Home's whole scroll position out from under
            // whoever was mid-scroll when they tapped the arrow. Rather than showing the grid
            // itself underneath the spinner (tried, but looked wrong — an empty calendar with a
            // spinner floating over the top of it isn't the clean "loading…" moment the plain
            // spinner alone already was), the spinner's own box is pinned to the grid's real,
            // once-measured height instead. The grid is a fixed 6 rows regardless of month, and
            // each cell's height only depends on the card's width (a fixed aspect ratio) — which
            // doesn't change between months — so one measurement is valid for the rest of the
            // session, and swapping to the spinner never changes this card's own height again.
            if (isLoadingMonth) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(measuredGridHeight?.let { Modifier.height(it) } ?: Modifier.padding(vertical = 40.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.glow, modifier = Modifier.size(20.dp))
                }
            } else {
                // The grid keeps its own inset from the card's edges; the cells themselves are
                // untouched (same size, spacing, radius and per-state treatment as before).
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp)
                        .onGloballyPositioned { measuredGridHeight = with(density) { it.size.height.toDp() } },
                ) {
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

        // A real floating overlay this time, but positioned with a plain Modifier.align + fixed
        // padding rather than a DropdownMenu/Popup — those position themselves via the anchor's
        // coordinates *in the window*, which this card's own ancestors (several blur/graphicsLayer
        // wrappers up the tree) were throwing off. Alignment within this Box is resolved during
        // normal layout instead, immune to that. Drawn after the Column above, so it sits on top
        // of the grid without adding to this card's own height.
        AnimatedVisibility(
            visible = showInfo,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Invisible — its only job is to catch a tap on anything else (the month arrows,
                // a day in the grid, the info icon itself again) and close this, the same
                // tap-outside-to-dismiss behavior every menu/dialog elsewhere in the app already
                // has. Sits above the header/grid in the Box's own draw order, so it intercepts
                // the tap before whatever's underneath ever sees it — closing this is what that
                // first tap does, not also whatever button happened to be under it.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { showInfo = false },
                        ),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 50.dp, start = 14.dp, end = 14.dp)
                        .clip(EmberRadii.dialogShape)
                        .background(colors.overlayPanel)
                        .border(1.dp, colors.border, EmberRadii.dialogShape)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bookmark_filled),
                        contentDescription = null,
                        tint = colors.glow,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Only photos you save appear here.",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 13.sp,
                        color = colors.cream,
                        modifier = Modifier.padding(start = 10.dp),
                    )
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
        imageVector = if (pointsLeft) Icons.Rounded.ChevronLeft else Icons.Rounded.ChevronRight,
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

/** Explains why Memories no longer mirrors every photo ever sent — since only explicitly saved
 * photos show up here now, a first-time (or just-updated) user has no way to know that from the
 * grid alone. A quiet "i" next to the month arrows, not a banner or a dialog forced on anyone —
 * entirely optional to ever tap; see the call site for the floating explanation this toggles. */
@Composable
private fun MemoriesInfoButton(onClick: () -> Unit) {
    val colors = EmberTheme.colors
    Icon(
        painter = painterResource(R.drawable.ic_info),
        contentDescription = "About Memories",
        tint = colors.mutedDim,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(20.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(2.dp),
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
    // Softer, more generous rounding across every cell type (was 10dp) — the one deliberate
    // "redesign" change that applies uniformly, everything else below is either restored to its
    // original look (the past-day dot) or newly split out (future days).
    val shape = RoundedCornerShape(14.dp)

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
                        cornerRadius = CornerRadius(14.dp.toPx()),
                    )
                }
                .clip(shape)
                .clickable(onClick = onCameraClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Take a photo", tint = colors.glow, modifier = Modifier.size(14.dp))
        }

        // Split by whether the day has actually happened yet. A past day with nothing shared
        // keeps exactly its original quiet dot treatment. A future day gets the same outlined
        // square shape every photo cell uses — so the grid still reads as a uniform calendar,
        // not literally nothing — but at a fraction of colors.panel's full opacity: at full
        // strength, a month with only a few real photos is mostly empty future days, and filling
        // dozens of them with the same solid tone a real photo card uses made the whole grid read
        // as a heavy wall of grey instead of a quiet placeholder a few real photos stand out from.
        is MemoryGridCell.EmptyDay -> if (cell.date.isBefore(LocalDate.now())) {
            Box(modifier = modifier.aspectRatio(0.85f), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.border),
                )
            }
        } else {
            Box(
                modifier = modifier
                    .aspectRatio(0.85f)
                    .clip(shape)
                    .background(colors.panel.copy(alpha = 0.7f))
                    .border(1.dp, colors.border, shape),
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
    // Reports whichever photo scrolling has actually come to rest on, for HomeScreen's own
    // AmbientPhotoBackdrop (the same blurred-photo wash Home's own featured card uses) to pick
    // up — deliberately NOT wired to the live page the way currentEntry below is. See
    // HomeScreen's own identical isScrollInProgress effect for the fuller reasoning.
    onCurrentPhotoChanged: (String?) -> Unit = {},
    // Real, permanent deletion (see HomeViewModel.deleteMemoryPhoto/PhotoService.delete on the
    // backend) — a suspend Result rather than a fire-and-forget callback so the confirm dialog
    // below can show its own spinner/error inline instead of closing optimistically.
    onDeletePhoto: suspend (String) -> Result<Unit> = { Result.success(Unit) },
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val density = LocalDensity.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // Only actually needed on Android 9 and below — scoped storage on 10+ lets MediaStore.insert
    // write to the gallery with no permission at all (see saveImageToGallery's own doc comment).
    // The lambda captured here always re-attempts the same download once the user responds,
    // rather than needing a second explicit tap after granting.
    var pendingDownloadUrl by remember { mutableStateOf<String?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val url = pendingDownloadUrl
        pendingDownloadUrl = null
        if (granted && url != null) {
            coroutineScope.launch { downloadPhoto(context, url) }
        }
    }
    // Pages through the whole month's flattened photo list (see buildMonthCarousel), not just
    // the tapped day's own photos — landing on the tapped day's first photo, but continuing into
    // neighboring days instead of stopping dead at that one day's boundary.
    val pagerState = rememberPagerState(initialPage = target.initialPage) { target.monthEntries.size }
    // Whichever entry the pager is actually settled/settling on right now — drives the date
    // label and per-day dot-row, so both track wherever swiping has actually taken you rather
    // than staying frozen on the originally-tapped day.
    val currentEntry = target.monthEntries.getOrElse(pagerState.currentPage) { target.monthEntries[target.initialPage] }

    LaunchedEffect(target) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { isScrolling ->
                if (!isScrolling) {
                    onCurrentPhotoChanged(target.monthEntries.getOrNull(pagerState.currentPage)?.photo?.photoUrl)
                }
            }
    }

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
        // LocalNavDockHeight is the dock's own real measured height, which already includes the
        // true system nav-bar inset (BottomNavDock applies navigationBarsPadding() internally) —
        // no separate WindowInsets.navigationBars term needed on top of it any more.
        val navDockReservePx = with(density) { LocalNavDockHeight.current.toPx() }
        val usableHeightPx = (screenSize.height - statusBarPx - navDockReservePx).coerceAtLeast(1f)
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
                // Matches Home's own FeaturedPhotoCard, which this is explicitly "the same
                // recipe" as — elevated, not plain panel, so it outranks the grid tiles behind it.
                .background(colors.elevatedPanel)
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
                // Same rule Home's own featured card uses (see its own doc comment): stays at
                // FEATURED_CARD_DOT_WIDTH each as long as they fit within the card's own
                // FEATURED_CARD_SIDE_PADDING inset, otherwise all shrink together rather than
                // running past the card's rounded edges — this card uses the exact same shared
                // recipe (cardWidthPx above), so it had the exact same overflow bug.
                val dotCount = currentEntry.totalForDay
                val cardWidthDp = with(density) { cardWidthPx.toDp() }
                val availableWidth = cardWidthDp - FEATURED_CARD_SIDE_PADDING * 2
                val naturalWidth = FEATURED_CARD_DOT_WIDTH * dotCount + FEATURED_CARD_DOT_SPACING * (dotCount - 1)
                val dotWidth = if (naturalWidth <= availableWidth) {
                    FEATURED_CARD_DOT_WIDTH
                } else {
                    ((availableWidth - FEATURED_CARD_DOT_SPACING * (dotCount - 1)) / dotCount)
                        .coerceAtLeast(FEATURED_CARD_DOT_MIN_WIDTH)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .alpha(progress),
                    horizontalArrangement = Arrangement.spacedBy(FEATURED_CARD_DOT_SPACING),
                ) {
                    repeat(dotCount) { index ->
                        Box(
                            modifier = Modifier
                                .width(dotWidth)
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

        // A true screen-level overlay, not a child of the card above — deliberately NOT inside
        // that Box (which clips to the card's own animated, growing/shrinking bounds and sits
        // underneath Home's chromeBlur in the same way the rest of that card's content does).
        // Living here instead, as this Box's own last child, means it's laid out against the
        // real screen (top-right corner, below the status bar, entirely independent of wherever
        // the card itself currently is mid-animation) and painted after everything else — above
        // the blur, not affected by the card's own clip/transform, and a tap on it can't reach
        // the dismiss-scrim or the card underneath since it's a fully separate subtree, not
        // nested inside either one's own gesture-catching Box.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 16.dp, end = 16.dp)
                .alpha(progress),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(enabled = !isDeleting) { menuExpanded = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            // Material3's DropdownMenu already fades+scales in/out by default — that's the
            // "fade animation" here, rather than a second, hand-rolled AnimatedVisibility on
            // top of it, which would just fight the built-in one.
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                containerColor = colors.panel,
                shape = EmberRadii.dialogShape,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, colors.border),
            ) {
                DropdownMenuItem(
                    text = { Text("Save", fontFamily = PublicSansFontFamily, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.cream) },
                    leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null, tint = colors.cream, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menuExpanded = false
                        val url = currentEntry.photo.photoUrl
                        val needsPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
                        if (needsPermission) {
                            pendingDownloadUrl = url
                            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            coroutineScope.launch { downloadPhoto(context, url) }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                HorizontalDivider(color = colors.border, thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))
                DropdownMenuItem(
                    text = { Text("Delete", fontFamily = PublicSansFontFamily, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = MemoriesDestructiveColor) },
                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MemoriesDestructiveColor, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menuExpanded = false
                        showDeleteConfirm = true
                    },
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (showDeleteConfirm) {
        DeleteMemoryConfirmDialog(
            isDeleting = isDeleting,
            onDismiss = { if (!isDeleting) showDeleteConfirm = false },
            onConfirm = {
                val photoId = currentEntry.photo.photoId
                coroutineScope.launch {
                    isDeleting = true
                    onDeletePhoto(photoId).onSuccess {
                        isDeleting = false
                        showDeleteConfirm = false
                        // Back to the grid rather than trying to re-page a now-shorter carousel —
                        // the grid itself already reflects the removal (HomeViewModel updates its
                        // cache on success), so there's nothing stale left to look at here.
                        onDismiss()
                    }.onFailure {
                        isDeleting = false
                        Toast.makeText(context, it.message ?: "Couldn't delete that photo", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

private suspend fun downloadPhoto(context: Context, photoUrl: String) {
    saveImageToGallery(context, photoUrl).fold(
        onSuccess = { Toast.makeText(context, "Saved to your gallery", Toast.LENGTH_SHORT).show() },
        onFailure = { Toast.makeText(context, it.message ?: "Couldn't save that photo", Toast.LENGTH_SHORT).show() },
    )
}

// Same dark red already used for Delete account/list confirms elsewhere in the app (see
// SettingsScreen/RecipientPickerScreen's own DeleteAccountDestructiveColor/
// DeleteListDestructiveColor) — kept as its own local constant rather than a shared one across
// three unrelated packages purely for one hex value.
private val MemoriesDestructiveColor = Color(0xFFB3261E)

/** Same shell every other confirm-before-delete dialog in the app uses (see
 * RecipientPickerScreen's DeleteListConfirmDialog) — plain "This can't be undone.", no dash, dark
 * red confirm button. Stays open through the delete request (spinner on the confirm button)
 * rather than closing optimistically, since a real, permanent deletion failing needs to be
 * visibly a failure, not silently pretend to have worked. */
@Composable
private fun DeleteMemoryConfirmDialog(isDeleting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = EmberTheme.colors
    EditDialogShell(title = "Delete this photo?", onDismiss = onDismiss) {
        Text(
            text = "This can't be undone.",
            fontFamily = PublicSansFontFamily,
            fontSize = 13.sp,
            color = colors.muted,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.panel)
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .clickable(enabled = !isDeleting, onClick = onDismiss)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(text = "Cancel", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.muted)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MemoriesDestructiveColor)
                    .clickable(enabled = !isDeleting, onClick = onConfirm)
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(15.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(text = "Delete", fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}
