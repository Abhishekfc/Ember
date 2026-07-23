plugins {
    id("com.android.application") version "9.3.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.10" apply false
    // Backend already has a real Firebase project (ember-app06) wired up for push — this is
    // just the client-side half finally landing. See app/build.gradle.kts for where it's applied.
    id("com.google.gms.google-services") version "4.4.2" apply false
}
