// Deliberately empty of plugin declarations.
//
// Declaring the Android plugin here — even with `apply false` — makes Gradle
// resolve it before it can configure anything, including the module that has no
// Android in it. Each module names its own plugins instead, so `:core` and its
// tests build anywhere there is a JDK, with or without an Android SDK and with
// or without reachable Google servers.
