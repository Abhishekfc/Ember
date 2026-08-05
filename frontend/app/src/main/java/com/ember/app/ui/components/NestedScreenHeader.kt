package com.ember.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

/**
 * Fixed tap-target size for [NestedScreenHeader]'s mandatory back button, and for any trailing
 * control placed next to it — every nested/pushed screen has a back button, so this size already
 * governs every one of those headers' real height regardless of what else is on the row.
 */
val HeaderRowHeight = 40.dp

/**
 * One header shape for every pushed/nested screen (Profile, Friend profile, Theme, Ember Gold,
 * Find people, Blocked accounts, Widget settings, Send-to picker) — a plain back chevron with no
 * background chrome, and a bold title truly centered in the row regardless of the back button's
 * own width. Replaces what used to be a different icon, size, button treatment, and title size
 * per screen. `title == null` renders just the back control, for screens whose own centered
 * avatar+name or hero moment below already serves as the page's title.
 */
@Composable
fun NestedScreenHeader(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = EmberTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 20.dp)
            .height(HeaderRowHeight),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(HeaderRowHeight)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBackIos,
                contentDescription = "Back",
                tint = colors.cream,
                modifier = Modifier.size(20.dp),
            )
        }
        if (title != null) {
            Text(
                text = title,
                fontFamily = PublicSansFontFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = colors.cream,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (trailing != null) {
            Box(modifier = Modifier.align(Alignment.CenterEnd), content = trailing)
        }
    }
}

/**
 * Same title treatment as [NestedScreenHeader] (bold, centered, plain UI font, tight padding) but
 * with no back control — for the bottom-nav tabs themselves (Friends, Activity, Settings), which
 * you swipe/tap between rather than back out of. Previously each of these three grew its own
 * header inline; Friends had drifted to a left-aligned display-serif title with a subtitle while
 * Activity and Settings already matched each other, which is the inconsistency this replaces.
 *
 * Tab headers have no mandatory element the way nested screens have a back button — a title-only
 * header (Activity, Settings) sizes itself to the title text's own natural height with nothing
 * forcing it taller. A `Box` sizes itself to its tallest child, so a [trailing] control taller
 * than that text would silently make its screen's whole header row taller than its siblings',
 * which is exactly the drift this previously caused for Friends' "+".
 *
 * [trailing] is placed with `matchParentSize()`, which measures it against this Box's already-
 * decided size instead of letting it contribute to that size — so the row's height comes from the
 * title alone, always, and [trailing] can ask for whatever size looks right (a generous touch
 * target, say) without ever pushing the row taller.
 *
 * This deliberately replaced an earlier version that measured the title's real rendered height
 * into state and clamped [trailing] to it. That worked once settled, but the measurement is only
 * available on the *second* frame — on the first, [trailing] was unclamped and the header
 * rendered at the taller control's height, so every fresh composition of a screen with a
 * [trailing] (only Friends today) briefly laid its whole page out ~10dp lower, then snapped up
 * once the measurement landed. Returning from a nested screen is exactly such a fresh
 * composition, which is what made the Friends list visibly jump on every back-navigation.
 * `matchParentSize()` needs no measurement pass of its own, so there is no first frame that
 * disagrees with the rest.
 */
@Composable
fun TabScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = EmberTheme.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 20.dp),
    ) {
        Text(
            text = title,
            fontFamily = PublicSansFontFamily,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.cream,
            modifier = Modifier.align(Alignment.Center),
        )
        if (trailing != null) {
            Box(
                modifier = Modifier.matchParentSize(),
                contentAlignment = Alignment.CenterEnd,
                content = trailing,
            )
        }
    }
}
