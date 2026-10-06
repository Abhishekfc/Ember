package com.emigo.app.ui.auth

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.R
import com.emigo.app.core.AppLinks
import com.emigo.app.core.openUrl
import com.emigo.app.ui.profile.UsernameCheckState
import com.emigo.app.ui.theme.PublicSansFontFamily

@Composable
internal fun LoginStep(viewModel: LoginViewModel, onAuthenticated: () -> Unit) {
    val colors = AuthPalette
    val emailFocus = remember { FocusRequester() }
    AutoFocusAndShowKeyboard(emailFocus)

    AuthStepScaffold(onBack = viewModel::goBack, bottomWeight = 0f) {
        Text(text = stringResource(R.string.auth_login_title), fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        AuthTextField(
            value = viewModel.loginIdentifier,
            onValueChange = viewModel::onLoginIdentifierChange,
            // Firebase only knows emails; AuthRepository.signIn resolves a username to its email
            // through a backend lookup first, so this field accepts either.
            placeholder = stringResource(R.string.auth_login_identifier_hint),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Next,
            modifier = Modifier.padding(top = 24.dp).focusRequester(emailFocus),
        )
        AuthPasswordField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            placeholder = stringResource(R.string.auth_password_hint),
            imeAction = ImeAction.Done,
            onImeAction = { viewModel.submitLogin(onAuthenticated) },
            modifier = Modifier.padding(top = 10.dp),
        )
        Text(
            text = stringResource(R.string.auth_forgot_password),
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
        Spacer(modifier = Modifier.weight(1f))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_login_button),
            onClick = { viewModel.submitLogin(onAuthenticated) },
            isLoading = viewModel.isLoading,
        )
    }
}

/** Its own screen with its own state (see [LoginViewModel.forgotPasswordEmail]), not shared with
 * [LoginStep]. */
@Composable
internal fun ForgotPasswordStep(viewModel: LoginViewModel) {
    val colors = AuthPalette
    val focusRequester = remember { FocusRequester() }
    AutoFocusAndShowKeyboard(focusRequester)

    AuthStepScaffold(onBack = viewModel::goBack, bottomWeight = 0f) {
        Text(text = stringResource(R.string.auth_reset_title), fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        Text(
            text = stringResource(R.string.auth_reset_description),
            fontFamily = PublicSansFontFamily,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = colors.muted,
            modifier = Modifier.padding(top = 10.dp),
        )
        AuthTextField(
            value = viewModel.forgotPasswordEmail,
            onValueChange = viewModel::onForgotPasswordEmailChange,
            placeholder = stringResource(R.string.auth_email_hint),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::sendPasswordReset,
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )
        if (viewModel.passwordResetSent) {
            Text(
                text = stringResource(R.string.auth_reset_sent),
                fontFamily = PublicSansFontFamily,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = colors.glow,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_reset_send_button),
            onClick = viewModel::sendPasswordReset,
            enabled = viewModel.isForgotPasswordEmailValid,
            isLoading = viewModel.isSendingPasswordReset,
        )
    }
}

@Composable
internal fun RegisterEmailStep(viewModel: LoginViewModel) {
    val colors = AuthPalette
    val focusRequester = remember { FocusRequester() }
    AutoFocusAndShowKeyboard(focusRequester)

    AuthStepScaffold(onBack = viewModel::goBack, bottomWeight = 0f) {
        Text(text = stringResource(R.string.auth_register_email_title), fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        AuthTextField(
            value = viewModel.email,
            onValueChange = viewModel::onEmailChange,
            placeholder = stringResource(R.string.auth_register_email_hint),
            keyboardType = KeyboardType.Email,
            imeAction = ImeAction.Done,
            onImeAction = viewModel::onEmailStepContinue,
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )
        // "Email already has an account" is shown here, where it can be fixed.
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
        // Pushes the legal notice and Continue button to the bottom, right above the keyboard.
        // It takes the slack that the scaffold's trailing spacer takes on other steps.
        Spacer(modifier = Modifier.weight(1f))
        LegalAgreementNotice(modifier = Modifier.fillMaxWidth())
        AuthPrimaryButton(
            text = stringResource(R.string.common_continue),
            onClick = viewModel::onEmailStepContinue,
            enabled = viewModel.isEmailValid,
            isLoading = viewModel.isLoading,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** Shown only on [RegisterEmailStep], the step that commits to creating the account. */
@Composable
private fun LegalAgreementNotice(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // Same muted color as the sentence, only bolder, so links don't compete with the field and button.
    val linkStyle = SpanStyle(color = AuthPalette.muted, fontWeight = FontWeight.SemiBold)
    val prefix = stringResource(R.string.auth_legal_prefix)
    val termsLabel = stringResource(R.string.auth_legal_terms)
    val andWord = stringResource(R.string.auth_legal_and)
    val privacyLabel = stringResource(R.string.auth_legal_privacy)
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = AuthPalette.muted)) {
            append("$prefix ")
            withLink(
                LinkAnnotation.Url(
                    url = AppLinks.TERMS_OF_SERVICE,
                    styles = TextLinkStyles(style = linkStyle),
                ) { openUrl(context, AppLinks.TERMS_OF_SERVICE) },
            ) { append(termsLabel) }
            append(" $andWord ")
            withLink(
                LinkAnnotation.Url(
                    url = AppLinks.PRIVACY_POLICY,
                    styles = TextLinkStyles(style = linkStyle),
                ) { openUrl(context, AppLinks.PRIVACY_POLICY) },
            ) { append(privacyLabel) }
        }
    }
    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

@Composable
internal fun RegisterPasswordStep(viewModel: LoginViewModel) {
    val colors = AuthPalette
    val focusRequester = remember { FocusRequester() }
    AutoFocusAndShowKeyboard(focusRequester)

    AuthStepScaffold(onBack = viewModel::goBack, bottomWeight = 0f) {
        Text(text = stringResource(R.string.auth_password_create_title), fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        AuthPasswordField(
            value = viewModel.password,
            onValueChange = viewModel::onPasswordChange,
            placeholder = stringResource(R.string.auth_password_hint),
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submitRegister,
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )
        Text(
            text = stringResource(R.string.auth_password_rule),
            fontFamily = PublicSansFontFamily,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (viewModel.isPasswordValid) colors.glow else colors.mutedDim,
            modifier = Modifier.padding(top = 10.dp, start = 4.dp),
        )
        if (viewModel.errorMessage != null) {
            AuthInlineMessage(text = viewModel.errorMessage.orEmpty(), modifier = Modifier.padding(top = 10.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_create_account_button),
            onClick = viewModel::submitRegister,
            enabled = viewModel.isPasswordValid,
            isLoading = viewModel.isLoading,
        )
    }
}

@Composable
internal fun RegisterNameStep(viewModel: LoginViewModel) {
    val colors = AuthPalette
    val focusRequester = remember { FocusRequester() }
    AutoFocusAndShowKeyboard(focusRequester)

    AuthStepScaffold(onBack = viewModel::goBack, bottomWeight = 0f) {
        Text(text = stringResource(R.string.auth_name_title), fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        AuthTextField(
            value = viewModel.firstName,
            onValueChange = viewModel::onFirstNameChange,
            placeholder = stringResource(R.string.auth_first_name_hint),
            imeAction = ImeAction.Next,
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )
        AuthTextField(
            value = viewModel.lastName,
            onValueChange = viewModel::onLastNameChange,
            placeholder = stringResource(R.string.auth_last_name_hint),
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submitName,
            modifier = Modifier.padding(top = 10.dp),
        )
        if (viewModel.errorMessage != null) {
            AuthInlineMessage(text = viewModel.errorMessage.orEmpty(), modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        AuthPrimaryButton(
            text = stringResource(R.string.common_continue),
            onClick = viewModel::submitName,
            enabled = viewModel.isNameValid,
            isLoading = viewModel.isLoading,
        )
    }
}

@Composable
internal fun RegisterUsernameStep(viewModel: LoginViewModel) {
    val colors = AuthPalette
    val focusRequester = remember { FocusRequester() }
    AutoFocusAndShowKeyboard(focusRequester)

    val check = viewModel.usernameCheck

    AuthStepScaffold(onBack = viewModel::goBack, bottomWeight = 0f) {
        Text(text = stringResource(R.string.auth_username_title), fontFamily = AuthPalette.display, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = colors.cream)
        // Availability status shows below the field, never inside it.
        AuthTextField(
            value = viewModel.usernameDraft,
            onValueChange = viewModel::onUsernameDraftChange,
            placeholder = stringResource(R.string.auth_username_hint),
            imeAction = ImeAction.Done,
            onImeAction = viewModel::submitUsername,
            modifier = Modifier.padding(top = 24.dp).focusRequester(focusRequester),
        )

        // No space is reserved while Idle; animateContentSize lets the button glide down when a
        // status line or suggestion chips appear.
        Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
            val statusText = viewModel.errorMessage ?: when (check) {
                UsernameCheckState.Checking -> stringResource(R.string.auth_username_checking)
                UsernameCheckState.Available -> stringResource(R.string.auth_username_available)
                is UsernameCheckState.Taken -> stringResource(R.string.auth_username_taken)
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

        Spacer(modifier = Modifier.weight(1f))
        AuthPrimaryButton(
            text = stringResource(R.string.common_continue),
            onClick = viewModel::submitUsername,
            enabled = check is UsernameCheckState.Available,
            isLoading = viewModel.isLoading,
        )
    }
}

/** A checkmark that bounces in each time the username becomes [UsernameCheckState.Available]. */
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
