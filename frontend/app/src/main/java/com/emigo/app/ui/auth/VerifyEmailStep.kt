package com.emigo.app.ui.auth

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emigo.app.R
import com.emigo.app.data.repository.EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS
import com.emigo.app.ui.theme.PublicSansFontFamily
import kotlinx.coroutines.delay

// Firebase's cooldown between verification emails to the same address (see the resend countdown).
private const val RESEND_COOLDOWN_MILLIS = 3 * 60 * 1000L

/** Blocks entry until the account's email is verified (see [AuthStep.NEEDS_EMAIL_VERIFICATION]
 * in LoginViewModel). No back arrow: signing out is the only exit, since going back can't undo
 * the Firebase account that already exists. */
@Composable
internal fun VerifyEmailStep(viewModel: LoginViewModel, onAuthenticated: () -> Unit, onSignOut: () -> Unit) {
    val colors = AuthPalette

    // Counts down to the server-side verification deadline (EmailVerificationExpiryService).
    // Recomputed from the real deadline each second instead of decremented, so it can't drift
    // across device sleep. At zero the screen shows its expired state rather than navigating away.
    // Keyed on the deadline: without that, a stale expired value from a previous account would
    // stop the loop from starting, and a new valid countdown would show "Verification failed",
    // frozen.
    var remainingMillis by remember(viewModel.pendingVerificationDeadlineMillis) {
        mutableStateOf(viewModel.pendingVerificationDeadlineMillis - System.currentTimeMillis())
    }
    LaunchedEffect(viewModel.pendingVerificationDeadlineMillis) {
        while (remainingMillis > 0L) {
            delay(1000)
            remainingMillis = viewModel.pendingVerificationDeadlineMillis - System.currentTimeMillis()
        }
    }
    val hasExpired = remainingMillis <= 0L

    // Sign-up already sends one verification email (see AuthRepository.signUp), so an early
    // "Resend" is a second request and Firebase rate-limits it ("too many attempts"). The button
    // stays disabled until the cooldown ends. Derived from the account's createdAt (deadline
    // minus grace period), not a separate timer.
    val accountCreatedAtMillis = viewModel.pendingVerificationDeadlineMillis - EMAIL_VERIFICATION_GRACE_PERIOD_MILLIS
    var resendCooldownRemainingMillis by remember(viewModel.pendingVerificationDeadlineMillis) {
        mutableStateOf(accountCreatedAtMillis + RESEND_COOLDOWN_MILLIS - System.currentTimeMillis())
    }
    LaunchedEffect(viewModel.pendingVerificationDeadlineMillis) {
        while (resendCooldownRemainingMillis > 0L) {
            delay(1000)
            resendCooldownRemainingMillis = accountCreatedAtMillis + RESEND_COOLDOWN_MILLIS - System.currentTimeMillis()
        }
    }
    val canResend = resendCooldownRemainingMillis <= 0L

    AuthStepScaffold(onBack = null, bottomWeight = 0f) {
        Icon(
            Icons.Filled.MarkEmailRead,
            contentDescription = null,
            tint = if (hasExpired) colors.mutedDim else colors.cream,
            modifier = Modifier.size(30.dp),
        )
        Text(
            // Says what to do next; the expired state says what went wrong instead.
            text = stringResource(if (hasExpired) R.string.auth_verify_failed_title else R.string.auth_verify_check_inbox),
            fontFamily = AuthPalette.display,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = colors.cream,
            modifier = Modifier.padding(top = 20.dp),
        )
        Text(
            text = stringResource(if (hasExpired) R.string.auth_verify_expired_detail else R.string.auth_verify_sent_detail),
            fontFamily = PublicSansFontFamily,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = colors.muted,
            modifier = Modifier.padding(top = 12.dp),
        )

        // The address gets its own box so a typo is easy to spot; that's what stops people
        // getting stuck on this screen.
        Box(
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.cream.copy(alpha = 0.05f))
                .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 15.dp),
        ) {
            Text(
                text = viewModel.pendingVerificationEmail,
                fontFamily = PublicSansFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasExpired) colors.mutedDim else colors.cream,
            )
        }
        // Plain text under the address; the countdown shouldn't be loud.
        Crossfade(targetState = hasExpired, animationSpec = tween(320), label = "verifyCountdown") { expired ->
            val remainingSeconds = (remainingMillis / 1000).coerceAtLeast(0)
            Text(
                text = if (expired) {
                    stringResource(R.string.auth_verify_link_expired)
                } else {
                    stringResource(R.string.auth_verify_expires_in, remainingSeconds / 60, remainingSeconds % 60)
                },
                fontFamily = PublicSansFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.mutedDim,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        if (!hasExpired && viewModel.verificationCheckError != null) {
            AuthInlineMessage(text = viewModel.verificationCheckError.orEmpty(), modifier = Modifier.padding(top = 16.dp))
        }
        if (!hasExpired && viewModel.verificationResendMessage != null) {
            AuthInlineMessage(text = viewModel.verificationResendMessage.orEmpty(), modifier = Modifier.padding(top = 16.dp))
        }
        Spacer(modifier = Modifier.weight(1f))
        AuthPrimaryButton(
            text = stringResource(R.string.auth_verify_done_button),
            onClick = { viewModel.onEmailVerifiedContinue(onAuthenticated) },
            enabled = !hasExpired,
            isLoading = viewModel.isCheckingVerification,
        )
        // One slot, two jobs: "Resend" while the window is open, "Try again later" (back to the
        // start) once it has expired.
        Crossfade(targetState = hasExpired, animationSpec = tween(320), label = "verifySecondaryAction") { expired ->
            if (expired) {
                AuthSecondaryButton(
                    text = stringResource(R.string.auth_verify_try_again_later),
                    onClick = { viewModel.resetAfterSignOut(onSignOut) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            } else {
                val resendCooldownSeconds = (resendCooldownRemainingMillis / 1000).coerceAtLeast(0)
                AuthSecondaryButton(
                    text = if (canResend) {
                        stringResource(R.string.auth_verify_resend)
                    } else {
                        stringResource(R.string.auth_verify_resend_in, resendCooldownSeconds / 60, resendCooldownSeconds % 60)
                    },
                    onClick = viewModel::resendVerificationEmail,
                    enabled = canResend,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}
