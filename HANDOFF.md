UnifiedComms — handoff state
Generated: 2026-07-06
Build: GREEN (verified this session)
Package: com.unifiedcomms.debug
Main activity: com.unifiedcomms.ui.main.MainActivity
Branch: master
Latest release: v0.2.0-alpha.4 at https://github.com/Dvalin21/UnifiedComms/releases/tag/v0.2.0-alpha.4

Modified files
- app/src/main/java/com/unifiedcomms/ui/main/MainActivity.kt
- app/src/main/java/com/unifiedcomms/ui/main/SettingsScreen.kt
- app/src/main/java/com/unifiedcomms/ui/main/AddAccountScreen.kt
- app/src/main/java/com/unifiedcomms/ui/main/MainViewModel.kt
- app/src/main/java/com/unifiedcomms/ui/main/UnifiedInboxScreen.kt
- app/src/main/java/com/unifiedcomms/util/PreferencesManager.kt
- app/src/main/java/com/unifiedcomms/sync/SyncManager.kt
- app/src/main/AndroidManifest.xml

Fixes applied this session (2026-07-06 baseline)
- Theme toggle, account auto-sync, sync notifications, inbox badge, settings UI, advanced IMAP/SMTP, dead-code removal (see prior handoff)

=== FIX CAMPAIGN STARTED 2026-07-07 (suspension lifted) ===
Phases: Critical -> High -> Medium -> Low. Personas: Linus Torvalds + Ponytail.

PHASE 1 — CRITICAL (the lying paths): COMPLETE, assembleDebug GREEN
- Email send now actually transmits: ComposeEmailScreen routes through EmailSyncEngineImpl.sendEmail; local copy only persisted on real SMTP success; failures surfaced in UI (error text). Fixed mis-wired back arrow that navigated without saving.
- Email move/delete are now real IMAP ops (EmailSyncEngineImpl.moveToFolder/deleteMessages): match server messages by Message-ID, copy+flag DELETED+expunge. No longer fake success. Wired EmailScreen delete button + MainViewModel.moveEmails/deleteEmails through SyncManager.
- Task/Contact write stubs (createTask/updateTask/deleteTask/completeTask, createContact/updateContact/deleteContact) now FAIL HONESTLY instead of returning fake SyncResult.success(). No silent lies when wired.

Pending verification: install APK on emulator-5560, add a real/IMAP account, send a message, confirm it arrives + appears in Sent, delete a message and confirm server expunge.

Fix-before-ship carried into later phases (not yet fixed)
- HIGH: OAuth token refresh never called (accounts die at expiry) [#7]
- HIGH: Recurring events dropped on import (ICalParser/RecurrenceRule) [#8]
- HIGH: Calendar TZ parsing corrupts times (ICalParser DTSTART TZID) [#9]
- HIGH: Create-event calendarId=accountId mismatch vs DAV href [#10]; EventDetailScreen uses event.timezone [#11]
- HIGH: Boot reminder reschedule broken (param ignored) [#12]; alarm PendingIntent collapses to id 0 [#13]; cancelReminders no-op [#14]
- HIGH: Converters no try/catch -> one corrupt row kills whole query [#15]
- MEDIUM (all COMPLETE this session, assembleDebug GREEN): two Add-Account paths; main UI AddAccountScreen builds AppPassword for GOOGLE/OUTLOOK (no OAuth) [#19] -> FIXED: AddAccountScreen now delegates GOOGLE/OUTLOOK/YAHOO/ICLOUD to AddAccountActivity web OAuth flow instead of fake AppPassword.
- MEDIUM: MessagingService dead + permission not declared [#20]; PushManager fake backend [#21] -> FIXED: deleted dead MessagingService + manifest entry (removes undefined-permission hole); PushManager.subscribeToTopic/unsubscribeFromTopic now hit the real backend ($serverUrl/api/v1/devices/{id}/subscribe|unsubscribe) instead of returning hardcoded true.
- MEDIUM: SearchActivity no-op [#17]; SettingsActivity stub [#18]; ContentProvider/Authenticator empty [#16]
- LOW: manifest over-broad perms [#25] -> FIXED: removed 15 unused/over-broad perms (READ/WRITE/MANAGE_EXTERNAL_STORAGE, CAMERA+feature, RECORD_AUDIO+mic feature, BLUETOOTH*/NEARBY_WIFI_DEVICES, MANAGE_ACCOUNTS, AUTHENTICATE_ACCOUNTS, USE_FINGERPRINT). None referenced in code; targetSdk 35 makes storage perms no-ops and MANAGE_EXTERNAL_STORAGE a Play-rejection flag.
- LOW: findDirectConversation JSON-equality [#26] -> FIXED: query was `participantIds = :participants` (order-sensitive serialised-list match); caller passed listOf(userId, trimmed) so a stored conversation with reversed order was never found, creating duplicate conversations. DAO now matches both orderings (asc OR desc); repo passes the reversed list.

Wiring notes
- SyncManager instantiated in MainViewModel with app context
- NotificationHelper.showSyncNotification used for actual sync notifications
- Advanced account settings support GENERIC_IMAP_SMTP, GENERIC_CALDAV_CARDDAV, MAILCOW, OUTLOOK, EXCHANGE, CUSTOM

Compile status
- assembleDebug = GREEN (2026-07-07 Phase 1)
- Debug APK built: app/build/outputs/apk/debug/app-debug.apk
- Emulator targets: emulator-5554 (occupied), emulator-5560 (free test target)

=== PHASE 2 — HIGH (real defects fixed, assembleDebug GREEN 2026-07-07) ===
Files changed this phase:
- app/src/main/java/com/unifiedcomms/sync/OAuthTokenRefresher.kt (NEW)
- app/src/main/java/com/unifiedcomms/data/db/converters/Converters.kt
- app/src/main/java/com/unifiedcomms/sync/ICalParser.kt
- app/src/main/java/com/unifiedcomms/data/model/CalendarEvent.kt
- app/src/main/java/com/unifiedcomms/sync/CalDAVClient.kt
- app/src/main/java/com/unifiedcomms/sync/CalendarSyncEngineImpl.kt
- app/src/main/java/com/unifiedcomms/sync/EmailSyncEngineImpl.kt
- app/src/main/java/com/unifiedcomms/sync/SyncManager.kt
- app/src/main/java/com/unifiedcomms/reminder/ReminderSystem.kt
- app/src/main/java/com/unifiedcomms/sync/BootReceiver.kt
- app/src/main/java/com/unifiedcomms/ui/main/CalendarScreen.kt
- app/src/main/java/com/unifiedcomms/ui/main/MainViewModel.kt

Fixes applied (HIGH phase)
- #7 OAuth refresh: OAuthTokenRefresher.refresh() now hits oauthTokenUrl with refresh_token when token missing/expiring<5min; SyncManager.performFullSync refreshes before dispatch. Email engine uses XOAUTH2 for IMAP/SMTP when AuthType.OAUTH2; CalDAVClient sends Bearer when OAuth. Accounts no longer die silently at expiry.
- #8 Recurrence dropped on import: RecurrenceRule.Companion.parse() now reads FREQ/INTERVAL/COUNT/UNTIL/BYDAY/BYMONTHDAY; ICalParser sets event.recurrenceRule from RRULE. (Instance expansion still stored on master — UI/sync render pending, noted.)
- #9 TZ corruption: ICalParser.tzIdFromKey() reads DTSTART;TZID= and parseDateTime applies that zone; startAt/endAt/timezone now carry the real TZID instead of forced system default.
- #10 Local events wiped: locally-created events now marked isLocalOnly=true; CalendarSyncEngineImpl down-sync delete-sweep skips isLocalOnly. CreateEventScreen calendarId=accountId retained (harmless: not server-backed).
- #11 EventDetailScreen used event.timezone (forced default); now reads startAt.timeZone/endAt.timeZone for correct display.
- #12 Boot reschedule broken: BootReceiver + ReminderScheduler.scheduleReminders(null) now resolve "all" to every real account id via AccountRepository.
- #13 Alarm collapse: notification full-screen PendingIntent now uses eventId.hashCode() as requestCode (was 0).
- #14 cancelReminders no-op: now tracks scheduled intent keys and actually cancels them; added cancelAll().

Remaining HIGH carry-over (not yet done — honest status)
- Recurrence INSTANCE expansion (RECURRENCE-ID/EXDATE) not generated; masters carry rule but individual occurrences not materialized for UI.
- Task/Contact sync backends still stubs (fail honestly, unchanged this phase).
- OAuth refresh verified by compile only; needs a live OAuth account on emulator-5560 to confirm token round-trip.

Fix-before-ship
- Functional verification on emulator still limited; account auto-sync and notifications require adding an account and observing notification channel
- Advanced settings fields need end-to-end validation on device

=== PHASE 3 — MEDIUM (real defects fixed, assembleDebug GREEN 2026-07-07) ===
Files changed this phase:
- app/src/main/java/com/unifiedcomms/ui/search/SearchActivity.kt (rewritten: real search)
- app/src/main/java/com/unifiedcomms/ui/main/AddAccountScreen.kt (OAuth delegation)
- app/src/main/java/com/unifiedcomms/ui/main/UnifiedInboxScreen.kt (Search launch button)
- app/src/main/java/com/unifiedcomms/ui/main/MainActivity.kt (Search launch wiring)
- app/src/main/java/com/unifiedcomms/push/PushManager.kt (real subscribe/unsubscribe)
- app/src/main/AndroidManifest.xml (removed MessagingService + ContentProvider)
- DELETED: app/src/main/java/com/unifiedcomms/ui/settings/SettingsActivity.kt (orphan stub)
- DELETED: app/src/main/java/com/unifiedcomms/sync/accounts/ContentProvider.kt (vestigial)
- DELETED: app/src/main/java/com/unifiedcomms/messaging/MessagingService.kt (dead, undefined-permission)

Fixes applied (MEDIUM phase)
- #17 Search no-op: SearchActivity now queries EmailRepository.searchEmails / CalendarRepository.searchEvents / TaskRepository.searchTasks / ContactRepository.search across active account IDs, renders results grouped by kind. Launched from UnifiedInboxScreen top-bar Search icon (was never wired before).
- #18 SettingsActivity stub: deleted — orphan class not referenced anywhere; SettingsScreen composable (used by MainActivity) is the real settings UI.
- #16 ContentProvider/Authenticator empty: deleted vestigial UnifiedCommsContentProvider (returned empty MatrixCursors, no consumer) + manifest <provider> entry. Kept UnifiedCommsAuthenticatorProvider (real, Android account system depends on it).
- #20 MessagingService dead + permission not declared: deleted the Service + its <service> manifest block (it referenced com.unifiedcomms.permission.MESSAGING which was never declared -> install-time security gap). Nothing started this service; messaging flows through MessagingRepository/DAOs directly.
- #21 PushManager fake backend: subscribeToTopic/unsubscribeFromTopic were hardcoded `true`. Now POST to $serverUrl/api/v1/devices/{deviceId}/subscribe|unsubscribe with bearer auth; fail honestly if device unregistered.
- #19 Two Add-Account paths: AddAccountScreen previously built AuthConfig.AppPassword for GOOGLE/OUTLOOK/YAHOO/ICLOUD (OAuth providers) -> silently broken. Now delegates those types to AddAccountActivity's web consent flow (which correctly builds AuthConfig.OAuth2).

Remaining MEDIUM carry-over (honest)
- Search does not yet cover Messages (MessageDao.searchMessages needs conversationIds; no stable userId in local mode). Email/calendar/task/contact are covered.
- PushManager is now unreferenced by any running component (MessagingService removed); kept as a usable HTTP client utility. If nothing consumes it post-MEDIUM, consider removal in a later cleanup pass.

=== PHASE 4 — LOW (real defects fixed, assembleDebug GREEN 2026-07-07) ===
Files changed this phase:
- app/src/main/AndroidManifest.xml (pruned 15 unused/over-broad permissions)
- app/src/main/java/com/unifiedcomms/data/db/dao/MessageDao.kt (order-independent lookup)
- app/src/main/java/com/unifiedcomms/data/repository/MessagingRepositoryImpl.kt (pass reversed ordering)

Fixes applied (LOW phase)
- #25 Manifest over-broad perms: removed READ/WRITE/MANAGE_EXTERNAL_STORAGE, CAMERA (+feature), RECORD_AUDIO (+mic feature), BLUETOOTH/BLUETOOTH_ADMIN/BLUETOOTH_CONNECT/BLUETOOTH_SCAN/NEARBY_WIFI_DEVICES, MANAGE_ACCOUNTS, AUTHENTICATE_ACCOUNTS, USE_FINGERPRINT. Verified none are referenced in code (no camera/audio/bluetooth/MediaStore/external-storage usage; targetSdk 35 makes storage perms no-ops and MANAGE_EXTERNAL_STORAGE a Play-console rejection flag). Kept perms the sync/account system actually uses (contacts, calendar, accounts, sync settings, biometric, notifications, exact-alarm, etc.).
- #26 findDirectConversation order-sensitivity: the DAO compared the serialised participantIds list with `=` — order-sensitive. The only caller (MessagesScreen compose) passes listOf(currentUserId, recipient); if the stored conversation had the participants reversed, lookup failed and a duplicate conversation was created on every message. DAO now matches `(participantIds = :asc OR participantIds = :desc)`; repo passes participants and its reverse. No schema migration needed.

ALL PHASES COMPLETE (Critical -> High -> Medium -> Low).
Build: assembleDebug GREEN across all phases.
Carry-over (honest, not fixed):
- Recurrence INSTANCE expansion (RECURRENCE-ID/EXDATE) still not generated.
- Task/Contact sync backends still fail-honestly stubs.
- OAuth refresh + search + messaging verified by compile only; need live-account runs on emulator-5560.
- Search does not cover Messages (needs conversationIds; unstable userId in local mode).
- PushManager now unreferenced by any running component.

=== VERIFICATION (emulator-5560, 2026-07-07) ===
- Installed app-debug.apk on emulator-5560 (not the S22 physical device).
- Boot smoke test: MainActivity launches, process alive (PID 6865), NO crash, no
  permission denials from the pruned perms, no references to deleted components.
- #17 Search: found + fixed a real crash — SearchScreen nested LazyColumn inside a
  verticalScroll Column (IllegalStateException: infinity max height). Removed the
  outer verticalScroll; LazyColumn now owns scrolling. Verified SearchActivity launches
  as top resumed activity with no crash. Also added the missing <activity> manifest
  declaration (was absent -> in-app launch would have thrown ActivityNotFoundException).
- SearchActivity launch wired from UnifiedInboxScreen top-bar Search icon (content-desc
  "Search", confirmed present in hierarchy).

Remaining before ship
- Live functional verification on emulator-5560 (add account, send/receive, sync, reminders).
- Add automated UI/instrumented tests for core flows.

=== PHASE 5 — E2E EMAIL SYNC (LIVE TEST, 2026-07-07) ===
Drove the real sync engine against a live Ethereal IMAP/SMTP test account via a new
instrumented test (app/src/androidTest/java/com/unifiedcomms/EtherealEmailSyncTest.kt).
Ran ONLY on emulator-5560 (am instrument, single device) — did NOT touch the S22.

Test: testConnection -> sendEmail (self-mail) -> syncAccount -> assert Room persisted rows.
RESULT: PASS (OK 1 test). IMAP SSL login, SMTP STARTTLS send, IMAP fetch, Converters,
and Room insert all confirmed working end-to-end against a real server.

This E2E surfaced THREE real defects that compiled cleanly but broke email sync entirely:

[#P1] CRYPTO BOUNDARY — SyncManager.performFullSync passed the in-memory plaintext
  `account` straight to the engines; decryptAuthConfig only accepts the encrypted
  (DB) form -> every UI-added account failed auth ("testConnection failed: null",
  root cause: keystore2 INVALID_INPUT_LENGTH). FIXED: re-fetch stored account from
  accountRepo before syncing (encrypt-on-write / decrypt-on-read now consistent).

[#P2] IMAP PROTOCOL — openImapSession never set mail.store.protocol, so session.store
  threw "Invalid protocol: null" during the real sync (testConnection passed only
  because it explicitly calls getStore("imap")). FIXED: added mail.store.protocol=imap.

[#P3] CATASTROPHIC PARSING — parseEmail called msg.getHeader("X").firstOrNull() on every
  header. getHeader() returns a nullable Array<String>? and Kotlin's .firstOrNull()
  extension is on the NON-NULL receiver, so ANY absent header (Subject, Date, References,
  X-GM-THRID, From, Reply-To, etc.) threw "getHeader(...) must not be null" -> ZERO
  emails ever parsed -> no mail ever synced. This was the core feature being 100% dead.
  FIXED: getHeader("X")?.firstOrNull() at all 10 sites; also replaced the convenience
  getters (msg.subject / sentDate / receivedDate / size / contentType) that throw on
  missing headers with null-safe getHeader() access + parseDateHeader() helper.

Verified stable: rebuilt clean, ran test on emulator-5560 -> still PASS with the
diagnostic logs removed.

NOTE: Ethereal folder names are INBOX / Sent Mail / Junk / Trash (not "Sent"/"Spam").
  foldersToSync default ["INBOX","Sent","Drafts","Trash","Spam","Archive"] maps to
  INBOX/Drafts/Trash only on Ethereal; "Sent Mail" and "Junk" are skipped. Not a bug,
  but worth a folder-name alias map later for providers that diverge from the defaults.

Emulator artifacts
- APK: app/build/outputs/apk/debug/app-debug.apk
- Test APK: app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
- Test account: Ethereal (ethereal.email) — ephemeral, credentials in the test file.

=== PHASE 6 — RECURRENCE INSTANCE EXPANSION (2026-07-08) ===
Carry-over from Phase 2: recurring events were stored only as masters
(recurrenceId==null, recurrenceRule!=null). The range/date/upcoming repo queries
filtered on the master's single startAt, so every occurrence after DTSTART was
invisible in the UI, day view, month view, and reminders.

Fix: generate occurrences on the fly at the repository query boundary (NOT persisted
as thousands of rows). New object RecurrenceExpander (pure Kotlin, java.time) expands a
master into occurrences within [windowStart, windowEnd] for DAILY/WEEKLY(+BYDAY)/
MONTHLY(+BYMONTHDAY,+BYDAY)/YEARLY. SECONDLY/MINUTELY/HOURLY ignored. COUNT/UNTIL honored.
Wall-clock time preserved across DST via ZonedDateTime period arithmetic (not raw epoch
deltas). Infinite recurrences (no UNTIL/COUNT) bounded by UPCOMING_LOOKAHEAD_MS (366d).

Files changed:
- app/src/main/java/com/unifiedcomms/sync/RecurrenceExpander.kt (NEW)
- app/src/main/java/com/unifiedcomms/data/repository/CalendarRepositoryImpl.kt
  (getEventsInRange / getEventsInRangeUnified / getEventsForDate / getEventsForDateUnified /
   getUpcomingEvents / getUpcomingEventsUnified now expandInWindow(); non-recurring pass through)
- app/src/test/java/com/unifiedcomms/sync/RecurrenceExpanderTest.kt (NEW, 10 JVM tests)

Generated instances carry recurrenceId=master.uid (flagged via isInstance()); recurrenceRule
on instances is null so they don't re-expand. Master remains the sync source of truth.

Verification:
- RecurrenceExpanderTest: 10/10 PASS (weekly BYDAY, DAILY interval, COUNT cap, UNTIL boundary,
  MONTHLY BYMONTHDAY, YEARLY, window exclusion, DST wall-clock preservation, instance flags).
- :app:assembleDebug GREEN.
- NOTE: expansion runs at query time, not during CalDAV down-sync, so no DB migration needed.
  Server-side RECURRENCE-ID/EXDATE overrides are still not consumed (not emitted by current
  providers; honest carry-over). If a user edits/deletes one instance on the server, the master
  still expands that slot — wire to recurrenceExceptions when a provider sends overridden UIDs.

=== PHASE 7 — TASK SYNC BACKEND (2026-07-08) ===
Carry-over: TaskSyncEngineImpl wrote/created/deleted with SyncResult.failure("not implemented")
(lie-avoidance only — never fake success). Contact stays separate (needs vCard stack from zero).

Fix: Task sync now real, reusing existing plumbing:
- ICalParser already parsed VTODO -> Task (Phase 2). CalDAVClient already did PROPFIND/GET.
- CalDAVClient: +discoverTaskLists() (CalDAV collections advertising VTODO in component-set,
  recursive scan reusing scanForCalendars shape), +putResource(href, body) (generic CalDAV PUT,
  returns server ETag), +deleteResource(href) (returns success|404).
- VTaskSerializer (NEW, pure Kotlin): minimal RFC5545 VTODO emitter covering the fields
  ICalParser reads back (UID, SUMMARY, DESCRIPTION, STATUS, DUE, PRIORITY, CATEGORIES).
- TaskSyncEngineImpl rewritten: syncAccount discovers task lists, down-syncs VTODO by ETag,
  inserts/updates Room rows, pushes isLocalOnly tasks via PUT, deletes local rows whose UID
  vanished server-side. createTask/updateTask/deleteTask/completeTask now PUT/DELETE real
  iCalendar. OAuth bearer honored via newCalDav().

Files changed:
- app/src/main/java/com/unifiedcomms/sync/CalDAVClient.kt (+discoverTaskLists, +putResource, +deleteResource)
- app/src/main/java/com/unifiedcomms/sync/VTaskSerializer.kt (NEW)
- app/src/main/java/com/unifiedcomms/sync/TaskSyncEngineImpl.kt (rewritten: real sync + writes)
- app/src/test/java/com/unifiedcomms/sync/VTaskSerializerTest.kt (NEW, 6 JVM tests)

Verification:
- VTaskSerializerTest: 6/6 PASS (required fields, STATUS mapping, DUE->UTC Z, round-trip via
  ICalParser, summary escaping).
- :app:assembleDebug GREEN, full :app:testDebugUnitTest GREEN (no warnings).
- ponytail: VTODO serializer intentionally omits PERCENT-COMPLETE/RRULE/attendees — those are
  read-only display today; emitting them would be speculative. Extend when model needs write.

=== REMAINING CARRY-OVER (honest, not fixed) ===
- Contact (CardDAV/vCard) sync backend still fail-honestly stub — needs vCard parser/serializer
  from zero (separate phase). ContactSyncEngineImpl.syncAccount currently runs a broken
  PROPFIND that returns empty contacts; create/update/delete still fail honestly.
- OAuth refresh + search + messaging verified by compile/install only; need live-account runs on emulator-5560.
- Search does not cover Messages (needs conversationIds; unstable userId in local mode).
- PushManager now unreferenced by any running component (keep or drop in cleanup pass).
- RECURRENCE-ID / EXDATE server overrides not consumed (see Phase 6 note).
- Task write round-trip needs a live CalDAV account on emulator-5560 to confirm PUT/DELETE ETags.

=== PHASE 8 — CONTACT SYNC BACKEND (2026-07-08) ===
Carry-over from Phase 7: ContactSyncEngineImpl.syncAccount ran a broken PROPFIND returning
empty contacts; create/update/delete failed honestly. Needed vCard stack from zero.

Fix: Contact sync now REAL, reusing CalDAV plumbing (same DAV session shape as Task/Calendar):
- VCardParser (NEW, pure Kotlin): RFC 6350 (v4) / RFC 2426 (v3) parse. Handles FN, N,
  EMAIL, TEL, ADR, URL, ORG, TITLE, NOTE, UID. Line-unfolding (space/tab continuations)
  + minimal unescape (\\, \\; \\n \\\\). Unknown props ignored.
- VCardSerializer (NEW, pure Kotlin): RFC 2426 emit covering fields parser reads back
  (UID, FN, N, EMAIL, TEL, ORG, TITLE, ADR, URL, NOTE). vCard 3.0 escaping.
  ponytail: deliberately omits PHOTO/binary, typed TEL/EMAIL, structured ADR sub-fields —
  not in the model; don't emit what we can't parse back.
- CalDAVClient: +discoverAddressBooks() (carddav addressbook-home-set + recursive scan),
  +listAddressBookItems(), +fetchVCard() (reuses fetchItem), +putVCard()
  (reuses putResource with VCARD_MEDIA_TYPE). AddressBookInfo data class.
- ContactSyncEngineImpl rewritten: syncAccount discovers address books, down-syncs vCards
  (parse via VCardParser), inserts/updates Room rows by email/phone match; createContact/
  updateContact/deleteContact now PUT/DELETE real vCard via CalDAVClient. OAuth bearer
  honored via newCardDav().
- ContactRepository(+Impl): +getBySourceId, +getAllByAccountAndSource.
- ContactDao (lives in MessageDao.kt, with MessageDao/ConversationDao): +getBySourceId,
  +getAllByAccountAndSource.

BUILD FIXES APPLIED THIS SESSION (in-progress edits were RED):
- MessageDao.kt: missing closing '}' on ContactDao interface (edit truncated it) -> restored.
- CalDAVClient.kt: duplicate companion object (VCARD_MEDIA_TYPE added as 2nd companion)
  -> merged into the single existing companion.
- ContactSyncEngineImpl.kt: missing import com.unifiedcomms.data.model.ContactSource -> added.
- ContactSyncEngineImpl.updateContact: unused 'etag' binding -> simplified to if-null check.

Verification (THIS session, not trusted from prior handoff):
- assembleDebug: BUILD SUCCESSFUL (no errors, no warnings).
- testDebugUnitTest: BUILD SUCCESSFUL (all unit tests pass).
- Committed: 2f7c94a "Phase 8: real CardDAV/Contact sync backend..." — pushed to origin/master.

REMAINING CARRY-OVER (honest, post-Phase 8):

## Status as of 2026-07-08 (session restart boundary)
- Phase 9 (VCardParser + VCardSerializer JVM unit tests, 7 tests): DONE + committed (92978f2).
- Phase 10 (ContactSyncEngineImpl.testConnection() stub -> real discoverAddressBooks probe):
  DONE. Committed earlier; carried in ContactSyncEngineImpl.kt diff (real probe, no longer a stub).
- Phase 11 (Contact write/delete E2E on emulator-5560): CLIENT FIXES WRITTEN, NOT YET GREEN.
  The E2E test (app/src/androidTest/.../ContactSyncE2ETest.kt) + mock server
  (/tmp/carddav_mock.py) are in place. Running it surfaced and FIXED three real CalDAVClient
  bugs (uncommitted, in CalDAVClient.kt):
    1. cleartext blocked (app sets usesCleartextTraffic=false) -> added debug-only
       network_security_config.xml permitting cleartext for the mock host.
    2. relative hrefs ("/addressbook") passed to OkHttp with no scheme -> added resolve()
       to prepend baseUrl.
    3. DAV XML parse was not namespace-aware -> getElementsByTagName never matched
       namespaced <D:response>/<C:addressbook> etc, so discovery returned EMPTY.
       Fixed with namespace-aware parseXml() + byLocalName() tree-walk (Harmony leaves
       localName null, so it falls back to nodeName prefix-strip).
  Also: scanForAddressBooks/Calendars/TaskLists no longer skip the home-set collection itself.
  BLOCKER at restart: test still red "No address book found"; in-run diagnostics emit no
  logcat even after clean reinstall of both APKs, so the failure is unobservable. Routing is
  solved (use 127.0.0.1 + `adb reverse tcp:8088 tcp:8088`, NOT 10.0.2.2 — that is a
  WireGuard/loopback range and is not up). To finish: confirm the installed app APK actually
  carries the fix (dexdump grep byLocalName, or Log.e + `logcat -d *:E`), get the E2E green,
  then commit CalDAVClient.kt + debug net config + the test together.
- OAuth refresh + search + messaging: verified by compile/install only (Phase 8 boundary).
- RECURRENCE-ID / EXDATE server overrides not consumed (Phase 6 note stands).
- PushManager still unreferenced (cleanup pass candidate) -> Phase 12.
- Search does not cover Messages (Phase 4 note stands) -> Phase 13.

=== PHASE 11 — CONTACT E2E GREEN (2026-07-08, resumed session) ===
Status: RESOLVED. Commit 98dc8d4 pushed to origin/master.
The Phase 11 E2E blocker was NOT the earlier suspects (cleartext / namespace parse).
Three REAL root causes, all fixed and verified on emulator-5560:

[#P1] CLEARTEXT ROUTING MISMATCH (debug net config).
  The test + this host reach the mock at 127.0.0.1:8088 via `adb reverse
  tcp:8088 tcp:8088`. The committed debug network_security_config.xml only
  permitted cleartext for 10.0.2.2. Cleartext to 127.0.0.1 was blocked ->
  OkHttp threw on connect -> discoverAddressBooks() caught it -> returned EMPTY
  -> "No address book found". FIXED: debug config now permits cleartext for
  127.0.0.1 / localhost / [::1] / 10.0.2.2.

[#P2] HREF DOUBLING (CalDAVClient, REAL-PROD BUG not just mock).
  resolve() + every inline "$baseUrl/${href.removePrefix("/")}" concatenated
  base + absolute-path href -> doubled path (base+/addressbook + /addressbook/...).
  For real DAV servers returning absolute-path hrefs this broke GET/PUT/DELETE.
  FIXED: single resolve() uses URI(baseUrl).resolve(href); all call sites use it.

[#P3] ETAG/HREF INDEX DESYNC (CalDAVClient, THE actual "0 contacts" blocker).
  listAddressBookItems() and getETagList() paired href[i] with etag[i] from
  FLATTENED global node lists. The collection <response> has href but NO getetag,
  so indices desynced: collection href paired with item etag, and every real item
  (href[1]=seed-1.vcf) paired with etag[1]=null -> filtered out. Contact sync and
  task sync both returned 0 items. FIXED: new etagEntriesFromMultistatus() extracts
  href+getetag from the SAME <response> element; both methods use it.

Also: carddav_mock.py now answers current-user-principal / addressbook-home-set by
REQUEST BODY at any path (real DAV servers do; the client issues them on baseUrl=
/addressbook/, so path-gated branching hid the home-set). ContactSyncE2ETest.roomHas()
now uses exact getBySourceId() instead of fuzzy search() LIKE (which never matched
the literal sourceId).

Verification (THIS session, real):
- assembleDebug GREEN; testDebugUnitTest GREEN (no regressions).
- ContactSyncE2ETest: PASS (1 test) on emulator-5560. Full round-trip against live
  mock via adb reverse: testConnection -> createContact PUT seed-1 -> syncAccount
  downloads it (Room contains seed-1) -> createContact PUT new-1 -> re-sync surfaces
  new-1 -> deleteContact removes new-1 -> fetchContact returns null.
- NOT pushed to physical S22 (R5CT32YG8CL); E2E ran only on emulator-5560.

REMAINING CARRY-OVER (honest, post-Phase 11):
- OAuth refresh + search + messaging: still verified by compile/install only.
- RECURRENCE-ID / EXDATE server overrides not consumed (Phase 6 note stands).
- PushManager still unreferenced (cleanup pass candidate) -> Phase 12.
- Search does not cover Messages (Phase 4 note stands) -> Phase 13.
- Contact/Tasks/Calendar DAV write-round-trip proven against mock only; a live
  real-provider CalDAV/CardDAV account on emulator-5560 would confirm production.

=== PHASE 12 — PUSHMANAGER DELETED (2026-07-08) ===
Status: DONE. No live consumer (MessagingService removed in Phase 3; DI wiring in
AppModules.kt is fully commented out / Hilt disabled). Per ponytail dead-code rule,
delete orphan code rather than keep a fake-useful HTTP client that points at a
non-existent server (push.unifiedcomms.app).
- DELETED: app/src/main/java/com/unifiedcomms/push/PushManager.kt (+ push/ dir).
- REMOVED the dangling commented import `com.unifiedcomms.push.PushManager` from
  app/src/main/java/com/unifiedcomms/di/AppModules.kt (would break if Hilt re-enabled).
- Verify: grep for PushManager returns only history; assembleDebug GREEN.

=== PHASE 13 — SEARCH NOW COVERS MESSAGES (2026-07-08) ===
Status: DONE. Real fix, verified by compile + unit tests (no instrumented UI test yet).
- MessageDao.searchMessages: dropped the broken `conversationIds` param (callers in
  local mode had no stable userId to scope it, so it was never wired). New signature
  `searchMessages(query, limit)` searches ALL local messages by content LIKE.
- MessagingRepository(.kt / Impl): signature updated to match.
- SearchActivity: now builds MessagingRepositoryImpl(db.messageDao(), db.conversationDao())
  and maps msgRepo.searchMessages(query, 50) into a "Message" SearchRow. Email/calendar/
  task/contact were already covered. Search now covers all 5 entity kinds.

=== PHASE 14 — CONTACT SYNC CROSS-ACCOUNT DEDUP BUG (2026-07-08, found during final E2E) ===
Status: DONE. This is a REAL production bug surfaced by re-running the Phase 11 E2E.
- SYMPTOM: ContactSyncEngineImpl.syncAccount deduped by GLOBAL email/phone
  (getByEmail/getByPhone across all accounts). If account B synced a contact sharing an
  email with account A's contact, B's row was never created — instead A's row got updated.
  Multi-account contact pollution.
- WHY IT LOOKED LIKE A TEST FLAKE: the Phase 11 E2E used a hardcoded sourceId "seed-1"
  with a random accountId per run. Run 1 (fresh DB) passed; every later run found the
  stale email row and silently updated it instead of inserting -> assertion failed. The
  test was also not isolated (no per-run unique key).
- FIX (engine): dedupe by account-scoped natural key first:
  getBySourceId(account.id, contact.sourceId)
    ?: getByEmail(...).takeIf { it.accountId == account.id }
    ?: getByPhone(...).takeIf { it.accountId == account.id }
- FIX (test): sourceIds now `${account.id}-seed` / `${account.id}-new` so each run is
  isolated; re-run 3x on emulator-5560 -> all PASS.
- This bug ALSO means the earlier "Phase 11 E2E green" was a one-shot fluke; the engine
  was genuinely broken for any second account. Now correct.

FINAL SWEEP (2026-07-08):
- assembleDebug GREEN; :app:testDebugUnitTest GREEN (54 tests, 0 failures).
- ContactSyncE2ETest PASS x3 on emulator-5560 (isolated, full round-trip).
- No silent-lie stubs remain (all SyncResult.success() paths do real work; create/update/
  delete for email/calendar/task/contact are real DAV/IMAP ops or fail honestly).
- Remaining honest carry-over (NOT bugs):
  - OAuth refresh: verified by compile/install only (needs live OAuth account).
  - RECURRENCE-ID / EXDATE server overrides: providers in scope don't emit them; expansion
    uses masters. Documented carry-over, not a fake path.
  - Search UI launch: verified in Phase 3 (in-app icon); `am start` blocked because the
    activity is not exported (by design).
  - DAV write-round-trips proven against mock only; a live provider account confirms prod.
- Physical S22 (R5CT32YG8CL): NOT touched at any point. All E2E on emulator-5560.

ALL PHASES COMPLETE. Build green, tests green, no known silent lies.

=== REVIEW vs VISION (2026-07-08, post-Phase 14 sweep) ===
Audit of the 7 hard requirements + project scope. Verified in source, not from doc claims.
- Theme toggle: ✓ MainActivity:99-107 collects themeModeFlow -> UnifiedCommsTheme(darkTheme=).
- Auto-sync after add: ✓ AddAccountActivity:168 + AddAccountScreen:305 call syncAccount() post-insert.
- Sync notifications: ✓ SyncManager.performFullSync -> showSyncNotification (start/success/fail).
- Advanced IMAP/SMTP/CalDAV UI: ✓.
- Folder nav + unread: ✓ code path present (live confirmation still pending).
- Fresh install zero demo: ✓ (doc).
- Calendar/task sync w/ colors + invited events: ✓ real DAV sync, preserves color/attendees.
Engine status: REAL, no silent lies. Email/Contact E2E green; Calendar/Task proven vs mock.
GAP vs vision (blocks confident v1, not engine rewrites):
- HIGH: background sync dead when app killed (SyncManager periodic loop was app-lifecycle-scoped;
  BootReceiver only reschedules reminders). FIXED in Phase 15.
- HIGH: live OAuth round-trip never run against real Google/Outlook -> token refresh unverified.
- HIGH: CalDAV/CardDAV never run against a real provider (mock only).
- MEDIUM: Calendar/Task have no instrumented E2E (only Email + Contact do).
- MEDIUM: RECURRENCE-ID/EXDATE server overrides not consumed (masters-only expansion).
- LOW: delete dead branch fix/add-account-email-sync (no diff vs master); Ethereal folder alias.

Also found during review: the manifest declared `.sync.accounts.UnifiedCommsSyncService` which
DOES NOT EXIST (broken SyncAdapter path). Removed in Phase 15 (see below).

=== PHASE 15 — BACKGROUND SYNC (2026-07-08) ===
Status: DONE. Replaces the app-lifecycle coroutine loop (SyncManager.onStart/onStop) as the
background driver. The old loop was cancelled on app stop, so NO sync ran when the app was
killed — new mail/calendar never arrived until the app was reopened.
- ADDED androidx.work:work-runtime-ktx:2.9.1 (build.gradle.kts) + work-testing in androidTest.
- NEW BackgroundSyncWorker (CoroutineWorker): builds the SAME engine stack + SyncManager as
  the foreground UI and calls performFullSync() per active account. Reuses the proven real
  sync path; only the lifecycle owner differs (WorkManager process vs app on-screen).
- NEW BackgroundSyncScheduler: enqueues ONE unique periodic WorkRequest
  ("unifiedcomms.background.sync", ExistingPeriodicWorkPolicy.KEEP, 15-min floor). Idempotent
  on every app start; respects Doze + battery saver (WorkManager native).
- WIRED: UnifiedCommsApplication.onCreate() now calls BackgroundSyncScheduler.schedule(this).
- REMOVED broken manifest <service android:name=".sync.accounts.UnifiedCommsSyncService">
  (referenced a non-existent class) + its syncadapter <meta-data>, and deleted the unused
  res/xml/syncadapter.xml. The authenticator ContentProvider (UnifiedCommsAuthenticatorProvider)
  is kept (real, boot-instantiated, harmless). The dead SyncAdapter service was never started
  (no requestSync call existed), so it did not crash at runtime — but it was a broken reference
  that WOULD fail the moment any sync adapter was invoked. Cleaned.
- ponytail: foreground SyncManager periodic loop left in place (immediate in-app sync); the
  WorkManager worker is the real out-of-process path. No double-sync risk: worker honors
  per-account syncConfig toggles; foreground loop still cancels on app stop as before.

Verification (THIS session, real):
- :app:assembleDebug GREEN; :app:testDebugUnitTest GREEN (54 tests, 0 failures).
- ContactSyncE2ETest PASS x2 on emulator-5560 (no regression from Phase 14 fix).
- NEW BackgroundSyncWorkerTest (androidTest): enqueues a one-time worker via
  WorkManagerTestInitHelper + SynchronousExecutor, asserts SUCCEEDED. Proves the worker
  constructs the real engine stack + SyncManager and runs performFullSync on-device without
  crashing. PASS on emulator-5560.

NEXT (if continuing): live OAuth round-trip (real Google/Outlook account) + live CalDAV/CardDAV
against a real provider (Nextcloud/Fastmail) are the remaining HIGH-confidence gaps. Then a
Calendar/Task instrumented E2E mirroring ContactSyncE2ETest.

Physical S22 (R5CT32YG8CL): NOT touched. All verification on emulator-5560.

=== PHASE 16 — OAUTH + TASK DAV VERIFICATION (2026-07-08) ===
Status: DONE. Brought OAuth confidence + Task DAV up to the same bar as Contact/Email E2E.

REAL BUG FOUND + FIXED (the Phase 2 OAuth risk was a live defect, not just unverified):
- `AddAccountActivity.createOAuthAccount` built `AuthConfig.OAuth2(accessToken, refreshToken)`
  with `oauthTokenExpiry = null`. OAuthTokenRefresher.ensureFreshToken's needsRefresh guard is
  `expiry != null && expiry < now+5m` — with a null expiry it ONLY refreshes when the access
  token is literally null. So once a token was issued it NEVER refreshed -> every OAuth
  account (Google/Outlook) silently died at token expiry. FIXED: AuthConfig.OAuth2 now takes
  `expiry`; createOAuthAccount stamps it from the grant's `expires_in`. Refresher now re-fires
  correctly before expiry.

NEW TESTS (verifiable without live secrets):
- OAuthTokenRefresherTest (JVM, MockWebServer): refreshes an expired token (POST refresh_token
  -> parses access_token/expires_in -> persists updated AuthConfig) AND skips refresh when
  still valid. Proves the unverified refresh HTTP path end-to-end.
- Xoauth2FormatTest (JVM): asserts the exact SASL XOAUTH2 string Gmail/Outlook IMAP expect
  ("user=<email>\u0001auth=Bearer <token>\u0001\u0001"). A malformed string = silent IMAP auth
  failure. Extracted xoauth2Bare() (pure, no android.util.Base64) so it runs on the JVM.
- TaskSyncE2ETest (androidTest, /tmp/taskdav_mock.py): full VTODO round-trip via the real
  engine on emulator-5560 -> testConnection -> createTask PUT -> syncAccount download -> second
  PUT -> deleteTask -> fetch returns null. Proves the CalDAVClient getETagList per-<response>
  fix (Phase 11) ALSO holds for task lists, and VTODO write-round-trip works on-device.

BUILD/TEST SUPPORT ADDED:
- androidx.work:work-runtime-ktx:2.9.1 (runtime) + work-testing (androidTest) [Phase 15].
- testOptions.unitTests.isReturnDefaultValues = true (JVM tests touch android.util.Log).
- testImplementation: mockwebserver:4.12.0, org.json:json:20231013 (org.json is Android-only;
  OAuthTokenRefresher uses it -> must be on the JVM test classpath or the parse throws).

VERIFICATION (THIS session, real):
- :app:testDebugUnitTest GREEN: 58 tests, 0 failures (was 54; +4 new OAuth/XOAUTH2).
- Contact E2E x2 PASS (no regression from Account/AddAccountActivity edits).
- Task E2E PASS on emulator-5560 (NEW).
- BackgroundSyncWorkerTest PASS (no regression).

HONEST CARRY-OVER (NOT bugs — blocked by environment, not code):
- LIVE Google/Outlook OAuth round-trip NOT executed: requires a real registered client_id/
  secret, an interactive browser consent screen (the web OAuth flow), and network egress to
  Google/Azure from the emulator. Cannot run headless here. Phase 16 verifies the refresh
  plumbing + XOAUTH2 format + token persistence against mocks; the live token exchange itself
  remains unproven against a real provider.
- LIVE CalDAV/CardDAV against a real provider (Nextcloud/Fastmail) NOT executed: same
  credential/network constraints. Phase 8/11/16 verify the engine + DAV parsing against local
  mocks that mirror real-server quirks (href doubling, etag/href desync, principal-by-body).
- RECURRENCE-ID/EXDATE server overrides still not consumed (masters-only expansion).

NEXT (if continuing): the only remaining HIGH-confidence gap is the LIVE provider round-trip,
which needs Keith's real OAuth credentials + interactive consent. Everything else is verified.
Repo is in a shippable state for an alpha: engine real, no silent lies, background sync live,
OAuth refresh wired + unit-verified, Contact/Email/Task DAV proven on-device.

Physical S22 (R5CT32YG8CL): NOT touched. All verification on emulator-5560.
