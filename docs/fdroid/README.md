# F-Droid

`com.nyancatlabs.whisper.yml` is Whisper's entry for F-Droid's app list,
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). It lives there, as
`metadata/com.nyancatlabs.whisper.yml`; this copy is the one to edit and paste.

- **Builds from the tag.** `commit: v1.0.2` is the release; F-Droid checks out
  that tag, runs the `release` build (unsigned without `keystore.properties`)
  and signs the APK with its own key.
- **The listing text is not in this file.** Title, descriptions, changelog and
  screenshots come from `fastlane/metadata/android/en-US` in this repository.
- **No anti-features.** No trackers, no proprietary libraries, and the network
  services are the reader's own feeds, their own FreshRSS server, and
  Open-Meteo, which is free software.
- **Updates:** `UpdateCheckMode: Tags` and `AutoUpdateMode: Version` mean a new
  `vX.Y.Z` tag with a higher `versionCode` is picked up without another merge
  request.
- **Signing lines stay on one line each.** Before building, F-Droid deletes
  every line that sets a signing key (`remove_signing_keys` in fdroidserver).
  One that runs over several lines leaves the rest behind and breaks the
  build file - which is why 1.0.0 never built there. `FdroidBuildTest`
  runs the same deletion over `app/build.gradle.kts`.
- **Plain line endings.** The copy in the merge request must have Unix line
  endings and end with a newline, or `fdroid rewritemeta` fails.
- **Nothing of ours under Google's package names.** F-Droid's APK scan
  reads any class under `com.google.android.libraries.gsa` as Google's
  proprietary library. 1.0.1 failed it on the launcher panel's classes,
  which were open code with Google's name; they are in
  `com.saulhdev.feeder.launcherpanel` now, and `FdroidScannerTest` keeps
  them there. The Binder interfaces in `launcherclient` keep their names:
  the descriptor strings are Lawnchair's protocol.
- **Checked before every tag, with F-Droid's own tools** (fdroidserver,
  installed from PyPI): `fdroid lint` and `fdroid rewritemeta` on this file,
  with fdroiddata's `config/categories.yml`; and on a fresh copy with
  `remove_signing_keys` applied and a keyless `assembleRelease`, the
  scanner's `scan_source` on the tree and `scan_binary` on the APK. All
  clean for 1.0.2; `scan_binary` on the 1.0.1 build finds the same 20
  problems F-Droid's pipeline did.

