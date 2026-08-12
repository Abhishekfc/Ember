// Imported explicitly: inside a Gradle Kotlin DSL script, a bare `java.` prefix resolves to
// Gradle's own `java` extension rather than the JDK package, so `java.util.Properties` does not
// compile here even though it would in ordinary Kotlin.
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
    // Reads app/google-services.json (matches the same Firebase project — ember-app06 — the
    // backend's service-account credential already belongs to) to configure this app for FCM.
    id("com.google.gms.google-services")
}

// Signing credentials are read from a properties file kept OUTSIDE this repository
// (~/ember-secrets/), never committed and never passed on a command line where they'd land in
// shell history or CI logs. Absent — on a machine that only ever builds debug, or a fresh clone —
// the release signingConfig is simply not registered, so a debug build still works and a release
// build fails loudly rather than silently producing an unsigned or wrongly-signed artifact.
val keystorePropsFile = file("${System.getProperty("user.home")}/ember-secrets/emigo-keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.emigo.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.emigo.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        // Registered only when the credentials file is actually present, so a clone without it can
        // still build debug. A release build without it fails at signing rather than quietly
        // emitting an unsigned artifact that Play would reject much later.
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildTypes {
        // Previously there was no buildTypes block at all, meaning every variant — including
        // "release" — used AGP's implicit defaults (isMinifyEnabled = false, no ProGuard/R8) and
        // shared the exact same hardcoded `http://localhost:8080/` BASE_URL as debug. A release
        // APK built as-is could never reach a real backend, which invites exactly the wrong fix
        // under deadline pressure (hardcoding a real prod URL as plain HTTP, or widening the
        // debug-only cleartext network security config to cover release too).
        debug {
            // Defaults to localhost for the normal USB/adb-reverse dev workflow; override via
            // `-PEMBER_DEBUG_BASE_URL=https://...` (e.g. a Cloudflare Tunnel URL) to build a debug
            // APK that reaches the backend over the real internet instead — for testing on a
            // phone with no USB/adb connection to this machine at all.
            val debugBaseUrl = (project.findProperty("EMBER_DEBUG_BASE_URL") as? String)
                ?: "http://localhost:8080/"
            buildConfigField("String", "BASE_URL", "\"$debugBaseUrl\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // No real production backend exists yet for this project — this reads from a Gradle
            // property (`-PEMBER_RELEASE_BASE_URL=https://...` or `gradle.properties`) so the
            // real URL is never hardcoded in source, and falls back to an obviously-fake
            // placeholder rather than silently reusing localhost or failing the build outright.
            // Must be set to the real deployed HTTPS backend URL before shipping a release build.
            val releaseBaseUrl = (project.findProperty("EMBER_RELEASE_BASE_URL") as? String)
                ?: "https://REPLACE-WITH-REAL-PRODUCTION-BACKEND-URL.invalid/"
            buildConfigField("String", "BASE_URL", "\"$releaseBaseUrl\"")
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation(platform("com.squareup.okhttp3:okhttp-bom:5.4.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    implementation("androidx.datastore:datastore-preferences:1.2.1")
    implementation("androidx.security:security-crypto:1.1.0")

    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Event-driven sync (see EmberFirebaseMessagingService) — replaces the widget's old
    // 30-minute poll as the primary way new photos reach a closed app.
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    // The -ktx artifact was merged into the base module and is no longer published separately
    // as of recent Firebase BoM releases.
    implementation("com.google.firebase:firebase-messaging")
    // Bridges FirebaseMessaging's Task-based token API to a suspend call (.await()).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.1")

    implementation("dev.chrisbanes.haze:haze:1.7.2")

    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
