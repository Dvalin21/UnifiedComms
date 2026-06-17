# UnifiedComms

A unified communication app for Android that syncs email, calendar, tasks, and messages across multiple accounts — all without telemetry, tracking, or data leaving your device without explicit consent.

## Features

### 📧 Email
- **Multi-account support**: Google, Mailcow, Outlook, Yahoo, Exchange, iCloud, and generic IMAP/SMTP/CalDAV/CardDAV
- **Unified inbox**: View all accounts in one place with color-coded account indicators
- **Full folder sync**: INBOX, Sent, Drafts, Trash, Spam, Archive, and custom folders
- **Push notifications**: Real-time email delivery (where supported)
- **Attachments**: Download, preview, and manage attachments
- **Encryption**: PGP/GPG support for encrypted emails
- **Offline-first**: Full read/write access offline, syncs when online

### 📅 Calendar
- **Multi-calendar sync**: Google Calendar, CalDAV, Exchange, iCloud
- **Shared calendars**: Full support for calendars shared with you
- **Color preservation**: Calendar colors maintained exactly as on server
- **Event invites**: Create events with email invites; recipients get Yes/No/Maybe responses
- **Recurring events**: Full RRULE support (daily, weekly, monthly, yearly, custom)
- **Reminders**: Default 1-hour reminder with full-screen alerts
- **Multiple views**: Day, Week, Month with smooth navigation
- **Time zones**: Proper time zone handling for travel

### ✅ Tasks
- **Task lists**: Multiple lists per account (Personal, Work, Shopping, etc.)
- **Subtasks**: Hierarchical task breakdown with progress tracking
- **Due dates & reminders**: With recurring task support
- **Priority levels**: Low, Normal, High, Urgent with color coding
- **Categories/Tags**: Organize with custom tags
- **Kanban-style**: Drag to reorder, mark complete

### 💬 Messaging (Inter-app)
- **End-to-end encrypted**: AES-256 + RSA-4096 for all messages
- **Direct messages**: 1:1 conversations with other UnifiedComms users
- **Group chats**: Multiple participants with admin controls
- **Rich sharing**: Share emails, calendar events, tasks directly in chat
- **Calendar invites in chat**: Send/receive invites with Yes/No/Maybe buttons
- **Disappearing messages**: Optional auto-delete timer
- **Push notifications**: Real-time delivery via FCM/WebPush

### 🔒 Privacy & Security
- **Zero telemetry**: No analytics, no crash reporting, no usage tracking
- **No personal data collection**: Nothing sent to our servers without explicit action
- **Local-first**: All data stored locally in encrypted Room database (SQLCipher)
- **Biometric lock**: Fingerprint/Face ID to open app
- **AES-256 encryption**: All data at rest encrypted with Android Keystore
- **Certificate pinning**: For all server connections
- **App password support**: For services requiring app-specific passwords

### 🎨 UI/UX
- **Material 3**: Expressive, colorful, adaptive
- **Account colors**: Each account gets a unique Material color
- **Dark/Light/System**: Full theme support
- **Responsive**: Phones (6.5") to Tablets (14.5") with adaptive layouts
- **Widgets**: Email, Calendar, Tasks, and Unified widgets (Glance)
- **Animated**: Smooth transitions, shared element animations
- **Accessibility**: TalkBack, large text, high contrast support

## Requirements

- Android 12+ (API 31+)
- Kotlin 1.9+
- Gradle 8.5+
- Java 17

## Building

```bash
# Clone
git clone https://github.com/yourusername/UnifiedComms.git
cd UnifiedComms

# Copy local.properties.example to local.properties and configure
cp local.properties.example local.properties

# Build debug APK
./gradlew assembleDebug

# Build release AAB
./gradlew bundleRelease
```

## Configuration

### Account Setup
1. Open Settings → Accounts → Add Account
2. Select provider (Google, Mailcow, Outlook, etc.)
3. For Google/Outlook/Yahoo/iCloud: OAuth flow in browser
4. For Mailcow/Exchange/Generic: Enter server URL, email, password/app password
5. Choose sync options (Email, Calendar, Contacts, Tasks)
6. Set as default account (optional)

### Sync Settings
- **Interval**: 15 min (default), 30 min, 1 hour, manual
- **Wi-Fi only**: Save mobile data
- **Push**: Enable where supported (Google, Exchange, iCloud)
- **Attachments**: Auto-download (configurable size limit)

### Notifications
- Per-account notification priority
- Custom sounds/vibration per account
- Full-screen reminders for calendar (1 hour default)
- Message notifications with preview

## Architecture

```
UnifiedComms/
├── app/
│   ├── src/main/
│   │   ├── java/com/unifiedcomms/
│   │   │   ├── data/           # Data layer (Room, Repository, Models)
│   │   │   │   ├── model/      # Data classes (Account, Email, Event, Task, Message)
│   │   │   │   ├── db/         # Room database, DAOs, Converters
│   │   │   │   └── repository/ # Repository interfaces & implementations
│   │   │   ├── di/             # Hilt dependency injection modules
│   │   │   ├── sync/           # Sync engines (Email, Calendar, Tasks, Contacts)
│   │   │   ├── security/       # CryptoManager, BiometricManager
│   │   │   ├── push/           # PushManager (FCM/WebPush)
│   │   │   ├── messaging/      # MessagingService (inter-app communication)
│   │   │   ├── reminder/       # Full-screen reminders, scheduling
│   │   │   ├── widgets/        # Glance app widgets
│   │   │   └── ui/             # Compose UI (MainActivity, Screens, Theme)
│   │   ├── res/                # Resources (layouts, drawables, values, xml)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## Data Structures First (Linus Philosophy)

All models defined before logic:
- `Account` - Multi-provider account configuration
- `Email` - Full RFC 5322 email with attachments, threading, flags
- `CalendarEvent` - iCal-compliant with recurrence, attendees, colors
- `Task` - VTODO-compliant with subtasks, priorities, recurrence
- `Message` - E2E encrypted inter-app messages
- `Conversation` - Threaded messaging with metadata
- `UnifiedContact` - Merged contacts from all sources

## Sync Engines

Each sync engine implements a common interface:
- `EmailSyncEngine` - IMAP/SMTP + OAuth2 (Gmail API, Graph API)
- `CalendarSyncEngine` - CalDAV + Google Calendar API + Exchange Web Services
- `TaskSyncEngine` - CalDAV VTODO + Google Tasks API
- `ContactSyncEngine` - CardDAV + Google People API + Exchange

All support:
- Incremental sync with sync tokens
- Push notifications (IDLE, webhooks, FCM)
- Conflict resolution (server/client/merge/prompt)
- Offline queue with retry logic

## Widgets (Glance)

Four widget types:
1. **Email Widget** - Unread counts per account, quick compose
2. **Calendar Widget** - Today's events with times, new event button
3. **Tasks Widget** - Progress ring, due tasks with checkboxes
4. **Unified Widget** - All three in compact panels

All widgets:
- Configurable size (2×2 to 4×4 cells)
- Tap to open specific section
- Auto-refresh every 30 minutes
- Dark/light theme aware

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Follow Linus-style commits: small, focused, descriptive subjects
4. Run tests: `./gradlew test`
5. Submit PR with clear description

## License

MIT License - See LICENSE file for details.

## Acknowledgments

- Material 3 design system
- Room, Hilt, Compose, Glance from AndroidX
- OkHttp, Retrofit, Kotlinx Serialization
- ical4android, dav4jvm for CalDAV/CardDAV
- JavaMail for IMAP/SMTP