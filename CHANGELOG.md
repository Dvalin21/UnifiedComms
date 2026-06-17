# Changelog

All notable changes to UnifiedComms will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0-alpha] - 2024-12-19

### Added
- **Multi-account Email Sync**
  - Google (OAuth 2.0), Microsoft/Outlook (OAuth 2.0), Yahoo (OAuth 2.0), iCloud (OAuth 2.0)
  - Exchange/Office 365 (OAuth 2.0 + EWS)
  - Mailcow (manual IMAP/SMTP/CalDAV/CardDAV)
  - Generic IMAP/SMTP with custom server config
  - Generic CalDAV/CardDAV
  - Unified inbox with color-coded account indicators
  - Push notifications (IDLE for IMAP, webhooks for Exchange/Gmail)
  - Full folder sync: INBOX, Sent, Drafts, Trash, Spam, Archive, custom folders
  - Attachments: download, preview, auto-download toggle
  - Threading/conversation view
  - Flags: read/unread, starred, answered, forwarded
  - Search across all accounts (subject, sender, body)

- **Calendar Sync**
  - Google Calendar API, CalDAV, Exchange EWS, iCloud CalDAV
  - Shared calendars fully supported
  - **Event colors preserved exactly as on server**
  - Multiple views: Month, Week, Day (6 AM - 10 PM hourly)
  - Event creation with:
    - Title, description, location, start/end, all-day
    - Calendar color picker (18 Material colors)
    - Attendees with email invites
    - Reminders (default 1 hour, configurable)
    - Recurrence: Daily, Weekly, Monthly, Yearly, Custom RRULE
  - Invite responses: **Yes / No / Maybe** buttons in notification and chat
  - Time zone handling for travel
  - Search events by title, location, description

- **Tasks**
  - CalDAV VTODO, Google Tasks API
  - Multiple lists per account (Personal, Work, Shopping, custom)
  - Subtasks with progress tracking (X/Y, percentage)
  - Due dates with reminders
  - Recurring tasks
  - Priority levels: Low, Normal, High, Urgent (color-coded)
  - Categories/tags
  - Drag-to-reorder, swipe actions
  - Filters: All, Active, Completed, Starred, Overdue, Today

- **Messaging (Inter-app, UnifiedComms users only)**
  - End-to-end encryption: AES-256 + RSA-4096 key exchange
  - Direct messages (1:1), Group chats, Broadcast
  - Rich sharing in chat:
    - Calendar invites with Yes/No/Maybe response buttons
    - Task sharing with due date, priority
    - Email sharing with preview
  - Disappearing messages (per-conversation timer)
  - Read receipts (delivered/read)
  - Voice messages
  - Push notifications via FCM/WebPush
  - Per-conversation mute, pin, archive

- **Widgets (Glance)**
  - **Email Widget**: Per-account unread badges, quick Compose/Inbox
  - **Calendar Widget**: Today's events with colored time blocks, New Event
  - **Tasks Widget**: Progress ring, checkable tasks with priority bars
  - **Unified Widget**: Three-panel design (Calendar + Tasks + Quick Actions)
  - Configurable sizes (2×2 to 5×5), accounts, filters
  - Auto-refresh every 30 minutes + on app sync
  - Dark/light theme aware

- **Reminders**
  - **Full-screen alerts** (1 hour default before events)
  - Shows over lock screen, turns screen on
  - Actions: Snooze 5min, Dismiss, View Event
  - Also creates standard notification as backup
  - Default reminder time configurable (At time, 5/15/30 min, 1 hour, 1 day)

- **Security & Privacy**
  - **Zero telemetry**: No analytics, crash reporting, usage tracking
  - **Local-first**: All data stored locally in encrypted Room database (SQLCipher)
  - **Biometric lock**: Fingerprint/Face ID with configurable auto-lock timeout
  - **AES-256 encryption** at rest (Android Keystore, hardware-backed)
  - **Certificate pinning** for all known providers
  - **App password support** for services requiring it
  - **E2E encrypted messaging** (keys never leave device)
  - Data control: Clear All Data, Backup & Restore (encrypted export/import)

- **UI/UX**
  - Material 3 (Material You) with dynamic theming
  - Account color palette (18 Material colors assigned per-account)
  - Dark/Light/System theme
  - Responsive: Phones (6.5") to Tablets (14.5") with adaptive layouts
  - Accessibility: TalkBack, large text, high contrast
  - Smooth animations, shared element transitions

- **System Integration**
  - Android Account Authenticator (system account manager)
  - Sync Adapter (system sync framework)
  - ContentProvider for data sharing
  - Foreground sync service with progress notification
  - Boot receiver for auto-start
  - WorkManager-ready for background sync

- **Build & CI**
  - Gradle 8.5, Kotlin 1.9, Compose BOM 2024.06
  - Hilt 2.50, Room 2.6, Glance 1.1
  - GitHub Actions: lint (ktlint, detekt), test, build debug/release
  - ProGuard/R8 full mode for release
  - BuildConfig fields for OAuth credentials (env vars)

### Security
- Network Security Config: cleartext disabled, cert pinning for known providers
- EncryptedSharedPreferences for all sensitive prefs
- Data extraction rules: exclude database, auth tokens, encryption keys, attachments
- Backup rules: exclude all sensitive data

### Documentation
- Comprehensive README with feature usage guide for all modules
- MIT License
- GitHub Actions CI workflow

---

## [Unreleased]

### Planned
- PGP/GPG email encryption support
- CalDAV scheduling (iTIP) for full invite workflow
- CardDAV sync for contacts (photos, groups)
- Message reactions, threads, search
- Widget configuration activities (per-widget settings UI)
- Tablet-optimized layouts (two-pane, drag-drop)
- Export/import account settings
- Backup to local file / cloud (encrypted)
- Conversation pinning with custom emoji
- Message translation (on-device)
- Smart replies (on-device ML)
- Calendar heat map view
- Task Kanban board view
- Unified search across all data types