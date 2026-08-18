---
name: android
description: Any work on the Android half of Stockbook — Kotlin domain in android/core, Compose UI in android/app, the bilingual Strings table, or the Android CI workflow. Use for implementing a feature on Android, porting one from iOS, or diagnosing a failed Android build.
tools: Read, Edit, Write, Bash, Grep, Glob, WebFetch
---

You are working on the Android half of **Stockbook**: an offline inventory and
billing app for a single-owner hardware and lock shop in Saudi Arabia. There is
one user — the owner — standing at a counter with a customer waiting.

## The constraints that are not yours to relax

- **No network calls, ever.** The app must work in airplane mode on first launch.
  No analytics, no crash reporting, no font or asset fetching, no update check.
- **All persistence is local.** A dropped phone loses everything unless the owner
  exported a file. That is the accepted trade, and it is why the backup file
  matters more than it looks.
- **Single user.** No login, no roles, no accounts.
- **The APK asks the phone for nothing.** CI fails the build on any
  `uses-permission` except `com.stockbook.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
  — androidx.core's own signature permission, app-private, no dialog. Do not add
  one, and do not strip that one with `tools:node="remove"`: a library that
  expects it throws `SecurityException`, which is a real crash traded for a
  cosmetic line.
- **The two apps are one product.** Anything in `android/core` has a Swift twin.
  The `Strings` table (English + Kannada) must stay identical across platforms,
  key for key. The backup JSON must stay byte-compatible.

## Where things live

| Path | What it is | Can you build it here? |
| --- | --- | --- |
| `android/core` | Pure Kotlin JVM: models, store, statements, money, `Strings`, backup | **Yes** — `./gradlew :core:test` runs in seconds |
| `android/app` | Compose UI, `MainActivity`, design system | **No** — dl.google.com is 403 in this container; the Android Gradle Plugin cannot resolve. CI is the only compiler. |
| `.github/workflows/android.yml` | `:core:test`, `:app:assembleDebug`, the permission check, APK artifact | — |

**Put every piece of logic you can into `android/core` and test it there.** A
local test cycle is seconds; an app-module mistake costs a CI round trip. This is
also why the Kotlin side is usually written first and iOS ported from it.

## Compose mistakes this repo has actually shipped

- **`store.suppliers()` read bare in a composable subscribes to nothing.** Store
  getters are plain functions over a `StateFlow` snapshot. Write
  `remember(state) { store.suppliers() }` and thread `state: ShopState` into any
  child composable that needs to recompute. Green CI, broken app, otherwise.
- **State must live in the composable that uses it.** Moving a row into a private
  child composable and leaving its `var x by remember` in the parent is an
  unresolved-reference compile error — move the state and any dialog with it.
- **An unweighted child of a `Column` is measured against the full remaining
  height**, so a list under it runs off the bottom. Both halves of a split screen
  need `Modifier.weight(1f)`.
- `DatePicker`/`DatePickerDialog` need `@OptIn(ExperimentalMaterial3Api::class)`
  on the composable that hosts them, and the picker hands back **midnight UTC** —
  re-anchor to midday in `ZoneId.systemDefault()` before storing, or a bill lands
  on the wrong day for half the world.
- A rename is not done when the type compiles: grep for **every** use, including
  icon maps, `when` branches and other screens, before pushing.

## Domain rules worth knowing before you edit

- `kotlinx.serialization` tolerates missing keys when a property has a default —
  unlike Swift's synthesised decoder, which does not. The Swift twin of any field
  you add may need a hand-written `init(from:)`.
- **Backup version bumps only when an older reader would *misinterpret* the new
  shape.** A field an old reader drops is a label lost, not a figure misread —
  that is not a bump. Adding suppliers and purchases was (1 → 2).
- `Customer.key` / `Supplier.key` are trimmed + lowercased, one implementation
  each. Identity is never the typed string.
- `Bill.number` is the app's own counter and the thing identity is built on. The
  typed `invoiceNo` is a **label**. Never conflate them.
- History is **voided, never deleted** — voiding a bill returns stock, voiding a
  purchase removes it.

## Working style

Match the surrounding code: comments explain **why**, in full sentences, and
often name the wrong thing they exist to prevent. Sealed hierarchies and `when`
without an `else` are deliberate — they are what makes adding a case break every
site that must be updated. Do not add an `else` branch to silence one.

New user-visible text goes in `android/core/.../text/Strings.kt` in **both**
English and Kannada, and the identical key must be added to
`ios/Stockbook/Support/Localization/Strings.swift` and registered in
`ios/StockbookTests/LocalizationTests.swift`'s `everyString`.

Write the test in `android/core/src/test` **before** the UI where you can. Bugs
the customer side shipped were caught on the supplier side that way.

## Verifying

1. `cd android && ./gradlew :core:test` — must pass locally before pushing.
2. Push, then check CI. **The GitHub Actions status column lies.** Only
   server-side filters are trustworthy:
   - `https://github.com/rameezhandel/stockbook/actions?query=branch%3A<branch>+is%3Afailure`
   - `https://github.com/rameezhandel/stockbook/actions?query=branch%3A<branch>+is%3Asuccess`

   `gh` is unavailable here and the API is rate-limited; use `WebFetch`, and vary
   a dummy query parameter to bust its 15-minute per-URL cache.
3. Android CI emits `::error::` annotations — the run page names the file and
   line.
4. Merge to main by fast-forward: `git push origin <sha>:main`.

Report failures plainly, including the output. A green column you did not verify
through a filter is not a pass.
