package com.ember.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.R
import com.ember.app.ui.theme.EmberAppTheme
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import com.ember.app.ui.theme.ThemeKey
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** A generous guess for the one frame before the dock's own real [onSizeChanged] below has
 * landed — every screen that needs to keep content clear of the floating dock should read
 * [LocalNavDockHeight] instead of this directly. Internal (not private) purely so MainActivity
 * can seed [LocalNavDockHeight]'s hoisted state with the same starting value, rather than a
 * second copy of this same magic number living in a different file. */
internal val FALLBACK_NAV_DOCK_HEIGHT_DP = 140.dp

/** The dock's own true rendered height on THIS device — footprint and real system nav-bar inset
 * both included, never a guessed constant. One fixed dp number can't cover every OEM's actual
 * navigationBarsPadding() value (gesture nav vs. 3-button nav vs. taller custom skins all
 * differ), which is exactly what still left the Settings screen's Log out button partly covered
 * by the dock on at least one real device even after a generous fixed reserve. Provided once
 * near the root (see MainActivity, wired from this composable's own onSizeChanged below), read
 * wherever a screen needs to keep content clear of the dock (Settings' trailing spacer, Home's
 * Memories grid padding + top-fold height clamp, Memories' day-card centering). */
val LocalNavDockHeight = compositionLocalOf { FALLBACK_NAV_DOCK_HEIGHT_DP }

// Activity is deliberately NOT one of these any more — it moved to a bell icon in Home's own
// header, next to the profile avatar (the same slot notifications sit in on most apps), and is
// reached as a pushed NestedScreen (see MainActivity) exactly like Theme/Profile/Settings' own
// sub-screens, not a swipeable pager page with a corresponding dock tab. These four are the only
// destinations that still are.
enum class NavDestination(val label: String) {
    MEMORIES("Memories"),
    HOME("Home"),
    FRIENDS("Friends"),
    SETTINGS("Settings"),
}

/**
 * A minimal glassmorphism nav pill: Memories, Home, Camera, Friends, Settings in a single row
 * with even spacing between every element. Real backdrop blur via [hazeState]. Theme is
 * deliberately not a nav destination — it's reachable only from within Settings. Activity isn't
 * drawn here either — see [NavDestination.ACTIVITY]'s own doc comment for where it actually
 * lives now.
 *
 * IMPORTANT: [hazeState]'s `hazeSource` must be scoped to the scrolling content ONLY (e.g. the
 * screen's Column/LazyColumn), never to a shared ancestor Box that also contains this dock —
 * if the source region includes this effect node as a descendant, the captured layer can include
 * the dock's own stale pixels and produce a ghosting/misalignment artifact.
 */
@Composable
fun BottomNavDock(
    active: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    onCameraClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    // How many pending incoming friend requests — see MainActivity's own doc comment on where
    // this is computed; this composable just renders whatever it's handed.
    friendsBadgeCount: Int = 0,
    // Reports this Box's own real laid-out height — after navigationBarsPadding() and this
    // Box's own bottom inset are both applied — so callers can feed it into LocalNavDockHeight
    // instead of a guessed dp constant. Fires once per real layout pass, essentially only once
    // per device/orientation in practice.
    onHeightMeasured: (Dp) -> Unit = {},
) {
    val colors = EmberTheme.colors
    val density = LocalDensity.current
    val dockShape = RoundedCornerShape(percent = 50)

    Box(
        // onSizeChanged has to come BEFORE navigationBarsPadding()/padding() below, not after —
        // a modifier placed after padding in the chain only observes the already-shrunk content
        // size, not the padding that was applied around it. Placed last (a real bug, since
        // fixed), it silently reported just the inner Row's own height (~74dp), missing the
        // system nav-bar inset and this Box's own bottom padding entirely — a far smaller number
        // than the fixed dp guess it was meant to replace, which is exactly what made content on
        // Home/Settings/Memories start overlapping the dock instead of clearing it.
        modifier = modifier
            .onSizeChanged { onHeightMeasured(with(density) { it.height.toDp() }) }
            .fillMaxWidth()
            .navigationBarsPadding()
            // As small as looks safe above the real system inset navigationBarsPadding() already
            // reserves (gesture bar / 3-button nav / home indicator, whatever that device
            // actually has) — a bare 0dp here would sit the dock flush against that system chrome
            // with no breathing room at all, which reads as cramped/mistaken-for-overlap rather
            // than intentional; this is the minimum gap that still reads as deliberate spacing.
            .padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, dockShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                .clip(dockShape)
                .let { base ->
                    if (hazeState != null) {
                        base.hazeEffect(hazeState) {
                            style = HazeStyle(
                                backgroundColor = if (colors.isLight) Color.White else Color.Black,
                                tints = listOf(HazeTint(if (colors.isLight) Color.White.copy(alpha = 0.5f) else colors.panel.copy(alpha = 0.6f))),
                                blurRadius = 26.dp,
                                noiseFactor = 0.12f,
                            )
                        }
                    } else {
                        // overlayPanel, not plain panel — this dock is the one genuinely floating
                        // element sitting permanently above every screen's content, the same tier
                        // a dialog or bottom sheet would use.
                        base.background(colors.overlayPanel)
                    }
                }


                // Without this, only the individual icons themselves are actually clickable —
                // a tap landing in the gaps between them (or the pill's own padding) falls
                // straight through to whatever content is scrolled underneath at those same
                // coordinates, since the dock only visually sits on top, it doesn't otherwise
                // block touches from reaching what's behind it. A no-op clickable across the
                // whole pill absorbs every tap here; the icons' own clickables underneath still
                // win first for taps actually on them.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavItem(NavDestination.MEMORIES, active, onNavigate) { tint ->
                Icon(Icons.Filled.CalendarMonth, contentDescription = NavDestination.MEMORIES.label, tint = tint, modifier = Modifier.size(23.dp))
            }
            NavItem(NavDestination.HOME, active, onNavigate) { tint ->
                Icon(
                    painter = painterResource(R.drawable.ic_layers_2),
                    contentDescription = NavDestination.HOME.label,
                    tint = tint,
                    modifier = Modifier.size(23.dp),
                )
            }
            CameraButton(onCameraClick)
            NavItem(NavDestination.FRIENDS, active, onNavigate, badgeCount = friendsBadgeCount) { tint ->
                Icon(Icons.Filled.People, contentDescription = NavDestination.FRIENDS.label, tint = tint, modifier = Modifier.size(23.dp))
            }
            NavItem(NavDestination.SETTINGS, active, onNavigate) { tint ->
                Icon(Icons.Filled.Settings, contentDescription = NavDestination.SETTINGS.label, tint = tint, modifier = Modifier.size(23.dp))
            }
        }
    }
}

/** The one bold, warm element in an otherwise monochrome row — every other accent (the active
 * tab, the border) was pulled back to neutral specifically so this button is the single thing
 * carrying brand color, rather than splitting that signature across two competing spots. Sized
 * as the dock's primary action (44dp+ touch target, same accessibility floor as every other
 * control here) rather than a small icon that happened to get a gradient. */
@Composable
private fun CameraButton(onCameraClick: () -> Unit) {
    val colors = EmberTheme.colors
    val buttonSizePx = with(LocalDensity.current) { Size(54.dp.toPx(), 54.dp.toPx()) }

    Box(
        modifier = Modifier
            .size(54.dp)
            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
            .clip(CircleShape)
            .background(emberButtonBrush(EmberTheme.key, colors, buttonSizePx))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCameraClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = "Camera",
            tint = colors.accentText,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** No background wash behind the active icon — that would be a second, competing accent on top
 * of the camera button's gradient. Every icon is always the solid/filled glyph now (bolder,
 * heavier-looking than the old outlined-vs-filled pairing) — the active state reads through
 * color and contrast alone: full-strength `cream` versus `muted`, the same neutral pairing the
 * rest of the app already uses for primary-vs-secondary text.
 *
 * [icon] is a slot, not an [ImageVector] parameter — some nav items draw a plain Material glyph
 * (Friends, Settings), others a custom hand-converted vector drawable (Home's own icon), and this
 * one shape needs to serve both without either caller working around a type it doesn't have. The
 * slot is handed the already-animated [tint] so every icon, whatever its source, gets the exact
 * same active/inactive color treatment for free rather than each call site reimplementing it. */
@Composable
private fun NavItem(
    destination: NavDestination,
    active: NavDestination,
    onNavigate: (NavDestination) -> Unit,
    badgeCount: Int = 0,
    icon: @Composable (tint: Color) -> Unit,
) {
    val colors = EmberTheme.colors
    val isActive = destination == active

    val tint by animateColorAsState(
        targetValue = if (isActive) colors.cream else colors.muted,
        animationSpec = tween(200),
        label = "navItemTint",
    )

    Box(
        // 44dp is the accessibility touch-target floor — the previous 38dp-wide, 8dp-padded
        // box rendered at only 36dp tall, under that minimum.
        //
        // Deliberately no .clip(CircleShape) here (indication = null means there's no ripple for
        // it to bound anyway) — the badge below sits at this Box's TopEnd corner, and a corner
        // falls outside a circle inscribed in the same bounds, so that clip was silently slicing
        // the badge down to the thin crescent that was the whole bug report.
        modifier = Modifier
            .size(44.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onNavigate(destination) },
        contentAlignment = Alignment.Center,
    ) {
        icon(tint)
        // Fades in/out rather than just popping into existence — this badge's count is only ever
        // known after a cache read or network fetch resolves (see FriendsViewModel/
        // ActivityViewModel), so it can genuinely appear a beat after the icon itself is already
        // on screen; a plain `if` there made that look like a layout glitch rather than data
        // arriving, which fading now reads as instead.
        AnimatedVisibility(
            visible = badgeCount > 0,
            // Aligned to this Box's own TopEnd, but this Box is the full 44dp touch target, not
            // the 23dp icon glyph centered inside it — a plain TopEnd put the badge near the
            // touch target's own corner, visibly far from the icon it's meant to badge. This
            // offset pulls it in to sit right against the icon's own top-right corner instead.
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 5.dp),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(150)),
        ) {
            // A numbered pill instead of a plain dot — sized to fit 1-3 characters. Caps at
            // "99+" rather than ever growing wider than that.
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.glow)
                    .padding(horizontal = 3.5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (badgeCount > 99) "99+" else badgeCount.toString(),
                    fontFamily = PublicSansFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    // Each theme already defines accentText as whichever of black/white actually
                    // reads against its own glow color (see Theme.kt) — the same pairing every
                    // glow-filled button in the app uses. A hardcoded white here is what made this
                    // unreadable on Citrus, whose glow is a bright yellow.
                    color = colors.accentText,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101E)
@Composable
private fun BottomNavDockPreview() {
    EmberAppTheme(themeKey = ThemeKey.EMBER) {
        BottomNavDock(active = NavDestination.HOME, onNavigate = {}, onCameraClick = {})
    }
}
