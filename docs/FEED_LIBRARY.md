# The bundled feed library

Whisper ships a directory of feeds so that somebody with an empty reader has
somewhere to start. Every other route in — paste an address, import OPML, scan
bookmarks, wait a week for it to notice what your reading links to — assumes
you already know the answer.

## Two tranches

The library was built twice. The first came wholesale from an upstream
directory; the second was assembled by hand to fill in the countries that
directory had never covered. They were verified the same way, and the second
is described under **The country tranche** below.

## Where the first tranche comes from

[plenaryapp/awesome-rss-feeds](https://github.com/plenaryapp/awesome-rss-feeds),
under **CC0 1.0** — public domain, no attribution required, no friction with
GPL-3.0. It is the directory a different Android reader uses for the same
purpose, which is some evidence it is maintained for the job.

Bundled, never fetched. A directory served over the network would mean asking
somewhere what feeds exist, and a query like "photography" or "local politics"
says a good deal about a person. `PRIVACY.md` lists exactly one
developer-initiated third-party call, and a catalogue is not worth making it
two.

## What was checked, and what was removed

Every one of the 720 addresses was fetched and its response inspected before
anything shipped.

| | |
|---|---|
| Verified: returned a parseable feed | 600 |
| Kept, unverifiable from the build machine | 58 |
| Dropped as dead | 62 |

**Dropped** means one of: HTTP 404 or 410; a 200 that served HTML rather than
a feed, which is a site that has removed or moved its feed; or a DNS, TLS or
connection failure. The last of those matters more here than it looks — the
app refuses cleartext, so a host with no working certificate cannot be read at
all.

**Unverifiable** means the build machine could not reach it: a 403 from a bot
wall, a 429 from the checker's own parallelism, or an egress policy in the
container. Those almost certainly work on a phone, and dropping a feed on the
strength of a proxy's opinion would be worse than shipping one that needs a
retry. They ship.

Two packs did not survive at all. **Iran**: nothing in it verified. **Russia**:
the source file has unescaped double quotes inside quoted attributes, which is
ambiguous rather than merely wrong — see below. Russia has since been added
back from hand-picked addresses rather than repaired; Iran has not.

## The ampersands

Thirteen of the twenty-four country files are not well-formed XML. They carry
bare `&` in attribute values — "Breaking news, showbiz & celebrity photos",
"World & Nation" — 479 of them across the set.

This was not only a problem for building the library. Whisper's OPML importer
uses SAX, which is strict and right to be, so a single unescaped ampersand two
hundred feeds into a file rejected the whole thing. Anything that writes OPML
by string concatenation produces this, which is most things that write OPML, so
the importer now escapes ampersands that do not begin an entity before parsing.

Only ampersands. Unclosed tags and stray quotes are ambiguous, and guessing at
somebody's subscription list is worse than declining it — which is why Russia
is absent rather than repaired.

## The country tranche

The upstream directory covers 22 countries, with nothing for Scandinavia, the
Low Countries, central Europe, Russia, or most of east and south-east Asia. A
reader in any of them opened the library, found their own country missing, and
learned the wrong thing about what the app is for.

So a second set was assembled by hand: national broadcasters and major papers,
164 addresses across 28 countries, probed exactly as the first tranche was.

| | |
|---|---|
| Verified: returned a parseable feed | 96 |
| Kept, unverifiable from the build machine | 15 |
| Dropped | 53 |

Two rounds of probing rather than one. The first lost 39, and roughly a
quarter of those were a publication that still has a feed at a different
address — an `arc/outboundfeeds` path, a `?outputType=xml`, a section id that
has moved on. Trying a second address for each recovered eight, which is worth
one extra round of anybody's time.

**Dropped** is the same three cases as before: 404 or 410; a 200 serving HTML,
which is a site that has retired its feed; or DNS, TLS or connection failure.
**Unverifiable** is a refusal to serve rather than an absence — fourteen 403s
from bot walls and one 451 — kept for the same reason the first tranche keeps
them, that a datacentre IP is not a phone.

Ireland, Japan and Poland already had packs and were merged into rather than
replaced. Ireland is the sharpest illustration of why the tranche was needed:
six feeds, and not one of the country's three largest news publishers.

### One editorial decision, stated plainly

The Russia pack is Meduza, The Moscow Times, Kommersant and Interfax. RT and
Sputnik are not in it. Both are widely carried, so their absence is a choice
rather than an oversight: a pack labelled "Russia" in a reader's library reads
as a recommendation, and state outlets under EU sanction are not something to
recommend silently. Anybody who wants them can paste the address — the app
takes any feed, and this is a starting list rather than a permitted one.

## The cleartext sweep

Checking the new packs turned up 119 `http://` addresses in the *first*
tranche, carried over from upstream and never looked at. They matter more than
they look: Whisper tells Android to refuse cleartext outright, so an `http://`
feed does not merely travel in the clear — it cannot be fetched at all, and a
new reader picking one gets a subscription that fails on its first sync and
lands in Broken feeds.

Each was asked for over https: 97 answered with a feed and were rewritten, and
23 had no working https and were dropped, on the same reasoning that dropped
the dead ones. A feed the app is structurally incapable of reading is not a
feed to offer somebody on their first day.

## Five is the floor

The first pass left 28 of the 50 countries with fewer than five feeds — four
had two — while the United States had nine. That is a directory with an
opinion about whose news matters, arrived at by nobody deciding anything: it
is simply where the upstream list was thickest and where the first round of
probing happened to succeed.

So every pack now carries at least five, countries and topics alike. A third
tranche of 131 addresses was probed to get there, and the four countries still
short after it — Indonesia, China, Kenya, Vietnam — were given a round of
their own rather than left at four.

A pack of two is worse than no pack. Somebody opening "Denmark" and finding a
pair of feeds learns that the app does not really cover Denmark, which is a
more damaging thing to learn than that Denmark is not listed — and the reader
in a country the directory skimped on is the one who most needs it to work.

The floor is now a test rather than an intention: `FeedLibraryAssetTest` fails
the build if any pack drops below five. Because every address is verified
before it ships, that is a promise about five *working* feeds rather than five
names.

China was the hardest. Most Chinese outlets' English feeds have been retired,
and eight candidate addresses returned HTML or 404 before CGTN, ECNS, Nikkei
Asia and The Diplomat filled the pack out.

## The same publication twice

Filling the packs out to five put some publications in twice under two
addresses — Tagesschau, Hong Kong Free Press, Republika at `/rss` and `/rss/`,
the Japan Times. A pack of five with two rows for one masthead is really a
pack of four, so this undercuts the floor rather than merely looking untidy.

Eight were removed. A heuristic found them — same registrable domain, one
title containing the other — and **three of its eight were wrong**, which is
the useful part of the story:

- **Al-Ahram** alongside **Ahram Online** is the Arabic edition beside the
  English one. Two editions are two feeds. The language guard missed it
  because "Al-Ahram" names no language.
- **Philippine News Agency** and **Philippine Information Agency** are two
  different agencies that share a government domain and most of a name.
- For **Feld Thoughts** it kept the wrong one of the pair, a tag archive over
  the blog itself.

So the automated check that ships is the narrow one: the same address twice,
including the `/rss` versus `/rss/` case that let Republika through. Deciding
that two *publications* are the same needs a person, and the scan is a tool to
put candidates in front of one — `FeedLibraryAssetTest` does not attempt it,
because a matcher confident enough to decide it silently deleted three feeds
that belonged.

## The topic packs

The country packs were levelled first, and levelling them made the topic
packs' own problem visible. Their counts were fine; their contents were not.

**Origin.** Business & Economy was fifteen feeds, thirteen of them American —
Bloomberg, Forbes, Fortune, CNBC, Business Insider, Yahoo Finance, Seeking
Alpha and five US podcasts — with no Financial Times, no Economist, no Nikkei,
nothing from continental Europe. News was the BBC, the New York Times, the
Guardian and Google News. The counts said the packs were full; what they were
full of said something else.

61 addresses were probed and 44 added across fourteen packs: the FT, the
Economist, Nikkei Asia, Handelsblatt, Les Echos and Business Standard to
Business & Economy; Reuters, AP, Al Jazeera, Deutsche Welle, France 24, ABC
Australia and Euronews to News; The Register, heise, Rest of World and Golem
to Tech; L'Équipe, Marca, Gazzetta and Eurosport to Sports; and so on through
Science, Film, Books, Food, Travel, Architecture, Photography and Music.

The packs where origin does not change what you read — Programming, iOS
Development, Web Development, UI/UX — were left alone. A Swift blog is a Swift
blog, and adding feeds to hit a quota there would be arithmetic rather than
balance.

**Names.** 41 rows carried a title that named no publication, and four were
*blank* — an unlabelled checkbox in a picker. The rest were search-engine
furniture: "All News", "World", "Science Latest", "US Top News and Analysis",
or the whole of "Cricket News Today, Latest Cricket Updates, Cricket…". A feed
reports its own title, and a site optimising that string for a search engine
has not written a name for a row in a list. All 41 now carry the
publication's name.

**And one plain filing error**: the Gaming pack contained a makeup blog. It is
in Beauty now.

## The specialist packs

Five topics nobody had covered, asked for by the reader on 27 September 2026:
**Rugby**, **Formula 1 & Motorsport**, **Golf**, **Forex** and **Crypto**.
Assembled by hand, and held to a stricter bar than the tranches above: a feed
ships only if it was fetched and verified, never on the strength of being
unreachable.

| | |
|---|---|
| Probed, over two rounds | 115 |
| Verified and shipped | 70 |
| Dropped | 45 |

**Verified** means the address answered 200 over https after any redirects,
parsed as RSS or Atom, carried items, and had posted within 45 days. Each one's
own title and newest headline were read as well, which is how a Sky Sports
section id is confirmed to be rugby and not darts. Where a feed redirects, the
pack carries the address it lands on, so a phone does not follow the hop on
every sync.

**Dropped**: 404s, most of them publications that moved or retired a feed
(Golf Digest, Golfweek, RugbyPass, Rugby365, GPblog); 403s from bot walls
(FXStreet, Forex Factory, FX Leaders, GolfWRX, bunkered, F1Technical), which
the older tranches would have kept; ESPN's 202s; a redirect to plain http
(Bitcoin Magazine's old address; its current one ships); and three feeds that
answered but had gone quiet — DL News at 143 days, The Roar's rugby section at
249, FXEmpire at 54.

The category a subscribed feed is filed under is the pack's name, except
Formula 1 & Motorsport, which files as **Motorsport**: the name is on every
card, and the full one would crowd the source out of it. ForexLive has
become investingLive and its feed redirects there; it is listed under the new
name with the old one beside it.

Two packs lean on central banks rather than the trade press, deliberately.
Forex news moves on what the ECB, the Federal Reserve and the Bank of England
publish, and their own feeds are the source the brokers' blogs are quoting.

## Refreshing it

Re-run the fetch and verification against the upstream repository, then
regenerate `app/src/main/assets/library/`. The manifest is `index.json`: slug,
display name, `topic` or `country`, and a feed count. Verification is the part
worth repeating — a directory ages in exactly the way the 62 dropped addresses
show.

## No Reddit feeds

Reddit stops serving RSS on 13 November 2026. The 22 Reddit feeds the packs
carried were each replaced with a feed on the same subject that passed the
usual check (https after redirects, a real feed with items, a post within 45
days), so every pack keeps its size. A test fails if a Reddit feed is added
back.
