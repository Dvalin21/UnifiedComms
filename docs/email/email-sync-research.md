# Email Sync Research Notes

Reference for implementing real IMAP/SMTP email sync in this project.

Date: 2026-07-06

## Approaches
- **Maildir-style IMAP sync via JavaMail** — partial; already used in `EmailSyncEngineImpl` for IMAP/SMTP transport.
- **Sync Adapter integration** — incomplete; worker classes exist but more wiring is required.
- **Re-verified OSS patterns**:
  - K-9 / K-9mail upstream Google Code search paths are defunct.
  - AOSP Email app: `com.android.email` uses `EmailContent` + `ExchangeUtils` for EAS; IMAP providers use JavaMail too.
  - Reference JavaMail IMAP best practice: use `Folder.READ_ONLY`, reconnect on `FolderClosedException`, honor `IDLE` where supported.

## Proven working patterns
- IMAP host/port defaults: `993/SSL`, `143/STARTTLS`.
- SMTP host/port defaults: `465/SSL`, `587/STARTTLS`.
- Auth config: `AuthType.APP_PASSWORD` with `passwordEncrypted` as encrypted field.
- Store property keys for JavaMail:
  `mail.imap.host`, `mail.imap.port`, `mail.imap.ssl.enable`, `mail.imap.auth`, `mail.imap.connectiontimeout`, `mail.imap.timeout`.

## Blockers found
- `androidTest` instrumentation code path is not currently routed; Compose app needs dedicated instrumented tests.
- `AddAccountActivity` was not registered in `AndroidManifest.xml`, so manual setup could not be launched.
- `AddAccountScreen` did not expose server selection/credentials for non-OAuth providers; fixed.
- `IMAPFolder(MailFolder)` wrappers are not present; MOVE/DELETE stubs in `EmailSyncEngineImpl` need IMAP extension support.

## Decisions
1. Fix account creation path first — `AddAccountScreen` now builds `AuthConfig.AppPassword` for manual flows.
2. Keep JavaMail IMAP/SMTP engine, add robustness for real servers.
3. Defer CalDAV/CardDAV to later; ensure IMAP/SMTP works first.
4. Do not weaken `network_security_config` for self-signed setups.

## Next steps
1. Implement `EmailSyncWorker`/`SyncService` trigger path.
2. Fix `AuthConfigConverter` round-trip plaintext behavior.
3. Harden `EmailSyncEngineImpl` with reconnect on `StoreClosedException` and `FolderClosedException`.
4. Add real account behavior wiring instead of seeds.
