package com.ember.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.ui.theme.EmberAppTheme
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.ThemeKey

/**
 * The signature "light bleed" photo tile used across Home, Friends, and the recipient picker:
 * a soft radial glow behind the photo, colored per-friend by rotating through the theme's accent
 * palette, with the friend's name/timestamp overlaid at the bottom. When [photoUrl] is null (no
 * photo yet, or previewing the component), falls back to just the glow gradient.
 */
@Composable
fun GlowPhotoTile(
    size: Dp,
    modifier: Modifier = Modifier,
    seed: Int = 0,
    name: String? = null,
    time: String? = null,
    photoUrl: String? = null,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    val hues = listOf(colors.glow, colors.glow2, colors.violet, colors.glow)
    val hue = hues[seed.mod(hues.size)]
    val cornerRadius = size * 0.16f
    val shape = RoundedCornerShape(cornerRadius)

    val sizePx = with(LocalDensity.current) { Size(size.toPx(), size.toPx()) }
    val baseColor = if (colors.isLight) Color.Black.copy(alpha = 0x18 / 255f) else Color(0xFF2A2450)
    val tileBackground = cssAngleGradient(160f, listOf(hue.copy(alpha = 0x55 / 255f), baseColor), sizePx)
    val highlight = Brush.radialGradient(
        colors = listOf(hue.copy(alpha = 0x88 / 255f), Color.Transparent),
        center = Offset(sizePx.width * 0.3f, sizePx.height * 0.2f),
        radius = (sizePx.width * 0.6f).coerceAtLeast(1f),
    )
    val bottomScrim = Brush.verticalGradient(
        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
        startY = sizePx.height * 0.5f,
        endY = sizePx.height,
    )

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (colors.isLight) {
                    Modifier.shadow(10.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
                } else {
                    Modifier.shadow(20.dp, shape, ambientColor = hue.copy(alpha = 0.35f), spotColor = hue.copy(alpha = 0.35f))
                },
            )
            .clip(shape)
            .background(tileBackground)
            .then(
                if (colors.isLight) Modifier.border(1.dp, colors.border, shape) else Modifier,
            ),
    ) {
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(highlight))
        if (photoUrl != null && name != null) {
            Box(modifier = Modifier.fillMaxSize().background(bottomScrim))
        }

        if (name != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
            ) {
                Text(
                    text = name,
                    fontFamily = typography.display,
                    color = if (colors.isLight && photoUrl == null) colors.cream else Color(0xFFFBF8F3),
                    fontSize = if (size > 80.dp) 15.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (time != null) {
                    Text(
                        text = time,
                        fontFamily = typography.body,
                        color = if (colors.isLight && photoUrl == null) {
                            Color(0xFF3D2E28).copy(alpha = 0.6f)
                        } else {
                            Color.White.copy(alpha = 0.75f)
                        },
                        fontSize = 10.5.sp,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF12101E)
@Composable
private fun GlowPhotoTilePreview() {
    EmberAppTheme(themeKey = ThemeKey.EMBER) {
        GlowPhotoTile(size = 150.dp, seed = 0, name = "Meera", time = "2m ago")
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF2E9D8)
@Composable
private fun GlowPhotoTilePolaroidPreview() {
    EmberAppTheme(themeKey = ThemeKey.POLAROID) {
        GlowPhotoTile(size = 150.dp, seed = 1, name = "Rohan", time = "38m ago")
    }
}
