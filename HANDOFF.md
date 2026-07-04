UnifiedComms — verified handoff state
Generated: 2026-07-04
Verified by: assembleDebug clean --no-build-cache --no-configuration-cache --rerun-tasks on 2026-07-04
Latest commit: 21508c9 test: fix TaskRepositoryImplTest getOverdueUnified stub after ActiveUnified path change

Clean verified build/test
- assembleDebug: BUILD SUCCESSFUL
- Last log: /tmp/uc-build11.log
- Current compile state: green, warnings only
  - TaskSyncEngineImpl.kt:116 parameter unused
  - CalendarScreen.kt:345 parameter unused
  - EmailScreen.kt:73 variable unused
  - MessagesScreen.kt:72/77/419 unused params

Installed APK/runtime
- Build: app/build/outputs/apk/debug/app-debug.apk
- Device: unverified — runtime walkthrough not completed on 2026-07-04
- Note: /tmp/uc-build11.log is the verified build baseline; if further changes are made, rerun clean assembleDebug and refresh this block.

Resolved since last handoff
- Replaced invalid JSON IN queries with JSON1 EXISTS checks in MessageDao
- Replaced broken SQLite date() checks on TEXT JSON fields with Kotlin-side day/week-range filtering in CalendarRepositoryImpl and TaskRepositoryImpl
- Fixed SyncManager StateFlow immutable-copy write path
- Added BASIC auth basic-header support in CalendarSyncEngineImpl
- Added stable calendar identity and server auth in calendar discovery
- Added Message-ID-based IMAP UID mapping in EmailSyncEngineImpl
- Fixed EmailScreen compose sender/recipient wiring; added CC/BCC
- Fixed MainViewModel divide-by-zero with no active accounts
- Fixed ReminderSystem PendingIntent lifecycle leak
- Routed reminder openEventDetail action back to MainActivity
- Replaced broken .transform{} on StateFlow progress emission with explicit projection paths in CalendarSyncEngineImpl and TaskSyncEngineImpl
- Updated TasksScreen to preserve filter after toggle refresh
- Updated CalendarScreen to refresh after reminder action
- Fixed TaskRepositoryImplTest stale getOverdueUnified stub mismatch against current ActiveUnified-backed production path
