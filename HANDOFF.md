# UnifiedComms - Production Handoff (Review Pass)
**Working directory:** `~/host/UnifiedComms/`
**Local HEAD:** `6026695`
**Branch:** master  
**Build:** `BUILD SUCCESSFUL :app:compileDebugKotlin` and `:app:assembleDebug`

**Install state on this host:** FAILED. ADB-attached device is Android 11 (API 30) while the code targets API 31+. Valid debug APK: `/home/keith/host/UnifiedComms/app/build/outputs/apk/debug/app-debug.apk` — installable only on a device running API >=31.

## Verified State (2026-06-24)

### Operating Store
- `EmailSyncEngineImpl.kt`: IMAP/SMTP path exists.
- `CalendarSyncEngineImpl.kt`: account-aware CalDAV PROPFIND discovery; compiles.
- `ContactSyncEngineImpl.kt`: account-aware CardDAV discovery; compiles.
- `TaskSyncEngineImpl.kt`: account-aware CalDAV discovery for task lists; compiles.
- `SyncManager.kt`: aggregates per-engine failures instead of aborting all-of-sync.
- `Account.kt`: Google/Exchange creation uses `AuthConfig.Password(...)` (no OAuth 2.0).
- `AndroidManifest.xml`: duplicate READ/WRITE_SYNC_SETTINGS removed.

### Completed Work
- Rebased ViewModel wiring for `CalendarScreen`, `TasksScreen`, `MessagesScreen`, `UnifiedInboxScreen`, `EmailScreen`.
- Replaced mock state with repository-backed flows where the repo surface already exists (`Email`, `CalendarEvent`, `Task`, `Conversation`).
- Removed dead mock helpers and stale `MockEvent`/`MockTask` conversion paths.
- Updated `MainActivity` nav routes to pass `viewModel` into composable screens.
- Removed duplicate import block and unresolvable duplicate symbols from `EmailScreen.kt`; verified clean compile after cleanup.
- Synced sync-engine call sites to use account-aware methods.
- Preserved `SearchActivity` as intentional no-op until a real search backend is wired; not dead code, not misfeature.

### Known Remaining Warnings
- `TaskSyncEngineImpl.kt:116` unused parameter `idx`
- `CalendarScreen.kt:345` unused parameter `eventId`
- `EmailScreen.kt:73` unused variable `deleteTarget`
- `MessagesScreen.kt:72` unused parameter `onNavigateToCreateEvent`
- `MessagesScreen.kt:77` unused variable `coroutineScope`
- `MessagesScreen.kt:218` delicate API usage (`collectAsStateWithLifecycle`)
- `MessagesScreen.kt:414` unused parameter `conversationId`

### Edison Mail Competitive Baseline (target feature set)
- Unified inbox across unlimited accounts
- Fast IMAP/Exchange/IMAP fetch
- Smart inbox classification: subscriptions, travel, bills, packages, entertainment
- One-tap unsubscribe header parsing
- Sender blocking + phishing protection
- Swipe action customization; custom templates; Focused Inbox
- Device-only storage; no ads; no tracking pixels
- Premium upsell avoided here; all features will stay free/open by design
