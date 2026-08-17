# Stockbook — native iOS

SwiftUI implementation of the design in
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
│   ├── Model/                 plain value types — no framework, no persistence
│   ├── Store/                 StockbookStore (all rules) + the storage seam + cart
│   ├── Transfer/              the backup file format and its I/O
│   ├── Features/              one folder per screen
│   └── Support/               money, dates, and the two-language string table
└── StockbookTests/            Swift Testing suites over the domain layer
```

Files are picked up by **synchronized folder groups** — adding a `.swift` file
anywhere under `Stockbook/` puts it in the target with no `.pbxproj` edit and no
merge conflict.

## How the layers fit

**Views read, the store writes.** Screens read `store.products`, `store.bills`
and `store.settings`; the setters are private, so "no view mutates data" is
enforced rather than requested. Stock arithmetic, bill numbering, snapshotting,
voiding and restocking all live in `StockbookStore` — the layer the tests drive,
with no UI in the loop.

**Storage is behind one protocol.** `StockbookRepository` is the only seam
between the rules and the disk. The shipping implementation is
`JSONFileRepository`: the whole shop as one atomically-written JSON file. That
suits what this app actually is — 50–300 products, one user, no queries, no
reporting — and it has no minimum OS version, which SwiftData's iOS 17 floor
turned out to matter for.

The protocol's writes are incremental (`upsert`, `append`, `update`) rather than
`save(wholeState:)`. A whole-state save is trivial for a file and ruinous for a
real database, so the easy version of this protocol would have quietly ruled out
the engines it exists to permit. Swapping in Core Data or SQLite means writing
one type and adding a line to `RepositoryTests`, which runs the same contract
suite against every implementation so no backing can rot unnoticed.

**Navigation is flat.** The handoff describes a router
(`setup0 | setup1 | setup2 | home | items | sell | bills | settings`) plus three
overlays that can sit above any screen, and `AppRouter` is exactly that. There is
no drill-down anywhere in this app: every detail view is a bottom sheet or a
full-screen overlay, so there is no `NavigationStack`.

**Persisted vs transient is a real boundary.** Products, bills and settings go
through the repository. The cart, search strings, sheet drafts and payment mode
are `Cart` and `@State` — a half-typed bill is not history and must not survive
a relaunch.

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

### Customers

There is no customer record. A bill carries a name, and `Customer` is assembled
from history whenever it is asked for — which at this size is free, and means a
customer can never go stale or be orphaned from their bills.

**Identity is `Customer.key`: trimmed and lowercased.** `"ahmed "` and `"Ahmed"`
are one person, which is the only workable rule for a name typed fresh at a
counter for every bill. Everything that groups, matches or filters customers
goes through that one function — the debtors banner, the cart's suggestions, and
the Bills filter.

The **display** name is the spelling from the customer's most recent bill, so
correcting the capitalisation on a new bill corrects it everywhere it is shown,
without rewriting what older bills record. Bills keep what was actually typed on
them; history does not move.

When there is more to know about a customer than a name — a phone number, an
address, a credit limit — the shape that fits is a stored record keyed by `key`
and merged onto `Customer` in `StockbookStore.customers()`. The derived figures
stay derived; only the typed-in facts get stored. That is why callers are handed
a `Customer` today rather than a bare name string.

### Data model notes

The handoff's deliberate modelling decisions, and where they are enforced:

| Decision | Where |
| --- | --- |
| Everything sells by the piece — no units, no packs, no fractions | `Product` |
| Bill lines snapshot name and price; editing a product never rewrites history | `StockbookStore.saveBill`, tested in `StoreTests.historyIsImmutable` |
| Cost is *latest paid*, not a weighted average | `StockbookStore.restock(mode: .purchase)` |
| Stock changes in exactly five places, and nowhere else | setup · product editor · save bill (floor 0) · void (restore) · restock |
| Bills are voided, never deleted | `StockbookStore.void` |

Products carry a `uid: UUID` and nothing else identifies them. A row id or an
object reference would be local to one store, and a bill line has to still point
at its product after the database has been carried to another phone in a file —
or after the storage engine underneath has been swapped.

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

## Two languages

English and Kannada, switched in Settings and applied to every screen at once.

**There is no `.strings` file.** `Support/Localization/Strings.swift` is one
struct holding both languages of every sentence, side by side:

```swift
var saveBill: String { pick("Save bill", "ಬಿಲ್ ಉಳಿಸಿ") }
```

A key-based catalogue would let an untranslated line ship silently as an English
word on a Kannada screen. Here a new string cannot compile until both languages
are written, and a reviewer reads the pair on one line without holding a key in
their head. `LocalizationTests` walks every entry and fails on any two columns
that are identical or any Kannada column with no Kannada letters in it.

**Nothing is assembled from fragments.** Word order differs between the two
languages, so a phrase with a number or a name in it is a single function taking
that number or name — `Loc.onlyInStock(3)`, not `Loc.only + n + Loc.inStock`.
Counts are written out per language rather than pluralised by rule, because
Kannada does not form a plural by adding an "s".

**The language is the shop's, not the phone's.** It is stored in `Settings`, so
it survives a relaunch and a Start over — being handed setup in a language you
cannot read is not a decision the owner made. Import does *not* take the
language from the file: a backup carried over from an English shop must not
switch this one. `Settings` decodes every field as "if present, else the
default", so a shop saved by the build before languages existed still opens.

**What is never translated:** product names, customer names and bill contents —
they are the owner's data, typed once. The time on a bill stays 24-hour in both.
Dates do follow the language: weekday and month names come from the system. The
backup filename stays ASCII and unlocalised so it sorts and parses the same on
every phone.

Changing the language rebuilds the whole view tree (`RootView` keys on it) —
heavy-handed, and exactly right for something that happens once and must leave
nothing behind in the old language.

## One currency

The shop bills in exactly one, chosen in setup step 1 and changeable in
Settings. The app never converts, never holds a rate, and never shows two
currencies on one screen.

`Currency` is a small table — code, symbol, minor units — and **the ISO code is
what gets stored**, not the symbol. A wrong symbol is then a one-line fix here
rather than a migration out of everyone's saved settings.

- **The symbol carries its own spacing.** Alphabetic codes read as `SAR 194`, a
  glyph reads as `₹194`. That is how each is written, and putting the space in
  the symbol keeps `Money` free of a rule about which is which.
- **Minor units follow the currency.** Two almost everywhere; three for KWD, BHD
  and OMR. Rendering `0.125` as `0.13` in a shop that bills in fils is an error,
  not a rounding preference. Whole numbers still show no decimal point at all,
  as the handoff specifies.
- **Grouping does not follow the currency.** `en_US` for all of them, so the same
  number reads the same way whatever the shop bills in and whichever language it
  reads — an INR shop does not start seeing lakh grouping mid-bill.
- **Nothing is converted when it changes.** Amounts already saved keep their
  numbers and start being drawn with the new symbol. That is the only honest
  behaviour for an app holding no rate, and the Settings copy says so before the
  tap rather than after.
- **Import takes the file's currency**, unlike the language: those prices were
  entered in it. Settings written before the code existed carry `currencySymbol`
  instead, and are resolved back through the table.

The backup format still writes `currencySymbol` alongside the new
`currencyCode`, so a build from before currencies were selectable reads a
current file unchanged — no version bump, because nothing an old reader sees has
changed meaning.

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

### Focus, and the toolbar above the keyboard

A numeric keypad has no return key, so every screen with one carries a single
toolbar button. It is declared **once per screen and unconditionally** — an
early version gave each field its own conditional toolbar and setup step 3, with
three boxes per product, hung as focus moved between them.

On setup step 3 that button says **Next** until the last box, then **Done**. A
field cannot know what comes after it, so the screen holds one `FocusState` for
all of them and hands each field its tag through `NocturneField.focusTag`.
Fields that need nothing of the sort keep their own focus and pass neither.

That also fixes what the accent border was saying. Dismissing the keyboard
through the responder chain — which is what a toolbar button does — tells
SwiftUI's `FocusState` nothing, so a field went on claiming focus it no longer
had and its border stayed lit after the keyboard was gone. Clearing the screen's
focus does both jobs at once; for the screens that still dismiss through the
responder chain, `NocturneField` watches `keyboardDidHideNotification` and stands
itself down.

### The keyboard never moves the layout

`AppShell` declares `.ignoresSafeArea([.container, .keyboard], edges: .bottom)`,
and every screen presented outside it — setup, Settings, the backup handoff, the
bottom sheet — says the same for the keyboard. The keyboard **overlays** the app;
nothing slides up to make room for it.

This was arrived at the hard way. Letting the layout move produced a different
bug on every screen it touched: fields vanishing from a squeezed scroll view on
setup step 3, a footer floating up over content as it bounced, and finally the
tab bar itself travelling to the top of the display when a search field took
focus. Each was fixed locally and the next one appeared somewhere else, because
the cause was one rule, not several bugs.

The tab bar case is why the declaration belongs in `AppShell` rather than on each
screen: the bar lives above all four tabs, so protecting the tabs individually
left the thing between them unprotected.

The cost is that content can sit under the keyboard, so the scroll views that can
be searched use `.scrollDismissesKeyboard(.interactively)` — the way back to what
the keyboard covers is to push it down.

### Motion

`DesignSystem/Motion.swift` holds one rule: **motion carries information or it
does not happen.** A number that rolls says it changed while you were looking
somewhere else. A row that slides in says it was added rather than always having
been there. A screen that fades says which way you went. Anything that only
decorates is a delay between the owner and the next customer.

What moves, and why:

| Motion | Says |
| --- | --- |
| Cart total, balance and line totals roll | the number moved while your eye was on the stepper |
| The ✓ mark on an already-added product pops | the tap landed — the list itself does not move |
| Cart rows and product rows fade in and out | added or removed, not always there |
| The picker/cart swap and tab changes cross-fade | which way you went |
| The Bills list restacks on a filter change | the whole list was rewritten, not just scrolled |
| A tab icon dissolves outline → filled | same glyph, new state |
| The opened bill redraws on void | the tap changed the document under your thumb |

Everything goes through `.motion(_:value:)` rather than `.animation(_:value:)`,
which checks **Reduce Motion** in one place — the call site that forgets is the
one on the phone belonging to somebody who asked it not to move.

Numbers roll only where they change *while being read*. A saved bill's total is
drawn once and has nothing to say by moving.

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
- `Product` / `Bill` / `BillLine` / `Settings`, the repository seam, and
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
- **Bills** — full history newest-first, with the muted treatment on voided
  ones, and a customer filter. Tapping any bill on Bills or Today opens it as a
  document.
- **Customers** — derived from bills, never stored. Filtering Bills to one
  shows what they have bought and what they still owe.
- **The bill itself** — `BillTemplate`: letterhead, number, date, customer,
  every line with its arithmetic, total and what is owed. One view, used both
  by the confirmation after saving and by the sheet that opens from history, so
  the two cannot drift apart. Void lives inside the opened bill rather than on
  the list row — the app's one destructive action on history now costs a
  deliberate tap to reach, and the row goes back to being a row.
- **Settings** — owner name and counts, language and currency as two dropdowns
  in one card, and a single row through to the backup handoff whose subtitle
  carries the backup state. Export and import live one level in, on
  `BackupScreen`: real export to Files, Share via the OS share sheet, and a
  validated import gated behind a warning that names what will be lost. **Start over is `#if DEBUG` only**: one
  tap with no confirmation clears every product, price and bill, which is right
  for resetting to first-run during development and wrong to leave under
  Settings on the phone holding the only copy of the shop. The store's
  `startOver` rule is not conditional and stays tested in both configurations —
  it just has no button in a release build.
- **First-run setup** — all three steps: name, product names with the four
  suggestion capsules, then the stock-and-prices grid with its completeness gate.
- **English and Kannada**, with the switcher in Settings.
- **Currency selection** — fourteen currencies, picked in setup and changeable
  in Settings.
- **Product editor** and **Add stock** sheets, both complete.
- 60-odd tests over the domain layer.

## What is not built yet

Every screen in the handoff is built. What remains is verification rather than
construction:

- **Nothing asserts what a view renders**, and nothing renders them in CI. An
  automated screenshot walkthrough was built and then removed: it kept stalling
  in setup and, worse, saved the stalled screen under the name of the screen it
  never reached, which made a broken run look like a complete one. Visual checks
  are done by running the app on a Mac.
- **View bodies remain untested.** The import gating is now covered by
  `ImportFlowTests` after being lifted out of the view, but nothing asserts what
  any SwiftUI body renders — the screenshots are for looking at, not for failing
  a build.

**Not built, and not to be built without asking:** low-stock alerts, a customer
ledger, returns, bill editing beyond void, printable receipts, barcode scanning,
product photos, multi-user, VAT, reporting, and weighted-average costing. All
were considered and deliberately cut.

## Tests

`⌘U`, or:

```sh
xcodebuild test -scheme Stockbook -destination 'platform=iOS Simulator,name=iPhone 16'
```

They cover the rules where a plausible-looking wrong implementation is easy to
write: stock flooring at zero, part payments clamping to the total, history
surviving a product edit, voiding being idempotent, the owed banner counting
people rather than bills, suggestion ranking, cost being latest-paid, and the
backup round trip and its rejections.

## Getting it onto the phone

There is no Xcode for iOS, so the app cannot be built on the phone it runs on.
It does not have to be: `.github/workflows/testflight.yml` builds a signed
release on a rented Mac and uploads it to App Store Connect, and TestFlight
installs it. The loop from a phone with no Mac anywhere near it is **push →
wait → tap install**.

That workflow **never runs on a push to a branch** — macOS minutes bill at 10x
on a private repo and the `iOS` workflow already covers every push. Release by
pushing a `v*` tag, or by pressing *Run workflow*.

### One-time setup

All four steps are web pages, and all four work in mobile Safari.

1. **Register the bundle ID** at [developer.apple.com](https://developer.apple.com/account/resources/identifiers/list)
   → Identifiers → App IDs. The project declares `com.stockbook.app`; register
   that, or pick another and change `PRODUCT_BUNDLE_IDENTIFIER` in **both**
   `Stockbook.xcodeproj/project.pbxproj` and `project.yml`.
2. **Create the app record** in [App Store Connect](https://appstoreconnect.apple.com)
   → Apps → +. Nothing is published; TestFlight needs the record to exist.
3. **Create an API key**: App Store Connect → Users and Access → Integrations →
   App Store Connect API → +. Give it **App Manager** access. You get an Issuer
   ID and a Key ID on screen, and a `.p8` file that **can only be downloaded
   once** — download it before leaving the page.
4. **Add four repository secrets** (GitHub → Settings → Secrets and variables →
   Actions):

   | Secret | Where it comes from |
   | --- | --- |
   | `APPSTORE_ISSUER_ID` | the Issuer ID above the key list |
   | `APPSTORE_KEY_ID` | the key's own ID |
   | `APPSTORE_PRIVATE_KEY` | the whole `.p8` file's contents, `-----BEGIN` and `-----END` lines included |
   | `APPLE_TEAM_ID` | developer.apple.com → Membership details |

No certificates and no provisioning profiles go into secrets. `xcodebuild
-allowProvisioningUpdates`, handed the same API key, creates and fetches what it
needs on the runner and throws it away with the runner.

### After that

Every upload gets its **build number from the GitHub run number**, so it always
rises and never repeats — App Store Connect rejects a build number it has seen
before, and it means a build in TestFlight can be traced back to the run that
made it. Pass a marketing version to the workflow to change the `1.0` part.

Add yourself as an **internal tester** in App Store Connect. Internal builds skip
Beta App Review and are installable about 5–15 minutes after upload; external
testers wait on a review each time the version changes.

`ITSAppUsesNonExemptEncryption` is already `NO` in the project, so no build stops
to ask the export-compliance question. It is true: the app makes no network
calls at all.

## If the project file ever breaks

`Stockbook.xcodeproj` is hand-written. If it goes stale or conflicts badly:

```sh
brew install xcodegen && cd ios && xcodegen generate
```

`project.yml` reproduces the same two targets and settings. Keep the two in step.
