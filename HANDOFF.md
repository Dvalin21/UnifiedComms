# UnifiedComms — HANDOFF (session restart)

Last updated: 2026-07-18 (EXHAUSTIVE line-by-line bug review — full project, all 105 Kotlin src files + manifest + 45 build/resource files + 29 test files)
Authoritative branch: `master`  (single branch; `fix/add-account-email-sync` was DELETED — never restore it)
Current HEAD: `1ae34aeb`  (post exhaustive-review snapshot; assembleDebug GREEN, EXIT=0)
Latest release: **v1.0.2** (versionCode 2) — https://github.com/Dvalin21/UnifiedComms/releases/tag/v1.0.2

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

## Status: shipped & verified (git HEAD c33722f)
- **Release v1.0.2** published (signed, v2) — see "Full-project bug hunt (2026-07-17)" block below.
- **Release v1.0.1** published (signed, v2) — prior baseline.
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
- **Calendar write path (create/update/delete to server) — DONE (2026-07-16)**. See block below.
- **Calendar recurrence exceptions — DONE (2026-07-16, this session)**. See block below.

## Calendar write path — DONE (2026-07-16)
- **Root cause**: the app PARSED VEVENT but never SERIALIZED it. `CalendarScreen` wrote
  events local-only (`calendarRepository.insertEvent`); `CalendarSyncEngineImpl.createEvent/
  updateEvent/deleteEvent` touched Room but never PUT/DELETE to the CalDAV server. So new/
  edited/deleted events looked saved but never reached the server — a real data-not-backed-up
  hole (inverse of a silent lie). Tasks/Contacts already uploaded (proven in 64187b0); calendar
  was the missing half. Also: `syncCalendar(account, calendar)` 2-arg was a no-op stub reporting
  COMPLETED+success — fake-success path in the interface contract.
- **What shipped**:
  - `VEventSerializer` (new, mirrors `VTaskSerializer`): emits UID/DTSTAMP/DTSTART/DTEND/
    SUMMARY/DESCRIPTION/LOCATION/STATUS/RRULE. DTSTART/DTEND use `TZID=<canonical>` + local
    wall-clock (never a floating Z) — same DST-correctness fix as the parser. `hrefFor()` builds
    `<calendarId>/<uid>.ics`, matching the download-side `uidFromHref()` convention.
  - `CalendarSyncEngineImpl.createEvent/updateEvent/deleteEvent` now PUT/DELETE via the existing
    `CalDAVClient.putResource/deleteResource`, store the returned server ETag locally (so the
    next down-sync sees a matching etag and doesn't re-fetch/duplicate), and set `needsSync=false`.
    `deleteEvent` skips the server DELETE for local-only events and treats server 404 as success.
  - `syncCalendar(account, calendar)` 2-arg now delegates to `syncAccount` (honest) instead of
    lying about COMPLETED.
- **Verification**: `VEventSerializerTest` (4 JVM tests) — timed event round-trips UID/summary/
  escapes/TZID times; non-canonical TZID (`AMERICA/NEW_YORK`) normalizes to `America/New_York`
  with NO `Z` stamp; all-day emits `VALUE=DATE`; href matches download convention. Full suite:
  67 unit tests pass; `assembleDebug` + `assembleAndroidTest` green.
- **Not yet done (honest)**: emulator E2E PUT/GET/DELETE round-trip against `tools/dav_mock.py`
  for the calendar collection (the mock only serves contacts/tasks today). The serializer + engine
  paths are unit-verified and use the SAME `CalDAVClient` plumbing proven for tasks/contacts in
  64187b0, but a real-server calendar write has not been exercised end-to-end. Low risk; flagged
  for the next session if you want full provider-proof.

## Calendar write E2E — DONE (2026-07-16)
- **`CalendarSyncE2ETest`** added and PASSES on emulator-5556 against `dav_mock.py` (port 8088):
  `testConnection -> createEvent (PUT) -> syncAccount (download) -> updateEvent (PUT) ->
  deleteEvent (DELETE)`. Proves the full VEVENT write round-trip through `CalDAVClient`.
- **REAL BUG FOUND + FIXED by the E2E** (would have shipped as data loss): the down-sync cleanup
  sweep checked `event.calendarId !in masterServerHrefs` with raw string equality. `discover-
  Calendars` stores `cal.path` as a FULL URL (`http://host/calendars/default`), but `getETagList`
  returns the server's hrefs as-is (often RELATIVE, e.g. `/calendars/default/x.ics`). So a full
  URL never matched a relative href → **every downloaded event was deleted on the next sync**
  against any server returning relative hrefs (Baikal/Nextcloud do). Fixed by comparing the
  event's expected server href path-normalized (`pathOf()` strips scheme://host) against a
  path-normalized `masterServerPaths` set. Correct regardless of relative/absolute href form.
  This was a latent production data-loss bug, not a mock artifact — surfaced only because the E2E
  exercises a real down-sync + cleanup cycle.
- Verification: `CalendarSyncE2ETest OK (1 test)`; Contact/Task E2E still OK; 67 unit tests pass;
  `assembleDebug` + `assembleAndroidTest` green.
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

## Full-project bug hunt — DONE (2026-07-17)
Linus/Ponytail style entire-codebase bug hunt + emulator/uiemulator verification + release.

SCOPE COVERED (read the actual files; grep all 75 Kotlin for fake-success/TODO/stub):
- Security: `CryptoManager` (AES-GCM, key in AndroidKeyStore), `BiometricManager`,
  `OAuthTokenRefresher`, `Authenticator`, `OAuthCallbackActivity`→`AddAccountActivity`
  (intent.data read in onCreate — OAuth routing closed).
- Sync: `SyncManager` lifecycle, `BackgroundSyncWorker`/`BackgroundSyncScheduler`
  (WorkManager scheduled from `UnifiedCommsApplication.onCreate`), `EmailSyncEngineImpl`,
  `CalDAVClient` (per-response etag, URI.resolve), `CalendarSyncEngineImpl`,
  `ContactSyncEngineImpl`, `TaskSyncEngineImpl`.
- Parsers/serializers: `ICalParser`, `VCardParser`, `VEventSerializer`, `VTaskSerializer`,
  `VCardSerializer`.
- Data: `UnifiedCommsDatabase`, `Migrations` (MIGRATION_1_1 is a no-op self-migration — fine),
  `Converters` (AuthConfig decrypt on every repo insert/update — confirmed).
- UI: `MainActivity`, `MainViewModel`, `UnifiedInboxScreen`, `EmailScreen`,
  `AddAccountScreen`, `SettingsScreen`, `CalendarScreen`, `ReminderSystem`.
- Misc: `Autodiscover` (corrected URL strategy), `RuntimePermissionGate` (line 41 `return true`
  is the granted branch — correct, NOT a stub), all 9 manifest-declared components resolve.

ONLY ONE REAL BUG FOUND + FIXED:
- `ReminderSystem.openEventDetail()` relaunched `FullScreenReminderActivity` with a
  `navigate_to` extra that Activity never reads → the reminder "View" button just re-shows
  the reminder (dead button / broken window). Re-routed to `MainActivity` (owns the
  `event_detail` route) via the same `navigate_to` extra. Commit `3069f95`.

NON-REPRODUCING FLAKE (deliberately NOT fixed — no speculative code):
- `ContactSyncE2ETest` failed once inside a 3-in-1 combined run, then passed 3/3 standalone
  AND 3/3 on re-running the same 3-in-1 combo. Contact sync logic is correct; it was an
  environment/timing one-off. Characterized empirically before deciding.

VERIFICATION (all real, on emulator-5556 + uiemulator):
- Build GREEN: `:app:assembleDebug` + `:app:assembleAndroidTest` + `:app:testDebugUnitTest`
  (67 unit tests pass).
- UI emulator: `ScreenshotGalleryTest` (uc_01..uc_13, light+dark) PASS (2 tests). Fresh
  shots in `docs/screenshots/fresh/`; vision-reviewed — inbox/calendar/tasks/add-account/
  settings (both themes) render correctly, no crashes/overflow, status-bar icons visible
  in both themes. (Settings screen scrolls — lower groups are below the fold in a still;
  dark settings shot confirms the top region is correct.)
- DAV E2E (Contact/Task/Calendar write round-trips vs `dav_mock.py` 8088/8089): PASS,
  2x with unique per-run keys.
- Release APK v1.0.2: built (R8 shrink + lintVital clean), `apksigner verify` → v2=true,
  v1/v3=false (correct). Installed clean, launched crash-free (vision-confirmed render;
  shows "No accounts yet" because it's a separate package from the debug-seeded data — expected).

BUILD/RELEASE NOTES (carried forward):
- Debug and release share the authenticator authority `com.unifiedcomms.authenticator`,
  so they CANNOT co-install on one device. To UI-verify the release: uninstall debug +
  test APKs, install release, launch/screencap, then reinstall debug + test APKs. The
  release APK is a separate installable artifact (not left on the emulator).
- `ScreenshotGalleryTest` lives in the DEBUG test APK (`com.unifiedcomms.debug.test`,
  target `com.unifiedcomms.debug`); it cannot run against the release package.

DELIVERED: commits `3069f95` (reminder fix + doc-rot refresh + fresh screenshots) and
`c33722f` (release smoke shot) pushed to `master`; tag `v1.0.2` pushed to origin.
Emulator left in working state (debug + test APKs reinstalled, gallery green).

## EXHAUSTIVE BUG REVIEW — FIX PLAN (2026-07-18)

Full line-by-line review of ALL 105 Kotlin source files (~16.7k LOC) + AndroidManifest +
45 build/resource files + 29 test files. Done via 6 partitioned review subagents (every line
of every assigned file) + primary-source verification of every SEV-1/SEV-2 and every subagent
LIVE claim by the orchestrator. Build baseline: `:app:assembleDebug` GREEN (EXIT=0).

Verification legend: [V] = orchestrator read source and confirmed. [A] = subagent, in-repo,
cross-checked. [X] = subagent claim DISPROVED at source (do NOT act on these).

### TIER 1 — LIVE SEV-1 (ship-blockers)
1. [V] OAuth refresh returns PLAINTEXT → engine re-decrypt throws. `OAuthTokenRefresher.kt:74`
   returns `updatedAccount` (plaintext AuthConfig built at 67-71) after `accountRepo.update`
   re-encrypts to DB. `SyncManager.performFullSync:91` feeds that `fresh` into email/calendar/
   task/contact engines (99/112/120/128), each calls `decryptAuthConfig` → real OAuth token
   (>12 bytes) GCM-decrypts non-ciphertext → throws → OAuth accounts fail every sync after
   token refresh. FIX: `return accountRepo.getById(account.id) ?: updatedAccount` at line 74.
2. [V] Four widget receivers are DEAD manifest components. `AndroidManifest.xml:133/146/159/172`
   declare EmailWidgetReceiver/CalendarWidgetReceiver/TasksWidgetReceiver/UnifiedWidgetReceiver,
   but only `*.kt.bak` exist (Gradle won't compile `.bak`). ClassNotFoundException on widget
   re-bind/reboot. FIX: delete the 4 `<receiver>` blocks + 8 `*.kt.bak` files, OR restore real
   sources + AppWidgetProviderInfo XML.
3. [V] ConversationDao JSON-`IN` → empty messaging inbox. `MessageDao.kt:123/126/129/132/138/141`
   `WHERE :userId IN (participantIds)` — `participantIds` is a JSON-array STRING column; SQLite
   `IN` compares the whole blob → never matches real userId. `MessagesScreen:83` calls
   `getAllConversationsForUser(getCurrentUserId())` → empty. (Demo coincidentally works only
   because both sides are literal `"current_user"`.) FIX: normalize participants (junction
   table) or query `participantIds LIKE '%"userId"%'`.
4. [V] `getCurrentUserId()` returns constant `"current_user"`. `Message.kt:273-275`. LIVE callers:
   MessagesScreen (82/510/532), MainViewModel:158, DemoDataSeeder:127. `senderId == "current_user"`
   never matches real ids → outgoing mail shown as incoming; read-receipt filter wrong. FIX: wire
   to real authenticated user id (account id / email), not a TODO constant.
5. [V] Biometric lock fully bypassable. `MainActivity.kt:96` "Continue without biometrics"
   TextButton unconditionally calls `onUnlocked()`. Anyone with physical access gets in. The
   WEAK-or-CREDENTIAL gate (60-70) is cosmetic. FIX: remove the unconditional bypass; gate behind
   real fallback or require device credential.
6. [V] InviteActionReceiver corrupts RSVPs + dead + unsafe. `InviteActionReceiver.kt:42-53` updates
   ALL `NEEDS_ACTION` attendees (not current user) and syncs upstream (comment admits "just update
   all"); receiver NOT declared in manifest → action buttons dead; no `goAsync()` → work lost on
   process death. `showCalendarInviteNotification` has no callers today (LATENT-but-poisoned).
   FIX: declare receiver `exported=false`, match attendee by account email, wrap in `goAsync()`.

### TIER 2 — LIVE SEV-2
7. [V] ReminderSystem exact-alarm unguarded + cancel lost on reboot. `ReminderSystem.kt:172/242`
   `setExactAndAllowWhileIdle` with no `canScheduleExactAlarms()` guard → SecurityException on 12+
   with alarms revoked. `scheduledKeys` in-memory (207/254) → stale reminders for deleted events
   survive reboot (key `${eventId}_${minutesBefore}` is deterministic, so cancel can regenerate).
   `ReminderAlarmReceiver:46` also launches Activity from broadcast (BAL-restricted 10+). FIX: guard
   exact-alarm; regenerate PendingIntent deterministically at cancel; rely on full-screen-intent
   notification only.
8. [V] NotificationHelper.notifySafe silent drop. `NotificationHelper.kt:31-41` — when
   POST_NOTIFICATIONS denied (13+), every notification vanishes, no log/re-prompt. FIX: `Log.w` +
   surface in-app re-prompt via RuntimePermissionGate.
9. [V] CryptoManager.decryptField downgrade. `CryptoManager.kt:62` — `raw.size < 12` returned as
   trusted plaintext. Truncated/corrupted stored creds accepted. (Round-trip encrypt-on-write/
   decrypt-on-read is correct elsewhere.) FIX: explicit draft flag, else throw on malformed ciphertext.
10. [V] Email row tap is a no-op. `EmailScreen.kt:142` `clickable { /* open */ }`. Core inbox
    interaction dead. FIX: add onEmailClick callback → detail route.
11. [V] moveToFolder/deleteMessages Message-ID-only match. `EmailSyncEngineImpl.kt:479/508` match
    UI-passed `uids` against `Message-ID` header, but Message-ID-less mail has synthetic uid
    `"$folder#$start+msgNum"` (line 144) → `msgs` empty → `SyncResult.success(0)`. Move/delete
    silently no-ops. FIX: match by stored uid (persist lookup) or IMAP UID.
12. [V] `String.first()` crash on blank names. `EmailScreen:148` `localMessage.from.first()`,
    `MessagesScreen:303` `conversation.name.first()`, `UnifiedInboxScreen:263` `account.name.first()`.
    Blank sender/name → IndexOutOfBoundsException → crashes the list row. (AccountSettingsScreen:114
    already uses the safe form.) FIX: `firstOrNull()?.uppercase() ?: "?"` x3.

### TIER 3 — LIVE SEV-3 / correctness
13. [V] RecurrenceExpander MONTHLY multi-BYDAY dropped. `RecurrenceExpander.kt:137`
    `rule.byDay.first()` — `RRULE:FREQ=MONTHLY;BYDAY=MO,WE,FR` collapses to Monday. FIX: iterate `byDay`.
14. [V] Tasks tab "create" no-op. `UnifiedInboxScreen.kt:179` `onCreateTask = { }` empty lambda. FIX: wire to CreateTaskScreen.
15. [A] Dead Attendees TextField. `CalendarScreen.kt:477` `TextField(value="", onValueChange={}, ...)`.
    Event attendees can't be set/edited. FIX: wire to state + parse into CalendarEvent.attendees.
16. [A] Dead "More" menu. `MessagesScreen.kt:202` `MoreVert` → `"menu"`; dialogMessage when-block
    (163-171) has no `"menu"` branch → dead tap. FIX: add branch or remove button.
17. [A] Dead mock helpers. MessagesScreen `getMockConversations:456` / `getMockMessages:458`,
    CalendarScreen `getMockEventsForDate:295`, TasksScreen `getMockTasks:303` — no callers. DELETE.
18. [V] DemoDataSeeder direct DAO insert bypasses encryption. `DemoDataSeeder.kt:75`
    `db.accountDao().insert(account)` (no crypto). Low impact: seeder is a no-op on fresh install +
    demo-only. FIX: route through accountRepo.

### TIER 4 — DEAD (delete)
19. [V] `BiometricManager.kt` — entire file dead (zero instantiations; MainActivity has the live gate). DELETE.
20. [V] 8 × `*.kt.bak` widget files — dead weight (see #2).

### TIER 5 — LATENT / forward-risk (fix before wiring)
21. [A] No FileProvider/provider_paths.xml. Will `FileUriExposedException` the moment attachment/share
    is wired. ADD FileProvider + provider_paths.xml.
22. [A] `ic_provider_fastmail` ("F") / `ic_provider_mailcow` (squiggle) not brand-identifiable —
    violates the spec's identifiable-tile requirement. REPLACE.
23. [A] `build.gradle.kts:216` duplicate `work-runtime-ktx` (2.9.1 + 2.9.0). DELETE duplicate.
24. [A] `build.gradle.kts:212-213` alpha `security-crypto`/`biometric`. PROMOTE to stable.
25. [A] `activity_fullscreen_reminder.xml` uses `androidx.cardview.CardView` (transitive only).
    SWITCH to MaterialCardView or add explicit dep.
26. [A] Both SCHEDULE_EXACT_ALARM + USE_EXACT_ALARM (Manifest:13-14) — Play policy risk.
    DROP SCHEDULE_EXACT_ALARM unless justified.
27. [A] Hardcoded strings in `activity_fullscreen_reminder.xml` (i18n). USE @string/.
28. [A] Flows built inline in `by` delegate (CalendarScreen:93, TasksScreen:76, MainActivity create/
    edit routes) → re-subscription churn. HOIST with remember.
29. [V] Account type string mismatch: AddAccountActivity:469 `com.unifiedcomms.account` vs provider
    authority `com.unifiedcomms.authenticator` — latent while AccountManager path is dead; reconcile
    if ever wired.

### TEST SUITE — FALSE CONFIDENCE (verified)
30. [V] `IsOkTest.kt:9` `assertEquals(1,1)` — pure noise. DELETE.
31. [V] `EmailSyncEngineTest.kt:45/72` `assertTrue(true)`; result only println'd; crypto mocked
    identity + getAllActive empty → LIVE class, ZERO coverage. This test CANNOT catch #1 or #11.
    REWRITE with real assertions + real crypto.
32. [A] `ScreenshotGalleryTest` — zero assertions (screenshot harness mislabeled as UI test).
    ADD semantic assertions or demote to harness.
33. [A] `BackgroundSyncWorkerTest.kt:51` — green only because clean emulator has no accounts. SEED an account.
34. [A] `EtherealEmailSyncTest.kt:62-103` — no cleanup (cross-run account/row pollution). ADD finally-delete.
35. [A] `AccountRepositoryImplTest.kt:31-52` — sync-flag filter has no negative case. ADD negative test.
36. [V] `OAuthTokenRefresherTest` uses FakeCrypto + 13-char token → masks #1 entirely. Must use
    real-length token + real CryptoManager round-trip.
KEEP (good): RecurrenceExpanderTest, ICalParserRecurrenceExceptionTest, VEventSerializerTest,
VTaskSerializerTest, VCardTest, AutodiscoverDavTest, Xoauth2FormatTest, DateTimeConverterTest,
ConvertersTest, EmailRepositoryImplTest, ContactRepositoryImplTest.mergeContacts, CalDAV/CardDAV
E2E (correct per-run-unique keys + cleanup).

### SUBAGENT CLAIMS DISPROVED (do NOT act on these)
[X] "Secrets stored plaintext on disk" (data agent) → FALSE: encrypt applied at
    AccountRepositoryImpl.insert/update:16/18.
[X] "Room won't compile (LongArray/ByteArray)" (data agent) → FALSE: build is green.
[X] "Authenticator broken → accounts can't be created" (manifest agent) → OVERSTATED: app uses Room
    + WorkManager directly; no AccountManager.addAccount call; provider is dead scaffolding.
[X] "Dark mode broken/unreadable" (manifest agent) → FALSE: Theme.kt applies real DarkColorScheme;
    MainActivity feeds effectiveDark. Agent inspected only native styles.xml.
[X] "Theme toggle non-reactive" (UI agent) → FALSE: putThemeMode→putString→PreferencesManager:50
    `_themeMode.update`. Flow emits.
[X] "ContactsScreen:245 Attendees dead field" (UI agent) → LINE MISMATCH: 245 is the wired Phones
    field; real dead Attendees field is CalendarScreen:477.

### EXECUTION PLAN (per fix; isolated branch, green build + proof after each)
- Batch A (SEV-1): #1 OAuth return, #2 widget receivers, #3 ConversationDao, #4 getCurrentUserId,
  #5 biometric bypass, #6 invite receiver.
- Batch B (SEV-2): #7–#12.
- Batch C (cleanup/LATENT + tests): #13–#29 + test fixes #30–#36.
STATUS: review delivered 2026-07-18; execution pending user go-ahead.

## BRANCH A — fix/batch-a-sev1 (2026-07-18, EXECUTED)
Isolated off `1ae34ae`. Build GREEN (`:app:assembleDebug` + `:app:testDebugUnitTest`, EXIT=0).
Claims re-verified against source; two HANDOFF claims did NOT hold and were NOT churned:
- #1 [FIXED] OAuthTokenRefresher.kt:74 — returns `accountRepo.getById(account.id)` (re-encrypted
  AuthConfig) instead of the in-memory plaintext `updatedAccount`. Engines decryptAuthConfig on it;
  old code handed them plaintext → GCM decrypt threw on every post-refresh sync.
- #2 [DISPROVED — NOT churned] The four widget `<receiver>` blocks are INSIDE an `<!-- -->` comment
  in AndroidManifest.xml (lines 130–183), not active components. ClassNotFoundException-on-rebind
  claim is false. 8 `*.kt.bak` files are also dead but harmless (Gradle ignores `.bak`).
- #3 [FIXED] ConversationDao 4 queries — `:userId IN (participantIds)` compared a JSON-array STRING
  blob and never matched. Replaced with `participantIds LIKE '%' || :userId || '%'` (stored format
  is `["id1","id2"]`). This was the real empty-messaging-inbox root cause.
- #4 [DELIBERATELY NOT CHURNED] `getCurrentUserId()` returns constant "current_user". The messaging
  layer has NO account-bound identity (peer model + demo). Wiring it to an email account id would
  corrupt `Message.isOutgoing()` and demo data and there is no plumbing for it. Empty inbox was
  caused by #3 (fixed). If a real identity is ever introduced, wire it then. Left as a documented TODO.
- #5 [FIXED] MainActivity.kt — removed the unconditional "Continue without biometrics" TextButton
  that called `onUnlocked()` (anyone with physical access got in). If lock enabled but no usable
  authenticator, the dialog now explains how to disable it instead of silently opening.
- #6 [FIXED] InviteActionReceiver — (a) declared in manifest `exported=false` (was missing → dead
  buttons); (b) added `goAsync()` so work survives process death; (c) RSVP now matches the CURRENT
  user by `account.email` (lowercase) instead of stamping every NEEDS_ACTION attendee.
NOT pushed (Keith decides when to push). Branch is local only.

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
1. `git status` + `git log -3` — confirm clean tree, HEAD = 1ae34aeb (or newer).
2. `./gradlew :app:assembleDebug :app:testDebugUnitTest` — must be GREEN.
3. If resuming DAV write-proof: start both mocks (8088/8089) + `adb reverse` both, then run
   ContactSyncE2ETest + TaskSyncE2ETest. Strip DIAG logs from CalDAVClient.kt first/after.
4. Pick a carry-over item, or a new feature. Read the actual file before editing.
5. Verify on emulator-5556 with a clean install + ScreenshotGalleryTest; vision-review.
6. Commit/push without asking once build is green + fix is verified (Keith workflow).
