package com.emigo.app.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.AppShortcut
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Widgets
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.data.GoldPeriod
import com.emigo.app.data.GoldPlan
import com.emigo.app.ui.auth.AuthPalette
import com.emigo.app.ui.components.NestedScreenHeader
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.PublicSansFontFamily
import kotlin.math.roundToInt

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
    val danger = Color(0xFFE8927C)

    val backgroundBrush = Brush.verticalGradient(listOf(backgroundTop, backgroundBottom))
    val accentBrush = Brush.linearGradient(listOf(accentStart, accentEnd))

    val display = AuthPalette.display
    val body = PublicSansFontFamily
}

@Composable
fun EmberGoldScreen(
    viewModel: EmberGoldViewModel,
    onBack: () -> Unit,
    /** Fired once when a purchase is confirmed, so the rest of the app can drop its "not Gold"
     * assumptions without waiting for its own next status check. */
    onGoldActivated: () -> Unit,
) {
    val colors = GoldPalette
    val state = viewModel.uiState
    val context = LocalContext.current
    // The plan choice + purchase button live in a separate bottom sheet (GoldPlanSheet) over this
    // page, not inline in the scrollable perks list — tapping "Upgrade to Gold" below opens it.
    var showPlanSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.onShown() }
    // Keyed on isGold itself, not just a fresh in-session purchase — a purchase this screen
    // reconciles quietly (findActivePurchase, on a reinstall/new device where the backend hadn't
    // heard about it yet) flips isGold true without ever setting justActivated. Gating this on
    // justActivated only would fire the success flash correctly for a live purchase but leave the
    // rest of the app — Camera's gallery gate, themes, the widget picker — still reading "not
    // Gold" until some unrelated screen happened to re-check. Runs once per rising edge only
    // (LaunchedEffect re-fires on key change, not every recomposition), so revisiting an
    // already-Gold paywall just re-confirms state the app already has — harmless.
    LaunchedEffect(state.isGold) {
        if (state.isGold) {
            onGoldActivated()
            showPlanSheet = false
        }
    }
    LaunchedEffect(state.justActivated) {
        if (state.justActivated) viewModel.consumeJustActivated()
    }

    val perks = listOf(
        GoldPerk(Icons.Rounded.Restore, "Restore your streak", "Bring back a streak that slipped past midnight"),
        GoldPerk(Icons.Rounded.Palette, "Exclusive themes", "Unlock Cyber, Botanica, and Citrus looks"),
        GoldPerk(Icons.Rounded.PhotoLibrary, "Send from your gallery", "Share any photo, not just what you capture live"),
        GoldPerk(Icons.Rounded.Widgets, "Choose who's on your widget", "Pick exactly whose photos always show on your home screen"),
    )

    Box(modifier = Modifier.fillMaxSize()) {
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
                text = if (state.isGold) "Thank you for being an Emigo Gold member" else "A little extra glow for your favorite people",
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

        Spacer(modifier = Modifier.height(14.dp))
        GoldFooter(
            state = state,
            // Opens the plan-choice sheet rather than buying directly — GoldFooter's own
            // `enabled = state.canBuy` already keeps this from firing until plans are actually
            // loaded, so there's nothing to gate here.
            onUpgrade = { showPlanSheet = true },
            onManage = {
                val productId = state.selectedProductId ?: state.plans.firstOrNull()?.productId
                val uri = if (productId != null) {
                    "https://play.google.com/store/account/subscriptions?sku=$productId&package=${context.packageName}"
                } else {
                    "https://play.google.com/store/account/subscriptions"
                }
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
            },
            onDone = onBack,
        )
    }

    // A separate sheet rather than the inline picker this used to be — deliberately not the same
    // shape as the full-screen page above it (which slides up from Settings the same way this
    // slides up from here): partial height, its own scrim, dismissible by itself. Always composed
    // (like the GOLD screen itself is in MainActivity) so AnimatedVisibility's exit transition has
    // something to animate rather than being torn out mid-slide-down.
    AnimatedVisibility(
        visible = showPlanSheet,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
    ) {
        GoldPlanSheet(
            state = state,
            onSelectPlan = viewModel::selectPlan,
            onContinue = { context.findActivity()?.let { viewModel.purchase(it) } },
            onDismiss = { showPlanSheet = false },
        )
    }
    }
}

@Composable
private fun GoldPlanSheet(
    state: GoldUiState,
    onSelectPlan: (String) -> Unit,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GoldPalette
    // A purchase Play is already processing shouldn't be abandonable by an accidental back-press
    // or scrim tap — the button's own label already communicates progress ("Confirming…" etc.);
    // losing the sheet mid-flight would just make it look like the purchase itself got cancelled.
    val dismissable = !state.purchaseInFlight && !state.verifying
    BackHandler(enabled = dismissable, onBack = onDismiss)

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim — the "nothing behind this can be tapped" requirement, same as the full-screen
        // Gold page's own touch-absorbing background one level up, just for this sheet's own
        // backdrop (the dimmed Gold page) instead of whatever was behind that. Tapping it dismisses
        // the sheet, like tapping outside any bottom sheet, except mid-purchase.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = dismissable,
                    onClick = onDismiss,
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                // Swallows its own taps so they don't fall through to the scrim's dismiss handler
                // underneath — same technique the outer page uses against whatever's behind it.
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .background(colors.panel, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
            // Grabber — the one visual borrowed from how a bottom sheet usually reads, kept
            // deliberately plain (a hairline, not a pill button) so it doesn't compete with the
            // close icon on the same row.
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .background(colors.mutedDim.copy(alpha = 0.5f), RoundedCornerShape(50)),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Choose your plan",
                    fontFamily = colors.display,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.cream,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = colors.mutedDim,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = dismissable,
                            onClick = onDismiss,
                        ),
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                state.plans.forEach { plan ->
                    PlanRow(
                        plan = plan,
                        selected = plan.productId == state.selectedProductId,
                        savingsLabel = if (plan.period == GoldPeriod.YEARLY && state.yearlySavingsFraction != null) {
                            "Save ${(state.yearlySavingsFraction * 100).roundToInt()}%"
                        } else null,
                        onClick = { onSelectPlan(plan.productId) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            val label = when {
                state.verifying -> "Confirming…"
                state.purchaseInFlight -> "Opening Google Play…"
                state.pendingPayment -> "Payment pending"
                else -> "Continue"
            }
            FooterButton(
                text = label,
                onClick = onContinue,
                enabled = state.canBuy,
                showSpinner = state.purchaseInFlight || state.verifying,
            )

            val caption = state.error ?: "Cancel anytime in Google Play"
            Text(
                text = caption,
                fontFamily = colors.body,
                fontSize = 11.5.sp,
                color = if (state.error != null) colors.danger else colors.mutedDim,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun PlanRow(
    plan: GoldPlan,
    selected: Boolean,
    savingsLabel: String?,
    onClick: () -> Unit,
) {
    val colors = GoldPalette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, EmberRadii.cardShape)
            .then(
                if (selected) Modifier.border(1.5.dp, colors.accentStart, EmberRadii.cardShape)
                else Modifier,
            )
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (plan.period == GoldPeriod.YEARLY) "Yearly" else "Monthly",
                    fontFamily = colors.body,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.cream,
                )
                if (savingsLabel != null) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .background(colors.accentStart.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = savingsLabel,
                            fontFamily = colors.body,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.accentStart,
                        )
                    }
                }
            }
        }
        Text(
            text = plan.formattedPrice + if (plan.period == GoldPeriod.YEARLY) " / yr" else " / mo",
            fontFamily = colors.body,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.cream,
        )
    }
}

@Composable
private fun GoldFooter(
    state: GoldUiState,
    onUpgrade: () -> Unit,
    onManage: () -> Unit,
    onDone: () -> Unit,
) {
    val colors = GoldPalette

    if (state.isGold) {
        FooterButton(text = "Manage subscription", onClick = onManage)
        Text(
            text = "You're on Emigo Gold",
            fontFamily = colors.body,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.accentStart,
            modifier = Modifier.padding(top = 10.dp).clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone,
            ),
        )
        return
    }

    val label = when {
        state.loadingPlans -> "Loading…"
        state.billingUnavailable -> "Not available yet"
        state.verifying -> "Confirming…"
        state.purchaseInFlight -> "Opening Google Play…"
        state.pendingPayment -> "Payment pending"
        else -> "Upgrade to Gold"
    }
    val enabled = state.canBuy
    FooterButton(
        text = label,
        onClick = onUpgrade,
        enabled = enabled,
        showSpinner = state.purchaseInFlight || state.verifying,
    )

    val caption = when {
        state.error != null -> state.error
        state.pendingPayment -> "Payment pending — Gold unlocks once it clears"
        state.billingUnavailable -> "Check back soon"
        else -> "Cancel anytime in Google Play"
    }
    Text(
        text = caption,
        fontFamily = colors.body,
        fontSize = 11.5.sp,
        color = if (state.error != null) colors.danger else colors.mutedDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 10.dp),
    )
}

@Composable
private fun FooterButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    showSpinner: Boolean = false,
) {
    val colors = GoldPalette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (enabled) colors.accentBrush else Brush.linearGradient(listOf(colors.panel, colors.panel)),
                EmberRadii.buttonShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showSpinner) {
                CircularProgressIndicator(
                    color = colors.onAccent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(15.dp).padding(end = 8.dp),
                )
            }
            Text(
                text = text,
                fontFamily = colors.body,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) colors.onAccent else colors.mutedDim,
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
