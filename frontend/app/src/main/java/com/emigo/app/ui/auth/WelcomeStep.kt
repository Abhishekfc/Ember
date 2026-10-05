package com.emigo.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.emigo.app.R
import com.emigo.app.ui.theme.CourgetteFontFamily

/** [AuthStep.WELCOME] — the app's true entry point, its own dedicated screen (not sharing a
 * composable with [LoginStep]/[RegisterEmailStep]/etc.): the mockup, wordmark, and tagline, then
 * exactly two ways forward — "Create an account" (primary) into the register flow, "Sign in"
 * (a plain link, not a button) into the ordinary [LoginStep].
 *
 * Takes plain values/callbacks rather than [LoginViewModel] directly, rather than the ViewModel
 * itself — decouples it from needing a real `AuthRepository`/`TokenStore` just to construct,
 * which is what would make this screen previewable in Android Studio later if that's ever
 * useful again. */
@Composable
fun WelcomeStep(
    errorMessage: String?,
    onCreateAccountClick: () -> Unit,
    onSignInClick: () -> Unit,
) {
    val colors = AuthPalette
    var screenSize by remember { mutableStateOf(Size.Zero) }

    // One hero group — mockup, wordmark, tagline, and the button/sign-in row all flow together
    // and get centered as a single unit, rather than the button living in its own block pinned to
    // the bottom edge. That older split (kept from when the tagline was two lines and needed the
    // room) was what left a large dead gap once the tagline shrank to one line: the button never
    // moved, so all the slack from the shorter tagline just piled up between the two blocks
    // instead of closing. Still wrapped in verticalScroll for the same reason as before — a short
    // device or a large system font size can still make this taller than the viewport, and this
    // has to degrade to "scrolls" rather than "clips" if that happens.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp)
            // A fixed offset, not Arrangement.Center — centering a Column that's also
            // verticalScroll-able needs the scroll container's true content height, which isn't
            // known on the very first frame. That one-frame gap was what showed up as the phone
            // mockup's own inner content (the icon grid, the widget photo's offset — both
            // computed from AuthPhoneFrame's own BoxWithConstraints measurement) rendering at one
            // position, then visibly snapping to its real one right after. A fixed top padding
            // needs no such second pass, so there's nothing left to snap.
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Not wrapped in StaggeredEntrance like everything else below — PhoneHomeMockup animates
        // its own border in internally (see there), and wrapping it in another fade+slide on top
        // of that compounded the two into a choppier, different-looking animation than either one
        // alone. This is the one element on this screen with its own self-contained entrance.
        // Wider than before (was 0.56f) — at that size the mockup read as a small object
        // floating in a mostly-empty frame, with the side margins doing more work than the
        // content. This uses more of the screen's actual width instead of leaving it empty.
        PhoneHomeMockup(modifier = Modifier.fillMaxWidth(0.72f))
        StaggeredEntrance(delayMillis = 80) {
            Text(
                text = stringResource(R.string.app_name),
                fontFamily = CourgetteFontFamily,
                fontSize = 40.sp,
                color = colors.cream,
                modifier = Modifier.padding(top = 36.dp),
            )
        }
        StaggeredEntrance(delayMillis = 150) {
            Text(
                text = stringResource(R.string.welcome_tagline),
                fontFamily = AuthPalette.body,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
                color = colors.muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        StaggeredEntrance(delayMillis = 220) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 24.dp),
            ) {
                // Matches the mockup's own width fraction above (0.72f) rather than its own
                // separate, narrower one — keeps the same visual column running down the screen
                // instead of the button suddenly reading narrower than what's above it.
                AuthPrimaryButton(
                    text = stringResource(R.string.welcome_create_account),
                    onClick = onCreateAccountClick,
                    modifier = Modifier.fillMaxWidth(0.72f),
                )
                if (errorMessage != null) {
                    AuthInlineMessage(text = errorMessage, modifier = Modifier.padding(top = 10.dp))
                }
                Text(
                    text = stringResource(R.string.welcome_sign_in),
                    fontFamily = AuthPalette.body,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 22.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onSignInClick,
                        ),
                )
            }
        }
    }
}
