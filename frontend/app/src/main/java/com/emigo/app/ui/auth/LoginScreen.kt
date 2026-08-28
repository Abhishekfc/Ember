package com.emigo.app.ui.auth

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.emigo.app.R
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Link
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.emigo.app.ui.profile.UsernameCheckState
import com.emigo.app.ui.theme.PublicSansFontFamily

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
    val context = LocalContext.current

    // The classic Google Sign-In client — see build.gradle.kts's own comment on why this, not the
    // newer Credential Manager API. Built once and reused: constructing it is cheap, but the
    // ActivityResultLauncher it's paired with must be registered exactly once per composition,
    // which `rememberLauncherForActivityResult` already handles; the client just needs to survive
    // alongside it rather than being rebuilt on every recomposition.
    val googleSignInClient = remember {
        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            // The Web-type OAuth client id google-services.json generates alongside the Android
            // one — Firebase needs this specific one to exchange the resulting Google ID token
            // for a Firebase credential (see AuthRepository.signInWithGoogle). Auto-generated by
            // the google-services Gradle plugin as R.string.default_web_client_id; never written
            // by hand anywhere in this codebase.
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, options)
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val idToken = try {
            GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java).idToken
        } catch (ex: ApiException) {
            // Covers both a genuine failure and simply backing out of the account picker —
            // LoginViewModel.onGoogleSignInResult already treats a null token as a silent no-op
            // rather than an error, which is the right read for "the user changed their mind".
            null
        }
        viewModel.onGoogleSignInResult(idToken, onAuthenticated)
    }
    // Signing out of Firebase (see MainActivity.onSignOut) never touches this client's own
    // remembered account — GoogleSignInClient.getSignInIntent() silently re-picks whatever was
    // used last instead of showing the chooser again, which reads as "the button does nothing" for
    // anyone who signed out and wants to switch accounts (or just confirm which one they're using).
    // Signing this client out first, every time, forces the chooser to appear on every tap.
    val onGoogleSignInClick = {
        googleSignInClient.signOut().addOnCompleteListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }
        Unit
    }

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
                AuthStep.LOGIN -> LoginStep(viewModel, onAuthenticated, onGoogleSignInClick)
                AuthStep.FORGOT_PASSWORD -> ForgotPasswordStep(viewModel)
                AuthStep.REGISTER_EMAIL -> RegisterEmailStep(viewModel)
                AuthStep.REGISTER_PASSWORD -> RegisterPasswordStep(viewModel)
                AuthStep.REGISTER_NAME -> RegisterNameStep(viewModel)
                AuthStep.REGISTER_USERNAME -> RegisterUsernameStep(viewModel)
                AuthStep.REGISTER_WIDGET -> {
                    val widgetContext = LocalContext.current
                    WidgetSetupStep(
                        // Asks the launcher to pin the widget directly where that's supported
                        // (Android 8+ and a launcher that opted in, which most have). Where it
                        // isn't, there's nothing to fall back to that would actually work — the
                        // system offers no way to open the widget picker on an app's behalf — so
                        // this moves on and leaves the walkthrough as the route, rather than
                        // firing an intent that would dead-end or crash.
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
private fun LoginStep(viewModel: LoginViewModel, onAuthenticated: () -> Unit, onGoogleSignInClick: () -> Unit) {
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
            // Email only now, not "Email or username" — Firebase Authentication (which now owns
            // sign-in, see LoginViewModel's own doc comment) has no concept of a username at all,
            // so one typed here can no longer be resolved to an account the way it used to be.
            placeholder = "Email",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            modifier = Modifier.padding(top = 24.dp).focusRequester(emailFocus),
        )
        AuthPasswordField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            placeholder = "Password",
            imeAction = ImeAction.Done,
            onImeAction = { viewModel.submitLogin(onAuthenticated) },
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = "Forgot password?",
            fontFamily = PublicSansFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.muted,
            modifier = Modifier
                .padding(top = 10.dp)
                .clickable(enabled = !viewModel.isLoading, onClick = viewModel::onForgotPasswordClicked),
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
            onClick = onGoogleSignInClick,
            leadingContent = { GoogleGlyph() },
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Its own screen, not a dialog or an inline reveal on [LoginStep] — see
 * [LoginViewModel.forgotPasswordEmail]'s own doc comment for why this owns entirely separate
 * state (its own email field, its own loading/result flags) rather than sharing anything with the
 * login screen it's reached from. */
@Composable
private fun ForgotPasswordStep(viewModel: LoginViewModel) {
    val colors = AuthPalette
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboard?.show()
    }

    AuthStepScaffold(onBack = viewModel::goBack) {
        Text(text = "Reset your password", fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        Text(
            text = "Enter your email and we'll send you a link to set a new password.",
            fontFamily = PublicSansFontFamily,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = colors.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
        AuthTextField(
            value = viewModel.forgotPasswordEmail,
            onValueChange = viewModel::onForgotPasswordEmailChange,
            placeholder = "Email",
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::sendPasswordReset,
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )
        if (viewModel.passwordResetSent) {
            Text(
                text = "If that email has an account, we've sent a link to reset your password.",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.glow,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        AuthPrimaryButton(
            text = "Send reset link",
            onClick = viewModel::sendPasswordReset,
            enabled = viewModel.isForgotPasswordEmailValid,
            isLoading = viewModel.isSendingPasswordReset,
            modifier = Modifier.padding(top = 22.dp),
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
        // "That email already has an Emigo account" lands here, at the step that can actually fix
        // it, instead of at the end of the flow.
        viewModel.errorMessage?.let { message ->
            Text(
                text = message,
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.glow,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        AuthPrimaryButton(
            text = "Continue",
            onClick = viewModel::onEmailStepContinue,
            enabled = viewModel.isEmailValid,
            isLoading = viewModel.isLoading,
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
        AuthPasswordField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            placeholder = "Password",
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
            placeholder = "Last name (optional)",
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

/** One app the invite can be sent through. [packageName] is what makes the row open that app
 * directly instead of the system chooser, and also what its real launcher icon is read from;
 * null means "let the user pick" and falls back to [fallbackIcon]. */
internal data class InviteTarget(
    val label: String,
    val fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val packageName: String?,
)

/**
 * The app's own launcher icon, straight from the system, so each row shows the real brand mark in
 * its real colors. Read from PackageManager rather than bundled as drawables: no logo assets to
 * ship or keep current, no trademark artwork checked into the repo, and it always matches whatever
 * version of that app is actually installed. Returns null when the app isn't installed (or isn't
 * visible to this app — see the <queries> block in AndroidManifest), letting the caller fall back
 * to a plain glyph.
 */
@Composable
internal fun rememberAppIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName == null) {
            null
        } else {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap() }.getOrNull()
        }
    }
}

/** Fires a plain text share at [packageName] specifically, falling back to the system chooser
 * whenever that app isn't installed — targeting a package that isn't there throws, and an invite
 * button that does nothing is worse than one that offers the full share sheet instead. */
internal fun shareInvite(context: android.content.Context, message: String, packageName: String?) {
    val base = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
    }
    if (packageName != null) {
        val direct = Intent(base).setPackage(packageName)
        if (direct.resolveActivity(context.packageManager) != null) {
            context.startActivity(direct)
            return
        }
    }
    context.startActivity(Intent.createChooser(base, "Invite a friend"))
}

/**
 * Opens a specific surface inside Instagram (its DM inbox, its story camera) by deep link, having
 * already put the invite on the clipboard so it can be pasted straight in.
 *
 * Instagram deliberately accepts no plain-text share intent — its only supported intent takes an
 * image or video for a story, nothing text-based for a DM or a caption. So "send this sentence via
 * Instagram" can't be done in one tap by any app, Ember included; copy-then-open is what actually
 * gets someone there with the text in hand. Falls back to the normal share sheet if Instagram
 * isn't installed, so the row still does something useful either way.
 */
internal fun openInstagram(context: android.content.Context, deepLink: String, fallbackMessage: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink)).setPackage("com.instagram.android")
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        shareInvite(context, fallbackMessage, null)
    }
}


/** The last step of sign-up: getting someone's first friend in, which is the whole point of an
 * app built on seeing people's photos. This used to be a placeholder that just said invites would
 * be available from Settings later — the single worst moment to defer it, since a brand-new
 * account with no friends has nothing to look at.
 *
 * Two ways to send, because they suit different intents: a quick row of the apps most invites
 * actually go through (one tap, no reading), and a labelled list underneath for everything else,
 * where each row can say what it does. Real launcher icons throughout — see [rememberAppIcon].
 */
@Composable
private fun RegisterSharingStep(viewModel: LoginViewModel, onAuthenticated: () -> Unit) {
    val colors = AuthPalette
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val username = viewModel.usernameDraft.trim()
    val inviteMessage = remember(username) {
        if (username.isEmpty()) {
            "Come add me on Emigo — it puts my photos right on your home screen."
        } else {
            "Come add me on Emigo — I'm @$username. It puts my photos right on your home screen."
        }
    }

    val quickTargets = remember {
        listOf(
            InviteTarget("Instagram", Icons.Filled.PhotoCamera, "com.instagram.android"),
            InviteTarget("Snapchat", Icons.Filled.PhotoCamera, "com.snapchat.android"),
            InviteTarget("Messages", Icons.Filled.Sms, null),
            InviteTarget("More", Icons.Filled.MoreHoriz, null),
        )
    }

    // Three zones, not one long scrolling column: a fixed back button, a scrollable middle that
    // absorbs however tall the invite content ends up being on a given device, and a fixed
    // bottom action that's always on screen regardless. The old version put everything —
    // including the "skip" action — in one scrollable Column, so on any device short enough (or
    // with enough installed share targets) that the content ran past the viewport, the only way
    // to finish signing up was to discover you needed to scroll for it.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 24.dp, end = 24.dp)) {
            AuthBackButton(onClick = viewModel::goBack)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            // Same display face, size and weight every other step of this flow uses for its
            // heading, so arriving here doesn't look like a different app.
            Text(
                text = "Add your first friend",
                fontFamily = AuthPalette.display,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = colors.cream,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            Text(
                text = "Invite someone to get started",
                fontFamily = PublicSansFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SectionLabel(text = "INVITE FROM", modifier = Modifier.padding(top = 32.dp, bottom = 16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                quickTargets.forEach { target ->
                    QuickInviteButton(
                        target = target,
                        onClick = { shareInvite(context, inviteMessage, target.packageName) },
                    )
                }
            }

            SectionLabel(text = "SHARE YOUR EMIGO LINK", modifier = Modifier.padding(top = 34.dp, bottom = 6.dp))

            InviteListRow(
                title = "Copy link",
                subtitle = "Share your invite anywhere",
                packageName = null,
                fallbackIcon = Icons.Rounded.Link,
                onClick = { clipboard.setText(AnnotatedString(inviteMessage)) },
            )
            InviteListRow(
                title = "WhatsApp",
                subtitle = "Invite via WhatsApp",
                packageName = "com.whatsapp",
                fallbackIcon = Icons.Filled.Chat,
                onClick = { shareInvite(context, inviteMessage, "com.whatsapp") },
            )
            InviteListRow(
                title = "Instagram DM",
                subtitle = "Copies your invite, opens your inbox",
                packageName = "com.instagram.android",
                fallbackIcon = Icons.Filled.PhotoCamera,
                onClick = {
                    clipboard.setText(AnnotatedString(inviteMessage))
                    openInstagram(context, "instagram://direct-inbox", inviteMessage)
                },
            )
            InviteListRow(
                title = "Instagram Story",
                subtitle = "Copies your invite, opens the camera",
                packageName = "com.instagram.android",
                fallbackIcon = Icons.Filled.PhotoCamera,
                onClick = {
                    clipboard.setText(AnnotatedString(inviteMessage))
                    openInstagram(context, "instagram://story-camera", inviteMessage)
                },
            )
            InviteListRow(
                title = "Telegram",
                subtitle = "Invite via Telegram",
                packageName = "org.telegram.messenger",
                fallbackIcon = Icons.AutoMirrored.Filled.Send,
                onClick = { shareInvite(context, inviteMessage, "org.telegram.messenger") },
            )
            InviteListRow(
                title = "Messages",
                subtitle = "Invite via SMS",
                packageName = null,
                fallbackIcon = Icons.Filled.Sms,
                onClick = { shareInvite(context, inviteMessage, null) },
            )

            // Bottom padding here, not just on the fixed footer below, so the last invite row
            // never sits flush against the footer's own top edge when the list is long enough to
            // actually reach it.
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Fixed footer, outside the scroll — always visible no matter how tall the content above
        // is or how short the device's screen is. A real button now, not text styled to look
        // tappable, so it reads as an actual control rather than something easy to miss or mistake
        // for a caption.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            AuthSecondaryButton(
                text = "I'll do this later",
                onClick = onAuthenticated,
            )

            // Deliberately quieter and lighter than the button above it, and not tappable at all
            // — smaller, dimmer, un-bolded is what marks it as a closing line rather than a
            // second thing to press.
            Text(
                text = "Real moments. Real people.",
                fontFamily = PublicSansFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                color = colors.mutedDim.copy(alpha = 0.55f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 18.dp),
            )
        }
    }
}

/** The small uppercase group heading above each block of invite options. */
@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        color = AuthPalette.mutedDim,
        modifier = modifier,
    )
}

/** One app in the quick row — a large icon with its name beneath, sized to be tapped without
 * reading. */
@Composable
private fun QuickInviteButton(target: InviteTarget, onClick: () -> Unit) {
    val colors = AuthPalette
    val appIcon = rememberAppIcon(target.packageName)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        InviteIcon(appIcon = appIcon, fallbackIcon = target.fallbackIcon, size = 62.dp, glyphSize = 26.dp)
        Text(
            text = target.label,
            fontFamily = PublicSansFontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = colors.muted,
            maxLines = 1,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

/** One row of the labelled share list — icon, what it is, what it does, and a chevron. */
@Composable
private fun InviteListRow(
    title: String,
    subtitle: String,
    packageName: String?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val colors = AuthPalette
    val appIcon = rememberAppIcon(packageName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InviteIcon(appIcon = appIcon, fallbackIcon = fallbackIcon, size = 46.dp, glyphSize = 21.dp)
        Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                text = title,
                fontFamily = PublicSansFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colors.cream,
            )
            Text(
                text = subtitle,
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.mutedDim,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The real launcher icon when the app is installed, and a plain glyph on the theme's own panel
 * when it isn't — so a missing app degrades to something deliberate rather than a blank space. */
@Composable
private fun InviteIcon(
    appIcon: ImageBitmap?,
    fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    glyphSize: androidx.compose.ui.unit.Dp,
) {
    val colors = AuthPalette
    if (appIcon != null) {
        // Already the brand's own artwork and colors — no tint, and nothing drawn behind it.
        Image(bitmap = appIcon, contentDescription = null, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(
            modifier = Modifier.size(size).clip(CircleShape).background(colors.panel),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = fallbackIcon, contentDescription = null, tint = colors.glow, modifier = Modifier.size(glyphSize))
        }
    }
}

/** Asks the launcher to pin Emigo's own widget, which is the one route an app has to add a
 * widget without the user hunting through the picker themselves. Supported on Android 8+ and only
 * by launchers that opted in — [AppWidgetManager.isRequestPinAppWidgetSupported] is the honest
 * check, and where it's false there is genuinely nothing an app can do (the system exposes no way
 * to open the widget picker on an app's behalf), so this returns quietly and the walkthrough on
 * that same screen is the real path. Wrapped in runCatching because OEM launchers have been known
 * to throw from this despite reporting support, and a crash here would take out onboarding
 * itself over a step the user can complete by hand anyway. */
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
