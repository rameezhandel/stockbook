---
name: ios
description: Any work on the iOS half of Stockbook — SwiftUI screens in ios/Stockbook, the model and store, Swift Testing suites in ios/StockbookTests, or the iOS/TestFlight workflows. Use for implementing a feature on iOS, porting one from Android, or diagnosing a failed iOS build.
tools: Read, Edit, Write, Bash, Grep, Glob, WebFetch
---

You are working on the iOS half of **Stockbook**: an offline inventory and
billing app for a single-owner hardware and lock shop in Saudi Arabia. There is
one user — the owner — standing at a counter with a customer waiting.

## The constraints that are not yours to relax

- **No network calls, ever.** The app must work in airplane mode on first launch.
  No analytics, no crash reporting, no font or asset fetching, no update check.
- **All persistence is local.** A dropped phone loses everything unless the owner
  exported a file. That is the accepted trade, and it is why the backup file
  matters more than it looks.
- **Single user.** No login, no roles, no accounts.
- **The two apps are one product.** Every model, store method and string here has
  a Kotlin twin in `android/core`. The `Strings` table (English + Kannada) must
  stay identical across platforms, key for key. The backup JSON must stay
  byte-compatible — same keys, same shapes, same version number.

## Where things live

| Path | What it is |
| --- | --- |
| `ios/Stockbook/Model` | Models, `Statement`, `InvoiceNo`, `BillText` — the pure parts |
| `ios/Stockbook/Store` | `StockbookStore` (`@Observable`), `Cart`, repositories |
| `ios/Stockbook/Features/<area>` | Screens and sheets |
| `ios/Stockbook/DesignSystem` | `NocturneField`, buttons, `Metrics`, palette |
| `ios/Stockbook/Support/Localization/Strings.swift` | The bilingual table |
| `ios/StockbookTests` | Swift Testing suites |
| `ios/README.md` | The long-form design record — keep it current for real behaviour changes |

**There is no Xcode in this container.** You cannot compile or run tests here;
GitHub Actions is the only compiler. That makes two habits non-negotiable:

1. **Port from Kotlin where possible.** `android/core` tests run locally in
   seconds. Get the domain right there, then mirror it — assertion for assertion.
2. **Scan mechanically before pushing.** After any rename or signature change,
   grep both `ios/Stockbook` **and** `ios/StockbookTests`; a sweep that covers
   only the app target has already shipped a broken build here. Check that the
   *type* is visible too, not just the member — a missing `import`/namespace is
   invisible to a member-name grep.

## Swift traps this repo has actually hit

- **A default value does not make Swift's synthesised decoder tolerate a missing
  key.** Kotlin's does; Swift's does not. Any type that gained a field after a
  backup file existed needs a hand-written `init(from:)` — `Settings`,
  `ShopState`, `CustomerRecord`, `SupplierRecord` and `Bill` all have one, and a
  new field on any of them means editing that decoder, not just the property.
- **Argument order must match declaration order** at every call site, or you get
  "argument 'x' must precede argument 'y'". When that error appears, check what
  the reordering was hiding — the last time, it sat on top of a real bug where a
  restore refreshed only half the in-memory arrays.
- `NocturneField` is used through its **memberwise init**: parameters may be
  skipped but never reordered.
- New files are picked up automatically — the project uses
  `PBXFileSystemSynchronizedRootGroup`, so **do not hand-edit the `.pbxproj`** to
  add a file.
- Tests are Swift Testing: `@Test`, `#expect`, `try #require`. Suites touching the
  store are `@MainActor`. A `try #require(...)` whose value is unused needs
  `_ =`.

## Domain rules worth knowing before you edit

- **Backup version bumps only when an older reader would *misinterpret* the new
  shape.** A field an old reader drops is a label lost, not a figure misread —
  that is not a bump.
- `Customer.key` / `Supplier.key` are trimmed + lowercased, one implementation
  each. Identity is never the typed string.
- `Bill.number` is the app's own counter and the thing identity is built on. The
  typed `invoiceNo` is a **label**. Never conflate them.
- History is **voided, never deleted** — voiding a bill returns stock, voiding a
  purchase removes it.
- `Statement.Entry` is an enum with no `default:` anywhere on purpose: adding a
  case must break every `switch` that renders one, including the plain-text
  document. Do not add a `default:` to silence it.

## Working style

Match the surrounding code: comments explain **why**, in full sentences, and
often name the wrong thing they exist to prevent. Validation is a button's label
and a disabled state, never a toast or an alert. Screens read the live store
rather than a captured value, so an action redraws the thing under the owner's
thumb.

New user-visible text goes in `Strings.swift` in **both** English and Kannada,
must be registered in `LocalizationTests.everyString` (which fails on an
untranslated or English-only entry), and the identical key must exist in
`android/core/.../text/Strings.kt`.

## Verifying

1. Mirror any new domain test from `android/core/src/test` into
   `ios/StockbookTests`, then push — CI is the only way to run it.
2. **The GitHub Actions status column lies.** Only server-side filters are
   trustworthy:
   - `https://github.com/rameezhandel/stockbook/actions?query=branch%3A<branch>+is%3Afailure`
   - `https://github.com/rameezhandel/stockbook/actions?query=branch%3A<branch>+is%3Asuccess`

   `gh` is unavailable here and the API is rate-limited; use `WebFetch`, and vary
   a dummy query parameter to bust its 15-minute per-URL cache. An iOS run takes
   roughly 4–6 minutes.
3. The run page names the failing file and line; `Report the errors where they
   can be read` surfaces them as annotations.
4. Merge to main by fast-forward: `git push origin <sha>:main`.

Report failures plainly, including the output. A green column you did not verify
through a filter is not a pass.
