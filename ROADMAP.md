# Whisper — roadmap

Status against the milestones in the handoff, what is left, and the order it
should be done in.

Last reviewed against the tree, not from memory: every "done" below was checked
in the code. Sync (§7) updated 26 September 2026, after two days against a live
server.

---

## What this is now

Worth stating plainly, because the answer has changed and several decisions
below were made against the old one.

Whisper started as a replacement for Google Discover on Lawnchair's minus-one
page. That was the brief, and it is why the launcher work came first. **It is
no longer what the app is.** Four layouts, weighted ordering, breaking-news
clustering, read state, search, source management, categories, OPML both ways,
a reader with offline caching — that is a full RSS reader, and the launcher
page is now one surface it renders on rather than its reason for existing.

Five things follow, and they are not cosmetic:

- **The Lawnchair debug step stops being a blocker.** It was recorded as
  blocking real-world install, on the reasoning that a Discover replacement
  which cannot reach the minus-one page is useless. A reader whose launcher
  page is a bonus is perfectly usable without it. Getting whitelisted is still
  worth doing; it is no longer the gate.
- **Onboarding inverts.** The brief in `docs/ONBOARDING_PROMPT.md` argued the
  Lawnchair pane was the one to build even if the rest slipped. That was right
  under the old framing and wrong under this one: most people installing this
  will not have Lawnchair, and a first run that leads with a setup step for a
  launcher they do not use tells them they have the wrong app. One dismissible
  pane, offered late.
- **The audience is much larger, and so is the field.** Not "Lawnchair users
  who miss Discover" but anyone who wants an RSS reader — against Feedly,
  Inoreader, FreshRSS, Feeder and Read You. Being good is no longer enough on
  its own; being *distinct* matters.
- **What makes it distinct is the weighting, not the launcher.**
  `docs/REFERENCES.md` §4 found no open-source prior art for transparent,
  resettable preference learning in a feed reader, and §5 now ships it: an
  ordering that explains itself per article and can be disagreed with per
  source. Every competitor is chronological or opaque. That is the thing worth
  leading with — the minus-one page is the second sentence, not the first.
- **Getting subscriptions in becomes a competitive concern rather than a
  convenience.** §11 and §13 move up: a reader nobody can populate in five
  minutes loses to one they can, whatever the feed looks like afterwards.

One thing this does **not** change: the web version is still impossible (§12).
That was refused on CORS, which has nothing to do with what the app is for.

---

## Where the milestones stand

| # | Milestone | Status |
|---|---|---|
| 0 | Launcher feasibility | **Done** — builds, installs, minus-one works, mechanism documented in `UPSTREAM_NOTES.md` |
| 1 | Material shell | **Done** — identity, M3, dynamic colour, edge-to-edge, light/dark/black, scaffold, header, chips. Plus a shape scale, bundled Inter and a two-stage splash, none of which the milestone asked for |
| 2 | Cards layout | **Done** — cards, images, metadata, pull-to-refresh, save, read state, per-card overflow, source favicons, hide source, More/Less |
| 3 | Remaining layouts | **Done** — Cards, Magazine, List and Mosaic, chosen in Settings; Mosaic swaps the container for a staggered grid |
| 4 | Source management | **Done** — add, autodiscovery, duplicate detection, edit, remove with undo, multi-select bulk editing, a category screen, search, sort, broken feeds surfaced, OPML in/out, and pinned sources at the top of the list — which is what reorder became; see §2 |
| 5 | Personalisation | **Done** — weighting drives Cards and Mosaic, reads back More/Less and reading habits, two structural diversity rules, read-on-scroll with a tunable dwell, three read-visibility settings, bulk mark with undo, a per-article explanation and a transparency-and-reset screen |
| 6 | Sync | **Done, and proven on FreshRSS** — backup (§14) and Google Reader sync (§7), both ways: subscriptions, read and unread, saves, with a summary of what each sync did. Running against a live FreshRSS since 25 September 2026, 114 feeds. Removals made on the server are deliberately not applied here. FreshRSS is the one supported service; others are not planned |
| 7 | Glance row | **Done** — weather, sunrise/sunset, feed status. Calendar deferred, as the spec says |
| 8 | Reader / offline / polish | **Done** — reader, offline caching, sync/filter/frame-path performance, accessibility, battery, and motion: feed items move rather than being replaced, and every animation in the app stops when the reader has told the system to stop animating |
| — | Onboarding | **Done** (§15) — welcome panes, a six-stop guided tour, starter sources, a first-run restore, and a launcher-page setup screen |
| — | Tablets | **Done** (§20) — columns sized to the width, the article beside the feed, a two-column cap on lead stories, and tuning for a slower device. Proven on a Galaxy Tab S5e, Android 11 |

The card **rhythm** (hero / card / compact) is what Cards does *within* one
layout; the four layouts are what the user chooses between. Both exist now.

---

## Reported bugs

Found on device, so they take precedence over anything below when they are in
the way.

- ~~**The guided tour could consume itself without ever appearing.**~~ Found by
  audit, never seen on a device — which is the point. `onGloballyPositioned`
  runs *after* composition, so on the frame the tour started, the map of
  target positions was still empty. It read that as "there is nothing to point
  at", finished immediately, and wrote the flag that stops the tour running
  again. A one-time tour, spent without being shown. It now waits up to a
  second for the first control to report where it is.

- ~~**Every JSON model would have broken in a release build.**~~ Both JSON
  paths — JSON Feed subscriptions and the Google Reader protocol — build their
  adapters reflectively with `KotlinJsonAdapterFactory`. `moshi-kotlin` ships
  no consumer ProGuard rules of its own, and nothing kept the models, so R8
  would have renamed their fields and the reflective adapter would no longer
  have found them. R8 renames 559 classes in this app, confirmed in the
  mapping file. Invisible in every build tested so far, because the debug
  build does not run R8.

- ~~**A device restore would have crashed the app on launch.**~~ `allowBackup`
  was on with both rules files left as the empty AGP templates, so Android's
  automatic backup included the two `EncryptedSharedPreferences` files holding
  the Google Reader and Mastodon tokens. Their key lives in the hardware
  Keystore, which is never backed up — restoring the ciphertext without the
  key means the first read throws rather than returning empty. Cloud backup
  now excludes them; device-to-device transfer still carries them, because
  there the Keystore travels too.

- ~~**A blocking disk read on every recomposition, in fourteen places.**~~
  `collectAsState(initial = pref.getValue())` reads well but the initial value
  is an ordinary argument, evaluated on every recomposition — and `getValue()`
  is `runBlocking` against DataStore. Two of the fourteen sat in the feed's
  scroll path. All now wrapped in `remember`. `configurePeriodicSync()` was a
  related case: three of those reads inside `onCreate`, on the main thread, as
  the first DataStore access of a cold start.

- ~~**The starter feeds could have appeared with none of them ticked.**~~ The
  default selection was applied in a `remember` block that returned Unit, and
  Compose is free to skip those. Seeded where the map is built instead.

- ~~**Articles dimmed while they were being read.**~~ Read-on-scroll marked an
  article the moment its dwell timer ran out, so a card being read carefully
  faded out under the reader mid-sentence — the setting doing the exact
  opposite of what it is for. Time on screen is now the *qualifier* and being
  scrolled past is the *trigger*: an article is marked when it has been looked
  at long enough **and** has left the top of the screen. Leaving through the
  bottom is scrolling back, not reading past, so it keeps the time it earned.
  Two consequences, both correct: an article left on screen is never marked,
  and the last article in the list cannot be marked at all.

- ~~**The read-count offer interrupted every scroll.**~~ The undo for
  automatic read marks waited 1.2 seconds after the last one before offering
  itself, which is not a pause — it is what happens between two flicks of a
  thumb. So it arrived every few seconds, covering the article being read to
  report a number nobody asked for. Half a minute of stillness now, and only
  above five marks: scrolling past two articles is not an event.
- ~~**The feed was empty after a restart.**~~ Not a display fault and nothing
  was being deleted — cleanup was reporting `deleting=0` throughout. The app
  was drowning in its own sync. A device log showed the heap pinned at 244MB
  of 256MB, **520 blocking collections**, the main thread stalled for over a
  second at a time and 131 frames skipped, so the feed could not draw. Two
  causes in the sync, compounding, plus a third in the query:

  `maxFeedItemCount` was passed into `syncFeed` and used only for the cleanup
  afterwards, never to trim what was built — so a feed offering 135 entries
  had 135 articles and 135 content bodies materialised and written, and the
  "items per feed" setting capped nothing. And every feed synced at once, so
  with forty-five sources every one of those payloads was live in memory
  simultaneously. Now trimmed before anything is built, and four feeds at a
  time, which makes the peak the largest feed rather than the whole list.

  The feed query had no limit either: every article of every enabled source,
  whole rows including the full article text, rebuilt on every emission. It is
  a **window** now rather than a page — the weighting, the clustering and the
  two diversity rules all reason about the whole list, so handing them a page
  at a time would change what they mean rather than making them cheaper.
  Search widens it, because a search that quietly stopped covering older
  articles would no longer be the feature it claims to be.
- ~~**The search bar's two buttons did nothing.**~~ Reported by a tester with
  both circled. They were not broken: the bar was drawn *under* the status
  bar, so the back arrow and the clear button sat where the system takes the
  taps. `FeedSearchBar` replaces a `TopAppBar`, which insets itself, and the
  app's call site swapped one for the other without adding the inset back. It
  now insets by default, so the same mistake cannot be made again; the
  launcher overlay, which measures its own, passes zero. The clear icon was
  also `SubtractSquare` — a minus in a box, which at 22dp reads as a copy
  button, and was reported as one. It is a cross now.
- ~~**An empty feed said nothing at all.**~~ No message, no spinner, no
  artwork — the header, the glance row, the chips, and then a void. Every
  cause looked identical, so an empty feed could not be diagnosed even from a
  screenshot. It now says which of four things happened: no sources, a sync in
  progress, a sync that returned nothing, or a filter excluding everything.
  They are told apart because the right response differs completely, and
  because "nothing here" is not information.
- ~~**Sliders had no value on them.**~~ Every `FloatPref` has carried a
  `specialOutputs` lambda saying how to write its value down since the class
  was written, and nothing ever read it. The dwell slider and the overlay
  transparency slider were both a bare track with no number anywhere.
- ~~**The feed jumped while being scrolled.**~~ With read-on-scroll enabled,
  marking an article read subtracted from its weight, which dropped it a size,
  which shrank the card under the reader's finger and shunted everything below
  it up the screen — once per article, continuously, for the whole scroll.
  An article's size is now settled for as long as the feed is open: whatever
  it was first given, it keeps. Read state was the visible cause and not the
  only one, since marking read also rewrites the reading-habit counts and
  re-runs the clustering, so all three inputs change at once.

- ~~**The overflow menu opens over the status bar.**~~ Removed rather than
  repositioned. Reload is what pull-to-refresh is for and Restart was a
  development leftover, so the menu's only real entry was Settings — which is
  now a single button straight to it. No dropdown in this window means no
  dropdown to position in it.
- ~~**The in-app reader showed the feed's excerpt, not the article.**~~ Fixed:
  the readable page is fetched when the reader opens. The per-feed "fetch full
  articles" toggle still prefetches during sync; the reader no longer depends
  on it.
- ~~**Compact rows lost their save button whenever they had a thumbnail.**~~
- **Coming back from the browser lands on the home screen.** The in-app reader
  does *not* have this problem — it returns to the feed correctly — so the
  in-app mode is the answer and the browser is the path that cannot be fixed. Half of this is
  fixed — the workspace no longer shows *before* the browser — but the return
  cannot be fixed from this side, and the reason is in Lawnchair rather than
  here. A fullscreen browser stops the launcher;
  `LauncherClient.onStop` disconnects the overlay service, which reaches
  `OverlayCallbackImpl.onServiceStateChanged(false)` →
  `Launcher.setLauncherOverlay(null)` → `Workspace.setLauncherOverlay`, whose
  last line is `onOverlayScrollChanged(0)` — the workspace is snapped back to
  home. Re-attaching on resume runs the same line again. No message the
  overlay can send prevents it; pushing `overlayScrollChanged(1f)` afterwards
  would slide the panel open again, but only *after* the home screen had
  already appeared, which is worse than the problem.

  The way out is not to leave the launcher at all: render the article inside
  the overlay panel rather than starting an activity. Then nothing pauses,
  back returns to the feed, and the trip never happens. It only helps the
  in-app reading mode — a browser is someone else's activity by definition.
  ~~**and flashes the home screen on the way out**~~ Tapping an article closes the minus-one panel, shows
  the desktop for a frame, and only then opens the browser; pressing back from
  the browser returns to the home screen rather than to the feed. Both halves
  are the same cause: the overlay is a window on the launcher's token, not an
  activity with its own task, so the browser starts into the launcher's task
  and there is nothing for back to return to. Worth reading
  `OverlayView.closePanelIfNeeded` and the `pendingCloseOnResume` flag together
  with how the view intent is launched — the fix is likely to be starting the
  browser in its own task and closing the panel after, not before, it is up.

---

## Suggested order

The numbers are labels, not a schedule — they stay put so they can be referred
to. The working order set by the user is 1, 3, 4, 2, then the rest.

**§14 and §15 are done.** The backup that reduced the real risk — a
subscription list built over years existing in exactly one place — is built,
scheduled and offered on first run, and the app now explains itself to
somebody opening it for the first time.

**§22 is done.** The deep dive of 26 September found things that should not
reach people who never agreed to test: a diagnostics report that could carry
feed addresses and a location, a settings file that could stop the app
starting, a way for another app to open any page inside Whisper, glance text
cut on every 360 dp phone, and database upgrades no test had ever run. All of
it, and the rest of §22, is fixed and on main.

**What to build next: §8, shipping it.** Everything above is being tested by
three people on builds handed to them directly, which does not scale and does
not produce the feedback that finds the remaining problems. A release signing
key and a GitHub release are a day's work and change who can find the app.

**§7 is done.** Sync runs both ways against a live FreshRSS server, and the
account screen says what each sync did. That leaves §8 as the one large thing
between the app and the people who would use it.

### 1. Adding a feed should be forgiving

The whole "add source" flow is the roughest edge left, and it is what a new
user meets first.

- **Feed autodiscovery from a site URL.** Paste `https://www.howtogeek.com/`
  and get `https://www.howtogeek.com/feed`. Two stages, and both are needed:
  first try the site's own HTML for a `<link rel="alternate">` pointing at the
  feed, which is the correct answer whenever it exists; then fall back to
  probing the handful of conventional paths — `/feed`, `/rss`, `/feed.xml`,
  `/rss.xml`, `/atom.xml`, `/index.xml`. Guessing paths first would be wrong
  often enough to matter, so it is the fallback, not the opener.

  `docs/REFERENCES.md` §1 has the research: Twine's `FeedFetcher` for the
  HTML stage, and its `FeedUrlResolver` for the sites where discovery cannot
  work at all — Reddit serves a script shell to non-browsers, Mastodon handles
  are not URLs, and YouTube needs the channel id rather than the page.

- **Duplicate detection while adding.** Adding a feed already in the list should
  say so rather than silently creating a second copy. Match on the resolved feed
  URL after redirects, not the string typed — `example.com/feed` and
  `https://www.example.com/feed/` are the same subscription. An OPML import
  should merge against the same rule instead of doubling everything.

- **Undo remove** — deleting a source with dozens of articles is currently
  irreversible and instant.

### 2. Source management worth the name

Everything above is about one feed at a time. Once someone imports an OPML with
a hundred sources, one at a time is the wrong unit.

- ~~**Multi-select in the source list**, with mass actions: add a tag, remove a
  tag, replace tags, enable, disable, delete.~~ — done. Long-press any source to
  start a selection; the search field is replaced by a bar carrying select-all,
  clear, enable, disable, add/remove/replace category, and delete. Categories
  are picked as chips rather than typed, so a bulk edit cannot invent "tech"
  alongside "Tech". Every action clears the selection afterwards, because twelve
  sources left highlighted after acting on them invites acting on them twice,
  and a bulk delete is undoable from the same snackbar a single delete uses.

  The bar itself was rebuilt after none of this could be found on a device.
  It was a card in the list carrying six chips in a horizontally scrolling row,
  which put Delete sixth — off the right edge of a phone, with nothing to say
  the row scrolled — and labelled it with `remove_title`, whose text is
  "Confirm action". The one destructive action was both off screen and
  misnamed. It is now the Material contextual app bar: the count and a close
  cross where the title and back arrow were, Category and Delete as icons,
  everything else in an overflow, all of it pinned while the list scrolls.

  Also built with it:

  - ~~**Select all means all shown.**~~ The search field used to be *replaced*
    by the selection bar, so a filter stayed in force while invisible and
    Select all quietly took every source in the database — in a bar whose next
    button is Delete. The field now stays put and both bulk gestures read the
    drawn list.
  - ~~**Range select.**~~ Long-press a second source and the run between it and
    the last one picked comes with it, in the order on screen. Filing thirty
    imported feeds was thirty taps.
  - ~~**Category filter chips.**~~ The categories in use, as a single-choice
    row above the list. Searching the name nearly did this, but it also matched
    titles and addresses containing the word.
  - ~~**Clear articles, keep the source.**~~ For the feed sitting on nine
    hundred items nobody will read. Bookmarked and pinned articles are kept and
    the snackbar reports the count, since that is the only honest way to say
    what a delete-with-exceptions did.
  - ~~**Find duplicates.**~~ Two imports will happily add the same feed twice
    under two titles; they sort apart and look alike. Matched on the normalised
    address, so the http/https/trailing-slash variants that let the duplicate
    in are the ones it catches.
  - ~~**Fetch full articles, in bulk.**~~ Was per-feed and reachable only from
    each source's editor.

  Still missing, in the order they are worth doing:

  - **Refresh these now.** Syncing one selection is a smaller, more useful
    action than pulling the whole list, particularly straight after a bulk
    enable. Left out for now because `requestFeedSync` takes one feed id and
    enqueues one work request, and firing it per selected source needs its
    unique-work naming checked first.
  - **Export the selection.** OPML export is currently all-or-nothing. Handing
    someone the eight feeds from one category is a share, not a backup, and it
    is the same writer with a different input list.
  - **Require link / require image, in bulk** — the same argument as full text,
    one notch less common.
- **Category management as its own screen** — rename a category everywhere it is
  used, merge two, delete one and choose what happens to its feeds. Categories
  are currently a free-text field on each source, so a typo creates a category
  and nothing can rename it.
- ~~**Sort and search the source list**~~ — done: by name, category, least
  recently updated, and recently added.
- **Surface broken feeds.** A source that has failed to fetch for days looks
  identical to one that is simply quiet. `lastSync` is already stored; nothing
  reads it back to the user.
- ~~**Reorder**~~ — answered as favourites, not as a drag handle. The problem
  with reorder was never the work: a persisted manual order fights the sort
  selector built alongside it, because a hand-made order has no meaning while
  the list is sorted by name, and the usual fix — manual as a fourth sort —
  leaves a handle that silently does nothing in the other three. Both options
  are a control that lies about what it does.

  Up to five pinned sources sit at the top instead, and everything below
  keeps whatever sort was asked for — including the pinned ones among
  themselves. It composes with every sort rather than competing with them,
  needs a preference rather than a column and a migration, and the cap is the
  feature: a list where everything is at the top is a list in its original
  order.
- ~~**Tags should be picked, not typed.**~~ Done, and done in all three places
  a category can be set: adding a source, editing one, and the bulk dialog on
  the sources list. Every tag in use offers itself as a chip, several can be
  selected on one feed, and typing is reserved for making a new one — with a
  guard that still saves a typed tag if Save is pressed instead of Done, which
  is the kind of loss nobody reports and everybody notices. This entry was
  simply left open after the work.

That set is Milestone 4's "complete source management", read literally.

### 3. Finish the card

- **Per-card overflow menu** — the four entries the mockups show, in order:
  More like this, Less like this, Hide source, Share. The first two are the
  visible half of Milestone 5 and should be built to record a signal even
  before anything reads it back, so the menu is not a lie in the meantime.
  Hide source and Share work on their own from day one. Every article currently
  offers only save and share.
- **One symbol for saving.** A heart on the card saves an article and a
  bookmark ribbon in the header shows the saved ones — two glyphs for one idea,
  neither of which is ours. Both become a single mark derived from the app
  symbol: it is three blades, so a three-stroke form is already in the
  artwork. Vector rather than a bitmap, so it tints with the theme and carries
  a filled state for saved against an outline for not.

  Decided against, so it does not come back: a **Saved chip in the category
  row**. That row is topics — All, Misc, News, Tech — and Saved is a state, so
  the row would answer two questions at once and raise one it cannot (is Saved
  additive with News, or exclusive?). One route in, through the header toggle,
  which already shows when it is on.
- **Source favicon on the card** — the meta row names the source in text only.
  Discover puts the site's mark beside it, and it is what makes a source
  recognisable at a glance in a mixed feed.

  Where the image comes from, cheapest first: the feed's own `<image>` or
  `<icon>` element (many carry one and it is already parsed past), then
  `/favicon.ico`, then the `<link rel="icon">` set in the site's HTML head.
  Resolve **once per source, not per article** — store the URL on `Feed` and
  the bytes in the existing blob directory, refreshed only when a feed is
  edited or fails to load. Falling back to a tinted monogram of the source's
  first letter keeps the row from collapsing when a site has nothing usable,
  which is common enough on small blogs to be the default case rather than an
  edge one.

  Note the privacy cost and keep it honest: fetching a favicon is a request to
  the site's own server, no third-party favicon service. Google's
  `s2/favicons` endpoint is the easy path and it tells Google every source the
  user reads — it should not be used.

### 4. Layouts (Milestone 3)

Magazine, List, Adaptive Mosaic, over the same domain model. The rhythm work
already split the card into three shapes with a shared item API, so this is less
of a jump than it was.

### 5. Personalisation (Milestone 5)

More/Less like this, source affinity, hide source and topic, reset,
chronological and smart ordering. `readAt` exists now, which is the first piece
of article state; the rest of the signal model is still to design.

**First consumer shipped: article weighting in Mosaic.** `FeedWeight.kt` scores
each article — image, freshness, source affinity, headline length, whether a
summary exists, read state, saved, pinned — and the score picks the tile size.
It is the first thing that reads the affinity scores back rather than only
writing them, and it is deliberately a display decision rather than a filter:
a low weight makes an article small, never absent, so a bad score cannot hide
anything. The constants are in one object and want tuning against a real feed.

Cards now reads the same weight. `FeedEmphasis` is one scale shared by both
layouts — Cards draws Large as a full-bleed hero, Mosaic as a tile across both
columns — so the two promote the same articles and differ only in how the
promotion looks. The old positional rule gave the hero to whatever happened to
be eighth.

**Scrolling past as a read mark — built.** Two things kept it off by default,
and both still hold:

- **This is a Discover-shaped surface, not an inbox.** People scroll it idly
  and come back to something they glimpsed. A reader who has not asked for
  this and finds forty articles marked has no way to tell that is what
  happened.
- **It interacts with the weighting.** Read articles carry a −1.5 term, so
  scroll-to-read shrinks everything already seen on the next pass. That is
  arguably correct and it is certainly strong, and it wants seeing on a real
  feed before it is anyone's default.

**Dwell, not velocity — built.** The instinct was right, that a fling past
forty headlines is not reading, but scroll speed is the wrong way to measure
it: velocity swings inside a single fling, so the same gesture would mark some
articles and not others depending on where in the deceleration curve they
landed. Time on screen asks the question directly.

The threshold is the reader's, not a constant: 1, 2, 3 or 5 seconds, off by
default. There is no right number — it depends on how fast someone reads and
how they scroll — so guessing one and hard-coding it would only have produced
a number to argue with. A card is on screen for roughly 300-500ms during a
fling, so every option sits above that floor.

A card also has to be at least 60% visible before its clock starts, or the two
items straddling the edges of the viewport accrue time as fast as the one being
looked at. Dwell accumulates while the list is still as well as while it moves:
an article held on screen while it is read has been read, and requiring
movement would mean the one card you stopped on was the one that never counted.


**Whether a read article then disappears is a separate setting, and the default
is no.** Today `readAt` does exactly two things: it subtracts 1.5 from the
article's weight, so a read article gets a smaller shape next time, and it
feeds the unread count on the glance row. It changes nothing else and hides
nothing. Three levels are worth offering, because they suit genuinely
different readers:

| Setting | Behaviour |
|---|---|
| **Keep** (default) | Read articles stay, at reduced weight — smaller, lower down, still there |
| **Dim** | Also visibly marked as read — the traditional reader's greyed row |
| **Hide** | Removed from the stream entirely |

**All three are built**, ahead of the rest of this section, since they stand on
their own: opening an article is already the one thing that marks it read, so
the setting does something real today and does not wait on the scroll trigger.
Saved and pinned articles are never hidden by it, whatever it is set to.

Hide must not be the default and probably should not be reachable without
scroll-to-read being on first: on a surface people scroll idly, an article that
vanishes because it was on screen for two seconds is indistinguishable from a
bug, and it is gone before the reader knows to look for it.

Two interactions that had to be right, and are:

- **Do not count the same thing twice.** The weight already shrinks read
  articles. Fade on top of that is fine; hide makes the weight term irrelevant
  for those articles rather than compounding with it.
- **Hiding needs a way back** — a "show articles you have read" switch in the
  filter sheet, not buried in Settings. The one place a reader looks for
  something that has vanished is the filter that made it vanish. It is staged
  until Apply like every other control in that sheet, and turning read
  articles back on restores Keep rather than Fade: Fade is a choice made
  deliberately in Settings, and a switch labelled "show" should not quietly
  pick a different way of showing.

#### Still open in this corner

- ~~**"Mark all as read."**~~ **Built**, in the feed's filter sheet under its
  own heading: **Everything**, or older than **1**, **24** or **48 hours**, by
  the article's own date. Each shows how many unread articles it would mark,
  one with nothing in it cannot be picked, and it acts at once rather than
  waiting for Apply, which the heading says. Undo appears in the feed straight
  away. It started as a Settings row; that row is gone, because the feed is
  where you are when you want it, and its undo waited for half a minute of
  stillness meant for scroll marks, so nobody saw it. Not in the launcher
  panel yet: the panel has no undo for a batch of reads.
- **Later: a new icon for the filter sheet.** It now sorts, filters, shows or
  hides read articles and marks as read, which is "feed options" rather than
  a filter. The candidate is Phosphor's SlidersHorizontal, with a dot for
  "the feed is narrowed" in place of the funnel's filled twin. Not in the
  provided icons, so it waits for the asset; the funnel stays until then.
- ~~**An undo window on a scroll-produced batch.**~~ **Built.** A run of scroll
  marks, or a mark-all, is offered back as one batch in the feed's snackbar.
- **A debug readout of accrued dwell**, showing what each card banked as it
  passed. Worth building only if the four thresholds turn out not to cover
  it — the point is to choose the number from what actually happens rather
  than infer it from behaviour, and that is only worth the screen if the
  behaviour is in question.

**"Why is this here?" — built.** In the per-article menu, listing the signals
that decided the article's size, largest first, with what each contributed.
Derived from the same terms the weight sums rather than computed alongside
them: an explanation written separately from the thing it explains drifts the
first time either is edited, and then confidently says the wrong thing.

It is at the article rather than only in Settings because by the time someone
has walked to a settings screen they have stopped wondering about the card
that prompted the question.

**Reading habits as a weight term — built, and it did not need accounts.** The
plan was to gate this on sign-in and count usage server-side. It did not have
to wait: `readAt` is already on every article, so per-source read counts, opens
per week and time-since-last-read can all be derived on device today, from data
that is already there. What an account adds is *carrying those counts to
another phone* — which is §7's job, not a prerequisite for the feature. Build
it locally, sync it later.

The term itself is a small one on purpose. A source read often gets a nudge, not
a promotion: enough to break a tie between two similar articles, never enough
to outrank a fresh story from somewhere else.

**The diversity constraint is not optional, and it is the hard half — built as
two structural rules.** Left alone, "favour what they read" converges on one
site: it gets shown more, so it
is read more, so it is weighted higher. The counter has to be structural rather
than a smaller coefficient, because any positive coefficient runs away
eventually. Two rules, both cheap:

- **No source may hold more than one of the top slots.** Whatever the weights
  say, the large tiles and the hero slot go to distinct sources.
- **A cap on consecutive items from one source** in the ordered list, with the
  overflow displaced rather than dropped — it moves down, it never disappears.

Both are display rules, which keeps the property the weighting already has: a
low score changes how big something is and where it sits, never whether it is
there at all. Nothing in Whisper should be able to hide an article the user
subscribed to.

**The reset control is a screen, not a button.** `docs/REFERENCES.md` §4 found
no open-source prior art for transparent, resettable preference learning in a
feed reader, and a bare reset is only the resettable half. "What Whisper has
learned" lists every source with its More/Less score, how many of its articles
were read in the last thirty days, and the resulting weight — the number the
feed actually uses, computed by the same arithmetic rather than an
approximation of it. A score nobody can audit is one nobody can correct.

Resetting is per source as well as wholesale, so one mistaken Less can be
undone without discarding a year of signal. Read state is deliberately not
cleared by it: that is a record of what happened rather than an opinion about
it, and "forget what you have learned" does not mean "mark a year of articles
unread" to anyone who presses it.

Worth restating: `docs/REFERENCES.md` §4 found **no open-source prior art** for
transparent, resettable preference learning in a feed reader. This is
build-it-ourselves rather than assembly, and should be budgeted that way.

### 6. Breaking news, sticky and pinned

Three related asks, in increasing order of difficulty.

**Pinning — built.** A reader pins an article they are following and it holds
the top of the feed until unpinned, whichever sort is active. No inference,
nothing to get wrong, and it is the manual escape hatch for whenever the
automatic detection below gets it wrong.

The boolean on `Article` was already there, and that turned out to be the
problem rather than the head start: `bookmarkArticle` set `pinned` to the same
value as `bookmarked`, so saving an article pinned it and unsaving released it.
Pinning could not mean anything on its own, and every saved article was
quietly collecting both weight bonuses. The two are different things — saving
is "I want to find this later", pinning is "I am following this, keep it in
front of me" — and they are now separate switches.

Since pinning was never reachable from the interface, every pinned row in an
existing database is a saved article rather than a pinned one. Migration 13→14
clears the column, so upgrading does not put every article the reader has ever
saved at the top of their feed.

Opening a pinned article no longer releases it either. That was right when a
pin was a side effect of saving; it is wrong when a pin means the reader is
following a story, since reading today's report is not a signal that they have
stopped.

**Breaking news detection — built, on title similarity.** The signal is the
right one: when several sources publish about the same thing inside a few
hours, that is a story, and a single-source post is not. The hard part was
"the same thing", and the cheapest option turned out to work.

What makes plain word overlap usable is weighting words by how *rare* they are
in the current feed. A token in two or three titles out of four hundred is a
proper noun identifying an event; one in fifty is a topic. Two articles sharing
two rare tokens are almost always the same story; two sharing four common ones
almost never are. An inverted index over the rare tokens only keeps it well
short of comparing every pair.

Ten tests cover it, including the two ways it can fail in public: inventing a
story out of headlines that merely share a topic, and being fooled by a feed
that appends its own name to every title.

The options considered, and the honest cost of each, kept for whoever revisits
this:

- *Title similarity* — normalise, strip the source suffix, then compare on
  token overlap or trigram Jaccard. Runs locally, no network, no model, a few
  milliseconds over a few hundred articles. Misses stories that are worded
  differently and joins ones that merely share a proper noun. Cheap enough to
  try first and see how it reads on a real feed.
- *Shared outbound links* — articles about one event tend to cite the same
  source document. Precise when it fires, silent when it does not.
- *Embeddings* — accurate, and a model in the APK plus per-article inference
  on a phone, for a feed of a few hundred items. Not for a first pass.

Whichever is used, the cluster is what gets promoted, not the article: one
representative gets the hero slot, and the others become a "N sources" line
under it. That also answers a question the current rhythm cannot — which of
five near-identical articles to show.

**This is not a second mechanism beside the weighting in §5 — it is the
weighting's largest term.** Both answer the same question, "how much of the
screen has this earned", and building them as two systems would mean two
things competing for the hero slot with no rule between them. A cluster of
distinct sources publishing inside an hour is simply worth a lot of weight,
which is what makes it beat a merely-fresh article for the same slot. It also
inherits the diversity rules for free.

Scope the burst to where it means something: a spike across five news sources
is a story, the same spike across five review sites is a product launch and a
release-day rush from one topic is neither. Restrict it to sources the user has
categorised as news, at least for the first pass. The categories already exist
and are already the user's own judgement, so the app is reading a decision the
user made rather than guessing at one.

Guard against the obvious failure: one prolific feed posting six times about
its own topic is not breaking news. The cluster must span **distinct sources**
— three of them — and everything in it must fall inside a twelve-hour window,
or a chatty feed would hold the hero slot all day. Both are tested.

Two things it deliberately does not do. It does not collapse the cluster: every
member stays in the feed at whatever size it earned, and only the lead is
promoted, because hiding four reports means choosing which four the reader does
not see and no automatic rule here is good enough for that. And it is off by
default — this is the one part of the weighting that infers rather than counts,
and a feed that hands the biggest slot to the wrong article for reasons the
reader never asked for is worse than one that never tries.

**Pinning and sticky are what remain.**

**Sticky until scrolled past — built.** A setting, off by default.

The one ordering rule those three needed turned out to be dull, which is the
point: **whatever the ordering already put first, if it is being held on
purpose.** Pinning sorts an article first; the breaking-news term weights a
cluster lead heavily enough that it lands first. Both arrive at the top
through machinery that already existed, and sticky only asks whether the
article now at the top got there for a reason worth holding. Nothing
competes, and only one thing can be held, because only one article can be
first — the reader's own pin winning, since it sorts above everything.

`stickyHeader` alone gives the wrong behaviour: a header stays pinned while
its section is on screen, and with one header over the whole list that means
for ever. Sticky would become permanent, and the reader could not get rid of a
story by scrolling, which is the first thing they will try. Two sections
instead — the held article heads one holding the next five articles, then an
empty, zero-height header takes the sticky slot and pushes it off. What the
reader sees is the card sliding away once a few articles have gone by, with no
animation written.

Mosaic does not take part: a staggered grid has no sticky slot, and the lead
already crosses both columns there, which is that layout's way of saying the
same thing.

Both settings sat with the personalisation switches, not in a category of
their own: "Highlight breaking news" and "Hold it there while you scroll", the
second directly under the first because it only modified what the first
promoted.

#### Sticky was removed, and the reasoning is worth keeping

The passage above is left as written because it is an accurate account of
something that was built and then taken out. The hold is gone; the detection
is not.

What was wrong with it was the condition, not the intention:

    val first = articles.firstOrNull() ?: return null
    val isLead = clusters[first.id]?.leadId == first.id
    if (isLead) first else null

That is positional, not editorial. It held whatever was first in the list
*currently on screen*, if that item led any cluster — so in a search it held
the first result for whatever was typed, in a category the first of that
category, and it would have done the same in the single-source view built
since. None of those has anything to do with a story breaking, and a reader
who had searched for something got a card stuck over their results.

The paragraph above about "whatever the ordering already put first" is exactly
the assumption that failed. It holds for the whole feed, where the ordering is
the weighting, and stops holding the moment a filter decides what comes first
instead.

It also cost more than it looked. A `stickyHeader` draws over the list rather
than in it, so every part of the card had to be opaque or it became a window
onto the articles sliding underneath — fixed twice, and a held card with no
image was see-through still. The read-dimming carried an exemption for it. It
needed a Dismiss action, because being unavoidable was the one thing it was
reliably good at.

What it was for was already on the card and stayed there: `CoverageLine` draws
a megaphone and the number of sources carrying the story, in the card's
ordinary place. The clustering still sets emphasis, still explains itself in
the weighting reasons, and a dismissal still gives back the promotion.

**Two alternatives were considered and not taken.** Recorded because the first
is cheap if the hold is ever wanted back, and the second is the honest version
of the feature.

- **Restrict it to the unfiltered feed.** Do not hold while searching or
  filtered. The smallest possible change, and it fixes every symptom reported.
  It leaves the trigger positional, so the hold still fires on whatever the
  weighting happened to put first rather than on anything that has actually
  broken.
- **Make the trigger editorial.** Require a real threshold — *n* sources
  carrying the story within *m* hours, rather than "leads a cluster and
  happens to be top of this list" — so that "breaking" means something a
  reader would recognise. This is the version worth building if a held card is
  wanted at all, and it is a genuine piece of work rather than a condition
  change: it needs a threshold chosen against real feeds, and it needs the
  transparency the rest of the weighting has, so that a held story can say why
  it was held.

Either would need the opacity problem solved rather than worked around, since
that is a property of `stickyHeader` and not of the condition.

### 7. Sync and backup (Milestone 6)

**Done, and proven on a live server.** Since 25 September 2026 Whisper has
synced both ways with a FreshRSS server: subscriptions, read and unread, and
saves. The setup is `docs/SYNC_SERVER_FRESHRSS.md`; what the first two days
against it found is under *Proven on a live server* below.

**Decided: Google Reader protocol.** Recorded in `docs/REFERENCES.md` §2 —
Feedly's own API turned out to be enterprise-gated, but the Google Reader
protocol is spoken by FreshRSS, Inoreader, Miniflux and BazQux, so one
implementation buys several services and needs no Google account and no Play
Services. ReadYou has a complete client (`GoogleReaderAPI.kt`, around 560 lines
plus DTOs) to work from, under a compatible licence.

The shape to copy with it is ReadYou's `AbstractRssRepository`: one base class
where only `sync()` is abstract and everything else has a working *local*
implementation that remote providers override selectively. That matches the
local-first rule exactly — the app stays fully functional with no account, and
sync is genuinely additive rather than a mode.

#### What is built

- **`GoogleReaderApi`** — the protocol itself: ClientLogin, the separate write
  token, subscriptions, item ids, edit-tag, subscription/edit. Fourteen tests
  cover the parsing and the identifiers.
- **`GoogleReaderIds`** — the three shapes an id comes in, in one place. The
  long form is sixteen hex digits, unsigned, and larger than `Long.MAX_VALUE`
  for half its range; `toLong()` on it throws on exactly the ids whose top bit
  is set, which is how a client works for months and then falls over on one
  article. Tested at the boundary.
- **`RssService`** — the abstraction, with only `sync()` abstract. Everything
  else has a working local implementation, and `LocalRssService` is the real
  default rather than a stub.
- **`SyncAccount`** — one account, in `EncryptedSharedPreferences` rather than
  DataStore because it holds a credential.
- **The account screen** — Settings → Account. Sign in at the server's web
  address (the API path is found for it), Sync now, sign out. Below the
  account: what the last sync did and what today's syncs did, as counts —
  feeds on the server, articles it knows, reads and saves sent and received —
  and any feeds the server lacks, grouped by why.
- **`SyncOutbox`** — reads, unreads, saves and unsaves made here, kept until a
  sync sends them, so a change made offline is not lost and one undone before
  the sync cancels out.
- **Two-way subscriptions** — `planSubscriptions` with two memories, the feeds
  here at the last sync and every feed ever seen on the server, which is what
  tells "removed here" from "new there". `matchFeeds` pairs the two lists by
  address, then a remembered pairing, then title, because a server keeps a
  feed under the address it settled on and that is often not the one Whisper
  was given.
- **Sync now runs in the worker**, as a foreground task, so it keeps the
  network when the reader switches apps. One account sync at a time, whoever
  asked.

#### The division of labour, which is the design decision

The remote service syncs the **subscription list** and **read state**. It does
not fetch articles; the local path still does that.

That is deliberate. Whisper's articles carry things the protocol has no field
for — extracted full text, the chosen image, the built summary — so taking
them from the server would mean losing those or fetching twice. Fetching
locally also keeps behaviour identical with and without an account, and keeps
the app working when the server is down. An account changes *which feeds* and
*what has been read*, not what an article is.

Google Drive is **not** a second version of this and is no longer described
here; see §14. It is a backup destination for the OPML, it does not depend on
any of this, and it should be built before the rest of it.

#### Deliberately unfinished, and named rather than hidden

- ~~**Read state is pulled but not applied.**~~ Built. `Article.remoteId`, and
  it needed more than the column this note promised: because articles are
  fetched from the feeds rather than from the server, the server's id never
  arrives with them, and the ids endpoint returns bare ids with nothing to
  match against. So there is a `stream/contents` call now whose only job is to
  say which id belongs to which address — the link being the one thing both
  sides know. Not the guid: that is set by the publisher and has nothing to do
  with the id the server assigned.

  **Only articles the server has claimed are touched**, and that is what makes
  applying it safe at all: an article with no `remoteId` has never been
  mentioned by the server, so its absence from a list of unread ids means
  nothing. An empty unread response is also ignored rather than treated as
  "everything is read", which is the failure a first version meets.

  Read state pushes back too, through the same mapping — `setRead` tells the
  server when it knows the article and does nothing when it does not.

  ~~**Unverified against a real server.**~~ Proven on FreshRSS; see below.
- **Removals on the server are not applied here.** A feed the server no
  longer lists is kept, and listed as removed on the server so the reader can
  decide. Deleting somebody's subscriptions because of a partial response or
  the wrong account is unrecoverable, and those are exactly the failure modes a
  first version meets. A feed removed *here* is removed from the server: that
  is what the reader did, on purpose.
- ~~**No background sync.**~~ Built. `FeedSyncer` dispatches through the
  active service now, so a scheduled run reconciles subscriptions, maps ids and
  applies read state rather than only fetching RSS. It used to call `syncFeeds`
  directly in every case, which meant an account was reconciled only while its
  settings screen was open — subscriptions added on another device never
  arrived, and read state never moved unless somebody went looking for it. The
  scheduled sync was local-only without saying so.

  A single feed or one tag stays local: neither the protocol nor this worker
  has a notion of syncing part of an account. A signed-out account reports
  success rather than failure, so WorkManager does not back off and retry
  something that needs the reader rather than another attempt.

#### Proven on a live server

A FreshRSS server in Docker, 114 feeds, from the night of 25 September 2026.
Every fix below came from a diagnostics report or a screenshot of the
account screen, and each has tests.

- **Account syncs forced a fetch of every feed**, and the panel's and the
  app's syncs never reached the account at all (they asked for "all feeds" in
  a form the dispatcher did not count).
- **Being stopped read as failing.** Android cancelling a sync was logged as
  `failed: pd2` and retried at once, with 15–50 MB downloads each time. Three
  syncs ran at once and added one feed four times; there is a lock now.
- **The first match of articles was unbounded.** It is two days and eight
  pages, kept page by page.
- **29 feeds went both ways twice**, because the server kept them under
  different addresses. Matching by title, remembered once found, fixed it.
- **Sync now lost the network on an app switch** and said no server could be
  found. It runs in the worker now.
- **Four feeds the server would not take.** Two were sites blocking servers,
  fixed for every feed by giving FreshRSS a reader's user agent; one had moved
  behind a Cloudflare challenge and was moved to its new address; one sent an
  unusual content type and needed FreshRSS's `#force_feed`. Whisper now keeps
  a refused feed on the phone, says so in plain words, and offers it again
  weekly rather than every sync. The fixes are in the setup guide.
- **Nothing said whether a sync had worked.** The account screen and the sync
  history now carry the counts, and Today keeps them in view after a quiet
  sync.

Result: every feed on the server, and reads confirmed in both directions —
one sent, 216 received on the first morning.

#### Still open

- **Miniflux, Inoreader and BazQux: not planned.** They speak the same
  protocol and may well work, but FreshRSS is the one service tested and
  supported; one is enough. `docs/FRESHRSS_TEST_SERVER.md` keeps the Miniflux
  notes for anyone who wants to try.
- **Changes wait for the next sync.** A read made here reaches the server at
  the next hourly sync, or at once with Sync now or pull to refresh. Sending
  them within a minute was offered and declined: the hourly schedule is
  enough.

#### ~~Sync only when charging — asked for, not yet built~~ Built

Settings has *Sync on Wifi Only*; the obvious sibling is *Sync only while
charging*, for the reader who wants forty feeds fetched overnight and nothing
touched on battery.

Built as a second `BooleanPref`, off by default, applied in
`configurePeriodicSync` and offered beside *Sync on Wifi Only*. One thing
changed on the way in that was not in this plan: the schedule was configured
once in `onCreate` and never again, so *both* switches only took effect on the
next cold start — turning Wi-Fi-only on and watching the app go on syncing over
mobile data was the existing behaviour. The schedule is re-enqueued whenever
one of its inputs changes now. See `SyncConstraintsTest`.

The record of the reasoning follows.

**What the scheduled sync already carries** (`MainActivity.configurePeriodicSync`):
`NetworkType.UNMETERED` or `CONNECTED` depending on the wifi switch, plus
`setRequiresBatteryNotLow(true)`, which is already a battery guard — it just
means "not nearly flat" rather than "plugged in". Adding this is one line,
`constraints.setRequiresCharging(true)`, behind a `BooleanPref`.

**Why it is not one line.** Battery-not-low is satisfied most of the time;
charging is satisfied for a few hours a night, and for some people not every
night. Turning this on can mean a phone that opens the panel to a feed a day
and a half old, with nothing on screen saying why — WorkManager holds the work
silently until the constraint is met, and a periodic request that never meets
its constraints simply never runs. That is the whole design problem, and the
switch is worth nothing without an answer to it:

- The summary has to say what it costs, not just what it does. Something like
  "Scheduled syncs wait until the phone is plugged in. Pull to refresh still
  works any time" — the second sentence being the part that keeps it from
  feeling broken.
- The never-synced and stale-feed marks (§ "Make a never-synced feed
  distinguishable") are what make the delay legible rather than mysterious.
  This switch should not ship before them.
- Pull-to-refresh carries no constraints and must not grow one, for the same
  reason it carries no battery guard today: that sync was asked for.

~~**Decide before building:** whether it is a third state of one "when to sync"
choice rather than a second independent switch. Three switches (frequency,
wifi, charging) recreate exactly the boolean-pair muddle that
`articleOpenMode` was built to replace — a single list ("Any time", "On Wi-Fi",
"On Wi-Fi while charging") says the same thing and cannot be set to a
combination nobody wants.~~

**Decided: a second switch.** The list above was the wrong call, and it is
worth saying why rather than quietly swapping it, because the reasoning is
the reusable part.

That list has no entry for *unlimited data, but not on battery* — the reader
who does not care about Wi-Fi and does care about charge. Two switches give
four states and all four are real: any network or Wi-Fi only, crossed with any
time or while charging. A list that offers three of them is not simpler, it is
short of one.

The `articleOpenMode` comparison was a false match. There, two booleans encoded
*three* modes, so one of the four combinations was meaningless and the switches
misdescribed the shape of the choice — which is when a list is right. Here the
conditions are genuinely independent, which is when switches are. Android
agrees, for what it is worth: `Constraints.Builder` takes network type,
charging, battery-not-low, idle and storage-not-low as separate constraints
rather than one setting, and most apps follow it.

`DiscoveryWorker` sets its own constraints and would want the same treatment,
or an explicit note saying why it is exempt.

### 8. Ship it

- **The Lawnchair whitelist PR** — see below. Worth doing, and no longer the
  gate it was recorded as: see "What this is now". The app is usable without
  ever touching a launcher.
- Release signing key, then GitHub Releases → F-Droid → Play, per the staged
  plan in `docs/brand/ASSET_SPEC.md` §8.

### 9. Search, and a filter that explains itself

- ~~**Search the feed** from the header.~~ Done, in both surfaces. Over the
  headline, the source and the summary — all already in the database, so it
  needs no network, no account and no index. The article body is deliberately
  not covered: it lives in a file per article, so searching it would mean
  reading every one off disk on each keystroke, or building an index. Worth
  revisiting if the shorter fields prove too thin.
- ~~**The filter is confusing as it stands.**~~ The overlay showed a
  View-based XML sheet while the app showed a Compose one, and the two now
  share the app's. What it is filtering *by* is now visible without opening
  anything: each active narrowing is a named chip under the header, on both
  surfaces, with its own cross — so a muted category is undone where the
  shortened feed is rather than inside the sheet that set it. A solid funnel
  answers *whether* a filter is on and never *what*, which is exactly the
  state in which somebody decides the app has lost their articles.

  The chosen sort is shown but not removable: there is no "no sort", so a
  cross on it would have to mean "back to newest first" — a different action
  wearing the same symbol as the ones beside it.

### 10. Glance and header, once the rest is in

Small, and none of it blocks anything.

- ~~**Chance of precipitation on the weather chip.**~~ Done. The note here
  claimed the Open-Meteo call already returned it; it did not — the request
  asked only for temperature and weather code, and now asks for
  precipitation_probability_max as well. Shown beside the temperature with the
  supplied rain artwork at 16dp, and only above 20%, so a dry day's chip is
  unchanged.
- **Account avatar, top right.** Discover puts the signed-in user there and it
  is where a hand goes looking. It waits on §7: there is no account to show
  until the Google Reader client exists, and a silhouette that opens nothing
  would be worse than the space it fills.

---

### 11. Finding sources the user does not have

Discover's most-liked trick is putting a site in front of you that you never
subscribed to. The assumption was that a pure RSS reader cannot do this,
because there is no recommendation service behind it. That is half right: what
it cannot do is *rank the whole web*. What it can do is notice things it is
already holding.

Three mechanisms, all local, none needing an account, a server, or a model.
Listed cheapest first — the first is the strong one and the other two are
garnish.

**Built, as mechanism 1 only.** The other two remain as written below.

A weekly pass reads the bodies of articles with a `readAt` — read, not merely
delivered — counts the domains they link to, and runs the same autodiscovery
that adding a feed by hand uses against anything that recurs. Three separate
articles is the threshold: one is a citation, and three is high enough that a
single link-heavy roundup cannot manufacture a suggestion on its own. A domain
is counted once per article however many times it appears, for the same reason.

Share buttons, link shorteners and image hosts are excluded outright — every
article links to those, so counting them would hand everybody the same four
suggestions.

Suggestions live on a screen the reader goes to, never in the feed, and each
one carries its evidence in the row: *"six articles you read linked here"*.
Refusing one is remembered rather than deleted, so the next pass does not ask
again. Weekly, on unmetered network and a battery that is not low, because
nobody asked for a home page to be fetched at that particular moment.

**1. Outbound links in articles that were actually read.** Every article
already fetched carries links, and the reader's own `readAt` says which
articles were read rather than merely delivered. Count the outbound domains
across read articles over a few weeks, discard ones already subscribed, and
for a domain that recurs, do the autodiscovery pass §1 already implements
against its home page. If it publishes a feed, that is a suggestion with a
reason attached: *"You have followed six links to The Verge this month."*
No inference about taste, no profile, no third party. The reason is legible
and always true, and if the user disagrees they can see exactly what produced
it — which is the thing §5's `docs/REFERENCES.md` note says nobody else does.

**2. Feeds the sources themselves point at.** Blogrolls, `<link
rel="related">`, OPML files published on a site's own links page. A small
number of sites still do this and it costs one fetch of a page already being
fetched. Low yield, near-zero cost.

**3. Curated OPML collections.** Public topic lists that a user opts into and
imports. Honest, but it is a directory rather than a discovery, and it puts
whoever curates the list in charge of what gets seen. Worth it only if the
list is chosen explicitly by the user, never fetched in the background.

What is deliberately *not* here: sending the subscription list anywhere to be
matched against other users' lists. That is the mechanism that makes Discover
work and it is the one thing this reader should not do — the whole premise is
that nobody else learns what is read.

Constraints when this is built: a suggestion is a suggestion. It appears in one
place the user can go to and dismiss from, never injected into the feed itself
as an unsubscribed article — the moment articles from sites the user did not
choose start appearing in the feed, the reader stops being theirs. And every
suggestion states its evidence.

This sits after §7 in any sensible order: it depends on read history being
worth something, which depends on the reader having been used for a while.

---

### 12. A desktop reader — last, and only as an app

Bottom of the pile deliberately. Recorded so the reasoning is not re-derived.

**Not as a website.** A browser cannot fetch a third-party feed: the
same-origin policy stops it and news sites do not serve CORS headers to
strangers. Importing an OPML file is fine — that is a local file — but the
forty feeds it names are all blocked, so a web version needs a server
proxying every fetch. That server then sees every feed every user subscribes
to and every article they open, which is the one thing this reader exists not
to do. It is also a permanent hosting bill and a liability the Android app
does not have.

This is why Feedly makes it look easy: **Feedly is the server.** It fetches
every feed centrally, for everyone, and the browser only ever talks to
Feedly. That is a different product with a different bargain, not a smaller
version of this one.

**As a desktop app, yes.** Checked against the actual dependency set rather
than assumed:

- Room 2.8.4 publishes a `standard-jvm` variant, so the database and every
  DAO port unchanged.
- OkHttp, Rome, Readability4J, Jsoup, TagSoup and Moshi are plain JVM.
- DataStore is multiplatform.

The work is three things: `androidx.compose` → Compose Multiplatform, which
brings the theme, shape scale, Inter and the whole card set across because the
Material 3 API is near-identical; Coil 2 → 3 for images; and replacing
WorkManager with a coroutine scheduler, which is simpler on desktop than on
Android. What does not come across: the launcher overlay, and dynamic colour,
since Material You is an Android API — desktop would use the static palette.

**What OPML does not carry.** It is the source list and its categories. No
read state, no bookmarks, no article state of any kind. A desktop reader seeded
from OPML opens with everything unread, including the hundreds already dealt
with on the phone, and the two drift apart from that moment. That makes it *a
separate reader that happens to have the same feeds*, not the same reader on a
bigger screen. Continuity needs §7; once §7 exists a desktop client inherits it.

**Order:** after §7, after v1 ships. The styling it would reuse is the part
still changing weekly, and adding a second platform while testers are being
recruited works against the testing. The cheap de-risking step, if it is
wanted earlier, is splitting `:core` from `:app` as a pure refactor with no
desktop module — nothing user-visible, but the seam exists while the code is
fresh.

### 13. Bookmarks in, feeds out

**Built, as the folder-scoped version this section argued for.** Choose a
browser's bookmark export, tick the folders worth scanning, and Whisper looks
up the feeds itself: grouped by site before anything is fetched, probed at the
origin rather than at each bookmarked path, six at a time. Each result says
whether the site declared its feed or Whisper guessed a common address — two
states, because a middle band adds a word and changes no decision. The folder
name becomes the category, which is what makes §6's clustering fire at all.

Nothing is uploaded. The file is parsed on the phone and the phone does the
fetching, which is a stronger answer than any policy.

**Not yet tested against a real export.** Everything here is covered by unit
tests over fixture HTML — nested folders, unclosed tags, the loose-bookmarks
case — but no live Chrome or Firefox export with real folders has been run
through it on a device. That pass is outstanding and is the gate on calling
this section finished.

The private-address block is a network interceptor rather than a check on the
address typed, and that is the load-bearing detail: OkHttp follows redirects
itself, so a public host redirecting to `192.168.1.1` would otherwise walk
straight past a check made at the start. Every hop is checked.

Still to build from this section: the whole-file scan with its resumable job,
the local discovery cache, and broken-feed recovery — which is the best idea
here and now needs only the `FeedDiscovery` this shipped with.

**Feasible, and cheaper than it looks — most of the engine is already built.**
`FeedParser.alternateFeedLinksAtUrl` already reads `<link rel="alternate">` out
of a page's head and filters on the RSS, Atom and JSON types.
`candidateFeedUrls` already probes eight common paths including the Blogger and
WordPress ones. `normalizeFeedUrl`/`isSameFeedUrl` already answer "is this the
same feed", and OPML import and export already exist. What is missing is a
bookmark parser, a queue, and a review screen — plumbing around code that
works.

The idea is a good one and the framing is the right one: *the user gives
Whisper the websites, Whisper finds the feeds.* Nobody should have to search
for "BBC RSS feed".

#### Three parts cannot be built, because they need a server

The proposal was written for something with a backend. Whisper has none, and
§12 records why it must not acquire one.

- **The shared discovery cache** — "if another Whisper user later imports the
  same website, the known feed can be tested first" — requires a service that
  sees which sites users are subscribing to. That is precisely the bargain
  declined for Discover and for the web version. A **local** cache, keyed by
  domain on the device, is worth having and costs nothing.
- **The standalone web tool** — same CORS wall as §12. A browser cannot fetch
  arbitrary third-party sites, so the tool would need a server proxying every
  scan, and that server would see every bookmark file uploaded to it.
- **The privacy section's framing.** It asks what happens to bookmark data
  "sent to the scanning service". Nothing is sent anywhere: the file is parsed
  on the phone and the phone does the fetching. That is a stronger answer than
  any policy could be, and the screen should say so plainly rather than
  reassure.

#### SSRF is still a real concern, for a different reason

Blocking private address ranges matters here too, but not because a server is
being tricked into reaching its own network — because a *phone* would be
reaching the user's own LAN. A bookmark, or a redirect from one, pointing at
`192.168.1.1` would have Whisper probing the household router, printers and
NAS for `/feed.xml`. Block the private ranges and `localhost`, check the
destination again after every redirect, and allow only `http` and `https`.
The proposal's list is right; only the reasoning changes.

#### Pick folders, not a file — this is the design, not an option

The proposal treats folder preservation as a checkbox near the end. It is the
whole feature.

Importing an entire bookmark collection has a yield problem: bookmarks are a
junk drawer, not a reading list. Amazon products, Stack Overflow answers, a
router admin page, forty half-read docs pages. Most of it publishes no feed,
and a good share of what does is a site someone bookmarked once for one thing.
Reviewing four hundred results to keep thirty is not obviously less work than
adding thirty by hand.

**A folder is not a junk drawer.** Someone who keeps a News folder and a Tech
folder has already done the curation, by hand, over years — and did it because
they read those sites. Scanning only the chosen folders takes the yield from
"a fraction of what is found is wanted" to "nearly all of it".

It also changes what has to be built. Forty sites is not eleven hundred: it is
under a minute, and it needs no background job, no Wi-Fi-only default and no
resumable queue. Those become a later concern for whoever does want the whole
file, not a prerequisite.

And the folder names are the categories. Whisper already has categories, so a
News folder becomes the News category — which is exactly what the breaking-news
detection in §6 reads, since it only considers sources filed under news.

**That is more than a convenience, and it is the argument for building this
sooner rather than later.** Clustering needs several sources covering the same
story, filed under news. Someone who has added eight feeds by hand has neither
the sources nor the categories, so the feature that most distinguishes the app
is switched off by default and would do nothing if it were switched on.
Importing a News folder produces precisely the input it needs — a dozen news
sources, already categorised, in one action. The importer is not only how
people get started; it is what makes the clustering fire at all.

The same holds for the weighting generally. Reading habits, source affinity and
the diversity rules all want a feed with breadth in it, and all of them are
inert on a handful of hand-added sources.

So the first screen is not "choose a file" but, after choosing one, a folder
tree with checkboxes and a count beside each:

    ☑ News          14 bookmarks
    ☑ Tech          22 bookmarks
    ☐ Shopping      88 bookmarks
    ☐ Work         310 bookmarks

#### Scale is the actual engineering problem, once the whole file is in scope

The spec's worked example is 1,482 bookmarks and 1,126 unique URLs. Costed
honestly on a phone:

- 1,126 page fetches for autodiscovery, at perhaps 100 KB each, is **~110 MB**
  before any probing.
- Suppose 40% advertise a feed. The other ~676 fall through to probing, and
  `candidateFeedUrls` currently generates up to 16 candidates per URL —
  **~10,800 further requests**, nearly all of them 404s.
- At a dozen concurrent connections that is **twenty minutes to an hour** of
  continuous radio, and a meaningful bite out of a mobile data allowance.

Three things make it tractable, and the first is by far the biggest:

1. **Group by domain before probing.** Fifteen BBC bookmarks are one site to
   probe. This collapses the long tail more than any other change.
2. **Probe the origin, not the path.** `candidateFeedUrls` tries both because
   the Add Feed screen handles one URL at a time and can afford to; a scan of a
   thousand cannot.
3. **Wi-Fi by default, as a resumable WorkManager job.** The scan has to
   survive the screen going off and the app being swapped out — an hour is
   long enough that requiring the user to sit and watch it is not an option.

#### Cut from the first version

- **CMS-specific rules.** WordPress, Ghost, Substack, Blogger and Medium all
  advertise their feeds in `<head>`; autodiscovery already catches them. This
  is a page of code for cases that are handled.
- **Three-level confidence.** Two states carry the whole distinction that
  matters: the site *said* this is its feed, or Whisper *guessed*. A middle
  band between them adds a word to the UI and nothing to the decision.

#### The best idea in the document was buried at §46

**Broken-feed recovery — now §18, and built.** When a subscribed feed starts failing, run discovery
against its site and offer the replacement. Whisper already surfaces broken
feeds, so this is one screen and a use of an engine built for something else —
and it fixes a problem every RSS reader has and none of them solve. It may be
worth building *before* the bookmark importer: it needs only the engine that
exists today, and it earns its keep on a feed list of forty as much as on a
bookmark file of two thousand.

#### What the first version should be

One `discoverFeeds(url)` entry point, shared by the Add Feed screen (which
should stop having its own), bookmark import, bulk paste, and broken-feed
recovery — the proposal's §49 instinct is right and the reason to follow it is
that four half-implementations is how this goes wrong.

Then: parse Netscape `bookmarks.html`, show its folder tree and scan only what
is ticked. Drop non-http schemes, normalise, group by domain, scan with bounded
concurrency, validate that what came back parses as a feed and has items,
deduplicate several bookmarks onto one feed, and show a reviewable list with
the four states that matter — found, several found, none found, unreachable.
Each folder becomes a category on the feeds that came out of it.

Not deleting anything is the rule here as everywhere: a bookmark with no feed
is reported, never silently dropped.

**Where it goes:** the engine first, in the Add Feed screen and in broken-feed
recovery, where it is small and immediately useful and gets exercised daily.
Folder-scoped import lands on top of a proven engine and is an afternoon's
plumbing rather than a gamble. Whole-file import — with the job queue, the
data-use warnings and the domain grouping that scale needs — only if anyone
asks for it.

### 14. Google Drive — somewhere to put the OPML

Previously recorded here as not being pursued, on two objections. One was
wrong and the other is avoidable, so it is back.

**The framing was wrong.** "It syncs your devices rather than your reading" was
written as a dismissal and is actually the point. Google Reader sync needs a
server the reader chose and probably runs; plenty of people will never do that
and still stand to lose every subscription to a factory reset. For them a Drive
backup is not a lesser sync — it is the only thing standing between them and
starting again. The two features serve different people and should not be
weighed against each other.

**The Play Services objection is avoidable.** Google Sign-In needs Play
Services; the Drive REST API does not. `AppAuth` performs a standard OAuth2
flow in a browser tab and hands back a token the plain REST endpoints accept,
so the app can talk to Drive with no proprietary dependency and keep building
on F-Droid. F-Droid will mark it NonFreeNet, which is accurate and applies to
every network service.

**One objection does stand and should be said out loud in the interface.**
Uploading a backup means Google holds the reader's subscription list. That is
their own Drive and their own choice, and it is categorically different from a
recommendation service profiling them — but this app tells people nothing
leaves their phone, so the screen that offers this has to be equally plain that
turning it on is the exception.

**Scope is deliberately small: this is the existing export, sent somewhere
automatic.** Not continuous synchronisation — that is §7's job and the two
should not be confused. This is "my subscriptions are safe", nothing more, and
keeping it that narrow is what makes it a week rather than a milestone.

OPML export and bookmark export both work today and already produce exactly
the right bytes; all that is missing is a destination that is not a file
picker. In order:

1. ~~**`appDataFolder`, not the visible Drive.**~~ **Built, and not this way.**
   Reaching Drive's own API needs an OAuth client registered against the app's
   package and signing certificate — a step this project cannot take on its
   users' behalf, that would need repeating for the debug build, the release
   build and any fork, and that would have left the feature unusable until
   somebody did it.

   The **Storage Access Framework** needs none of it. Drive ships a
   `DocumentsProvider`, so it appears in the system's own folder picker, and a
   persistable permission lets the app keep writing there afterwards. No OAuth,
   no client id, no Google dependency to declare for F-Droid.

   It is also plainly better: Dropbox, OneDrive, Nextcloud and an SD card are
   all in the same picker, so this is not a Google feature spelled generically
   — it works wherever the reader already keeps things. The file is visible in
   their storage rather than hidden, which for a backup is the right way round.
2. **The OPML.** Built. Sources and their categories, written by the same code
   the manual export uses — a destination, not a second format. One file,
   overwritten: a backup that accumulates is a folder somebody has to tidy,
   and the second-newest copy of a subscription list has never been the one
   anyone wanted. Bookmarks can follow; they are not what anyone means by "I
   lost my feeds".
3. **Restore.** Built, as a file picker that accepts any OPML rather than only
   one Whisper wrote — somebody arriving from another reader has an export of
   their own and it is the same file. Additive, like the import it reuses: a
   feed already subscribed is left alone, and nothing local is removed. A
   restore that deleted whatever the file did not mention would be a far more
   dangerous operation than the word suggests.

   **Offered during onboarding**, on the starter-sources step and above the
   list rather than under it: a returning reader should not have to scroll past
   nine feeds they do not want to reach the one thing on that screen that is
   for them. A successful restore ends onboarding immediately — their own list
   is back, and a starter list would be nine unasked-for feeds on top of the
   ones they spent years choosing.

   There it takes a **folder** and restores both files at once, which
   contradicts the settings screen on purpose. There, restoring the settings is
   a separate button because overwriting every preference on a phone somebody
   has already arranged is dangerous. On a phone installed minutes ago there is
   nothing to overwrite, so making them pick two files out of one folder would
   be ceremony protecting nothing. The folder is also kept as the backup
   destination: they have just told the app where their backups live, and
   asking again later would be asking a question already answered.
4. **Automatic, daily.** Built. It waited for unmetered Wi-Fi until September
   2026; that rule is gone, because the two files are a few kilobytes and a
   folder on the phone never touched the network at all. A cloud folder waits
   for a connection of any kind, a local one for nothing but a battery that is
   not low. A manual export is not a
   backup, it is a thing people mean to do, so the scheduled version is the
   feature and the button is the reassurance. Daily rather than hourly: a
   subscription list changes a few times a month, and rewriting an identical
   file to somebody's cloud storage every hour would be rude to their storage
   and their battery for nothing.
5. **The settings, in a second file beside it.** Built. `whisper-settings.json`
   — every preference with its type, because DataStore is typed and a JSON
   number does not say whether it was an Int, a Long or a Float, and a value
   read back as the wrong one throws a long way from where it was written.

   **Not inside the OPML.** The whole value of writing an OPML is that Feedly,
   FreshRSS and Thunderbird can read it; smuggling this app's furniture into
   its head would make a portable file into a private one. Two files in one
   folder costs nothing and keeps the interchange format honest.

   Two preferences are held back deliberately. The backup folder is a Uri
   permission granted to *one* install by the document picker — on another
   phone it names a grant that does not exist, and restoring it would leave the
   screen claiming a destination it cannot write to. The last-run timestamp
   goes with it for the same reason.

   Restore is a **separate button** from restoring sources, and that is the
   point rather than an oversight. Adding somebody's subscriptions to a new
   phone is additive and safe; overwriting every setting on a phone already
   arranged the way they like it is not. Unknown keys are skipped rather than
   written, so an older build reads what it understands from a newer backup and
   leaves no junk behind.

6. **Everything that can send data anywhere is off until asked, including
   Android's own backup.** The platform backup was on by default — the
   platform's choice, never anyone's here — quietly copying the reading
   database, subscription list and every preference to the reader's Google
   Drive. An app whose claim is that nothing leaves the phone cannot also do
   that in the background.

   The first pass simply turned it off, which traded one imposed default for
   another: somebody who *wants* their apps to follow them to a new phone had
   that decided for them too. Choice is the point, so the reader is asked.

   **The mechanism is not the obvious one.** `allowBackup` is a manifest
   attribute read at install time and `BackupManager` cannot enable or disable
   a backup, only request one — so no settings switch can turn the platform
   backup on and off. What an app *can* control is what it hands over when the
   system asks. `PlatformBackupAgent` overrides `onFullBackup`, reads the
   reader's choice, and returns without writing anything if they have not made
   it. The system sees an app with no data rather than an error.

   **Two routes, asked separately**, via `getTransportFlags()`: a
   phone-to-phone transfer is a direct copy to the reader's next handset with
   no server in it, and a cloud backup puts the same data in Google's hands.
   Plenty of people want the first and not the second, and one switch for both
   would force them to choose the stricter answer.

   Both default to off, and the sign-in tokens are excluded on every route
   whatever is chosen — their key lives in the Keystore, which is never part of
   a backup, so restoring the ciphertext without it means the first read throws
   and the app will not start.

   Verified in the built APK rather than the source, and in the release mapping
   file: R8 leaves the agent unrenamed, which matters because the manifest
   names it as a string.

Because it is a file rather than a live connection, there is no reconciliation
to design, no conflict to resolve and no id mapping — the three things making
§7 the larger piece. Uploading a copy of a file that already exists is most of
the work.

**Where it goes:** it does *not* need to wait for §7, and it should not be
built on §7's `RssService` seam either — an earlier draft of this note said it
should, on the reasoning that Drive is a provider in the same sense. It is not.
That interface is about reconciling with a service that has opinions about
read state; this uploads a file. Forcing it through would mean implementing
`sync()` as "write the OPML" and leaving every other method empty, which is a
worse description of what is happening than a plain backup class.

Being independent of §7 also makes it the better thing to ship first: it is
smaller, it needs nothing that is unfinished, and it protects the thing people
would actually grieve.

### 15. Onboarding and the guided tour

Built. Two things, and the second is worth more than the first.

**The welcome.** Three panes over the supplied artwork, picked by the rendered
surface's luminance rather than the theme setting — the theme has three values
and one of them is pure black, so what the copy has to sit against is whatever
actually got painted. Skip is on every pane, not only the last: somebody who
knows what an RSS reader is should not have to swipe through three panes to be
let in, and Back on the first pane leaves rather than trapping them in a
carousel on their first minute with the app.

**The tour**, which is the part that teaches anything. Six stops on real
controls: the glance row, the category chips, an article card's menu, the
bookmarks toggle, the filter button and the settings menu. Six is the limit —
every extra stop is another chance for somebody not to finish, and a tour
nobody finishes taught nothing.

Three details are worth recording because they are where this goes wrong:

- **`querySelector` does not translate.** The web version finds its targets by
  selector and measures them with `getBoundingClientRect`. Compose has neither
  and cannot be given them, so the targets announce themselves instead:
  `Modifier.tourTarget(TourTarget.Chips)` writes its own bounds into a map the
  overlay reads. The compiler then guarantees a step names a control that
  exists, which no string selector could.
- **The hole needs an offscreen layer.** `BlendMode.Clear` without
  `CompositingStrategy.Offscreen` punches through to black rather than to the
  app underneath. One line, and the whole effect depends on it.
- **Look, don't touch.** Advancing is always the tooltip's own button, never a
  tap on the lit control, so the tour never has to guess whether the real
  interaction happened the way it expected — and a mis-tap cannot navigate away
  mid-tour.

**Never on an upgrade**, and there is no version number to check against: the
preference does not exist on either an upgrade or a fresh install. So the
question is answered by the only honest signal available — an install that
already has sources has been used, and is stamped as having seen both without
being shown either. A genuinely new install has no sources, so nothing is
stamped and the welcome runs. It stays correct if they close the app halfway:
still no sources, so still new.

**The tour waits for articles.** Separate flag from the welcome, because the
two wait for different things: the panes can be shown to an empty app, the
tour points at real controls holding real headlines, and spotlighting an empty
feed would look broken at precisely the wrong moment. On a new install that is
minutes after the welcome, not seconds.

The step machine is pure and tested — advancing, a target that is not on
screen, the last step, and the counter. A control can be missing for perfectly
ordinary reasons (the glance row is a setting, the chips need categories), and
a tour that stalls pointing at nothing is worse than one a step shorter, so
absent targets are stepped over and the counter says "1 of 4" rather than
promising six stops that will not arrive.

Replayable from Settings, next to "What Whisper has learned".

**The starter sources.** A fourth step after the panes: nine feeds across
World, Technology, Science and one regional, ticked by default and every name
visible before anything is agreed to. Offered rather than applied, because the
pane two screens earlier says no algorithm decides what you see and
subscribing somebody silently would make that a lie.

Nothing marks them as built in. Once subscribed they are ordinary sources,
removed exactly like any other — a "starter" flag would exist only to stop
somebody deleting a feed they did not choose.

Four world sources rather than two, deliberately: breaking-news clustering
needs three carrying the same story before it groups anything, so a shorter
list would leave the feature switched on and never firing, which looks like it
does not work. They are tagged, which fills the category chip row on day one —
the tour's second stop points at it, and untagged that step gets skipped.

The regional feed is ticked only where the phone's region matches. Still
listed everywhere, because hiding it would be worse, but a reader in Berlin
should not be handed an Australian national broadcaster by default.

Bundled rather than fetched: a list downloaded on first launch means the app
phoning home before the reader has done anything, which is not a promise worth
breaking to save an app update. The same list is reachable afterwards from
Settings, minus whatever is already subscribed.

**The launcher page.** Built as a settings screen rather than an onboarding
pane, which is the same reasoning taken one step further: the steps are
genuinely obscure — Lawnchair keeps a hardcoded whitelist and the way past it
is a hidden command typed into the app drawer — and the people who need them
are holding the phone, not reading the README. But leading a first run with
setup for a launcher most readers do not have tells them they downloaded the
wrong app. So: in Settings, where somebody who wants it will look.

"Connected" is said only when true. There is no way to ask a launcher what it
has selected, so the only honest signal is a bind to `OverlayService` — proof
of success, never proof of failure. After a process restart it reads false
until the launcher next asks for the page, and saying "not connected" then
would send somebody back through four steps they had already done.

§14's restore offer lives on the starter-sources step — see that section.

Still to do: nothing on this section. The Lawnchair pane became a settings
screen instead, for the reason above.
---

### 16. The reader and the browser, made to match

Done. The two screens showing the same kind of thing no longer look unrelated.

- ~~**The same margins.**~~ Both sat at four points while the feed sat at
  sixteen, so an article opened from a card that started sixteen points in
  began four points in. One margin now, named once and shared.
- ~~**Justified text.**~~ With hyphenation, which is not optional alongside it:
  a justified column this narrow and unhyphenated opens rivers, the spaces
  stretching to fill each line until the eye follows the gaps down the page
  instead of the words across it.
- ~~**The source's favicon beside its name.**~~ The same mark the cards carry
  and already cached from the feed. A byline with a face on it is recognisable
  at a glance; a line of text has to be read.
- ~~**Share instead of a menu.**~~ The overflow held two items and the second
  was a summary service nobody uses.

Two things found while doing it, both of which would have shipped:

- **The reader attributed articles to "Neo Feed".** A source with no title fell
  back to that literal string, and the same string was the sentinel it compared
  against. It uses this app's name now.
- **Changelog and Licence were Neo Feed's.** The changelog screen showed that
  app's release notes from 2023, and the licence page carried its name and
  copyright alone. Both rewritten — the licence now names the whole line, since
  a GPL fork has to credit what it was forked from.

### 18. Broken-feed recovery

Built, and promoted out of §13 where it was a paragraph. It fixes a problem
every RSS reader has and none of them solve.

A feed's address changes — a site moves to a new CMS, drops `/rss` for `/feed`,
changes host — and every reader in the world treats that as the feed having
died. The site is usually still there, still publishing, still advertising the
new address in its own head. Nobody looks, so nobody finds it, and the reader
eventually notices a silence and unsubscribes from something that was working
all along.

**A failure had to be recorded before it could be noticed.** It was logged and
forgotten: the syncing flag was cleared, `lastSync` left alone, and nothing
anywhere said a feed had stopped working — so a dead feed was
indistinguishable from a quiet one. `Feed.consecutiveFailures` counts them and
`failingSince` records when the run began; any success wipes both, because one
bad afternoon on somebody's server is not a broken feed.

Three failures in a row before it is mentioned. A single timeout is noise, and
warning on one is how a warning becomes something people learn to ignore.

Looking is one request through the `FeedDiscovery` §13 shipped with. Three
answers, and they are different problems: a **different address** is the
useful case and is offered; the **same address** means the feed did not move
and something else is wrong, so offering to "fix" it by writing the identical
URL would be theatre; **nothing found** may mean the site stopped publishing,
which only the reader can judge, so the offer there is to stop warning rather
than to unsubscribe.

Accepting updates the feed **in place**. Every article, the read state, the
categories and whatever the weighting has learned about that source survive —
a subscription somebody has had for years is not worth losing to a URL change.

**Proven on the device** for the common case: a feed whose address changed on
the same site (a new path, `/rss` to `/feed`) was found and fixed in place
several times.

#### Still open: a site that moved completely

When a publication changes domain, looking on the old site finds nothing, and
the only way back today is to find the new address yourself and edit the feed.
Wanted: a **search** from the broken-feed offer — type the publication's name,
or accept its title as the query, and pick the new feed from the results.
Accepting would still update the feed in place, so nothing is lost.

The open question is where the results come from. Whisper has no server and no
search engine of its own: the candidates are the bundled feed library (offline,
but only the sites it already lists), a public feed directory, or a web search
through one — each of which sends the name typed to somebody, and would have to
be said so in `PRIVACY.md` before it is built.

---

### 19. The audit's leftovers, in order

Four things the September audit found, judged worth doing properly rather than
half-doing in the same pass. Ordered by what they buy against what they cost,
not by how interesting they are.

#### 19a. ~~Strip the logs from the release build~~ — done

Four `-assumenosideeffects` lines for `Log.d`, `Log.v` and `Log.i`. `Log.w` and
`Log.e` stay: a crash report with no preceding warning is one nobody can act
on.

**Verified against the shipped dex rather than assumed.** Every `Log.d/i/v`
literal in the source was extracted and searched for in the preview APK's dex
files, and every `Log.w/e` literal checked for wrongful removal. Result: 33
warning and error lines all present, and every line that carried an address
gone — the full-text fetcher's `Fetching full page <url>`, the discovery pass's
`Suggesting <host>`, `requestFeedSync`, the Mastodon and cleanup lines, the
launcher's message log.

Three survive, and it is worth writing down why. Each is the final statement of
a `try` inside a suspend function, where the call's `int` result is the block's
value before being coerced to Unit, and R8 will not remove a call in that
position. The same file's other log, which is *not* last in its block, went.
What the three actually print is `Exported OPML in 412 ms`, `Imported OPML in
88 ms` and `Exported Bookmarks successfully` — a duration and a success marker,
no address, no title, nothing about what anybody reads. Contorting three
functions to move a log statement off the end of a block would buy nothing, so
they stay.

Two of them also lost a `${Thread.currentThread().name}` while this was being
worked out. That was a diagnostic from an old threading investigation, and it
was doing real work on every OPML import and export to produce a string that is
thrown away.

#### 19b. ~~Index the read state~~ — done

`CREATE INDEX index_Article_readAt ON Article (readAt)`, database version 18.
No column added, no row rewritten, no behaviour changed; SQLite builds it in
one pass over a table that is thousands of rows rather than millions.

The migration's index name has to match what Room generates for the entity or
Room's schema validation fails on the next open and the app will not start —
checked against the generated `18.json` rather than trusted.

#### 19c. ~~Stop blocking on preference reads~~ — done

**The diagnosis in this section was half wrong, and the correction is the
interesting part.** It said fifty disk reads. It is one. DataStore keeps the
loaded file in memory and serves every later collection from it, so only the
first read in a process touches the disk.

What the other forty-nine were is worse in a way that is harder to see:
`runBlocking(Dispatchers.IO) { flow.first() }` stops the calling thread, hands
the work to the IO pool, and waits for it to come back. That is fast whenever
the pool has a free thread, and unbounded when it does not — and what saturates
the IO pool in this app is a sync, which is what runs while somebody is reading.
Not a slow app. An occasionally, unreproducibly frozen one.

**What was built**

- `PrefCache` holds the whole preferences file in memory, filled once at
  startup by the preferences singleton's constructor — before any screen
  composes — and kept current by one collector. Reading a preference is now a
  volatile field load and a map lookup, on any thread, waiting for nothing.
  The blocking path survives only for the window before that first fill, which
  a worker on a cold process can still hit, and where answering with a default
  instead of the reader's actual setting would be the worse failure.
- `runBlocking` on that fallback path lost its `Dispatchers.IO`. DataStore does
  its file work on its own scope regardless, so dispatching there only added a
  second thread to wait for.
- `PrefDelegate.set()` writes on a scope that outlives the screen. `setValue()`
  still blocks and is still right for workers, which must know the value landed
  before they finish.
- `PrefDelegate.asState()` replaces the fourteen hand-written
  `collectAsState(initial = remember { pref.getValue() })` lines. Those were
  themselves a repair — without the `remember`, the initial argument was
  re-evaluated on every recomposition — which is the argument for having one
  helper instead of fourteen chances to get it subtly wrong.
- StrictMode in debug builds, watching for main-thread disk work, network on
  the main thread, and leaked activities and receivers. Logged rather than
  fatal: the platform itself trips it, and a build nobody can run is a build
  nobody turns on. This is step 1 of the plan, kept, so the next one of these
  is found by the machine rather than by reading fifty call sites.

**A bug this fixed on the way past.** The theme-selection dialog wrote the
chosen value in a coroutine started on `rememberCoroutineScope`, on the line
after the one that closed the dialog. Closing it removes the composable, which
cancels that scope. The write was racing its own screen's destruction and could
simply not happen. It goes through `set()` now, and the ordering is no longer
load-bearing.

**Two mistakes worth recording, both caught before they shipped.** `PrefCache`
was first written as a single process-wide snapshot — true of this app, which
has one DataStore, and wrong as a design: the first test written against it
warmed the cache through one store and read it back through another. Keying it
by store cost a hash lookup and made it testable, which is the same property as
being able to reason about it. Then `dataStore !in snapshots` on a
`ConcurrentHashMap` resolves to `containsValue`, not `containsKey`, so the
"prime once" guard would never have matched and it would have blocked on every
call — the compiler caught that one.

Six tests over a real DataStore on a temporary file, not a fake: what is being
tested is how the cache behaves relative to the store, and a fake store would
only test the fake.

#### 19d. Coil 2 to 3 — after 1.0, not before

Recommendation: **not yet**, and not because it is hard.

Coil 2.7.0 is the final 2.x release and still maintained; Coil 3 is where new
work happens and eventually where fixes will. So this is a "when, not if", and
the argument is only about timing. Against doing it now: it is a breaking API
change across every place the app shows a picture, it fixes nothing anyone can
observe today, and it would land in the same build as the first signed release
— which is the one build that should carry as little new risk as possible.

The right moment is the first time image handling is opened for its own reasons.
The custom `ImageLoader` the audit added is the natural seam: it already puts
the cache limits and the private-network interceptor in one place, so the
migration has somewhere to land instead of being spread across call sites.

#### 19e. Material 3 Expressive — after the store listing, not before

Recommendation: **after launch**, and the version numbers decide it rather
than taste.

The app is on `androidx.compose.material3:1.4.0` via Compose BOM 2026.08.00,
which is the current stable. Checked against the artifact rather than from
memory, 1.4.0 already carries:

- `MaterialExpressiveTheme(colorScheme, motionScheme, shapes, typography)`,
  public;
- `MotionScheme` and `MaterialTheme.motionScheme`, with the spatial and
  effects specs;
- `LocalUsingExpressiveTheme`, which existing components consult and change
  their defaults under;
- `ShortNavigationBar`, `WideNavigationRail`, Carousel.

And carries these as `internal`, so they cannot be called: the `MotionScheme`
factories (`standard()`, `expressive()`), the emphasized type roles
(`displayLargeEmphasized` and its siblings), and the increased shape tokens
(`largeIncreased`, `extraLargeIncreased`, `extraExtraLarge`).

Absent entirely: `ButtonGroup`, `FloatingToolbar`, `LoadingIndicator`,
`SplitButton`, `FlexibleBottomAppBar`. Those are in 1.5.0, which is at
`alpha28` — alpha, not beta, and nowhere near the build that should carry the
first signed release.

The sharper point is that the two new components 1.4.0 *does* ship are a
navigation bar and a navigation rail, and this app uses neither. So on stable
today, "adopt Expressive" means one line at the two theme entry points and
nothing else — which then changes shape and motion defaults across 108
`Button` call sites, 25 `TopAppBar`, 20 `Card`, 15 `CircularProgressIndicator`,
7 `LinearProgressIndicator`, 6 FAB, 6 `Switch` and a `Slider`.

That lands squarely on the card geometry and the overlay panel, both of which
were tuned by measurement against a launcher surface that is translucent and
not ours. With no screenshot tests (see *Debt worth clearing*), the whole
verification would be somebody looking at it on a phone.

Three routes, in preference order:

1. **Wait for 1.5.0 stable**, then adopt theme and components together. This
   is the plan.
2. **Take the look without the framework** — adopt the larger corner radii and
   heavier heading weights into `WhisperShapes` and the typography directly.
   No alpha dependency, no behavioural surprises, and the values stay ours.
   Worth doing at any point if the look is wanted sooner than the components.
3. **Switch `MaterialTheme` to `MaterialExpressiveTheme` on 1.4.0 now** —
   cheap to try behind the existing theme preference, and the only honest way
   to see it. Not to be merged on the strength of the diff being small.

The natural moment is the same one §19d names for Coil: the first time the
theme is opened for its own reasons. `ui/theme/Theme.kt` is the seam — two
entry points, already passing `colorScheme`, `typography` and `shapes`
explicitly, so a fourth argument has somewhere to go.

### 20. Tablets and large screens

Built, and proven on a **Samsung Galaxy Tab S5e** (SM-T720, Android 11,
2560×1600, about 1280×800 dp) beside the Pixel 10 Pro. The tablet was set up
from the phone's OPML backup and then signed in to the same FreshRSS account,
so both devices carry the same 114 feeds and the same read state.

**Columns.** The feed takes as many columns as the width holds, from the width
itself rather than a phone/tablet switch:

| Layout | Column width | On the S5e, landscape |
|---|---|---|
| Mosaic | 200 dp a lane, 2 to 4 | 4 |
| Cards, Magazine, List | 360 dp a column, 1 to 3 | 3 |

With an article open beside the feed, the feed keeps 360 dp and the article
gets the rest. The first attempt checked the pane scaffold's own "detail is
expanded" flag, which a tablet reports as true with nothing open, so nothing
changed on the device; it now asks whether an article is actually selected.

**Lead stories.** A Large story spans the row on a phone's two Mosaic lanes and
nowhere wider. Across four lanes a full-width tile was a banner, and the
staggered grid can only span one lane or all of them.

**Speed on an older device.** The first tablet report had 18.3% of frames slow
while photos were decoding and 0.9% while none were, with single WebP decodes
at 100–240 ms. Android 11 and below, or a low-memory device, now decodes two
photos at a time instead of Coil's four; slow frames while decoding fell to
7.0%. Newer devices keep four, and Diagnostics says which is in force. Two
ideas were not built: a different decoder (the app already uses BitmapFactory;
the HEIF errors in the log were about eleven AVIF images Android 11 cannot open
at all), and holding photos back during a fling.

**Read articles keep their card.** The tablet showed a wall of thin rows where
the phone showed cards for the same stories. Every one of the rows was an
article already read — by sync from the phone, or by scrolling past three
columns at a time — and reading dropped an article to the smallest size. A
read article now keeps Medium if it would have earned Medium unread; reading
still costs it the large slot. This changed the phone too, on purpose.

**Found on the way, and fixed for both.**

- Day headings — **Today**, **Yesterday**, then weekdays and dates — in
  chronological order, not while searching.
- "Updated 12m ago" sits at the right of the first day heading instead of on
  a line of its own; it keeps its own line when the feed does not open on a
  heading.
- Share left the cards (it is in the ⋮ menu); save and the menu are drawn as
  a pair at the card's edge.

**Not done, on purpose.** No bottom navigation bar and no summaries on the lead
cards: both were offered after a comparison with Feedly and declined.

### 21. From the device, 26 September 2026

Built after a day of use on both devices; each item is on main and in the
preview build.

- **Read later.** Saving an article downloads the whole of it at once, as its
  own work so it cancels nothing, on the same network and battery conditions
  as every other advance download. A save received from the account does the
  same. Saved articles are never cleaned away, so the text stays until the
  save is removed.

  **Pictures too.** The first version kept the text only, and offline a saved
  article came up as text between grey placeholders: the reader asked the web
  for each picture on opening, and Coil's disk cache, shared with the whole
  feed, keeps nothing for certain. Each picture is now downloaded with the
  text — the srcset candidate for the phone's own screen, forty at most, none
  over 8 MB — into one folder by address, and the reader looks there under
  every address an image offers before asking the web. Removing the save
  deletes them. Saves made before this get their pictures at the next start.
  A signal lost halfway is not recorded as done. The empty Saved screen, the tour and the header call it
  Read later. **Confirmed on the device**, text and pictures, offline.
- **Saves between devices.** A report from a third phone (Sony XQ-BC52,
  Android 13) showed two saves and no sync that had ever sent one. A save
  can only go to the server by the server's id for the article, and a device
  matched only what the server delivered from a little before its last
  sync — so a save of an older article had no id and was let go unsent, and
  a save arriving from the server was applied only to an article already
  matched. Now: a waiting save is looked for in its own feed on the server
  (a thousand items back) and matched there; one still not found, from a
  feed the server carries, waits for the next sync instead of being dropped;
  the saved list is read with addresses and matched as it is read; and every
  save already on a device is sent once, to recover the ones lost. Unsaving
  still reaches the server; an unsave from the server is still never applied.
  **Confirmed on the devices.**
- **Mark as read in the filter sheet** — Everything, or older than 1, 24 or
  48 hours, with counts and an immediate Undo. See §5's list.
- **"Show articles you have read" remembers Fade.** Turning it off and on in
  the sheet used to come back as Keep, which is how the phone lost its Fade.
  Diagnostics now names the setting ("Read articles:").
- **Backups without a Wi-Fi rule.** A folder on the phone needs no network; a
  cloud folder needs any connection. Both wait while the battery is low. The
  page says which rule applies, and no longer says "no folder is chosen" when
  one is. "Backup and Restore" takes a capital R.
- **The Data sources search takes focus only when tapped.** A trace showed it
  gaining focus twenty milliseconds after the page was rebuilt on the way back
  from Manage categories and the two repair lists, with no tap. The field now
  refuses focus until pressed; TalkBack and hardware keyboards are never
  gated. Confirmed on the device.
- **A test for the stuck-sync notice**, under the debugging switch: the real
  notice with "Test:" in the title, recording nothing. Confirmed on the device.
- **Feeds from your reading** is out of Settings and its weekly pass stopped
  (see "Suggestions, after the device report").

#### Still to test on the device

- Bookmark import from a browser file (§13).
- The daily backup writing on its own, after "Back up now" once.

#### Later

- A feed-options icon for the filter sheet (§5's list), waiting on the asset.
- A search for a feed whose site moved completely (§18).
- Mark as read in the launcher panel, once the panel can offer an undo.
- Release: signing key, then GitHub Releases, F-Droid and Play (§8), after
  the company registration.

### 22. Deep dive, 26 September 2026: security, bugs and screens

A read of the whole app for security and correctness, and renders of the feed
at the screen sizes of the Android phones that sell most. Nothing here was
reported from a device; each item says where it is and what the fix is.

**Severity.** *High*: most people who install the app would meet it, or it
breaks a rule this project set itself. *Medium*: a real fault, in a narrower
case. *Low*: hardening or tidying.

**Status: all done, the same day**, in the order of §22d, each with a test
that fails with the fix taken out. Two things are left, on purpose: the second
half of S6 (moving the token off the deprecated library), and a device check
of B9 (the launcher panel's insets), which renders cannot show.

#### 22a. Security

- ~~**S1 · High · Diagnostics can carry addresses.**~~ The report includes this
  app's log as it stands (`Diagnostics.kt:430`), and release builds keep
  warnings and errors (§19a). Three lines put addresses there: every failed
  feed with its full URL (`RssLocalSync.kt:356`, and the failure message
  built at `:449`), which for some feeds carries a private token; the weather
  request with its query (`WeatherRepository.kt:198`), which is the location's
  coordinates or the place that was typed; and exception messages that quote
  the sync server's address. This breaks the rule that the report never
  carries what anybody typed. **Fix:** log the feed title and the reason, not
  the address; drop the URL from the weather line; and scrub the log section as
  it is read, turning any `http(s)://…` into `<address>` and any
  latitude/longitude pair into `<place>`. A test builds a report after
  failing feeds and a failed forecast, and finds no `://` in it.
  **Done.** Ten lines no longer write an address: the failing feed and its
  failure message, the weather request, a chosen file, a feed's image links,
  a full article's link, the typed search, a bookmark's site and a suggested
  site. The report is scrubbed as one piece as it is built (`LogScrub`):
  anything with a scheme, IP addresses of both kinds, coordinates, and the
  reader's own sync server, account name and place. Tested on lines taken
  from real reports, including the Sony's; a source test fails, naming the
  line, if a warning or error writes an address again.
- ~~**S2 · Medium · No limit on the size or type of a download.**~~ A feed is read
  whole into memory (`RssLocalSync.kt:454`, `body.bytes()`), and so is a page
  for the full article (`FeedParser.kt:410`, `curl`). A feed address that
  turns into a 300 MB podcast episode, or an article link to a video, is read
  in full: out of memory in the middle of a sync, and the data spent on mobile
  data. **Fix:** refuse audio, video and binary content types before reading;
  read through a counting stream capped at 10 MB for a feed and 5 MB for a
  page; and fail that one item, not the sync. **Done.** Audio, video and
  pictures are refused before a byte is read; other types, even
  `application/octet-stream`, which servers really do send feeds as, are
  read to the limit and no further, and a body that declares itself larger
  is refused unread. The feed's history says "too large" or "not a feed" in
  words, where an exception's name would have been three letters in a
  release build. The icon lookup's home page is capped with the article's.
- ~~**S3 · Medium · A settings file can write anything.**~~ The import says unknown
  keys are skipped (`SettingsBackup.kt:136`); the code writes every key, as
  whatever type the file claims (`:142`). A file with the sync frequency as a
  number rather than text makes the next read of that setting throw, at start,
  on every start, until the app's data is cleared. The same happens without
  anybody editing a file: a backup from a version in which a setting had a
  different type. **Fix:** import only the keys `FeedPreferences` declares,
  only as the type it declares, and count the rest as skipped. That also makes
  the comment true. **Done.** The keys register themselves as they are
  declared, so a new setting is restorable with no second list, and a test
  compares the register with the declarations. Two more stay on their own
  phone: the backup-stopped warning, and whether the phone has asked for
  notification permission, which restored on a new phone meant it never
  asked. Tested against a real preference store; the old code failed four of
  the seven tests.
- ~~**S4 · Medium · DNS rebinding gets past the private-network guard.**~~
  `BlockPrivateNetworks` (`SafeAddress.kt:141`) looks the host up itself;
  OkHttp has already looked it up again to connect. A hostile feed's domain can
  give a private address to one lookup and a public one to the other, and the
  guard passes a request into the reader's own network. It takes a hostile
  feed the reader has subscribed to, and the answer only shows in the app, so
  the harm is requests sent blind to a router or a printer. **Fix:** check the
  address the connection actually went to (`chain.connection()?.route()
  ?.socketAddress`), which is one lookup and cannot disagree with itself; and
  add a `Dns` that drops private answers, so the socket is never opened.
  **Done**, the first half, and the real risk was lower than written: all of
  this is https against the system's certificates, and a router cannot
  present a valid certificate for the attacker's domain, so the connection
  failed before any request. The guard now judges the connected address
  (through a proxy, still the name, since the socket is then the proxy's).
  The `Dns` filter was left out on purpose: it cannot tell a target from a
  proxy, and would have cut off anybody behind a proxy on their own
  network. The refusal no longer names the host, which went into the log.
- ~~**S5 · Low · Other apps can open any page inside Whisper.**~~ The web-view
  screen has a deep link (`NavigationManager.kt:141`), and `MainActivity`, which
  must be exported because it is the launcher entry, hands every intent it
  receives to navigation (`MainActivity.kt:274`). Any installed app can open a
  page of its choosing in Whisper's own browser, JavaScript on: a convincing
  "sign in to FreshRSS again". Nothing in the app uses that link; the stuck-sync
  notice, the panel and the settings shortcut use the other three. **Fix:**
  remove it. **Done**, and a test fails if a deep link takes an address
  again.
- ~~**S6 · Low · The account store can stop the app from starting.**~~ The
  encrypted preferences are created with nothing around them
  (`SyncAccount.kt:51`). Backups already leave the file out, so a restore
  cannot cause it, but a failing Keystore (known on some devices after a system
  update) throws there, and takes the app down with it. The library is also
  deprecated. **Fix:** catch it, delete the file, start signed out, and say so
  on the account screen; later, keep the token in DataStore under a Keystore
  key of our own. **Done**, the first half: the file and its key are deleted,
  the store starts again, and the account screen says why the reader is
  signed out. If even a new store will not open, the account is kept in
  memory while the app runs, never written down unencrypted. Robolectric,
  which has no Keystore at all, is the test: the old code threw at start.
- ~~**S7 · Low · Feed passwords answer any challenge.**~~ A feed address with
  `user:password@` in it gets an authenticator (`FeedParser.kt:371`) that sends
  those details to whoever asks for them, including a different host the feed
  redirected to, and to any proxy (`:384`). **Fix:** answer only a challenge
  from the feed's own host, over https; remove the proxy authenticator.
  **Done.**
- ~~**S8 · Low · Signing in follows a redirect with the password.**~~ OkHttp
  follows a 307 or 308 by sending the same body again, so a server that
  redirects `accounts/ClientLogin` elsewhere hands the email address and
  password to the new host (`GoogleReaderApi.kt:99`). Only the reader's own
  server could do this, so the case is a server set up wrongly or taken over.
  **Fix:** do not follow redirects for the sign-in; report one as "the address
  has moved" and show the new host. **Done**, with one allowance: a redirect
  to the same server (a trailing slash, a moved folder) is still followed,
  since the password goes nowhere new. Tested against a local server that
  redirects to another name for the same machine.

**Checked and fine.** Deeply nested HTML (jsoup's depth limit), cleartext
(off everywhere), file access in the WebViews (off), credentials in backups
(left out of every route), and the intent-redirection shape (removed from
`MainActivity` earlier).

#### 22b. Bugs

- ~~**B1 · Medium · Unsaving one article can take another's pictures.**~~ Saved
  pictures are stored once per address, so two saved articles that carry the
  same picture (one story in two feeds, a site's standard header) share a
  file. Unsaving either deletes it (`SavedImages.kt:112`), and the other shows
  grey boxes offline. **Fix:** before deleting, keep any file another saved
  article's list still names. **Done**; the test fails, saying the other save
  lost its picture, with the fix taken out.
- ~~**B2 · Medium · Saved-article downloads pile up while they wait.**~~ Each one
  is appended to the last (`FullTextParser.kt:85`). While the conditions are
  not met (no Wi-Fi with full articles off on mobile data, or the battery low)
  every save, and every sync that brings a save from another device, adds
  another. Nothing is lost, but the queue has no limit, and all of it runs back
  to back once the phone is on Wi-Fi. **Fix:** if one is already waiting, add
  nothing; append only behind one that is running. **Done**, asked without
  blocking, since the app's start calls it on the main thread. Against
  WorkManager's own test scheduler: ten saves waiting for Wi-Fi leave one run,
  where the old way left ten.
- ~~**B3 · Medium · A save the server no longer has is looked for on every sync,
  for ever.**~~ A waiting save whose feed is on the server but whose article is
  not found is kept for next time (`SyncOutbox.kt:361`), and each sync searches
  up to a thousand items of that feed again (`GoogleReaderService.kt:423`). An
  article the server has already purged will never be found. **Fix:** count
  the attempts; after five syncs or seven days, stop, keep the save here, and
  say so in the sync summary ("2 saves are not on the server"). **Done**, as
  five looks spaced out rather than a count and a date: at once, then after
  one, three, six and twelve hours, about a day in all, which covers a server
  that has not fetched the article yet. A save not due is held without a
  look. The account summary says "2 saves not on the server".
- ~~**B4 · Medium · The saved list is downloaded whole on every sync.**~~
  `pullStars` (`GoogleReaderService.kt:531`) reads up to eight pages of 250
  saved articles, contents included, and writes an id for each, every sync.
  With a few hundred saves that is megabytes per sync, on mobile data too.
  **Fix:** ask for the saved ids only (`stream/items/ids`, a few kilobytes),
  and fetch contents only for ids not already known here. **Done**, by id
  (`stream/items/contents`, a hundred at a time). A saved item that is still
  not found here is remembered and not asked about again while it stays
  saved, so an account's old saves cost one lookup, not one a sync.
- ~~**B5 · Medium · Database upgrades are never tested.**~~ Twenty-four steps take
  the database from version 1 to 25, twenty written by hand; twenty-three
  schemas are exported (`app/schemas`); no test runs a single migration. A
  mistake in the next one is found on somebody's phone, at the update, as a
  crash or lost articles. Worth doing before §8 puts updates in front of
  strangers. **Fix:** a `MigrationTestHelper` test from each exported schema to
  the current one, run under Robolectric so it stays in the normal suite.
  **Done**, with one change of method. Room's helper reads the schemas from
  assets, and a unit test here only sees the debug app's own, so each old
  database is built from its schema file's statements directly, which is
  what the helper does, and opened with Room as the app opens it. Every
  version from 3 upgrades to 25 with its rows, two in every table, one with
  every optional column empty. Checked by breaking: a column of the wrong
  type fails with the error a phone would crash on, and a lost row fails at
  every version that passes through the migration that lost it.
- ~~**B6 · Low · Full articles in older encodings come out garbled.**~~ The page is
  decoded with the charset in the response header, or UTF-8
  (`FullTextParser.kt:323`); a page that names its encoding only in a `<meta>`
  tag (windows-1252, Shift_JIS) is decoded wrongly. There is a TODO on the
  next line. **Fix:** read the bytes, within S2's cap, and let jsoup find the
  charset (`Jsoup.parse(stream, null, url)`). **Done**: the header's charset
  if it names one, then the page's `<meta>`, then UTF-8. Tested with pages in
  windows-1252 and Shift_JIS.
- ~~**B7 · Low · A sync stops the advance download in progress.**~~ The download
  replaces itself (`FullTextParser.kt:58`) and every sync asks for one
  (`RssLocalSync.kt:407`), so a pull to refresh or a sync on opening the app
  cancels a pass partway. The next pass picks up what is still missing, so
  little is lost but the article that was mid-download. **Fix:** keep a
  waiting one; append behind a running one. **Done** with B2, by the same
  rule.
- ~~**B8 · Low · "1 sources".**~~ Seven strings put a number in front of a plural
  noun without a plurals resource: `sources_deleted`,
  `sources_articles_cleared`, `category_in_use`, `backup_restored`,
  `covered_by_sources`, `starter_add` and `starter_added`. **Fix:**
  `<plurals>`, like the fifteen that already are. **Done**, and three more
  found on the way: "Restored 1 settings", and two strings with two counts
  each, now built from plural parts. A test finds any new one.
- ~~**B9 · Low · The panel sizes itself from Android's private values.**~~
  `OverlayView.kt:408` and `:414` read `status_bar_height` and
  `navigation_bar_height` from the system's own resources by name. They are
  not an API, and with a tall camera cutout or gesture navigation they are not
  the real insets. **Fix:** take the insets from the window, as the app does.
  **Done**, more narrowly than written. The window's insets were already
  read; the private values were a floor under them, so with gesture
  navigation the bottom kept a 48 dp button bar's room. Each edge now trusts
  the window once the window has reported it, and the private values stand
  in only for a window that reports nothing, which some launchers' overlay
  windows do. Not checked on a device.
- ~~**B10 · Low · Interrupted picture downloads leave files behind.**~~ A download
  that fails partway leaves its `.part` file (`SavedImages.kt:132`); only the
  too-large case removes it. **Fix:** delete the part file whenever it is not
  renamed, and sweep old ones at start. **Done** with B1: removed however the
  download ends, and any left by a download that died are swept when the
  next one starts, if more than an hour old.

#### 22c. Screens: the phones that sell most

Twenty Android phones: every Android model in Counterpoint's best-seller lists
for the first half of 2026, last year's leaders, and the makers and shapes
those lists leave out (Pixel, OPPO, vivo, Infinix, the foldables). Plus the
tablet here. Android lays apps out in dp, so the table gives width × height
in dp. **~** means worked out from the panel's pixels, not published.

| Group | Phones | dp |
|---|---|---|
| Budget, HD+ | Galaxy A07, Galaxy A06, Redmi A5, Redmi 14C, Infinix Hot 70 | 360 × 788–820 |
| Samsung's base flagship | Galaxy S26 | 360 × 780 |
| Samsung A series, FHD+ | Galaxy A17 5G, A17 4G, A16 5G, A36, A56 | ~412 × 892 |
| Samsung's Ultra | Galaxy S26 Ultra | 384–412, with the resolution setting |
| Chinese flagships | OPPO Find X8 Pro, vivo X200 Pro | 394–395 × 869–875 |
| Pixel | Pixel 10, Pixel 10 Pro | 410–412 × 914–923 |
| Fold, cover screen | Galaxy Z Fold6; Fold7 | 323 × 792; ~360 × 840 |
| Fold, inner screen | Galaxy Z Fold6; Fold7; Pixel 10 Pro Fold | 619 × 720; ~656 × 728; 692 × 717 |
| Flip | Galaxy Z Flip7; its FlexWindow cover | ~393 × 916; ~350 × 390 |
| Tablet | Galaxy Tab S5e (tested here) | 1280 × 800 |

Three widths cover most of the list: 360, about 395, and 412. The outliers are
narrow (the Fold6 cover), short (the Flip cover, and any phone on its side,
which is 360–412 dp tall), and wide (the inner screens, tablets). Text size
moves every one of them: 130% is a common choice, Android 14 allows 200%, and
Display size at its largest takes a 360 dp phone to roughly 320 dp.

**How it was checked.** The top of the feed (header, glance row, category
chips, the first day heading and a lead story) was rendered off the device at
each size, at 100%, 130%, 150% and 200% text, at 220 dp for split screen, and
at the Fold6's and Pixel Fold's inner screens, in Inter, the app's default
face. A render at the Pixel 10 Pro's own size matched a screenshot from the
phone to the pixel: the name 255 × 61 px, in the same place. The render
harness is not in the repository.

- ~~**C1 · High · The glance chips cut their text.**~~ Their height is fixed at 84
  dp (`GlanceRow.kt:56`). At 130% on a 360 dp phone the third line (the place)
  is cut in half; at 150% on 412 dp it is a sliver. The rain figure is cut
  short at 360 dp at *every* text size ("100%" shows as "100", then "10" at
  130% and "1" at 200%), and at 412 dp from 150%. At 360 dp that is the whole
  budget group and the Galaxy S26, at their default settings. On a phone on
  its side, a Fold's inner screen or a tablet the two chips stretch to half
  the width each. **Fix:** a minimum height instead of a fixed one; a number
  never shortened (the label goes first); chips capped at about 200 dp wide
  and started from the left. **Done.** The height comes from a line of each
  text style, measured: under Android's non-linear scaling a line grows with
  its font, so at 200% a label line is 32 dp where the arithmetic said 28,
  and a first attempt from the arithmetic still cut the chips short. Where
  the rain figure will not fit whole beside the temperature it takes a line
  of its own and the row grows by that line; on the Pixel it stays where it
  was. Chips stop at 220 dp, so all three fit on a wide screen.
- ~~**C2 · High · The name wraps in the header.**~~ "Whisper", beside the mark and
  four buttons, needs about 355 dp at 100%, so a 360 dp phone has almost
  nothing to spare. It breaks to "Whispe / r" on a 360 dp phone at 130%, and
  to "Whis / per" on the Fold6 cover at 100% and a 360 dp phone at 200%; at
  220 dp it stands one letter to a line. **Fix:** one line, never wrapped;
  when it will not fit, the mark alone, which is still the brand. **Done**,
  in one title both headers now share (`BrandTitle`); TalkBack still reads
  the name.
- ~~**C3 · Medium · Large headlines run above their shade.**~~ The dark gradient
  under a lead story starts 30% of the way down the picture
  (`ArticleCard.kt:225`). A headline that is tall for its card, at 200% text
  or on a narrow screen, reaches above that, and its top line is white text on
  the photograph. **Fix:** draw the gradient behind the text itself, from its
  measured top, so it grows with the headline. **Done**: the same three stops,
  anchored to the text, starting 72 dp above its first line. Where the text
  sits as usual the shade lands where it did.
- ~~**C4 · Medium · On a short screen the header is the whole first screen.**~~ On
  the Flip cover (about 400 dp tall) the lead story starts at the bottom edge;
  on a phone on its side no story shows at all until a scroll. **Fix:** below
  about 480 dp of height, glance goes and the header scrolls away with the
  feed. **Done.** The header already scrolled away; the glance row now steps
  aside below 480 dp, in both feeds, and the lead story shows on the first
  screen of the Flip cover and of a phone on its side.
- ~~**C5 · Medium · On the inner screens one lead story fills the first
  screen.**~~ Cards takes a second column only from 720 dp (§20), so the Fold6
  (619 dp) and the Pixel 10 Pro Fold (692 dp) get one column, and a lead
  story's picture grows with the width. At 692 × 717 the header and one story
  are the whole screen, its source line pushed off the bottom. There is no
  tablet mode as such: columns come from the width alone, and an article
  opens beside the feed only from 840 dp, so an opened Fold also opens
  articles full screen. It is a large phone, not the tablet. **Fix:** cap a
  lead story at about half the screen's height; and consider two columns from
  600 dp, 300 dp each. **Done, both.** A lead picture is four by three as
  before and never taller than half the window, which also helps a phone on
  its side. And from 600 dp, Android's own line between a phone and an
  unfolded or tablet window, Cards has two columns: an opened Fold now looks
  like the tablet upright. No phone is that wide, so none changes.
- ~~**C6 · Medium · Slow-device tuning misses new budget phones.**~~ Two photo
  decodes at a time instead of four is chosen for Android 11 and older, or a
  phone that calls itself low on memory (`ImageDecodes.kt:33`). A Galaxy A07
  runs Android 15 on an entry-level chip and is neither, so it gets the
  setting that made the Tab S5e stutter, and it is the best-selling Android
  phone of the year so far. **Fix:** also treat a phone with no media
  performance class (Android 12 and up) and 4 GB of memory or less as older.
  Diagnostics already says which is in force. **Done**, drawn at 5 GB as
  reported: a phone sold with 4 GB reports about 3.7, one sold with 6 about
  5.5.
- ~~**C7 · Low · Choice lists cut their second line at 200%.**~~ Each row is 48 dp
  high and no more (`StringSelectionPrefDialogUI.kt:131`); "Only on Wi-Fi and
  while charging" shows as "Only on Wi-Fi and". **Fix:** a minimum height.
  **Done.**
- ~~**C8 · Low · The panel permission button can crash on the cheapest
  phones.**~~ It starts Android's overlay settings with nothing around it
  (`LauncherPage.kt:173`). Android Go editions cannot grant that permission,
  and a phone without the screen throws. **Fix:** guard it, fall back to the
  app's info page, and on a Go device say plainly that the panel cannot open
  articles. `PermissionDialog.kt`, which has the same call, is used nowhere
  and can go. **Done**, and a third copy found in Settings, where anybody
  without the permission could reach it. All three go through one guarded
  helper; the dialog is deleted.
- ~~**C9 · Low · Split screen at about 220 dp.**~~ Everything at the top is cut
  down to dots. Mostly mended by C1 and C2; below about 280 dp glance can go.
  **Done** with C4, by the same check.

**Fine as it is.** Mosaic takes three lanes on the inner screens, which suits
it. The Ultra's QHD+ screen costs nothing extra, because pictures are decoded
at the size they are shown. A sync server on the home network over http, or
with a self-signed certificate, is still refused, which is a choice made in §7
rather than a fault.

Sources: [Counterpoint, Q2 2026](https://counterpointresearch.com/en/insights/iphone-17-global-best-selling-smartphone-in-q2-2026);
[Android Authority, Q1 2026](https://www.androidauthority.com/q1-2026-best-selling-phones-3663434/);
[Android Headlines, Q2 2026](https://www.androidheadlines.com/2026/08/apple-and-samsung-owned-every-spot-in-the-q2-2026-best-selling-smartphone-list.html);
[GSMArena on the Galaxy A06](https://m.gsmarena.com/counterpoint_samsung_galaxy_a06_was_the_bestselling_phone_in_latam_for_2025-news-71620.php);
dp sizes from [screensizechecker](https://screensizechecker.com/devices/android-viewport-sizes),
[webmobilefirst](https://www.webmobilefirst.com/en/devices/samsung-galaxy-a17-2025/) and
[viewpo](https://viewpo.io/tools/device-viewports/samsung-galaxy-s25/); panels from
[Samsung](https://www.samsung.com/ae/support/mobile-devices/what-is-the-display-size-resolution-of-galaxy-flip-7-galaxy-fold-7/).

#### 22d. Order of work

Small and certain first, then what the most phones see, then the rest.

1. **S1** the report scrub, and **S5** the web-view link: an hour between
   them, and S1 breaks a rule of our own.
2. **S3** the settings import: a crash on every start from one file.
3. **C1** and **C7**, the minimum heights and the rain figure (every 360 dp
   phone, at default settings), then **C2**, the name.
4. **B5** migration tests, before §8.
5. **S2** download limits with **B6** the charset: the same code.
6. **S4**, the address check.
7. **B1** to **B4**, Read later and sync.
8. **C3** to **C6**, the screens.
9. The Lows, as they are passed.

### 17. Scroll parallax on the feed — parked, at the bottom

Prototyped, demonstrated, and deliberately not built. The image inside a card's
frame moves slower than the card as it travels up the screen; the frame is
clipped and the picture is taller than it, so nothing reflows and the whole
cost is a transform per visible image in the draw phase.

It looks fine. It is not being built for 1.0 for three reasons, in order of
weight:

- **It carries no information.** Every other movement in the app earns its
  place: items animate so a card reads as having *moved* rather than been
  replaced, the held article stays put so the reader does not lose their place.
  This is the only purely decorative motion proposed, and it would sit on the
  surface people spend all their time on.
- **Its risk is the shape that keeps catching us.** A transform per frame is
  cheap in theory, and nothing in this project has been verified on a device by
  anyone but the author. A scroll effect that stutters on a mid-range phone is
  worse than no effect, and there is currently no way to find that out before a
  tester does.
- **It is slightly against what the app says it is.** "Curate. Read. Breathe."
  A page that is still until moved suits that better than one with something
  always sliding.

What settled it was the comparison rather than the effect: the same hour buys
**broken-feed recovery** — running discovery against a failing feed's site and
offering the replacement — which needs only the `FeedDiscovery` that §13
shipped with, fixes a problem every RSS reader has and none of them solve, and
is something a reader would mention to somebody else. Parallax is not.

Worth revisiting once 1.0 has been on real devices long enough to know what the
frame budget actually looks like. Until then it stays here, and the working
demo stands as the record of the decision.

---

## Replacing Discover: what is actually possible

The short version: **the minus-one page belongs to the launcher, not to
Android.** No app can claim it. What an app can do is offer itself as a feed
*provider* to a launcher that supports choosing one.

| Launcher | Can Whisper be its feed? |
|---|---|
| **Pixel Launcher** | **No, and this will not change.** It is hardcoded to Google's app. There is no public API, no intent, no setting. Anything claiming otherwise is describing a different launcher. |
| **Lawnchair** | **Yes** — today via the debug toggle, and without it if we get whitelisted. See below. |
| **Lawnchair forks** (Neo Launcher, Librechair) | Likely, since they inherit the same `FeedBridge`. Unverified — nobody has tested it. |
| **Everything else** | Mostly no. Most launchers have no minus-one page at all, and those that do generally hardwire it. |

### Getting rid of the debug step

From `FeedBridge.kt`, recorded in `UPSTREAM_NOTES.md` §3: Lawnchair filters
providers through a hardcoded map of package name to signing-certificate hash. A
package that is not in it is rejected unless `pref_ignoreFeedWhitelist` is on.

So there is exactly one legitimate route to "install it and it works":

> **A pull request to Lawnchair** adding `io.zero76.whisper` and our release
> signing certificate hash to that map.

That needs a stable release signing key first — which we do not have yet, and
which is a prerequisite for every distribution channel anyway. It also needs
Lawnchair's maintainers to accept it, and then a Lawnchair release, and then
users on that release. Not instant, but it is a normal open-source contribution
and the whitelist already contains five third-party apps, so there is precedent.

### One shortcut that exists and should not be taken

Neo Feed's whitelist entry is not a fixed hash — it is computed at runtime from
whatever is installed under `com.saulhdev.neofeed`. Any app using that package
name is accepted, whoever signed it.

Shipping under Neo Feed's package ID would therefore work immediately, with no
PR and no debug toggle. It would also collide with every real Neo Feed install,
break their updates, and pass this app off as someone else's. It is worth
knowing the hole is there, and worth not climbing through it.

### Shipping our own launcher — decided against

The only way to own the experience end to end would be to fork Lawnchair, wire
the feed in directly and distribute one app. That removes the whitelist, the
debug toggle and the "install Lawnchair first" step together.

It also turns a feed reader into a launcher project — home screen, app drawer,
widget hosting, gestures, backup, and every device quirk that comes with being
what runs when someone presses Home. **Off the table**, decided rather than
merely unattempted.

Which leaves Lawnchair's whitelist as the one route worth spending effort on,
and makes the standalone app the primary product with the minus-one surface as
a bonus for the people running a launcher that supports it.

---

## Suggestions, after the device report

Link harvesting answers "what do the things you read point at". That is a good
question about blogs and a useless one about news, because a BBC article links
to the BBC — so a reader of large news sites saw an empty screen and reasonably
concluded the feature made no sense. Two things came out of that.

**A bug**: the pass was reading the feed's own summary rather than the article
body, so even for link-rich sources it was counting a teaser paragraph whose
only outbound link is back to the publisher.

**And a second mechanism**, suggested from the device: if somebody reads a lot
of the BBC, offer ITV or Sky News. The bundled library is already a set of
curated peer groups, so "in the United Kingdom pack beside the BBC" is the
statement "similar publication", made by whoever assembled the pack rather than
inferred from behaviour.

The difficulty is that a large publication has a feed for everything: the BBC
is in eight packs and the Guardian in fourteen, so counting shared sources
makes a news reader look exactly as much like a cricket reader. Each source's
vote is therefore divided by the number of packs it appears in — one that sits
in a single pack says something definite, one that sits in fourteen says almost
nothing about any of them.

Still local, still no account, and still never one reader's list compared
against another's.

**Out of Settings, September 2026, for later development.** In daily use with
114 feeds the suggestions were weak. The row is gone from Settings and the
weekly pass is cancelled, so nothing is fetched for a screen nobody can reach.
The code stays: the page, the worker and both mechanisms, ready to come back
when there is a better signal to build them on.

## Narrowing to one source — built, with two parts left

Tapping the mark or the name under a headline shows only that source. A bar
carrying a back arrow and an X says which, and the system back gesture clears
it. Newest first inside a source, whatever the feed is sorted by: the
weighting exists to choose between a hundred and nineteen sources, and within
one there is nothing to weigh against.

The idea arrived as "make it act like the search", and the chrome is exactly
right — same bar, same two ways out. Reusing the search *itself* would not
have been, and the reason is worth keeping because it is invisible until it
bites. `matchesSearch` compares substrings across the headline, the feed title
and the body, so "slate" also returns "tran**slate**", "**slate**d for
release" and every article that merely mentions Slate; and a search
deliberately widens the query from `FEED_WINDOW` to every article ever stored,
which is the cost §19 spent a day removing from the feed. So the filter
matches a source id, the window stays where it is, and the bar shows the
source's own mark and name with nothing to type into.

**Two parts were deliberately left.**

- ~~**The launcher panel does not have it.**~~ **Built.** It was withheld on
  the reasoning that the panel's back gesture belongs to the launcher, so a
  filter opened there would have no way out. That was simply wrong:
  `OverlayView.onBackPressed` already intercepts back for the filter sheet and
  for search, falling through to the launcher only when neither is open. A
  third clause was three lines. The bar goes in the header slot the search bar
  already uses, and the panel is handed the same `CompositionLocal` the app
  gets.

  The one thing worth confirming rather than assuming was whether the two
  surfaces share a view model — the app resolves through `koinNeoViewModel`
  and the panel through `KoinJavaComponent.inject`, both naming the same
  `viewModelOf` binding. Shared, a filter set in the app would silently narrow
  somebody's home screen. `ViewModelSharingTest` asks Koin directly rather
  than reading the DSL, because the answer is a property of the Koin version
  and would change without anything here changing with it. They are separate.
- **One publisher, several feeds.** Filtering is by the source that was
  tapped, which is what the tap said. A reader taking "Slate - Culture" and
  "Slate - News" separately will at some point tap one and wonder where the
  other went. Grouping by registrable domain is the obvious alternative and is
  wrong as a default — somebody who subscribed to two sections separately did
  that on purpose — so this wants a second control ("everything from
  slate.com") rather than a different rule. Not worth building until somebody
  with several sections from one publisher says it is missing; the machinery
  exists already, since `SourceListViewModel.groupLabel` works out registrable
  domains for the duplicate finder.

Two smaller things noticed while building it, neither yet done: the source
page is the natural home for mute, edit, unsubscribe and "last updated 17m
ago", all of which currently mean a trip to Data sources and a search; and the
tap target is the mark and the name only, because that row sits directly under
the headline and a wider one would take taps meant for the article. Whether
that target is comfortable is a question only a device answers.

## Text size — asked for, not yet designed

Raised from the device. Worth recording carefully, because the obvious version
of it is already there and the part that is missing is not the part it sounds
like.

**What already works.** Every size in `Typography.kt` comes from Material's own
scale and is expressed in `sp`, so Android's system font-size setting scales
the whole app today, chrome and articles alike. Somebody who has made text
larger system-wide already gets larger text here. The font *family* is a
preference — the bundled Inter, or the system face — and `typographyFor` takes
it as a parameter and rebuilds the scale around it.

**What is missing** is an in-app control, and the case for one is specific: the
system setting moves everything at once, and a reader who wants a bigger
article body does not necessarily want bigger chips, bigger source names and a
bigger header eating the screen. Every serious reader offers its own size
control for that reason.

**Where it goes.** `typographyFor(family)` is the seam and already has the right
shape — it takes a parameter and returns a whole `Typography`. A scale factor
alongside the family is a small change there and a small change at the two
`MaterialTheme` entry points in `Theme.kt`.

**What makes it more than that**, and the reason this is a roadmap entry rather
than an afternoon:

- The cards are laid out by measurement. The image ratios, the 96dp compact
  thumbnail and the three emphasis sizes were tuned against a real screen, and
  headlines are bounded by `maxLines`. Scaling the type without revisiting
  those gives clipped headlines and cards whose text no longer fits the space
  reserved for it — which is worse than small text.
- The launcher panel is a fixed width that Lawnchair decides, not us. Whatever
  the app does, that surface has less room and fewer options.
- The reader and the feed may want separate answers. Article body size is the
  thing people actually ask for; feed headline size is a different judgement
  and arguably belongs to the layout choice instead.
- It interacts with the system scale rather than replacing it, so the two
  multiply. A reader at 130% system with 130% in-app gets 169%, which needs
  deciding rather than discovering.

**Smallest useful first version**, if it is wanted before the rest: article body
only, three or four steps, applied in `HtmlToComposable` where the reader's
text is composed, leaving the feed and the chrome alone. That avoids every
layout problem above and covers the case people actually complain about.

## Debt worth clearing

Small, and cheaper now than later.

- **Nothing here has been verified on a device by me.** Everything is reasoned
  from the code and measured where it could be measured — text widths against
  the real font, icon alpha, migration SQL. The on-device checks have all been
  yours. Emulator-based screenshot tests would change that.
- **Test coverage is 936 unit tests** (September 2026), across article age, tag splitting, feed
  layout and weighting, clustering, the settings backup format, the tour's step
  machine, the starter list, bookmark import, link harvesting, the Google Reader
  id shapes, the source list's filters and range selection, and the URL scheme
  checks that stand between a feed's contents and an outgoing intent. Still
  untested and still the shape that would benefit: theme resolution, the
  day/night rule, and the sync and filter performance work. Nothing on a device
  — see the first item.
- ~~**Dead code**~~ — cleared. Nine unreferenced files and eight drawables
  removed, along with eight unused DAO methods, one of which had an
  `@Relation` without `@Transaction`: the same shape as the OPML crash fixed
  earlier, waiting to be called.
- ~~**Phosphor was unlicensed**~~ — the icon set was hand-transcribed into
  ImageVector sources with no licence recorded anywhere. `docs/licenses/
  Phosphor-MIT.txt` now carries it; new icons are parsed from the upstream
  SVG rather than retyped.
- **Assets still open**: the empty-bookmarks state. The onboarding backgrounds
  and the untagged horizontal lockup are wired in as of §15; article
  placeholders and the empty-feed states were done earlier. The horizontal lockup is no longer needed —
  the app bar composes the symbol and the wordmark itself, which keeps the two
  independently sizeable. Still missing if the cobalt splash is ever wanted
  back: a light colourway of the symbol, since the vivid gradient loses two of
  its three blades against `#2563EB`.
