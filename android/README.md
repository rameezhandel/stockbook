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
cd android && ./gradlew :core:test        # 66 tests, about 8 seconds
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
- **The palette.** `Nocturne.kt` carries the same hex values. Two apps drawn from
  one palette stay one product.
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

Easier than iOS: no developer account, no signing ceremony, no TestFlight.

Every CI run uploads **`stockbook-debug-apk`** as an artifact. Download it on the
phone, allow installing from that source once, and tap it.

## What is built

Everything the iOS app does.

- **The whole domain**, with 66 tests: products, bills, voiding, restock,
  customers and their case-insensitive identity, money in fourteen currencies,
  English and Kannada, the storage seam with two implementations run against one
  contract suite, and the backup format including its compatibility with iOS.
- **All four tabs** — Today, Items, Sell and Bills — plus first-run setup, the
  product editor and add-stock sheets, the receipt, the bill document, the
  customer filter, Settings and the backup handoff.
- The design system: palette, named type roles, metrics, motion, and the
  Phosphor-to-Material icon map.
- CI that runs the domain tests and ships an installable APK.

## Where Android answers better than iOS

Three problems the iOS build solved the hard way are one line here, and the
difference is worth recording rather than quietly enjoying:

| | iOS | Android |
| --- | --- | --- |
| Keyboard must not move the layout | four screen-level declarations, found one bug at a time | `windowSoftInputMode="adjustNothing"`, once, in the manifest |
| Next/Done between fields | a screen-level focus router and a hand-built toolbar, because a numeric keypad has no return key | `ImeAction.Next` on the field |
| Getting it on the phone | developer account, API key, signing, TestFlight, ~20 minutes | download the CI artifact and tap it |

The bottom sheet is hand-drawn on both, for the same reason: the design
specifies a scrim, top-corner-only rounding, a 38×4 handle and an 84% maximum
height, and neither platform's stock sheet exposes those.

## What is not built yet

- **Nothing asserts what a screen renders.** The domain is covered; the Compose
  layer is checked by compiling and by looking at it. Same position as iOS.
- **The launcher icon is a placeholder** — a mark, not the iOS icon redrawn for
  Android's adaptive shapes.
