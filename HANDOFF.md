# UnifiedComms — HANDOFF (session restart)

Last updated: 2026-07-16
Authoritative branch: `master`  (single branch; `fix/add-account-email-sync` was DELETED — never restore it)
Current HEAD: `beb112b`  (fix(biometric): accept WEAK biometric or device credential for lock gate)
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

## Status: shipped & verified (git HEAD beb112b)
- **Release v1.0.1** published (signed, v2).
- **Add Account overhaul + autodiscover wire-through** (verified on emulator-5556):
  - Email-first flow with provider buttons; autodiscover fires on email IME-Done AND
    manual provider chip select; auto-expands Advanced on failure; Save surfaces errors.
  - `Autodiscover.discover()` resolves IMAP/SMTP AND CalDAV/CardDAV:
    (1) provider overrides (gmail/outlook/icloud/fastmail/zoho/yahoo/...);
    (2) raw DNS SRV `_caldavs._tcp`/`_carddavs._tcp` (UDP, type 33, custom parser);
    (3) `.well-known/caldav` + `.well-known/carddav` redirect follow.
  - `AutodiscoverTest` (live net) PASSED: Gmail (IMAP/SMTP + DAV), Outlook, bogus→null.
  - `DavAutodiscoverTest` PASSED: Fastmail (real SRV DNS path) + Gmail (override) DAV.
  - `AddAccountAutodiscoverUiTest` (Compose UI, live net) PASSED: types gmail address,
    picks Generic IMAP/SMTP, asserts "Server settings found automatically".
  - `EtherealEmailSyncTest` PASSED: live IMAP/SMTP send + sync + Room read-back.
- **Build green**: `:app:assembleDebug`, `:app:assembleAndroidTest`, `:app:testDebugUnitTest`.

## Current in-flight work (2026-07-16, NOT yet committed)
GOAL: prove the production DAV **write** round-trip (PUT vCard / PUT VTODO → re-sync
GET → DELETE) against a REAL (local) DAV server, not just the parser/email path.

WHAT EXISTS NOW (uncommitted, in working tree):
- `tools/dav_mock.py` — NEW, stdlib-only RFC-ish CardDAV + CalDAV mock server. The repo's
  tests referenced `carddav_mock.py` / `taskdav_mock.py` that were NEVER shipped — that is
  exactly why the DAV E2E was unproven. This replaces them. One process = one port;
  run it twice (8088 contacts, 8089 tasks) because the tests hardcode those ports.
- `tools/dav_diag.py` — host-side PROPFIND sequence checker (debug aid).
- `app/src/androidTest/.../DavMockConnectivityTest.kt` — proves emulator→mock via
  `adb reverse tcp:8088` (PROPFIND returns 207). PASSED.
- `app/src/androidTest/.../DavDiscoverDiagTest.kt` — raw PROPFIND walk logger (debug aid).
- `app/src/main/java/com/unifiedcomms/sync/CalDAVClient.kt` — has TEMPORARY `Log.d("DIAG ...")`
  lines added for debugging. MUST be stripped before commit.

ROOT-CAUSE BUGS FOUND + FIXED IN THE MOCK THIS SESSION:
1. HTTP keep-alive reuse hang: mock sent HTTP/1.1 without `Connection: close` → 2nd
   OkHttp request to the same socket timed out ("No address book found" / "No task list").
   Fixed: `Connection: close` + `ThreadingHTTPServer`.
2. Double-nested `<resourcetype>`: mock emitted `<resourcetype><resourcetype>...` so the
   client's resourcetype walk found no `addressbook` child. Fixed: `_rt()` returns child
   Elements appended directly under the prop's `<resourcetype>`.
3. Escaped component-set XML: `supported-calendar-component-set` value was a STRING with
   `<C:comp .../>` that got HTML-escaped → `componentSetOf()` found no `VTODO` → task list
   never matched. Fixed: `_comp()` returns a real Element (not an escaped string).

VERIFICATION REACHED THIS SESSION:
- **ContactSyncE2ETest now PASSES** against `tools/dav_mock.py` on 8088: testConnection →
  createContact(PUT vCard) → re-sync(GET) → deleteContact(DELETE) all succeed. This was
  the missing DAV write proof.
- TaskSyncE2ETest: the `_comp()` fix (bug #3 above) is written but the FINAL instrumented
  run against the 8089 mock was NOT executed (the `adb reverse` + run command was blocked
  by the user mid-session). Expected to pass once re-run; not yet confirmed.

TO FINISH THIS SESSION'S WORK (exact steps):
```bash
# host: start BOTH mocks (mock is single-port; tests hardcode 8088 + 8089)
cd /home/keith/host/UnifiedComms
python3 tools/dav_mock.py 8088 &   # background
python3 tools/dav_mock.py 8089 &   # background
# emulator: forward both ports
adb -s emulator-5556 reverse tcp:8088 tcp:8088
adb -s emulator-5556 reverse tcp:8089 tcp:8089
# run the DAV write round-trip E2E
adb -s emulator-5556 shell am instrument -w -r \
  -e class com.unifiedcomms.ContactSyncE2ETest,com.unifiedcomms.TaskSyncE2ETest \
  com.unifiedcomms.debug.test/androidx.test.runner.AndroidJUnitRunner
# expect: OK (2 tests)
```
THEN before committing:
- Strip every `Log.d(TAG, "DIAG ...` line from `CalDAVClient.kt` (restore to clean).
- Keep `tools/dav_mock.py` + `DavMockConnectivityTest` (they are the real DAV test harness).
- Delete `tools/dav_diag.py` and `DavDiscoverDiagTest.kt` (one-off debug aids) OR keep
  `DavDiscoverDiagTest` if you want a reusable diagnostic. Recommend deleting both.
- `git add -A && git commit -m "test(dav): CardDAV/CalDAV write round-trip vs local mock"` and push.

## Biometric lock bug — FIXED (2026-07-16)
- **Symptom**: user reported "Biometric not available on this device" on the V2170A
  (vivo, Android 15), which DOES have face + fingerprint enrolled.
- **Root cause**: `MainActivity.BiometricLockScreen` gated the Unlock button (and showed
  the "unavailable" text) on `canAuthenticate(BIOMETRIC_STRONG)` ONLY. OEM face/fingerprint
  on many devices enroll as WEAK (Class 2); `BIOMETRIC_STRONG` then returns non-success even
  though a real biometric/credential exists. The app's own `BiometricManagerImpl` already used
  the correct `BIOMETRIC_STRONG or DEVICE_CREDENTIAL` set — so the lock screen was internally
  inconsistent and wrong.
- **Fix** (`MainActivity.kt`): gate + prompt now use `BIOMETRIC_WEAK or DEVICE_CREDENTIAL`.
  Any enrolled biometric (weak or strong) or screen-lock credential now satisfies the lock.
- **Proof** (`BiometricProbeTest`, run on emulator-5556 with PIN, no strong biometric):
  `STRONG_ONLY → code=11 (no biometrics) ok=false` (OLD UI said unavailable);
  `WEAK_OR_CRED → code=0 ok=true` (NEW UI shows Unlock). `FIX_EFFECTIVE=true`.
  Probe was a throwaway; removed after run.
- **Note**: cannot screenshot-verify on the physical V2170A (can't press a finger remotely),
  but the mechanism is device-independent and proven on the identical code path.
- **ON-DEVICE STATUS (2026-07-16)**: app APK is now INSTALLED and RUNNING on the V2170A
  (verified: launches to "No accounts yet" + full bottom nav). The biometric fix is in that
  installed build.
- **V2170A INSTALL GOTCHA (critical for next session)**: `adb install` AND `adb shell pm install`
  HANG/abort with `INSTALL_FAILED_ABORTED: User rejected permissions` whenever the KEYGUARD is up.
  Working flow: (1) unlock screen (swipe up), (2) `adb shell pm install -r -t -i com.android.shell
  /data/local/tmp/app-debug.apk`, (3) vivo pops a "Security Care" dialog — tap checkbox
  "I have understood the risk" (~540,1974) then "Continue installing" (~540,2139 on 1080x2310).
  Keyguard re-locks aggressively on idle, so run the tap sequence promptly in one shell chain.
- **Store / Play Store request (RESOLVED, no change needed)**: `pm disable-user` on
  `com.bbk.appstore` / `com.vivo.appfilter` is REJECTED (no root; bootloader locked). Google Play
  APP (`com.android.vending`) is NOT present but GMS core (gms/gsf/webview) IS preinstalled.
  Sideloading vending on a non-GMS-certified vivo is unreliable AND was not needed — the install
  now works via the unlock + Security-Care tap flow above.

## Honest carry-over (NOT shipped, NOT faked)
- **Live OAuth round-trip** (real Google/Outlook token refresh) never run against a real
  provider — verified by compile/install only. Highest remaining confidence gap.
  Needs `GOOGLE_CLIENT_ID` / `MICROSOFT_CLIENT_ID` in BuildConfig.
- **Calendar/Task write round-trip** now has a working Contact proof; Task proof pending
  the re-run above. Both currently rest on the LOCAL mock, not a third-party provider
  (Fastmail/Nextcloud). That is a stronger proof than before, but not a real-provider one.
- **RECURRENCE-ID / EXDATE** server overrides not consumed (masters-only expansion).
- **Per-account avatar tint (P3)** wired but not pixel-verified (demo email list empty on
  email screen, so no avatar rows render to screenshot). Code path build+unit verified.

## Known environment quirks
- Emulator-5556 10-min screen-off kills USB: `adb shell settings put global
  stay_on_while_plugged_in 7`. Backup: `adb tcpip 5555` + `adb connect 10.0.2.2:5555`.
- `fix/add-account-email-sync` branch was toxic (deleted the Phase 1–20 work); never
  resurrect or merge it.
- DAV E2E tests require the host mock servers reachable via `adb reverse`. Without the
  mock running + both ports forwarded, Contact/Task E2E will fail with "No address book" /
  "No task list" — that is a MISSING TEST HARNESS, not an app bug.
- V2170A (vivo, Android 15, no root): every sideload install hits a "Security Care" confirm
  dialog + requires an unlocked keyguard. See biometric section for the exact tap flow. The
  wedged `com.android.packageinstaller` (high CPU) after a failed install is cleared by
  `am force-stop com.android.packageinstaller` + reboot.

## Restart checklist
1. `git status` + `git log -3` — confirm clean tree, HEAD = beb112b (or newer).
2. `./gradlew :app:assembleDebug :app:testDebugUnitTest` — must be GREEN.
3. If resuming DAV write-proof: start both mocks (8088/8089) + `adb reverse` both, then run
   ContactSyncE2ETest + TaskSyncE2ETest. Strip DIAG logs from CalDAVClient.kt first/after.
4. Pick a carry-over item, or a new feature. Read the actual file before editing.
5. Verify on emulator-5556 with a clean install + ScreenshotGalleryTest; vision-review.
6. Commit/push without asking once build is green + fix is verified (Keith workflow).
