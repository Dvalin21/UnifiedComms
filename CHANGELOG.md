# Changelog

All notable changes to UnifiedComms are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [Unreleased]

### Fixed
- **Draft discard (data loss).** The compose screen's back arrow and the event
  editor's back arrow called the same callback as "send"/"save", so a
  half-written message or event was destroyed with no confirmation. Both now
  ask before discarding.
- **Room converters dropped default-valued fields.** Every converter used
  `Json { ignoreUnknownKeys = true }`, whose default is `encodeDefaults = false`,
  so any field equal to its declared default was silently omitted from the
  stored JSON — a mailcow `ServerConfig` persisted as `{"imapHost":..,
  "smtpHost":..}` with `imapPort`, `smtpPort`, `imapUseSsl` and
  `smtpUseStartTls` all missing. Decoding masked it by falling back to the same
  defaults, so the loss only surfaced when something read the raw JSON. All
  converters now share one instance with `encodeDefaults = true`.
- **Silent parse failures.** `parseEmail` returned `null` on error with no
  logging, so malformed mail vanished from the mailbox with no trace.
- **Time format ignored the system setting.** Seven sites hardcoded a time
  pattern, and CalendarScreen showed `h:mm a` and `HH:mm` on the same screen, so
  a 24-hour-locale user saw 15:30 rendered as "3:30 PM". All display sites now
  go through `util/TimeFormat`, which honours `DateFormat.is24HourFormat`.
- **Send paths logged nothing.** `sendEmail` returned success with no log line;
  both send paths now record their outcome (message content is deliberately not
  logged).
- **Release signing could use a default password.** The signing config fell
  back to `storePassword "password"` when the environment variables were absent,
  so a forgotten variable produced a release signed with a publicly known
  password and the build still succeeded. It now fails configuration instead.
- **Schema was never exported.** `exportSchema = false` meant the configured
  `room.schemaLocation` produced nothing, so the migrations could not be checked
  by any tool. Schemas are now written to `app/schemas/`.

### Removed
- **The dead chat subsystem.** The chat feature was removed in 3ab6450 but its
  data layer stayed behind with zero consumers: the `Message` entity, its DAO,
  `MessagingRepository` and its impl, a "Messages" notification channel, and a
  `showMessageNotification` that routed to a route which no longer existed —
  ~361 lines, plus the now-empty `messages` table (dropped by
  `MIGRATION_10_11`, version 10 → 11).
  Note that `Message.kt` also declared `UnifiedContact` and
  `CalendarInviteMessage`, and `MessageDao.kt` also declared the only
  `ContactDao`; those live types were preserved.
- **Dead code and unused configuration:** `RuntimePermissionGate`, the
  `CalendarWidgetFactory`/`CalendarWidgetService` pair (which read a
  SharedPreferences store nothing wrote), the unused Glance artifacts, the
  unused `FOREGROUND_SERVICE` permissions, and the Hilt / at.bitfire proguard
  keep rules for dependencies that are not on the classpath.
- **Phantom root `version`.** The root build script declared `version = "1.0.0"`
  while the app reported 1.0.28; nothing read it.

### Changed
- Personal data removed from tracked test fixtures (a username in
  `AutodiscoverDavTest`, a name in `CalendarDupeDebugTest`).

## [1.0.0-alpha] - 2024-12-19

Original alpha. The feature list recorded here — OAuth providers, PGP/MIME,
push notifications, Glance widgets, tablet layouts, message reactions and the
full chat feature — described intent rather than shipped behaviour. Several of
those items were never implemented and the chat feature was later removed; see
the Unreleased section above for what the code actually does today.
