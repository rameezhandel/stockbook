# Backlog — before going live

What is left, recorded here because the work happens in throwaway containers and
anything not written down is gone with them.

Nothing blocking is a code problem: what is left before going live is account
setup and store paperwork, all of it done by hand in Apple's and Google's
consoles. Everything under *Parked* is a choice nobody has had to make yet, not
work that is owed.

## 1. TestFlight — Apple account setup

The iOS release workflow is written and waiting; what it needs is an App Store
Connect account that knows about this app. All four steps are done once, by hand,
in Apple's web console — nothing in this repo can do them.

1. Register the bundle ID `com.stockbook.app` in the developer portal.
2. Create the App Store Connect record for it.
3. Create an App Store Connect **API key** with the App Manager role, and
   download the `.p8` — Apple shows it exactly once.
4. Add four repository secrets: `APPSTORE_ISSUER_ID`, `APPSTORE_KEY_ID`,
   `APPSTORE_PRIVATE_KEY` (the whole `.p8` file's contents, newlines and all),
   and `APPLE_TEAM_ID`.

**Done when** a push to `main` uploads a build that appears in TestFlight.

## 2. Google Play — the parts only a person can do

The signing config and the bundle workflow are written; see
[`android/keystore/README.md`](android/keystore/README.md).

1. **Generate the upload key** with the `keytool` command in that file, and back
   it up somewhere that is not this machine. Lost before the first upload, the
   listing can never be updated by anyone.
2. **Set the four repository secrets** the same file lists.
3. **Screenshots** — at least two, from a real phone. See
   [`play/README.md`](play/README.md).
4. **Console forms** — Data safety (*no data collected, no data shared*),
   content rating, target audience, ads: none.
5. **Install the release build and use it** before uploading. It differs from
   debug: no "Start over", a different signature.

**Done when** `Play bundle` produces a signed `.aab` that the Console accepts.

## Parked, not blocking

- **Photographs on deliveries.** `Purchase` would take `photoIDs` exactly as
  `Bill` did — a supplier's invoice is arguably the more valuable of the two,
  since it is paper the shop cannot reprint.
- **iOS excludes nothing from iCloud backup.** Android is explicit that nothing
  leaves the device, including via Google's backup transport; iOS sets no
  `isExcludedFromBackup`, so the shop file — and now the photographs — ride along
  in iCloud and iTunes backups. That is a product decision nobody has made, not a
  bug: excluding it honours the stated promise and matches Android, not excluding
  it means a lost iPhone restores the whole book.

## Settled, so nobody reopens them

- **A customer is identified by their name, and merging is deliberate.**
  `Customer.key` is the trimmed, lowercased name, so two accounts cannot share
  one. That was examined properly when an owner asked whether customers should
  carry an id instead, as products do.

  **Renaming onto a name somebody else answers to is refused**, by the form
  while the owner types and again by the store on save. It used to *merge* the
  two — silently, on a keystroke, keeping one opening balance and throwing the
  other away while stranding the credit notes under a key nothing pointed at.
  On a book of firms that is two companies' ledgers fused by a typo.

  **Joining two accounts is now something you ask for**, from the account that
  will be the one to go, with what moves and what the survivor will owe shown
  before you agree. The two opening balances are *added*: two entries in the
  paper book for one firm are two debts really owed.

  Ids were **deferred, not rejected**. The case for them is real — two different
  people with the same name are one account, and no amount of gating fixes
  that — but this is a B2B book where company names are distinct, and the change
  reaches `Bill.who`, four foreign keys, the backup format and every screen that
  derives a party from a bill. If it is ever done it has to be **before** real
  books exist: a year of history containing one name that is really three firms
  cannot be untangled afterwards, because what would separate them was never
  recorded.

- **What the goods earned — the screen, done.** Reached from the Sold card on
  Home. It walks Sold → Cost of goods → What the goods earned → Credited →
  Expenses → What the shop kept, and shares its arithmetic with the PDF through
  `EarningsDocument`. Called *what the goods earned* rather than *profit*, which
  would claim to have counted rent, wages and petrol.

  The three decisions the parked note left open were settled by building it:

  - **The discount needs no apportioning.** `Bill.total` is stored *after* the
    discount, so a bill's takings less its lines' cost is exactly what that bill
    earned. The whole apportion-across-the-lines branch was unnecessary.
  - **A bill entered as a figure is set aside whole and said to be** — never
    half-counted, which would flatter the figure by the difference. Named apart
    from the other reasons in the "Not counted" block, because "itemise the next
    one" and "this book is older than the field" ask different things of the
    owner.
  - **A credit note is netted, and its goods go back.** A return is a sale run
    backwards: take off what was credited, add back what the returned goods
    cost. A figure-only note hands nothing back, so that second figure is zero
    and the whole credit comes off — the rule arriving at the right answer
    rather than an exception to it.

  Two things an owner found on real data are pinned by tests. **An absence is
  not a loss**: where nothing in the period can be costed, the chain stops after
  "Counted" rather than subtracting the month's expenses from zero and printing
  the rent as a loss. And **a book written before `BillLine.cost` existed is
  costed at today's buying price**, labelled as an estimate on the page, written
  back nowhere — so it follows the shelf and clears itself as costed bills
  replace the old ones. A line whose product has since been deleted has no
  figure anywhere and is still set aside.

- **Multi-line supplier bills — done.** A delivery holds `lines`, the shape
  `Bill` already had. This was not the enhancement it looked like: the sheet
  refuses a repeated invoice number across the whole book — one number, one piece
  of paper — so a five-line delivery note could not be entered *at all*, not as
  five records and not as one. The four one-product fields survive as a way in
  from older records; `Purchase.items` folds them into a single line, and
  correcting a delivery rewrites it into the new shape. No version bump: `total`
  and `paid` are untouched and the shelf count lives on the product, so a reader
  that drops the lines has every figure right.
- **Creating a product from the delivery sheet — done.** The product list offers
  it when nothing matches, the way the supplier list already did. It was the
  thing that would have made a multi-line sheet worse than what it replaced: five
  new products meant ten sheets, and the half-typed delivery did not survive the
  trip. The new product gets no selling price, which the Items screen already
  flags as something to fill in.
- **Photographs travel in the backup — done.** The export is a store-only ZIP:
  `stockbook.json`, byte-identical to what the plain export always wrote, plus
  `photos/<id>.jpg`. Base64 inside the JSON was rejected on memory — both
  platforms' encoders build the whole document before a byte reaches disk, so
  200 pictures at 200 KB becomes 54 MB of base64 held as a UTF-16 Kotlin
  `String`, which is 108 MB on a phone given a 128 MB heap. The archive streams
  one picture at a time instead.

  `java.util.zip` is deliberately **not** used on Android. iOS has no zip reader
  and this project has no dependencies, so the format had to be hand-written for
  Swift regardless; written twice the two would drift. It is written once in
  Kotlin, where it is tested against `java.util.zip` in both directions, and
  ported — with a shared base64 fixture asserted in both suites as what each
  platform writes *and* reads. The DOS timestamp is fixed at 1980-01-01 so that
  fixture can be a constant.

  A bare `.json` still imports forever, sniffed by its magic bytes. Photograph
  ids were already in the document, so nothing had to migrate.
- **Branch cleanup cannot be done from a session container.** Not a permission
  on the token and not a flaky network: the agent proxy refuses write access to
  the paths that would do it. `git push origin --delete` answers **403**, and so
  does `DELETE /repos/.../git/refs/heads/...` — with the proxy saying so in as
  many words. Ordinary pushes, and creating and closing pull requests, all work
  fine, so the proxy allows some write paths and not others.

  The same wall stops `POST /actions/workflows/ios.yml/dispatches`, which is why
  the iOS workflow cannot be started by hand from here even though its own
  comment offers that as the easy route. **A pull request is the only way to
  reach the iOS build from a session**, and where everything is already on
  `main` that means opening one from `main` into a throwaway base branch.

  Delete merged branches from the GitHub UI instead. Nothing depends on them.

- **Bundled fonts — decided against.** Every type style names Inter and neither
  app bundles it, so both fall back to the system face: San Francisco on iOS,
  Roboto on Android. Spacing therefore sits slightly off the prototype
  everywhere. Left that way on purpose. It is invisible to anyone who has not
  seen the prototype, and shipping two font families to correct it is weight for
  a difference the shopkeeper will never notice.
- **The launcher icon — done.** The iOS mark redrawn for Android's adaptive
  shapes, plus a themed-icon silhouette for Android 13+, generated by
  [`tools/make_play_assets.py`](tools/make_play_assets.py) alongside the Play
  listing artwork.
- **The privacy policy — done and verified public.** Hosted at
  <https://sites.google.com/view/stockbook-privacy/home>, with the wording kept
  under [`docs/privacy/index.html`](docs/privacy/index.html) so it stays
  versioned beside the code whose behaviour it describes.
- **The Play package name is `com.stockbook.application`, and it is not the
  Kotlin package.** `com.stockbook.app` was taken — Play burns a name the moment
  any app is created with it, a deleted draft included, so it was never
  available to claim. The Console said so at listing time, which is exactly the
  last moment it could have.

  Only `applicationId` moved. `namespace` is still `com.stockbook.app`, so no
  Kotlin package, import or file moved with it — the two are separate on purpose
  and this is what that separation is for.

  Two things follow from `applicationId` and cost a build if forgotten: androidx
  derives its private permission from it, so the APK now declares
  `com.stockbook.application.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` and the
  CI allowlist had to move with it; and the `FileProvider` authority is
  `${applicationId}.files`, which followed on its own because the manifest never
  hardcoded it.

  **iOS is unaffected.** Apple's bundle IDs are a separate namespace from
  Google's, so the app is still `com.stockbook.app` there. The two platforms
  disagree on this one string, and that is the correct answer rather than a
  drift to tidy up.

  Play still binds a listing to its package name permanently. This one cannot be
  changed again either.
- **The receipt number stays required.** Consistent with invoice and credit-note
  numbers: a payment that cannot be matched to a slip in a drawer is the thing
  the number exists to prevent.
