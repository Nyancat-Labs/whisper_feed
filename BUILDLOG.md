# Whisper — build log

What was done, when, and anything worth knowing next time. Newest first.
What changed for users is in [`CHANGELOG.md`](CHANGELOG.md); what is next is
in [`ROADMAP.md`](ROADMAP.md).

**Test devices.** The owner runs preview builds (`.dev`, signed with the
release key, so each installs over the last with data kept) on a Pixel 10,
and Google Play builds on two other devices.

## 20 November 2026 — Last release check passed

- T7 of build 14 passed: Quartz returned an empty feed (under 1 KB, no
  items) at 10:04, 10:24, 11:04 and 11:22 and is listed under Feeds that
  stopped working (owner's screenshot 11:40, diagnostics 11:41 in
  archive/board-files). Card 111 closed. My first guess in chat (a
  Cloudflare block, from what a server here gets) was wrong.
- Open question for after 1.0.6: Carryology, Everyday Carry, Gigaom,
  Inoreader blog, Ireland: BreakingNews, TreeHugger, Tech Travel Geeks and
  076 log 'new 0 <1 KB' yet aren't counted as failing, so they held items.
  A 304 would log 'unchanged', so the small size isn't that; unexplained.
- Every release check has now passed on the phone.

## 20 November 2026 — Board read, late morning

- Passed: build 18 (restore all ticked) and build 19 T1 and T3 (centred
  indicator); 123, 124, 125 closed. 114 closed by the owner. 12 and 18
  dropped (Szept 182, 183 cancelled).
- 112: the owner asked about any issues since; the 19 November 22:44
  diagnostics show 14 syncs with sign-in at 0.1-0.4 s. Answered on the card.
- Release gate left: empty feeds (T7 of build 14).

## 20 November 2026 — Card 126 raised

- Owner: should the number of sources that makes a story breaking be
  adjustable, since more News subscriptions means more alerts? Today it is
  Clustering.MIN_SOURCES = 3 within WINDOW_MS (12 h), fixed. Proposed on the
  board for after 1.0.6: scale to max(3, 1 in 10 News sources), plus a
  Fewer / Usual / More choice; not a raw number. Screenshot of a third alert
  (10:43) saved.
- Owner: on the roadmap, automatic first, a user choice possibly later.
  Card 126 set to Later; ROADMAP 'After 1.0.6' section added.

## 20 November 2026 — Cards 124 and 125, preview b304b23

- 124: the welcome screen's restore line now names saved articles, which a
  restore from there brings back with sources and settings.
- 125 (owner's screenshot 10:23): the loading indicator sat at the left on
  the Account page. Centred there and on the weather-place search with
  fillMaxWidth + wrapContentWidth; the inline ones stay beside their text.
- 1386 pass, lint clean (one test of mine cut its text short at the first
  ')\n'; fixed to read a fixed span). Board build 19.

## 20 November 2026 — Card 123, preview 6945d78

- Owner (screenshot 10:06): What to restore should start with everything
  ticked. Settings was unticked on purpose (it replaces rather than adds);
  now all three start ticked, the 'Replaces the settings on this phone'
  note stays. 1384 pass, lint clean. Board build 18.

## 20 November 2026 — Reset all settings confirmed

- Owner, in chat: reset and restore from the settings backup passed. T1
  and T2 of build 17 passed; card 68 closed. Gate left: empty feeds (T7/14).

## 20 November 2026 — Breaking news confirmed

- Owner's screenshots, 07:08 (archive/screenshots/2026-11-20-0708-…): two
  breaking-news notifications overnight, headline as title, 'Covered by 3 /
  6 of your sources, including …', about two hours apart. T2 of build 14
  passed; card 115 closed. Gate left: empty feeds (T7/14), Reset all
  settings (T2/17).

## 20 November 2026 — Release gate narrowed

- Owner: Cyberspace is an unusual feed, so its photo check (T4 of build 14)
  is optional; and a day or two of heavy testing can replace the week.
  Gate: breaking news (T2/14), empty feeds (T7/14), Reset all settings
  (T2/17). Target still 28 November, earlier if those pass.

## 19 November 2026 — Card 68, preview 8afd9fc

- `manager/backup/SettingsReset.kt`: removes every DataStore key not in
  KEEP, so each setting reads its default. KEEP = SettingsBackup's
  NOT_PORTABLE (now internal) plus welcome and tour seen, the full-text
  migration flag, learning (reset time, source affinity), pinned and hidden
  sources, category order, the weather place, the cloud and device backup
  choices, and the account key. The sync account is in SharedPreferences
  and untouched.
- Help → Reset all settings, as a StringPref with the new `confirmId`:
  ActionPreference asks with an AlertDialog first. Confirm instead of the
  card's Undo: the screen redraws as theme and layout reset, and a settings
  backup is the way back.
- Goes into 1.0.6: the trial week starts after this weekend's checks.
- Board: stale T5 of build 13 set to Not tested (replaced by T4 of build
  14). ResetSettingsTest (2, real preference store); 1383 pass, lint clean.
  Board build 17.

## 19 November 2026 — Release date

- Agreed with the owner: 1.0.6 the weekend of 28 November, after this
  weekend's last checks and a week on the preview; translations to 1.0.7
  unless ready. The owner confirmed the refresh indicator no longer jumps
  and the top Settings page behaves; those old tests were already off the
  board. Szept 179, 180 and 236 moved to 28 November.

## 19 November 2026 — Results from f580c5f

- T1 and T2 of build 16 passed (23:02): 121 closed. The owner's 'Still
  wrong' and 9to5Google screenshot (22:59-23:00, archive/screenshots) came
  from the build before; the note landed on card 115 by mistake, so 115 went
  back to To check (still waiting for real breaking news).
- 122 dropped by the owner: message boxes keep Material's inverse colours.
- 114: backup writing confirmed from the owner's files; restore unchecked
  on a phone.

## 19 November 2026 — Board: OPML uploads (119)

- The owner's backup OPML wouldn't attach on the board: the picker's accept
  list had no .opml. Board version 11 accepts .opml, .xml and .rss, sent as
  text/plain. The backup itself (archive/board-files/2026-11-19-2257-…)
  shows whisperShowInAll="false" on Cyberspace: half of T8 of build 14.

## 19 November 2026 — Results from 6a97e06; card 121, preview f580c5f

- All seven steps of build 15 passed: 117, 118 and 120 closed. Darknet is
  back and updates; IT Security Guru shows the blocks-apps card. The
  diagnostics (archive/board-files/2026-11-19-2244-…) show FreshRSS refused
  to add Darknet (400), so it lives on the phone only: the server's parser
  has the same trouble with the trailing script. Told the owner.
- 121: Preview on a source's page showed the previous source's result
  (Darknet's under Huffington Post). SourceEditViewModel is shared between
  pages; `setFeedId` now clears the preview when the source changes, an
  address edit clears it, and the fetch job is cancelled with it. Preview
  also reports `BlocksApps` for a 403. Batch7Test covers it; 1381 pass,
  lint clean. Preview f580c5f, board build 16.
- 122 (new, to decide): the owner asked whether the light message box on
  the dark theme is right. Recommended keeping Material's inverse colours.

## 19 November 2026 — Batch 7, preview 6a97e06

- **117, Darknet.** `FeedParser.parseRssAtom` now reads the body as bytes
  and, only when the strict read fails, retries with everything after the
  last `</rss>`, `</feed>` or `</rdf:RDF>` cut (`withoutTrailingJunk`, the
  bytes read as Latin-1 so positions are byte positions). Covers sync, Add
  source, discovery and the https check, which all come through here.
- **117, IT Security Guru.** A 403 means the site turns apps away.
  `SearchFeedViewModel` notes a 403 on the typed address or a declared feed
  link (not on guessed paths) and, if nothing else is found, reports
  `SearchFailure.BlocksApps`; Add source shows the explanation, the 'To
  check' line and Open feed in browser. Feeds that stopped working reads the
  feed's own history (`FeedHistory.refusesApps`: the latest fetch with a
  network was 'failed 403') and shows the same, in place of Look for the new
  address, which would be refused too. Wording: 'Its feed exists (Open
  website shows it)' became 'The feed is probably fine', since Open website
  shows the home page; told the owner on the card.
- **117, diagnostics.** `errorKind` walks the cause chain: timed out,
  address not found, connection refused, secure connection failed, not a
  readable feed; Java's own class names otherwise; never an R8 name.
  SyncResultTest's expectation moved from 'SocketTimeoutException' to
  'timed out'.
- **118.** `ui/components/Loading.kt`: `WhisperLoading`, Expressive's
  `LoadingIndicator`, 48dp or 24dp inline. All nine
  CircularProgressIndicators replaced; a test keeps them out.
- **120.** Backup, Sources, Categories and Feed library use
  `FeedSnackbarHost`. 'Backed up: your sources, settings and saved articles'
  without file names; BackupMessageTest updated (it still requires the word
  settings, the reason the names were added).
- Tests: 1380 pass (Batch7Test, 7 new), lint clean. Not run: the real-feed
  check, since the parser change only acts on a feed that already fails.
  Preview signed with the release key (D4:54…B1:64). Board: T1–T7 of build
  15, cards 117, 118, 120 to preview.

## 19 November 2026 — Results from 1c18b40, board page

- Passed: T1 (About), T3 ('Default'), T5 (home-network message on Add
  source: 73 closed), T6 (Edit and Preview: 116 closed), T10 (Sign out and
  pill buttons: 15 closed). Waiting for real events: T2 (breaking news),
  T4 (a new Cyber photo post), T7 (feeds empty three updates running);
  T8 and T9 optional, not tried.
- New: 119, the board's upload takes one file at a time. Fixed on the board
  page itself (files/work-board.html, published as version 10): the picker
  takes several, they upload in turn, and failures are counted, not fatal.
- New: 120, the backup message looks out of place. Cause: Backup, Sources,
  Categories and Library use Material's plain SnackbarHost (near-square,
  default colours) while the feed uses FeedSnackbarHost; and the message
  lists both file names over three lines. For batch 7.
- 118 set to Fix by the owner: batch 7 with 117 and 120.
- Screenshots saved to archive/screenshots/2026-11-19-22*.

## 19 November 2026 — Batch 6, preview 1c18b40

- **115** `BreakingAlert`: title is `contentTitle` (was `displayTitle`,
  which is source + " [RSS]"); at most one per update (`PER_UPDATE`); only
  the sent story is marked told, so one held back comes later.
- **113** "As in Settings" is "Default".
- **45** `MarkdownLite`: content with no HTML but `<br>` and a clear
  Markdown mark is converted in `contentHtml()` and `contentText()`, before
  the picture search. Checked on the real Cyberspace feed (its post pages
  build the text with JavaScript, so the reader used the feed's text) and
  on last batch's 12 articles: identical.
- **73** `isHomeNetworkHost`: Add source's unreachable message and a line on
  Feeds that stopped working for feeds at home addresses.
- **111** A parsed feed with no items counts as a failure
  (`Parsed.itemsInFeed`), so it reaches Feeds that stopped working.
- **112** The step times use the wall clock, the eight-minute budget counts
  awake time only; an out-of-time run now adds "phone asleep for N min of
  it". Sign-in has a 60 s `callTimeout`.
- **114** OPML backups write `whisperFullText`, `whisperShowInAll`,
  `whisperEnabled`, `whisperOpenMode` when not default; the parser reads
  them back; existing sources keep their own settings on restore.
- **116** `NavRoute.SourceEdit`; "Edit this source" on broken feeds;
  "Preview this feed" on the source page (name, count, three headlines, or
  why not).
- **15** Sign out outlined in the normal colour (reverses review 63's red,
  which the dark theme draws pink); dialog buttons are pills.
- Owner's diagnostics at 21:34 (still on 3896ca3, archive/board-files):
  no crashes or errors; 126 sources (Darknet unsubscribed after the Open
  website test); 2 failing (IT Security Guru 403, Coolsmartphone timeouts);
  Ireland: BreakingNews still empty every fetch, which 1c18b40 will list.
- Owner's checks, evening: Darknet's feed is valid but WP Rocket appends a
  `<script>` after `</rss>`, so the strict parser rejects it (303 failures;
  Add source "No feed found"); "ah0" was an R8-shrunk exception name. IT
  Security Guru's feed is valid and current but the site refuses Whisper
  (403); the card wrongly said "stopped publishing". New sheet 117 (ignore
  anything after the closing tag; honest 403 wording; readable error names)
  and 118 (seven old-style spinners to Expressive's loading indicator).
  Owner put 117 on Fix: the user must be told plainly when a site blocks
  reading apps ("This site blocks reading apps. Its feed exists…"), on Feeds
  that stopped working and on Add source; never pretend to be a browser.
  "Open articles: Browser" can't help (the block stops the headlines
  arriving). An email to IT Security Guru is drafted on the card, not sent.
- Board and Szept (owner): the owner works on the board only; Szept
  entries renamed "Board N – …", one per open card (eight new "test"
  entries for the cards in preview), and Szept numbers removed from 17
  open cards' replies. CLAUDE.md and HANDOVER say so.
- Tests 1373 passing (`Batch6Test`, 12). `CountWordsTest` caught a plain
  "%d articles" (now a plural); `ReviewFixesTest` updated for Sign out.
  Preview signed; steps T1-T10 (`t014-*`). Not tested on a phone yet.

## 19 November 2026 — Results from 3896ca3 (batches 4 and 5)

- Passed and closed: batch 4 all ten steps (20, 23, 25, 26, 29, 33-39, 57,
  60, 61, 107, 110 done), and 52, 71, 80, 113 from batch 5.
- T5 (45) problem: Cyberspace (cyberspace.online) sends **Markdown** as
  its content, so its picture is a Markdown image line and its ASCII art a
  code block that loses its spacing. Batch 6: convert Markdown content.
- 115 (owner's card): breaking notifications work (three came), but the
  title was the source ("Irish Independent [RSS]"): `BreakingAlert` used
  `FeedItem.displayTitle`, which is source + " [RSS]", instead of
  `contentTitle`. All three came in one update. Batch 6: headline as
  title, at most one per update.
- 73: tested on Add source, which the change did not cover (account
  sign-in only). Batch 6 extends it. 76 and 73 stay in preview (T6, T8 not
  tested).
- T3 (113): "As in Settings" too long for its button: becomes "Default".
- T7 note: Edit on broken feeds and a feed preview: new sheet 116, to
  decide (Szept 240). 111 and 112: owner picked Fix.
- Owner, later the same evening: Fix on 114, 115 and 116, so batch 6 is 45,
  73, 111, 112, 113 label, 114, 115, 116 (Szept 241). Testing of 73 and 76
  waits until there's a home address or a French source (Szept 242); 76
  set to Later, 73 back to Fix for the Add source part.

## 19 November 2026 — Batch 5, preview 3896ca3

Started the same day as batch 4, at the owner's request, so the owner tests
one build while the next is made. 3896ca3 includes batch 4.

- **52** Reader: the second line keeps author and day; the time is only in
  the line above (`ofLocalizedDate(FULL)`).
- **73** A failed connection to a home-network address (private IPv4,
  loopback, link-local, `.local`/`.lan`/`.home.arpa`/`.internal`, read from
  OkHttp's own message) is `AccountProblem.HOME_NETWORK`: "Android blocks
  apps from reaching addresses on your home network…". No permission.
- **113** Per-source "Open articles": As in Settings / Reader / Browser.
  `Feed.openMode` (database 31, `MIGRATION_30_31`, schema exported;
  `MigrationTest` upgrades every version with its rows).
  `FeedPreferences.openModeFor` at all three places an article opens.
  Found: backups (OPML) carry no per-source settings at all: sheet 114.
- **80** `BreakingAlert`: after a successful sync, if "Notify me of breaking
  news" is on (off by default, Your feed), one notification per new
  breaking cluster from News sources, at most 3 a day; the first run after
  switching on only notes what is there; switching off forgets.
- **45** Parser: an enclosure with no or a generic type but an image address
  counts; pictures inside `media:group` count; `.avif`; a title that is only
  a file name becomes the post's first line. The feed itself is still
  unknown here.
- **76** `dropRelatedBlocks`: by heading in every app language plus English,
  with the link list after it; "Lire aussi : <link>"; an opening paragraph
  that is only a link to the same site.
- **71** Pictures: stated width/height give the shape (`aspectRatio`, `Fit`);
  decoded up to 4x as tall as wide (a square limit squeezed tall ones;
  width-only would have let an infographic decode whole, which
  `ImageSizingTest` caught); `dropEmptySlots` removes empty containers.
- **Real-feed check** on 12 articles (BFMTV, Le Figaro, Bored Panda): the
  same text and 157 pictures before and after; none of today's articles had
  a related block, so the rules are covered by unit tests built on the
  reported shapes.
- **Tests** 1361 passing (`Batch5Test`, 12); `AccountProblemTest` example
  moved to a public address, `ImageSizingTest` accepts the new bound.
  Preview signed with the release key; steps T1-T8 (`t013-*`) on the sheet.
  Not tested on a phone yet.

## 19 November 2026 — Batch 4, preview f801bc3

- **About** (33-35, 37-39, 61): Contact us is an intro line and three
  joined icon rows (Email us, Report a bug on GitHub, Report a problem,
  which opens Help's Report a problem page); the Website row went (the
  header pill has it). Version line "Version 1.0.5-f801bc3 (build 6)".
  Ko-fi button "Chip in on Ko-fi"; "Built on" heading. 37 was already in
  the code (same container colour), only needed testing.
- **Sources** (25, 26, 29, 60): push-pin for Pin everywhere (sources, the
  article's Pinned line and its menu); each list is one joined group (the
  list has no spacing of its own now; rows sit SEGMENT_GAP apart, other
  items carry 4dp above and below); bottom padding 104 (88 only touched the
  + button). New Sort button (Sort by, Order, ticks) and the ⋮ menu as a
  shared `GroupedMenu` in three named groups with icons; pencil for Rename
  in Manage categories.
- **Small** (20, 23, 107, 110): "Launcher page background"; file icons on
  Import from bookmarks; Open website on Feeds that stopped working (the
  feed's own site home page, `siteHome`); Your reading moved to Your data
  with a "Forget what Whisper has learned" row that opens the Learned page.
- **Help → Launcher page** (57): Lawnchair merged PR 7399 into 16-dev on
  24 October (nightlies only; latest named release 15 Beta 3, April). The
  page leads with "On Lawnchair 16, or a nightly from 24 October 2026 on",
  then the four debug steps under "On an older Lawnchair".
- **Icons**: EnvelopeSimple, PushPin, FileArrowDown/Up, LinkBreak,
  LockSimpleOpen, PencilSimple, Copy, from @phosphor-icons/core 2.1.1 (MIT,
  already credited), drawn with `addPathNodes`.
- **Tests**: 1349 passing (new `Batch4Test`); two older source checks
  updated to follow the change (the 104dp gap; the Email row's new shape).
  Lint clean. Preview signed with the release key (fingerprint checked),
  sent to the owner; steps T1-T10 on the sheet; Szept 154-167, 169, 229
  done, 230 is the owner's testing.
- Not tested on a phone yet.
- A user asked by email (19 November) to open one paid newsletter in the
  browser and everything else in the reader. Owner chose per-source
  Reader/Browser on the existing two, no Custom Tab: sheet 113, batch 5,
  Szept 234. Chrome engagement signals noted as someday (Szept 235).
- Owner's diagnostics from 43ee0b0 (19 November, archive/board-files):
  new sheet 111 (seven feeds return under 1 KB and no articles every time,
  never flagged; Szept 232) and 112 (the 08:11 sync "reached sign-in
  3788s"; Szept 233). Darknet – Hacking Tools has failed 303 times since
  2 November: a ready case for Open website (T7).

## 18 November 2026 — Results from 43ee0b0

- T1-T5 pass: install and version, Material's refresh indicator (106), the
  Feed layout dialog lined up (88), the Learned groups (91), Joho no Todai in
  the library (108). All four issues closed; Szept 150-152 done.
- T6 (Read today) and T7 (TalkBack) not tested. T7 checked in code instead:
  `selectableGroup()` and `Role.RadioButton` in
  `StringSelectionPrefDialogUI.kt`, which is what TalkBack reads.
- 19 November: 102 dropped (board features work), 109 closed (picture tap
  works after typing a note); Szept 148-149 done. The T4 question became
  sheet 110 (Szept 229): move 'Your reading' to Your data, keep Learned in
  Your feed, add a Forget line under Your data. Owner to decide.
- Was open from the owner: whether to add a 'Forget what Whisper has
  learned' line under Your data (asked on T4, no answer yet).

## 18 November 2026 — Preview 43ee0b0 (218a5b9 was never installed)

- About on the owner's phone showed d3ea2e7: the 218a5b9 APK never got
  installed, so its T2-T4 results were for the old build. Those steps set to
  untested; 106 reopened to preview.
- Preview 43ee0b0 built from main with the release key (fingerprint checked,
  `.dev` id, versionCode 6): the three fixes (106, 88, 91), Joho no Todai in
  the library (108), and the Weblate plurals move (no visible change).
  Tests 1343 passing, lint clean. Steps T1-T5 (`t011-*`) on the sheet.
- Board cleared down: the 88 test and checklist cards of builds 272fae7 to
  218a5b9 moved to the `archive` collection (not deleted). Every issue behind
  them is done, or replaced by 106 and in this build. Two never-done checks
  carried into this build as T6 (Read today) and T7 (TalkBack, optional).
  Build tests now shows only 43ee0b0, T1-T7; To check shows 88, 91, 106, 108.
- The first build failed fetching a lint dependency from Maven Central (the
  file was there a minute later); a retry built. If it recurs, retry.

## 18 November 2026 — Results from 218a5b9 (review sheet)

- T1 install and T2 refresh indicator: Pass. T3 feed layout dialog: Problem
  (radios indented and centred on the description). T4 Learned groups:
  question, answered, back to open. t009-07: asked again, already answered,
  back to open.
- All three screenshots show the previous build's behaviour (indicator on
  the 'Today' line, no 'Learned' heading, the old dialog), though the code
  for each is in 218a5b9. Likely the phone is still on d3ea2e7: owner to
  check the version in About, then reinstall and redo T2-T4.
- Owner's question (T4): move What Whisper has learned and Your reading to
  Your data? Claude's advice: keep them under Your feed (Learned steers the
  feed), and perhaps add a 'Forget what Whisper has learned' line under
  Your data. Waiting for the owner's choice.

## 16 November 2026 — Weblate: French added; plurals moved into strings.xml

- **Owner, on Weblate:** French added to the App component (687 strings).
  Libre hosting approval requested ("Pending approval"); trial ends
  22 November, Alice asked to extend it if the review needs longer.
- **Problem:** the Plurals component said "No file mask matches" and refused
  French, because only `values/plurals.xml` existed, no `values-*/plurals.xml`.
- **Fix (repo):** the two plurals in `values/plurals.xml`
  (`mark_read_unread`, `articles_marked_read`) moved to the end of
  `values/strings.xml`, and `plurals.xml` deleted. Android merges every file
  in `values/`, so names, English wording and the app are unchanged. Why not
  an empty `values-fr/plurals.xml`: every later language would need one too,
  or Weblate refuses it again. Undo: `git revert` this commit.
- **Owner, same evening:** pressed Update on App, which then showed
  693 strings to translate (655 + 38 plurals; 9 marked not translatable), then deleted the Plurals component. Licence on Weblate corrected from GPL-3.0-only to GPL-3.0-or-later at project level (App inherits it), to match `LICENSE` and the README. After approval: push to GitHub, repository hooks, unlock.

## 15 November 2026 — Preview 218a5b9; everything open into Szept

- Preview 218a5b9 built and sent: Material's refresh indicator (106), the
  layout dialog aligned (88), the Learned groups (91). Steps T1-T4 on the
  sheet. 87 merged into 106.
- Review board page: the redraw held back while typing waits 400 ms after
  the note box loses focus, so a tap on "+ Add a file" is no longer lost
  (sheet 109). The white gap after the keyboard is claude.ai's frame.
- Owner's CLAUDE.md Szept rules merged from GitHub. Szept now holds every
  open sheet card (Szept 146-178, 182-191; 108 is 131), the rest of 1.0.6
  (release 179, the email to the French user 180, the website 181) and
  the long-term ROADMAP items (192-197), each with priority, slot and plan.
  Each card's reply carries its Szept number. 130 and 131 moved to project
  Whisper.

## 12 November 2026 — Results from d3ea2e7

- Passed: T1 (database 29 -> 30 kept everything), T4, T5, T6, T8, T9.
  Items 99, 103, 104, 105 done.
- T3 (88): radios sat about 22dp in from the dialog title and centred on
  two- and three-line choices. Now Material's list in a dialog: the row is
  `selectable(role = RadioButton)`, the radio draws without its own 48dp
  (`LocalMinimumInteractiveComponentSize` 0) so it lines up with the title,
  and it is top-aligned with the name when there is a description.
- T7 (91): after a reset, zeros looked like never-learned sources. The
  Learned page now splits into "Learned" and a folded "Nothing learned yet"
  group of one-line rows with no bin and no selection.
- T6 question answered: the snackbar's inverse colours are Material's.
- 1343 tests.

## 12 November 2026 — Refresh indicator, Material's way (sheet 106)

Owner's screenshots of d3ea2e7 against Harmonic: the indicator sat on the
"Today" line, clipped the first card, and while the list scrolled it lagged
behind the line, jumped, then parked over the first headline. The cause was
the design, not a bug: it tracked the line's window position with a spring.
Owner: follow Material's guidelines. `RefreshIndicator` is now
`PullToRefreshDefaults.LoadingIndicator(isRefreshing = refreshing)` at
TopCenter, which Material holds at its fixed spot for the update. The anchor
(`RefreshAnchor`, `LocalRefreshAnchor`, `restingTop`) is gone;
`rememberRefreshing` still holds the state from pull to the end of the
update. 1341 tests (one test of the removed geometry dropped).

## 12 November 2026 — Batch for the next preview

- 88: `SingleSelectionListItem` uses Settings row styles (titleMedium,
  bodyMedium) instead of bold 16sp; 10dp vertical padding with a
  description.
- 103: `FeedNameSearch.addressFrom`: text with spaces that joins into a
  host ending in a 2-6 letter domain ending is an address, lowercased.
  "Mr. Potato Head" stays a name.
- T4 of f6632d3: "Saved under" in a primaryContainer band with a 20dp tick
  and one small pulse; Done a filled Button.
- 105: `repairDoubleEncoding` on the image client: on a 4xx, if the URL
  holds `%25XX`, one retry with it decoded once (`undoubleEncoded`). No
  og:image fallback yet: the retry fixes Quartz, and the fallback would need
  a page fetch per broken picture.
- 91: database 29 -> 30, `Feeds.learnedResetAt`. Engagement and dismissal
  queries leave out rows before a source's own reset (statistics pass
  `allHistory`). Learned page: faded line, bin whenever `hasLearned`,
  long-press multi-select with Forget in the bar.
- 1342 tests (`Batch1112Test`, `FeedNameSearchTest`).

## 12 November 2026 — Results from f6632d3

Owner tested f6632d3; no build made (owner: read, don't build yet).

- Passed: T1 install, T3 offline when adding a source, T4 filing (with a
  note that "Saved under" needs more emphasis). Items 89 and 90 done.
- T2 refresh still bounced: expected, the fix (31e0d71) came after this
  build. Item 87 back to Fix.
- New: 103 ("the verge.com" searched as a name because of the space) and
  104 (the Sources undo bar never closes). Replies, proposals and the
  owner's screenshots are on the cards; screenshots also in
  `archive/screenshots/` (gitignored).
- 104 fixed on main (owner agreed): the removed source was kept for Undo
  until its message ended, and leaving Sources cut the message short, so
  it came back on every visit (and its Undo could add a re-added source a
  second time). `SourceListPage` now forgets single and bulk removals when
  the screen is disposed. Side effect: a rotation or fold also ends the
  Undo. Not built yet.
- Board, later the same day: 63c9895 T1 and T3 passed; T4 (feed layout
  dialog) a problem, bold names too heavy for the rest of the app, so 88
  back to Fix (names to regular weight, as Settings rows). 103 set to Fix
  by the owner. 98 done (owner's GitHub settings, not checked from here).
  The owner's upload on 102 works both ways.
- 105: two Quartz articles showed the wave placeholder. Their feed's
  `media:thumbnail` addresses double-encode a space (`Michael%2520Burry.jpg`);
  qz.com's Cloudflare resizer answers 403 to that and 200 to `%20`. Checked
  with curl. Feed's fault; a repair is proposed on the sheet.

## 11 November 2026 — Review board upgrade

The review sheet got the four features added to the owner's Homelab board
the same day. Only the page changed; no record was touched (176 before and
after, same versions). Source: `files/work-board.html`.

- Attachments on every card: `assets` capability beside `db`; the file goes
  to the asset store and the card's `files` list keeps the pointer.
- One `onSnapshot` subscription in `subscribe()`, re-run after 30 s hidden
  and retried three times (4, 8, 12 s) on error.
- "Updated HH:MM" under the counters.
- A Question button on all four button sets (`status: "question"`). This
  board has more states than Homelab's, so the page also writes
  `askedFrom`, the status the card had, and that is where it goes back to.
- The card now shows `reply`, which this board did not have.
- Checked with a mocked `window.claude` in headless Chromium; the real
  upload button is the owner's to try, on card 102.
- Rules for using it are in `docs/HANDOVER.md`. `archive/` is gitignored.

## 9 November 2026 — Security audit

Covered: repo and full history, manifest and network config, the
security-sensitive code, the build and Actions, the container, and the built
APK (f6632d3). No secret in the tree, the history or the APK. Sheet items
92-101.

- 100: `ui-tooling` was `implementation`, so every build shipped Compose's
  exported `PreviewActivity`. Now `debugImplementation`; `@Preview` comes
  from `ui-tooling-preview`, which stays. Checked in the merged preview
  manifest.
- 101: Rome's two stray jars excluded from packaging; the `fileTree` over
  `app/libs` removed.
- 92: a reader's first name replaced with "a reader" in CHANGELOG, ROADMAP
  and HANDOVER (CHANGELOG is also shown inside the app). History not
  rewritten.
- 99: `<ownerEmail>` stripped from the 34 library OPML files. Two podcast
  descriptions still carry their publishers' own contact addresses, which
  is their text.
- 96: `distributionSha256Sum` in the Gradle wrapper, matching both
  services.gradle.org and the local download.
- 95: workflow renamed Whisper CI, read-only token, JDK 21, runs tests and
  lint, actions pinned to commits, credentials not persisted.
- 97: `SECURITY.md` and `.github/dependabot.yml` (Gradle and Actions,
  weekly). Dependabot opens pull requests; nothing merges by itself.
- `SecurityAuditTest` pins all of the above.
- Owner's: 93 (unused cloud keys in the environment), 94 (signing key
  backup), 97 (repo security switches), 98 (2FA, rulesets on main and tags).

## 9 November 2026 — Add source feedback; one refresh indicator

- Add source, offline: `whisperHasNetwork` is asked before searching
  ("You're offline"). If the typed address itself gets no answer
  (`isNetworkFailure`: unknown host, refused, timeout, socket), the search
  stops with "Couldn't reach 076.io" instead of probing the other stages
  and ending in "No feed found". `SearchResult.failure` carries which.
- Add source, filing: the heading turns into "Saved under X" with a tick,
  the chosen chip shows Material's tick, a typed category clears its box,
  and Done goes back. The added card is no longer a disabled `Card`, which
  greyed the feed's name. The search box uses outlined colours: a filled
  background inside an outline is cut away behind the label, which showed
  as a pale box.
- Refresh: one indicator (`RefreshIndicator` in
  `PullToRefreshLazyColumn.kt`), drawn by hand rather than Material's,
  which returns to its threshold on release. It follows the pull, then
  animates from where it was let go to `restingTop`: centred on the
  freshness line, which reports its window position through
  `LocalRefreshAnchor`; near the top when that line is off screen. Fades
  when the update ends. The small one in the line is gone; the line says
  "Updating…".
- Refresh bounce (owner, same day): after a pull, `rememberRefreshing`
  held the indicator for a flat 600ms; enqueued sync work can take longer
  to report running, so it faded, reset to the top, and dropped in again.
  Now held until the update it started finishes (5s grace for one that
  never reports). Fading and following the finger are one effect, so a
  finished update fades in place before the finger is followed again.

## 5 November 2026 — Review batch 3: the feed

- Preview e15f364 passed (all 11 fixes through To check).
- Chips (11): the underline is gone; a selected category chip shows
  Material's tick (`leadingIcon`), still a sign that does not depend on
  telling two shades apart.
- Refresh (47): `PullToRefreshDefaults.LoadingIndicator` in both feeds.
  Back to top (51): `SmallFloatingActionButton` in both.
- Veil (48): no code draws one; asked the owner for a screenshot. Possibly
  cards dimming as a sync brings in reads from the server.
- Byline (40, 43, 46): `ArticleMeta` is a hand-made `Layout`. `bylinePlan`
  drops the category, then the long age (clock and reading time) for
  `ageCompact`, until the name has 48dp or all it needs. The name's width
  is the ellipsised text's real width (`TextMeasurer`, `getLineRight`), so
  the age follows it. Mark, category and name each carry the identity's
  tap; the age does not.
- Text-only tiles (44): `MIN_WIDTH_FOR_ACTIONS_BESIDE_META` 220 → 140dp,
  so a two-lane tile keeps its buttons on the byline.
- Mosaic (42): `levelBeforeLeads` in `FeedLevel.kt`, applied by
  `buildFeedFrame` when Mosaic is in date order. Estimated heights
  (`mosaicTileHeight`); before each lead, up to two tiles from the next four
  move up when that brings the lanes 16dp closer, never across a day or a
  pin.
- Read today (14): from `ReadingTally` (`seenOn(day)`), which already
  counted only reads on the phone; `markAllRead` no longer tallies, and its
  Undo no longer untallies (`unmarkRead(tallied = false)`). No database
  change needed.
- Card menu (58): `DropdownMenuPopup` with three `DropdownMenuGroup`s and
  the source's name as a label. Long press (67): `cardClick` on all four
  card shapes, `LocalCardLongPress`, and `ArticlePreviewDialog`.
- Fold (65): no foldable or emulator here, so `FoldUnfoldTest` checks where
  the state lives. Found and fixed: `searching` and `showBookmarks` were
  plain `remember`, so a fold closed the search while the view model's
  query still filtered the feed.
- After the owner's screenshots: the byline was centred in picture cards
  (a `Layout` narrower than its minimum width is centred; now it takes
  the minimum). Then names were cut to keep the clock time, and a cut
  name measured at one width and drawn at a narrower one left a gap.
  `bylinePlan` now keeps the whole name before the long age; the name is
  measured at the width its cut was worked out for and advanced by its
  drawn width. Items 82–84 added to the sheet (www., back-to-top over a
  card's menu, text-only tiles).
- Follow-up (6 November, owner's screenshots and answers): the menu's
  source name, with its icon, heads the first group (it floated above the
  menu). Back to top shows only while scrolling up (`rememberScrollingUp`,
  item 83). Text-only Mosaic tiles keep only ⋮, with Save in it
  (`LocalMenuOffersSave`), a saved mark, a ⋮ that counts one line high,
  and three-line headlines (84). Feed layout choices carry a description
  (`StringSelectionPref.descriptions`, 86). Veil (48) dropped by the owner.
- `ReviewBatch3Test`, `FoldUnfoldTest`; `ScreenshotGlitchesTest`,
  `SourceFilterTest`, `CardReadabilityTest`, `FeedDaysTest`, `FocusAnchorTest` and `ImageSizingTest` follow. 1316 tests, 1 skipped. Every Mosaic tile then went to ⋮ only (owner, 6 November); the update indicator moved to the freshness line, centred (7 November, item 87). 8 November, from the owner's tests: the pulled indicator stayed at its pulled place for the whole update (Material holds it there while refreshing), so it now draws nothing once refreshing; the line's indicator is centred on the window (`centreShift`), not on the slot beside "Today"; Save in the menu and the saved mark use `ic_whisper_save`; the layout dialog's names are `onSurface`, their descriptions `bodySmall`. 1317 tests.

## 4 November 2026 — Review batch 2: filter sheet, title settle, stretch

- Preview a58a672 tested: 7 of 8 steps passed; T6 (title let go halfway)
  failed. Category sync confirmed on device: a feed moved off "Test" stayed
  in Tech through two FreshRSS syncs. The one reappearance came from the
  first sync on this build, which takes the server's folders by design.
- Title settle (t003-06, item 50): Material settles the bar only at the end
  of a fling, and a fling a finger stops, or a drag that ends without one,
  never gets there. `ViewWithActionBar` now watches the bar's offset and
  whether a finger is down (`PointerEventPass.Initial`, nothing consumed);
  120 ms after the bar stops between open and closed with no finger down it
  animates to the nearer end (`settleTarget`) on the theme's spatial spring.
- Stretch on short pages (81): Compose gives no overscroll to a list with
  nothing to scroll. `pageScrolling` (was `collapseOnlyWhenScrolling`, now
  for small titles too) hands what the bar leaves of a drag or fling to an
  `OverscrollEffect` drawn on the content, until the content shows it
  scrolls; after that the list's own stretch works as before. Outside the
  bar's connection's turn, so a pull-down still opens the title first.
- Filter sheet (1–9): content composed as soon as the sheet's target is
  open, not once it arrives (the empty strip that rose first); drag handle
  padded by the status bar; sheet padded by the navigation bar. Blocks are
  `surfaceContainerHighest` open or closed, content at 16dp, explanatory
  lines at `BlockTextIndent` under the heading text. Sort: `ChoiceButtons`
  twice (Date/Title/Source, then direction worded per sort; "Chronological"
  is now "Date"). Mark as read: one connected row of tonal buttons with the
  count under each; `markReadChoices` drops a range that marks the same as
  the next wider one, or nothing, and always keeps All.
- FreshRSS default folder (75): `isCatchAllFolder` knows the name in every
  language FreshRSS ships, from its `gen.short.default_category`.
- `ReviewBatch2Test`, `TitleSettleTest`; `ReviewBatch1Test` and
  `AccountHistoryTest` follow. 1292 tests, 1 skipped.

## 29 October 2026 — Batch 1 follow-up: category sync, titles, red buttons

- Batch 1 tested: 16 of 17 steps passed at once, T3 on clarification.
- Category sync both ways (59): `planFolders` in `SyncOutbox.kt` decides
  per feed from the phone's categories, the server's, and both as of the
  last sync (`GoogleReaderState.folders`, new pref `folders_v1`). Changed
  on one side wins; both, the phone. First sight takes the server's unless
  it has none. Sent with `subscription/edit`, removals before additions,
  because FreshRSS keeps one folder per feed and a removal moves the feed
  to its default folder. Unsent changes are remembered as before and tried
  again. A feed with several categories ends in one folder on FreshRSS.
- Large titles (64): `TwoRowsTopAppBar` with the large flexible bar's
  heights and `displaySmall`; expanded title wraps to two lines (the bar
  grows to fit, its measure policy takes the taller of height and title),
  collapsed stays one. Replaces batch 1's auto-size.
- Destructive buttons (63): `ActionButton(destructive)` draws the red
  outline `OutlinedActionButton` uses, so Sign out matches Delete.
- Item 50: Material already snaps the bar open or closed after a drag, and
  `collapseOnlyWhenScrolling` passes post-fling through; left to a test step.
- `FolderSyncTest`; `AccountHistoryTest`, `SettingsStage2Test` and
  `ReviewBatch1Test` follow. 1282 passing, 1 skipped, lint no errors.

## 27 October 2026 — Lawnchair accepted the whitelist request

- The owner heard from Lawnchair: Whisper is on the feed whitelist
  ([#7399](https://github.com/LawnchairLauncher/lawnchair/pull/7399)).
  Which Lawnchair version first carries it is still to confirm.
- Once that version is out, Help → Launcher page should lead with "choose
  Whisper as the feed" and keep the `/lawnchairdebug` steps only for older
  versions. Planned for review batch 4, before the translations.

## 27 October 2026 — Review batch 1: foundations (for 1.0.6)

- Title bars (53, 50, 22): `ViewWithActionBar` wraps the large bar's
  nested scroll in `collapseOnlyWhenScrolling`. Pre-scroll and pre-fling
  reach the bar only once the content has consumed a scroll itself, so a
  page that fits never collapses, and so never sits half collapsed with a
  faded title. Long large titles auto-size down to 22sp before ellipsis.
- Alignment (36, 28, 32): `PreferenceGroupHeading(insideMargin = true)` on
  About and Sources, whose lists already carry the 16dp margin; headings
  were 16dp too far in. Contributor photos 40dp, the size of every row
  icon, so names start on the same column.
- Shapes and colour (15, 27, 16, 21, 37): `categoryChipColors()` and
  `categoryChipShape` shared by the feed's row and Sources; chips select
  in `secondaryContainer`, `ActionButton(positive)` is filled `primary`.
  `WhisperShapes.small` 12 → 8dp (Expressive's chip corner), so About's
  link chips are no longer near-pills. List cards on About, Broken, Library,
  Insecure, Suggestions, Statistics and Glance location moved from
  `surfaceContainer` to `surfaceContainerHighest`, the settings rows' colour.
- About header full width (31) and its link chips centred inside a 16dp
  margin (30), since the file was open.
- Sources already pads the list's end by the + button's height (88dp), so
  item 26 looks like the button passing over rows mid-scroll rather than
  covering the last one. To check on the phone before fixing anything.
- `ReviewBatch1Test`; `ImageSizingTest` follows the 40dp photo. 1275
  passing, 1 skipped, lint no errors.

## 27 October 2026 — Review sheet for preview testing

- Testing 272fae7 and 2e413f0 on the Pixel gave 55 issues, too many to
  track in chat. They live in a review sheet now (link and fields in
  `docs/HANDOVER.md`): the owner picks Fix, Later or Drop, answers
  questions there, and confirms each fix once it reaches a preview.
- Checklist for the two builds: everything passed except the old refresh
  indicator (issue 47) and Settings' title collapsing when the page fits
  (issue 53). TalkBack not tested.

## 26 October 2026 — MaterialExpressiveTheme (for 1.0.6)

- `AppTheme` and the overlay's `setFeedContent` are `MaterialExpressiveTheme`
  now, both with `WhisperMotion` (`MotionScheme.expressive()`, in
  `Theme.kt`). Colours, type and `WhisperShapes` passed as before.
- The reader nests a `MaterialTheme` for text size. It now passes
  `motionScheme = MaterialTheme.motionScheme` explicitly, so the article
  never falls back to the standard scheme.
- `PictureViewer`'s hand-written spring (0.8, 380) is now
  `MaterialTheme.motionScheme.defaultSpatialSpec()`, which is the same
  spring, as its comment said it would become.
- `ExpressiveThemeTest`; `PictureViewerTest` follows. 1270 passing, 1
  skipped (RealFeedReport), lint no errors. Three older lint warnings in
  `PictureViewer` (`Configuration.screenHeightDp`) are untouched.
- **To check on the Pixel:** buttons, switches and progress indicators
  across the app and the launcher panel, and the card geometry in the
  panel, which was tuned by eye. No screenshot tests cover these.

## 25 October 2026 — Stage 2, Settings (for 1.0.6)

- `ViewWithActionBar` large titles: `LargeFlexibleTopAppBar` (Expressive),
  with the subtitle in its own slot. Smaller expanded height than
  `LargeTopAppBar`, which should also stop the top Settings page scrolling
  when it fits: to be checked on the Pixel.
- Segmented list: `groupItemShape` inner corners 4dp (`SEGMENT_INNER`, was
  the theme's 8dp extraSmall) and a 2dp gap (`SEGMENT_GAP`, was 4dp).
- Margins: About (8 plus some cards' own 8, now 16 for all), Sources (8 →
  16), the filter sheet (8 → 16). Add source was already 16 in effect.
- `StringSelectionPref.buttons`: short labels by value. Set, the row draws
  a connected button group (`ChoiceButtons`: `ToggleButton` with
  `ButtonGroupDefaults.connected*ButtonShapes`, radio semantics) instead of
  opening a dialog. On Theme, Font, Article times, Article text size, Open
  articles in, Read articles. Feed layout (five) keeps the dialog.
- One dialog system: the choice dialog is Material's `AlertDialog`;
  `BaseDialog` deleted.
- `SettingsStage2Test`. 1268 passing, lint clean.

## 25 October 2026 — SemiBold headlines; pictures in List

- SemiBold approved by the owner after a side-by-side trial and pushed.
  It went to main before the full suite ran: two source tests still
  expected Medium and failed on main for one commit (ca25e77a). Fixed in
  the next commit. Run the full suite before every push to main, trial or
  not.
- List with pictures: first built as a switch (`listPictures`), then, at
  the owner's word ("keep it simple"), a fifth Feed layout instead,
  `LAYOUT_LIST_PICTURES`. The switch is gone. Same row as List plus a 56dp
  picture at the right (`LIST_PICTURE_SIZE`); plain List loads nothing.
- 1263 passing, lint clean.

## 25 October 2026 — Support: Ko-fi renamed, GitHub Sponsors submitted

- Ko-fi is ko-fi.com/nyancatlabs now (was defsix): README, FUNDING.yml and
  About updated. 1.0.5 as released still links the old name until 1.0.6.
- GitHub Sponsors set up by the owner on the Nyancat-Labs organisation:
  Stripe connected, profile written (logo for light and dark mode), tiers
  $2, $5 and $10 monthly, $5 one-time, custom amounts on. No rewards on any
  tier, as decided. Submitted 25 October; pending GitHub's review.
- **When approved:** `github: Nyancat-Labs` in FUNDING.yml, Sponsors in the
  README's Support section, a second link on About's Support card. Not
  before: the sponsors page does not exist until it is published.
- The README has the Ko-fi button under the store badges. About's header
  keeps Whisper's own links; Neo Feed and HomeFeeder are rows under "Built
  on…". Not interested has a thumbs-down. `docs/EXPRESSIVE.md`, the
  design brief, waits on four owner's calls.

## 25 October 2026 — A crash, button tones, the rest of the icons

- Crash on the Pixel (Android 17): back over the feed while the filter
  sheet settled. `AnchoredDraggableState was not initialized correctly`:
  with `sheetPeekHeight = 0.dp` Material 3 1.5 builds only Hidden and
  Expanded anchors, but `rememberBottomSheetScaffoldState()` starts at
  PartiallyExpanded and the back handler asked for it again. Now starts
  Hidden (`skipHiddenState = false`) and closes with `hide()`. A
  regression from the 21 October library switch.
- Buttons: `positive = false` meant tertiary, the theme's pink. Now
  neutral (outline border, secondary container) and a new `destructive`
  (error colour) on Delete source, Reset all learned, Sign out, Forget and
  Delete broken feed. Reset and Dismiss stay neutral.
- Icons from the owner's pass: Moon (pure black), TextAa (font), CircleHalf
  (transparency), Prohibit (blocked words), CloudSun and MapPin (glance),
  ListChecks (mark read on scroll), SpeakerHigh (volume keys), Stack (items
  per source), UserCircle (account), Folder (backup); the feed's filter
  button SlidersHorizontal, filled while active. Copyleft kept on the
  licence row: it is the right mark for the GPL.
- 1262 passing, lint clean.

## 25 October 2026 — Owner's review of the 21 October preview

- Reviewed side by side on two phones. Nine items; all built today.
- About: `DocumentPages.kt` replaces the two WebView pages. The build copies
  CHANGELOG.md and LICENSE into the assets (`copyDocsToAssets`, registered
  through `androidComponents` as a generated assets directory), so there is
  no second copy to keep. The old `changelog.htm`, `license.htm` and both
  stylesheets are deleted, and with them the last JavaScript in the app.
  The licence page's "Author: Saul Henriquez" and Neo Feed source link
  were the fork's leftovers; the GPL asks for our source.
- Feed: the empty state resets the top app bar's offset (app and panel).
  The app's snackbar moved out of the Scaffold slot, which stacks it above
  the FAB, into a Box over the feed.
- Backup: `BackupStore.Part`, `restoreFolder(uri, parts)`,
  `exportSources(uri)`; the page regrouped, three restore buttons to one
  dialog. `BackupStore`'s file-name constants are public now.
- Icons: seven Phosphor regular icons added (Folder, Archive, Lifebuoy, Rss,
  Newspaper, ArrowsClockwise, UploadSimple), from @phosphor-icons/core 2.1.1.
- Read fade: `READ_ALPHA` 0.80 on the card, `PICTURE_FADE` 0.62 on its
  pictures through `LocalPictureFade`. 0.75 fails 4.5:1 for light-theme
  summaries (4.16); `ReadFadeTest` checks light, dark and black.
- Splash `MINIMUM_MS` 700 → 300.
- 3 new test files (`DocumentsTest`, `ReadFadeTest`, `ReviewFixesTest`).
  1259 passing, lint clean.
- **To try:** About → Licence and Changelog; Backup and Restore → Choose
  what to restore; a read card in the light theme; an empty category after
  scrolling down; Not interested → Undo.

## 25 October 2026 — Settings order; finding a feed by name

- Owner's notes on stage 1: Settings in a more logical order; a Sources
  search for a feed you don't have ends in an empty screen, and + forgets
  what was typed.
- Order checked against Android's settings guidelines and Material ("most
  frequently used at the top"): Appearance, Sources, Your feed, Reading,
  Fetching, Your data, Help, About. Fetching moved down: set once.
- Name search (`FeedNameSearch`): the built-in library by name, then up to
  six guessed addresses, the phone's country first (network, then SIM, then
  language setting), each checked as a typed address is. No search service,
  so nothing leaves for a third party; PRIVACY.md says so. Feedly-style
  search left as a possible opt-in later.
- Sources: "No source called …" with "Find … to add"; + carries the text.
  The Add screen takes a name or an address, and a passed-in query searches
  at once. "Could not parse" became "No feed found for".
- 5 new tests (`FeedNameSearchTest`). 1245 passing, lint clean.
- **To try:** Sources → search "the potato" → Find it to add.

## 24 October 2026 — Settings split, stage 1

- Owner's layout: Appearance, Your feed, Reading, Sources,
  Fetching, Your data, Help, and About as its own row. Report a problem in
  Help opens a page with the three diagnostics rows.
- `SettingsSection` (one enum: title, summary, icon, and which settings each
  page holds) and `SettingsSectionPage`; `NavRoute.SettingsSection(name)`.
  Same preferences, same order within each page; only where they live
  changed. Tour and Launcher page got their own icons (Help had three ⓘ).
- Six tests that read the old page now read the new one; `SettingsSplitTest`
  checks every old setting is on exactly one page. 1240 passing, lint clean.
- Stage 2 next: flexible title bars, button rows for short choices, the
  sample card on Appearance, one margin, the new `ListItem` form.
- **To try:** Settings → each row, check nothing is missing, and Help →
  Report a problem.

## 24 October 2026 — Lawnchair accepted Whisper

- LawnchairLauncher/lawnchair#7399 approved by MrSluffy and merged into
  `16-dev`. Whisper will be on Lawnchair's feed-provider list in the next
  Lawnchair release built from it, with no debug-menu step.
- README updated. The in-app Launcher page keeps the "Ignore feed whitelist"
  step until a Lawnchair release carries the change; most people's
  installed Lawnchair still has the old list.

## 21 October 2026 — Material 3 Expressive library, ahead of stable

- Owner's plan: 1.0.6 = everything since 1.0.5 plus the settings split;
  the full redesign as 1.1.0; roughly monthly releases.
- Not waiting for Expressive to be stable: material3 1.5.0-beta02 needs only
  stable Compose 1.12 (checked in its published module file), so only that
  one library is beta. Compose BOM 2026.08.00 → 2026.09.00; material3 and
  the navigation suite pinned to 1.5.0-beta02.
- One break: `SeekBarPreference`'s slider (custom track) moved to the
  `SliderState` form. `ListItem`'s old form now warns as deprecated; left for
  the settings split, which rebuilds those rows.
- 1237 passing, lint clean. Nothing should look different.
- **To try:** a look round every screen, and the two sliders in Settings
  (overlay transparency) still drag and save.

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
