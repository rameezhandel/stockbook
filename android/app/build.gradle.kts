plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

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
        versionCode = 1
        versionName = "1.0"
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
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            // So a debug build can sit beside a future release one rather than
            // fighting it for the same package name.
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
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
