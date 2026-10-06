# Setting up a test server for Google Reader sync

*FreshRSS first, then Miniflux. Both are covered here.*

> **Status, 26 September 2026:** the FreshRSS half is done. Sync has run both
> ways against a live FreshRSS server since 25 September; see `ROADMAP.md` §7.
> For setting one up to use, rather than to test, see
> [`SYNC_SERVER_FRESHRSS.md`](SYNC_SERVER_FRESHRSS.md). Miniflux is still untried.

**Handover note.** This is written for a Claude instance working on the
reader's own server, with no context on the Android app. It has one job:
stand up a FreshRSS instance that the Whisper app can sign in to, so that a
feature which has never met a real server can finally be tested against one.

The reader already runs self-hosted services behind Cloudflare, so the
networking is solved. What follows is the FreshRSS side, the two settings that
are easy to miss, and what to actually test once it is up.

---

## Why this exists

Whisper is an Android RSS reader. Alongside fetching feeds directly, it can
sync with a **Google Reader API–compatible server** — FreshRSS, Miniflux, The
Old Reader and others all speak this protocol, which outlived Google Reader
itself.

Whisper's implementation is complete: sign-in, subscription reconciliation,
read state in both directions, star/unstar, subscribe, unsubscribe, rename.
**None of it has ever spoken to a live server.** Every part is unit-tested
against fixtures, and the `stream/contents` response shape in particular was
written from the protocol specification and has never been proven against a
real implementation.

So the goal is not "get FreshRSS running". It is "find out which of Whisper's
assumptions about this protocol are wrong".

---

## Hard requirement: HTTPS

**Whisper refuses plaintext HTTP, in every build.** Its network security
configuration sets `cleartextTrafficPermitted="false"`, so a server at
`http://192.168.1.50:8080` will fail before the protocol is reached — the
error will look like a connection failure, not a configuration problem.

Private IP addresses are *not* blocked for this particular client, so a LAN
address is fine as long as it is served over HTTPS with a certificate the phone
trusts. Given Cloudflare is already in play, a tunnel to a public hostname is
the straightforward route.

---

## Docker

FreshRSS publishes an official image. Something along these lines:

```yaml
# compose.yaml
services:
  freshrss:
    image: freshrss/freshrss:latest
    container_name: freshrss
    restart: unless-stopped
    volumes:
      - ./data:/var/www/FreshRSS/data
      - ./extensions:/var/www/FreshRSS/extensions
    environment:
      TZ: Europe/Dublin          # adjust
      CRON_MIN: '*/20'           # server-side feed refresh
      # Required behind a reverse proxy, or generated links point at the
      # container rather than at the public hostname.
      BASE_URL: https://rss.example.com
      # Cloudflare terminates TLS, so FreshRSS sees the tunnel's address
      # unless it is told to trust it.
      TRUSTED_PROXY: 172.16.0.0/12
    ports:
      - '127.0.0.1:8080:80'      # tunnel connects here; not exposed publicly
```

Point the existing Cloudflare tunnel at `127.0.0.1:8080`.

Verify the environment variable names against the current FreshRSS Docker
documentation before relying on them — this list is written from memory and
that project does change them between releases.

---

## The two settings people miss

Both of these will produce "wrong username or password" in the app, which is a
misleading error, so check them first when sign-in fails.

### 1. API access must be switched on

In FreshRSS: **Settings → Authentication → Allow API access**. It is off by
default. Without it, every API request is rejected regardless of credentials.

### 2. The API password is a separate password

FreshRSS keeps a distinct **API password** from the web login password. Set it
under **Settings → Profile → API password**, or via the `ADMIN_API_PASSWORD`
environment variable at first run.

**The app must be given the API password, not the web one.** This is the single
most common cause of a failed first connection.

### Confirming it works before touching the app

FreshRSS exposes a check page at:

```
https://rss.example.com/api/greader.php
```

Loading it in a browser should produce a short status page rather than a 404 or
a PHP error. If that page is wrong, nothing else will work.

---

## What to enter in the app

- **Server**: `https://rss.example.com/api/greader.php`
- **Username**: the FreshRSS username
- **Password**: the **API password**

Whisper appends the protocol's own paths to that base, so the URL should end at
`greader.php` with nothing after it.

---

## What Whisper actually calls

Useful for reading the server logs, and for knowing where a failure happened:

| Order | Path | Purpose |
|---|---|---|
| 1 | `accounts/ClientLogin` | Sign in. Posts `Email` and `Passwd` as a form. |
| 2 | `reader/api/0/token` | Write token, needed for anything that changes state. |
| 3 | `reader/api/0/subscription/list` | Subscriptions, `output=json`. |
| 4 | `reader/api/0/stream/items/ids` | Which items are unread or starred. |
| 5 | `reader/api/0/stream/contents/<stream>` | **The unverified one.** Maps server ids to article links. |
| 6 | `reader/api/0/edit-tag` | Marks read/unread, starred/unstarred. |
| 7 | `reader/api/0/subscription/edit` | Subscribe, unsubscribe, rename, refile. |

### The id problem, which is where trouble is most likely

The protocol uses item ids in three shapes, and implementations disagree about
which they return:

- **Long form** — `tag:google.com,2005:reader/item/00000000cafebabe`
- **Hex** — `00000000cafebabe`
- **Decimal** — `3405691582`

Whisper normalises all three. FreshRSS is believed to use the long form. If
sync half-works — subscriptions arrive but read state does nothing — this is
the first place to look.

There is a second, subtler problem the app works around. Whisper fetches
articles from the **feeds**, not from the server, so the server's item id never
arrives with the article. `stream/contents` exists purely to answer "which
server id belongs to which article URL", matching on the link. **If that
response does not include article URLs in the shape Whisper expects, read state
will silently do nothing.** That is the most valuable thing this exercise can
find out.

---

## What to test, in order

1. **Sign in.** Wrong credentials should say so; correct ones should land.
2. **Subscriptions arrive.** Add three or four feeds in FreshRSS first, then
   sync from the app. They should appear as sources.
3. **Read state, server to app.** Mark an article read in FreshRSS's web UI,
   sync, and check the app shows it read.
4. **Read state, app to server.** The reverse. This is the path that depends on
   `stream/contents` having worked.
5. **Starring**, both directions.
6. **Subscribe from the app**, and check it appears in FreshRSS.
7. **Unsubscribe in FreshRSS**, sync, and confirm the app **keeps** the feed.
   This is intentional: Whisper never deletes a subscription because a server
   did not mention it, since a partial response or a wrong account would
   otherwise destroy someone's list irrecoverably. If the feed vanishes, that
   is a bug.

## What is worth sending back

More valuable than "it worked" or "it didn't":

- The **raw JSON** from `subscription/list`, `stream/items/ids` and especially
  `stream/contents`. A few hundred lines is plenty. That settles the shape
  questions above without guesswork.
- The exact **id format** in the responses.
- Any request that returned a non-200, with its response body.
- FreshRSS's version.

The Android app logs warnings and errors with tags including
`FeederRssLocalSync`, so `adb logcat -s GoogleReader:* FeederRssLocalSync:*`
alongside the server's access log gives both ends of each request.

---

## Second pass: Miniflux

**Do this after FreshRSS, not instead of it** — but do it.

The Google Reader API is a de facto standard with no specification authority
and no conformance suite. Implementations genuinely differ, most of all on the
item id shapes described above. Testing against one server risks coding to that
server's quirks and calling the protocol done; testing against two is what
tells you the implementation is actually right rather than accidentally
compatible.

FreshRSS goes first because it is what most people who use this feature will
actually be running, and because its implementation is the one most Android
readers are tested against. Miniflux goes second because it is a different
codebase in a different language written by different people, which is exactly
what makes the second test worth running.

### What differs

Miniflux is a single Go binary plus PostgreSQL — lighter than FreshRSS, and
with no PHP anywhere.

```yaml
# compose.yaml
services:
  miniflux:
    image: miniflux/miniflux:latest
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    ports:
      - '127.0.0.1:8081:8080'
    environment:
      DATABASE_URL: postgres://miniflux:secret@db/miniflux?sslmode=disable
      RUN_MIGRATIONS: 1
      CREATE_ADMIN: 1
      ADMIN_USERNAME: admin
      ADMIN_PASSWORD: changeme
      BASE_URL: https://miniflux.example.com
      # The Google Reader endpoint is not on by default.
      GOOGLE_READER_API_ENABLED: 1

  db:
    image: postgres:17-alpine
    restart: unless-stopped
    environment:
      POSTGRES_USER: miniflux
      POSTGRES_PASSWORD: secret
    volumes:
      - ./db:/var/lib/postgresql/data
    healthcheck:
      test: ['CMD', 'pg_isready', '-U', 'miniflux']
```

Check the variable names against Miniflux's current documentation before
relying on them; this is written from memory.

### The endpoint, and the credentials

Miniflux serves the protocol at:

```
https://miniflux.example.com/googlereader
```

That is the whole base URL to give the app — there is no `.php` and no `/api`
prefix, which is the first thing to get wrong when moving across from FreshRSS.

Miniflux, like FreshRSS, keeps **separate Google Reader credentials** from the
web login. Set them per-user in **Settings → Integrations → Google Reader**.
Using the web password will report as bad credentials, exactly as it does on
FreshRSS.

### What the second pass is looking for

Not "does it work" so much as "does it work *differently*". Specifically:

- **Item id shapes.** If read state syncs on one server and silently does
  nothing on the other, this is almost certainly why, and it is the single most
  likely bug in Whisper's implementation.
- **The `stream/contents` response.** Whether both servers include article URLs
  in the same place and the same form. Whisper matches server ids to articles
  by link, so a difference here breaks read state without breaking anything
  visible.
- **Whether anything Whisper does assumes FreshRSS.** A request that FreshRSS
  tolerates and Miniflux rejects is a bug in the app, even though the app
  appeared to work.

Send back the same raw JSON as for FreshRSS. The value is in the comparison.

## Scope note

Nothing here needs changing in FreshRSS beyond its own settings. If a test
fails, the fix belongs in Whisper's Android code, not on the server — please
report rather than work around, since a workaround on the server would hide a
bug every other user of the app would still hit.
