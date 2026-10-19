CHANGELOG
=========

Whisper's own history starts at 1.0.0. Everything below that is **Neo Feed's**,
kept because Whisper is a fork of it and the work is inherited rather than
replaced — but those are not Whisper releases, and the version numbers are not
Whisper's. See [`ATTRIBUTION.md`](ATTRIBUTION.md).

Unreleased
----------

- **How many new articles there are.** The line under the categories now
  says "Updated 12m ago · 8 new": the unread articles that have arrived
  since you last left the feed, counting only the tab you are on. Sources
  kept out of All count in their own tab. Nothing extra when there is
  nothing new. Suggested by a reader.
- **The headline once, not twice.** Many sites put a short line above their
  own headline, such as a category, a date or "Advertisement", and the
  reader kept the page's copy of the headline under its own. It now finds
  it a few lines down and takes it out, along with that short line.
- **Pictures that load as you scroll now show.** On sites such as IEEE
  Spectrum and SoraNews24 the reader drew a blank where each picture should
  be. Articles already saved get their pictures back too.
- **Pictures offered in several sizes load.** When a picture's address
  held a comma, as WordPress sites write them (`?resize=640,480`), the
  reader could pick a broken half of it and show the placeholder instead.
  Seen on SoraNews24, Gear Patrol, New Scientist, 9to5Google and TechCrunch.
- **Pictures kept for browsers without scripts come through.** Inverse's
  pictures inside its articles only exist in that form, and none of them
  survived into the reader.
- **xkcd shows the comic.** The reader preferred the page to the feed, and
  the page gave the site's footer instead of the comic.
- **A picture's credit leaves with the picture**, now also when the page
  sets the credit apart from it.
- **Whole articles from sites that split them.** Ars Technica and Wired set
  an article in several blocks with adverts between, and the reader kept
  only the largest. Ars articles lost up to eight opening paragraphs; one
  Wired review came through at a quarter of its length. The blocks are now
  joined first. Ars's one-line subtitle no longer comes along, though its
  feed carries it.
- **No leftover labels.** Lines that are only "Advertisement", "Sign up to
  newsletter", "Related articles" and the like are taken out.

These came from running the reader over 476 articles from 129 real feeds.

1.0.5 — 18 October 2026
-----------------------

- **Article text size.** Settings → Article text size, under Reading: Small,
  Default, Large or Larger. It sizes the article in the reader, headline and
  all, and leaves the feed as it is. Android's own font size still applies on
  top. Suggested by a reader, and the second person to ask.
- **A picture's credit leaves with the picture.** When the reader moves an
  article's opening picture to the top, a credit printed beside it (such as
  "Getty Images") no longer stays behind as a line of its own.
- **Bylines show the author's name.** A feed that gives its author as
  "staff@site.com (Name)" now reads just "Name".
- **No "Syncing" notice left behind.** One sync ended as the phone dozed and
  its notice stayed up for most of an hour with nothing running. Whisper now
  looks a minute after every sync, when the app or the launcher panel is
  opened, and every few hours, and takes down a "Syncing" notice no sync is
  behind. A sync that is running keeps its notice.
- **Saved articles, once read, are only in Bookmarks.** With read articles
  hidden, the saved ones stayed in the feed and gathered at the bottom. A
  saved article now leaves the feed once it is read, like any other, and
  stays in Bookmarks. Unread saved articles and pinned ones stay. Suggested
  by a reader.
- **The last articles can be scrolled past too.** With "mark read while
  scrolling" on, an article counts as read once it leaves the top of the
  screen, and the last few in the feed never could: there was nothing below
  them. With read articles hidden, they stayed. The feed now ends with a
  screen of space ("That's everything"), so they leave like the rest.
  Suggested by a reader.
- **Keep a source out of All.** A source's settings have a new switch,
  Show in All. Turn it off and the source still updates and still shows
  under its categories and in search, but not in the main feed. For a feed
  you want to read in its own corner. Data sources marks it "Not in All".
- **"Categories" throughout.** The source editor and the filter called them
  tags in one place and categories in the next.
- **Pictures and headlines, without the summaries.** Settings → Show
  summaries, under Feed layout. Off, Cards, Magazine and Mosaic keep their
  pictures and headlines and drop the text underneath. List already has no
  summaries. Suggested by a reader.
- **Times as a clock, if you prefer.** Settings → Article times: how long
  ago ("2h"), the time published ("14:32"), or both. Older than a week, the
  date stands in for the time. The 12- or 24-hour clock follows the phone.
  Suggested by the same reader.
- **The tour's highlight stays on screen.** Around a full-width card or the
  gear in the corner, two sides of the box ran off the edge of the display.

1.0.4 — 12 October 2026
-----------------------

- **Contact us, in the app.** About now opens with a Contact us section: the
  email address, the website, and where to file a bug report. Settings says
  it is there. Google Play asks news apps for exactly this, and turned 1.0.2
  down without it.
- **Every link in About can be seen.** The links under the app's name sat in
  a row that ran off the side of the screen, so the website, the fourth of
  them, only appeared after swiping. They now wrap onto a second line.

1.0.3 — 11 October 2026
-----------------------

- **Comfortable line length in the reader.** On a phone turned sideways or a
  tablet, the text keeps to about 75 characters a line instead of running past
  100. It scales with the text size.
- **Empty feeds have a button.** "No sources yet" offers *Add a source*,
  "Nothing came back" offers *Try again*, and "Nothing matches" offers *Show
  all*, instead of describing where to go.
- **Buttons say what they do.** Dialogs that act now confirm with the action
  itself, such as "Remove", "Rename" or "Forget everything", instead of "OK".
- **Deleting a category can be undone.** It happens straight away, with Undo on
  the message that follows, the same as removing a source. There is no
  question first.
- **Source cards lead with the name.** The site and categories sit under it
  ("science.org · Tech"). The full address is still on the source's own page,
  and on the duplicates view, where it is what tells two feeds apart.
- **"Show all" is readable on the duplicates banner.** Its buttons were in the
  page's accent colour, pale on the banner's own colour; they now use the
  banner's text colour.
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
- **One library fewer.** The multidex support library is gone: since
  Whisper needs Android 8 or later, Android loads the app's code itself. Raised
  in F-Droid's review, where the build log warned about it.
- **The store description names the weather service.** The glance row's
  forecast comes from Open-Meteo, and the description now says so, so it is
  plain where that connection goes.
- **The FreshRSS match reports its real size.** Twice it said "<1 KB" for
  hundreds of items, because it read Android's count of everything the app had
  downloaded and that count had not caught up yet. It now counts what the
  server sends as it arrives.
- **Diagnostics no longer name articles.** A failed full-article download
  wrote the article's internal id into the report. The line no longer carries
  it, and the report blanks out any such id it finds, wherever it came from.
- **One language on every screen.** Whisper came with 28 languages from Neo
  Feed, none of them more than 13% translated, so a phone set to German showed
  a few German words on screens that were otherwise English. They are removed
  until full translations exist; until then the app is in English throughout.
- **FreshRSS matching is much faster on the phone.** Diagnostics showed the
  server answering a match in 2.5 seconds and the phone taking a minute,
  once nearly three, to file the answer: it saved each article's server id
  as its own database write, up to two thousand of them. A page of 250 is
  now one write. The history's "to send" also counts only what is actually
  going to the server, not every article newly matched.
- **Adding a feed can no longer run the phone out of memory.** A sync
  stops reading a feed at 10 MB, but adding one, checking it for https and
  the weekly look for new sources read it whole, however long it went on.
  An address that never stopped sending would have crashed the app. They all
  stop at 10 MB now.
- **The share sheet is titled properly.** It said "Where to Send?", a
  phrase written into the code where no translation could reach it. It says
  "Share", from the app's own strings.
- **The save button stays put when the "⋮" menu opens.** Opening a story's
  menu nudged the bookmark icon beside it sideways, and closing it moved the
  icon back.
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
