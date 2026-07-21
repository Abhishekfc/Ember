package com.ember.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ThemeScreen(
    viewModel: ThemeViewModel,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        Column(modifier = Modifier.padding(top = 32.dp, start = 20.dp, end = 20.dp)) {
            Text(
                text = "Theme",
                fontFamily = typography.display,
                fontSize = 22.sp,
                color = colors.cream,
            )
            Text(
                text = "Applies to the app and your widgets",
                fontFamily = typography.body,
                fontSize = 12.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(ThemeKey.entries.toList(), key = { it.name }) { option ->
                ThemeRow(
                    option = option,
                    isSelected = option == viewModel.selectedTheme,
                    onClick = { viewModel.selectTheme(option) },
                )
            }
        }
    }
}

@Composable
private fun ThemeRow(
    option: ThemeKey,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val activeColors = EmberTheme.colors
    val optionDefinition = emberThemeDefinition(option)
    val optionColors = optionDefinition.colors
    val rowShape = RoundedCornerShape(20.dp)
    val swatchShape = RoundedCornerShape(14.dp)

    val swatchSizePx = with(LocalDensity.current) { Size(56.dp.toPx(), 56.dp.toPx()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(activeColors.panel)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) optionColors.glow else activeColors.border,
                shape = rowShape,
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(swatchShape)
                .background(optionColors.background.asBrush(swatchSizePx))
                .border(1.dp, optionColors.border, swatchShape),
        )

        Column(
            modifier = Modifier
                .padding(start = 14.dp)
                .weight(1f),
        ) {
            Text(
                text = option.displayName,
                fontFamily = optionDefinition.typography.display,
                fontSize = 16.sp,
                color = activeColors.cream,
            )
            Text(
                text = if (option.locked) "Ember Gold" else "Free",
                fontFamily = EmberTheme.typography.body,
                fontSize = 12.sp,
                color = if (option.locked) optionColors.glow else activeColors.muted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (isSelected) optionColors.glow else activeColors.panel)
                .border(1.dp, if (isSelected) optionColors.glow else activeColors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Selected",
                    tint = optionColors.accentText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
