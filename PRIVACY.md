# Privacy

**Short version: Whisper has no servers. Nothing you do in it reaches us,
because there is nowhere for it to arrive.**

This document describes what the app actually does, not what it intends to do.
Everything in it was checked against the source, and the source is here for you
to check it against too.

Whisper is made by **Nyancat Labs**, a company registered in Ireland, which is
the data controller for the purposes of the GDPR. Contact:
[hej@nyancatlabs.com](mailto:hej@nyancatlabs.com).

Last reviewed: September 2026, for Whisper 1.0.0. The most recent change was
the addition of time-on-screen, described under "What stays on your phone".

---

## What we collect

Nothing.

There is no account with us, no server we operate, no database we hold, and no
analytics, crash-reporting, advertising or tracking library in the app. Not one
— that is a statement about the dependency list, which you can read.

We could not tell you how many people use Whisper, which feeds are popular, or
whether anyone opened it today. That is not restraint; there is simply no
mechanism.

## What stays on your phone

All of it:

- The feeds you subscribe to, and the categories you file them under
- Articles fetched from those feeds, including their text, and the full page
  text where you have asked for that
- Which articles you have read, saved and pinned, and which you opened in full
- **How long each article's card was on your screen**, to a resolution of half
  a second and capped at thirty. This is new, and it is the most personal thing
  the app records: it is the difference between a headline you hurried past and
  one you stopped at, which is exactly what the ordering needs and exactly what
  nobody would expect an RSS reader to know. It is measured only while the feed
  is actually in front of you — not while the app is in the background or the
  panel is shut — and never leaves the device
- **How long you spend inside an article you open**, capped at ten minutes.
  More personal again than the line above, and recorded for the same reason:
  opening an article and coming straight back out is a judgement that it was
  not worth reading, and without this the app cannot tell that from reading it
  to the end.

  It is counted forward in one-second steps taken only while the article is on
  your screen, and never worked out by subtracting the time you opened it from
  the time you came back. That is not a detail of the implementation. It means
  closing the app, locking the phone or losing the process mid-article records
  the reading you actually did and then stops, rather than leaving a start time
  that a later resume turns into a three-day read.

  For an article you open in your **own browser**, Whisper is not on screen
  and cannot time anything directly, so it records how long it was away
  instead — from the moment it hands the link over to the moment you come
  back. That is a weaker measurement and is treated as one: it is held in
  memory only and lost if the app is closed in the meantime, and anything over
  fifteen minutes is **thrown away entirely** rather than rounded down,
  because a long absence is not evidence of a long read and recording it as
  one would be a guess wearing a number's clothes.

  Whisper never learns which page you were on, what you did while you were
  away, or whether you were reading at all — only how long it was until you
  returned to it.
- Which bundled collections your own sources fall into, so the app can suggest
  a publication that sits beside them. Worked out on the device against a file
  that ships inside the app: no query goes anywhere, and your subscription list
  is never compared against anybody else's. That last one is the mechanism
  every recommendation feed is built on, and it is the one thing this app will
  not do.
- Which sources you engage with most, which the app uses to decide what to show
  larger. Per source, and derived from the above: nothing about *which*
  articles, or what was in them
- Sources you have hidden, and words you have blocked
- Every setting

This lives in the app's private storage. Uninstalling removes it. So does
Android's "clear storage".

## What leaves your phone, and where it goes

Whisper makes network requests. It has to — a reader that never fetches
anything is a blank screen. Here is the complete list.

### 1. The feed servers you chose

When Whisper fetches a feed, that server sees what any web server sees: your IP
address, the time, and a `User-Agent` header identifying the app and version.
These are servers **you** added. We are not in the middle of that request and
never see it.

### 2. Article pages, if you turn on full-text fetching

Off by default, per-feed or globally. When on, Whisper fetches the article's own
page so it can show the whole piece rather than a summary. That publisher's
server sees the same things a browser would. No cookies and no account are sent,
so anything behind a paywall stays behind it.

### 3. Images inside articles

Loaded from wherever the article points at them, which is usually the
publisher's own servers or their CDN.

### 4. Open-Meteo, only if you turn on the glance row

The weather and sunrise strip is optional. When it is on, Whisper asks
[open-meteo.com](https://open-meteo.com) for a forecast.

Whisper **does not request location permission and cannot read your device's
location.** You type a place name. That name is sent to Open-Meteo's geocoding
service to turn it into coordinates, and the coordinates are then **rounded to
two decimal places — about a kilometre — before any forecast is requested.**
Open-Meteo sees an approximate area and your IP address, and nothing else.

Turn the glance row off and no request is ever made.

### 5. A sync server, only if you set one up

If you connect a Google Reader–compatible server — FreshRSS, Miniflux, or
similar — Whisper signs in with the username and password you give it and
syncs read state and subscriptions with **your** server. Plaintext HTTP is
refused outright, so those credentials cannot cross the network unencrypted.
The password is used to sign in and is not kept: what is stored is the
server's sign-in token, which the server can revoke. It is sealed on the device
with a key of Whisper's own, held in Android's hardware-backed keystore, and
is left out of every backup.

If you type the address as `http://`, Whisper corrects it to `https://` in
the field, before the password is sent, and tells you it did. Nothing is
guessed behind your back — you can see the address that will be used, and
it is the corrected one that gets saved.

This is also the one part of Whisper allowed to reach an address on your own
network, because a self-hosted server is usually *on* your own network: at
`192.168.1.50`, at `nas.local`, or behind Tailscale. The rule that keeps
everything else off your LAN exists because feed and article addresses come
from publishers. This one came from you.

### 6. Your own backup destination

If you turn on backups, the OPML and settings files are written to a folder
**you** pick with Android's document picker — local storage, an SD card, or a
cloud folder if that is what you choose. Whisper writes the file; where that
folder actually lives is between you and whoever provides it.

### 7. Android's own backup, only if you ask for it

Off by default, and asked separately for cloud backup and for phone-to-phone
transfer, because those are different questions. Your saved credentials are
excluded from both routes regardless of what you choose.

## What Whisper refuses to do

- **No plaintext HTTP request ever leaves the device.** Android is told to
  refuse cleartext traffic outright, at the platform level, so this holds for
  every request the app makes rather than for the ones we remembered to check.
  It matters because article HTML is rendered in a WebView, and anyone on the
  network can rewrite a page in transit.

  What happens to an `http://` address depends on what is at the other end of
  it, and the difference is deliberate:

  - **Feeds, article pages and images are retried over HTTPS.** Many feeds are
    still listed with an `http://` address years after the site itself moved,
    and the scheme is rewritten before any connection is opened. If the server
    genuinely has no HTTPS, the fetch fails and the source appears under Broken
    feeds.
  - **A sync server is corrected where you can see it, not on the way to the
    socket.** Typing `http://` in the account screen rewrites the field to
    `https://` and says so. Same host, checked against the system's own
    certificate list — but a password is the wrong thing to reroute quietly,
    so it happens in front of you instead.
- **Private and local network addresses are refused.** A feed or an article link
  pointing at `192.168.x.x`, `10.x.x.x`, `localhost` or similar is not fetched,
  on every redirect hop rather than only the first. A hostile feed cannot use
  your phone to reach inside your own network. This holds for every client the
  app builds, not only the ones checked by hand: a test reads the source and
  fails the build if a client neither carries the guard nor declares, in a name
  that test can find, that it is the exception. There is one exception — your
  own sync server, above — and the same test fails if a second one appears.
- **The in-app browser opens `http` and `https` only.** Not `file:`, not
  `content:`, not `javascript:`.

## Permissions, and why

| Permission | Why |
|---|---|
| `INTERNET` | Fetching feeds. |
| `ACCESS_NETWORK_STATE` | Knowing whether to sync now or wait for wifi. |
| `SYSTEM_ALERT_WINDOW` | Not used for drawing. Holding it exempts the app from Android's background-activity-launch restriction, which is what lets a tap in the launcher panel open an article. Without it, the panel renders and taps do nothing. |
| `READ/WRITE_EXTERNAL_STORAGE` | Android 9 and older only, capped in the manifest. Newer versions use the document picker, which grants access to one folder you choose. |

There is no location permission, no contacts, no camera, no microphone, and no
"query all packages".

## Your rights

The GDPR gives you rights over personal data a controller holds about you.
Nyancat Labs is that controller, and holds none of yours — so there is nothing
for us to disclose, correct, export or delete. You are welcome to ask, and the
answer will be that we have nothing, which the rest of this document explains
how to verify for yourself.

What you can do yourself:

- **Export**: OPML for your subscriptions, a JSON file for your settings, both
  from the Backup screen.
- **Delete**: uninstall the app, or clear its storage in Android settings.
- **Selective deletion**: remove a source and its articles go with it; clear a
  source's articles and keep the source.

If you want data removed from a *feed server* or *sync server*, that is a
request to whoever runs it, not to us.

## Children

Whisper shows whatever feeds it is given. It has no content rating, no
moderation, and no filtering beyond the blocked-words list, because it has no
view of what a feed will contain. It is not designed or directed at children.

## Changes

This file lives in the repository. Its history is the change log — every
revision is a commit, with a date and a diff.

## Contact

**Nyancat Labs** — [hej@nyancatlabs.com](mailto:hej@nyancatlabs.com)

That address reaches us for anything: a data protection question, a bug, or a
disagreement with something written here. Bug reports are usually better raised
as an issue on the repository, where other people can see them and where the
Report a problem button in the app already sends its diagnostics.

We have no data protection officer, because appointing one is required only of
organisations whose core activity is large-scale monitoring or processing of
special-category data. Whisper processes neither, and holds nothing.
