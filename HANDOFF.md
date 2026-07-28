# UnifiedComms — HANDOFF (2026-07-26, updated)

Project: ~/host/UnifiedComms (Android Kotlin/Compose, mailcow/SOGo accounts
under @houseofmanns.com). F-Droid only, Linus-permanent tone, caveman comms.
All clones go to ~/host/.

================================================================================
LINUS TORVALDS 10 PRINCIPLES (apply to every change)
================================================================================
1. Good code needs good data structures; spend time on them first.
2. Talk is cheap — show the code. Don't theorize, demonstrate.
3. Simplicity beats cleverness every time. Stupid is better if it's clear.
4. Start small. Don't boil the ocean; build the minimal correct thing.
5. Avoid big design decisions early. Let the code reveal what it needs.
6. Reproduce a bug before you fix it. Never patch blind.
7. Fix the root cause, not the symptom. One real fix > ten band-aids.
8. Comments explain WHY, never WHAT. The code says what; say why it's weird.
9. Fix the design, don't just indent the bug away.
10. If you see a broken window, surface it. Don't let rot spread.

================================================================================
CURRENT DEVICE
================================================================================
Tablet TB570FU (Lenovo Tab, Android 16), package com.unifiedcomms.debug,
reachable at 10.0.0.211:<PORT> — PORT ROTATES when wireless debug drops.
Keith supplies the new port. As of this handoff: 10.0.0.211:33323.
Emulator-5556 is OFF-LIMITS per Keith's standing rule (physical tablet only).

Latest commit on master: 7a3e467
  feat(calendar-invite): parse embedded text/calendar invites + render
  InviteCard with Accept/Decline/Add

================================================================================
WHAT SHIPPED THIS SESSION (committed, 7a3e467)
================================================================================
EMAIL CALENDAR-INVITE FEATURE (user-reported: raw ICS leaked into email body,
no Accept/Decline/Add buttons):
- EmailSyncEngineImpl.parseEmail: at SYNC time, scans bodyText+bodyHtml for
  BEGIN:VCALENDAR, parses via ICalParser, maps to CalendarInviteMessage
  (InviteMapper.toInviteMessage). Logs "INVITE" tag on extraction.
- Email model: nullable `invite` field + InviteMessageConverter (JSON) at
  DB @TypeConverters level.
- MainViewModel.addInviteToCalendar(invite) -> builds CalendarEvent via
  InviteMapper, calendarRepo.insertEvent, returns it.
- MainViewModel.respondToInvite(invite, status) -> inserts event if missing,
  stamps the account attendee status (sanitized email), calendarRepo.updateEvent
  + calendarSync.updateEvent (pushes RSVP to server).
- EmailScreen: InviteCard composable (title/time/location/organizer + Add to
  Calendar / Accept / Decline buttons) rendered in EmailDetailScreen when
  email.invite != null.
- InviteMapper.sanitize() strips whitespace from attendee emails (fixes the
  "houseo fmanns" domain typo in the captured invite so RSVP replies don't bounce).

DB SCHEMA (v3 -> v4):
- MIGRATION_3_4: idempotent guards. Adds emails.invite TEXT; drops legacy
  idx_emails_thread (not in current Email @Entity); adds calendar_events.isCancelled
  INTEGER NOT NULL DEFAULT 0 if missing.
- exportSchema = false (avoids validating against stale committed schema JSONs).
- Removed app/schemas/ (1.json/2.json/3.json) — unused with exportSchema=false.

================================================================================
VERIFICATION STATUS — READ THIS HONESTLY
================================================================================
PROVEN (on device 10.0.0.211:33323):
- App COMPILES + BUILDS (assembleDebug green).
- App BOOTS on a FRESH DB: shows "No emails yet / Add an account" with no crash.

NOT YET PROVEN (gap, not hidden):
- The InviteCard actually RENDERING for a real invite email + Accept/Decline/Add
  buttons performing the insert/RSVP. Requires an account with a real invite synced.
- The MIGRATION_3_4 path for an EXISTING v3 DB (legacy-drift case) was the cause of
  a launch crash; fixed idempotently, but full legacy-DB upgrade not re-exercised
  (see BLOCKER below — the personal account DB was wiped during verification).

WHY THE GAP EXISTS:
- During migration-debugging this session the app was `adb uninstall`ed (to force a
  fresh v4 DB), which WIPED the previously-added personal account + local data on
  the device. The app currently has NO account. To verify the invite end-to-end, a
  testbox@houseofmanns.com account is being added (see below) and Keith will send a
  real invite from keith.manns@houseofmanns.com -> testbox.

================================================================================
CREDENTIAL HANDLING (mailcow, CRITICAL)
================================================================================
- NEVER inline a password in chat/shell history. Read from file, type via
  `adb shell input text '<pw>'` with SINGLE quotes so $ * # survive the remote shell.
- testbox@houseofmanns.com login = MASTER password at ~/.hermes/uc_main_pw
  (proven by the live email-sync instrumentation test which logs in with it).
- MAILCOW LOCKOUT TRAP: testbox locks after >2 wrong passwords in 2 min. Max ~2
  auth attempts per session. After 2 failures, STOP and ask Keith.
- Servers: IMAP imap.houseofmanns.com:993 (SSL, acceptAllCerts=true — wildcard cert
  has no bare-domain SAN), SMTP smtp.houseofmanns.com:587 (STARTTLS).
- DAV base: https://email.houseofmanns.com/SOGo/dav/<user>/
- App-passwords containing $ * # are REJECTED by mailcow IMAP auth (server policy);
  the MASTER password works via Advanced settings. Do NOT "fix" client code on
  [AUTHENTICATIONFAILED] — the server rejects the credential.

================================================================================
NEXT STEP (in progress): ADD testbox ACCOUNT, VERIFY INVITE
================================================================================
1. Install current debug APK (already built as app-debug.apk).
2. In app UI: Add Account -> Mailcow chip -> Advanced (IMAP imap.houseofmanns.com:993
   SSL, SMTP smtp.houseofmanns.com:587 STARTTLS, acceptAllCerts=true) ->
   email testbox@houseofmanns.com + password from ~/.hermes/uc_main_pw typed via
   single-quoted `adb shell input text` -> Confirm.
3. Let inbox + calendar sync.
4. Keith sends a calendar invite from keith.manns@houseofmanns.com to testbox.
5. Open the invite email on device -> confirm InviteCard renders (title/time/location/
   organizer) with Add to Calendar / Accept / Decline buttons.
6. Tap Accept -> logcat "INVITE" + confirm calendar_events gains the event with the
   account attendee status stamped ACCEPTED. Tap Add to Calendar explicitly too.
7. Screenshot proof. Only THEN claim the feature works.

STATUS (2026-07-26, done): testbox@houseofmanns.com ADDED + SYNCING OK.
- Added via UI: Mailcow chip + Advanced (imap.houseofmanns.com:993 SSL,
  smtp.houseofmanns.com:587 STARTTLS, Accept-all-certificates ON), master pw
  from ~/.hermes/uc_main_pw (single-quoted input text).
- SyncManager log: performFullSync email=testbox@houseofmanns.com ->
  imapHost=imap.houseofmanns.com:993 ssl=true -> email leg success=true,
  calendar leg success=true. IMAP + CalDAV both connect; sync completes clean.
- INBOX currently empty (no invite sent yet). Awaiting Keith's invite from
  keith.manns@houseofmanns.com -> testbox to exercise the InviteCard path.
- NOTE: the live-credential-e2e ref mentions ~/.hermes/uc_testbox_pw but that
  file does NOT exist; testbox uses the MASTER pw at ~/.hermes/uc_main_pw
  (proven by the live email-sync instrumentation test which logs in with it). Do not use uc_test_pw.

================================================================================
KEY FACTS (cross-checked, current)
================================================================================
- Keith verification bar: green build + screenshot is NOT proof. Core functions must
  work on the REAL device before "fixed" is spoken.
- Biometric lock: confirmed working by user. Don't touch it.
- Chat feature: REMOVED 2026-07-28 (see CHAT REMOVAL below). No code references remain.
- Don't modify personal houseofmanns.com / keith.manns server data without go-ahead;
  testbox is for test writes only.
- THE EMAIL DOUBLE-CLICK BUG IS ROOT-CAUSE FIXED THIS SESSION (2026-07-27).
  See "SESSION 2026-07-27" section below. Verified on the PHONE (10.0.0.228:40311).

================================================================================
CURRENT DEVICE (2026-07-27 refresh)
================================================================================
PHONE = 10.0.0.228:40311 (CURRENT, primary test device this session). Account
  testbox@houseofmanns.com ATTACHED, Accept-All-Certs ON, emails synced.
TABLET TB570FU (Lenovo Tab, Android 16) = 10.0.0.211 (used earlier; currently
  the PHONE is the live target). emulator-5556 OFF-LIMITS per Keith.
Git HEAD: 71d5cd5 (ui: invite buttons — third matches Accept/Decline, '+Just Add').
  HANDOFF top-of-file "Latest commit 7a3e467" is STALE — real HEAD is 71d5cd5.

================================================================================
SESSION 2026-07-27 — INVITE BUTTONS, ENCRYPTION Q, + EMAIL DOUBLE-CLICK FIX
================================================================================
USER REQUESTS THIS SESSION (verbatim intent):
1. Invite 3rd button: make it look like the other two (Accept/Decline) and
   rename "Add to Calendar" -> "+Just Add". [DONE + VERIFIED on phone]
2. Telemetry toggle in Settings: there should be NONE (no telemetry exists). Remove it. [CODED]
3. Sync interval: minimum "every 5 minutes". [CODED]
4. Encryption: user asked what it means — does it encrypt email in transit / at rest?
5. Email list: "almost like you have to click twice before you can click the email.
   One click and I'm in the email." [ROOT-CAUSE FIXED + VERIFIED]
6. Account settings: when opened it's not in dark mode. [CODED]
7. Biometric: toggling ON should instantly popup fingerprint, verify, then close —
   no app restart, and NO separate "Unlock" button before the biometric prompt. [CODED]

--- 1. INVITE 3RD BUTTON (+Just Add) — VERIFIED ON PHONE ---
EmailScreen.kt InviteCard: 3rd button was a full-width FilledTonalButton labeled
  "Add to Calendar". Now a filled Button (matches Accept/Decline) in the same Row
  (Accept weight(1f), Decline weight(1f), +Just Add weight(1.3f) so the longer
  label fits one line), text "maxLines=1, labelSmall", icons removed so all 3 fit.
  Behavior unchanged (still calls addInviteToCalendar — no RSVP, no reminder;
  reminder gap noted earlier: alarms field not populated from invite VALARM).
Committed: 71d5cd5. Verified: 3 equal-ish buttons, +Just Add complete, no clip.

--- 4. ENCRYPTION MEANING (answer, no change) ---
EncryptionScreen is AT-REST ONLY: encrypts stored credentials, calendar, tasks on-device
  via AES-GCM with a master key in Android Keystore. It is NOT end-to-end and
  does NOT encrypt email bodies in transit. In transit, email uses
  the server's TLS (EmailSyncEngineImpl: imap.ssl.enable / smtp.starttls.enable).
  local data-at-rest only; not a Signal-style E2E scheme.

--- 5. EMAIL DOUBLE-CLICK — ROOT CAUSE FOUND + FIXED + VERIFIED ---
SYMPTOM: tapping an inbox row did nothing on first tap; second tap opened it.
DIAGNOSIS (real, not guessed — used logcat EMAILTAP/EMAILNAV tags, then removed):
- The row's click lambda DOES fire on the first tap (confirmed via log).
- It called onNavigateToEmail(accountId, "INBOX") -> navController.navigate(
  "email/$accountId/INBOX").
- THAT ROUTE ("email/{accountId}/{folder}") renders EmailScreen = a FOLDER
  LIST, not the email detail. The actual detail route is the SEPARATE
  "email_detail/{emailId}" (renders EmailDetailScreen via getById(emailId)).
- So tap1 -> folder list (EmailScreen), tap2 -> actually opens the email.
  That two-stage navigation is the "double click."
FIX: added onEmailClick(emailId) callback threaded Row -> EmailOverviewScreen
  -> UnifiedInboxScreen -> MainActivity, navigating straight to
  "email_detail/$emailId". Row now calls onEmailClick(email.id); onNavigateToEmail
  (accountId,folder) is kept for the drawer folder-open path (EmailScreen list).
VERIFIED ON PHONE (10.0.0.228): single tap on "Team Sync Invite" row -> opens
  EmailDetailScreen directly (InviteCard "Quarterly Planning Sync" + Accept/Decline/
  +Just Add visible). Dump after single tap shows Accept/Decline/+Just Add, list left.
NOT YET COMMITTED (part of the uncommitted batch below — verify/commit pending).

--- 2 / 3 / 6 / 7 — VERIFIED ON DEVICE 2026-07-27 (phone 10.0.0.228:40311) ---
- SettingsScreen.kt: removed the "No Telemetry" SettingItem entirely (pref
  no_telemetry was write-only — nothing reads it, so removal is honest).
  Added 5 to "Every 5 minutes" as the new floor in the interval picker list
  AND updated the syncLabel when-block (5 -> "Every 5 minutes"; else -> "Every 5
  minutes"). Biometric toggle: when enabling, now launches a BiometricPrompt
  immediately (canAuthenticate check first); pref is set true ONLY on
  onAuthenticationSucceeded. No more "set then restart" — instant verify.
- MainActivity.kt BiometricLockScreen: removed the separate "Unlock" AlertDialog
  button. The system prompt now auto-launches via LaunchedEffect(Unit) the
  moment the lock appears; on success -> onUnlocked() (no app restart). If
  canAuthenticate != SUCCESS it still shows the reason text.
- AccountSettingsScreen.kt + EncryptionScreen.kt: both were calling
  UnifiedCommsTheme { } with the DEFAULT darkTheme (= isSystemInDarkTheme()),
  ignoring the app's effectiveDark — that's why the account screen looked light.
  Added `darkTheme: Boolean = false` param to each; MainActivity passes
  effectiveDark into both nav destinations.

--- UNCOMMITTED WORKING TREE (git status --short, pre-commit) ---
 M AccountSettingsScreen.kt   (darkTheme param + pass effectiveDark)
 M EncryptionScreen.kt       (darkTheme param + import UnifiedCommsTheme)
 M MainActivity.kt           (onEmailClick nav; biometric auto-prompt; darkTheme pass)
 M SettingsScreen.kt         (telemetry removed; 5-min floor; biometric instant-verify)
 M UnifiedInboxScreen.kt     (onEmailClick threaded; row uses onEmailClick(email.id);
                                remember(emails) for stable threads list)
VERIFIED ON DEVICE 2026-07-27 (phone 10.0.0.228:40311, build 1.0.28-debug):
- Settings/Security: "No Telemetry" row GONE (dumped text list, absent).
- Sync interval picker: "Every 5 minutes" present as first option (5-min floor
  enforced). Current value still "Every 15 minutes" (user default, unchanged).
- Account settings screen: opened with Appearance=Dark -> screenshot corner
  luminance = 29 (near-black) => dark theme IS applied (was light before fix).
- Biometric Lock: toggled ON -> system fingerprint prompt appeared immediately;
  Keith authenticated with fingerprint -> lock engaged. No separate "Unlock"
  button, no app restart. VERIFIED by user.

COMMITTED this session (commit after verification): email single-tap fix +
  telemetry removal + 5-min floor + dark account/encryption + biometric instant.

--- KNOWN PRE-EXISTING BUG SURFACED (NOT fixed this batch) ---
Email LIST row preview leaks the raw ICS blob: "Please accept... --BOUND
method=REQUEST; charset=utf-8 BEGIN:VCALENDAR VERSI...". Commit 49569d1 only
stripped the ICS from the DETAIL screen; the OVERVIEW/LIST preview still shows
the raw invite body. Separate fix needed (sanitize list preview like detail).
Out of scope for this batch; flagged for Keith's call.

--- THIS SESSION (2026-07-27, continued) ---

VERIFIED ON DEVICE 10.0.0.228 (build after 0a4ccab):
- DRAWER TOO WIDE — FIXED. ModalDrawerSheet was default M3 360dp (≈1262px on
  this 411dp screen), a floating tonal-elevated panel for ~7 folder rows. Capped
  sheet at 280dp, drawerTonalElevation=0, surface-colored. Measured panel
  visible width ≈155dp. Added "Add Account" item at drawer bottom (icon+label)
  -> navigates to add_account.
- ADD ACCOUNT IN DARK MODE — FIXED. AddAccountScreen called UnifiedCommsTheme{}
  (system default), ignored app dark. Now passes effectiveDark. Screenshot
  corner luminance = 29 (near-black) => matches app dark theme.

CHAT REMOVAL — 2026-07-28
================================================================================
Decision: the Chat feature was email rendered as bubbles (BlueMail-style IMAP/SMTP
+ closed Blix cloud, NOT selfhostable). Keith ruled it not worth maintaining and
removed it. No selfhostable server-side exists, so no alternative path taken.

What was deleted (chat-only, no shared code lost):
- ChatSyncEngine.kt / ChatSyncEngineImpl.kt (IMAP Chat-folder poll + SMTP send)
- MessagesScreen.kt / ConversationScreen.kt (chat UI)
- IMessagingService.aidl + IMessagingCallback.aidl + MessageParcel.aidl + ConversationParcel.aidl (AIDL IPC)
- ConversationParcel.kt / MessageParcel.kt
- MessagingForegroundGate.kt
- Conversation entity + ConversationDao + conversations table
- MessagingRepository conversation half (kept message/search methods)
- BackgroundSyncWorker chat wiring; SyncManager chatSync param + sendChatMessage
- MainViewModel messagingRepo field + sendMessage; EmailSyncEngineImpl.listFolders
  Chat-folder exclusion (FIX: was orphaning real mail moved to a hidden Chat folder)
- Nav tab, compose_message route, syncChat/chatFolder config
- 2 chat androidTests

Kept (shared, non-chat): Message entity + MessageDao.searchMessages (powers the
message SEARCH feature), UnifiedContact, CalendarInviteMessage, share messages,
getCurrentUserId().

Room: v4 -> v5. MIGRATION_4_5 DROPs the conversations table. On existing installs
the migration runs at DB open (proven: live email-sync test opens DB and passes).

Verification (REAL DEVICE, logcat gold standard):
- Phone (Samsung SM-S908U 10.0.0.228:40311): HouseOfMannsEmailSyncTest
  started -> finished -> VM exit code 0. Real IMAP connect/send/sync/Room read-back.
- Tablet (TB570FU 10.0.0.211:34755): same test passed, result code 0.
- Release APK (assembleRelease --rerun-tasks) builds + signs (v1/v2/v3,
  CN=UnifiedComms O=Dvalin21). Installed on phone, launches, no crash.
- The "Migration didn't properly handle" failure seen mid-session was a leftover
  FolderListTest chatFolder ref (compile error), NOT a runtime migration bug.
  Fixed; all device tests pass.

BlueMail comparison / AltMarkMove chat study / live-chat milestone: CANCELLED —
chat no longer exists in this codebase.
