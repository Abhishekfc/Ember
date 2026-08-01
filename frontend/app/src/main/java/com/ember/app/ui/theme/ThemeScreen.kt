package com.ember.app.ui.theme

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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.ui.components.NestedScreenHeader
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.PublicSansFontFamily

/**
 * Small color chips in a dense grid, label underneath — not a full-height card per theme. A
 * swatch this size is honest about being "just a color," so it doesn't read as an empty/broken
 * image the way a nearly-full-screen block with one line of text at the bottom did. Picking a
 * theme now stages it (border + checkmark on the chip) rather than applying instantly; an Apply
 * bar at the bottom commits it, matching the confirm-then-commit pattern the recipient picker
 * already uses for its own selection screen.
 */
@Composable
fun ThemeScreen(
    viewModel: ThemeViewModel,
    onBack: () -> Unit,
    onPreview: (ThemeKey?) -> Unit,
    onUpgradeToGold: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    var pendingTheme by remember(viewModel.selectedTheme) { mutableStateOf(viewModel.selectedTheme) }

    // Leaving this screen without applying (back button, or navigating elsewhere) must drop
    // the preview override — otherwise the rest of the app would keep showing a theme that was
    // only ever browsed, not actually chosen, until the process restarts.
    DisposableEffect(Unit) {
        onDispose { onPreview(null) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp)) {
            NestedScreenHeader(onBack = onBack, title = "Theme")
            Text(
                text = "Applies to the app and your widgets",
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
            items(ThemeKey.entries.toList(), key = { it.name }) { option ->
                ThemeChip(
                    option = option,
                    isSelected = option == pendingTheme,
                    isGoldMember = viewModel.isGoldMember,
                    onClick = {
                        pendingTheme = option
                        onPreview(option)
                    },
                )
            }
        }

        // Staging/previewing any theme (tapping a chip, above) stays free for everyone, locked or
        // not — a harmless "see what it looks like" that never persists anything, and the closest
        // thing to a try-before-you-buy moment for the Gold themes. The gate is here instead, on
        // actually applying one: a locked theme with no subscription swaps this button's own
        // label/action to send you to Ember Gold rather than silently doing nothing or, worse,
        // applying anyway.
        val needsUpgrade = pendingTheme.locked && !viewModel.isGoldMember
        val canApply = pendingTheme != viewModel.selectedTheme && !needsUpgrade
        val buttonSizePx = Size(300f, 52f)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .background(
                    if (canApply || needsUpgrade) cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx) else SolidColor(colors.elevatedPanel),
                    EmberRadii.buttonShape,
                )
                .clickable(enabled = canApply || needsUpgrade) {
                    if (needsUpgrade) {
                        onUpgradeToGold()
                    } else {
                        viewModel.selectTheme(pendingTheme)
                        onPreview(null)
                    }
                }
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = when {
                    needsUpgrade -> "Get Ember Gold"
                    canApply -> "Apply theme"
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
private fun ThemeChip(
    option: ThemeKey,
    isSelected: Boolean,
    isGoldMember: Boolean,
    onClick: () -> Unit,
) {
    val activeColors = EmberTheme.colors
    val optionDefinition = emberThemeDefinition(option)
    val optionColors = optionDefinition.colors
    val chipShape = RoundedCornerShape(16.dp)
    var chipSizePx by remember { mutableStateOf(Size(110f, 110f)) }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .onSizeChanged { chipSizePx = Size(it.width.toFloat(), it.height.toFloat()) }
                .clip(chipShape)
                .background(optionColors.background.asBrush(chipSizePx))
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) optionColors.glow else Color.White.copy(alpha = 0.14f),
                    shape = chipShape,
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // Only an *inaccessible* Gold theme gets the padlock — a confirmed subscriber sees
            // every theme the same way, with nothing left implying they still don't have it.
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
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(optionColors.glow),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = optionColors.accentText, modifier = Modifier.size(14.dp))
                }
            }
        }

        Text(
            text = option.displayName,
            fontFamily = PublicSansFontFamily,
            fontSize = 12.5.sp,
            color = activeColors.cream,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = if (option.locked) "Ember Gold" else "Free",
            fontFamily = PublicSansFontFamily,
            fontSize = 10.5.sp,
            color = if (option.locked) optionColors.glow else activeColors.mutedDim,
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}
