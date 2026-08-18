# Stockbook — native iOS

SwiftUI implementation of the design in
[`../project/design_handoff_stockbook/README.md`](../project/design_handoff_stockbook/README.md).
That file is the spec; this one describes how the code is arranged.

**Requirements:** Xcode 16+, iOS 17.0+, iPhone only, portrait.
Open `Stockbook.xcodeproj` and run — there are no dependencies, no package
resolution, no `pod install`.

> Every screen in the handoff is built, and this app is at parity with the
> Android one. What is left is verification rather than construction — see
> [What is not built yet](#what-is-not-built-yet).

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
correcting and restocking all live in `StockbookStore` — the layer the tests drive,
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
specified. Setup step 4 waits until a field has been visited and left empty —
otherwise four products means twelve accent outlines on arrival, which reads as
twelve errors before the owner has done anything. The footer's gate line carries
the message until then.

**The import state machine lives outside its view.** `ImportFlow` owns the
idle → picked → imported path, so the most destructive operation in the app can
be asserted without a simulator. Its one guarantee: a document only ever comes
out of `confirm()`, and only from `picked` — so a corrupt file, a cancel, or a
second tap cannot reach `replaceEverything`.

**Setup is a draft, not a partial shop.** Nothing entered during the four
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

A customer is **assembled, not stored whole.** `CustomerRecord` holds only the
facts somebody typed on purpose — spelling, phone, place, and the balance carried
over from the paper book. Everything else is derived from bills and payments each
time `StockbookStore.customers()` is asked, which at this size is free and means
the figures can never go stale or be orphaned from the history behind them.

The roster and history are **merged, not chosen between**: somebody entered during
setup who has never bought anything is a customer with no bills, and a name on a
bill that nobody ever added to the roster is a customer too.

**Identity is `Customer.key`: trimmed and lowercased.** `"ahmed "` and `"Ahmed"`
are one person. Everything that groups, matches or filters customers goes through
that one function — the debtors banner, the cart's picker, the Bills filter, and
every payment.

**The bill's customer is chosen from that roster, never typed.** Free text is how
"Ahmed", "ahmed " and "Ahmd" become three people with three balances, which
stopped being cosmetic once statements, payments and opening balances started
hanging off a customer. `Cart` carries the chosen `customerKey` beside the name
and typing clears it, so a name that was edited after being picked cannot be
saved against the account it no longer names. It still cannot block a sale: a name
matching nobody offers to become a customer in the same tap that picks them.

The **display** name is the roster's spelling where there is one, and otherwise
the spelling from the customer's most recent bill. Bills keep what was actually
typed on them; history does not move.

That split is what the next stored fact should follow too — a credit limit, a tax
number, a delivery address. The derived figures stay derived; only the typed-in
facts get stored, and callers are handed a `Customer` rather than a bare name
string so the two arrive together.

### Data model notes

The handoff's deliberate modelling decisions, and where they are enforced:

| Decision | Where |
| --- | --- |
| Everything sells by the piece — no units, no packs, no fractions | `Product` |
| Bill lines snapshot name and price; editing a product never rewrites history | `StockbookStore.saveBill`, tested in `StoreTests.historyIsImmutable` |
| Cost is *latest paid*, not a weighted average | `StockbookStore.restock(mode: .purchase)` |
| Stock changes in exactly seven places, and nowhere else | setup · product editor · **itemised** bill (floor 0) · editing or removing a bill (by the difference) · restock · delivery with a product on it, and editing or removing one · `setStock` after a count |
| A bill or supplier bill entered as a figure moves no stock at all | `Bill.isItemised`, `Purchase.isItemised`, pinned in `AmountFirstTests` |
| A mistake is edited or removed, never marked | `StockbookStore.updateBill` / `deleteBill`, and their delivery mirrors |

Products carry a `uid: UUID` and nothing else identifies them. A row id or an
object reference would be local to one store, and a bill line has to still point
at its product after the database has been carried to another phone in a file —
or after the storage engine underneath has been swapped.

### The backup file

`BackupDocument` is a real versioned format, not an implementation detail —
it is the *only* way data moves between devices.

```json
{ "version": 1, "exportedAt": "…", "ownerName": "…", "currencyCode": "SAR",
  "products":  [ { "uid": "…", "name": "…", "stock": 12, "cost": 60, "price": 95 } ],
  "bills":     [ { "number": 1, "createdAt": "…", "total": 190, "paid": 100,
                   "who": "…", "lines": [ … ] } ],
  "customers": [ { "key": "ahmed", "name": "Ahmed", "openingBalance": 0, "createdAt": "…" } ],
  "payments":  [ { "id": "…", "customerKey": "ahmed", "amount": 30, "receivedAt": "…" } ] }
```

Written as `stockbook-YYYY-MM-DD.json`. On the way back in, `BackupService.decode`
checks the **version before the shape** — a file from a future build may decode
cleanly into today's structs while meaning something different, and the result of
getting that wrong is a destructive whole-database replace. Import is a swap, not
a merge, and the UI gates it behind a warning naming what is about to be lost.

**The version is 1, and it has been 3.** It was bumped when payments arrived and
again for opening balances, each time correctly by the rule above — and then
collapsed back, because nothing has shipped and those numbers described files
that exist nowhere. Three shapes of imaginary history is a cost paid every time
the format is touched. The rule itself stands: once a shop exists on somebody's
phone, the next field that changes what an older reader *believes* makes this 2.

Every key is written every time, empty lists included, so a missing one means a
file this app did not write. That strictness is the backup file's alone — the
shop's own file on disk is read leniently, because that is what lets a field be
added without a migration.

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
- **Import takes the file's currency**, unlike the language and the theme: those
  prices were entered in it. The file names it by ISO code and nothing else — the
  symbol used to be written alongside, for a build that could not read a code,
  and went out with the format versions that build belonged to.

## The design system

`DesignSystem/` is the whole of the visual spec, and feature code never reaches
past it:

- **`Nocturne.swift`** — every colour token, and the one file allowed to contain
  a hex literal. It holds **two palettes behind one set of names**: a screen
  reads `Nocturne.surface` and gets the surface of whichever theme the owner
  chose in Settings. That is what stops a second theme from becoming a second
  design — a screen cannot forget to handle light, because it never asks which
  theme it is in. The light palette is not the dark one inverted: the accent
  darkens to stay legible on white, the ground and the card swap roles rather
  than values, and `accent300` — the loudest shade on dark — becomes the deepest
  on light, because the ramp means "more attention", not "more light".
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
early version gave each field its own conditional toolbar and setup step 4, with
three boxes per product, hung as focus moved between them.

On setup step 4 that button says **Next** until the last box, then **Done**. A
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
setup step 4, a footer floating up over content as it bounced, and finally the
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
| The opened bill redraws after an edit | the tap changed the document under your thumb |

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
both it and setup step 4.

## What is built

- Xcode project, both targets, shared scheme.
- Full design-token layer and component set.
- `Product` / `Bill` / `BillLine` / `Settings`, the repository seam, and
  `StockbookStore` with every rule the handoff specifies.
- `Cart` — line management, price override, payment mode, the save gate.
- The backup format, its validation, file export via `.fileExporter`, and the
  destructive-replace import path.
- **Today** — stats, the owed banner (counting distinct people, not bills), the
  payables banner beside it, recent bills, and a backup nudge that writes a real
  file.
- **Items** — search, rows with margin and stock colouring, empty states, and the
  supplier panel folded underneath: this is where stock comes from, and where
  somebody is standing when "who did we get these from, and have we paid them?"
  comes up.
- **Sell** — product picker and cart: per-line stepper with live stock, the
  editable price with its override treatment and Reset, the customer chosen from
  the roster through an upward scrolling picker that can also create one on the
  spot, full/part payment, and save.
- **The number on the paper, and the day it happened** — a bill and a delivery
  both carry the number written on the physical paper, and both can be entered
  for the day they actually happened rather than the day they were typed. On the
  sell screen the number is **suggested**: one past the last one the shop wrote,
  digits only ("A-0099" → "A-0100"), so the usual bill needs no typing and the run
  starts wherever the shop's own book starts. Typing a number that is already on
  another bill — or on another delivery, whoever it came from — names the clash
  and refuses the save until it is changed.

  **Required on both sides of the book.** A record with no number cannot be
  matched to the paper it came from, which is the whole reason for keeping the
  number — so neither screen saves without one, the field is marked while it is
  empty, and the button says which thing is missing. On a bill it costs no typing,
  since the box arrives prefilled; on a delivery it is copied off the invoice that
  came with the stock. A document being **corrected** is left out of its own
  duplicate check, and removing one frees its number again.

  `Bill.number` is untouched by all of this. It stays the app's own counter and
  the thing identity is built on; the typed number is a **label**, and conflating
  the two is how a duplicate would start overwriting history. The store records
  what it is told — the refusal is the screen's, because a screen can hand the
  number back to the person who typed it and a restored backup cannot.
- **Receipt** — the full-screen confirmation, including the faded rule.
- **Bills** — full history newest-first, and a customer filter. Tapping any bill on Bills or Today opens it as a
  document.
- **Customers** — a stored roster of the typed-in facts (name, phone, place)
  merged with the figures derived from history, keyed case-insensitively so
  `"ahmed "` and `"Ahmed"` are one person. Entered during setup's optional fourth
  step or added from the Bills filter, and never required: a name typed at the
  counter is a customer too. Filtering Bills to one shows what they have bought
  and what they still owe.
- **Payments received after the bill** — their own records, attached to a
  customer rather than to an invoice, because that is how a counter settles.
  They come off what is owed, so the Bills filter and the Today banner stop
  claiming money already in the till. A mistyped one is deleted from the
  statement in two taps; unlike a bill there is nothing to correct, since a
  payment is one number and one date.
- **Suppliers and purchases** — the customer half of the book, pointing the other
  way. A delivery is entered from the add-stock sheet against a supplier **chosen
  from the roster**, never a typed name, and it puts the stock on the shelf, takes
  the new buying price and lands what is still owed on that supplier's account.
  One product per purchase, deliberately: the screen it is entered from puts one
  product back on the shelf, and five lines entered as five purchases are five
  true records rather than one convenient fiction.

  A wrong delivery is **edited or removed**, and either takes the stock back off
  the shelf — floored at zero, since it may already have been sold. Money paid
  to a supplier is its own record, exactly as a customer's payment is, and the same
  two sheets serve both directions: `PartyEditorSheet` and `PaymentSheet`, each
  with two entry points. What a payment *is* does not change with its direction.
- **Statements** — a full-screen document per customer or supplier: quick chips for this
  month, last month and this year plus a date range, the balance brought forward
  from everything earlier, every bill and payment in date order with a running
  balance beside each line, and the period's totals. Shareable as plain text.
  `Statement.make` is a pure function of bills and payments, so the arithmetic is
  checked against literal figures rather than by reading a screen.
- **The bill itself** — `BillTemplate`: letterhead, number, date, customer,
  every line with its arithmetic, total and what is owed. One view, used both
  by the confirmation after saving and by the sheet that opens from history, so
  the two cannot drift apart. Edit and Remove live inside the opened bill rather
  than on the list row — the app's actions on saved history cost a deliberate tap
  to reach, and the row goes back to being a row.

  **Shareable as plain text**, from the confirmation and from history alike —
  `BillText.plainText`, a pure function of the bill, checked against literal
  strings rather than by reading a screen. Plain text rather than a PDF because
  it lands in WhatsApp, which is where a customer here actually reads it; a PDF
  would look more like a document and be worse at being one.
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
- **First-run setup** — all four steps: name and currency, product names with
  the four suggestion capsules, the customer roster with opening balances, then
  the stock-and-prices grid with its completeness gate.
- **English and Kannada**, with the switcher in Settings.
- **Dark and light**, chosen in Settings and stored with the shop. Two themes,
  no "System": following the phone would hand the decision to whoever set the
  phone up, who is not always the person behind the counter.
- **Currency selection** — fourteen currencies, picked in setup and changeable
  in Settings.
- **Product editor** and **Add stock** sheets, both complete.
- Around 200 tests over the domain layer.

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

- **Payments are not allocated to particular bills.** They land against the
  customer's account, which is how somebody paying what they can against what
  they owe actually behaves — but it means "which invoices has Ahmed cleared" is
  not answerable. Allocation would be a fiction the owner then has to maintain.

**Not built, and not to be built without asking:** low-stock alerts, returns,
printable receipts,
barcode scanning, product photos, multi-user, VAT, reporting, profit reporting,
multi-line delivery notes, and weighted-average costing — `Product.cost` stays
"latest paid". All were considered and deliberately cut. *(A customer ledger was
on this list and has since been built, and so has its supplier mirror — see
Customers, Payments, Suppliers and Statements above. Reading a paper bill with
the camera was built and then removed; the diagnosis is in the `Remove bill
scanning` commit.)*

## Tests

`⌘U`, or:

```sh
xcodebuild test -scheme Stockbook -destination 'platform=iOS Simulator,name=iPhone 16'
```

They cover the rules where a plausible-looking wrong implementation is easy to
write: stock flooring at zero, part payments clamping to the total, history
surviving a product edit, an edit moving the shelf by the difference, the owed banner counting
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
