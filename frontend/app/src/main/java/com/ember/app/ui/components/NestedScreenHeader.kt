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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
 * which is exactly the drift this previously caused for Friends' "+". Rather than guessing a
 * dp value that happens to be no taller than the title text, the title's real rendered height is
 * measured and [trailing] is clamped to it directly — [trailing] can ask for whatever size looks
 * right; it always gets capped to the title's own height, never the reverse.
 */
@Composable
fun TabScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (BoxScope.() -> Unit)? = null,
) {
    val colors = EmberTheme.colors
    val density = LocalDensity.current
    var titleHeightPx by remember { mutableIntStateOf(0) }

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
            modifier = Modifier
                .align(Alignment.Center)
                .onSizeChanged { titleHeightPx = it.height },
        )
        if (trailing != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .let { base ->
                        if (titleHeightPx > 0) base.height(with(density) { titleHeightPx.toDp() }) else base
                    },
                contentAlignment = Alignment.Center,
                content = trailing,
            )
        }
    }
}
