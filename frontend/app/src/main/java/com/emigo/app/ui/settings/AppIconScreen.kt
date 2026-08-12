package com.emigo.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.emigo.app.ui.components.NestedScreenHeader
import com.emigo.app.ui.components.emberButtonBrush
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily

/**
 * Same shape as ThemeScreen, deliberately: a dense grid of chips (icon preview, label, lock
 * badge on Gold-only options) with a fixed, non-scrolling "Apply"/"Get Emigo Gold" button pinned
 * at the bottom rather than living inside the grid's own scroll — the grid is what should scroll
 * on a long list, never the one action that commits the choice.
 */
@Composable
fun AppIconScreen(
    viewModel: AppIconViewModel,
    onBack: () -> Unit,
    onUpgradeToGold: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val context = LocalContext.current
    var screenSize by remember { mutableStateOf(Size.Zero) }
    var pendingIcon by remember(viewModel.selectedIcon) { mutableStateOf(viewModel.selectedIcon) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp)) {
            NestedScreenHeader(onBack = onBack, title = "App icon")
            Text(
                text = "Changes how Emigo looks on your home screen",
                fontFamily = typography.body,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(AppIconKey.entries.toList(), key = { it.name }) { option ->
                AppIconChip(
                    option = option,
                    isSelected = option == pendingIcon,
                    isGoldMember = viewModel.isGoldMember,
                    onClick = { pendingIcon = option },
                )
            }
        }

        // Same confirm-then-commit pattern as ThemeScreen: tapping a chip only stages it, this
        // bottom bar is the one place that actually applies (or redirects to Gold) — fixed
        // outside the grid's own scroll, so it's always reachable regardless of how many icon
        // choices this list ever grows to.
        val needsUpgrade = pendingIcon.locked && !viewModel.isGoldMember
        val canApply = pendingIcon != viewModel.selectedIcon && !needsUpgrade
        val buttonSizePx = Size(300f, 52f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .background(
                    if (canApply || needsUpgrade) emberButtonBrush(EmberTheme.key, colors, buttonSizePx) else SolidColor(colors.elevatedPanel),
                    EmberRadii.buttonShape,
                )
                .clickable(enabled = canApply || needsUpgrade) {
                    if (needsUpgrade) {
                        onUpgradeToGold()
                    } else {
                        viewModel.selectIcon(context.applicationContext, pendingIcon)
                    }
                }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when {
                    needsUpgrade -> "Get Emigo Gold"
                    canApply -> "Apply icon"
                    else -> "Applied"
                },
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (canApply || needsUpgrade) colors.accentText else colors.mutedDim,
            )
        }
    }
}

@Composable
private fun AppIconChip(
    option: AppIconKey,
    isSelected: Boolean,
    isGoldMember: Boolean,
    onClick: () -> Unit,
) {
    val colors = EmberTheme.colors
    val chipShape = RoundedCornerShape(16.dp)
    // ic_launcher is an <adaptive-icon> XML resource (background + foreground layers), not a
    // plain rasterized drawable — painterResource() only supports VectorDrawables and real
    // image formats (PNG/JPG/WEBP) and throws IllegalArgumentException on anything else, which
    // crashed this whole screen the instant it composed. PackageManager already knows how to
    // composite an adaptive icon into a real bitmap; this is the exact same pattern
    // rememberAppIcon (LoginScreen.kt) already uses for *other* apps' launcher icons, applied to
    // this app's own.
    val context = LocalContext.current
    val iconBitmap = remember {
        runCatching { context.packageManager.getApplicationIcon(context.packageName).toBitmap().asImageBitmap() }.getOrNull()
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(chipShape)
                .background(colors.panel)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) colors.glow else Color.White.copy(alpha = 0.14f),
                    shape = chipShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // Same placeholder launcher art for every option today — the real, distinct icon set
            // for each choice hasn't been supplied yet (see AndroidManifest.xml's own note on the
            // Gold alias). Swapping in real art per option is a resource change, not a UI change.
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(0.62f).clip(RoundedCornerShape(12.dp)),
                )
            }

            // Only an *inaccessible* Gold icon gets the padlock — a confirmed subscriber sees
            // every option the same way, with nothing left implying they still don't have it.
            if (option.locked && !isGoldMember) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.glow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.accentText, modifier = Modifier.size(14.dp))
                }
            }
        }

        Text(
            text = option.displayName,
            fontFamily = PublicSansFontFamily,
            fontSize = 12.5.sp,
            color = colors.cream,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = if (option.locked) "Emigo Gold" else "Free",
            fontFamily = PublicSansFontFamily,
            fontSize = 10.5.sp,
            color = if (option.locked) colors.glow else colors.mutedDim,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
