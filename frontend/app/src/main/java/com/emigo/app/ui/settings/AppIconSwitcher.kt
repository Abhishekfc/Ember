package com.emigo.app.ui.settings

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** The only way Android lets an app change its own launcher icon at runtime: declare every icon
 * choice as its own `<activity-alias>` targeting the real Activity (see AndroidManifest.xml —
 * MainActivity itself carries no launcher identity of its own; every option, including Default,
 * is one of these aliases), then flip which *one* alias is enabled. Every other candidate has to
 * be explicitly disabled in the same call, not just left alone, since more than one enabled alias
 * pointing at the same Activity shows up as two separate icons for what's still one app. */
internal object AppIconSwitcher {

    /** `DONT_KILL_APP` is required — without it, the OS restarts the whole app process the
     * instant this call returns, which would tear down the very screen the user just tapped
     * "Apply" on. The launcher itself still typically needs a relaunch (or a home-screen return)
     * to visibly refresh the icon — that delay is the OS/launcher's own icon-cache behavior, not
     * something this call controls. */
    fun apply(context: Context, target: AppIconKey) {
        val packageManager = context.packageManager
        AppIconKey.entries.forEach { candidate ->
            val setting = if (candidate == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val componentName = ComponentName(context.packageName, "${context.packageName}${candidate.componentSuffix}")
            packageManager.setComponentEnabledSetting(componentName, setting, PackageManager.DONT_KILL_APP)
        }
    }
}
