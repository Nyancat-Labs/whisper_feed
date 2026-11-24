# Handover: Whisper, 17 October 2026

For a new Claude Code session picking up from the first one (late September to
17 October 2026). The repository is the record; this note covers what isn't
obvious from it.

## Where we are (22 November 2026)

Start here after a /clear. CLAUDE.md has the working rules; this is state.

- **1.0.6 (build 7) released 22 November:** live on Play since 23
  November, GitHub v1.0.6 on 3a108b2, F-Droid follows the tag. No
  preview is out for testing.
- **Signing key** is in this container at `/root/whisper-signing/` (owner
  asked to keep it, 18 November), `keystore.properties` points at it. A new
  container needs both files uploaded again.
- **Szept** is the one list since 22 November (see *Work tracking* below).
  At the start of a session: `list_questions`, then
  `project_summary("Whisper")`. The old review sheet is frozen history.
- **Next for Claude:** the startup profile (66, Szept 177) is set up
  (24 November): `:baselineprofile` module, plugin, profileinstaller. It
  waits for the owner to record it on the Pixel from the laptop (commands
  in `baselineprofile/build.gradle.kts` and the Szept item); then commit
  `app/src/main/generated/baselineProfiles/` and compare the numbers. The
  Fade bug (Szept 447) waits on the reader's answer and Test build
  7254a64. Translations (56, Szept 176) wait for Weblate's approval.
- **Preview 9d14297** (no "[IMG]" summaries, Szept 452) sent 24 November;
  test item Szept 456.
- **Build setup in a new container:** the "076" environment's setup script
  does all of this since 24 November. Android SDK in `/opt/android-sdk`
  (cmdline-tools, platform-tools, build-tools 37; `local.properties` has
  `sdk.dir`). Maven Central answers 429 for big files from this network:
  `~/.gradle/init.d/central-mirror.gradle.kts` reads it through Google's
  mirror (`maven-central.storage-download.googleapis.com`) and sets
  `robolectric.dependency.repo.url` to the same. Without it, Robolectric's
  15 test classes fail. Neither lives in the repo; see BUILDLOG 22 November.
- **GitHub:** all nine issues answered 22 November; weekly check is
  Szept 418. Issue templates still say Neo Feed (Szept 411).
- **Weblate:** Libre approval still pending; trial extended to 7 December.
  Licence GPL-3.0-or-later on both components, App updated from GitHub and
  locked (22 November). After approval: push to GitHub, hooks, then unlock
  once Claude's translations are in (Szept 215, check 28 November).
- **Lawnchair** merged the whitelist (their PR 7399) into 16-dev on 24
  October: nightlies have it, no named release yet. Help → Launcher page
  shows both ways since batch 4.
- **archive/** (gitignored) lasts only as long as one container: the board
  export and earlier screenshots are gone. Files that must last go on Szept
  items (Szept 455).

## The app

Whisper, an Android RSS reader. Package `com.nyancatlabs.whisper`, published by
Nyancat Labs. Repo `Nyancat-Labs/whisper_feed`; the old name `defsix/076feed`
redirects. Kotlin source still lives under `com.saulhdev.feeder` (a fork of
Neo Feed).

- **Google Play:** live since 15 Oct, version 1.0.6 (versionCode 7) since 23 Nov.
- **F-Droid:** live since 16 Oct, https://f-droid.org/packages/com.nyancatlabs.whisper/
- **GitHub Releases:** v1.0.0 to v1.0.6.

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
- **No user's name or address in the repository.** It is public. "A
  reader" in files; names only in Szept (private).
- **Commits:** no model names in messages or pushed files. Update the README
  test-count badge when the test count changes.
- **Writing:** release notes and replies should sound human, with little bold.
- **Tools (20 November 2026):** `scripts/preview.sh` builds a checked,
  signed preview; `scripts/diag-summary.py` reads a diagnostics report
  before any guessing; `docs/ICONS.md` (`scripts/icon-index.py`) says where
  every icon and image is used; picture tests in `app/src/test/screenshots`;
  problem feeds in `app/src/test/resources/feeds`. The old board had a search
  box and shows the latest preview build at the top.
- **Tests check behaviour, not wording of the code.** Source-text tests
  broke on harmless renames twice on 20 November; replace them as they're
  touched.
- **Tags:** pushing tags is refused (403). The owner creates tags in GitHub's
  release page, targeting main.
- **Work tracking is in Szept (from 22 November 2026, owner).** The review
  sheet (https://claude.ai/artifact/ErrU1RDxYbTrd6nU2rowRk, page source
  `files/work-board.html`) is **frozen**: read-only history of issues 1-132
  and build tests up to build 21, with a banner saying so. Don't write to
  it. A full export is in `archive/files/2026-11-22-board-export/`
  (container only). Old card numbers ("board 56") are still used as names
  in Szept titles, ROADMAP and BUILDLOG (and as `legacy_ref` WH-i<nnn>);
  new items take no board number. ROADMAP holds plans and ideas: Szept
  writes the open items into its "Open in Szept" block nightly, so never
  hand-copy them.
- **Issues** are Szept items in project Whisper: Claude's fixes `kind:
  claude`, the owner's jobs `mine`, choices `decide` (Yes / No / Later),
  ideas on the Later list. Answer the owner's notes and questions with
  `add_comment_reply`; finish with `complete_reminder` (done / tested /
  not tested / undo).
- **Build tests: one item per preview build** (owner, 22 November): a
  `mine` item titled "Test build <hash> – <what's in it>", the numbered
  steps in `detail`, each with "You should see: …", T1 always "About ends
  in <hash>". The owner ticks it Done when every step passes; a step that
  fails comes back as a comment (with a screenshot), and Claude opens a fix
  item from it. Tag `build-test`, area the build. Close the build item when a newer build replaces it.
- **Files** (Szept 455, 24 November): the owner attaches screenshots and
  files to the Szept item they belong to; Claude opens them with
  `get_file`. Drafts Claude writes (emails, replies) go in the item's notes
  or a comment. Files sent in the chat are also saved to
  `archive/screenshots/` or `archive/files/`, named
  `YYYY-MM-DD-HHMM-what-it-shows.ext`, but that is for the session only:
  `archive/` is gitignored (the repo is public and screenshots show the
  owner's phone), so it is gone with the container. Szept's connector can
  read files but not upload them.

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

1.0.6 (build 7) went out on 22 November 2026: Play (in review, then live),
GitHub v1.0.6 (tag on 3a108b2), F-Droid from the tag. Nothing unreleased
yet. Next is 1.0.7 with the translations (board 56).

**After releasing the translations:** remind the owner to email the user
in France who asked for them (his email of 27 October is in the hej@
inbox). He has agreed (28 October) to check the French: invite him to
Weblate once it is set up, and send him the French preview before 1.0.7
ships, so his corrections go into the release.

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
- **First user feedback (a reader, by email):** see ROADMAP.md, "From the first
  user email". Done: hide summaries and time format. Next in her order:
  - an in-app text size (needs design; cards are laid out for fixed text);
  - a new-article count in the top bar.

  Asking her permission to be named in a Thanks section is the owner's
  decision. Don't do it unprompted.
- **Material 3 Expressive:** wait until `material3` 1.5.0 is stable. It was
  at beta01 on 7 Oct.
- **Roadmap:** ROADMAP.md, "After the stores" has the order. Auto-scroll is
  wanted soon.

## Weblate (set up 8 November 2026)

hosted.weblate.org/projects/whisper — one component, App (strings.xml, all
plurals included; the Plurals component and plurals.xml went on 16 November),
base file read-only there, French added 16 November, project **locked** until
Claude's translations land; Libre hosting requested (pending; trial ends 22 November). Weblate cannot push to
GitHub yet (no push URL), on purpose. See ROADMAP *Translations* for what to
do when unlocking. A string a person corrects on Weblate is never
overwritten by Claude.
