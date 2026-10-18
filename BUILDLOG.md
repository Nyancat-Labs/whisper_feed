# Whisper — build log

What was done, when, and anything worth knowing next time. Newest first.
What changed for users is in [`CHANGELOG.md`](CHANGELOG.md); what is next is
in [`ROADMAP.md`](ROADMAP.md).

**Test devices.** The owner runs preview builds (`.dev`, signed with the
release key, so each installs over the last with data kept) on a Pixel 10,
and Google Play builds on two other devices.

## 18 October 2026 — Reader fixes from the real-feed check

- Headline repeated under a kicker, lazily loaded pictures, xkcd's comic,
  stray credits, furniture labels, and noscript-only pictures. Each pushed
  separately with its tests; details in CHANGELOG.md under Unreleased.
- Measured on the same 476 articles: 100 flags to 16, pictures 1512 to 1526.
- Full suite: 1170 passing, 1 skipped (RealFeedReport, which needs the data).
- The noscript and xkcd fixes act when an article is fetched, so articles
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
