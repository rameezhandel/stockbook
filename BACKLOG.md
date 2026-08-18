# Backlog — before going live

Three things deliberately left until the end. None of them block development, and
none are code problems: two are one-time account setup and one is an asset that
nobody has drawn yet. Recorded here because the work happens in throwaway
containers, and anything not written down is gone with them.

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

## 3. Launcher icon and bundled fonts

Two cosmetic gaps that are only cosmetic until somebody installs the app.

- **Android's launcher icon is the template placeholder.** iOS has a real one.
  A shop owner finding this on their home screen sees a generic Android robot.
- **Inter is not bundled on either platform.** Every type style names it, so both
  apps fall back to the system face — San Francisco on iOS, Roboto on Android.
  The layouts were designed against Inter's metrics, so spacing is slightly off
  from the prototype everywhere.

**Done when** Android ships a real adaptive icon and both apps bundle the Inter
weights the type scale actually uses.
