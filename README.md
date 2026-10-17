# UnifiedComms

[![Android](https://img.shields.io/badge/Android-12%2B-green.svg)](https://developer.android.com/about/versions/12)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.6-orange.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Releases](https://img.shields.io/github/v/release/Dvalin21/UnifiedComms?label=Latest%20Release)](https://github.com/Dvalin21/UnifiedComms/releases)
[![Komi Store](https://img.shields.io/badge/Get%20it%20on-Komi%20Store-2196F3.svg)](https://github.com/kurikomi-labs/komi-store)

> **A unified communication app for Android** — sync email, calendar, tasks, and messages across multiple accounts. Zero telemetry. Local-first. Encrypted by default.

---

## 🎯 Philosophy

**Data structures first.** Everything flows from the models: `Account`, `Email`, `CalendarEvent`, `Task`, `Message`, `UnifiedContact`. No telemetry, no tracking, no data leaves your device without explicit consent.

---

## ✨ Features Overview

| Feature | Providers | Key Capabilities |
|---------|-----------|------------------|
| **📧 Email** | Mailcow (IMAP/SMTP + CalDAV/CardDAV) | Unified inbox, attachments, threading flags, server-side folder list |
| **📅 Calendar** | Mailcow (CalDAV) | Shared calendars, color preservation, invites (Yes/No/Maybe), RRULE recurrence |
| **✅ Tasks** | Mailcow (CalDAV VTODO) | Priorities (Low/Normal/High/Urgent), due dates, recurring |
| **🔒 Security** | — | Biometric lock, AES-256 for stored credentials (Android Keystore), zero telemetry |
| **🔔 Reminders** | AlarmManager | Full-screen heads-up alerts with snooze/dismiss |

> **Scope note.** Mailcow is currently the only provider wired into the Add
> Account screen. Google, Microsoft, Yahoo, iCloud and generic IMAP/CalDAV are
> not implemented; see `docs/` for the research on what they would need.

---

## 🔒 Privacy & Security

- **Zero telemetry / zero analytics.** The app ships no tracking, analytics, or crash-reporting SDKs (no Firebase, Crashlytics, Sentry, Mixpanel, etc.). A "No Telemetry" toggle exists in Settings and is on by default.
- **Local-first.** Account data, credentials, and sync state live on your device. Nothing is sent to a UnifiedComms server — the app talks only to the email/calendar/task providers you configure.
- **Encrypted credentials.** Stored credentials are protected with AES-256 in the Android Keystore.
- **Secure transport.** Cleartext HTTP is disabled; all network traffic uses HTTPS (enforced via network security config).
- **Biometric lock.** Optional biometric / device-credential unlock for the app.
- **Open source.** MIT-licensed. Builds reproducible from source; no proprietary dependencies.

---

## 🚀 Quick Start

### Prerequisites
- Android 12+ (API 31)
- Kotlin 1.9+, Gradle 8.9+, Java 17
- Android SDK 35

### Build

```bash
git clone https://github.com/Dvalin21/UnifiedComms.git
cd UnifiedComms

# Copy and configure local properties
cp local.properties.example local.properties
# Edit local.properties with your SDK path and signing keys

# Set OAuth credentials (environment variables or local.properties)
export GOOGLE_CLIENT_ID="your_google_client_id"
export MICROSOFT_CLIENT_ID="your_microsoft_client_id"
export YAHOO_CLIENT_ID="your_yahoo_client_id"
export APPLE_CLIENT_ID="your_apple_client_id"

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease

# Build release AAB
./gradlew bundleRelease
```

### Install

```bash
# Debug
adb install app/build/outputs/apk/debug/app-debug.apk

# Or use Android Studio → Run → Run 'app'
```

---

## 📖 Feature Usage Guide

### 1. Adding Accounts

**Settings → Accounts → Add Account**

| Provider | Method | Required Info |
|----------|--------|---------------|
| **Mailcow** | Manual | Server URL (e.g., `mail.example.com`), email, password/app password |

**After adding:**
- Choose sync options: Email, Calendar, Contacts, Tasks (per-account)
- Set as **Default Account** for new emails/events/tasks
- Configure sync interval (15 min default), Wi-Fi only, push notifications

---

### 2. Email — Unified Inbox

**Main Screen → Inbox Tab**

- **Unified view**: All accounts in one list, color-coded by account
- **Per-account folders**: Tap account card → INBOX, Sent, Drafts, Trash, Spam, Archive
- **Actions**: Swipe or long-press → Star, Mark Read/Unread, Archive, Delete, Move, Flag
- **Search**: Top-app-bar search → queries subject, sender, body across all accounts
- **Compose**: FAB → New Email → auto-uses default account (or select)
- **Attachments**: View inline (images/PDFs), download, share
- **Threading**: messages carry a thread id, shown as a flat list

**Keyboard shortcuts are not implemented.**

---

### 3. Calendar — Events & Invites

**Main Screen → Calendar Tab**

**Views:**
- **Month** — Color-coded dots per day, tap for day view
- **Week** — 7-day grid, scroll horizontally
- **Day** — Hourly slots for all 24 hours, all-day section at top

**Creating Events:**
1. Tap **+** FAB or long-press time slot
2. Fill: Title, Description, Location, Start/End time, All-day toggle
3. **Calendar Color** — Pick from 18 Material colors (preserved from server)
4. **Attendees** — Add email addresses (comma-separated)
   - Each gets an invite via email + in-app notification
   - Recipients see **Yes / No / Maybe** buttons
5. **Reminders** — Default 1 hour (configurable: At time, 5/15/30 min, 1 hour, 1 day)
6. **Recurrence** — Daily, Weekly, Monthly, Yearly, Custom (RRULE)
6. **Save** → Invites sent automatically

**Invite Responses:**
- **Yes** → Green check, adds to calendar
- **No** → Red X, removed from calendar
- **Maybe** → Yellow clock, shows as tentative

**Shared Calendars:**
- Automatically synced from server
- Colors **preserved exactly** as on server
- Toggle visibility per-calendar in Settings → Accounts → [Account] → Calendars

**Navigation:**
- Swipe left/right: Previous/Next period
- Today FAB: Jump to today
- Search: Find events by title, location, description

---

### 4. Tasks — Lists & Subtasks

**Main Screen → Tasks Tab**

**Filters (chips at top):**
| Filter | Shows |
|--------|-------|
| **All** | Every task |
| **Active** | Incomplete |
| **Completed** | Done |
| **Starred** | ⭐ flagged |
| **Overdue** | Past due date |
| **Today** | Due today |

**Creating Tasks:**
1. Tap **+** FAB
2. Title (required), Description, Due date, Priority (Low/Normal/High/Urgent)
3. **List** — Personal, Work, Shopping, or custom
4. **Subtasks** — Add hierarchical breakdown
5. **Save**

**Task Actions:**
- Tap checkbox → Toggle complete
- Long-press → Star, Edit, Delete, Move to list

> Reordering and swipe gestures are not implemented.

---

### 5. Reminders & Alerts

**Default: 1 hour before events**

When reminder fires:
```
┌─────────────────────────────────────┐
│ ⏰ Event Reminder                   │
│ ┌─────────────────────────────────┐ │
│ │ 📅 Team Standup                 │ │
│ │ Tomorrow, 10:00 AM - 11:00 AM   │ │
│ │ 📍 Conference Room A            │ │
│ │ Weekly team sync...             │ │
│ └─────────────────────────────────┘ │
│ [Snooze 5min] [Dismiss] [View Event]│
└─────────────────────────────────────┘
```

- **Shows over lock screen** — No unlock needed
- **Turns screen on** — Even if device asleep
- **Snooze** → Reschedules 5 minutes
- **Dismiss** → Removes reminder
- **View Event** → Opens calendar detail
- Also creates standard notification as backup

**Configure:** Settings → Notifications → Full-screen reminders (toggle), Default reminder time

---

### 6. Security & Privacy

**Biometric Lock:**
- Settings → Security → Biometric Lock → Enable
- Prompts on app launch, after timeout (configurable: 1/5/15/30 min)

**Encryption:**
- **At Rest** — AuthConfig credentials (passwords, tokens) encrypted with Android Keystore (AES-256-GCM)
- **Keys** — Stored in Android Keystore (hardware-backed)
- **In Transit** — TLS for IMAP/SMTP sync (IMAPS 993 implicit TLS + SMTP STARTTLS 587). Credentials are never sent in plaintext. Note: accounts configured with `acceptAllCerts` (auto-enabled for Mailcow/SOGo boxes whose cert SAN excludes the bare domain) skip certificate hostname validation — encryption without strict authentication, by design for self-hosted mailcow.
- **Database** — Standard Room database (no SQLCipher)

**Zero Telemetry:**
- No analytics SDKs (Firebase, Play Services analytics, etc.)
- No crash reporting (no Crashlytics, Sentry, etc.)
- No usage tracking
- No network calls except explicit sync/messaging

**Data Control:**
- Settings → Advanced → Clear cached data
- Biometric lock with an optional auto-lock timeout

---

### 7. Sync & Data Management

**Auto-Sync:**
- Interval: 15 min (default) / 30 min / 1 hour
- Sync on unmetered networks toggle
- Push is not implemented; sync is periodic via WorkManager

**Manual Sync:**
- Settings → Accounts → [Account] → Sync Now

> Per-account conflict resolution (Server Wins / Client Wins / Merge / Prompt)
> is not implemented.

**Attachments:**
- Viewed inline via the system viewer (FileProvider)
- Downloaded attachments are stored in the app's cache directory

---

## ⚙️ Configuration

### Environment Variables (for build)

| Variable | Required | Description |
|----------|----------|-------------|
| `KEYSTORE_PATH` | Release builds | Path to `.jks` file |
| `KEYSTORE_PASSWORD` | Release builds | Keystore password |
| `KEY_ALIAS` | Release builds | Key alias |
| `KEY_PASSWORD` | Release builds | Key password |

The release signing config fails the build if `KEYSTORE_PATH` is set without
the matching credentials; it no longer falls back to a default password.

> `GOOGLE_CLIENT_ID`, `MICROSOFT_CLIENT_ID`, `YAHOO_CLIENT_ID`,
> `YAHOO_CLIENT_SECRET` and `APPLE_CLIENT_ID` are still declared as
> `buildConfigField`s but nothing reads them — no OAuth provider is wired up.
> `PUSH_API_KEY` is documented nowhere in the code and does not exist.

### Provider Setup

**Mailcow** is the only provider the Add Account screen can configure:

1. Enter the account email address.
2. Autodiscover resolves the server settings, or open **Advanced Settings** to
   enter IMAP/SMTP/CalDAV hosts manually.
3. Enter the password or app password.

Google, Microsoft (Outlook/Exchange), Yahoo and Apple (iCloud) OAuth flows are
**not implemented** — there is no browser launcher, no authorization-code
exchange, and no OAuth tile in the provider list.

---

## 🏗️ Architecture

```
app/
├── data/
│   ├── model/          # Account, Email, CalendarEvent, Task, Message, UnifiedContact
│   ├── db/             # Room: Database, 9 DAOs, TypeConverters
│   └── repository/     # 7 Repositories (interfaces + impls)
├── sync/
│   ├── accounts/       # Authenticator + AuthenticatorService (AccountManager)
│   ├── EmailSyncEngineImpl      # IMAP/SMTP + OAuth2
│   ├── CalendarSyncEngineImpl   # CalDAV + Google + Exchange
│   ├── TaskSyncEngineImpl       # CalDAV VTODO + Google Tasks
│   ├── ContactSyncEngineImpl    # CardDAV + Google People
│   └── SyncManager              # Orchestrates all engines
├── security/
│   ├── CryptoManager   # AES-256-GCM (Keystore), encrypt AuthConfig secrets
│   └── Biometric prompt in MainActivity (no separate manager class)
├── reminder/           # AlarmManager exact alarms, FullScreenReminderActivity
├── ui/
│   ├── main/           # MainActivity, 5 screens (Inbox, Email, Calendar, Tasks, Settings)
│   ├── auth/           # AddAccountActivity (OAuth + manual), OAuthCallbackActivity
│   ├── search/         # Global search
│   ├── settings/       # Settings activity
│   └── theme/          # Material 3, dynamic colors, account color palette
└── util/               # PreferencesManager, NotificationHelper, InviteActionReceiver
```

**Key Libraries:**
- `Room 2.6`, `Compose 1.6`
- `JavaMail 1.6.7` (IMAP/SMTP via `android-mail`)
- `OkHttp 4.12` + `Retrofit 2.11` (REST)
- `kotlinx-serialization 1.6` (JSON)
- `Coil 2.6` (images)
- `WorkManager 2.9` (background)

---

## 📦 Building Release

```bash
# Set signing env vars (or use local.properties)
export KEYSTORE_PATH=/path/to/release.jks
export KEYSTORE_PASSWORD=your_store_password
export KEY_ALIAS=your_key_alias
export KEY_PASSWORD=your_key_password

# Build signed APK
./gradlew assembleRelease

# Or build signed AAB
./gradlew bundleRelease
```

---

## 🤝 Contributing

1. Fork → Create feature branch (`git checkout -b feature/amazing-feature`)
2. Follow Linus-style commits: small, focused, descriptive subjects
3. Run tests: `./gradlew test`
4. Submit PR with clear description

**Code Style:** 8-char tabs, K&R braces, 80-col, functions do ONE thing

---

## 📄 License

MIT License — See [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- **Material 3** design system
- **AndroidX**: Room, Compose, WorkManager, Biometric, Security-Crypto
- **OkHttp**, **Retrofit**, **Kotlinx Serialization**, **Coroutines**
- **JavaMail** for IMAP/SMTP
- **Coil** for image loading

---

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/Dvalin21/UnifiedComms/issues)
- **Discussions**: [GitHub Discussions](https://github.com/Dvalin21/UnifiedComms/discussions)
- **Security**: Email `security@dvalin21.dev` (PGP key available)

---

**Made with ❤️ for privacy-conscious communicators.**
