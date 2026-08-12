package com.emigo.app.ui.settings

/** One selectable launcher icon. [componentSuffix] is the manifest `<activity-alias>` name (see
 * AndroidManifest.xml) that has to be the *enabled* one for this icon to actually show — every
 * option, including [DEFAULT], is an alias; `MainActivity` itself carries no launcher identity of
 * its own any more (see the manifest's own comment on why). See [AppIconSwitcher] for the actual
 * `PackageManager` toggle this drives. */
enum class AppIconKey(val displayName: String, val locked: Boolean, val componentSuffix: String) {
    DEFAULT("Default", locked = false, componentSuffix = ".MainActivityDefaultIcon"),
    GOLD("Emigo Gold", locked = true, componentSuffix = ".MainActivityGoldIcon"),
}
