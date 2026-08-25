import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * The release signing key, from the environment or an untracked properties file.
 *
 * Never from the repository. The debug key below is committed on purpose and
 * protects nothing; this one is the permanent identity of the app on Google
 * Play, and a copy of it in git history cannot be taken back — history outlives
 * whatever the repository's visibility happens to be today.
 *
 * Absent is not an error. A build with no key produces an unsigned release,
 * which is what a machine that has no business signing anything should get.
 * Only the workflow that uploads insists on it.
 */
val releaseKeyProperties = Properties().apply {
    val file = rootProject.file("keystore/release.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun releaseSecret(environmentVariable: String, propertyName: String): String? =
    System.getenv(environmentVariable) ?: releaseKeyProperties.getProperty(propertyName)

val releaseStoreFile = releaseSecret("STOCKBOOK_KEYSTORE_FILE", "storeFile")
val releaseStorePassword = releaseSecret("STOCKBOOK_KEYSTORE_PASSWORD", "storePassword")
val releaseKeyAlias = releaseSecret("STOCKBOOK_KEY_ALIAS", "keyAlias")
val releaseKeyPassword = releaseSecret("STOCKBOOK_KEY_PASSWORD", "keyPassword")

val canSignRelease = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).none { it.isNullOrBlank() }

android {
    namespace = "com.stockbook.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.stockbook.app"
        // API 26 for java.time without desugaring, which the domain leans on for
        // every timestamp. Anything older is a rounding error of the install base
        // by now and would cost a compatibility layer on the one thing this app
        // must get exactly right.
        minSdk = 26
        targetSdk = 35
        // The number Play orders uploads by. It must increase with every upload
        // and can never be reused or lowered, so the release workflow passes it
        // in rather than relying on somebody remembering to edit this line.
        versionCode = (findProperty("stockbook.versionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("stockbook.versionName") as String?) ?: "1.0.0"
    }

    signingConfigs {
        // A committed, stable key — not a secret, and not pretending to be one.
        //
        // Without this, AGP generates a debug keystore on whatever machine is
        // building, and every CI runner is a fresh machine. Each build would be
        // signed by a different key, so installing a new APK over an older one
        // fails with a signature mismatch and the only way through is to
        // uninstall — which, in an app whose whole premise is that the shop
        // lives on this phone and nowhere else, means throwing the shop away to
        // take an update.
        //
        // A debug key protects nothing; Android's own default one is public.
        // What this one buys is that build 12 installs over build 11 and the
        // owner keeps their bills.
        getByName("debug") {
            storeFile = rootProject.file("keystore/stockbook-debug.jks")
            storePassword = "stockbook"
            keyAlias = "stockbook"
            keyPassword = "stockbook"
        }

        // Created only when there is something to create it from, so a checkout
        // without the key still builds.
        if (canSignRelease) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // So a debug build can sit beside a future release one rather than
            // fighting it for the same package name.
            versionNameSuffix = "-debug"
        }
        release {
            // Left off deliberately. R8 would need keep rules for
            // kotlinx.serialization, and getting those wrong does not crash — it
            // silently changes what the backup file contains, which is the one
            // failure this app cannot afford. Nothing about Play requires it.
            isMinifyEnabled = false
            signingConfig = if (canSignRelease) signingConfigs.getByName("release") else null
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
        // Off by default in AGP 8. Settings reads BuildConfig.DEBUG to keep
        // "Start over" out of a release build, so it has to be generated.
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    // Already on the runtime path underneath activity-compose; named here because
    // `WindowCompat` is used directly, to tell the status bar which theme it is
    // sitting over. It declares no permissions of its own, and the CI step below
    // `assembleDebug` now proves that of the built APK rather than trusting it.
    implementation(libs.androidx.core)
    implementation(libs.lifecycle.runtime.compose)

    debugImplementation(libs.compose.ui.tooling)
}
