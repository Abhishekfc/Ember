package com.emigo.app.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.emigo.app.data.remote.dto.MemoryPhotoDto
import com.emigo.app.ui.components.LocalNavDockHeight
import com.emigo.app.ui.components.TabScreenHeader
import com.emigo.app.ui.profile.EditDialogShell
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Flat photo-grid column count — square tiles, four across, matching the reference redesign
 * (no more calendar/weekday structure). */
private const val MEMORIES_GRID_COLUMNS = 4

/** Anything saved within this many days groups under "Recent"; everything older groups by its
 * own calendar month instead. */
private const val RECENT_SECTION_DAYS = 7L

/** How far back [HomeViewModel.loadMemories] fetches when the account's real creation date isn't
 * known yet — see that property's own doc comment. Mirrored here only for this file's own
 * fallback empty-state copy; the real backstop constant lives on HomeViewModel. */

/** One labeled band of the flat Memories grid — "Recent" or a calendar month name. Built fresh
 * from [HomeViewModel.memories] (already sorted newest-first) every time that list changes,
 * rather than kept as its own persisted state — grouping is cheap and entirely derived from data
 * already in memory. */
private data class MemoriesSection(val label: String, val photos: List<MemoryPhotoDto>)

/** Splits [memories] (already newest-first) into "Recent" (anything from the last
 * [RECENT_SECTION_DAYS] days) followed by one section per calendar month for everything older,
 * each labeled with that month's own name — no month-navigation state to keep around any more,
 * this is the entire grouping. */
private fun buildMemoriesSections(memories: List<MemoryPhotoDto>): List<MemoriesSection> {
    if (memories.isEmpty()) return emptyList()
    val recentCutoff = Instant.now().minus(RECENT_SECTION_DAYS, ChronoUnit.DAYS)
    val recent = mutableListOf<MemoryPhotoDto>()
    val olderByMonth = LinkedHashMap<YearMonth, MutableList<MemoryPhotoDto>>()
    memories.forEach { photo ->
        val createdAt = runCatching { Instant.parse(photo.createdAt) }.getOrNull()
        if (createdAt != null && createdAt.isAfter(recentCutoff)) {
            recent += photo
        } else {
            val month = createdAt
                ?.atZone(ZoneId.systemDefault())
                ?.toLocalDate()
                ?.let { YearMonth.from(it) }
                ?: return@forEach
            olderByMonth.getOrPut(month) { mutableListOf() } += photo
        }
    }
    val sections = mutableListOf<MemoriesSection>()
    if (recent.isNotEmpty()) sections += MemoriesSection("Recent", recent)
    olderByMonth.forEach { (month, photos) ->
        sections += MemoriesSection("${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}", photos)
    }
    return sections
}

/** A tapped photo, remembered together with the on-screen bounds of the grid tile that was tapped
 * (the origin the featured card grows out of, and shrinks back into on dismiss), the full
 * newest-first memories list, and which page within it the tapped photo corresponds to —
 * swiping continues across the *entire* saved history, not just whatever section it was tapped
 * from, the same "keep going" feel Home's own featured card already has across friends. */
internal data class MemoryFocusTarget(
    val photo: MemoryPhotoDto,
    val originBounds: Rect,
    val allMemories: List<MemoryPhotoDto>,
    val initialPage: Int,
)

/** Hoisted out of the grid so the actual [MemoryFeaturedOverlay] can be rendered by
 * [MemoriesTabScreen] at its own true full-screen Box instead — that Box has a real, bounded
 * size, unlike a container nested inside a scrollable column (which measures children with an
 * unbounded height constraint), so a "centered against the full device screen" overlay rendered
 * there was positioning itself against a container that was never actually the full screen to
 * begin with. */
internal class MemoryFocusState {
    var target by mutableStateOf<MemoryFocusTarget?>(null)
    var isOpen by mutableStateOf(false)
    val progress = Animatable(0f)
}

@Composable
internal fun rememberMemoryFocusState(): MemoryFocusState = remember { MemoryFocusState() }

/** The flat Memories grid — one square tile per saved photo, grouped into [MemoriesSection]s
 * ("Recent", then a section per calendar month), with no calendar structure, no empty-day
 * placeholders, and no month navigation. A real [LazyVerticalGrid], not a plain eagerly-composed
 * Column — unlike the old one-month-at-a-time calendar (small and fixed-size enough to compose
 * eagerly), this can hold a long-time account's entire saved history, so it needs real
 * virtualization. That's also why this composable owns its own scrolling: a lazy layout can't be
 * nested inside another scrollable container (the "vertically scrollable component measured with
 * an infinity maximum height constraint" crash) — see [MemoriesTabScreen]'s own layout for how
 * the header stays fixed above this instead of scrolling away with it. */
@Composable
internal fun MemoriesPhotoGrid(
    memories: List<MemoryPhotoDto>,
    onCameraClick: () -> Unit,
    focusState: MemoryFocusState,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
    isLoading: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
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

    // Swiping back (or pressing back) while a photo is open should close the featured card, the
    // same way tapping outside it does — not fall through to the system and back out of the app.
    BackHandler(enabled = isOpen) { focusState.isOpen = false }

    val gridBlur by rememberFocusBlur(isOpen)
    val gridFade by rememberFocusFade(isOpen)

    val sections = remember(memories) { buildMemoriesSections(memories) }

    Box(modifier = modifier) {
        when {
            isLoading && memories.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 72.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = colors.glow, modifier = Modifier.size(20.dp))
            }

            sections.isEmpty() -> MemoriesEmptyState(
                onCameraClick = onCameraClick,
                modifier = Modifier.padding(horizontal = 22.dp).padding(top = 60.dp),
            )

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(MEMORIES_GRID_COLUMNS),
                modifier = Modifier
                    .fillMaxSize()
                    .blur(gridBlur, BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = gridFade },
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                sections.forEach { section ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "label-${section.label}") {
                        Text(
                            text = section.label,
                            fontFamily = typography.body,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = colors.cream,
                            modifier = Modifier.padding(top = 14.dp, bottom = 8.dp),
                        )
                    }
                    items(section.photos, key = { it.photoId }) { photo ->
                        MemoryPhotoCell(
                            photo = photo,
                            onClick = { bounds ->
                                val initialPage = memories.indexOfFirst { it.photoId == photo.photoId }.coerceAtLeast(0)
                                focusState.target = MemoryFocusTarget(photo, bounds, memories, initialPage)
                                focusState.isOpen = true
                            },
                        )
                    }
                }
            }
        }

    }
}

/** Nothing saved yet — a quiet invitation, not a dead end. */
@Composable
private fun MemoriesEmptyState(onCameraClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "No memories yet",
            fontFamily = typography.body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = colors.cream,
        )
        Text(
            text = "Save a photo from the camera to see it here.",
            fontFamily = typography.body,
            fontSize = 13.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = "Open camera",
            fontFamily = PublicSansFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.glow,
            modifier = Modifier
                .padding(top = 14.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCameraClick,
                ),
        )
    }
}

@Composable
private fun MemoryPhotoCell(
    photo: MemoryPhotoDto,
    onClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = EmberTheme.colors
    val shape = RoundedCornerShape(10.dp)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .onGloballyPositioned { coordinates = it }
            .clip(shape)
            .background(colors.panel)
            .clickable { coordinates?.let { onClick(it.boundsInRoot()) } },
    ) {
        AsyncImage(
            model = photo.photoUrl,
            contentDescription = "Your photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A photo, grown out of the grid tile that was tapped into the exact same card recipe Home's own
 * `FeaturedPhotoCard` uses — rounded glow-shadowed card, bottom gradient scrim — via a
 * container-transform: the card animates from the tapped tile's actual on-screen position/size
 * ([MemoryFocusTarget.originBounds]) to a centered full-width position, and shrinks back into that
 * same spot on dismiss, rather than a flat full-screen takeover. Rendered by the caller
 * ([MemoriesTabScreen]) at its own true full-screen Box — [MemoryFocusTarget.originBounds] is
 * already captured via `boundsInRoot()`, i.e. relative to the same compose root that Box sits at,
 * so it needs no further coordinate conversion here. [screenSize] is that same Box's real measured
 * size in px, used to center the destination within the safe area below the status bar and above
 * the floating nav dock. */
@Composable
internal fun MemoryFeaturedOverlay(
    target: MemoryFocusTarget,
    screenSize: Size,
    progress: Float,
    onDismiss: () -> Unit,
    // Reports whichever photo scrolling has actually come to rest on, for the caller's own
    // AmbientPhotoBackdrop (the same blurred-photo wash Home's own featured card uses) to pick up
    // — deliberately NOT wired to the live page the way currentPhoto below is.
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
    // Pages through the whole saved history (see MemoryFocusTarget.allMemories), not just the
    // tapped photo alone — landing on the tapped photo, but continuing into neighboring photos
    // instead of stopping dead at it.
    val pagerState = rememberPagerState(initialPage = target.initialPage) { target.allMemories.size }
    // Whichever photo the pager is actually settled/settling on right now — drives the date label,
    // so it tracks wherever swiping has actually taken you rather than staying frozen on the
    // originally-tapped photo.
    val currentPhoto = target.allMemories.getOrElse(pagerState.currentPage) { target.allMemories[target.initialPage] }

    LaunchedEffect(target) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { isScrolling ->
                if (!isScrolling) {
                    onCurrentPhotoChanged(target.allMemories.getOrNull(pagerState.currentPage)?.photoUrl)
                }
            }
    }

    // Same reasoning as Home's FeaturedPhotoCard: this pager and whatever's behind it (the
    // Memories grid's own scroll, and the page underneath) are both potential recipients of a
    // drag that runs out of pages to turn — without this, swiping past the first/last photo lets
    // that drag bubble down into the grid or the page underneath instead of just stopping.
    // Consuming every bit of leftover scroll/fling here (onPost*, not onPre* — the pager itself
    // still scrolls normally first) means nothing from a drag that starts on this card is ever
    // left for whatever's behind it to receive.
    val cardNestedScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Centered within the SAFE usable area of the true screen (below the status bar, above
        // the floating nav dock) — this Box is the caller's own top-level Box, which has a real,
        // bounded size (screenSize), not a container nested inside a scrollable column (which
        // measures its children with an unbounded height, making "center against the full
        // screen" from in there meaningless). No root-position subtraction is needed either:
        // originBounds and this Box both live at the same compose root, so originBounds is
        // already in the right coordinate space as-is.
        val sidePaddingPx = with(density) { featuredCardSidePadding().toPx() }
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
        // visible pop.
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
                // card (there, tapping the card is exactly what toggles it back out of focus).
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val photo = target.allMemories[page]
                AsyncImage(
                    // The same photo is already showing small in the grid tile this card grew
                    // out of, and this Box's own size keeps animating during the grow/shrink
                    // transition — pin the decode to the card's final, settled pixel size rather
                    // than Coil's default (which follows the composable's size and would end up
                    // re-requesting on every animation frame, or reusing the tiny grid decode).
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
                    // progress, the same way the date text below already does, keeps it absent at
                    // the tile-sized end of the transition instead of a full-strength gradient
                    // popping in against a tile that never had one.
                    .alpha(progress)
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.65f),
                        ),
                    ),
            )

            Text(
                text = run {
                    val date = runCatching {
                        Instant.parse(currentPhoto.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    }.getOrNull()
                    if (date != null) "${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${date.dayOfMonth}" else ""
                },
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
        // that Box (which clips to the card's own animated, growing/shrinking bounds). Living
        // here instead, as this Box's own last child, means it's laid out against the real screen
        // (top-right corner, below the status bar, entirely independent of wherever the card
        // itself currently is mid-animation) and painted after everything else.
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
                        val url = currentPhoto.photoUrl
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
                val photoId = currentPhoto.photoId
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

/** Memories as its own bottom-nav tab — a real, standard-shaped tab screen (own header, own
 * scaffold), same "one tab, one screen" pattern Friends/Activity/Settings already follow. Reuses
 * [HomeViewModel] (already hoisted at the app root, already fetching/holding
 * [HomeViewModel.memories]) rather than a second ViewModel duplicating that same fetch.
 *
 * The header stays fixed at the top (not part of any scroll) and [MemoriesPhotoGrid] owns the
 * only real scrolling on this screen — see that composable's own doc comment for why a lazy grid
 * can't be nested inside a second scrollable container. [MemoryFeaturedOverlay] needs to center
 * itself against the screen's *real* pixel size, which only this composable's own outer,
 * fillMaxSize Box actually is. [AmbientPhotoBackdrop] and the blur/fade-while-a-photo-is-open
 * treatment are the exact same building blocks Home's own featured-card focus state already
 * uses — nothing new invented for this screen specifically. */
@Composable
fun MemoriesTabScreen(
    viewModel: HomeViewModel,
    onCameraClick: () -> Unit,
    hazeState: HazeState,
) {
    val colors = EmberTheme.colors
    var screenSize by remember { mutableStateOf(Size.Zero) }
    var currentPhotoUrl by remember { mutableStateOf<String?>(null) }
    val focusState = rememberMemoryFocusState()
    val isFocused = focusState.isOpen

    val chromeBlur by rememberFocusBlur(isFocused)
    val chromeFade by rememberFocusFade(isFocused)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        AmbientPhotoBackdrop(
            photoUrl = currentPhotoUrl,
            visible = isFocused,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState)
                .statusBarsPadding(),
        ) {
            TabScreenHeader(
                title = "Memories",
                modifier = Modifier
                    .blur(chromeBlur, BlurredEdgeTreatment.Unbounded)
                    .graphicsLayer { alpha = chromeFade },
            )
            MemoriesPhotoGrid(
                memories = viewModel.memories,
                onCameraClick = onCameraClick,
                focusState = focusState,
                isLoading = viewModel.isLoadingMemories,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    bottom = LocalNavDockHeight.current + 24.dp,
                ),
            )
        }

        // Rendered from this screen's own outer Box (fillMaxSize of the true measured screen
        // size), not from inside the grid above — see this composable's own doc comment for why.
        focusState.target?.let { target ->
            MemoryFeaturedOverlay(
                target = target,
                screenSize = screenSize,
                progress = focusState.progress.value,
                onDismiss = { focusState.isOpen = false },
                onCurrentPhotoChanged = { currentPhotoUrl = it },
                onDeletePhoto = { photoId -> viewModel.deleteMemoryPhoto(photoId) },
            )
        }
    }
}
