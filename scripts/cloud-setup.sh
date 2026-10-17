#!/usr/bin/env bash
# Setup script for Claude Code cloud sessions on Whisper.
#
# Paste into the environment's settings (environment menu in the session's
# title bar → Edit → Setup script). It runs when a new container starts, so
# the session can build and test straight away instead of spending a quarter
# of an hour fetching the Android SDK first. Safe to run twice.
#
# Not for the signing key: the .p12 and keystore.properties are uploaded to
# each session by hand, never kept in the environment.
set -euo pipefail

SDK=/opt/android-sdk
TOOLS=$SDK/cmdline-tools/latest/bin

# Retries for Maven Central and Google, which answer 429 under load.
fetch() {
  local url=$1 out=$2
  for i in 1 2 3 4 5; do
    curl -sSfLo "$out" "$url" && return 0
    sleep $((i * 10))
  done
  return 1
}

# Android command-line tools, then the platform and build tools the app uses
# (compileSdk 37 in app/build.gradle.kts).
if [ ! -x "$TOOLS/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools"
  fetch https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip /tmp/clt.zip
  unzip -q -o /tmp/clt.zip -d "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm /tmp/clt.zip
fi
yes | "$TOOLS/sdkmanager" --licenses >/dev/null || true
"$TOOLS/sdkmanager" "platforms;android-37.0" "build-tools;37.0.0" "platform-tools" >/dev/null

# Gradle finds the SDK through ANDROID_HOME.
echo "export ANDROID_HOME=$SDK" > /etc/profile.d/android-sdk.sh
grep -q ANDROID_HOME ~/.bashrc 2>/dev/null || echo "export ANDROID_HOME=$SDK" >> ~/.bashrc

# Robolectric's Android jar (200 MB). Robolectric fetches it itself on the
# first test run and fails through the proxy, failing every Robolectric test.
# The version follows Robolectric (4.17) and the tests' @Config sdk (35); when
# either changes, the failing test names the new one.
V=15-robolectric-13954326-i7
D=~/.m2/repository/org/robolectric/android-all-instrumented/$V
if [ ! -s "$D/android-all-instrumented-$V.jar" ]; then
  mkdir -p "$D"
  base=https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/$V/android-all-instrumented-$V
  fetch "$base.pom" "$D/android-all-instrumented-$V.pom"
  fetch "$base.jar" "$D/android-all-instrumented-$V.jar"
fi

echo "Android SDK ready at $SDK"
