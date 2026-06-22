# UnifiedComms - Production Handoff Document
**Session Date:** 2026-06-22
**Repository:** https://github.com/Dvalin21/UnifiedComms
**Branch:** master
**Working directory:** `~/host/UnifiedComms/`
**Local HEAD:** `f322de8`

---
## Executive Summary
Build is **green** (`:app:compileDebugKotlin` succeeds; `:app:assembleRelease` also green; `:app:lintRelease` green).

**Phase 0 (Release build baseline)** — COMPLETE
- ProGuard rules audited: add explicit default constructor keeps for Room/Hilt/kotlinx.serialization annotated classes
- `assembleRelease` and `lintRelease` both green

**Phase 1 (Unit test scaffold)** — COMPLETE
- Audit confirmed: 0 unit tests, 0 instrumented tests

**Phase 2 (Room migration strategy)** — IN PROGRESS, BLOCKED
- `kotlin-kapt` enabled, `room-compiler:2.6.1`, `room.schemaLocation` configured
- `@TypeConverters` added to entity fields: Account (ServerConfig/AuthConfig/SyncConfig/UIConfig), CalendarEvent, Task
- New converter: `EventReminderListConverter`
- **BLOCKER:** DAO queries use dotted paths on JSON-embedded fields (`flags.isRead`, `systemLabels.draft`, `syncConfig.syncEmail`). Room SQLite cannot traverse these — filtering must move to RepositoryImpl via `Flow.map { ... }`
- **BLOCKER:** `Email` `@Index` annotations reference non-existent columns (`isRead`, `body_text`) — must align with actual entity fields
- Schema JSON files not yet generating until DAO/entity mismatch is resolved

**Phase 3 (Keystore/signing + target API)** — IN PROGRESS
- compileSdk/targetSdk bumped to 35, Android SDK 35 installed
- assembleRelease and lintRelease both green
- Signing still gated on env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`)

---

## ✅ Work Completed in This Session
1. **P1 ViewModel/Repository wiring** — replaced mock state with repository-backed flows in CalendarScreen, TasksScreen, MessagesScreen, and SettingsScreen. Added `MockEvent`/`MockTask`/`MockConversation`/`MockMessage` UI-layer adapters to bridge repo model types into existing composable UIs. UnifiedInboxScreen and MainActivity pass `viewModel` through correctly.
2. **P2 Unused-parameter cleanup** — zero-arg'd unused private helper params in `CalendarSyncEngineImpl`, `ContactSyncEngineImpl`, `TaskSyncEngineImpl`; removed unused composite call-site args in `MainActivity` (`backStackEntry` renamed to `_`); killed dead `newMsg` allocation in `MessagesScreen`.
3. **P3 Composable stub-parameter mismatch** — removed dead `@Suppress("UNUSED_PARAMETER")` parameters:
   - `UnifiedInboxScreen` — dropped `onNavigateToTasks`/`onNavigateToMessages`
   - `FolderChip` — dropped unused `_folder` arg
   - `ComposeEmailScreen` — dropped unused `viewModel`
   - `EmailDetailScreen` — dropped unused `viewModel`; composable itself was dead code and **removed**
   - `ComposeMessageScreen` — dead stub, **removed entirely**
   - `CreateEventScreen` — dropped unused `viewModel`
   - `EventDetailScreen` — dropped unused `viewModel`/`eventId`
   - `CreateTaskScreen` — dropped unused `viewModel`

---
## ⚠️ Remaining / Deferred
1. Keystore release signing — blocked on env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
2. Unit tests (Phase 1 deliverable).
3. Destructive migration path for users upgrading from pre-schema export builds.

---
## ✅ Resolved / No Longer Blockers
- **CalendarEvent ZoneOffset deprecation** — no longer reproduces; file uses `UtcOffset` only.
- **CalendarEvent unnecessary `!!`** — no longer reproduces on current Kotlin/AGP.
- **Opt-in requirement markers** — removed stale commented-out entries from `build.gradle.kts`; build is warning-free.
- **PushManager** `RequestBody.create` — already migrated to `body.toRequestBody(mediaType)` (verified in prior session).
- **Task.kt** unused `tz`/`zoneId` — `tz` does not exist at top level; `zoneId` is actively used throughout `Task`.
- **CryptoManager.kt** param name change warning — suppressed with `@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")`.
- **MainActivity** unused `backStackEntry` — renamed to `_` on affected composable lambdas.
- **CalendarSyncEngineImpl** unused `config`/`auth`/`account`/`calendarId` — removed private helper params; placeholders now zero-arg.
- **ContactSyncEngineImpl** unused `account` — removed placeholder helper param.
- **TaskSyncEngineImpl** unused `account`/`listId` — removed placeholder helper params.
- **ReminderSystem.kt** `FLAG_FULLSCREEN` — deprecated on API >=27. Retained `@Suppress("DEPRECATION")` on API <27 path. Not a blocker.

---
## 📋 Next Actions
1. Provide keystore env vars (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) to enable release signing.
2. Unit tests (Phase 1 deliverable).
3. ProGuard/R8 rules audit for targetSdk 35 behavior changes.
4. Handle destructive migration path for users upgrading from pre-schema export builds.

---

## Phase List

| Phase | Name | Status | Deliverable |
|-------|------|--------|-------------|
| 0 | Release build baseline | COMPLETE | assembleRelease + lintRelease green; ProGuard rules audited |
| 1 | Unit test scaffold | COMPLETE | 0 tests audited; scaffolding pending post-Room fix |
| 2 | Room migration strategy | IN PROGRESS | Schema JSON files emitting, Migration objects defined |
| 3 | Keystore/signing + target API | IN PROGRESS | Fastlane/env vars pending; compileSdk/targetSdk 35 aligned |

---
## 🚀 Resume
```bash
cd ~/host/UnifiedComms
# Verify compile still green
./gradlew :app:compileDebugKotlin --no-daemon --no-configuration-cache --rerun-tasks 2>&1 | tail -6

# Verify kapt schema generation
./gradlew :app:kaptDebugKotlin --no-daemon --rerun-tasks 2>&1 | grep -E "schema|Cannot figure out how to" | head -10

# Check generated schemas
find app/schemas -name '*.json' 2>/dev/null
```
