---
name: android
description: Any work on the Android half of Stockbook — Kotlin domain in android/core, Compose UI in android/app, or the Android CI workflow. Use for implementing a feature on Android, porting one from iOS, or diagnosing a failed Android build.
tools: Read, Edit, Write, Bash, Grep, Glob, WebFetch
---

The project's rules, constraints, traps and verification loop are in
**[`CLAUDE.md`](../../CLAUDE.md)** at the repository root. Read it first; it
applies here unchanged and is the one copy. This file is only what is specific to
Android.

## The split, and why it decides how you work

`android/core` is pure Kotlin on the JVM — models, store, statements, money,
`Strings`, the backup format — with **no Android dependency at all**, so
`./gradlew :core:test` runs in about fifteen seconds on any machine with a JDK.

`android/app` is Compose and nothing else, and **cannot be built here**:
`dl.google.com` is 403 in this container, so the Android Gradle Plugin never
resolves. CI is the only compiler it has.

**Put every rule you can into `:core` and test it there.** That is also why the
Kotlin side is usually written first and the Swift side ported from it.

`:app` has no local test task at all. That gap is not theoretical: `PhotoStore`
shipped refusing every photograph, because `BitmapFactory.decodeStream` returns
null by contract when `inJustDecodeBounds` is set and that null was read as
failure. Nothing in CI could have caught it. Code in `:app` should be thin, and
anything with a rule in it belongs one module down.

## Compose mistakes this repo has actually shipped

- **`store.suppliers()` read bare in a composable subscribes to nothing.** Store
  getters are plain functions over a `StateFlow` snapshot. Write
  `remember(state) { store.suppliers() }`, and thread `state: ShopState` into any
  child composable that has to recompute. Green CI, broken app, otherwise.
- **State must live in the composable that uses it.** Moving a row into a private
  child and leaving its `var x by remember` in the parent is an unresolved
  reference — move the state, and any dialog with it.
- **An unweighted child of a `Column` is measured against the full remaining
  height**, so a list under it runs off the bottom. Both halves of a split screen
  need `Modifier.weight(1f)`.
- `DatePicker` / `DatePickerDialog` need
  `@OptIn(ExperimentalMaterial3Api::class)` on the composable that hosts them,
  and the picker hands back **midnight UTC** — re-anchor to midday in
  `ZoneId.systemDefault()` before storing, or a bill lands on the wrong day for
  half the world.
- `NocturneField` is `singleLine` unless told otherwise, and that beats whatever
  `ImeAction` says. A multi-line box needs `multiline = true`.

## The permission promise is Android's to keep

The manifest asks for nothing, and CI proves it by reading the built APK rather
than the manifest — libraries merge their own in. The camera works *because*
nothing is declared: Android requires `CAMERA` for `ACTION_IMAGE_CAPTURE` only
from apps that declare it. The gallery goes through `PickVisualMedia`, which
needs no permission on any version, and photographs are written to app-private
storage, which needs none either.

Adding a dependency is the realistic way this breaks. If CI's permission step
fails after you add one, the dependency is the cause.

## Getting a build onto a phone

Every push touching `android/` uploads a debug APK as the `stockbook-debug-apk`
artifact. The debug keystore is committed on purpose — see
[`android/keystore/README.md`](../../android/keystore/README.md), which also
covers the release key, which is not.
