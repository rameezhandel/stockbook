# Signing keys

Two keys live here, and only one of them is in the repository. That difference
is the whole point of this file.

## `stockbook-debug.jks` — committed, deliberately

Its password is in `app/build.gradle.kts`. That is not an oversight.

Left alone, AGP generates a debug keystore on whatever machine is building, and
every CI runner is a fresh machine — so each build would carry a different
signature, installing a new APK over an older one would fail with a mismatch,
and the only way through would be to uninstall first. In an app whose whole
premise is that the shop lives on this phone and nowhere else, that means
throwing the shop away to take an update.

A debug key protects nothing. Android's own default one is public.

## `release.jks` — never committed, and never recoverable

This one is the **permanent identity of the app on Google Play**. It is in
`.gitignore`, and it must stay out of git history: history cannot be edited back
to safety, and it outlives whatever the repository's visibility happens to be
today.

### Creating it, once

```sh
keytool -genkeypair -v \
  -keystore android/keystore/release.jks \
  -alias stockbook \
  -keyalg RSA -keysize 4096 \
  -validity 10000 \
  -storetype PKCS12
```

`-validity 10000` is about 27 years. Play requires a key valid past 2033, and a
key that expires is a key that cannot ship an update.

**Back the file and its password up somewhere that is not this machine and not
this repository.** A password manager, or an encrypted archive you keep. If you
enrol in Play App Signing — which you should, and which is the default for new
apps — Google holds the *app signing key* and this becomes the *upload key*,
which can be reset with Google's help if it is lost. Lose it **before** that
first upload and there is no recovery: the listing cannot be updated by anyone,
ever, and the only way forward is a new listing under a new package name, with
every install orphaned.

### Building with it locally

Create `android/keystore/release.properties` — also gitignored:

```properties
storeFile=keystore/release.jks
storePassword=…
keyAlias=stockbook
keyPassword=…
```

Then:

```sh
cd android && ./gradlew :app:bundleRelease
```

Without that file and without the environment variables below, the release build
still succeeds — **unsigned**. That is intentional: a machine with no business
signing anything should not be stopped from compiling. Only
[`play.yml`](../../.github/workflows/play.yml) insists, and it checks the
finished bundle rather than trusting the configuration.

### Building with it in CI

`play.yml` reads four repository secrets:

| Secret | What goes in it |
| --- | --- |
| `PLAY_KEYSTORE_BASE64` | `base64 -w0 android/keystore/release.jks` |
| `PLAY_KEYSTORE_PASSWORD` | the `-storepass` you chose |
| `PLAY_KEY_ALIAS` | `stockbook`, unless you chose otherwise |
| `PLAY_KEY_PASSWORD` | the `-keypass` you chose |

The workflow is **manually triggered** and takes `versionCode` and `versionName`
as inputs. Play orders uploads by `versionCode`; it can never be reused or
lowered, so it is passed in rather than left to somebody remembering to edit
`build.gradle.kts`.

The workflow produces the `.aab` as an artifact and uploads nothing. Publishing
stays a human action, which keeps the credential that can publish out of CI.
