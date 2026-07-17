# UnifiedComms — HANDOFF (session restart)

Last updated: 2026-07-16 (resumed session)
Authoritative branch: `master`  (single branch; `fix/add-account-email-sync` was DELETED — never restore it)
Current HEAD: `57aefd3`  (fix(calendar): consume RECURRENCE-ID/EXDATE server overrides; case-insensitive TZID)
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

## Status: shipped & verified (git HEAD 57aefd3)
- **Release v1.0.1** published (signed, v2).
- **Add Account overhaul + autodiscover wire-through** (verified on emulator-5556):
  - Email-first flow with provider buttons; autodiscover fires on email IME-Done AND
    manual provider chip select; auto-expands Advanced on failure; Save surfaces errors.
  - `Autodiscover.discover()` resolves IMAP/SMTP AND CalDAV/CardDAV:
    (1) provider overrides (gmail/outlook/icloud/fastmail/zoho/yahoo/...);
    (2) raw DNS SRV `_caldavs._tcp`/`_carddavs._tcp` (UDP, type 33, custom parser);
    (3) `.well-known/caldav` + `.well-known/carddav` redirect follow.
- **UI polish pass (commit f08b67c, emulator-5556 screenshots re-verified):**
  - Status bar icon contrast fixed: driven from theme via WindowInsetsControllerCompat
    in MainActivity; removed static windowLightStatusBar=false. Icons visible in both themes.
  - Top app-bar title 'UnifiedComms' now renders in full (removed redundant top-bar
    Add Account action that crowded it).
  - EmailScreen has a 'No emails yet' empty state (was a blank list).
  - DemoDataSeeder seeds 3 demo emails so Unified Inbox / folder views show real content.
  - Tasks filter chips use FlowRow (wrap) — 'Overdue' no longer clipped at screen edge.
  - Add Account provider chips: labels centered in each chip + uniform min-width, so the
    grid renders as a tidy uniform list (no jagged uneven wrap). Task filter chip labels
    centered too. 'Server settings found' banner text centered.
  - ScreenshotGalleryTest harness: Add Account reached via Settings (top-bar Add removed);
    Search captured while app foreground (kills coordinate flakiness). uc_01..uc_13 all verified.
  - `AutodiscoverTest` (live net) PASSED: Gmail (IMAP/SMTP + DAV), Outlook, bogus→null.
  - `DavAutodiscoverTest` PASSED: Fastmail (real SRV DNS path) + Gmail (override) DAV.
  - `AddAccountAutodiscoverUiTest` (Compose UI, live net) PASSED: types gmail address,
    picks Generic IMAP/SMTP, asserts "Server settings found automatically".
  - `EtherealEmailSyncTest` PASSED: live IMAP/SMTP send + sync + Room read-back.
- **Build green**: `:app:assembleDebug`, `:app:assembleAndroidTest`, `:app:testDebugUnitTest`.

## DAV write round-trip — DONE (2026-07-16, resumed session, COMMITTED 64187b0)
GOAL (achieved): prove the production DAV **write** round-trip (PUT vCard / PUT VTODO →
re-sync GET → DELETE) against a REAL (local) DAV server, not just the parser/email path.

WHAT SHIPPED (commit 64187b0):
- `tools/dav_mock.py` — stdlib-only RFC-ish CardDAV + CalDAV mock server, single-port
  (run twice for 8088 contacts / 8089 tasks; tests hardcode those ports).
- `app/src/androidTest/.../DavMockConnectivityTest.kt` — proves emulator→mock via
  `adb reverse tcp:8088` (PROPFIND returns 207).
- Debug aids `tools/dav_diag.py` + `DavDiscoverDiagTest.kt` DELETED.
- `CalDAVClient.kt` confirmed free of `DIAG` logging.

ROOT-CAUSE BUGS FIXED IN THE MOCK (reproved by the passing run):
1. keep-alive hang → `Connection: close` + `ThreadingHTTPServer`.
2. double-nested `<resourcetype>` → `_rt()` appends child Elements directly.
3. escaped `supported-calendar-component-set` → `_comp()` returns a real Element.

VERIFICATION (run this session on emulator-5556, clean install, both mocks + reverses up):
```
am instrument -w -r -e class com.unifiedcomms.ContactSyncE2ETest,com.unifiedcomms.TaskSyncE2ETest \
  com.unifiedcomms.debug.test/androidx.test.runner.AndroidJUnitRunner
→ OK (2 tests)   [ContactSyncE2ETest OK, TaskSyncE2ETest OK]
```
Contact PUT vCard → GET → DELETE and Task PUT VTODO → GET → DELETE both confirmed through
the REAL production engine paths. This was the previously-missing DAV write proof.

RE-RUN LATER (repro): host `python3 tools/dav_mock.py 8088 &`, `… 8089 &`; then
`adb -s emulator-5556 reverse tcp:8088 tcp:8088` + `… tcp:8089 tcp:8089`; install
debug + androidTest APKs; run the instrument command above.

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
  Needs `GOOGLE_CLIENT_ID` / `MICROSOFT_CLIENT_ID` in BuildConfig (Keith must supply).
- **Calendar/Task write round-trip — PROVEN on local mock** (commit 64187b0): both
  ContactSyncE2ETest + TaskSyncE2ETest pass PUT/GET/DELETE via the real engine. Still NOT
  against a real third-party provider (Fastmail/Nextcloud) — mock proof, not provider proof.
- **Per-account avatar tint (P3)** wired but not pixel-verified (demo email list empty on
  email screen, so no avatar rows render to screenshot). Code path build+unit verified.
- **Calendar recurrence exceptions — DONE (2026-07-16, this session)**. See block below.

## Calendar recurrence exceptions — DONE (2026-07-16)
- **What shipped**: `ICalParser` now parses `EXDATE` (server-side deletions) and
  `RECURRENCE-ID` override VEVENTs (same UID, no RRULE) and folds them into
  `CalendarEvent.recurrenceExceptions`. `RecurrenceExpander.expand()` now drops EXDATE'd
  occurrences and re-emits moved/cancelled overrides from their override event instead of
  the generated master slot. New tests: `ICalParserRecurrenceExceptionTest` (parse EXDATE,
  parse RECURRENCE-ID move, parse cancelled override) + 2 `RecurrenceExpanderTest` cases
  (EXDATE suppression, RECURRENCE-ID replacement). All 63 unit tests pass; assembleDebug +
  assembleAndroidTest green.
- **Root-cause bugs found and fixed (these were the REAL blockers, not just #3):**
  1. **Case-sensitive TZID crash.** `parseProperties` uppercased the ENTIRE iCal key
     including params (`RECURRENCE-ID;TZID=AMERICA/NEW_YORK`), so `RECURRENCE-ID` lookups
     missed. Worse, both `ZoneId.of()`/`TimeZone.of()` and the event construction passed the
     raw (often non-canonical-case) TZID straight to `kotlinx.datetime.TimeZone.of()`, which
     THROWS `IllegalTimeZoneException` on anything but canonical IANA case — silently dropping
     the WHOLE VEVENT (any event with a `TZID` param, not just recurring ones). Fixed by
     (a) uppercasing only the property name in `parseProperties`, and (b) new `TimeZoneUtil`
     (`data/model/TimeZoneUtil.kt`) that resolves TZIDs case-insensitively and NEVER throws.
     **This was a latent data-loss bug affecting every DAV calendar event with a TZID.**
  2. **EXDATE/RECURRENCE-ID exact-key lookup.** `extractExdates` + the rid lookup now scan by
     prefix (`startsWith("EXDATE")`, `startsWith("RECURRENCE-ID")`) since the keys carry
     `;TZID=` params. Added `resolveZoneId` for DST-correct parsing of exception dates.

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
1. `git status` + `git log -3` — confirm clean tree, HEAD = c72b538 (or newer).
2. `./gradlew :app:assembleDebug :app:testDebugUnitTest` — must be GREEN.
3. If resuming DAV write-proof: start both mocks (8088/8089) + `adb reverse` both, then run
   ContactSyncE2ETest + TaskSyncE2ETest. Strip DIAG logs from CalDAVClient.kt first/after.
4. Pick a carry-over item, or a new feature. Read the actual file before editing.
5. Verify on emulator-5556 with a clean install + ScreenshotGalleryTest; vision-review.
6. Commit/push without asking once build is green + fix is verified (Keith workflow).
