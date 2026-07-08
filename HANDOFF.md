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

Remaining before ship
- Live functional verification on emulator-5560 (add account, send/receive, sync, reminders).
- Add automated UI/instrumented tests for core flows.

Emulator artifacts
- APK: app/build/outputs/apk/debug/app-debug.apk
