# Handoff: Stockbook — offline inventory & billing app for a hardware shop

## Overview

Stockbook is a **single-user, fully offline** mobile app for a small hardware/lock
shop owner in Saudi Arabia. It tracks products, stock counts, buying and selling
prices, and daily bills. There is no account, no server, no sync: all data lives
on the device, and the only way data moves between devices is a file the owner
exports and imports by hand.

The app is designed for one person — the business owner — standing behind a
counter, one-handed, often while a customer waits. Speed of billing and honesty
about stock are the two things that matter.

**Core loop:** first-run setup (owner name → product names → stock & prices) →
daily billing (search or browse products, quantity, override price, customer
name, full or part payment, save) → stock decrements automatically → restock via
quick add or a supplier purchase entry.

---

## About the design files

The files in this bundle are **design references authored in HTML** — an
interactive prototype that shows the intended look, copy, and behaviour. They are
**not production code to copy**. The prototype uses a bespoke HTML templating
runtime that is irrelevant to your implementation.

Your task is to **recreate these designs in the target codebase's own
environment**, using its established patterns, component library, navigation and
state conventions. If there is no codebase yet, pick the framework that fits an
offline-first mobile app and build there — recommended: **React Native + Expo
with SQLite (expo-sqlite) or WatermelonDB**, or **Flutter + Drift**. Anything
that persists locally and works with the radio off is acceptable; a web app with
a service worker + IndexedDB also works, but native file export/import is
smoother.

**Non-negotiable product constraints:**
1. No network calls, ever. No analytics, no crash reporting that phones home, no
   remote config. The app must be fully functional in airplane mode on first
   launch.
2. All persistence is local. A dropped phone loses everything unless the owner
   exported a file — the UI must keep reminding them of that.
3. Single user. No login, no roles, no multi-tenant anything.

## Fidelity

**High fidelity.** Colors, type sizes, spacing, radii, copy and interaction
states are all final and should be matched closely. Every color below is a token
value from the "Nocturne" design system (`styles.css` is included in this
bundle). Recreate the UI to match, using your codebase's own primitives where
they exist.

---

## Design tokens

### Color

| Token | Value | Used for |
| --- | --- | --- |
| `--color-bg` | `#161826` | App background, sheet field backgrounds |
| `--color-surface` | `#232532` | Cards, rows, tab bar, bottom sheets |
| `--color-text` | `#e9e9ed` | Primary text |
| `--color-accent` | `#9184d9` | Primary action outlines, active tab, focus ring, required-field outline |
| `--color-accent-300` | `#d2cefd` | Edited-price value, warning text on tinted ground |
| `--color-accent-400` | `#b5abfc` | Money owed, low/zero stock, secondary accent text |
| `--color-accent-700` | `#5d5294` | Selling-price input border (marks the "money out" field) |
| `--color-accent-900` | `#2b2741` | Gradient start on the "Sold today" stat card |
| `--color-neutral-400` | `#b2b6ca` | Body copy on dark surfaces |
| `--color-neutral-500` | `#9397ab` | Meta text, labels, inactive tab labels, muted icons |
| `--color-neutral-600` | `#75798c` | Empty-state icons only |
| `--color-neutral-800` | `#3f424d` | Borders, dividers, dashed empty-state outlines |
| `--color-divider` | `rgba(233,233,237,.16)` | Hairlines |

Rules that matter: **nothing is filled with the accent** — primary buttons are a
1px accent border on transparent. No pure black or pure white. Elevation on this
dark ground is a hairline edge plus ambient shadow, never a stack of shadows.
Text at 11–13px must use `--color-neutral-500` or lighter (contrast); reserve
`--color-neutral-600` for icons.

### Type

Inter throughout. Weight 400 body, 500 headings — **never bolder than 500**;
hierarchy is size and space.

| Role | Size | Notes |
| --- | --- | --- |
| Screen title | 25px | letter-spacing −0.02em |
| Setup step title | 26px | letter-spacing −0.02em |
| Big number (stat, total) | 26–28px | letter-spacing −0.025em |
| Sheet title | 19px | |
| Row primary | 14.5px | |
| Row / input value | 14px | |
| Body copy | 13px, line-height 1.5 | `--color-neutral-500` |
| Meta / sub-row | 11.5px | `--color-neutral-500` |
| Field label | 12px | 70% opacity text |
| Section kicker | 10.5px, uppercase, letter-spacing 0.09em | `--color-neutral-500` |
| Tab label | 10.5px | |

### Spacing, radius, elevation

- Screen horizontal padding **20px**. Header top padding **58px** (below status bar).
- Gaps: 5–6px between list rows, 8–10px between cards, 10px between form fields.
- Radius: rows/cards **9–10px**, buttons/inputs **8px**, stat cards **12px**,
  bottom sheets **18px top corners only**, pills **6–7px**.
- Bottom sheet shadow: `0 -16px 40px rgba(0,0,0,0.65)`; sheet scrim
  `rgba(16,17,28,0.74)`.
- Card hairline: `box-shadow: 0 0 0 1px var(--color-neutral-800)`.
- **Minimum touch target 44px.** Inputs are 42–46px tall, primary buttons 46–48px.

### Icons

Phosphor Icons (regular weight; filled only for the active tab and the backup
"shield-check"). Icons used: `gear-six`, `plus`, `plus-circle`, `house`,
`shapes`, `receipt`, `trash`, `minus`, `pencil-simple`, `user`,
`list-magnifying-glass`, `folder-open`, `export`, `tray-arrow-down`, `file-text`,
`share-network`, `check`, `x`, `arrow-left`, `stack-plus`, `money`, `scissors`,
`hand-coins`, `shield-warning`, `shield-check`.

### Currency

`SAR ` prefix, space after. Integers render without decimals
(`SAR 194`), non-integers to exactly 2 (`SAR 0.25`). Thousands separator
`en-US`. Make the symbol a single configurable constant.

---

## Data model

```
Product {
  id            int, primary key
  name          string, required, unique-ish (case-insensitive dedupe on entry)
  stock         int   — pieces on the shelf, required
  cost          number — latest buying price per piece, required
  price         number — selling price per piece, required, must be > 0
}

Bill {
  id            int, primary key (display as "Bill #<id>")
  lines         BillLine[]
  total         number — sum of line qty * line price, stored (not recomputed
                         from current product prices)
  paid          number | null — null means paid in full; a number means part paid
  who           string, required — customer name, trimmed
  time          "HH:MM"
  createdAt     timestamp
  voided        boolean
}

BillLine {
  productId     int
  name          string — snapshot of product name at sale time
  qty           int, min 1
  price         number — snapshot of the price actually charged
}

Settings {
  ownerName     string
  currencySymbol string  default "SAR "
  lowStockAt    int      default 40
  lastExportAt  date | null
}
```

**Deliberate modelling decisions:**

- **Everything sells by the piece.** No units, no packs, no fractional
  quantities. One product = one stock count = one price. (An earlier design had
  pack/loose variants; it was cut.)
- **Line items snapshot name and price.** Editing a product later must never
  rewrite history.
- **Cost is "latest paid", not weighted average.** A purchase entry overwrites
  `cost`. Profit shown is simply `price − cost`; there is no inventory
  valuation layer. Keep it that way unless asked.
- **Stock is only ever changed by:** setup, manual edit in the product editor,
  saving a bill (decrement, floored at 0), voiding a bill (increment back), and
  restock (increment).

---

## Screens

### 0. First-run setup — step 1 of 3: owner name

Shown on first launch only. Three-segment progress bar at top (20px side
padding, 3px tall, 6px gaps; filled segments `--color-accent`, empty
`--color-neutral-800`).

- 38×38 rounded-10 tile, 1px accent border, accent `shapes` glyph.
- H "Welcome to Stockbook" (26px).
- Body: "Everything stays on this phone — no account, no signal needed. First,
  what should we call you?"
- Field, label "Your name", placeholder "Business owner name", 46px tall. While
  empty its border is `--color-accent` (marking it required); once filled it
  returns to `--color-neutral-800`.
- Footer: full-width primary "Continue", **disabled until a non-empty trimmed
  name exists**. Enter key advances.

### 1. Setup — step 2 of 3: product names

- Kicker "HELLO, <FIRSTNAME>" in accent, uppercase, letter-spacing 0.09em.
- H "What do you stock?" · body "Names only for now. Prices and counts come
  next, and you can add or remove items any time after."
- Row: text input (placeholder "e.g. 4 inch hinge", 46px) + 46px square accent
  `+` button. Enter also adds.
- Kicker "COMMON HARDWARE LINES", then suggestion capsules — 1px accent border,
  transparent, 11.5px, `+ ` prefix, 30px min height, 6px gaps, wrapping. The set
  is exactly: **Lever Handle Lock, Cisa lock, Padlock, Deadbolt**. Tapping one
  adds it and removes it from the suggestion row.
- Kicker "ADDED · N" (or "NOTHING ADDED YET"), then added rows — surface, 8px
  radius, name + muted `x` to remove.
- Footer: 56px secondary back button + primary "Next — stock & prices",
  disabled while the list is empty.
- Duplicate names (case-insensitive) are silently ignored.

### 2. Setup — step 3 of 3: stock and prices

- H "Stock and prices" · body "All three are needed for every item — the count
  on the shelf, what you paid, what you charge."
- One card per product: name (14.5px), then a **3-column grid, 8px gap**:
  "In stock" · "You pay" · "You sell". All numeric, 42px tall. The selling-price
  input carries the `--color-accent-700` border; empty required fields carry the
  `--color-accent` border.
- Footer, above the buttons: a centred 11.5px gate line —
  "N items still need stock, buying and selling price." → when complete,
  "All set — stock and both prices filled in." in `--color-accent-400`.
- Back button + primary "Open the shop", **disabled until every item has a
  stock value, a cost value, and a selling price greater than zero.**

### 3. Today (home)

- Header: kicker with the date ("TUESDAY, 11 AUGUST"), title
  **"Hello, <FirstName>"**, and a 36px secondary icon button (`gear-six`)
  opening Settings.
- Two stat cards, 2-col grid, 9px gap, radius 12, 14px padding, hairline:
  - "Sold today" — value 26px. Background
    `linear-gradient(155deg, var(--color-accent-900), var(--color-surface))`.
  - "Bills" — count of non-voided bills. Flat surface.
- **Owed banner** (only when someone owes): surface, `border-left: 2px solid
  var(--color-accent)`, radius `0 10px 10px 0`, `hand-coins` icon, text
  "<Name> still owes" for one customer / "N customers still owe" for several —
  **count distinct customer names, not bills** — and the summed balance in
  `--color-accent-400`.
- "RECENT BILLS" kicker with an "All" ghost link → Bills. Up to 3 bill rows.
  Empty state: dashed `--color-neutral-800` box, "No bills yet today." + primary
  "＋ Start a bill".
- **Backup nudge**, always present: dashed box, shield icon, and
  "Nothing backed up yet. Everything lives on this phone only." → after export,
  filled `shield-check` in accent and "Backup written. Copy it somewhere safe —
  everything lives on this phone only." Secondary "Save file" button performs
  the export.
- Bottom tab bar.

### 4. Items

- Header title "Items", sub-line "N products · M running low" (or "nothing added
  yet"), primary "＋ Add" button opening the product editor in create mode.
- Search input (42px), filtering by case-insensitive substring.
- Rows: name (14.5px) / sub "buy SAR X · you make SAR Y" (11.5px muted); right
  side selling price (15px) over stock (11.5px). Stock color:
  `--color-accent-400` when 0 ("out of stock"), `--color-accent-400` when
  ≤ `lowStockAt`, otherwise `--color-neutral-500`.
- Tapping a row opens the product editor. Empty state: dashed box with `shapes`
  icon, "Nothing on the shelf yet. Add your first product." (or "Nothing
  matches “<query>”." when searching) + primary "Add a product".

### 5. New bill — product picker

Shown when the cart is empty, when the search box has text, or when the user
taps "Add another item".

- Header "New bill" with a muted right-side count ("empty" / "N lines").
- Search input "Add a product…".
- Hint line: "All N products — tap to add" or "Matching “<query>”".
- Rows: name / stock (11.5px muted) / price in `--color-accent-400`. Tapping
  adds one piece to the cart at the product's current selling price; tapping an
  item already in the cart increments it.
- If the cart is non-empty, a sticky footer shows "N lines" + running total
  (19px) and a primary "Done adding" that returns to the cart.
- If the cart is empty, the tab bar shows instead.

### 6. New bill — cart *(the most important screen)*

- Header "New bill", "N lines".
- Empty search input at top (tapping it re-opens the picker).
- **Cart line card** (surface, radius 10, 11–12px padding):
  - Top row: name (14.5px) · line total (15px) · muted `trash`.
  - Bottom row: a stepper (`−` 34px / qty input 44px centred / `+` 34px, all
    inside a 34px-tall `--color-bg` box with a `--color-neutral-800` border) ·
    "pieces · N in stock" (11.5px muted, becomes **"only N in stock"** when the
    quantity exceeds stock) · then, right-aligned, the **price field**: 34px box,
    `SAR` prefix in muted, right-aligned numeric input.
  - **The price is prefilled from the product's selling price and is editable.**
    When the value differs from the prefill, the price box border becomes
    `--color-accent`, the value becomes `--color-accent-300`, and a line appears
    below: `✎ Usual price SAR 145 — changed for this bill only` with a "Reset"
    ghost button. **The product's own price is never modified by this.**
  - Quantity minimum 1; `trash` removes the line.
- "⌕ Add another item" secondary button under the last line.
- **Sticky footer** (surface, `0 -1px 0 var(--color-neutral-800)`):
  1. **Customer name input** (40px, `--color-bg` background). **Required.**
     Border is `--color-accent` while empty. On focus, a suggestion dropdown
     opens *above* the field: surface card, 1px border, radius 8,
     `0 6px 18px rgba(0,0,0,.55)`, rows of `user` icon + name + right-aligned
     meta. Suggestions are the distinct customer names from non-voided bills,
     **sorted by outstanding balance descending, then bill count descending**,
     max 4, filtered by what's typed, excluding an exact match. Meta reads
     "owes SAR 40" when they owe, otherwise "3 bills". Pick on `mousedown` (so
     blur doesn't cancel it).
  2. Two payment buttons, "Paid in full" / "Part payment" — the selected one
     takes the accent border and accent text, the other neutral.
  3. When part payment is selected: a "Paid now" numeric field.
  4. "Total" label + total (28px). When part: a "Balance" line with
     `total − paid` in `--color-accent-400`.
  5. Primary full-width save button. Label is **"Enter a customer name"** and
     disabled while the name is empty; otherwise "Save bill".

**Saving a bill:** snapshot each line (name + price charged), decrement each
product's stock by the quantity (floor 0), store `paid` (clamped to the total,
`null` when paid in full) and the trimmed customer name, prepend to the bill
list, clear the cart and payment fields, and show the receipt.

### 7. Receipt (full-screen overlay)

- Circled accent `check` (36px, pops in), "Bill saved",
  meta "Bill #1 · 09:41 · Ahmed Contracting".
- Surface card, radius 12: one row per line — name · "2 × SAR 32" (muted) ·
  line total. Then a **rule that fades to transparent at both ends**
  (`linear-gradient(to right, transparent, #3f424d 24px, #3f424d calc(100% − 24px), transparent)`,
  1px) — this fade is a design-system signature, keep it.
- "Total" + 25px amount, then a `--color-accent-400` line: "Paid in full, cash."
  or "Paid SAR 100 · Ahmed Contracting owes SAR 94".
- Two buttons: secondary "See bills", primary "Next customer" (returns to an
  empty new bill).

### 8. Bills

- Title "Bills". Rows: joined line names (single line, ellipsised) / meta
  "<Name> · 09:41 · 2 items · owes SAR 94", right-aligned total.
  Voided bills prefix the meta with "voided · ", drop the name and total to
  muted, and lose the void action.
- Each live bill has a small ghost "Void & put stock back" action, which
  restores every line's quantity to product stock and marks the bill voided
  (bills are never deleted).
- Empty state: dashed box, `receipt` icon, "Nothing sold yet. Every bill you
  save shows up here." + primary "Start a bill".

### 9. Product editor (bottom sheet)

Scrim + sheet with a 38×4 grab handle, title "New product" / "Edit product" and
a muted `x`.

- "Product name" (44px, 15px text).
- 2-col grid: "In stock" · "Buying price".
- "Selling price" full width, `--color-accent-700` border.
- Note line: "You make SAR 30 a piece." or, if the selling price isn't above
  cost, "Set a selling price above the buying price."
- Buttons: secondary "Add stock" (existing products only) + primary "Save",
  **disabled unless name, stock, cost are all filled and price > 0**.
- Existing products also get a ghost "Remove this product", which deletes the
  product and drops it from the cart.

### 10. Add stock (bottom sheet)

- Title "Add stock", sub "<Product> — N pieces on the shelf now".
- Two mode pills (34px): **"Quick add"** / **"Purchase entry"**.
- Purchase entry only: "Supplier" field (placeholder "Who delivered it").
- "How many" always; "Paid per piece" only in purchase mode.
- Note line:
  - Quick add → "Topping up the bin. Buying price stays at SAR 18."
  - Purchase entry → "Bill total SAR 850. This becomes the buying price used
    from now on."
- Primary action label: "Add 50 to stock" / "Record purchase".
- Effect: `stock += qty`; in purchase mode with a cost > 0, `cost = newCost`.
  Zero or empty quantity just closes the sheet.

### 11. Settings

Reached from the Today gear. Header "Settings" with a ghost "Done".

- **THIS PHONE** — card with an editable "Business owner" field, then three
  inline stats: Products / Bills / Customers.
- **MOVE TO ANOTHER PHONE** — body: "Stockbook never uploads anything, so a new
  phone gets your shop from a file you carry across. Export here, then import on
  the other phone."
  - **Export card** (`export` icon, "Export everything"): note "Writes one file
    with every product, price, stock count and bill." → after exporting, a file
    chip (`file-text` icon, `stockbook-2026-08-11.json`, "8 products · 4 bills ·
    2 KB") and the note becomes "Written to Files. Send it to the other phone
    however you like — AirDrop, WhatsApp, a memory card." Buttons: primary
    "Create backup file" / "Write a fresh file", plus a "Share" button that
    turns accent with a `check` and "Sending" and switches the note to "Ready to
    send — pick AirDrop, WhatsApp or Files in the share sheet."
  - **Import card** (`tray-arrow-down`): secondary "Choose a file" → shows the
    picked file in an accent-bordered box with its summary ("Khalid Al-Amri ·
    8 products · 4 bills · saved 28 July 2026") and a
    `--color-accent-300` warning "This replaces the 1 product and 0 bills
    already on this phone. It cannot be undone." Then "Cancel" / "Replace
    everything". After importing, the note reads "Imported. Everything from that
    file is now on this phone."
- **START AGAIN** — body "Clears every product, price and bill on this phone and
  runs setup from the beginning." + secondary "Start over", which wipes all state
  and returns to setup step 1.

**Real implementation of export/import** (the prototype fakes the file layer):
export the whole database as one JSON document —
`{version, exportedAt, ownerName, currencySymbol, products, bills}` — write it
to a dated filename `stockbook-YYYY-MM-DD.json` and hand it to the OS share
sheet / document picker. Import must **validate the version and shape, and
require explicit confirmation before replacing** — it is a destructive
whole-database swap, not a merge. Set `lastExportAt` on export so the Today
nudge can go quiet.

### Tab bar

Four tabs, fixed at the bottom: **Today** (`house`) · **Items** (`shapes`) ·
**Sell** (`plus-circle`) · **Bills** (`receipt`). Surface background,
`0 -1px 0 var(--color-neutral-800)` top hairline, 6px top / 24px bottom padding
(home-indicator inset). Active tab: filled icon + `--color-accent`; inactive:
regular icon + `--color-neutral-500`. Icon 22px over a 10.5px label. Settings is
not a tab.

---

## Interactions & behaviour

- **Validation is expressed as disabled primary actions with explanatory
  labels**, never as error toasts or red text. Required-but-empty inputs carry
  an accent border. The three gates: setup step 3 completeness, product editor
  completeness, and a customer name on every bill.
- **Focus.** `:focus-visible` is a 2px `--color-accent` outline with 2px offset.
  Never leave the platform default focus ring.
- **Pressed / hover states** come from the accent ramp: outlined buttons take a
  12% accent tint on hover and 22% when pressed; secondary buttons a 7%/14%
  text tint. Disabled controls drop to 45% opacity.
- **Bottom sheets** slide up from the bottom over a `rgba(16,17,28,0.74)` scrim;
  tapping the scrim dismisses. ~250–300ms, ease-out.
- **Transitions** are short: 150–250ms, ease-out for entrances. The receipt's
  check mark pops (scale overshoot ~1.25 then settle).
- No loading states, no error states, no empty network states — nothing is
  fetched.
- **No destructive action is irreversible without confirmation**, except product
  deletion (low stakes) — bills are voided rather than deleted, and import is
  gated by an explicit warning.

## State

Screen router: `setup0 | setup1 | setup2 | home | items | sell | bills |
settings`, plus three overlays that can sit above any screen: product editor
sheet, add-stock sheet, receipt.

Persisted: products, bills, ownerName, currencySymbol, lowStockAt, lastExportAt,
and whether setup has been completed. Transient: the cart, the current search
strings, sheet drafts, payment mode and paid amount.

## Assets

None. All iconography is Phosphor Icons; all type is Inter. No images, no logo
file — the wordmark is the word "Stockbook" beside a `shapes` glyph in a
rounded, accent-outlined tile.

## Files in this bundle

- `Stockbook.dc.html` — the interactive prototype (all screens and behaviour).
  Open it in a browser; the phone on the right is fully clickable.
- `styles.css` — the Nocturne design-system stylesheet: all tokens as CSS
  custom properties plus the component classes the prototype uses.
- `nocturne-readme.md` — the design system's own guidance (direction, color,
  type, do/don't).

## Suggested build order

1. Data layer + local persistence, and the export/import file format.
2. Product CRUD and the Items screen.
3. Billing: picker → cart → price override → customer → payment → save →
   stock decrement → receipt.
4. Today, Bills, void.
5. Restock (both modes).
6. First-run setup and Settings.

## Known gaps the owner has not asked for (do not build without asking)

Low-stock alerts, a customer ledger screen, returns and bill editing beyond
void, printable receipts, barcode scanning, product photos, multi-user, VAT
handling, month/year reporting, and weighted-average costing were all considered
and deliberately left out.
