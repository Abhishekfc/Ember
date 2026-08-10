package com.ember.app.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.ui.theme.PublicSansFontFamily

/** The pill-shaped, gradient-filled call to action every auth step ends on ("Continue with
 * Email", the per-step "Continue" buttons, "Log in"). No glow/shadow effect — the gradient fill
 * itself (the same colors.glow → colors.glow2 sweep already used for streak rings and other
 * accents elsewhere in the app) is what marks it as the primary action, not a blurred halo
 * behind it. Compresses slightly on press (a real spring, not a linear tween) — the one
 * micro-interaction every button in this flow shares. */
@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val colors = AuthPalette
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val canInteract = enabled && !isLoading
    val scale by animateFloatAsState(
        targetValue = if (isPressed && canInteract) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "authPrimaryButtonScale",
    )
    val shape = RoundedCornerShape(50)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                if (canInteract) {
                    Brush.horizontalGradient(listOf(colors.accentFill, colors.accentFill))
                } else {
                    Brush.horizontalGradient(listOf(colors.mutedDim.copy(alpha = 0.35f), colors.mutedDim.copy(alpha = 0.35f)))
                },
            )
            .clickable(interactionSource = interactionSource, indication = null, enabled = canInteract, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val textColor = if (canInteract) colors.accentText else colors.mutedDim
        AnimatedContent(targetState = isLoading, label = "authPrimaryButtonContent") { loading ->
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = textColor, strokeWidth = 2.dp)
            } else {
                Text(text = text, fontFamily = PublicSansFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = textColor)
            }
        }
    }
}

/** The quieter alternative-method button (Google sign-in) — outlined rather than filled, so it
 * reads as secondary to whichever [AuthPrimaryButton] shares its screen without competing for
 * attention. Same press-compress micro-interaction as the primary button, just applied to an
 * outline instead of a gradient fill. */
@Composable
fun AuthSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable () -> Unit = {},
) {
    val colors = AuthPalette
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "authSecondaryButtonScale",
    )
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingContent()
        Text(
            text = text,
            fontFamily = PublicSansFontFamily,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            color = colors.cream,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** A plain, monochrome "G" glyph stands in for Google's own multi-color mark — this app doesn't
 * bundle Google's actual brand asset, and drawing a look-alike of it would misrepresent an
 * asset that isn't really there. Honest placeholder over a fabricated logo. */
@Composable
fun GoogleGlyph(modifier: Modifier = Modifier) {
    val colors = AuthPalette
    Box(
        modifier = modifier.size(20.dp).clip(CircleShape).border(1.5.dp, colors.mutedDim, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "G", fontFamily = PublicSansFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colors.cream)
    }
}

/** A single-line field with no icon and a larger type size than the rest of the app's forms —
 * each auth step asks exactly one question, so the field carries more visual weight than a
 * typical compact row would. [trailingIcon] is null by default (every existing field stays
 * exactly as-is); a password field passes a show/hide toggle here — see [AuthPasswordField]. */
@Composable
fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val colors = AuthPalette
    val shape = RoundedCornerShape(16.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panel)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = placeholder, fontFamily = PublicSansFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.mutedDim)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = colors.cream),
                cursorBrush = SolidColor(colors.glow),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(
                    onNext = { onImeAction() },
                    onDone = { onImeAction() },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailingIcon != null) {
            trailingIcon()
        }
    }
}

/** [AuthTextField] specialized for a password: owns its own show/hide state and swaps
 * [PasswordVisualTransformation] for a plain one, with an eye-icon toggle — the standard
 * password-field pattern every major app uses, which this flow was missing entirely (typing a
 * password here had no way to double check what was typed). */
@Composable
fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
) {
    val colors = AuthPalette
    var visible by remember { mutableStateOf(false) }

    AuthTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        keyboardType = KeyboardType.Password,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        imeAction = imeAction,
        onImeAction = onImeAction,
        modifier = modifier,
        trailingIcon = {
            Icon(
                imageVector = if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                contentDescription = if (visible) "Hide password" else "Show password",
                tint = colors.mutedDim,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(20.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { visible = !visible },
            )
        },
    )
}

/** Consistent circular back affordance, top-left, every non-entry step of the flow uses. */
@Composable
fun AuthBackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = AuthPalette
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(colors.panel)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.cream, modifier = Modifier.size(18.dp))
    }
}

/** Inline validation/error line — a gentle fade, not an abrupt pop-in, and a warm muted-amber
 * tone (colors.glow2) rather than a harsh red, matching the calm register the rest of the app's
 * copy keeps even when something's wrong. */
@Composable
fun AuthInlineMessage(text: String, modifier: Modifier = Modifier) {
    val colors = AuthPalette
    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.Bold,
        color = colors.glow2,
        modifier = modifier,
    )
}

/** Fades/slides its content in after [delayMillis] — used to stagger the welcome screen's
 * wordmark → tagline → buttons entrance ([WelcomeStep]) so they arrive as one small orchestrated
 * moment instead of popping in all at once. */
@Composable
fun StaggeredEntrance(delayMillis: Int, content: @Composable () -> Unit) {
    val state = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { state.targetState = true }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(450, delayMillis = delayMillis, easing = FastOutSlowInEasing)) +
            slideInVertically(tween(450, delayMillis = delayMillis, easing = FastOutSlowInEasing)) { it / 3 },
    ) {
        content()
    }
}
