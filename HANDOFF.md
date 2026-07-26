# UnifiedComms — HANDOFF (2026-07-24)

Project: ~/host/UnifiedComms (Android Kotlin/Compose, mailcow/SOGo account
keith.manns@houseofmanns.com, MAILCOW). This session continues email/calendar
polish for Keith (Dvalin21). F-Droid only, Linus-permanent tone, caveman
comms. All clones go to ~/host/.

================================================================================
LINUS TORVALDS 10 PRINCIPLES OF CODING (apply to every change)
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

Translation for this repo: no over-abstraction, no fake-success stubs, verify on
the real device/emulator before claiming done, and never weaken a boundary to
make a test pass.

================================================================================
CURRENT DEVICE
================================================================================
Tablet TB570FU (Lenovo Tab, Android 16) reachable at 10.0.0.211:<PORT>
— the PORT ROTATES every time the wireless debug session drops. Keith
supplies the new port when he's back at the computer. Package:
com.unifiedcomms.debug. Emulator-5556 is OFF-LIMITS per Keith's
standing rule (use ONLY the physical tablet for verification). The emulator
falls back only if he explicitly unblocks it.

================================================================================
WHAT'S ALREADY SHIPPED (committed + verified earlier)
================================================================================
- f5a772b: email body extraction fix (IMAP raw blob → real text) + CursorWindow
  overflow fix on large folders. USER CONFIRMED: "Finally seeing the content."
- d7c9ed9: CalDAV calendar discovery recursion fix (was recursing into
  .ics event items → 20s timeout → 0 events). USER CONFIRMED: 1544 events.
- b5efea8: IMAP error-classification fix (mailcow lockout mislabeled).
- d5dd160: email body cleanup for forwarded/quoted + html-only messages
  (225/228 bodies clean). USER CONFIRMED content shows.

================================================================================
THIS SESSION'S WORK (CODED, NOT YET COMPILED-CLEAN / NOT COMMITTED)
================================================================================
Four user-reported items, all edits are UNSTAGED working-tree changes:

A) ATTACHMENTS (Tax Return email) — root cause found + fix coded.
   - Bug: extractAttachments(msg, …) was called on the raw IMAPMessage.
     On Android JavaMail msg.content is a raw IMAPInputStream, so
     `part.content as MimeMultipart` threw → every attachment silently dropped.
   - Fix 1: EmailSyncEngineImpl calls extractAttachments(parsedMsg, …)
     (the re-parsed MimeMessage) and builds MimeMultipart from the stream
     safely (same pattern as the body fix).
   - Fix 2: NEW EmailSyncEngine.fetchAttachment(account, folder, uid, att)
     → opens IMAP, gets message by UID, walks parts, matches by
     filename/content-id, saves to cacheDir/attachments, returns path.
   - Fix 3: MainViewModel.downloadAttachment(...) delegates to the engine
     (captured emailSyncEngine as a property instead of inline).
   - Fix 4: EmailScreen detail view renders an "Attachments" section
     (chips: name + size); tap → download → open via FileProvider.
   - Fix 5: AndroidManifest <provider> for androidx.core.content.FileProvider
     authority com.unifiedcomms.fileprovider + res/xml/file_paths.xml
     (cache-path).
   - Fix 6: threaded `attachments` into updateSyncMeta (DAO @Query SET
     clause + EmailRepository interface/impl + engine call site) so the
     extracted list actually PERSISTS on sync.

B) HTML RENDERING — coded.
   - EmailScreen detail body: if bodyHtml present, render via AndroidView(WebView)
     with JavaScript disabled + blockNetworkLoads=true (no network access);
     else fall back to plain bodyText. WebView is the correct renderer for
     arbitrary email HTML (GMail/Samsung-style).

C) CALENDAR COLORS (match creation color, Samsung-style) — coded.
   - CalDAVClient.CalendarInfo gained `color: String`.
   - scanForCalendars now PROPFINDs `calendar-color` (urn:ietf + Apple
     IC:/A: namespaces — byLocalName is prefix-agnostic) and captures it
     via extractCalendarColor(resp).
   - ICalParser.parse(..., defaultColor="") threads the calendar
     color down; a VEVENT with no per-event COLOR inherits defaultColor.
   - CalendarSyncEngineImpl.syncAccount: resolves color as
     event.color (if not default) → calendar color → existing → event.color;
     AND re-fetches events still on default-blue whose calendar has a real
     color (one-time recolor pass so the first-pull blue gets repainted).

D) CALENDAR DELAY + TEXT OVERFLOW — coded.
   - Delay: the open-time LaunchedEffect(activeAccountIds) used to call
     syncCalendarForAccounts on EVERY open; that re-fetches from server and
     briefly blanks the list (delete-then-insert emits empty mid-sync) →
     "takes a second before events show". Now only fires when the cache
     is empty (background WorkManager already keeps the DB fresh).
   - Overflow: event title Text got Modifier.fillMaxWidth() so
     maxLines=1 + softWrap=false + overflow=Ellipsis actually clips
     instead of spilling across the event box.

================================================================================
BLOCKER TO RESOLVE FIRST (next session)
================================================================================
Final `./gradlew :app:assembleDebug` FAILED at :app:kaptGenerateStubsDebugKotlin
(a compile error). The tail was truncated by the `--rerun-tasks` cache
warning so the exact file:line was NOT captured. The last edits were
PURELY the `attachments` column threading:
  - app/src/main/java/com/unifiedcomms/data/db/dao/EmailDao.kt
      updateSyncMeta @Query gained `attachments = :attachments` in the
      SET clause + the suspend fun gained `attachments: List<Attachment>`.
  - app/src/main/java/com/unifiedcomms/data/repository/EmailRepository.kt
      interface updateSyncMeta gained `attachments: List<Attachment>`
      + `import com.unifiedcomms.data.model.Attachment`.
  - app/src/main/java/com/unifiedcomms/data/repository/EmailRepositoryImpl.kt
      override signature + dao call updated + `import ...Attachment`.
  - app/src/main/java/com/unifiedcomms/sync/EmailSyncEngineImpl.kt
      updateSyncMeta call site passes `attachments = email.attachments`.

The failure is almost certainly a missing import or signature mismatch in that
chain. RESOLUTION: run
  export ANDROID_HOME=/home/keith/Android/Sdk
  cd ~/host/UnifiedComms
  ./gradlew :app:assembleDebug 2>&1 | grep -E "e:|\.kt:[0-9]+"
to surface the exact error, fix it, rebuild green.

================================================================================
VERIFICATION PLAN (after compile is green)
================================================================================
1. export ANDROID_HOME=/home/keith/Android/Sdk
2. ./gradlew :app:assembleDebug :app:assembleAndroidTest --rerun-tasks
3. adb -s 10.0.0.211:<NEWPORT> install -r app/build/outputs/apk/debug/app-debug.apk
   adb -s 10.0.0.211:<NEWPORT> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
4. Trigger a calendar sync (recolor pass) + email sync (attachment extraction):
   re-run the EmailSyncDebugTest instrumented class, OR open the app and
   let background sync run, then:
   sqlite3 pulled DB:
     - SELECT count(*) FROM emails WHERE json_array_length(attachments) > 0;
       (expect the Tax Return email to now have an attachment row)
     - SELECT count(*) FROM calendar_events WHERE color != '#2196F3';
       (expect non-default colors after the recolor pass)
5. On-device manual: open Tax Return email → tap attachment → opens;
   open an HTML email → renders; open Calendar → events colored + no
   open delay + titles clipped.
6. git add -A the real source files (NOT the throwaway
   EmailSyncDebugTest.kt / CalendarSyncDebugTest.kt / stray png+xml),
   commit, push origin master (Keith: push straight to master, no PR).

================================================================================
THROWAWAY / DO NOT COMMIT
================================================================================
- app/src/androidTest/java/com/unifiedcomms/EmailSyncDebugTest.kt (untracked)
- app/src/androidTest/java/com/unifiedcomms/CalendarSyncDebugTest.kt (untracked)
- any /tmp/uc_db*.db pulls, /sdcard/*.png, /sdcard/*.xml dumps

================================================================================
KEY FACTS (memory-cross-check)
================================================================================
- mailcow DAV base: https://email.houseofmanns.com/SOGo/dav/<user>/
  calendar collection: .../Calendar/personal/  (tasks home-set separate)
- NEVER use mailcow app-password (server rejects $*#); login via Advanced
  + MASTER password only. Master pw at ~/.hermes/uc_main_pw (chmod 600,
  read-only, never inline, never to memory).
- IMAP imap.houseofmanns.com:993 ; SMTP smtp.houseofmanns.com:587.
- Biometric lock: confirmed working by user. Don't touch it.
- Chat: user will test tomorrow — out of scope this session.
- Keith verification bar: green build + screenshot is NOT proof. Core
  functions must work on the REAL device before "fixed" is spoken.
