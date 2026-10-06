package com.emigo.app.ui.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp

/** The whole sign-in / sign-up flow as a step machine (see [AuthStep]) rather than a NavHost.
 * EmberRoot only sees "still loading" or [onAuthenticated]. New accounts get one question per
 * screen; returning users get a single login screen (see [LoginViewModel] for why). */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onAuthenticated: () -> Unit,
    onSignOut: () -> Unit,
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
                AuthStep.FORGOT_PASSWORD -> ForgotPasswordStep(viewModel)
                AuthStep.REGISTER_EMAIL -> RegisterEmailStep(viewModel)
                AuthStep.REGISTER_PASSWORD -> RegisterPasswordStep(viewModel)
                AuthStep.REGISTER_NAME -> RegisterNameStep(viewModel)
                AuthStep.REGISTER_USERNAME -> RegisterUsernameStep(viewModel)
                AuthStep.NEEDS_EMAIL_VERIFICATION -> VerifyEmailStep(viewModel, onAuthenticated, onSignOut)
                AuthStep.REGISTER_WIDGET -> {
                    val widgetContext = LocalContext.current
                    WidgetSetupStep(
                        // Pins the widget directly where the launcher supports it (Android 8+).
                        // Elsewhere there's no way to open the widget picker for the user, so we
                        // just move on and let the walkthrough be the route.
                        onAddWidget = {
                            requestPinEmberWidget(widgetContext)
                            viewModel.onWidgetStepDone()
                        },
                    )
                }
                AuthStep.REGISTER_SHARING -> RegisterSharingStep(viewModel, onAuthenticated)
            }
        }
    }
}

@Composable
internal fun AuthStepScaffold(
    onBack: (() -> Unit)?,
    // 1.4f keeps content slightly above center. RegisterEmailStep passes 0f so its Continue
    // button sits flush with the bottom, right above the keyboard.
    bottomWeight: Float = 1.4f,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            // After navigationBarsPadding (the recommended order) so the IME inset isn't doubled
            // on gesture-nav devices. Keeps content clear of the keyboard instead of relying on
            // the Activity's window resize, which isn't reliable on small screens.
            .imePadding()
            .padding(horizontal = 28.dp, vertical = 32.dp),
    ) {
        if (onBack != null) {
            AuthBackButton(onClick = onBack)
        }
        Spacer(modifier = Modifier.weight(1f))
        content()
        // weight() throws on non-positive values, so skip the spacer when bottomWeight is 0f.
        if (bottomWeight > 0f) {
            Spacer(modifier = Modifier.weight(bottomWeight))
        }
    }
}

/** Focuses the first field, then shows the keyboard one frame later. Showing it in the same
 * instant as the focus request sometimes made the Continue button snap above the keyboard
 * instead of sliding up, because the IME inset animation wasn't attached yet. */
@Composable
internal fun AutoFocusAndShowKeyboard(focusRequester: FocusRequester) {
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        withFrameNanos {}
        keyboard?.show()
    }
}

/** Asks the launcher to pin Emigo's widget (Android 8+, launchers that opted in). Where that's
 * unsupported there's nothing else an app can do, so it returns quietly and the walkthrough is the
 * path. In runCatching because some OEM launchers throw despite reporting support, and a crash
 * here shouldn't take down onboarding. */
private fun requestPinEmberWidget(context: android.content.Context) {
    runCatching {
        val manager = android.appwidget.AppWidgetManager.getInstance(context) ?: return
        if (!manager.isRequestPinAppWidgetSupported) return
        manager.requestPinAppWidget(
            android.content.ComponentName(context, com.emigo.app.widget.EmberWidgetReceiver::class.java),
            null,
            null,
        )
    }
}
