# Sync with your own FreshRSS server

Whisper works with no account at all. If you want your subscriptions and what
you have read to stay in step across devices, you can run a small
[FreshRSS](https://freshrss.org) server for Whisper to sync with. This guide
sets one up with Docker.

You need:

- a machine that is always on, with Docker and Docker Compose
- an **https** address for it: Whisper won't send a password over plain http.
  A Cloudflare Tunnel, Tailscale Funnel, or a reverse proxy with a certificate
  all work.

Throughout, `rss.example.com` stands for your own address.

## 1. Make a folder

```sh
mkdir -p ~/freshrss && cd ~/freshrss
```

## 2. Create `.env`

Change every value. Keep this file private.

```ini
BASE_URL=https://rss.example.com
ADMIN_EMAIL=you@example.com
ADMIN_PASSWORD=change-me-web-login
ADMIN_API_PASSWORD=change-me-api-password
```

The **API password** is the one Whisper uses. Make it different from the web
login.

## 3. Give FreshRSS a reader's identity

Some sites turn away requests that don't look like a feed reader or browser.
FreshRSS then can't fetch those feeds, and refuses to add them. One file sets
an identity for every feed:

```sh
printf '%s\n' '<?php' "define('FRESHRSS_USERAGENT', 'Mozilla/5.0 (compatible; Whisper/1.0.0; +https://github.com/Nyancat-Labs/whisper_feed)');" > constants.local.php
```

FreshRSS reads `constants.local.php` at start-up; it is its supported place for
local settings. A feed's own **User agent** setting, under *Advanced*, still
wins over it.

## 4. Create `compose.yaml`

```yaml
services:
  freshrss:
    image: freshrss/freshrss:latest
    container_name: freshrss
    restart: unless-stopped
    ports:
      - "127.0.0.1:8080:80"   # only the tunnel or proxy reaches it
    volumes:
      - ./data:/var/www/FreshRSS/data
      - ./extensions:/var/www/FreshRSS/extensions
      - ./constants.local.php:/var/www/FreshRSS/constants.local.php:ro
    environment:
      TZ: Europe/Dublin
      CRON_MIN: "3,33"         # the server refreshes feeds twice an hour
      TRUSTED_PROXY: 172.16.0.0/12 192.168.0.0/16
      FRESHRSS_INSTALL: |-
        --api-enabled
        --base-url ${BASE_URL}
        --db-type sqlite
        --default-user admin
        --language en
      FRESHRSS_USER: |-
        --api-password ${ADMIN_API_PASSWORD}
        --email ${ADMIN_EMAIL}
        --language en
        --password ${ADMIN_PASSWORD}
        --user admin
```

Set `TZ` to your own time zone. `FRESHRSS_INSTALL` and `FRESHRSS_USER` only
apply on the first start, when `./data` is empty.

## 5. Start it

```sh
docker compose up -d
docker compose logs -f freshrss   # Ctrl+C once it says it is ready
```

## 6. Put it on https

Point your tunnel or proxy at `http://localhost:8080`. With a Cloudflare Tunnel,
that is **Networks → Tunnels → your tunnel → Public hostname**:

- **Hostname:** `rss.example.com`
- **Service:** `http://localhost:8080`

## 7. Check it

- `https://rss.example.com` shows the FreshRSS login page. Sign in with `admin`
  and `ADMIN_PASSWORD`.
- `https://rss.example.com/api/greader.php` shows a short status page, not a
  404 or an error.
- In FreshRSS, **Settings → Authentication → Allow API access** is ticked. The
  install turns it on; check it anyway.

## 8. Sign in from Whisper

Go to **Settings → Account** and enter:

- **Server:** `https://rss.example.com`. The web address is enough: Whisper adds
  `/api/greader.php` itself.
- **Username:** `admin`
- **Password:** your `ADMIN_API_PASSWORD`, not the web password

Whisper syncs straight away. The first time, feeds either side is missing are
copied across. After that, every sync is two way: subscriptions, read and
unread, and saved articles.

The Account screen shows what the last sync did: how many feeds the server has,
how many articles it knows, and what was sent and received.

## If something goes wrong

**"Wrong username or password"**

It's almost always one of these:

1. The web password instead of the **API password**.
2. **Allow API access** is off.

**Some feeds are "On this phone only"**

The server couldn't fetch them, usually because the site blocks servers. They
still update on the phone, but reading them won't sync. Check step 3 is in
place:

```sh
docker exec freshrss cat /var/www/FreshRSS/constants.local.php
```

Then tap **Sync now** in Whisper, which offers those feeds to the server again
at once. If one is still refused, add it in FreshRSS by hand, under
**Subscription management → Add a feed**. The message and **Check FreshRSS
logs** will say why.

The log line says which of three things it is:

| The log says | What it means | Fix |
|---|---|---|
| status code `202` or `403` | The site blocks servers | Step 3 usually fixes it. A `403` with a Cloudflare challenge can't be passed by any server: look for the site's newer feed address, and change the address in Whisper |
| "A feed could not be found", status `200` | The download worked, but the site labels it oddly (Slate sends `text/rss+xml`) | Add it in FreshRSS with `#force_feed` on the end of the address. Whisper ignores that part when it compares addresses |
| anything else | The feed may have moved or broken | Check the address in a browser |

**Locked out of the web interface**

If the authentication method was changed to HTTP and the login page is now a
403, set it back to the login form:

```sh
docker exec -u www-data freshrss php ./cli/reconfigure.php --auth-type form
```

**Where did I install it?**

Docker remembers:

```sh
docker inspect freshrss --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}'
```

## Updating later

```sh
docker compose pull && docker compose up -d
```

Your feeds, users and `constants.local.php` are kept outside the container.
Back up the whole folder.
