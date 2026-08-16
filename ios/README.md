# Stockbook — native iOS

SwiftUI + SwiftData implementation of the design in
[`../project/design_handoff_stockbook/README.md`](../project/design_handoff_stockbook/README.md).
That file is the spec; this one describes how the code is arranged.

**Requirements:** Xcode 16+, iOS 17.0+, iPhone only, portrait, dark-only.
Open `Stockbook.xcodeproj` and run — there are no dependencies, no package
resolution, no `pod install`.

> This is the **architecture + foundations** pass. The data layer, the design
> system and two screens are real; the remaining screens are scaffolded and
> listed under [What is not built yet](#what-is-not-built-yet).

---

## The three constraints everything else follows from

1. **No network calls, ever.** No analytics, no crash reporting, no remote
   config, no CloudKit — `ModelStack` passes `cloudKitDatabase: .none`
   explicitly so nobody adds sync by accident. The app is fully functional in
   airplane mode on first launch.
2. **All persistence is local.** A dropped phone loses everything unless the
   owner exported a file, and the UI keeps saying so.
3. **Single user.** No login, no roles, no tenancy.

## Layout

```
ios/
├── Stockbook.xcodeproj        hand-written, Xcode 16 synchronized folder groups
├── project.yml                XcodeGen fallback, kept in step with the above
├── Stockbook/
│   ├── App/                   entry point, router, root view + tab shell
│   ├── DesignSystem/          Nocturne tokens, type scale, metrics, components
│   ├── Model/                 SwiftData @Model types
│   ├── Store/                 StockbookStore (all business rules) + the cart
│   ├── Transfer/              the backup file format and its I/O
│   ├── Features/              one folder per screen
│   └── Support/               money, copy and date helpers
└── StockbookTests/            Swift Testing suites over the domain layer
```

Files are picked up by **synchronized folder groups** — adding a `.swift` file
anywhere under `Stockbook/` puts it in the target with no `.pbxproj` edit and no
merge conflict.

## How the layers fit

**Views read, the store writes.** Screens use `@Query` so SwiftData keeps them
fresh, but no view mutates a model directly. Stock arithmetic, bill numbering,
snapshotting, voiding and restocking all live in `StockbookStore` — which is
also the layer the tests drive, with no UI in the loop.

**Navigation is flat.** The handoff describes a router
(`setup0 | setup1 | setup2 | home | items | sell | bills | settings`) plus three
overlays that can sit above any screen, and `AppRouter` is exactly that. There is
no drill-down anywhere in this app: every detail view is a bottom sheet or a
full-screen overlay, so there is no `NavigationStack`.

**Persisted vs transient is a real boundary.** Products, bills and settings are
SwiftData. The cart, search strings, sheet drafts and payment mode are `Cart` and
`@State` — a half-typed bill is not history and must not survive a relaunch.

**Required-empty marking has two modes.** A handful of fields marked at once
reads as a checklist; twelve do not. The product editor marks immediately, as
specified. Setup step 3 waits until a field has been visited and left empty —
otherwise four products means twelve accent outlines on arrival, which reads as
twelve errors before the owner has done anything. The footer's gate line carries
the message until then.

**The import state machine lives outside its view.** `ImportFlow` owns the
idle → picked → imported path, so the most destructive operation in the app can
be asserted without a simulator. Its one guarantee: a document only ever comes
out of `confirm()`, and only from `picked` — so a corrupt file, a cancel, or a
second tap cannot reach `replaceEverything`.

**Setup is a draft, not a partial shop.** Nothing entered during the three
steps touches the database until "Open the shop" — a half-finished setup should
leave nothing behind to reconcile. `RootView` shows the flow whenever
`ShopSettings.setupCompleted` is false, which is also what "Start over" clears,
so there is no separate route back into it.

**Setup's price fields sit on `--color-bg`, not `--color-surface`.** The
prototype leaves them the same colour as the card they sit on, distinguished
only by the border. This uses the inset treatment instead, so the stock / cost /
price trio looks identical here and in the product editor — the same three
fields, asked for at two different moments.

**Share is a real share sheet, not a fake state.** The prototype turned its
Share button accent with a "Sending" label, because it could not open anything.
`ShareLink` hands the OS a real file, and the sheet that appears *is* the
feedback — so that state is deliberately not reproduced. Announcing "Sending"
for something we cannot observe would be the screen's one dishonest moment.

**Sell has no mode flag.** Which of the picker and the cart is showing is
derived — the picker appears when the cart is empty, when the search box has
text, or when "Add another item" was tapped. There is no state the owner can get
stranded in, and the search field is shared by both.

**Setup is persisted state, not a route.** `ShopSettings.setupCompleted` decides
whether `RootView` shows setup or the app, so "Start over" is a data operation
rather than a navigation one.

### Data model notes

The handoff's deliberate modelling decisions, and where they are enforced:

| Decision | Where |
| --- | --- |
| Everything sells by the piece — no units, no packs, no fractions | `Product` |
| Bill lines snapshot name and price; editing a product never rewrites history | `StockbookStore.saveBill`, tested in `StoreTests.historyIsImmutable` |
| Cost is *latest paid*, not a weighted average | `StockbookStore.restock(mode: .purchase)` |
| Stock changes in exactly five places, and nowhere else | setup · product editor · save bill (floor 0) · void (restore) · restock |
| Bills are voided, never deleted | `StockbookStore.void` |

Products carry a `uid: UUID` alongside SwiftData's own identifier. That is not
redundancy: `persistentModelID` is local to one store, and a bill line has to
still point at its product after the database has been carried to another phone
in a file.

### The backup file

`BackupDocument` is a real versioned format, not an implementation detail —
it is the *only* way data moves between devices.

```json
{ "version": 1, "exportedAt": "…", "ownerName": "…", "currencySymbol": "SAR ",
  "products": [ { "uid": "…", "name": "…", "stock": 12, "cost": 60, "price": 95 } ],
  "bills":    [ { "number": 1, "createdAt": "…", "total": 190, "paid": 100,
                  "who": "…", "voided": false, "lines": [ … ] } ] }
```

Written as `stockbook-YYYY-MM-DD.json`. On the way back in, `BackupService.decode`
checks the **version before the shape** — a file from a future build may decode
cleanly into today's structs while meaning something different, and the result of
getting that wrong is a destructive whole-database replace. Import is a swap, not
a merge, and the UI gates it behind a warning naming what is about to be lost.

## The design system

`DesignSystem/` is the whole of the visual spec, and feature code never reaches
past it:

- **`Nocturne.swift`** — every colour token. The one file allowed to contain a
  hex literal.
- **`NocturneType.swift`** — the type scale as named roles (`.screenTitle`,
  `.meta`, `.kicker`…), applied with `.nocturneText(_:)`. No feature file names a
  point size. Inter is not bundled; drop `Inter-Regular.ttf` and
  `Inter-Medium.ttf` into `Stockbook/Resources/Fonts/` and they register at
  launch. Until then it falls back to the system face at the same metrics.
- **`Icons.swift`** — the design calls for Phosphor Icons, which are not on the
  system, so each Phosphor name maps to its nearest SF Symbol *in one place*.
  Swapping in real Phosphor glyphs changes this file only.
- **`Metrics.swift`** — spacing, radii, the hairline-and-ambient elevation rule.
- **`Components/`** — button styles, the field, cards and empty states, the
  bottom sheet, the tab bar.

Two places where the implementation deliberately differs from a literal reading
of the spec, both about physical screens rather than the design canvas:

- **Header top padding.** The spec says 58px from the top. Applied literally that
  collides with the Dynamic Island, whose safe inset is 59pt, so
  `screenHeaderPadding()` tops the safe area *up to* 58 and no further.
- **Tab bar bottom padding.** The spec's 24px exists to clear the home
  indicator, which is exactly what the device's bottom safe inset does; the bar
  defers to it and falls back to 24 where there is none.

Bottom sheets are drawn by the app rather than by `.sheet`, because the design
specifies things the system sheet does not expose: an `rgba(16,17,28,0.74)`
scrim, 18px rounding on the top corners only, a specific upward shadow, a 38×4
handle, and the tab bar staying visible behind the scrim.

### Validation, as the design does it

Never a toast, never red text. A required-but-empty input carries an accent
border, and the primary action goes disabled with a label that says what is
missing ("Enter a customer name"). `ProductEditorSheet` is the reference
implementation; `StockbookStore.isProductDraftComplete` is the shared rule behind
both it and setup step 3.

## What is built

- Xcode project, both targets, shared scheme.
- Full design-token layer and component set.
- `Product` / `Bill` / `BillLine` / `ShopSettings`, the SwiftData container, and
  `StockbookStore` with every rule the handoff specifies.
- `Cart` — line management, price override, payment mode, the save gate.
- The backup format, its validation, file export via `.fileExporter`, and the
  destructive-replace import path.
- **Today** — stats, the owed banner (counting distinct people, not bills),
  recent bills, and a backup nudge that writes a real file.
- **Items** — search, rows with margin and stock colouring, empty states.
- **Sell** — product picker and cart: per-line stepper with live stock, the
  editable price with its override treatment and Reset, the required customer
  field with an upward suggestion dropdown, full/part payment, and save.
- **Receipt** — the full-screen confirmation, including the faded rule.
- **Bills** — full history newest-first, with void-and-restore on every live
  bill and the muted treatment on voided ones.
- **Settings** — owner name and counts, real export to Files, Share via the OS
  share sheet, and a validated import gated behind a warning that names what
  will be lost. Plus Start over.
- **First-run setup** — all three steps: name, product names with the four
  suggestion capsules, then the stock-and-prices grid with its completeness gate.
- **Product editor** and **Add stock** sheets, both complete.
- 40-odd tests over the domain layer.

## What is not built yet

Every screen in the handoff is built. What remains is verification rather than
construction:

- **Nothing asserts what a view renders.** The `Screenshots` workflow now walks
  the app in a simulator and publishes a picture of every screen to the
  `screenshots` branch on each push to `main`, so the views are at least *seen*.
  It is a camera, not a test — it cannot tell you a screen looks right.
- **View bodies remain untested.** The import gating is now covered by
  `ImportFlowTests` after being lifted out of the view, but nothing asserts what
  any SwiftUI body renders — the screenshots are for looking at, not for failing
  a build.

**Not built, and not to be built without asking:** low-stock alerts, a customer
ledger, returns, bill editing beyond void, printable receipts, barcode scanning,
product photos, multi-user, VAT, reporting, and weighted-average costing. All
were considered and deliberately cut.

## Tests

Two suites, deliberately separated. `StockbookTests` is correctness and gates
every push. `StockbookUITests` only takes screenshots, runs in its own workflow,
and is allowed to fail — a stumbling walkthrough should cost you pictures, not a
red tick on working code.

`⌘U`, or:

```sh
xcodebuild test -scheme Stockbook -destination 'platform=iOS Simulator,name=iPhone 16'
```

The unit suite covers the rules where a plausible-looking wrong implementation is easy to
write: stock flooring at zero, part payments clamping to the total, history
surviving a product edit, voiding being idempotent, the owed banner counting
people rather than bills, suggestion ranking, cost being latest-paid, and the
backup round trip and its rejections.

## If the project file ever breaks

`Stockbook.xcodeproj` is hand-written. If it goes stale or conflicts badly:

```sh
brew install xcodegen && cd ios && xcodegen generate
```

`project.yml` reproduces the same two targets and settings. Keep the two in step.
