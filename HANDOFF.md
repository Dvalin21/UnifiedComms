# UnifiedComms - Production Handoff (Review Pass)
**Working directory:** `~/host/UnifiedComms/`
**Local HEAD:** `2978ae2`
**Branch:** master  
**Build:** `BUILD SUCCESSFUL :app:compileDebugKotlin` and `:app:assembleDebug`

**Install state on this host:** FAILED. ADB-attached device is Android 11 (API 30) while the code targets API 31+. Valid debug APK: `/home/keith/host/UnifiedComms/app/build/outputs/apk/debug/app-debug.apk` — installable only on a device running API >=31.
---

## Verified State (2026-06-24)

### Operating Store
- `EmailSyncEngineImpl.kt`: IMAP/SMTP path exists.
- `CalendarSyncEngineImpl.kt`: account-aware CalDAV PROPFIND discovery; compiles.
- `ContactSyncEngineImpl.kt`: account-aware CardDAV discovery; compiles.
- `TaskSyncEngineImpl.kt`: account-aware CalDAV discovery for task lists; compiles.
- `SyncManager.kt`: aggregates per-engine failures instead of aborting all-of-sync.
- `Account.kt`: Google/Exchange creation uses `AuthConfig.Password(...)` (no OAuth 2.0).
- `AndroidManifest.xml`: duplicate READ/WRITE_SYNC_SETTINGS removed.

### Broken / Deferred / Missing
- ServerDefaults configuration (`ServerConfig.GoogleDefaults()`, `ServerConfig.ExchangeDefaults()`, and other providers) not fully aligned with actual host constants.
- `fetchTasksFromServer(account)` emits stub tasks only; no real VTODO parsing.
- `fetchEventsFromServer(account)` emits stub events only; no real iCalendar parsing.
- `fetchContactsFromServer(account)` emits stub contacts only; no real vCard/CardDAV parsing.
- HKDF-SHA256 at-rest encryption (`CryptoManager.kt`) is stashed, not applied.
- Biometric lock enforcement (`MainActivity.kt`) is stashed, not applied.
- TODO/FIXME/NotImplementedError markers remain across sync engines and UI.
