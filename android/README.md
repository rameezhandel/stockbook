# Stockbook — native Android

Kotlin + Jetpack Compose, the same app as [`../ios`](../ios/README.md), against
the same design and the same file format.

**Requirements:** JDK 17+. Android Studio for the UI; the domain needs neither.

## Two modules, and the split is the point

```
android/
├── core/    plain Kotlin on the JVM — the whole domain, no Android in it
└── app/     Compose, and nothing else
```

`:core` holds the model, the rules, money, dates, both languages and the backup
format. It has **no Android dependency at all**, which means:

```sh
cd android && ./gradlew :core:test        # 108 tests, about 6 seconds
```

runs on any machine with a JDK — no SDK, no emulator, no Google servers. That is
not a convenience. The rules of this shop are the part where a wrong answer
costs somebody their stock count, and they are now checkable in seconds by
anyone, on anything.

`:app` is screens over a domain it does not own. It needs the Android SDK and
Google's Maven, so it builds in CI and in Android Studio.

## Shared with iOS, deliberately

- **The backup file.** `BackupDocument` is byte-compatible with the iOS build:
  same keys, same ISO-8601 timestamps, same absent-means-paid-in-full rule. A
  shop exported from an iPhone opens on Android and back again, which is tested
  in `CrossPlatformBackupTests` against a literal iPhone-written file.
  `productUID` is spelled the way Swift spells it for exactly that reason.
- **Every string**, in both languages, in one table ported line for line from
  the iOS `Strings`. A correction belongs in both.
- **The palette.** `Nocturne.kt` carries the same hex values, in both themes.
  Two apps drawn from one palette stay one product. Where the two builds differ
  is only in how a theme change reaches the screen: Compose reads the tokens
  through a snapshot state and recomposes what draws, while iOS rebuilds its tree
  on a key, because its palette is not observable.
- **The rules.** `StoreTests` is a port of the iOS suite, assertion for
  assertion. The two apps share a file and a shop; they had better share their
  arithmetic.

### Timestamps are truncated to whole seconds

`Instant.now()` carries nanoseconds and the file format carries seconds, because
that is what Foundation's `.iso8601` writes and all it will parse. Left alone, a
bill saved at `09:41:07.705` comes back from its own file as `09:41:07` — the
value in memory and the value on disk quietly disagreeing. `Timestamps.now()`
truncates at the moment of creation, so what you hold is what you saved. A round
trip test caught this; nothing on a screen would have.

## Nothing leaves the phone

The manifest asks for **no permissions at all** — not INTERNET, not storage.
`data_extraction_rules.xml` opts out of Google's own backup transport in both
directions, because "all persistence is local" would be a half-truth if the shop
were quietly synced to a Google account behind the owner's back. The only copy
that leaves this phone is the file the owner exports on purpose.

## Getting it onto a phone

Easier than iOS: no developer account, no App Store Connect, no TestFlight.

Every push that touches `android/` builds an APK and uploads it as the
**`stockbook-debug-apk`** artifact, kept for 14 days.

On the phone:

1. Open the repository's **Actions** tab and pick the newest green **Android**
   run. You need to be signed in to GitHub — artifacts are not public links.
2. Download **`stockbook-debug-apk`**. GitHub always wraps artifacts in a zip, so
   what lands is `stockbook-debug-apk.zip`.
3. Extract it — the Files app on most phones will.
4. Tap the `.apk` inside. Android asks once for permission to install from
   whichever app is doing the tapping; allow it.

### The build is signed with a committed key, on purpose

`keystore/stockbook-debug.jks` is in the repository and its password is in
`app/build.gradle.kts`. That is not an oversight.

Left alone, AGP generates a debug keystore on whatever machine is building, and
every CI runner is a fresh machine — so each build would carry a different
signature, installing a new APK over an older one would fail with a mismatch,
and the only way through would be to uninstall first. In an app whose whole
premise is that the shop lives on this phone and nowhere else, that means
throwing the shop away to take an update.

A debug key protects nothing — Android's own default one is public. What this
one buys is that build 12 installs over build 11 and the owner keeps their
bills. A real release key, for the Play Store, would be a secret and would not
live here.

## What is built

Everything the iOS app does.

- **The whole domain**, with 108 tests: products, bills, voiding, restock,
  customers and their case-insensitive identity, the stored roster and the
  payments that come off what is owed, statement periods and their arithmetic,
  money in fourteen currencies,
  English and Kannada, the storage seam with two implementations run against one
  contract suite, and the backup format including its compatibility with iOS.
- **All four tabs** — Today, Items, Sell and Bills — plus first-run setup and
  its optional third step for customers, the product editor and add-stock
  sheets, the customer editor, the record-a-payment sheet, the statement
  document, the receipt, the bill document, the customer filter, Settings and the
  backup handoff.
- The design system: both palettes, named type roles, metrics, motion, and the
  Phosphor-to-Material icon map — plus the dark/light switch in Settings, which
  also tells the status bar and the few Material surfaces (menus, the date
  picker) which theme they are sitting over.
- CI that runs the domain tests and ships an installable APK.

## Where Android answers better than iOS

Three problems the iOS build solved the hard way are one line here, and the
difference is worth recording rather than quietly enjoying:

| | iOS | Android |
| --- | --- | --- |
| Keyboard must not move the layout | four screen-level declarations, found one bug at a time | `windowSoftInputMode="adjustNothing"`, once, in the manifest |
| Next/Done between fields | a screen-level focus router and a hand-built toolbar, because a numeric keypad has no return key | `ImeAction.Next` on the field |
| Getting it on the phone | developer account, API key, signing, TestFlight, ~20 minutes | download the CI artifact and tap it |
| A new field on a stored type | the synthesised decoder throws on a missing key even with a default, so every shop already on a phone fails to load — `ShopState` decodes by hand | kotlinx.serialization applies the default; nothing to write |

The bottom sheet is hand-drawn on both, for the same reason: the design
specifies a scrim, top-corner-only rounding, a 38×4 handle and an 84% maximum
height, and neither platform's stock sheet exposes those.

## What is not built yet

- **Nothing asserts what a screen renders.** The domain is covered; the Compose
  layer is checked by compiling and by looking at it. Same position as iOS.
- **The date picker is Material's own dialog** — the one place in this app where
  stock chrome shows through. iOS has a compact inline picker that sits in the
  design; Compose does not, and reimplementing a calendar to avoid one dialog is
  not a trade worth making.
- **Payments are not allocated to particular bills**, the same deliberate
  limitation the iOS README explains.
- **The launcher icon is a placeholder** — a mark, not the iOS icon redrawn for
  Android's adaptive shapes.
