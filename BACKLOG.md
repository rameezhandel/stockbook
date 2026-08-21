# Backlog — before going live

What is left, recorded here because the work happens in throwaway containers and
anything not written down is gone with them.

None of it is a code problem any more: account setup, store paperwork, and
housekeeping that is now done. The one that was — photographs not travelling in
the backup file — is finished; see the closed item at the bottom for what was
built and why.

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

## Parked, but on a clock

- **Profit, and the one field that has to be stored before release.**

  Deferred as a feature — but `BillLine` snapshots the name, the quantity and
  the price *charged*, and **not what the goods cost**. Computing margin today
  would mean reading `Product.cost`, which is the buying price *now*: raise a
  supplier's price next month and last March's profit silently changes. The
  codebase already knows this is wrong, which is why `Bill.total` is stored
  rather than recomputed — cost simply never needed the same treatment.

  Adding `cost` beside `price` on `BillLine` is a few lines and, before release,
  needs no migration. **Afterwards it is a format change plus every bill already
  written permanently unable to answer the question.** Whoever picks this up
  should decide it on that basis, not on whether the screen is wanted yet.

  Two gaps would remain even then: a bill entered as a figure has no lines and
  so no cost, and would read as pure profit unless excluded and said so; and a
  figure-only credit note has no goods to reverse. Both argue for calling it
  *what the goods earned* rather than *profit*, which claims to have counted
  rent, wages and petrol.

## Parked, not blocking

- **Photographs on deliveries.** `Purchase` would take `photoIDs` exactly as
  `Bill` did — a supplier's invoice is arguably the more valuable of the two,
  since it is paper the shop cannot reprint.
- **Multi-line supplier bills.** A delivery still records one product.
- **iOS excludes nothing from iCloud backup.** Android is explicit that nothing
  leaves the device, including via Google's backup transport; iOS sets no
  `isExcludedFromBackup`, so the shop file — and now the photographs — ride along
  in iCloud and iTunes backups. That is a product decision nobody has made, not a
  bug: excluding it honours the stated promise and matches Android, not excluding
  it means a lost iPhone restores the whole book.

## Settled, so nobody reopens them

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
- **The stale branches — done.** Every branch whose commits were already on
  `main` has been deleted; `git branch -r --no-merged origin/main` was empty
  first.

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
- **The package name is `com.stockbook.app`.** Confirmed before the first
  upload, which is the last moment it could be. Play binds a listing to its
  package name permanently: it cannot be renamed afterwards by anyone, and
  changing it means a new listing with every existing install orphaned on the
  old one.
- **The receipt number stays required.** Consistent with invoice and credit-note
  numbers: a payment that cannot be matched to a slip in a drawer is the thing
  the number exists to prevent.
