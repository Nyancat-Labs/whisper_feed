#!/usr/bin/env python3
"""
Fetches real feeds and their newest articles for the reader check.

    python3 -I scripts/real-feed-check.py fetch <subscriptions.opml> <data dir> [per feed]
    REAL_FEED_DIR=<data dir> ./gradlew :app:testDebugUnitTest --tests '*RealFeedReport*'
    python3 -I scripts/real-feed-check.py report <data dir>

The first step only downloads: each feed as it is served, and the pages its
newest items link to, asked for as the app asks (see HttpIdentity). The second
runs the app's own code over them - the feed parser, the page clean-up,
Readability and the reader - and writes what the reader would show, with
anything that looks wrong. The third gathers that by source.

Nothing here is the app's logic. Links are picked out of the feed only to know
which pages to fetch; the app's parser decides everything the report is about.
"""
import concurrent.futures
import gzip
import hashlib
import json
import os
import ssl
import sys
import urllib.request
import xml.etree.ElementTree as ET

FEED_AGENT = "Mozilla/5.0 (compatible; Whisper/real-feed-check; +https://github.com/Nyancat-Labs/whisper_feed)"
ARTICLE_AGENT = ("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) "
                 "Chrome/126.0.0.0 Mobile Safari/537.36")
HTML_ACCEPT = ("text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,"
               "image/webp,*/*;q=0.8")
MAX_BYTES = 5 * 1024 * 1024

CONTEXT = ssl.create_default_context(cafile=os.environ.get("SSL_CERT_FILE"))


def get(url, agent, accept="*/*"):
    request = urllib.request.Request(url, headers={"User-Agent": agent, "Accept": accept})
    try:
        with urllib.request.urlopen(request, timeout=30, context=CONTEXT) as response:
            body = response.read(MAX_BYTES)
            # Some servers compress without being asked. OkHttp undoes that
            # itself, so the app never sees it; nor should the check.
            if body[:2] == b"\x1f\x8b":
                body = gzip.decompress(body)
            return response.status, response.geturl(), response.headers.get("Content-Type", ""), body
    except urllib.error.HTTPError as e:
        return e.code, url, "", b""
    except Exception as e:  # noqa: BLE001 - every failure is a result here
        return type(e).__name__, url, "", b""


def name_for(url):
    return hashlib.sha1(url.encode()).hexdigest()[:16]


def local(tag):
    return tag.rsplit("}", 1)[-1]


def item_links(xml_bytes, count):
    """The first [count] article links, RSS, RDF or Atom."""
    try:
        root = ET.fromstring(xml_bytes)
    except ET.ParseError:
        return []
    links = []
    for element in root.iter():
        if local(element.tag) == "item":
            link = next((c.text for c in element if local(c.tag) == "link" and c.text), None)
            if link is None:
                guid = next((c for c in element if local(c.tag) == "guid"), None)
                if guid is not None and guid.get("isPermaLink", "true") != "false" and (guid.text or "").startswith("http"):
                    link = guid.text
        elif local(element.tag) == "entry":
            candidates = [c for c in element if local(c.tag) == "link"]
            chosen = next((c for c in candidates if c.get("rel", "alternate") == "alternate"), None)
            link = chosen.get("href") if chosen is not None else None
        else:
            continue
        if link:
            links.append(link.strip())
        if len(links) == count:
            break
    return links


def fetch(opml, out, per_feed):
    os.makedirs(os.path.join(out, "feeds"), exist_ok=True)
    os.makedirs(os.path.join(out, "pages"), exist_ok=True)
    feeds = [
        {"title": o.get("title") or o.get("text") or "", "url": o.get("xmlUrl")}
        for o in ET.parse(opml).iter("outline") if o.get("xmlUrl")
    ]

    def one_feed(feed):
        status, final, kind, body = get(feed["url"], FEED_AGENT)
        feed.update(status=status, finalUrl=final, contentType=kind, file=None, pages=[])
        if body:
            feed["file"] = "feeds/" + name_for(feed["url"])
            with open(os.path.join(out, feed["file"]), "wb") as f:
                f.write(body)
        feed["links"] = item_links(body, per_feed) if body else []
        return feed

    with concurrent.futures.ThreadPoolExecutor(16) as pool:
        feeds = list(pool.map(one_feed, feeds))

    def one_page(link):
        status, final, kind, body = get(link, ARTICLE_AGENT, HTML_ACCEPT)
        page = {"url": link, "status": status, "finalUrl": final, "contentType": kind, "file": None}
        if body:
            page["file"] = "pages/" + name_for(link)
            with open(os.path.join(out, page["file"]), "wb") as f:
                f.write(body)
        return page

    links = [link for feed in feeds for link in feed["links"]]
    with concurrent.futures.ThreadPoolExecutor(16) as pool:
        pages = dict(zip(links, pool.map(one_page, links)))
    for feed in feeds:
        feed["pages"] = [pages[link] for link in feed.pop("links")]

    with open(os.path.join(out, "index.json"), "w") as f:
        json.dump(feeds, f, indent=1)
    got = sum(1 for feed in feeds if feed["file"])
    print(f"{got} of {len(feeds)} feeds, {sum(1 for p in pages.values() if p['file'])} of {len(pages)} pages")


def report(out):
    with open(os.path.join(out, "results.json")) as f:
        results = json.load(f)
    by_flag = {}
    for feed in results:
        flags = [flag for article in feed["articles"] for flag in article["flags"]]
        kinds = sorted(set(flag["kind"] for flag in flags))
        problem = feed.get("problem")
        print(f"\n## {feed['title']}  ({len(feed['articles'])} articles)" + (f"  -- {problem}" if problem else ""))
        for kind in kinds:
            hits = [(a, fl) for a in feed["articles"] for fl in a["flags"] if fl["kind"] == kind]
            by_flag.setdefault(kind, set()).add(feed["title"])
            print(f"- {kind} x{len(hits)}: {hits[0][1]['detail'][:160]}")
    print("\n## Sources per kind")
    for kind, titles in sorted(by_flag.items(), key=lambda kv: -len(kv[1])):
        print(f"- {kind}: {len(titles)} - {', '.join(sorted(titles))}")


if __name__ == "__main__":
    if sys.argv[1] == "fetch":
        fetch(sys.argv[2], sys.argv[3], int(sys.argv[4]) if len(sys.argv) > 4 else 4)
    elif sys.argv[1] == "report":
        report(sys.argv[2])
