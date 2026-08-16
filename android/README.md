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

- The whole domain, with 66 tests: products, bills, voiding, restock, customers
  and their case-insensitive identity, money in fourteen currencies, English and
  Kannada, the storage seam with two implementations run against one contract
  suite, and the backup format including its compatibility with iOS.
- The Gradle build, both modules, and CI.
- The colour palette.

## What is not built yet

**The screens.** `MainActivity` renders a stub over a finished domain.

That is on purpose. Google's Maven is unreachable from the environment this was
written in, so Compose code cannot be compiled here at all — only in CI. Writing
several thousand lines of unverifiable UI in one go and hoping CI catches it is
exactly how the iOS build lost an afternoon to a keyboard toolbar. The domain
went first because it could be proven; the screens follow against a toolchain
already known to compile.
