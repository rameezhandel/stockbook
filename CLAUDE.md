# Working on Stockbook

Offline inventory and billing for a single-owner hardware and lock shop in Saudi
Arabia. **Two native apps, one product** — iOS in SwiftUI, Android in Kotlin and
Compose — held to the same rules, the same wording in two languages, and the same
file format.

There is one user: the owner, standing at a counter with a customer waiting.

## The constraints that are not yours to relax

- **No network calls, ever.** No analytics, no crash reporting, no sync, no font
  fetching, no update check. Both apps work in airplane mode on first launch.
- **All persistence is local.** A dropped phone loses everything unless the owner
  exported a file. That is the accepted trade, and it is why the backup format
  matters more than it looks.
- **Single user.** No login, no roles, no accounts.
- **The APK asks the phone for nothing.** CI reads the built APK and fails on any
  `uses-permission` except `com.stockbook.application.debug.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
  — the **debug** id, because that is the APK the check reads, and `debug` gets
  `applicationIdSuffix = ".debug"` so the two builds can sit on one phone
  — androidx.core's own signature permission, app-private, no dialog. Do not add
  one. Do not strip that one with `tools:node="remove"`: a library expecting it
  throws `SecurityException`, a real crash traded for a cosmetic line.
- **Anything in `android/core` has a Swift twin**, and vice versa. The `Strings`
  table must stay identical key for key. The backup JSON must stay
  byte-compatible.

## What can actually be compiled here

This is the single biggest influence on how to work, so it comes before anything
else.

| | Build locally? |
| --- | --- |
| `android/core` | **Yes.** `cd android && ./gradlew :core:test` — 313 tests, ~15s |
| `android/app` | **No.** `dl.google.com` is 403 here; the Android Gradle Plugin cannot resolve |
| `ios/` | **No.** No macOS, no Xcode, no Swift compiler |

**Put every rule you can into `android/core` and test it there.** A local cycle
is seconds; anything else is a CI round trip. That is also why the Kotlin side is
usually written first and the Swift side ported from it.

The cost is real and has been paid: of the eight CI failures during the photo
feature, **seven were iOS** at five to ten minutes each. Android failed once,
because Android can be half-compiled here and the mistake died in seconds.

## Before every push

```sh
python3 tools/check.py            # ~1s, static, cross-platform invariants
cd android && ./gradlew :core:test
```

`tools/check.py` asserts the things that are invisible until they cost a round
trip: string-table parity, `LocalizationTests` registration, and every field the
bill form collects being handed to `saveBill` on both platforms. Each check is
there because that exact thing has really gone wrong. Neither command replaces
the other.

## The traps that have actually cost round trips

**Swift**

- **A default does not make the synthesised decoder tolerate a missing key.** It
  throws. Adding `creditNotes` this way made every older backup unreadable. New
  fields on a `Codable` that reads old files need `decodeIfPresent(...) ?? []`,
  and if the type has a hand-written `init(from:)` you must add the line yourself
  — `tools/check.py` will tell you if you forget.
- **`Loc` is main-actor isolated.** Reading it inside a closure that is not —
  `PhotosPicker`'s label, a nested type's method — is a compile error in Swift 6
  mode. Read the string in the enclosing view and pass it in as a `String`.
- **`BackupService.encode` returns `Data` here and `String` in Kotlin.** Twinned
  tests that forget this fail to compile.
- Swift 5 language mode: **no `@retroactive`.** Wrap rather than conform
  retroactively.
- An optional property *is* tolerated when missing — the synthesised decoder uses
  `decodeIfPresent` for optionals. That asymmetry with defaulted non-optionals is
  the whole trap.
- **A `.transition` only runs when *that* view is the one being inserted.** Put
  it on a child of a custom view and insert the custom view, and SwiftUI animates
  the parent with the default `.opacity` while the child's transition never gets a
  turn. Every bottom sheet in the app faded in for months with
  `.transition(.move(edge: .bottom))` written on it. The `if` and the
  `.transition` have to sit at the same level, inside a container that is always
  mounted.
- The memberwise initialiser takes its arguments **in declaration order**.
  `NocturneField(text:keyboard:height:)` does not compile when `height` is
  declared first — and the error points at the call, not at the swap.

**Kotlin / Compose**

- **`store.suppliers()` read bare in a composable subscribes to nothing.** Store
  getters are plain functions over a `StateFlow` snapshot. Write
  `remember(state) { store.suppliers() }` and thread `state: ShopState` into any
  child that must recompute. Green CI, broken app, otherwise.
- `BitmapFactory.decodeStream` **returns null by contract** when
  `inJustDecodeBounds` is set. Reading that null as failure made every photograph
  on Android unreadable.
- `DatePicker` hands back **midnight UTC** — re-anchor to midday in
  `ZoneId.systemDefault()` or a bill lands on the wrong day for half the world.
- **`Customer` and `Supplier` share no supertype.** A value holding either —
  `if (isSupplier) store.suppliers() else store.customers()` — infers as
  `List<Any>`, and every field access on it fails to resolve. Reduce to the
  field you actually want *inside* each branch. Written the wrong way round it
  compiles nowhere, and `android/app` has no local build, so each occurrence
  costs a CI round trip; two screens cost one each, because only the line CI
  named was fixed the first time. **Grep for the pattern after the first fix,
  not just the line in the error.**
- `kotlinx.serialization` fills a missing key from the property's default. Swift
  does not. The Swift twin of a field you add may need hand-written decoding.

**Both**

- A rename is not done when it compiles. Grep for **every** use — icon maps,
  `when` branches, other screens, the twin platform.
- **A new field on `saveBill` is not done when the box appears on the form.**
  Every parameter of `saveBill` and `updateBill` carries a default, so a screen
  that collects a figure and forgets to pass it compiles clean and tests clean.
  The discount shipped that way on Android: the form showed 100 becoming 90 and
  the store wrote 100. `tools/check.py` now counts each field at both call sites
  on both platforms — add the new one to `BILL_FIELDS`.
- Backup fields have **four** call sites: export and restore, on each platform.
  `paymentNo` once matched three of four and would have dropped every receipt
  number on the way to a new phone.

## Domain rules worth knowing before you edit

- **Backup version bumps only when an older reader would _misinterpret_ the new
  shape**, not merely lose a label. Credit notes bumped it to **3**, because a
  reader dropping them shows every credited customer owing more than they do.
  Invoice numbers, receipt numbers, the shop address and photograph references
  did not.
- `Customer.key` / `Supplier.key` are trimmed and lowercased. Identity is never
  the typed string.
- **There are two orders for people and neither is the other's default.**
  `customers()` / `suppliers()` are biggest-debt-first, which is what Today's
  banner and both owed sheets are built on. `customers(matching:)` /
  `suppliers(matching:)` are the directory: by name, filtered on name and phone,
  for a screen you go to in order to *find* somebody. `PartyDirectoryTests` pins
  that they stay apart — collapsing them would silently rename whoever Today's
  banner points at.
- `Bill.number` is the app's own counter and what identity is built on. The typed
  `invoiceNo` is a **label**. Never conflate them.
- **A mistake is edited or removed, not voided.** `deleteBill` returns the stock
  and frees the number. A `voided` key survives in old files and is ignored.
- **Read `Purchase.items`, never `Purchase.lines`.** A delivery used to hold one
  product, and those four fields — `productUid`, `name`, `qty`, `unitCost` — are
  still on the type as the way in from records written then. `items` folds them
  into a single line and is the only thing the app should read; `lines` is the
  storage. Reading the old four compiles perfectly and shows nothing for every
  delivery entered since, which is how five screens nearly shipped blank.
- **Every number is typed by the owner, never suggested** — invoice, delivery,
  credit note and receipt numbers, each checked against its own series. Receipt
  1024 and invoice 1024 are different slips.
- **The owner's spending is joined to nothing.** An `Expense` has no customer,
  no supplier and no bill; it does not move Sold, Receivable or Payable, it does
  not touch the shelf, and it cannot reach a statement — that is a document the
  owner may turn round and show a customer. Half of `ExpenseTests` asserts the
  absences rather than the behaviour, because a rule of the form "these two must
  never meet" decays silently. It is also why the field did not bump the backup
  version: a reader that drops expenses misreads no figure.
- Photographs are **ids in the book, files on disk**. Cleanup runs one way only:
  a file nothing references may be deleted; an id whose file is missing is never
  pruned, because a book restored ahead of its pictures must re-adopt them.
- **The backup file is a store-only ZIP** — `stockbook.json` plus
  `photos/<id>.jpg` — hand-written on both platforms from the specification.
  `java.util.zip` is deliberately unused on Android: iOS has no zip reader, so
  the format had to be hand-written for Swift regardless, and two independent
  implementations would drift. Kotlin is the original because it can be tested
  against `java.util.zip`; Swift is the port, held to it by a shared base64
  fixture asserted in both suites. The DOS timestamp is fixed at 1980-01-01 so
  that fixture can be a constant — do not make it "now".
- **Which way the archive fails is decided.** A picture the phone has lost is
  skipped on export. A damaged picture on import costs that picture, never the
  book: the bill keeps the id either way, so it can be re-adopted later. Reading
  the document must not walk every entry — doing so CRC-checks every photograph
  and would refuse a whole import over one bad one.

## Working style

Match the surrounding code. Comments explain **why**, in full sentences, and
often name the wrong thing they exist to prevent. Sealed hierarchies and `when`
without an `else` are deliberate — they are what makes adding a case break every
site that must be updated. Do not add an `else` to silence one.

New user-visible text goes in `android/core/.../text/Strings.kt` in **both**
English and Kannada, with the identical key in
`ios/Stockbook/Support/Localization/Strings.swift`, registered in
`ios/StockbookTests/LocalizationTests.swift`. `tools/check.py` enforces all
three.

Write the test in `android/core/src/test` **before** the UI wherever you can.

Work in small, watchable commits. Do not pause for approval at every commit
boundary; keep going until the piece is done or something genuinely needs a
decision.

## Verifying and merging

1. `python3 tools/check.py`, then `./gradlew :core:test`.
2. Push. **Android runs on every push; iOS runs only on a pull request** —
   macOS minutes are expensive and running both triggers builds the same commits
   twice. **A change that never opens a pull request is never built on iOS.**
3. **The Actions status column lies.** Only server-side filters are trustworthy:
   - `…/actions?query=branch%3A<branch>+is%3Afailure`
   - `…/actions?query=branch%3A<branch>+is%3Asuccess`

   `gh` is not installed and the API returns 403; use `WebFetch` and vary a dummy
   query parameter to bust its 15-minute cache. A green column you did not verify
   through a filter is **not** a pass — this has been got wrong more than once.
4. Both workflows write compiler and test errors to the **job summary**, with
   file and line. That is what to read on a failure.
5. Merge by fast-forward: `git push origin <sha>:main`.

Report failures plainly, with the output.

## Subagents

Use them for **read-heavy exploration** — surveying how something works across
many files, where only the conclusion is needed. That pays.

Do **not** use them for implementation. The work here is inherently serial —
domain, then the Swift twin, then UI, then CI — and handing it off costs more
context than it saves.

## Where things are

| Path | What |
| --- | --- |
| `android/core` | Pure Kotlin JVM: models, store, statements, money, `Strings`, backup |
| `android/app` | Compose UI, `MainActivity`, design system, photo storage |
| `…/feature/book` | The Book: the party directory, one party's screen, the three panes |
| `ios/Stockbook` | SwiftUI app; `Model`, `Store`, `Transfer`, `Features`, `Support` |
| `tools/check.py` | The cross-platform invariants |
| `tools/make_play_assets.py` | Launcher icon and Play listing artwork |
| `play/`, `docs/` | Store listing assets; the privacy policy; [releasing](docs/releasing.md) |
| `BACKLOG.md` | What is left before going live, and what was decided against |
