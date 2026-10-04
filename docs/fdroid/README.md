# F-Droid

`com.nyancatlabs.whisper.yml` is Whisper's entry for F-Droid's app list,
[fdroiddata](https://gitlab.com/fdroid/fdroiddata). It lives there, as
`metadata/com.nyancatlabs.whisper.yml`; this copy is the one to edit and paste.

- **Builds from the tag.** `commit: v1.0.0` is the release; F-Droid checks out
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
