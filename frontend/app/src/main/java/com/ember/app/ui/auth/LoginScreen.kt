package com.ember.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onAuthenticated: () -> Unit,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .padding(start = 26.dp, end = 26.dp, top = 90.dp, bottom = 30.dp),
    ) {
        val badgeSizePx = with(LocalDensity.current) { Size(56.dp.toPx(), 56.dp.toPx()) }
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), badgeSizePx)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(24.dp))
        }

        Box(modifier = Modifier.padding(top = 20.dp)) {
            Text(
                text = if (viewModel.mode == AuthMode.LOGIN) "Welcome to Ember" else "Create your account",
                fontFamily = typography.display,
                fontSize = 28.sp,
                color = colors.cream,
            )
        }
        Text(
            text = "A photo from someone you love, glowing on your home screen.",
            fontFamily = typography.body,
            fontSize = 13.sp,
            color = colors.muted,
            modifier = Modifier.padding(top = 6.dp, bottom = 36.dp),
        )

        if (viewModel.mode == AuthMode.REGISTER) {
            FieldLabel("NAME")
            EmberTextField(
                value = viewModel.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                placeholder = "Your name",
                icon = Icons.Filled.Person,
                keyboardType = KeyboardType.Text,
            )

            FieldLabel("USERNAME", topPadding = 14.dp)
            EmberTextField(
                value = viewModel.username,
                onValueChange = viewModel::onUsernameChange,
                placeholder = "How friends find you",
                icon = Icons.Filled.AlternateEmail,
                keyboardType = KeyboardType.Text,
            )
        }

        FieldLabel("EMAIL")
        EmberTextField(
            value = viewModel.email,
            onValueChange = viewModel::onEmailChange,
            placeholder = "you@email.com",
            icon = Icons.Filled.Mail,
            keyboardType = KeyboardType.Email,
        )

        FieldLabel("PASSWORD", topPadding = 14.dp)
        EmberTextField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            placeholder = "••••••••",
            icon = Icons.Filled.Lock,
            // Deliberately KeyboardType.Text, not .Password: masking still comes from
            // PasswordVisualTransformation below, but .Password sets the underlying InputType's
            // password variation flag, which is what triggers Google Password Manager's "Save
            // password?" prompt on every login — the manifest importantForAutofill exclusion
            // didn't suppress it in practice on this device, so this is the actual working fix.
            keyboardType = KeyboardType.Text,
            visualTransformation = PasswordVisualTransformation(),
            imeAction = ImeAction.Done,
        )

        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 12.sp,
                color = colors.glow2,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        val buttonSizePx = with(LocalDensity.current) { Size(300.dp.toPx(), 52.dp.toPx()) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx))
                .clickable(enabled = !viewModel.isLoading) { viewModel.submit(onAuthenticated) }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.accentText, strokeWidth = 2.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(
                        text = "Continue",
                        fontFamily = PublicSansFontFamily,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.accentText,
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = colors.accentText,
                        modifier = Modifier.padding(start = 8.dp).size(16.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp)
                .clickable { viewModel.toggleMode() },
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = if (viewModel.mode == AuthMode.LOGIN) "New here? " else "Already have an account? ",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.sp,
                color = colors.mutedDim,
            )
            Text(
                text = if (viewModel.mode == AuthMode.LOGIN) "Create an account" else "Log in",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.glow,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val colors = EmberTheme.colors
    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 11.5.sp,
        color = colors.mutedDim,
        modifier = Modifier.padding(top = topPadding, bottom = 6.dp),
    )
}

@Composable
private fun EmberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = EmberTheme.colors
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panel)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.mutedDim, modifier = Modifier.size(16.dp))
        Box(modifier = Modifier.padding(start = 10.dp).fillMaxWidth()) {
            if (value.isEmpty()) {
                Text(text = placeholder, fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.mutedDim)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.cream),
                cursorBrush = SolidColor(colors.glow),
                visualTransformation = visualTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
