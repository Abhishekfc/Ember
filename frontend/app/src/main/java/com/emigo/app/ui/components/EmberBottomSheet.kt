package com.emigo.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.ui.auth.AuthPalette
import com.emigo.app.ui.theme.EmberRadii
import com.emigo.app.ui.theme.EmberTheme
import com.emigo.app.ui.theme.PublicSansFontFamily
import kotlinx.coroutines.launch

/** A fixed near-black palette for the sheets, like the app's dialogs use (see EditDialogShell). The
 * theme's own panel colors can sit almost on top of each other, which turned the first version of
 * these sheets into a muddy grey slab with unreadable text; fixed colors keep them crisp in every
 * theme. Only the accent (the icon and the main button) still follows the theme. */
internal object SheetPalette {
    val card = Color(0xFF19181A)
    val hairline = Color(0x1AFFFFFF)
    val field = Color(0xFF242226)
    val track = Color(0xFF3A383C)
    val cream = Color(0xFFF3EFE6)
    val muted = Color(0xFFA39D9B)
}

/**
 * A sheet that slides up from the bottom and covers about half the screen, with a grabber handle
 * and big rounded top corners, and can be dragged down or tapped away to close. Flat and dark like
 * the rest of the app: no shadow, glow or blur.
 *
 * [header] sits at the top and [actions] at the bottom, so the buttons stay put whatever the text
 * above them says. [actions] gets a `dismiss` that slides the sheet away before calling
 * [onDismiss], for a "Maybe later" button; dragging or tapping outside calls [onDismiss] directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmberBottomSheet(
    onDismiss: () -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val halfScreen = (LocalConfiguration.current.screenHeightDp / 2).dp
    val dismiss: () -> Unit = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = EmberRadii.bottomSheetShape,
        containerColor = SheetPalette.card,
        contentColor = SheetPalette.cream,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
        dragHandle = { Grabber() },
    ) {
        // At least half the screen, more if the text needs it. SpaceBetween puts the extra room
        // between the header and the buttons instead of under them.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = halfScreen)
                .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, content = header)
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = { actions(dismiss) },
            )
        }
    }
}

@Composable
private fun Grabber() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 6.dp)
            .size(width = 40.dp, height = 5.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.22f)),
    )
}

/** The sheet's big title, in the same serif the app's dialogs use. */
@Composable
fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = AuthPalette.display,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        color = SheetPalette.cream,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** The quieter line under a [SheetTitle]. */
@Composable
fun SheetMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        color = SheetPalette.muted,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

private val SheetButtonShape = RoundedCornerShape(16.dp)

/** The one main action: the app's usual button fill, 54dp tall, shrinking a touch while pressed.
 * [isLoading] swaps the label for a spinner and ignores taps. */
@Composable
fun SheetPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    isLoading: Boolean = false,
) {
    val colors = EmberTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(SheetButtonShape)
            .background(emberButtonBrush(EmberTheme.key, colors), SheetButtonShape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = !isLoading, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = colors.accentText, strokeWidth = 2.dp)
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(20.dp))
                Box(modifier = Modifier.width(8.dp))
            }
            Text(text = text, fontFamily = PublicSansFontFamily, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = colors.accentText)
        }
    }
}

/** The other way out: same size as [SheetPrimaryButton] but quiet, a plain panel with a hairline. */
@Composable
fun SheetSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = EmberTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val scale = pressScale(interactionSource)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(SheetButtonShape)
            .background(SheetPalette.field, SheetButtonShape)
            .border(BorderStroke(1.dp, SheetPalette.hairline), SheetButtonShape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.glow, modifier = Modifier.size(20.dp))
            Box(modifier = Modifier.width(8.dp))
        }
        Text(text = text, fontFamily = PublicSansFontFamily, fontSize = 15.5.sp, fontWeight = FontWeight.SemiBold, color = SheetPalette.cream)
    }
}

/** "Maybe later": plain text, no box. */
@Composable
fun SheetTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 14.5.sp,
        fontWeight = FontWeight.Medium,
        color = SheetPalette.muted,
        modifier = modifier
            .padding(top = 6.dp)
            .clip(SheetButtonShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** Buttons give a little under the finger and spring back, the way iOS ones do. */
@Composable
private fun pressScale(interactionSource: MutableInteractionSource): Float {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "sheetButtonPress",
    )
    return scale
}
