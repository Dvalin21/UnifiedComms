# UnifiedComms — HANDOFF (session restart)

Last updated: 2026-07-15
Authoritative branch: `master`  (single branch; `fix/add-account-email-sync` was DELETED — never restore it)
Current HEAD: `229dd5d`  (README privacy section + Komi badges)
Latest release: **v1.0.1** (versionCode 2) — https://github.com/Dvalin21/UnifiedComms/releases/tag/v1.0.1

> WARNING: This file rots. Before trusting any claim here, run `git log -5` and
> `git status`. Git is the source of truth, not this doc.

## What the app is
Unified communications client for Android (email + calendar + tasks + encrypted
messages + contacts) across multiple accounts. Kotlin + Jetpack Compose (Material 3).
Local-first, zero telemetry, MIT-licensed. minSdk 31 / targetSdk & compileSdk 35.

## Build & verify (the only commands you need)
```bash
export ANDROID_HOME=/home/keith/Android/Sdk
./gradlew :app:assembleDebug :app:assembleAndroidTest :app:testDebugUnitTest   # unit + build
./gradlew :app:assembleRelease                                                  # signed release APK
```
- Release signing: reads `local.properties` (KEYSTORE_PATH / KEYSTORE_PASSWORD /
  KEY_ALIAS / KEY_PASSWORD) or env vars. Keystore: `/home/keith/host/UnifiedComms/release.jks`.
  **Never commit the keystore or local.properties** (both gitignored).
- Verify a release APK: `$ANDROID_HOME/build-tools/34.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk`
  Expect: `Verified using v2 scheme: true`, `Number of signers: 1`. Do NOT ship if it
  reports `app-release-unsigned.apk`.
- Emulator test target: **emulator-5556** (AVD `testAVD2`). Clean install ALWAYS
  (`adb -s emulator-5556 uninstall com.unifiedcomms.debug` then `install -t`), never
  `adb install -r` — stale Compose renders lie.
- UI proof: `ScreenshotGalleryTest` (com.unifiedcomms) writes uc_01..uc_13 to /sdcard,
  pulls into docs/screenshots/, vision-reviewed. Gallery PASS (2 tests) = minimum gate.

## Status: shipped & verified
- **Release v1.0.1** published (signed, v2). Cut as a distinct versionCode (2) so store
  crawlers / updaters pick up the Add Account "Close" fix cleanly.
- **UI overhaul ("awesome not square", Phases 21–23)** done + verified on emulator:
  - P1: rounded layered surfaces (24dp + outlineVariant stroke) on all main screens;
    floating pill bottom-nav with tinted active indicator.
  - P2: illustrated empty states (Tasks, Search); calendar month view shows one colored
    dot per event (kills "T..." truncation); dropped the hand-rolled TypographyDefault
    block in favor of canonical M3 type scale (pure deletion).
  - P3: per-account email avatar tint (Account.uiConfig.color by accountId).
  - Fix: inbox TopAppBar title no longer wraps to two lines (maxLines=1).
  - Fix: Add Account top-bar "Close" button was clipped ("Clos") inside an IconButton;
    switched to TextButton — full "Close" now renders.
- **Komi Store**: auto-indexes GitHub Releases (no submit form). v1.0.1 (versionCode 2)
  will be picked up on next crawl. App meets the bar: MIT, no GMS/firebase/billing deps,
  cleartext disabled, signed APK on a public release. README has a "Get it on Komi Store"
  badge + Privacy & Security section (claims grep-verified: no analytics SDKs in deps).
- **Fix-campaign history (Phases 1–16, 2026-07-06 → 07-08)**: email/calendar/task/contact
  sync engines, OAuth refresh, recurrence expansion, background WorkManager sync, manifest
  perm pruning, dead-code deletion. All real fixes, E2E-verified on emulator-5560.
  Source of truth = `git log`; do NOT reconstruct from this paragraph.

## Honest carry-over (NOT shipped, NOT faked)
- **Live OAuth round-trip** (real Google/Outlook token refresh) never run against a real
  provider — verified by compile/install only. Highest remaining confidence gap.
- **Live CalDAV/CardDAV** only proven against a mock server; not against a real provider
  (Nextcloud/Fastmail). Production DAV write round-trip unconfirmed.
- **Calendar/Task instrumented E2E** — only Email + Contact have it.
- **RECURRENCE-ID / EXDATE** server overrides not consumed (masters-only expansion).
- **Per-account avatar tint (P3)** wired but not pixel-verified: demo email list is empty
  on the email screen, so no avatar rows render to screenshot. Code path build+unit verified.

## Known environment quirks
- Emulator-5556 10-min screen-off kills USB: `adb shell settings put global
  stay_on_while_plugged_in 7`. Backup: `adb tcpip 5555` + `adb connect 10.0.2.2:5555`.
- Physical test phone (vivo V2170A, Android 15, bootloader LOCKED, no root): NOT used this
  phase. Don't bother with Magisk here.
- `fix/add-account-email-sync` branch was toxic (deleted the Phase 1–20 work); never
  resurrect or merge it.

## Restart checklist
1. `git status` + `git log -3` — confirm clean tree, HEAD = 229dd5d.
2. `./gradlew :app:assembleDebug :app:testDebugUnitTest` — must be GREEN.
3. Pick a carry-over item above, or a new feature. Read the actual file before editing.
4. Verify on emulator-5556 with a clean install + ScreenshotGalleryTest; vision-review.
5. Commit/push without asking once build is green + fix is verified (Keith workflow).
