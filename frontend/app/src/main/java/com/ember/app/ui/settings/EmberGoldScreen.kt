package com.ember.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

private data class GoldPerk(val icon: ImageVector, val title: String, val detail: String)

@Composable
fun EmberGoldScreen() {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val panelShape = RoundedCornerShape(18.dp)

    val perks = listOf(
        GoldPerk(Icons.Filled.Palette, "Exclusive themes", "Unlock Cyber, Botanica, and Citrus looks"),
        GoldPerk(Icons.Filled.History, "Longer photo history", "Friends' photos stay for 7 days instead of 24 hours"),
        GoldPerk(Icons.Filled.Favorite, "Support Ember", "Help keep the lights glowing for everyone"),
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(top = 32.dp, start = 20.dp, end = 20.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val badgeSizePx = Size(72f, 72f)
        Box(
            modifier = Modifier
                .padding(top = 26.dp)
                .size(72.dp)
                .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), badgeSizePx), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(32.dp))
        }

        Text(
            text = "Ember Gold",
            fontFamily = typography.display,
            fontSize = 26.sp,
            color = colors.cream,
            modifier = Modifier.padding(top = 18.dp),
        )
        Text(
            text = "A little extra glow for your favorite people",
            fontFamily = PublicSansFontFamily,
            fontSize = 12.5.sp,
            color = colors.muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, bottom = 26.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, panelShape)
                .border(1.dp, colors.border, panelShape)
                .padding(vertical = 4.dp),
        ) {
            perks.forEach { perk ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(perk.icon, contentDescription = null, tint = colors.glow, modifier = Modifier.size(18.dp))
                    Column(modifier = Modifier.padding(start = 14.dp)) {
                        Text(
                            text = perk.title,
                            fontFamily = PublicSansFontFamily,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.cream,
                        )
                        Text(
                            text = perk.detail,
                            fontFamily = PublicSansFontFamily,
                            fontSize = 11.5.sp,
                            color = colors.muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel, RoundedCornerShape(16.dp))
                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Coming soon",
                fontFamily = PublicSansFontFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = colors.mutedDim,
            )
        }
    }
}
