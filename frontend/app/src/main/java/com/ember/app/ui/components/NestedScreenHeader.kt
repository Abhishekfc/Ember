package com.ember.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
 * forcing it taller. A `Box` sizes itself to its tallest child in one synchronous measure pass, so
 * a [trailing] control taller than that text (Friends' add-friend circle, 44dp, taller than the
 * title's own ~28dp line height) naturally grows the row to fit it — deliberately, not a bug: the
 * row is always exactly as tall as its tallest real content, computed fresh every frame with no
 * stored/remembered measurement, so there's no first-frame-vs-later-frame mismatch to cause a
 * jump (see the note below on why an earlier, stateful version of this had exactly that problem).
 * Title-only headers (no [trailing]) are completely unaffected either way.
 *
 * This deliberately replaced an earlier version that measured the title's real rendered height
 * into state and clamped [trailing] to it. That worked once settled, but the measurement was only
 * available on the *second* frame — on the first, [trailing] was unclamped and the header
 * rendered at the taller control's height, so every fresh composition of a screen with a
 * [trailing] (only Friends today) briefly laid its whole page out ~10dp lower, then snapped up
 * once the measurement landed. Returning from a nested screen is exactly such a fresh
 * composition, which is what made the Friends list visibly jump on every back-navigation. Plain
 * `Box` sizing has no such state to lag behind — it's correct on every frame, including the first.
 *
 * Horizontal inset (22dp) and the flush-under-status-bar top (0dp, no gap) match Home's own
 * brand header (`HomeBrandHeader`) exactly, baked in here rather than left for each caller to
 * reproduce, so every bottom-nav tab's header sits at the same size/position as Home's.
 *
 * Height is floored at 44dp (Home's own icon-row height) unconditionally, not just when a
 * [trailing] control happens to be taller than the title — a screen with no [trailing] at all
 * (Settings, Activity, Memories) would otherwise size its header to the shorter title-text-only
 * height, so swiping from Friends (44dp, has a trailing icon) to any of those visibly changed the
 * header's height mid-swipe. Every tab's header is now the same fixed height regardless of what
 * it carries, which is what makes moving between tabs read as one continuous surface.
 *
 * Bottom padding trimmed to 12dp (was 20dp) — with the 44dp floor above, 20dp pushed the whole
 * block to 64dp total, taller than a typical app's title bar (~56dp). 12dp lands there instead
 * without touching the 44dp icon itself.
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
            .padding(start = 22.dp, end = 22.dp, top = 0.dp, bottom = 12.dp)
            .heightIn(min = 44.dp),
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
                modifier = Modifier.align(Alignment.CenterEnd),
                contentAlignment = Alignment.CenterEnd,
                content = trailing,
            )
        }
    }
}
