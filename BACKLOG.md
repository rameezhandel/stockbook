# Backlog — before going live

Four things deliberately left until the end. Recorded here because the work
happens in throwaway containers, and anything not written down is gone with
them.

Three of them are not code problems at all — two are one-time account setup and
one is an asset nobody has drawn yet. The fourth is: photographs do not travel
in the backup file yet, and that is the one with a date on it.

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

## 2. Stale remote branches

Roughly two dozen merged feature branches are still on the remote. Harmless, but
they make the branch list useless for seeing what is actually in flight, and it
gets worse every week.

**Done when** every branch whose commits are already on `main` is deleted.
`git branch -r --merged origin/main` lists them.

## 3. Bundled fonts

**Inter is not bundled on either platform.** Every type style names it, so both
apps fall back to the system face — San Francisco on iOS, Roboto on Android. The
layouts were designed against Inter's metrics, so spacing is slightly off from
the prototype everywhere. Cosmetic, and only cosmetic until somebody installs
the app beside the prototype and compares.

**Done when** both apps bundle the Inter weights the type scale actually uses.

*The launcher icon that used to sit here is done: the iOS mark redrawn for
Android's adaptive shapes, plus a themed-icon silhouette for Android 13+. See
[`../tools/make_play_assets.py`](tools/make_play_assets.py).*

## 4. Photographs do not travel in the backup file

A bill carries the **ids** of its photographs in the export, but not the
pictures. Move to a new phone today and every bill says "not on this phone"
where a photograph used to be. The backup screen says so in plain words, in
accent colour, rather than letting anyone find out the hard way.

Base64 inside the JSON is not the answer. Both platforms' encoders build the
whole document in memory before a byte reaches disk, so 200 photographs at
200 KB becomes 54 MB of base64 — held as a Kotlin `String`, which is UTF-16, so
108 MB, built by a `StringBuilder` that doubles as it grows. Android hands a
mid-range phone a 128–256 MB heap. It runs out, on the owner's only copy of
their records.

**The plan is a store-only ZIP:**

```
stockbook-2026-08-19.zip
├── stockbook.json          ← byte-identical to today's export
└── photos/
    └── <id>.jpg
```

- Peak memory is **one photograph**, because each is streamed from disk straight
  to the archive. There is no cliff to fall off.
- JPEG is already compressed, so the archive stores rather than deflates — no
  compression code at all, which is what makes hand-writing it on iOS
  reasonable. Android has `java.util.zip`; iOS has no zip writer and this project
  has no dependencies, so it is roughly 150 lines of local file header, central
  directory, EOCD and a CRC32 table.
- The JSON entry is unchanged, so every existing backup test and the
  cross-platform byte guarantee survive untouched. A bare `.json` keeps
  importing forever, and the reader sniffs the `PK` magic bytes rather than
  trusting the extension, because SAF and Files both lie about types.
- Photograph ids are **already in the file**, so bills re-adopt their pictures
  the moment the pictures arrive. Nothing needs migrating.

**Done when** a phone restored from a `.zip` shows the photographs, the line on
the backup screen is deleted, and a fixture archive written by each platform is
read by the other in both test suites.

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
