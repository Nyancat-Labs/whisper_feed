#!/usr/bin/env python3
"""
What matters in a Whisper diagnostics report, first (20 November 2026).

    python3 -I scripts/diag-summary.py <report.txt> [<earlier report.txt>]

Prints the build, the feeds failing and why, feeds that came back empty,
feeds whose latest fetches all failed, slow syncs, and stopped runs. Given an
earlier report too, it says what is new since then. Read it before guessing:
on 20 November a guess about Quartz (a block) was wrong, and the report
said plainly it was an empty feed.
"""
import re
import sys

FEED_LINE = re.compile(r"^  (.{28}) (\d\d:\d\d .*)$")
FETCH = re.compile(r"(\d\d:\d\d) (new \d+|unchanged|same|failed [^·]+?|no network)(?: ([<\d.]+ ?[KM]?B))?(?: ·|$)")


def parse(path):
    text = open(path, encoding="utf-8", errors="replace").read()
    report = {"text": text, "feeds": {}, "syncs": [], "failing": []}
    m = re.search(r"Version:\s+(\S+)", text)
    report["version"] = m.group(1) if m else "?"
    m = re.search(r"Generated:\s+(.+)", text)
    report["generated"] = m.group(1).strip() if m else "?"
    section = None
    for line in text.splitlines():
        if line.startswith("== "):
            section = line.strip("= ").strip()
            continue
        if section and section.startswith("Feeds"):
            fm = FEED_LINE.match(line)
            if fm:
                name = fm.group(1).strip()
                report["feeds"][name] = [(t, what.strip(), size) for t, what, size in FETCH.findall(fm.group(2))]
        elif section and section.startswith("Sync history"):
            sm = re.match(r"^  (\d\d \d\d:\d\d:\d\d)\s+(.+?)\s+((?:ok|stopped|failed|nothing)\b.*?)\s+(\d+)s$", line)
            if sm:
                report["syncs"].append((sm.group(1), sm.group(2).strip(), sm.group(3), int(sm.group(4))))
        elif section == "Counts":
            cm = re.match(r"^\s+on\s+(.+?)\s+last sync .* fails (\d+)", line)
            if cm:
                report["failing"].append((cm.group(1).strip(), int(cm.group(2))))
    return report


def describe(fetches):
    return " · ".join(f"{t} {w}{' ' + s if s else ''}" for t, w, s in fetches)


def summary(r, earlier=None):
    out = [f"Build {r['version']}, report {r['generated']}"]
    m = re.search(r"Not on the server: \d+\n((?:  .+\n)+)", r["text"])
    if m:
        out.append("Not on the sync server:\n" + m.group(1).rstrip())

    out.append("\nFailing now (counted against the source):")
    for name, n in r["failing"] or [("none", 0)]:
        latest = next((f for k, f in r["feeds"].items() if k.startswith(name[:28].strip())), [])
        why = latest[0][1] if latest else ""
        out.append(f"  {name}: {n} in a row" + (f", latest: {why}" if why else "") if n else "  none")

    empty = [k for k, f in r["feeds"].items() if any(w == "failed empty feed" for _, w, _ in f)]
    if empty:
        out.append("\nCame back empty: " + ", ".join(empty))

    all_failed = [k for k, f in r["feeds"].items() if f and all(w.startswith("failed") for _, w, _ in f)]
    if all_failed:
        out.append("\nEvery recent fetch failed:")
        out += [f"  {k}: {describe(r['feeds'][k])}" for k in all_failed]

    flaky = [k for k, f in r["feeds"].items() if k not in all_failed and any(w.startswith("failed") for _, w, _ in f)]
    if flaky:
        out.append("\nFailed some of the time:")
        out += [f"  {k}: {describe(r['feeds'][k])}" for k in flaky]

    slow = [s for s in r["syncs"] if s[3] >= 120]
    stopped = [s for s in r["syncs"] if s[2].startswith("stopped") or s[2].startswith("failed")]
    if slow:
        out.append("\nSyncs over 2 minutes:")
        out += [f"  {d} {kind}: {secs}s" for d, kind, _, secs in slow]
    if stopped:
        out.append("\nStopped or failed runs:")
        out += [f"  {d} {kind}: {what[:90]}" for d, kind, what, _ in stopped]
    asleep = re.findall(r"phone asleep for (\d+) min", r["text"])
    if asleep:
        out.append(f"\nSyncs stretched by sleep: {len(asleep)} (up to {max(map(int, asleep))} min)")

    if earlier:
        was = {n for n, _ in earlier["failing"]}
        now = {n for n, _ in r["failing"]}
        out.append("\nSince the earlier report:")
        out.append("  newly failing: " + (", ".join(sorted(now - was)) or "none"))
        out.append("  recovered: " + (", ".join(sorted(was - now)) or "none"))
    return "\n".join(out)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    now = parse(sys.argv[1])
    before = parse(sys.argv[2]) if len(sys.argv) > 2 else None
    print(summary(now, before))
