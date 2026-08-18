# Stockbook

Offline inventory and billing for a small hardware shop. Single user, no
account, no server — everything lives on the phone, and the only way data moves
between devices is a file the owner exports by hand.

Two native apps, one product: **iOS** in SwiftUI and **Android** in Kotlin and
Jetpack Compose. They are not a shared codebase and not a port of one another —
they are two implementations held to the same spec, the same wording in both
languages, and the same file format.

## The three constraints everything else follows from

1. **No network calls, ever.** No analytics, no crash reporting, no sync. Both
   apps are fully functional in airplane mode on first launch. The Android APK
   declares no permissions at all, and CI fails the build if one appears.
2. **All persistence is local.** A dropped phone loses everything unless the
   owner exported a file, and both apps keep saying so until one exists.
3. **Single user.** No login, no roles, no tenancy.

## Where things are

| Path | What it is |
| --- | --- |
| [`ios/`](ios/) | The iOS app. Open `ios/Stockbook.xcodeproj` and Run — no dependencies. |
| [`ios/README.md`](ios/README.md) | iOS architecture: how the layers fit, and the TestFlight setup. |
| [`android/`](android/) | The Android app, split into a pure-JVM domain and a Compose UI. |
| [`android/README.md`](android/README.md) | Android architecture, and why the domain has no Android in it. |
| [`project/design_handoff_stockbook/README.md`](project/design_handoff_stockbook/README.md) | **The spec.** Every screen, colour, type size, validation rule and the export/import format. |
| [`project/`](project/) | The original HTML design prototype and the Nocturne design-system stylesheet. |
| [`chats/`](chats/) | The design conversation the app was specified in. |
| [`HANDOFF.md`](HANDOFF.md) | Notes from the design-tool export that produced `project/`. |
| [`BACKLOG.md`](BACKLOG.md) | What is deliberately left until just before going live. |

## What the two apps share

- **The backup file.** `BackupDocument` is byte-compatible across both: same
  keys, same ISO-8601 timestamps, same absent-means-paid-in-full rule, same
  format version. A shop exported from an iPhone opens on Android and back
  again, and that is tested against literal files written by each.
- **Both string tables**, key for key, in English and Kannada. A string added to
  one app and not the other fails a test rather than shipping.
- **Every domain rule.** Each core store method has a twin on the other side,
  and the arithmetic they disagree on is a bug in one of them — that is how the
  paid-in-full divergence got caught.

## Building

**iOS** — Xcode 16+:

```sh
open ios/Stockbook.xcodeproj
```

Tests: `⌘U`, or

```sh
xcodebuild test -scheme Stockbook \
  -destination 'platform=iOS Simulator,name=iPhone 16' \
  -project ios/Stockbook.xcodeproj
```

**Android** — JDK 17+, Android Studio only for the UI:

```sh
cd android && ./gradlew :core:test     # 186 tests, ~12s, no SDK or emulator needed
cd android && ./gradlew :app:assembleDebug
```

The domain lives in `:core`, plain Kotlin on the JVM with no Android dependency,
so the rules of the shop are checkable in seconds on any machine with a JDK. The
part where a wrong answer costs somebody their stock count is the part that does
not need a device to test.

CI builds and tests both apps on every push —
[`ios.yml`](.github/workflows/ios.yml),
[`android.yml`](.github/workflows/android.yml), and
[`testflight.yml`](.github/workflows/testflight.yml) for releases.

## Status

Both apps are feature-complete against the spec and at parity with each other:
Today, Items, Sell, Bills, the Book (customers and suppliers), Customers,
Settings, and first-run setup are built on both platforms, along with backup
export and import.

What is left before release is in [`BACKLOG.md`](BACKLOG.md) — TestFlight's
Apple-side setup, branch cleanup, and the launcher icon and bundled fonts. None
of it is app code.
