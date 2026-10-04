# Getting Whisper onto Lawnchair's feed whitelist

## Where to send it

**A pull request to [LawnchairLauncher/lawnchair](https://github.com/LawnchairLauncher/lawnchair),
targeting the `15-dev` branch.** Their CONTRIBUTING.md is explicit that all PRs
target `15-dev`, and classes a one-line whitelist addition as "simple,
self-contained work": open the PR, assign a reviewer, enable auto-merge.

Worth raising it in chat first, since it needs a maintainer to accept a
third-party package:

- Telegram: https://t.me/lccommunity
- Discord: https://discord.com/invite/3x8qNWxgGZ

## What has to be in the PR

One line in `lawnchair/src/app/lawnchair/FeedBridge.kt`, in
`initializeWhitelist`:

```kotlin
whitelist["com.nyancatlabs.whisper"] = 0x________   // release signing certificate hash
```

**This cannot be written until the release signing key exists.** The value is
the hash of the certificate the release APK is signed with; a debug-signed
build would put the wrong number in Lawnchair's source permanently.

There is a second form already in that file:

```kotlin
whitelist["com.saulhdev.neofeed"] = getSignatureHash(context, "com.saulhdev.neofeed")
```

which computes the hash from whatever is installed — i.e. accepts any
signature. Neo Feed has it; nothing else does. Asking for the fixed-hash form
is the better ask: it is what the other six entries use, and it is the form
that actually verifies anything.

## The draft

> **Subject / PR title:** Add Whisper to the feed provider whitelist
>
> Hello,
>
> I maintain Whisper, an open-source RSS reader that can serve as a launcher
> feed provider. I would like to ask for its package to be added to
> `FeedBridge`'s whitelist so that Lawnchair users can select it without
> turning on "Ignore feed whitelist" in the debug menu.
>
> **Package:** `com.nyancatlabs.whisper`
> **Source:** https://github.com/defsix/whisper_feed
> **Licence:** GPL-3.0
> **Signature hash:** `0x________`
>
> Whisper is a fork of Neo Feed (`com.saulhdev.neofeed`), which is itself a
> fork of iTaysonLab's HomeFeeder (`ua.itaysonlab.homefeeder`) — both of which
> are already on the whitelist. It implements the same
> `com.android.launcher3.WINDOW_OVERLAY` service and the same
> `ua.itaysonlab.hfsdk` interface, so nothing on Lawnchair's side needs to
> change beyond the whitelist entry itself.
>
> It is a reader rather than a Discover replacement: user-chosen RSS sources,
> no recommendation service, no account required, and no data leaves the device
> unless the user turns on a backup themselves. It has been tested against
> Lawnchair on-device using the debug toggle, which is the only reason this
> request is about convenience rather than function.
>
> I am happy to open the pull request against `15-dev` myself, or to provide
> anything else you need — a signed release build to verify the hash against,
> for instance.
>
> Thank you for maintaining Lawnchair.
>
> — defsix

## Before sending

- [ ] Release signing key created, and the certificate hash computed
- [ ] A tagged release exists that the hash corresponds to
- [ ] The repository is renamed, if that is happening first — the PR should not
      contain a link that is about to move
