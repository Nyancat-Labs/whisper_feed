# Handover: Whisper, 17 October 2026

For a new Claude Code session picking up from the first one (late September to
17 October 2026). The repository is the record; this note covers what isn't
obvious from it.

## The app

Whisper, an Android RSS reader. Package `com.nyancatlabs.whisper`, published by
Nyancat Labs. Repo `Nyancat-Labs/whisper_feed`; the old name `defsix/076feed`
redirects. Kotlin source still lives under `com.saulhdev.feeder` (a fork of
Neo Feed).

- **Google Play:** live since 15 Oct, version 1.0.4 (versionCode 5).
- **F-Droid:** live since 16 Oct, https://f-droid.org/packages/com.nyancatlabs.whisper/
- **GitHub Releases:** v1.0.3 and v1.0.4.

## How we work

- **Push to main, always.** No PRs, no feature branches.
- **Instructions in steps.** Clear and short. When there are several stages,
  stop after each one so the owner can check it and ask questions.
- **Use only the assets provided.** No new icons, images or fonts without
  asking first. The bundled Phosphor icons are in `ui/icons/phosphor/`.
- **Don't raise the version without need.** Release only when there's
  something worth shipping.
- **Diagnostics never log field contents.** Counts, class names and feed
  titles are fine. Headlines, links and IDs are not (`ReportPrivacyTest`).
- **Never commit `keystore.properties` or the `.p12`.**
- **Commits:** no model names in messages or pushed files. Update the README
  test-count badge when the test count changes.
- **Writing:** release notes and replies should sound human, with little bold.
- **Tags:** pushing tags is refused (403). The owner creates tags in GitHub's
  release page, targeting main.
- **The review sheet** (from 27 October 2026):
  https://claude.ai/artifact/ErrU1RDxYbTrd6nU2rowRk holds every issue found
  while testing previews, and the owner's decisions. Read it with the
  ArtifactData tool (collection `items`) before starting fixes; add new
  issues there rather than starting another list. Each issue has a `status`:
  `open` (owner to decide), `fix`, `later`, `drop`, `merged` (see
  `mergedInto`), then `preview` once a fix is in a preview build (set
  `build` to the commit and, if useful, `fixNote`), and `done` when the
  owner taps Works. Still wrong sends it back to `fix` with a note.
  Test-checklist rows are `kind: "check"`.
  **Every preview sent to the owner gets build tests** (owner, 27 October):
  `kind: "test"`, doc id `t<seq>-<nn>`, with `seq` (one more than the last
  build's), `build`, `n` (the order to do them in), `area` naming the build,
  `title`, `detail` starting "You should see:", `issues` (the issue numbers
  it proves) and `status` (`open`, `pass`, `problem`, `untested`). The page
  closes an issue in `preview` once every test naming it passes.

## Release signing

The owner holds `nyancatlabs-whisper-release.p12` and `keystore.properties`.
Put the `.p12` somewhere in the new container and set `storeFile` in
`keystore.properties` (repo root, gitignored) to its absolute path.

- **Our key (SHA-256):** `D4:54:52:45:6C:89:FE:AF:2D:D5:A4:90:10:EF:14:93:70:A4:09:B6:3A:15:31:02:7B:D4:CA:A8:2F:88:B1:64`
- **F-Droid signs with its own key:** `A8:DD:E0:89:…:71:6B`. Both are
  registered with Android developer verification.

**Builds:**
- Preview APK: `./gradlew :app:assemblePreview`, then copy to `out/preview/`
  and send to the owner. It installs over the Play build.
- Tests: `./gradlew :app:testDebugUnitTest`. 1122 tests passing at handover.

## F-Droid releases

Metadata lives in `docs/fdroid/com.nyancatlabs.whisper.yml`. It holds the
latest version only, with no blank line after `Builds:`. Before a release,
check with fdroidserver:

1. `remove_signing_keys`
2. `scan_source` on a copy (it deletes the Gradle wrapper, so build the copy
   with `./gradlew -p <copy>`)
3. `scan_binary`

Store text and screenshots are in `fastlane/metadata/android/en-US/`.

## Unreleased on main (the next release)

Release when the owner says so; there's no rush.

**After releasing the translations:** remind the owner to email the user
in France who asked for them (his email of 27 October is in the hej@
inbox). He offered to check the French.

- A stale "Syncing" notification is taken down (`clearStaleSyncNotice`).
- Tour highlight boxes stay inside the screen.
- Settings → Show summaries (off keeps picture and headline).
- Settings → Article times: "2h", "14:32", or both.
- New F-Droid screenshots and short description (F-Droid shows them from the
  next build). Play's short description was changed in the console on 16 Oct.

`CHANGELOG.md` "Unreleased" has the wording. Store changelogs go in
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.

## Open items

- **Tablet screenshots:** the owner takes them on the Galaxy Tab S5e. Style
  them to match the phone set, upload to Play, and add them to fastlane as
  `tenInchScreenshots`.
- **Google's key-verification emails:** ask every few days whether they've
  arrived. No action needed.
- **Daily reminder:** routine `trig_012NELTGdde7wPutZFQxu25j` (8:52 Dublin).
  Point it at the new session, and turn it off once both items above are done.
- **First user feedback (Jitka, by email):** see ROADMAP.md, "From the first
  user email". Done: hide summaries and time format. Next in her order:
  - an in-app text size (needs design; cards are laid out for fixed text);
  - a new-article count in the top bar.

  Asking her permission to be named in a Thanks section is the owner's
  decision. Don't do it unprompted.
- **Material 3 Expressive:** wait until `material3` 1.5.0 is stable. It was
  at beta01 on 7 Oct.
- **Roadmap:** ROADMAP.md, "After the stores" has the order. Auto-scroll is
  wanted soon.
