#!/usr/bin/env bash
# One command for a preview build (20 November 2026): the full tests and
# lint, the signed preview, a check that it's signed with the release key,
# and a copy in out/preview/. Stops at the first thing that fails.
#
#   scripts/preview.sh
#
# Needs: a clean, committed tree (the build is named after the commit),
# ANDROID_HOME (defaults to /opt/android-sdk), and keystore.properties
# pointing at the release key. What it doesn't do: send the file, write the
# board's test steps, or Szept; those are by hand, from its last lines.
set -euo pipefail
cd "$(dirname "$0")/.."

# The release key's SHA-256 fingerprint (public, safe to keep here).
RELEASE_SHA256="d45452456c89feaf2dd5a49010ef149370a409b63a1531027bd4caa82f88b164"
export ANDROID_HOME="${ANDROID_HOME:-/opt/android-sdk}"

if [ -n "$(git status --porcelain -- app)" ]; then
  echo "Uncommitted changes under app/: commit first, so the build matches its name." >&2
  exit 1
fi
[ -f keystore.properties ] || { echo "keystore.properties is missing: the preview would be unsigned." >&2; exit 1; }

hash=$(git rev-parse --short HEAD)
echo "== Tests and lint ($hash)"
./gradlew -q :app:testDebugUnitTest :app:lintDebug
python3 -I - <<'EOF'
import glob, xml.etree.ElementTree as E
t = f = 0
for p in glob.glob("app/build/test-results/testDebugUnitTest/*.xml"):
    r = E.parse(p).getroot()
    t += int(r.get("tests")); f += int(r.get("failures")) + int(r.get("errors"))
print(f"{t} tests, {f} failed")
raise SystemExit(1 if f else 0)
EOF

echo "== Preview build"
./gradlew -q :app:assemblePreview
apk=$(ls app/build/outputs/apk/preview/*-"$hash"_preview.apk)
apksigner=$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)/apksigner
if ! "$apksigner" verify --print-certs "$apk" 2>/dev/null | grep -q "SHA-256 digest: $RELEASE_SHA256"; then
  echo "Not signed with the release key: it would not install over the last preview." >&2
  exit 1
fi
mkdir -p out/preview
cp "$apk" out/preview/
echo "== Ready: out/preview/$(basename "$apk")"
echo "Next: send it, add the build's steps to the board (T1: About ends in $hash), Szept, BUILDLOG."
