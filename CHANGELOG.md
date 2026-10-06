CHANGELOG
=========

Whisper's own history starts at 1.0.0. Everything below that is **Neo Feed's**,
kept because Whisper is a fork of it and the work is inherited rather than
replaced — but those are not Whisper releases, and the version numbers are not
Whisper's. See [`ATTRIBUTION.md`](ATTRIBUTION.md).

Unreleased
----------

- **No more "display over other apps" card in Settings.** It sat under
  Appearance for every reader without the permission and said feed items would
  not open, which was only ever true of the launcher panel. The permission is
  now explained in one place, Settings → Launcher page, where the panel is set
  up.
- **Stock tickers stay in full articles.** Investing.com puts each ticker in
  a hover-card wrapper whose name says "popup", and the article extractor
  threw anything named like that away as clutter, so "Goosehead Insurance
  Inc. (NASDAQ:GSHD)" read as "Goosehead Insurance Inc. ()". Words inside a
  sentence now keep their text whatever their wrapper is called. Articles
  already downloaded change when they are fetched again.
- **Source names show on narrow Mosaic tiles.** On a tile with no picture,
  the save and menu buttons shared the line under the headline and squeezed
  the source's name to nothing: "NEWS · · 2h". On a narrow tile they now sit
  under that line instead.
- **Buttons on pictures can always be seen.** The save and menu icons over a
  photo had only a faint fade behind them and vanished on white or pale
  pictures. Each now sits on a small dark disc.
- **No stray space before the comma in the reader.** Some feeds end their
  author's name with a space, which showed as "Zhiye Liu , Tuesday". Author
  names are trimmed, and a blank one is treated as none.
- **The diagnostics report says why matching was slow.** The step that pairs
  FreshRSS's articles with Whisper's now reports how far back it started, how
  many it matched, how long it waited on the server (and its slowest page),
  and how long the phone took. One evening it took eight minutes where the
  night before it took half a minute, and the report could not say which side
  had slowed. Counts and times only, never what was matched.

1.0.2 — 5 October 2026
----------------------

- **F-Droid's scan passes.** F-Droid built 1.0.1 and then failed its scan
  for proprietary code: the launcher panel's classes were filed under
  Google's package name, so they read as Google's library. They are open
  code inherited from Neo Feed, and they are under Whisper's own name now.
  Nothing else changes; the panel speaks to Lawnchair exactly as before.

1.0.1 — 5 October 2026
----------------------

- **F-Droid can build it.** F-Droid deletes every line that sets a signing
  key before it builds; one such line in the build file ran over three, and
  the deletion left the file broken. 1.0.0 never built there.
- **Signing in is two steps**: the server's address, then the username and
  password, so a password manager fills the right fields.
- **A backup folder is asked about before it is written to.** Choosing one
  that already held a backup replaced it with the new install's empty one;
  it now offers Restore, Replace or Cancel, and every backup keeps the copy
  it replaces as `.previous`.
- **The reader** puts the card's picture on top rather than a site's logo,
  and drops a site's own "By / Published / Updated" lines from the top.
- **Background syncs are lighter.** Android keeps a sync it starts in the
  background at the slow end of the phone, and one night's ran into its
  eight-minute stop. In the background a sync now spends at most half its
  time on feeds and matches a quarter as much with the account; what it
  leaves, the next sync takes up, and one begun on screen does it all.
- The store title is "Whisper: RSS Reader", and the About page names
  Nyancat Labs.

1.0.0 — 4 October 2026
----------------------

First Whisper release, from Nyancat Labs.

Rather than list several hundred commits, the position is documented where it
is maintained:

- [`ROADMAP.md`](ROADMAP.md) — every section, what is built, and what was left
  undone on purpose
- [`docs/AUDIT_2026-09.md`](docs/AUDIT_2026-09.md) — the security, correctness
  and performance audit, and the four items it left open

Two things are repeated here so a release note cannot be read as more than it
is: **sync is supported with FreshRSS only** — proven against a live server
with 114 feeds, on a phone and a tablet at once — and **there are no
instrumentation or screenshot tests**: the 1066 unit tests cover logic, and
every on-device check has been manual.

---

Inherited from Neo Feed
=======================

1.9.0 (09.11.2025) +200 Commits & +30 Translations
------------

### Function

- Add: Options to export/import bookmarked articles
- Add: Google GSA bridge
- Fix: Crash on creating ConfigurationOverlayController
- Fix: Fix and improve OPML export/import logic
- Fix: Overlay's bookmark icon coloring
- Fix: Reload event from the dropdown menu
- Fix: Manual syncing
- Update: Split ArticleViewModel to ArticleViewModel, ArticleListViewModel & SortFilterViewModel
- Update: Rebase all viewmodels to be state-based
- Update: Replace deprecated test libraries
- Update: Replace FeedArticle with Article (DB)
- Update: Replace legacy FeedItem calls with the new embedded entity
- Update: Lazy composition of slide pages
- Update: Make all non-flow database calls suspend
- Remove: Double permission declaration in manifest (credits @thePrivacyFanatic)
- Remove: Unused aidl files

### UI/UX 

- Add: Permission dialog to enable draw over other apps (should fix usage on Lawnchair - credits @thePrivacyFanatic)
- Add: Bookmark button to article page
- Add: Sort/filter sheet for the launcher layout (as incomplete)
- Add: Support for predictive back gesture
- Add: Option to disable dynamic color
- Add: Option to see bookmarks in xml views
- Add: Tags filter
- Update: Revamp sort/filter sheet layout 
- Update: Separate enabled/disabled sources list
- Update: Replace source item's delete button with an enabling switch
- Update: Revamp button and chip layouts
- Update: Padding pages on showing keyboard
- Update: Revamp source edit page

1.8.0 (23.04.2025) +40 Commits & 10 Translations
------------

### Function

- Update: Revamp viewmodels and repositories applying separation of concerns
- Update: Make prefs real delegates
- Update: Restructure the project into 5 main packages
- TargetSDK 35

### UI/UX

- Add: SortFilter sheet to the articles page
- Fix: Back handling on edit and add feed pages
- Update: Replace removed MD icons with Phosphor icons
- Update: Fix background color of the navigation suite
- Update: Make UI wide screens friendly & navigation adaptive

1.7.2 (17.12.2024) +30 Commits & 5 Translations
------------

### Function

- Add: Option to open feed in WebView
- Update: Re-sync source after relevant update
- Update: Make FullTextWorker unique
- Update: PullToRefreshLazyColumn based on PullToRefreshBox
- Update: Replace deprecated usage of systemUiVisibility

### UI/UX

- Add: Parsing much more and improve existing HTML-tags
- Fix: Placeholder icons visibility on black backgrounds
- Fix: Applying dark/light system bars
- Update: Revamp pref layouts
- Update: Revamp About page
- Update: Improve UI paddings

1.7.1 (17.08.2024) -10 Commits & 1 Translations
------------

### Function

- Fix: Crashing offline reader on certain systems
- TargetSDK 34

### UI/UX

- Add: Black themes
- Update: Selection dialog layout

1.7.0 (15.08.2024) +120 Commits & +10 Translations
------------

### Function

- Fix: Make sure that DataStore is single
- Fix: Restarting app
- Fix: Over-composition of dialogs in SourcesPage
- Fix: Feed sorting by time
- Update: Use Flow for articles in overlay
- Update: Revamp NavigationManager to use args-safe NavRoute
- Update: Inject repos and client
- Update: Migrate DI from KodeIn to Koin
- Update: Use Kotlin generator in Room
- CompileSDK 35
- Kotlin 2.0
- Dependency Catalogue

### UI/UX

- Add: Bookmarks filter to OverlayPage
- Add: Pref to remove duplicate articles
- Add: Main pager with Feed, Settings & Feeds
- Add: Scroll to top button
- Add: Hint if no articles are present
- Add: Transparency & collapsable app bar
- Add: Share button as action to articles
- Fix: Applying updated Overlay theme
- Fix: Theming system
- Update: App icon
- Update: Use favorite instead of bookmark icon
- Update: Revamp preferences, articles and feed layouts
- Update: BookmarkItems use same layout as normal articles
- Update: Revamp & unify overlay layouts (xml & composable)
- Update: Overlay menu popup animator
- Remove: BookmarksPage
- Remove: Card background pref (for now)

1.6.0 (XX.XX.2023) Y Commits & Z Translations
------------

### Function
- Add: Bookmarks page
- Fix: Opening links from article page
- Fix: Editing disabled Feeds
- Update: CompileSdk 34

### UI
- Add: Dynamic-theming
- Add: Monochrome app icon
- Fix: StatusBar color
- Fix: About page shortcuts
- Update: Revamp Preferences & About pages
- Update: Article card layout
- Update: Return to top FAB color to match theme
- Update: Revamp all item components
- Update: Use Phosphor icons instead of Material
- Update: Drop Compose Material for Material3

### UX
- Add: Share & open-in-browser buttons to article view
- Add: Bookmark button to article cards
- Fix: Showing real state of Switches e.g. in EditFeedPage
