package com.emigo.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.ui.auth.AuthPalette
import com.emigo.app.ui.components.NestedScreenHeader
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.PublicSansFontFamily

private data class GoldPerk(val icon: ImageVector, val title: String, val detail: String, val badge: String? = null)

/** Every color and gradient this page uses — fixed hex values, none of them read from
 * [com.emigo.app.ui.theme.EmberTheme]. Every other screen in the app deliberately follows
 * whatever theme the signed-in user picked, but that broke down here two different ways: image-
 * backed themes render `EmberBackground.asBrush` as fully transparent (this screen had no
 * background at all on those), and even on themes where the background worked, the page's own
 * accent color/gradient (badge, perk icons, CTA) shifted with the theme too — a purple theme's
 * "Gold" page read as purple, a blue theme's as blue, which undercuts a page whose entire point
 * is "gold." A subscription/paywall page having one consistent identity regardless of theme is
 * the same reasoning the auth flow's own [AuthPalette] already applies elsewhere — reused here
 * for its font choice (a considered, already-vetted pairing) but not its colors, which are that
 * flow's own fixed yellow, not this page's fixed gold/amber. */
private object GoldPalette {
    val backgroundTop = Color(0xFF201306)
    val backgroundBottom = Color(0xFF08060A)
    val panel = Color(0xFF241A10)
    val cream = Color(0xFFF5EFE3)
    val muted = Color(0xFFB7AC9C)
    val mutedDim = Color(0xFF7A7166)
    val accentStart = Color(0xFFFFD36E)
    val accentEnd = Color(0xFFFF9A3D)
    val onAccent = Color(0xFF2A1B08)

    val backgroundBrush = Brush.verticalGradient(listOf(backgroundTop, backgroundBottom))
    val accentBrush = Brush.linearGradient(listOf(accentStart, accentEnd))

    val display = AuthPalette.display
    val body = PublicSansFontFamily
}

@Composable
fun EmberGoldScreen(onBack: () -> Unit) {
    val colors = GoldPalette

    val perks = listOf(
        GoldPerk(Icons.Rounded.Restore, "Restore your streak", "Bring back a streak that slipped past midnight"),
        GoldPerk(Icons.Rounded.Palette, "Exclusive themes", "Unlock Cyber, Botanica, and Citrus looks"),
        GoldPerk(Icons.Rounded.PhotoLibrary, "Send from your gallery", "Share any photo, not just what you capture live"),
        GoldPerk(Icons.Rounded.Widgets, "Choose who's on your widget", "Pick exactly whose photos always show on your home screen"),
        GoldPerk(Icons.Rounded.AppShortcut, "Custom app icon", "Personalize Emigo's icon on your home screen", badge = "Coming soon"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundBrush)
            // Absorbs every touch on this screen so nothing behind it (still mounted underneath,
            // just visually covered by the slide-up) can receive taps meant for this one —
            // Compose doesn't block pointer input just because something is drawn on top; only an
            // element that itself claims pointer input does that. Most of this screen (everything
            // but the back button and the disabled CTA) had no handler at all, so touches fell
            // straight through to whatever page was open underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NestedScreenHeader(onBack = onBack)

        // Fixed header + fixed footer, scrollable middle — same three-zone shape
        // RegisterSharingStep uses for the same reason: on a short device (or with all five perks
        // plus a long detail line each), the old single fixed Column could push the CTA button
        // off the bottom of the screen entirely, with no way to reach it. The hero badge/title
        // scrolls away with the perks now rather than staying pinned, which is the right trade —
        // keeping it fixed would eat into the same limited height it's trying to free up.
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val badgeShape = RoundedCornerShape(26.dp)
            // A soft halo behind the badge, not a flat circle sitting on the background — a real
            // shadow read as "this is lit from within" rather than just another rounded rect, which
            // is most of what was making the old version feel flat rather than premium.
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(84.dp)
                    .shadow(elevation = 28.dp, shape = badgeShape, ambientColor = colors.accentStart, spotColor = colors.accentStart)
                    .background(colors.accentBrush, badgeShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(38.dp))
            }

            Text(
                text = "Emigo Gold",
                fontFamily = colors.display,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = colors.cream,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = "A little extra glow for your favorite people",
                fontFamily = colors.body,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 28.dp),
            )

            // Each perk gets its own card now, not one flat list — real separation between rows
            // rather than just a hairline's worth of vertical padding, which is most of what reads
            // as "basic" versus a real product page's own pricing/perks block.
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                perks.forEach { perk ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.panel, EmberRadii.cardShape)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Plain tinted glyph, no badge/circle behind it — icons in this app read as
                        // flat glyphs, never sitting on a colored background.
                        Icon(perk.icon, contentDescription = null, tint = colors.accentStart, modifier = Modifier.size(22.dp))
                        Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = perk.title,
                                    fontFamily = colors.body,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.cream,
                                )
                                if (perk.badge != null) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .background(colors.accentStart.copy(alpha = 0.16f), RoundedCornerShape(50))
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            text = perk.badge,
                                            fontFamily = colors.body,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accentStart,
                                        )
                                    }
                                }
                            }
                            Text(
                                text = perk.detail,
                                fontFamily = colors.body,
                                fontSize = 12.sp,
                                color = colors.muted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }

        // A real gradient-filled CTA now, the same brush the hero badge uses, rather than a flat
        // muted "Coming soon" box — that flat box was reading as an error/disabled state on
        // first glance, not a premium upgrade prompt. The button itself is honest about not
        // being wired up yet via the caption underneath, not by looking disabled.
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .background(colors.accentBrush, EmberRadii.buttonShape)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Upgrade to Gold",
                fontFamily = colors.body,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onAccent,
            )
        }
        Text(
            text = "Coming soon",
            fontFamily = colors.body,
            fontSize = 11.5.sp,
            color = colors.mutedDim,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
