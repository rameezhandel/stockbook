# Releasing

How a build gets from `main` onto a shopkeeper's phone.

Written down because the work happens in throwaway containers, and because the
two things that can go permanently wrong here — a version code and a signing key
— both go wrong quietly and cannot be undone afterwards.

## The two numbers, before anything else

Every release names two, and they are different kinds of thing.

| | What it is | Example | Rule |
| --- | --- | --- | --- |
| `versionCode` | Play's ordering number | `2` | A whole number. Higher than every upload before it. **Never reusable, never lowerable.** |
| `versionName` | What people read | `1.0.1` | Three parts. Play does not care what it says. |

They sit next to each other on the form and both look like numbers, which is how
`1.0.0` once went into the version code — Gradle calls `toInt()` on it and throws
`For input string: "1.0.0"` half a minute later, naming a line in
`build.gradle.kts` rather than the box that was wrong. The workflow now refuses a
non-integer in about a second and says which box it means.

`versionCode` only counts: 1, 2, 3. It does not have to match `versionName` and
usually will not.

## Android

### 1. Get it onto `main`

Merge, and wait for Android CI to pass — it builds the APK and reads its
permissions back, which is the check that matters. **iOS only builds on a pull
request**, so if the change touched `ios/`, open one and let it run before
merging. Merging straight to `main` skips iOS entirely; a session's worth of
Swift once reached `main` with three compile errors in it that way.

### 2. Run `Play bundle`

Actions tab → **Play bundle** → *Run workflow*. Fill in `versionCode` and
`versionName` per the table above.

It runs the domain tests, builds, and then **checks the finished `.aab` for a
signature** rather than trusting the configuration — Gradle will happily produce
an unsigned bundle, and Play would otherwise be the one to tell you, slowly. If a
secret is missing it stops at the first step and names it.

Download the **stockbook-release-aab** artifact from the run.

The workflow uploads nothing. Publishing stays a human action, which keeps the
credential that can publish out of CI.

### 3. Upload to a track

- **Internal testing** — instant, no tester minimum. Your own loop.
- **Closed testing** — the one that counts toward production access. See below.
- **Production** — only once Play has granted access.

Install from the tester link and actually use it. A release build is not a debug
build: no "Start over", a different signature, and it is the artifact Play
re-signs with **Google's** app signing key, so what installs is not byte-identical
to what was built. Sideloading never exercises that step.

### 4. Promote

Production takes the *same artifact* from a testing track — Play Console →
Production → *Promote release*. Nothing is rebuilt, so nothing can differ between
what was tested and what ships.

## The 12-tester gate

**A personal developer account created from November 2023 onward cannot publish
to production until it has run a closed test.** Play requires:

- **Closed** testing — internal testing does **not** count
- **At least 12 testers** opted in
- **Continuously for 14 days** — the count has to hold for the whole stretch
- Then an application for production access, which Google reviews

Organisation accounts are exempt, which is why plenty of advice online omits this
entirely.

What it means in practice:

1. Create a closed track and a tester list — a Google Group, or an email list of
   twelve or more Google accounts.
2. Send the opt-in link. **Each person must open it and accept**; somebody who
   agreed in conversation and never opted in does not count.
3. Keep the count at twelve or above for the full fourteen days. People opting
   out can put the clock back.
4. Keep shipping to the closed track while it runs. That is what the fortnight is
   for.
5. Apply for production access when the Console says you qualify.

Recruiting the twelve is the hard part, not the build. They have to install it
and leave it installed; they do not have to use it.

**Check the Console's own Production access page rather than this file for the
exact numbers.** Play has changed this policy before and that page tracks the
account.

## iOS

`TestFlight` is also manually triggered, and takes a marketing version. It needs
the four App Store Connect secrets listed in [`../BACKLOG.md`](../BACKLOG.md).

Remember that **iOS builds only on a pull request**, so a change that never opened
one has never been compiled — regardless of how green Android looks.

## The signing key

The Android release key is in four repository secrets and CI reads them; a
release needs nothing from your machine.

The key itself is `android/keystore/release.jks`, gitignored and never committed.
It is **the permanent identity of the app on Google Play**. Before the first
upload there was no recovery from losing it. After it, with Play App Signing
enrolled, Google holds the app signing key and this one is the *upload* key, which
can be reset with their help.

Back it up somewhere that is not the machine that made it. See
[`../android/keystore/README.md`](../android/keystore/README.md).

## The package names

They differ per platform, and that is correct rather than drift:

| | |
| --- | --- |
| Play | `com.stockbook.application` |
| Apple | `com.stockbook.app` |
| Kotlin `namespace` | `com.stockbook.app` — unchanged by the Play rename |
| Debug build | `com.stockbook.application.debug`, so it sits beside a release one |

`com.stockbook.app` was taken on Play. A package name is burned the moment any app
is created with it — a deleted draft included — so it was never available to
claim. Apple's namespace is separate from Google's, so iOS keeps it.

Two things hang off `applicationId` and cost a build if forgotten: androidx
derives its private permission from it, so the CI permission allowlist has to
match whichever build it reads (**the debug one**, so with `.debug` on it); and the
`FileProvider` authority is `${applicationId}.files`, which follows on its own
because the manifest never hardcoded it.

## What cannot be done from a session container

The agent proxy refuses write access to some GitHub API paths and allows others.
Pushing, and opening or closing pull requests, all work. These do not:

- `git push origin --delete` and `DELETE /git/refs/…` — branch cleanup is a
  GitHub UI job
- `POST /actions/workflows/…/dispatches` — **neither `Play bundle` nor
  `TestFlight` can be started from a session.** Both are `workflow_dispatch`
  only, so a person runs them from the Actions tab.

Reading is fine, and the API is the honest source for whether a run passed:
`conclusion` is one word, where the rendered status column is not reliably
readable from outside a browser.
