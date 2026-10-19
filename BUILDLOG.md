# Whisper — build log

What was done, when, and anything worth knowing next time. Newest first.
What changed for users is in [`CHANGELOG.md`](CHANGELOG.md); what is next is
in [`ROADMAP.md`](ROADMAP.md).

**Test devices.** The owner runs preview builds (`.dev`, signed with the
release key, so each installs over the last with data kept) on a Pixel 10,
and Google Play builds on two other devices.

## 19 October 2026 — Saved articles in the backup

- Second in the order. On main, for 1.0.6.
- Found Neo Feed's manual Export/Import bookmarks already in the Sources
  menu, so the backup reuses its file format rather than inventing one:
  `whisper-saved.json` beside the OPML and settings, kept with a
  `.previous` copy like the other two.
- Restore by folder (first run and "Restore" on an existing folder) now
  reads all three files, sources first so articles find their source. New
  "Restore saved articles" button on the Backup screen.
- Two bugs found on the way:
  - The daily backup wrote only switched-on sources (it asked the sync's
    question, `getAllSources`). Now `getAllSubscriptions`.
  - The inherited bookmark import wrote by uuid, so on a phone that had
    synced the same article it made a duplicate, and it dropped articles
    from unsubscribed sources. It now goes through the same restore.
- 7 new tests (`SavedArticlesBackupTest`); `BackupOverwriteTest` now expects
  three files kept with `.previous`. 1216 passing.
- **To try:** save two or three articles, then Backup and Restore → Back up
  now. The folder should hold `whisper-saved.json`. Unsave one, then
  "Restore saved articles" and pick that file: it should say "Restored 1
  saved article".

## 19 October 2026 — Update notice for GitHub installs

- First item in the owner's new working order. On main, for 1.0.6.
- A copy counts as a GitHub install when it is signed with our key and was
  not installed by Play, F-Droid, Obtainium or another updater. The key
  check matters: someone who sideloaded F-Droid's APK can't install ours
  over it, so they're never pointed at it.
- Off by default. The switch is under About → Updates, and that section
  only appears on GitHub installs. Turning it on checks straight away, then
  once a day on any network. One request to GitHub's "latest release" API,
  with the user agent just "Whisper". Nothing installed: a card in About and
  one notification per version, opening the release page.
- PRIVACY.md has a new section 8 for it. The diagnostics report says
  whether the copy counts as a GitHub install and the newest tag seen.
- 10 new tests (`UpdateCheckTest`), including one that the key in the code
  matches the one in `docs/HANDOVER.md`. 1209 passing, lint clean.
- **To try on the Pixel:** the preview is signed with our key and sideloaded,
  so it counts. Turn the switch on; About should say nothing new, since
  1.0.5 is the latest release and the preview is 1.0.5. The card appears
  once 1.0.6 is tagged.

## 18 October 2026 — Reader fixes from the real-feed check

- Headline repeated under a kicker, lazily loaded pictures, xkcd's comic,
  stray credits, furniture labels, and noscript-only pictures. Each pushed
  separately with its tests; details in CHANGELOG.md under Unreleased.
- Measured on the same 476 articles: 100 flags to 16, pictures 1512 to 1526.
- Full suite: 1173 passing, 1 skipped (RealFeedReport, which needs the data).
- SoraNews24 pictures showed as the placeholder on the phone: the reader
  split a srcset at every comma, and WordPress addresses carry commas
  (`?resize=640,480`). Now parsed as browsers do. In the check's data, 15
  articles across 6 sources had such addresses. 1176 tests passing.
- Confirmed on the Pixel 10 (preview ee4f691), 19 October: SoraNews24's
  pictures all load; TechCrunch's headline once, no "In Brief" above it.
- Split articles (Ars, Wired) found on the phone and fixed the same way.
  Decided against a second purge of stored full text to apply it to articles
  already fetched: with full text for every feed on, that is a week of pages
  fetched again, and saved articles would lose their offline copy meanwhile.
  They age out within the one-week sync window.
- The noscript, split-article and xkcd fixes act when an article is fetched, so articles
  already stored keep their old text until they age out. The others act in
  the reader and mend stored articles as well.

## 18 October 2026 — Reader checked against real feeds

- `scripts/real-feed-check.py` and `RealFeedReport`: the owner's 129 feeds,
  476 articles, through the app's own parser and reader. Findings and the
  fix order are in ROADMAP.md under "Checking the reader against real feeds".
- `RealFeedReport` is skipped in a normal test run; it only runs with
  REAL_FEED_DIR set. The fetched pages stay out of the repository.
- The first fetch did not unpack pages servers compressed unasked (The
  Independent's feed, Archaeology's pages), which looked like parse failures.
  OkHttp unpacks those for the app; the script now does too.

## 18 October 2026 — Lawnchair whitelist request

- Opened [LawnchairLauncher/lawnchair#7399](https://github.com/LawnchairLauncher/lawnchair/pull/7399)
  against `16-dev` (their default now, not `15-dev`). One line in
  `FeedBridge.kt`, using `getSignatureHash` like Neo Feed, since a fixed hash
  would match only one of Play, F-Droid and GitHub. Edited in the browser,
  which forked to `defsix/lawnchair` and committed to `patch-1`.
- Waiting on a maintainer. This session cannot read Lawnchair's PR checks;
  GitHub notifies the PR's author.

## 18 October 2026 — 1.0.5 released

- **Google Play:** `Whisper_1.0.5_release.aab` (version code 6) sent to
  production at full rollout. One warning, about native debug symbols; it
  comes from two AndroidX libraries that ship already stripped
  (`libandroidx.graphics.path.so`, `libdatastore_shared_counter.so`), so
  there is nothing to upload. Supported devices unchanged.
- **GitHub:** release v1.0.5, tag on 93b9d69 (the release commit, not main,
  which had moved on by three commits). APK `Whisper_1.0.5.apk`, SHA-256
  62627fcb281116c82ee31c685641d671fdbe2841e952a33ac9f24bc246201b81,
  checked against the uploaded file.
- **F-Droid:** entry in `docs/fdroid/` builds 93b9d69. Their bot follows tags,
  so it should pick up v1.0.5 by itself.
- **Upgrade from 1.0.4, checked without a device:** same package and signing
  certificate, version code 5 → 6, identical manifest names, and the database
  upgrades from every past version to 27 with its rows. That last check had
  been stopping at 26; `MigrationTest` now follows the newest exported schema.
- **README:** Obtainium badge, and a note that Play, F-Droid and GitHub are
  signed with different keys.

**Next time:** tag the release commit when it is pushed, so the GitHub release
only has to pick the existing tag.
