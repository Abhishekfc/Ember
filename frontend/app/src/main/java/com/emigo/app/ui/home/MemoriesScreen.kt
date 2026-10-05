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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.emigo.app.R
import com.emigo.app.data.remote.dto.MemoryPhotoDto
import com.emigo.app.ui.components.LocalNavDockHeight
import com.emigo.app.ui.components.TabScreenHeader
import com.emigo.app.ui.profile.EditDialogShell
import com.emigo.app.ui.theme.EmberFixedColors
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

/** Square tiles, four across. */
private const val MEMORIES_GRID_COLUMNS = 4

/** Anything saved within this many days goes under "Recent"; older photos group by calendar
 * month. */
private const val RECENT_SECTION_DAYS = 7L

/** One labeled band of the Memories grid: "Recent" or a month name. Built from
 * [HomeViewModel.memories] (newest first) whenever it changes. It is derived data, so it isn't
 * persisted. */
private data class MemoriesSection(val label: String, val photos: List<MemoryPhotoDto>)

/** Splits [memories] (newest first) into "Recent" (the last [RECENT_SECTION_DAYS] days), then one
 * section per calendar month for everything older. */
private fun buildMemoriesSections(memories: List<MemoryPhotoDto>, recentLabel: String): List<MemoriesSection> {
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
    if (recent.isNotEmpty()) sections += MemoriesSection(recentLabel, recent)
    olderByMonth.forEach { (month, photos) ->
        sections += MemoriesSection("${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}", photos)
    }
    return sections
}

/** A tapped photo with the on-screen bounds of its grid tile (the featured card grows out of that
 * rectangle and shrinks back into it), the full newest-first list, and the photo's page in it.
 * Swiping continues across the whole history, not just the section it was tapped from, like Home's
 * featured card across friends. */
internal data class MemoryFocusTarget(
    val photo: MemoryPhotoDto,
    val originBounds: Rect,
    val allMemories: List<MemoryPhotoDto>,
    val initialPage: Int,
)

/** Hoisted out of the grid so [MemoriesTabScreen] can render [MemoryFeaturedOverlay] in its own
 * full-screen Box. That Box has a real, bounded size. A container nested in a scrollable column
 * measures its children with unbounded height, so an overlay centered there wasn't centered on the
 * device screen. */
internal class MemoryFocusState {
    var target by mutableStateOf<MemoryFocusTarget?>(null)
    var isOpen by mutableStateOf(false)
    val progress = Animatable(0f)
}

@Composable
internal fun rememberMemoryFocusState(): MemoryFocusState = remember { MemoryFocusState() }

/** The Memories grid: one square tile per saved photo, grouped into [MemoriesSection]s. It is a
 * real [LazyVerticalGrid] because it can hold an account's entire history. It owns its own
 * scrolling, since a lazy layout can't sit inside another scrollable (it crashes with "measured
 * with an infinity maximum height constraint"); [MemoriesTabScreen] keeps the header fixed above
 * it instead. */
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

    // Back closes the open photo, like tapping outside it, instead of leaving the app.
    BackHandler(enabled = isOpen) { focusState.isOpen = false }

    val gridBlur by rememberFocusBlur(isOpen)
    val gridFade by rememberFocusFade(isOpen)

    val recentLabel = stringResource(R.string.memories_section_recent)
    val sections = remember(memories, recentLabel) { buildMemoriesSections(memories, recentLabel) }

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
            text = stringResource(R.string.memories_empty_title),
            fontFamily = typography.body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = colors.cream,
        )
        Text(
            text = stringResource(R.string.memories_empty_detail),
            fontFamily = typography.body,
            fontSize = 13.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 4.dp),
        )
        // Same solid-white pill as RecipientPickerScreen's "Find friends" button: the same look for
        // the same kind of action.
        Row(
            modifier = Modifier
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCameraClick,
                )
                .padding(horizontal = 28.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.memories_open_camera),
                fontFamily = PublicSansFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
            )
        }
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
            contentDescription = stringResource(R.string.memories_photo_description),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** A photo grown out of its tapped grid tile into the same card as Home's `FeaturedPhotoCard`
 * (rounded card, bottom scrim), as a container transform: it animates from the tile's on-screen
 * bounds ([MemoryFocusTarget.originBounds]) to a centered full-width card, and back on dismiss.
 * The caller ([MemoriesTabScreen]) renders it in its full-screen Box; originBounds comes from
 * `boundsInRoot()`, the same coordinate space, so no conversion is needed. [screenSize] is that
 * Box's measured size in px, used to center the card between the status bar and the nav dock. */
@Composable
internal fun MemoryFeaturedOverlay(
    target: MemoryFocusTarget,
    screenSize: Size,
    progress: Float,
    onDismiss: () -> Unit,
    // Reports the photo the pager has settled on (not the live page, unlike currentPhoto below),
    // for the caller's AmbientPhotoBackdrop, the blurred wash Home's featured card also uses.
    onCurrentPhotoChanged: (String?) -> Unit = {},
    // Permanent deletion (see HomeViewModel.deleteMemoryPhoto and the backend's
    // PhotoService.delete). A suspend Result, not a callback, so the confirm dialog can show its
    // own spinner and error instead of closing optimistically.
    onDeletePhoto: suspend (String) -> Result<Unit> = { Result.success(Unit) },
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val density = LocalDensity.current
    val context = LocalContext.current
    // Read from the Compose configuration so the date label follows a language change.
    val locale = LocalConfiguration.current.locales[0]
    val deleteFailedMessage = stringResource(R.string.memories_delete_failed)
    val coroutineScope = rememberCoroutineScope()
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    // Only needed on Android 9 and below: from 10 on, MediaStore.insert writes to the gallery with
    // no permission (see saveImageToGallery). The pending URL is retried automatically once the
    // user answers, so no second tap is needed.
    var pendingDownloadUrl by remember { mutableStateOf<String?>(null) }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val url = pendingDownloadUrl
        pendingDownloadUrl = null
        if (granted && url != null) {
            coroutineScope.launch { downloadPhoto(context, url) }
        }
    }
    // Pages through the whole saved history (MemoryFocusTarget.allMemories), starting at the
    // tapped photo.
    val pagerState = rememberPagerState(initialPage = target.initialPage) { target.allMemories.size }
    // The photo the pager is on right now. It drives the date label, so the label follows swiping.
    val currentPhoto = target.allMemories.getOrElse(pagerState.currentPage) { target.allMemories[target.initialPage] }

    LaunchedEffect(target) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { isScrolling ->
                if (!isScrolling) {
                    onCurrentPhotoChanged(target.allMemories.getOrNull(pagerState.currentPage)?.photoUrl)
                }
            }
    }

    // Same as Home's FeaturedPhotoCard: this pager and what's behind it (the grid, the page below)
    // could both receive a drag that runs out of pages. Consuming all leftover scroll and fling
    // here (onPost*, so the pager still scrolls first) stops a drag that started on this card from
    // reaching anything behind it.
    val cardNestedScrollBoundary = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset = available
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity = available
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Centered in the usable screen area (below the status bar, above the nav dock). This is
        // the caller's top-level Box with a real bounded size, not a container inside a scrollable
        // column. originBounds and this Box share one compose root, so no coordinate conversion is
        // needed.
        val sidePaddingPx = with(density) { featuredCardSidePadding().toPx() }
        val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
        // The dock's measured height already includes the system nav-bar inset (BottomNavDock
        // applies navigationBarsPadding), so there is no separate navigation-bar term.
        val navDockReservePx = with(density) { LocalNavDockHeight.current.toPx() }
        val usableHeightPx = (screenSize.height - statusBarPx - navDockReservePx).coerceAtLeast(1f)
        val cardWidthPx = (screenSize.width - sidePaddingPx * 2).coerceAtLeast(1f)
        val cardHeightPx = cardWidthPx / FEATURED_CARD_ASPECT_RATIO
        val destLeft = sidePaddingPx
        val destTop = statusBarPx + (usableHeightPx - cardHeightPx) / 2f
        val destRect = Rect(destLeft, destTop, destLeft + cardWidthPx, destTop + cardHeightPx)

        val currentRect = lerp(target.originBounds, destRect, progress)

        // The grid tile has 10.dp corners and the open card uses FEATURED_CARD_CORNER_RADIUS.
        // Interpolating avoids a visible pop when this overlay is removed at the end of the close
        // animation and the real tile shows.
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
                // A separate .offset{} (raw px) and .size(dp) round independently each frame. At
                // the small end of the shrink-back animation that mismatch is a large fraction of
                // the box and shows as jitter. Measuring and placing in one pass, in raw px, rounds
                // once.
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
                // Same recipe as Home's FeaturedPhotoCard: elevated, not plain panel, so it
                // outranks the grid tiles behind.
                .background(colors.elevatedPanel)
                // A tap anywhere on the open photo closes it, as on Home's featured card.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val photo = target.allMemories[page]
                AsyncImage(
                    // The photo already shows small in the tile this card grew from, and this Box
                    // keeps animating in size. Pin the decode to the card's final pixel size:
                    // Coil's default follows the composable's size and would re-request every
                    // frame or reuse the tiny grid decode.
                    model = remember(photo.photoUrl, cardWidthPx, cardHeightPx) {
                        ImageRequest.Builder(context)
                            .data(photo.photoUrl)
                            .size(cardWidthPx.roundToInt(), cardHeightPx.roundToInt())
                            .build()
                    },
                    contentDescription = stringResource(R.string.memories_photo_description),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // The real grid tile has no bottom scrim. Fading it in with progress (like the
                    // date text) keeps it absent at tile size instead of popping in at full
                    // strength.
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
                    if (date != null) "${date.month.getDisplayName(TextStyle.FULL, locale)} ${date.dayOfMonth}" else ""
                },
                fontFamily = typography.display,
                fontSize = 24.sp,
                color = EmberFixedColors.onPhotoText,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dp, bottom = 22.dp)
                    .alpha(progress),
            )
        }

        // A screen-level overlay, deliberately not inside the card's Box, which clips to the card's
        // animated bounds. As this Box's last child it lays out against the real screen (top-right,
        // below the status bar) and paints last, whatever the card is doing.
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
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.common_more_options), tint = Color.White, modifier = Modifier.size(18.dp))
            }
            // Material3's DropdownMenu already fades and scales in and out; a hand-rolled
            // AnimatedVisibility on top would fight it.
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
                    text = { Text(stringResource(R.string.common_save), fontFamily = PublicSansFontFamily, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = colors.cream) },
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
                    text = { Text(stringResource(R.string.common_delete), fontFamily = PublicSansFontFamily, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = MemoriesDestructiveColor) },
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
                        // Back to the grid instead of re-paging a now-shorter carousel; the grid
                        // already reflects the removal (HomeViewModel updates its cache on success).
                        onDismiss()
                    }.onFailure {
                        isDeleting = false
                        Toast.makeText(context, it.message ?: deleteFailedMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

private suspend fun downloadPhoto(context: Context, photoUrl: String) {
    saveImageToGallery(context, photoUrl).fold(
        onSuccess = { Toast.makeText(context, context.getString(R.string.memories_saved_to_gallery), Toast.LENGTH_SHORT).show() },
        onFailure = { Toast.makeText(context, it.message ?: context.getString(R.string.memories_save_failed), Toast.LENGTH_SHORT).show() },
    )
}

// The shared destructive red (EmberFixedColors.destructive), as the other delete confirms use.
private val MemoriesDestructiveColor = EmberFixedColors.destructive

/** The shared confirm-before-delete dialog shell (see RecipientPickerScreen's
 * DeleteListConfirmDialog): "This can't be undone.", dark red confirm button. It stays open through
 * the delete, with a spinner on the confirm button, because a failed permanent deletion must
 * visibly fail, not pretend it worked. */
@Composable
private fun DeleteMemoryConfirmDialog(isDeleting: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = EmberTheme.colors
    EditDialogShell(title = stringResource(R.string.memories_delete_title), onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.common_undo_warning),
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
                Text(text = stringResource(R.string.common_cancel), fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, color = colors.muted)
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
                    Text(text = stringResource(R.string.common_delete), fontFamily = PublicSansFontFamily, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/** Memories as its own bottom-nav tab, with its own header and scaffold like Friends, Activity and
 * Settings. Reuses [HomeViewModel] (hoisted at the app root, already holding
 * [HomeViewModel.memories]) instead of a second ViewModel fetching the same data.
 *
 * The header stays fixed and [MemoriesPhotoGrid] owns the only scrolling (see its doc).
 * [MemoryFeaturedOverlay] must center against the screen's real pixel size, which only this
 * composable's outer fillMaxSize Box has. The ambient backdrop and the blur and fade while a photo
 * is open are the same building blocks Home's focus state uses. */
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
                title = stringResource(R.string.memories_title),
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

        // Rendered from this outer Box (the real screen size), not from inside the grid; see the
        // doc above.
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
