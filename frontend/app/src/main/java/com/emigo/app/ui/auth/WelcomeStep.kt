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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.emigo.app.ui.theme.CourgetteFontFamily

/** [AuthStep.WELCOME] — the app's true entry point, its own dedicated screen (not sharing a
 * composable with [LoginStep]/[RegisterEmailStep]/etc.): the mockup, wordmark, and tagline, then
 * exactly two ways forward — "Create an account" (primary) into the register flow, "Sign in"
 * (a plain link, not a button) into the ordinary [LoginStep]. No Google button here — that
 * lives on [LoginStep] itself instead, next to the returning-user fields it actually pairs with.
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

    // Same fixed-footer shape RegisterSharingStep uses: a flexible, independently scrollable
    // middle (the mockup + wordmark + tagline, whose combined height varies with font scale and
    // device size) and the actual buttons pinned below it, outside that scroll — so "Create an
    // account"/"Sign in" are always on screen instead of depending on there being enough room
    // above the fold for the mockup and both lines of tagline to fit first.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp)
                .padding(top = 88.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            // Not wrapped in StaggeredEntrance like everything else below — PhoneHomeMockup
            // animates its own border in internally (see there), and wrapping it in another
            // fade+slide on top of that compounded the two into a choppier, different-looking
            // animation than either one alone. This is the one element on this screen with its
            // own self-contained entrance.
            PhoneHomeMockup(modifier = Modifier.fillMaxWidth(0.56f))
            StaggeredEntrance(delayMillis = 80) {
                Text(
                    text = "Emigo",
                    fontFamily = CourgetteFontFamily,
                    fontSize = 40.sp,
                    color = colors.cream,
                    modifier = Modifier.padding(top = 36.dp),
                )
            }
            StaggeredEntrance(delayMillis = 150) {
                Text(
                    text = "A private place for the moments only your\nclosest people get to see.",
                    fontFamily = AuthPalette.body,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                    color = colors.muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        StaggeredEntrance(delayMillis = 220) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(top = 24.dp, bottom = 48.dp),
            ) {
                // Narrower than a full-width button — at the mockup's own width, a full-bleed
                // button looked disproportionately long next to everything above it.
                AuthPrimaryButton(
                    text = "Create an account",
                    onClick = onCreateAccountClick,
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
                if (errorMessage != null) {
                    AuthInlineMessage(text = errorMessage, modifier = Modifier.padding(top = 10.dp))
                }
                Text(
                    text = "Sign in",
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
