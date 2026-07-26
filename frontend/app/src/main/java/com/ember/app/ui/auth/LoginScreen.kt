package com.ember.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.ember.app.ui.profile.UsernameCheckState
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
                AuthStep.REGISTER_PASSWORD -> RegisterPasswordStep(viewModel)
                AuthStep.REGISTER_NAME -> RegisterNameStep(viewModel)
                AuthStep.REGISTER_USERNAME -> RegisterUsernameStep(viewModel)
                AuthStep.REGISTER_SHARING -> RegisterSharingStep(viewModel, onAuthenticated)
            }
        }
    }
}

@Composable
private fun AuthStepScaffold(
    onBack: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
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
private fun RegisterPasswordStep(viewModel: LoginViewModel) {
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
            onImeAction = viewModel::submitRegister,
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
            onClick = viewModel::submitRegister,
            enabled = viewModel.isPasswordValid,
            isLoading = viewModel.isLoading,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

@Composable
private fun RegisterNameStep(viewModel: LoginViewModel) {
    val colors = AuthPalette
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    AuthStepScaffold(onBack = viewModel::goBack) {
        Text(text = "What's your name?", fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        AuthTextField(
            value = viewModel.firstName,
            onValueChange = viewModel::onFirstNameChange,
            placeholder = "First name",
            imeAction = ImeAction.Next,
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )
        AuthTextField(
            value = viewModel.lastName,
            onValueChange = viewModel::onLastNameChange,
            placeholder = "Last name",
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submitName,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (viewModel.errorMessage != null) {
            AuthInlineMessage(text = viewModel.errorMessage.orEmpty(), modifier = Modifier.padding(top = 12.dp))
        }
        AuthPrimaryButton(
            text = "Continue",
            onClick = viewModel::submitName,
            enabled = viewModel.isNameValid,
            isLoading = viewModel.isLoading,
            modifier = Modifier.padding(top = 22.dp),
        )
    }
}

@Composable
private fun RegisterUsernameStep(viewModel: LoginViewModel) {
    val colors = AuthPalette
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    val check = viewModel.usernameCheck

    AuthStepScaffold(onBack = viewModel::goBack) {
        Text(text = "Pick a username", fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        // Plain field, no availability hint inside it — status only ever appears below, never
        // overlapping the placeholder/typed text.
        AuthTextField(
            value = viewModel.usernameDraft,
            onValueChange = viewModel::onUsernameDraftChange,
            placeholder = "Username",
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submitUsername,
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )

        // Nothing reserved when there's nothing to say yet (Idle) — tight, clean gap to the
        // button by default. animateContentSize means the button smoothly glides down (rather
        // than snapping) the moment there's a status line or suggestion chips to make room for.
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            val statusText = viewModel.errorMessage ?: when (check) {
                UsernameCheckState.Checking -> "Checking availability…"
                UsernameCheckState.Available -> "Username available"
                is UsernameCheckState.Taken -> "Username already taken"
                UsernameCheckState.Idle -> null
            }
            if (statusText != null) {
                val statusColor = when {
                    viewModel.errorMessage != null || check is UsernameCheckState.Taken -> colors.glow2
                    check == UsernameCheckState.Available -> colors.glow
                    else -> colors.muted
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 14.dp)) {
                    if (check == UsernameCheckState.Available && viewModel.errorMessage == null) {
                        AvailableTickIcon(modifier = Modifier.padding(end = 6.dp))
                    }
                    Text(text = statusText, fontFamily = PublicSansFontFamily, fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = statusColor)
                }
            }

            val suggestions = (check as? UsernameCheckState.Taken)?.suggestions.orEmpty()
            if (viewModel.errorMessage == null && suggestions.isNotEmpty()) {
                Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { suggestion ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.glow.copy(alpha = 0.1f))
                                .border(1.dp, colors.glow.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .clickable { viewModel.pickUsernameSuggestion(suggestion) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = "@$suggestion",
                                fontFamily = PublicSansFontFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = colors.cream,
                            )
                        }
                    }
                }
            }
        }

        AuthPrimaryButton(
            text = "Continue",
            onClick = viewModel::submitUsername,
            enabled = check is UsernameCheckState.Available,
            isLoading = viewModel.isLoading,
            modifier = Modifier.padding(top = 20.dp),
        )
    }
}

/** A small checkmark that pops in with a springy bounce rather than just appearing — the one
 * moment in this step that's actually good news, so it gets a little more life than a plain
 * static icon would. Re-plays every time this enters composition, i.e. every fresh transition
 * into [UsernameCheckState.Available] (see call site: it's only ever composed while that's the
 * current state), not just once ever. */
@Composable
private fun AvailableTickIcon(modifier: Modifier = Modifier) {
    val colors = AuthPalette
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "availableTickScale",
    )
    Icon(
        Icons.Filled.CheckCircle,
        contentDescription = null,
        tint = colors.glow,
        modifier = modifier.size(15.dp).graphicsLayer { scaleX = scale; scaleY = scale },
    )
}

/** Placeholder only — real design deferred, see FEATURE_IDEAS.md. Just needs to exist as the
 * flow's final step and hand off to [onAuthenticated] once satisfied. */
@Composable
private fun RegisterSharingStep(viewModel: LoginViewModel, onAuthenticated: () -> Unit) {
    val colors = AuthPalette
    AuthStepScaffold(onBack = viewModel::goBack) {
        Text(text = "Share Ember with people", fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        Text(
            text = "You'll be able to invite friends and family from Settings once you're in.",
            fontFamily = PublicSansFontFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = colors.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
        AuthPrimaryButton(
            text = "Get started",
            onClick = onAuthenticated,
            modifier = Modifier.padding(top = 22.dp),
        )
    }
}
