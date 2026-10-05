package com.emigo.app.ui.auth

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import com.emigo.app.R

/** One app the invite can be sent through. [packageName] is what makes the row open that app
 * directly instead of the system chooser, and also what its real launcher icon is read from;
 * null means "let the user pick" and falls back to [fallbackIcon]. */
internal data class InviteTarget(
    val label: String,
    val fallbackIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val packageName: String?,
    // Bundled brand artwork (res/drawable/ic_invite_*), preferred over rememberAppIcon.
    // Null for Messages and More, which use fallbackIcon.
    val drawableResId: Int? = null,
)

/**
 * An app's launcher icon from PackageManager, for targets without a bundled
 * [InviteTarget.drawableResId]. Not reliable as the only source: some installed apps return
 * null depending on OEM and Android version (Snapchat did on a real device), and apps that
 * aren't installed always do. That's why the brand rows bundle their own artwork.
 */
@Composable
internal fun rememberAppIcon(packageName: String?): ImageBitmap? {
    val context = LocalContext.current
    return remember(packageName) {
        if (packageName == null) {
            null
        } else {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap() }
                .onFailure { android.util.Log.w("EmberIconDebug", "Failed to fetch real icon for $packageName", it) }
                .getOrNull()
        }
    }
}

/** Shares [message] to [packageName], or to the system chooser if that app isn't installed
 * (targeting a missing package throws). */
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
    context.startActivity(Intent.createChooser(base, context.getString(R.string.invite_chooser_title)))
}

/**
 * Opens an Instagram screen (DM inbox, story camera) by deep link; the caller copies the invite
 * to the clipboard first. Instagram has no text share intent, so copy-then-open is the closest
 * thing to one tap. Falls back to the share sheet if Instagram isn't installed.
 */
internal fun openInstagram(context: android.content.Context, deepLink: String, fallbackMessage: String) {
    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(deepLink)).setPackage("com.instagram.android")
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        shareInvite(context, fallbackMessage, null)
    }
}
