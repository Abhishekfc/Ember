package com.ember.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.ember.app.ui.theme.PublicSansFontFamily

/** Ember's entire sign-in/create-account flow — a small step machine (see [AuthStep]), not a
 * NavHost: this screen and [LoginViewModel] are both self-contained, and MainActivity only ever
 * sees the two outcomes it already knew about (still loading, or [onAuthenticated] fired). One
 * question per screen on the new-account path, a single combined screen for returning users —
 * see [LoginViewModel]'s own doc comment for why those two paths are shaped differently. */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onAuthenticated: () -> Unit,
) {
    val colors = AuthPalette
    var screenSize by remember { mutableStateOf(Size.Zero) }

    BackHandler(enabled = viewModel.step != AuthStep.WELCOME) { viewModel.goBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize)),
    ) {
        AnimatedContent(
            targetState = viewModel.step,
            transitionSpec = {
                val forward = viewModel.isMovingForward
                val enter = slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> if (forward) width / 4 else -width / 4 } +
                    fadeIn(tween(240, easing = FastOutSlowInEasing))
                val exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { width -> if (forward) -width / 4 else width / 4 } +
                    fadeOut(tween(180))
                enter togetherWith exit
            },
            label = "authStep",
        ) { step ->
            when (step) {
                AuthStep.WELCOME -> WelcomeStep(
                    errorMessage = viewModel.errorMessage,
                    onCreateAccountClick = viewModel::onContinueWithEmailClicked,
                    onSignInClick = viewModel::onSignInClicked,
                )
                AuthStep.LOGIN -> LoginStep(viewModel, onAuthenticated)
                AuthStep.REGISTER_EMAIL -> RegisterEmailStep(viewModel)
                AuthStep.REGISTER_PASSWORD -> RegisterPasswordStep(viewModel, onAuthenticated)
            }
        }
    }
}

@Composable
private fun AuthStepScaffold(
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 32.dp)) {
        if (onBack != null) {
            AuthBackButton(onClick = onBack)
        }
        Spacer(modifier = Modifier.weight(1f))
        content()
        Spacer(modifier = Modifier.weight(1.4f))
    }
}

@Composable
private fun LoginStep(viewModel: LoginViewModel, onAuthenticated: () -> Unit) {
    val colors = AuthPalette
    val keyboard = LocalSoftwareKeyboardController.current
    val emailFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        emailFocus.requestFocus()
        keyboard?.show()
    }

    AuthStepScaffold(onBack = viewModel::goBack) {
        Text(text = "Welcome back", fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        AuthTextField(
            value = viewModel.loginIdentifier,
            onValueChange = viewModel::onLoginIdentifierChange,
            placeholder = "Email or username",
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next,
            modifier = Modifier.padding(top = 24.dp).focusRequester(emailFocus),
        )
        AuthTextField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            placeholder = "Password",
            keyboardType = KeyboardType.Text,
            visualTransformation = PasswordVisualTransformation(),
            imeAction = ImeAction.Done,
            onImeAction = { viewModel.submitLogin(onAuthenticated) },
            modifier = Modifier.padding(top = 10.dp),
        )
        if (viewModel.errorMessage != null) {
            AuthInlineMessage(text = viewModel.errorMessage.orEmpty(), modifier = Modifier.padding(top = 12.dp))
        }
        AuthPrimaryButton(
            text = "Log in",
            onClick = { viewModel.submitLogin(onAuthenticated) },
            isLoading = viewModel.isLoading,
            modifier = Modifier.padding(top = 22.dp),
        )
        AuthSecondaryButton(
            text = "Continue with Google",
            onClick = viewModel::onGoogleSignInClicked,
            leadingContent = { GoogleGlyph() },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun RegisterEmailStep(viewModel: LoginViewModel) {
    val colors = AuthPalette
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    AuthStepScaffold(onBack = viewModel::goBack) {
        Text(text = "What's your email?", fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        AuthTextField(
            value = viewModel.email,
            onValueChange = viewModel::onEmailChange,
            placeholder = "you@email.com",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::onEmailStepContinue,
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )
        AuthPrimaryButton(
            text = "Continue",
            onClick = viewModel::onEmailStepContinue,
            enabled = viewModel.isEmailValid,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun RegisterPasswordStep(viewModel: LoginViewModel, onAuthenticated: () -> Unit) {
    val colors = AuthPalette
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    AuthStepScaffold(onBack = viewModel::goBack) {
        Text(text = "Create a password", fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        AuthTextField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            placeholder = "Password",
            visualTransformation = PasswordVisualTransformation(),
            imeAction = ImeAction.Done,
            onImeAction = { viewModel.submitRegister(onAuthenticated) },
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )
        Text(
            text = "At least 8 characters",
            fontFamily = PublicSansFontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (viewModel.isPasswordValid) colors.glow else colors.mutedDim,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
        if (viewModel.errorMessage != null) {
            AuthInlineMessage(text = viewModel.errorMessage.orEmpty(), modifier = Modifier.padding(top = 10.dp))
        }
        AuthPrimaryButton(
            text = "Create account",
            onClick = { viewModel.submitRegister(onAuthenticated) },
            enabled = viewModel.isPasswordValid,
            isLoading = viewModel.isLoading,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}
