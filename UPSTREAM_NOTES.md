# UPSTREAM_NOTES.md — Milestone 0: Launcher Feasibility Spike

Status: **Milestone 0 passed.** Upstream builds, the provider mechanism is
documented from source on both sides, and minus-one replacement is
**confirmed working on-device** — Lawnchair debug menu configured, APK
installed, Discover replacement functioning, as predicted in §3. Everything
below that is not explicitly marked "on-device" is verified by building the
real upstream source and reading the real Lawnchair source — not assumption.

The remaining §5 item is re-confirming the same behaviour after the
`applicationId` rename to `com.nyancatlabs.whisper` (first `io.zero76.whisper`,
changed again on 4 October 2026 for Nyancat Labs), which has since been made.

## 1. What was forked and built

- Upstream: `NeoApplications/Neo-Feed`, commit `42e023c2f454229055bca35bebdbdbd18ad5f0e3` (shallow clone, `main`).
- Built unmodified with: JDK 21 (Temurin), Gradle wrapper 9.7.1, AGP 9.3.2, Kotlin 2.4.10.
- Android SDK provisioned: `platforms;android-37.0`, `build-tools;36.0.0`, `platform-tools` (upstream CI uses JDK 17 on `ubuntu-latest`; JDK 21 also builds cleanly).
- Command: `./gradlew assembleDebug` → **BUILD SUCCESSFUL** (76 tasks, ~6 min cold).
- Output: `app/build/outputs/apk/debug/Neo_Feed_1.9.0_debug.apk` (24.8 MB).
- `aapt dump badging` confirms: `package: name='com.saulhdev.neofeed.dev' versionCode='1900' versionName='1.9.0'`, `minSdk=26`, `targetSdk=37`.

**Important nuance:** the **debug** build variant appends `applicationIdSuffix = ".dev"`, so it installs as `com.saulhdev.neofeed.dev`, not `com.saulhdev.neofeed`. This matters for provider discovery — see §3.

No APK was installed on a device (none available here). The APK above is ready to sideload for the on-device tests in §5.

## 2. The provider mechanism (exact classes/manifest)

`app/src/main/AndroidManifest.xml` declares:

```xml
<service
    android:name=".manager.service.OverlayService"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="com.android.launcher3.WINDOW_OVERLAY" />
        <action android:name="com.google.android.apps.gsa.sidekick.SidekickService" />
        <data android:scheme="app" />
    </intent-filter>
    <intent-filter>
        <action android:name="com.android.launcher3.WINDOW_OVERLAY" />
        <data android:scheme="app" />
    </intent-filter>
    <meta-data android:name="service.api.version" android:value="7" />
</service>
```

`OverlayService` (`com.saulhdev.feeder.manager.service.OverlayService`) is a plain
`Service` that delegates `onBind`/`onUnbind` to a
`ConfigurationOverlayController`, which implements
`com.google.android.libraries.gsa.d.a.OverlaysController` — an AIDL-based
reimplementation of Google's private launcher-overlay protocol (the same one
Google Now Launcher / Pixel Launcher's Discover integration uses). This AIDL
layer lives in the separate `:google-gsa` Gradle module and is credited in
the README to `DrawerOverlayService` by FabianTerhorst, itself a
reverse-engineering of `com.google.android.libraries.launcherclient` (the
matching client-side AIDL is literally still named that in Lawnchair's own
source — see §3).

Other manifest components: `MainActivity` (LAUNCHER category + deep-link
intent filters for `neofeed.saulhdev.com` and a `nf-mastodon://callback`
scheme used for Mastodon OAuth — irrelevant to us but present), and the
`NeoApp` Application class. No content providers, no other services besides
the standard WorkManager `SystemForegroundService`.

## 3. How Lawnchair actually decides who gets to be the feed (read from `LawnchairLauncher/lawnchair`, file `lawnchair/src/app/lawnchair/FeedBridge.kt`)

This is the load-bearing finding for the whole package-rename question, so
it's worth being precise. Lawnchair does **not** just scan for any app that
answers `WINDOW_OVERLAY` — it filters that list through a signature
whitelist:

```kotlin
private const val OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY"

fun initializeWhitelist(context: Context) {
    whitelist["com.saulhdev.neofeed"] = getSignatureHash(context, "com.saulhdev.neofeed")  // computed live from whatever's installed
    whitelist["ua.itaysonlab.homefeeder"] = 0x887456ed
    whitelist["launcher.libre.dev"] = 0x2e9dbab5
    whitelist[SmartspacerConstants.SMARTSPACER_PACKAGE_NAME] = 0x15c6e36f
    whitelist["amirz.aidlbridge"] = 0xb662cc2f
    whitelist["com.google.android.googlequicksearchbox"] = 0xe3ca78d8
    whitelist["com.google.android.apps.nexuslauncher"] = 0xb662cc2f
}

fun getAvailableProviders(context: Context) = context.packageManager
    .queryIntentServices(Intent(OVERLAY_ACTION).setData(Uri.parse("app://${context.packageName}")), PackageManager.GET_META_DATA)
    .map { it.serviceInfo.applicationInfo }
    .distinct()
    .filter { getInstance(context).CustomBridgeInfo(it.packageName).isSigned() }
```

`CustomBridgeInfo.isSigned()`:

```kotlin
override val signatureHash = whitelist[packageName]?.toInt() ?: -1
val ignoreWhitelist = prefs.ignoreFeedWhitelist.get()
override fun isSigned(): Boolean {
    ...
    return ignoreWhitelist || signatureHash != -1 && super.isSigned()
}
```

So a package not in `whitelist` gets `signatureHash == -1` and is rejected
**unless** the user-facing preference `pref_ignoreFeedWhitelist` is `true`.
That preference is a real toggle, not a hidden build flag, but it is not on
the About screen (there's no Android-style "tap the version number" easter
egg in this codebase — an earlier draft of this doc guessed that and was
wrong). The actual unlock, read from `AllAppsSearchInput.kt`, is a hidden
text command: **open the App Drawer, tap the search field, and type
`/lawnchairdebug`** — that flips `pref_enableDebugMenu`. Once enabled, a
hammer/build icon appears in the overflow bar of Lawnchair's main
Preferences screen (`PreferencesDashboard.kt`); tapping it opens the "Debug
menu" screen, which is where "Ignore feed whitelist" actually lives. No
root/custom build/Shizuku required either way. There's a matching
`pref_feedProvider` string preference (Home screen settings → Feed provider)
where the user picks which discovered package to use.

### Direct answer to Milestone 0's mandatory question ("does a package rename break detection?")

**Yes, by default** — `com.nyancatlabs.whisper` is not in the hardcoded whitelist, so
it will not appear in Lawnchair's Feed Provider picker and will not be
auto-selected.

**No, if the user does one manual one-time setup step** — enable
Lawnchair's "Ignore feed whitelist" debug toggle, then manually pick the
renamed package in Home screen → Feed provider. At that point
`CustomBridgeInfo.isSigned()` returns `true` unconditionally and the
launcher binds to it exactly as it does today for Neo Feed. This requires
**no root, no LSPosed, no Shizuku, and no change to Lawnchair itself** — it
matches the handoff doc's "no root" requirement.

Three longer-term options if we don't want to depend on a manual toggle:
1. Keep relying on the toggle indefinitely (fine for a personal/enthusiast install, which is the stated primary use case).
2. Upstream a PR to `LawnchairLauncher/lawnchair` adding `com.nyancatlabs.whisper`'s signature hash to the hardcoded `whitelist` map — puts us at the mercy of Lawnchair's release cadence and review, and only helps users of a Lawnchair build that includes it.
3. Ship under the literal package `com.saulhdev.neofeed` — rejected; that's Neo Feed's real identity, not ours, and would be actively misleading.

Recommendation: proceed with `com.nyancatlabs.whisper` + the toggle (option 1). It's
consistent with "primary launcher target: Lawnchair" for a single
enthusiast device, not a mass-market whitelist submission.

### Also relevant: the debug-suffix trap

Because upstream's debug build type sets `applicationIdSuffix = ".dev"`,
even *today's* unmodified Neo Feed, built as `assembleDebug`, installs as
`com.saulhdev.neofeed.dev` — which is **also not** in the hardcoded
whitelist (only the exact string `com.saulhdev.neofeed` is). So on-device
testing of the unmodified upstream app must either (a) build `assembleRelease`
with a signing config so the applicationId is the bare
`com.saulhdev.neofeed`, or (b) use the same "ignore whitelist" + manual
picker path described above even before any renaming happens. Worth keeping
in mind so a "why doesn't the debug APK show up as a feed provider" moment
isn't mistaken for a deeper problem.

## 3b. SYSTEM_ALERT_WINDOW is required — for opening articles, not for drawing the feed

Upstream declares `android.permission.SYSTEM_ALERT_WINDOW` and `MainPage.kt`
shows a blocking dialog until `Settings.canDrawOverlays()` returns true. It is
easy to assume this is for rendering the overlay and therefore removable. It
is not, and on-device behaviour confirms the split: **the feed renders fine
without it**; what breaks is tapping an article.

The reason is background activity launch (BAL) restrictions. The overlay is
not a foreground activity of ours — the window is attached to the *launcher's*
window token (`OverlayControllerCallback.setupOverlayController()` calls
`window.setWindowManager(null, layoutParams.token, …)`), so a `startActivity()`
from it is a background launch. Holding `SYSTEM_ALERT_WINDOW` is one of the
documented BAL exemptions. Upstream's own dialog says as much: *"without it
feed items would not open on click"*.

Notably there is no `TYPE_APPLICATION_OVERLAY` or `TYPE_SYSTEM_ALERT` anywhere
in the codebase, which is what makes the permission look vestigial on a first
read. It is the BAL exemption that is being bought, not a window type.

### Device setup gotcha

On a sideloaded build the "Display over other apps" toggle is **greyed out**,
because Android's *restricted settings* protection blocks sensitive
permissions for apps not installed from an app store. Unblock it with:

```text
Settings → Apps → Whisper → ⋮ → Allow restricted settings
```

then grant the permission normally. Or directly:

```bash
adb shell appops set com.nyancatlabs.whisper.dev SYSTEM_ALERT_WINDOW allow
```

This is a second manual setup step on top of the Lawnchair whitelist toggle in
§3. Both are one-time and neither needs root, but they are worth revisiting in
the polish milestone: if article opening can be routed through a BAL-exempt
path instead, the permission and this setup step both disappear, which would
better match the handoff's minimum-permission principle (§25). Treat that as
unproven until tested — the permission stays until a replacement is shown to
work.

## 4. Licensing (resolved)

> **Update:** the repository's LICENSE is now GPLv3, byte-identical to
> upstream's, so the merge that imported Neo Feed resolved it cleanly.
> Upstream copyright and the credits inherited through it are recorded in
> `ATTRIBUTION.md`. The original analysis follows.


Neo Feed is **GPLv3+**, copyright © 2025 Saul Henriquez & Antonios Hazim.
Its README credits iTaysonLab's `HomeFeeder` as the project it was forked
from, and `FabianTerhorst/DrawerOverlayService` as the base for the overlay
service.

**At the time of the spike this repository had an MIT `LICENSE` file.** MIT and GPLv3 are
not compatible in the direction we'd need (a GPLv3+ derivative work cannot
be redistributed under a plain MIT license). Before any Neo Feed–derived
source is actually copied into this repository, the repo's license needs to
become GPLv3+ (or GPLv3-compatible with clear per-file/module attribution),
and an attribution/`LICENSES` area needs to preserve the upstream copyright
notices. Both have since been done.

## 5. On-device test results

This environment has no Android emulator, no physical device, and no adb
target, so these were run by hand on the target Pixel 10 Pro:

- [x] Sideload the built debug APK and unlock the whitelist override (`/lawnchairdebug` in the App Drawer search bar → Debug menu → "Ignore feed whitelist") — **done, works as §3 predicted.**
- [x] With the toggle enabled and the app picked as feed provider: swipe-right-from-Home opens it — **confirmed, Discover replacement works.**
- [ ] Confirm the feed survives a Lawnchair restart.
- [ ] Force-stop the feed process and confirm Lawnchair recovers (re-binds) on next swipe.
- [ ] Re-confirm the above now that `applicationId` is `com.nyancatlabs.whisper` (the rename has been made; a fresh APK needs the same provider-picker check, since it installs as a *new* package alongside any existing Neo Feed install rather than upgrading it).

The two unchecked resilience checks are not blockers for Milestone 1 — they
test upstream's overlay lifecycle, which this fork does not modify — but they
are worth running once before any of it is built on heavily.

## 6. Conclusion / recommendation

The launcher-integration mechanism is fully understood from source on both
sides (Neo Feed's service + Lawnchair's discovery code), the unmodified
upstream builds cleanly against current tooling, the package-rename question
has a concrete, low-risk answer (manual toggle, no root), and minus-one
replacement is confirmed working on-device.

**Milestone 0 is passed and Milestone 1 is unblocked.** The hardest risk in
the project — whether a third-party app can own Lawnchair's minus-one page
without root — is now retired. The two residual items are the resilience
checks in §5 and the eventual decision on whether to rename the Kotlin
namespace (`com.saulhdev.feeder`) to match the new application ID; the
latter is best done after the UI replacement, since it would otherwise churn
files that are about to be rewritten.
