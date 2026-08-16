// Two modules, and the split is the point.
//
// `core` is plain Kotlin on the JVM: the whole domain, no Android anywhere in
// it. Its tests run in milliseconds on any machine with a JDK, which is what
// makes the rules of this shop checkable without an emulator or an SDK.
//
// `app` is Compose and nothing else — screens over a domain it does not own.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "stockbook"
include(":core")
include(":app")
