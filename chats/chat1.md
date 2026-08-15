# Business inventory management app

_Started 2026-08-11 09:20 UTC_

---

## User

Design a mobile app for business owner to handle products price, stocks inventory, etc.

1. The app works completely offline! only for business owner ,
2. As a initial setup user should be able to add all the products he sell/buy , also there should be a way to add/remove the product after setup in future..
3. once products lists are added, user can setup the stocks available (count), buying price, and selling price.. in future he can edit the selling price as well!!
4. by default when user enter a bill or sold product info it will prefil the selling price but he should be able to edit it before saving it!!
5. suggest a nice name for the App.


and also if you think there are some gaps in what i mentioned let me know..

## Assistant



## User

Answered — biz: hardware items; scale: 50–300; units: By piece, Packs & loose of same item; payment: Cash paid in full, Partial payment; restock: Both — quick add, plus purchase entries when needed; cost: Keep it simple — just latest, no profit math beyond that; features: Export/backup to file; platform: iPhone; depth: Full clickable prototype of the core flow (setup → stock → bill); name: Plain & functional (Stockbook, Shopdesk)

## Assistant

Gaps I spotted before I build:

- **Selling to whom** — is there a customer/party record on a bill, or is it anonymous walk-up sales?
- **Units** — do you sell by piece only, or also kg/litre/metre (and fractional quantities)?
- **Payments** — cash vs credit ("pay later")? If credit exists, you need a ledger of who owes what.
- **Purchases/restock** — you mention buying price but not a restock flow. Stock has to go *up* somewhere, and buying price changes over time (which affects profit math).
- **Returns, damage, corrections** — a bill entered wrong needs an edit/void path or stock drifts from reality forever.
- **Bill identity** — do you need printable/shareable receipts, or is this just a private sales log?
- **Backup** — fully offline means one dropped phone = everything gone. At minimum an export-to-file.
- **Low-stock alerts** and a simple day/month summary (sales, profit) — cheap to add, high value.

Questions so I build the right thing:

_[tool: ask_user]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: copy_starter_component]_

_[tool: grep]_

_[tool: read_file]_

Building it now — going with **Daybook** as the name (the shopkeeper's book of daily entries).

_[tool: dc_write]_

Got it — **Stockbook**, hardware shop, iPhone. Rewriting with packs/loose, partial payment, and both restock modes.

_[tool: dc_write]_

_[tool: copy_files]_

_[tool: ready_for_verification]_

**Stockbook** — hardware-shop inventory, iPhone, fully offline. The prototype is clickable end to end: first-run setup → stock & prices → billing → restock.

Two things I built beyond the brief because your answers implied them: **packs and loose are one product** (a box of 100 screws and a single screw share stock, counted in pieces), and **part payment** writes the balance and a name onto the bill.

Two I'd still argue for, and left a hook for: **voiding a bill** (currently a quiet link on Bills — without it one mistyped bill puts stock permanently out of step) and a **low-stock line** on Today. Say the word and I'll remove or expand either.

## User

The price currency should be by default SAR

## Assistant

_[tool: dc_set_props]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

Rescaling the sample prices to sensible SAR figures too.

_[tool: dc_js_str_replace]_

_[tool: ready_for_verification]_

Currency is now SAR throughout, and I rescaled the sample prices to realistic Saudi hardware figures (screws at 0.25, a box of 100 at 20). Still editable in Tweaks if you want a different symbol.

## User

lets remove the packs selling!! keep only piece selling

## Assistant

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: grep]_

_[tool: read_file]_

_[tool: dc_html_str_replace]_

Now the copy and data.

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

Now the logic: strip pack from the data model.

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

Now the renderVals side.

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: ready_for_verification]_

Packs are gone — one product, one stock count, one price. The freed space in the cart row now shows live stock next to the quantity ("pieces · 620 in stock", and a warning if you exceed it), and the Items list shows your margin per piece instead of pack info.

## User

Could you please remove the default recent bills and items from it!! so that only what it is added by user is visble

## Assistant

_[tool: dc_js_str_replace]_

Now empty states for each screen.

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: ready_for_verification]_

All sample products and bills removed — the app now starts genuinely empty. I added proper empty states so the screens don't look broken: Items and Bills each get a prompt with an action button, and the Sell search tells you when nothing matches.

The setup screen's suggestion chips are still there (they only fill the input, nothing is pre-added) — say if you want those gone too.

## User

?

## Assistant

_[tool: show_html]_

_[tool: show_html]_

_[tool: eval_js]_

The phone was wrapping below the notes on narrower screens — tightening the layout so it stays beside them.

_[tool: dc_html_str_replace]_

_[tool: show_html]_

The phone is now beside the notes instead of dropping below on narrow screens.

If your "?" meant something else — a broken screen, unclear copy, or a question about how something works — tell me which and I'll fix it.

## User

I can still see all the  bills and items by default after setup!!

## Assistant

_[tool: grep]_

The file is empty of sample data — your preview is still holding the state it had before the change (the prototype keeps its data in memory between edits). A reload clears it.

I'll also make **Replay setup** a genuine fresh start so you can reset from inside the app.

_[tool: dc_js_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: ready_for_verification]_

I reopened it, which reloads fresh — you should see empty Items and Bills now. **Replay setup** on the Today screen is now **Start over** and wipes products, bills and prices, so you can always get back to a blank shop.

## User

Found issues — fixing…

## Assistant

_[tool: run_script]_

_[tool: dc_html_str_replace]_

_[tool: ready_for_verification]_

Fixed: every color now comes from a Nocturne token (no hard-coded hexes left), the small meta text and inactive tab labels moved up a ramp step to clear 4.5:1, and the stray "×" in the cart row is gone.

## User

when enter a bill there should be a way to add the customer name as well!! and in future when we add the customer name it can suggest the existing customers name

## Assistant

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

Now surface the name on the bill rows and receipt.

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: ready_for_verification]_

Customer name is now a field on every bill, not just part payments. Focus it and Stockbook suggests names you've billed before — sorted so anyone who still owes you money comes first, with their balance shown next to the name. The name then appears on the receipt and in the bill list.

## User

1. in setup user must enter in stock, buy and sell price , currently i can add without entering those!

2. customer name in bill additon should be mandatory

3. when adding a bill, i selected a product then i have only option to save it!! inorder to add new product to bill i have to search!! what if i dont remember the name of the product i added? show all products option ther?

## Assistant

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

Now the customer requirement and save gating.

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

Now setup step 2 completeness.

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

Now the logic for all three.

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: ready_for_verification]_

All three done:

1. **Setup gates properly.** Stock, buying and selling price are all required — empty fields get an accent outline, a line above the button counts what's still missing, and *Open the shop* stays disabled until it's clean. Same rule applies to adding a product later.
2. **Customer name is mandatory.** The Save button reads "Enter a customer name" and stays disabled until one is there.
3. **Browse all products.** *Add another item* under the cart opens the full list without typing — tap as many as you like, a bar at the bottom shows the running total, then *Done adding* returns to the bill.

## User

in capsule suggestion in setup product name , show the following
Lever Handle Lock
Cisa lock
Padlock
Deadbolt

## Assistant

_[tool: dc_js_str_replace]_

## User

in capsule suggestion in setup product name , show the only the following:

Lever Handle Lock
Cisa lock
Padlock
Deadbolt

## Assistant

That's the exact list now — those four and nothing else.

_[tool: ready_for_verification]_

The suggestion capsules now show only Lever Handle Lock, Cisa lock, Padlock and Deadbolt.

## User

In setup , ask for the users name as first setup! and then show that name with greeting on Dashboard

## Assistant

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

Now the Today header greeting.

_[tool: dc_html_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: ready_for_verification]_

Setup now starts with a welcome step asking your name (required, three-step progress bar, back arrow to return). The dashboard heading greets you by first name — "Hello, Ahmed" — with the date still above it, and the products step carries the same greeting as a small kicker.

## User

in placeholder of the name textfield show as : Business owner name instead of Shop owner name

## Assistant

_[tool: dc_html_str_replace]_

Done.

## User

now create a new video of the app showcasing all the features end to end!!

## Assistant

_[tool: read_skill_prompt]_

_[tool: copy_starter_component]_

_[tool: copy_starter_component]_

Now building the video. Story: first-run setup → billing with price override → customer & part payment → stock → restock → offline backup.

_[tool: write_file]_

_[tool: dc_write]_

_[tool: show_html]_

_[tool: get_webview_logs]_

_[tool: eval_js]_

_[tool: multi_screenshot]_

_[tool: eval_js]_

_[tool: grep]_

Found it — `interpolate` returns a function; my motion helpers were producing NaN. Fixing.

_[tool: str_replace_edit]_

_[tool: show_html]_

_[tool: multi_screenshot]_

_[tool: str_replace_edit]_

_[tool: show_html]_

_[tool: multi_screenshot]_

_[tool: str_replace_edit]_

_[tool: show_html]_

_[tool: multi_screenshot]_

_[tool: ready_for_verification]_

A 65-second walkthrough in 14 scenes: title → setup (name, products, prices) → empty dashboard → billing by search and by browsing → a zoom onto the price being overridden → customer name with suggestions → part payment → saved bill → stock already decremented → supplier purchase entry → offline with backup → close.

The phone is one continuous element — the camera pushes in and pulls back rather than cutting, and a cursor drives every tap. Scene lengths are editable on the timeline below the preview, and the Tweaks panel has the owner name if you want it to read differently. Export via Share → Export → Video.

## User

how can i download as a video ?

## Assistant

Open the **Share** menu at the top of the preview, then **Export → Video**. It renders the 65 seconds frame by frame and downloads an MP4 — pick a resolution there (1080p matches the design exactly).

Two notes: make sure the preview shows the video file, not the app; and let it finish without switching tabs, since it captures live.

## User

Since it is a offline app, i think we should have a settings where user can export / import the data to a different device

## Assistant

_[tool: snip]_

Adding a Settings screen with a real export/import handoff.

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: ready_for_verification]_

_[tool: ready_for_verification]_

Settings is in — reached by the gear on the Today screen.

- **This phone** — editable owner name, plus counts of products, bills and customers.
- **Move to another phone** — *Create backup file* writes one dated JSON with everything and shows it as a file chip with a Share action. *Import a backup file* picks a file, shows what's in it (owner, counts, date) and warns exactly what will be overwritten before you confirm.
- **Start again** — Start over moved here out of the Today header, where it was one stray tap from wiping the shop.

The Today backup nudge now also marks the file as written, so the two stay consistent.

## User

Found issues — fixing…

## Assistant

_[tool: snip]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

Now the import action itself, plural handling, and the Share feedback.

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: ready_for_verification]_

All three fixed:

1. **Import actually imports.** The backup file is real data now — 8 lock products and 4 bills from "Khalid Al-Amri". Confirming replaces products, bills and the owner name, so the greeting, Items list, bill history and the customer suggestions all change.
2. **Plurals** go through one helper, so it reads "1 product · 0 bills".
3. **Share responds** — it turns accent with a check and "Sending", and the card's copy switches to "Ready to send — pick AirDrop, WhatsApp or Files in the share sheet."

## User

Found issues — fixing…

## Assistant

_[tool: dc_js_str_replace]_

_[tool: dc_js_str_replace]_

_[tool: ready_for_verification]_

Fixed — the banner now counts distinct customers, so the imported data reads "Ahmed Contracting still owes — SAR 184" instead of miscounting his two bills as two people.

## User

Found issues — fixing…

## Assistant

_[tool: dc_html_str_replace]_

_[tool: ready_for_verification]_

Copy fixed — the notes panel now says the customer name is required, matching what the app enforces.

(The hex warnings are all in the video file, where the literals are deliberate: the exported SVG can't resolve CSS variables from an external stylesheet, and every value is a Nocturne token.)

## User

I want to build this app, can you provide me the complete description for the claude to paste in claude code?

## Assistant

_[tool: snip]_

_[tool: read_skill_prompt]_

_[tool: read_file]_

_[tool: write_file]_

_[tool: copy_files]_

_[tool: present_fs_item_for_download]_

Download the zip, unpack it into an empty folder, then open Claude Code there and paste:

> Read `design_handoff_stockbook/README.md` and build the app it describes. The HTML file is a design reference, not code to copy — recreate it as a real offline-first mobile app. Start with the data layer and export/import format, then the Items screen, then billing. Ask me before adding anything the README lists as deliberately left out.

The README is self-contained: every screen, exact colors and type sizes, the data model, all validation rules, and the real export/import behaviour (the prototype fakes the file layer).

Want screenshots of each screen added to the bundle?

