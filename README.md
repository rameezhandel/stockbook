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
   declares no permissions at all, and CI fails the build if one appears — a
   check the camera survived, because photographing a bill goes through the
   phone's own camera app rather than asking for `CAMERA`.
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

- **The backup file.** A store-only ZIP — `stockbook.json` plus
  `photos/<id>.jpg` — written and read by hand on both sides, because iOS has no
  zip reader and this project has no dependencies. `java.util.zip` is
  deliberately unused on Android so that one implementation is ported rather
  than two written; a shared base64 fixture is asserted in both test suites as
  what each platform writes *and* reads.

  Inside it, `BackupDocument` is byte-compatible across both: same keys, same
  ISO-8601 timestamps, same absent-means-paid-in-full rule, same format version
  — **3**. A shop exported from an iPhone opens on Android and back again, and
  that is tested against literal files written by each. A bare `.json` from
  before the pictures travelled still imports, recognised by its first four
  bytes rather than its name.

  The version bumps only when an older reader would *misinterpret* the new
  shape, not merely lose a label. Credit notes bumped it, because a reader that
  dropped them would show every credited customer owing more than they do.
  Invoice numbers, receipt numbers, the shop address and photograph references
  did not: a reader that ignores those loses a label, not a figure.
- **Both string tables**, key for key, in English and Kannada. A string added to
  one app and not the other fails a test rather than shipping.
- **Every domain rule.** Each core store method has a twin on the other side,
  and the arithmetic they disagree on is a bug in one of them — that is how the
  paid-in-full divergence got caught.
- **What a printed document says.** `StatementDocument` holds every label and
  every already-formatted figure, so the two PDF renderers — Core Graphics on
  one side, a `Canvas` on the other — only ever decide how to draw boxes, never
  what a row is called or what goes in it.

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
cd android && ./gradlew :core:test     # 297 tests, ~15s, no SDK or emulator needed
cd android && ./gradlew :app:assembleDebug
```

The domain lives in `:core`, plain Kotlin on the JVM with no Android dependency,
so the rules of the shop are checkable in seconds on any machine with a JDK. The
part where a wrong answer costs somebody their stock count is the part that does
not need a device to test.

CI runs on different schedules for the two, and the reason is money.
[`android.yml`](.github/workflows/android.yml) builds and tests on **every
push**: Linux runners bill at 1x and finish in about ninety seconds.
[`ios.yml`](.github/workflows/ios.yml) runs **once per pull request** and not on
push at all, because macOS bills at 10x on a private repository — a seven-minute
build spends seventy minutes of the monthly allowance, and triggering on both
would spend it twice for one change.

**A change that never opens a pull request is never built on iOS.** Open the
pull request, wait for it, then merge — or run the workflow by hand from the
Actions tab. [`testflight.yml`](.github/workflows/testflight.yml) handles
releases.

## Status

Both apps are feature-complete and at parity with each other. Four tabs —
**Home**, **Items**, **Sales** and **Reports** — plus first-run setup, Settings,
and backup export and import.

Each half of Reports opens on the **people** it is about — customers on one
side, suppliers on the other — and a row opens that person's own screen: what
they have bought, what is outstanding, their statement, their payments, and
every document between them and the shop. They used to be a dropdown filter
above a list of bills, which made a person something you narrowed a list by
rather than something you could go and look at.

Built on top of the original spec since:

- **Customer statements**, on screen and as a **PDF** either phone can share.
  The wording and every figure come from one shared `StatementDocument`, so two
  entirely different graphics stacks print the same document.
- **Credit notes** — a figure, or the goods that came back, with their own
  numbering, shown on the statement and taken off what is owed.
- **Photographs of the paper bill**, taken while writing it or added to a saved
  one — and they travel in the backup, so a new phone gets the pictures as well
  as the book.
- **Typed numbers throughout** — invoice, credit note and receipt numbers are
  all written by the owner, never auto-generated, each checked against its own
  series.
- **The shop's printed address**, and monthly sales on Home over a month or year
  you pick.
- **Import from a backup file during first-run setup**, so a new phone starts as
  the old one left off.

What is left before release is in [`BACKLOG.md`](BACKLOG.md).
