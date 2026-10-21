# Whisper — build log

What was done, when, and anything worth knowing next time. Newest first.
What changed for users is in [`CHANGELOG.md`](CHANGELOG.md); what is next is
in [`ROADMAP.md`](ROADMAP.md).

**Test devices.** The owner runs preview builds (`.dev`, signed with the
release key, so each installs over the last with data kept) on a Pixel 10,
and Google Play builds on two other devices.

## 21 October 2026 — Design audit, and its small fixes

- Auto-scroll parked by the owner. Design audit done (`docs/DESIGN_AUDIT.md`):
  Google's pages read on the day, the code read against them.
- The reader's back handler looked as if it could close the app; checked on
  the Pixel: back from an article returns to the feed. Harmless, but wrong
  in its own terms; now says what it does.
- Fixed: side insets (once, in `ViewWithActionBar`, and in the panel),
  bottom space on Settings and Sources, four spoken labels, heading height,
  web view back, sync channel text, two unused library entries.
- Not changed, on purpose: the card source-name target (Compose already
  widens it to 48dp), one-line page titles (would break the collapsed bar;
  for Expressive), the old translucent style flags (the panel shares the
  theme; needs an older phone to check).
- `DesignAuditFixesTest` (6) holds them. 1237 passing, lint clean.
- **To try:** turn the phone sideways on Settings and About: nothing under
  the navigation bar or camera. Scroll Settings to the end: the last row
  clear of the gesture bar.
- **Confirmed on the Pixel, 21 October:** "Looks good".

## 20 October 2026 — Comments link

- Fourth in the order, brought forward at the owner's request. Checked
  first against the owner's 121 feeds: 24 carry a comments address, plus
  Hacker News (its feed hadn't downloaded in the stored check; fetched
  live). Reddit needs nothing: its links already go to the thread.
- Read from RSS `<comments>` and Atom `rel="replies"` (web pages only, not
  comment feeds), kept in `Article.commentsLink`, database 29. A later
  fetch that leaves it out keeps what was stored.
- Shown as "Comments on <site>" at the end of the reader, only when there
  is one. New `ChatCircle` icon (Phosphor).
- 5 new tests (`CommentsLinkTest`); `MigrationTest` covers 28 → 29 by
  itself. 1231 passing, lint clean.
- **To try:** open a Hacker News item in the reader and scroll to the end:
  "Comments on news.ycombinator.com". Articles already stored get it at
  their feed's next sync.
- **Confirmed on the Pixel, 21 October 16:24:** "Comments on
  news.ycombinator.com" at the end of a Hacker News article. The "Comments"
  seen at the top first was HN's own feed text, replaced by the full
  article as designed.

## 19 October 2026 — Reading time and Not interested

- **Owner's first test, 22:59:** a card stuck a third of the way off
  during a sync. The gesture was keyed on the dismiss lambda, which every
  feed refresh replaces, so it was cancelled mid-drag. Now keyed on nothing,
  reads the lambda through `rememberUpdatedState`, and springs back if
  cancelled for any reason. Also: a swipe started at the screen's left edge
  is Android's back gesture and leaves the app; nothing an app can claim.
- **Second test, 20 October 07:16:** still couldn't finish a swipe, and new
  lines above and below every card. Both mine:
  - Half resistance and a 50% threshold meant a full screen of finger
    travel; from mid-card it was unreachable. Now the card follows the
    finger and commits at 40% (25% with a flick). A test now checks the
    commit point is reachable from mid-card.
  - The reveal panel's fade was applied after its colour, so the colour was
    always drawn and showed in the 3 dp gaps between cards. Fade first.
- **Third test, 07:31: the swipe works.** Two finishing touches asked for:
  the dismissed card's space now closes (220 ms) before the feed is told,
  instead of standing empty until it reloads; and the Undo messages in the
  app and panel use `FeedSnackbarHost`, every colour named and a shadow.
  Nothing in the code made them translucent; the screenshot most likely
  caught the fade-in, but the shadow helps over photos either way.
- **Fourth round:** the back-to-top button sat over Undo; it now steps
  aside while an Undo message is showing (app and panel). Dismissed today
  rows spaced apart.
- **Fifth round, owner's choice:** the list is now "Dismissed", covering
  everything still held (a week with the owner's settings), not only today.
  What Whisper learned shows "Not interested: N this week" per source. 2 new
  tests; 1226 passing.
- **Confirmed on the Pixel, 20 October 16:22:** swipe dismisses, Undo is
  solid and clear of the back-to-top button, "Dismissed · 3" lists all
  three across two days, and What Whisper learned shows "Not interested: 2
  this week" for TheJournal.ie. The owner is now using it for a few days to
  decide whether it stays in 1.0.6.

- Third in the order; one database change, version 27 → 28 (`hiddenAt`,
  `readingMinutes` on Article). `MigrationTest` covers it, and its "stays in
  All" check now only runs for versions before that column existed (the
  seed was writing its own value into it from 27 on).
- Reading time: counted on sync from the feed's text when it's whole, and
  after a page download from the page. Shown after the age on all four
  card shapes and in the reader.
- Not interested: swipe right on any card (all three feeds share one card
  composable, so one wrapper covers app list, grid and panel), or ⋮ → Not
  interested, or the TalkBack action. Hidden, marked read, Undo snackbar.
  Every third from a source in a week = one "Less like this".
- Heavy swipe, per the owner: half resistance, half the width to commit
  (a third with a hard flick), haptic at the commit point, springs back
  otherwise. Only claims drags that start rightward and are twice as wide
  as tall, so the list scroll and Lawnchair's leftward swipe home are left
  alone.
- Dismissed today: in the filter sheet, app and panel, with Bring back.
- `CardReadabilityTest` read the card source to the exact indentation;
  now a regex, same check. 6 new tests (`NotInterestedTest`). 1222 passing.
- **To try:** swipe a card right slowly a little way and let go: it should
  spring back. Swipe past halfway: a tick, and it goes, with Undo. Then
  filter sheet → Dismissed today → Bring back. And the same in the panel.

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
- **Confirmed on the Pixel, same day:** backup wrote `whisper-saved.json`,
  and restoring after unsaving one put it back.

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
