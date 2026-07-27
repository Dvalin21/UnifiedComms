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
  (proven by seed_chat.py which logs in with it). (~/.hermes/uc_test_pw also exists;
  use uc_main_pw for testbox — matches the working seed_chat.py path.)
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
  (matches seed_chat.py which logs in with it). Do not use uc_test_pw.

================================================================================
KEY FACTS (cross-checked, current)
================================================================================
- Keith verification bar: green build + screenshot is NOT proof. Core functions must
  work on the REAL device before "fixed" is spoken.
- Biometric lock: confirmed working by user. Don't touch it.
- Chat feature: out of scope this session.
- Don't modify personal houseofmanns.com / keith.manns server data without go-ahead;
  testbox is for test writes only.
