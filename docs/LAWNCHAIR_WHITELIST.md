# Getting Whisper onto Lawnchair's feed whitelist

## Where to send it

**A pull request to [LawnchairLauncher/lawnchair](https://github.com/LawnchairLauncher/lawnchair),
targeting `16-dev`.** Their development workflow
(docs.lawnchair.app/developers/standards/development-workflow) says PRs target
`16-dev`, and a single-file change is "Simple": open the PR, assign a reviewer,
enable auto-merge. Commits follow Conventional Commits, `type(scope): subject`.

## The line

In `lawnchair/src/app/lawnchair/FeedBridge.kt`, in `initializeWhitelist`,
straight after the Neo Feed entry:

```kotlin
whitelist["com.nyancatlabs.whisper"] = getSignatureHash(context, "com.nyancatlabs.whisper")
```

### Why this form and not a fixed hash

The whitelist is a map with one hash per package, and Whisper is signed three
ways:

- Google Play: Google's app signing key (Play re-signs bundles)
- F-Droid: F-Droid's key, SHA-256 A8:DD:E0:89:93:25:1F:DF:4A:35:99:C2:C4:FF:53:CF:CE:88:0C:19:26:3E:20:08:03:04:5C:81:16:AB:71:6B
- GitHub releases and Obtainium: ours, SHA-256 D4:54:52:45:6C:89:FE:AF:2D:D5:A4:90:10:EF:14:93:70:A4:09:B6:3A:15:31:02:7B:D4:CA:A8:2F:88:B1:64

A fixed entry would let in one of the three. `getSignatureHash` reads the hash
of whatever is installed, which is how Neo Feed, Whisper's parent, is listed.

Known limit, shared with Neo Feed: the whitelist is built once when Lawnchair
starts, so a Whisper installed afterwards shows up after Lawnchair restarts.

## The pull request

**Commit message:** `feat(feed): add Whisper to the feed provider whitelist`

**Title:** `feat(feed): add Whisper to the feed provider whitelist`

**Body** (their template's headings):

```
### Description

Adds Whisper (`com.nyancatlabs.whisper`) to the feed provider whitelist, so it can be chosen as the feed without turning on "Ignore feed whitelist".

Whisper is an open-source RSS reader (GPL-3.0, https://github.com/Nyancat-Labs/whisper_feed) and a fork of Neo Feed, which is already on the list. It implements the same `com.android.launcher3.WINDOW_OVERLAY` service, so nothing else changes on Lawnchair's side.

### Reasoning

The entry uses `getSignatureHash`, as Neo Feed's does, rather than a fixed hash. Whisper ships signed three ways: Google Play re-signs it with Google's key, F-Droid builds and signs it with theirs, and GitHub releases carry ours. The whitelist holds one hash per package, so a fixed value would only let in one of the three. Like Neo Feed, a Whisper installed while Lawnchair is running appears after Lawnchair restarts.

Whisper is a reader rather than a Discover replacement: sources the user picks, no account, no recommendation service, and nothing leaves the device unless the user turns on a backup.

### Testing

1. Install Whisper from Google Play, F-Droid or the GitHub release.
2. With "Ignore feed whitelist" off, open Lawnchair's settings, then Feed provider.
3. Whisper is listed and can be chosen; swiping right from the home screen opens it.

Whisper has been in daily use on Lawnchair through the debug toggle. I have not built Lawnchair with this change myself; the line mirrors the Neo Feed entry above it.

### Type of change

:x: **Bug fix** (A non-breaking change that fixes an issue)
:white_check_mark: **New feature** (A non-breaking change that adds functionality)
:x: **Breaking change** (A fix or feature that would cause existing functionality to not work as expected)
:x: **Refactor** (A code change that neither fixes a bug nor adds a feature)
:x: **Performance** (A code change that improves performance)
:x: **Style** (Code style changes)
:x: **Docs** (Changes to documentation)
:x: **Chore** (Changes to the build process or other tooling)
```

## Status

- [x] Release signing key, and all three signers known
- [x] A tagged release on every channel (1.0.5, 18 October 2026)
- [x] Pull request opened, 18 October 2026:
      [LawnchairLauncher/lawnchair#7399](https://github.com/LawnchairLauncher/lawnchair/pull/7399),
      from `defsix:patch-1`, commit 0f13ea3
- [x] Approved by MrSluffy and merged into `16-dev`, 24 October 2026
- [ ] In a Lawnchair release (then: README and the in-app Lawnchair note can
      drop the "Ignore feed whitelist" step). Until then the step stays:
      Lawnchair releases people have installed still carry the old list.
