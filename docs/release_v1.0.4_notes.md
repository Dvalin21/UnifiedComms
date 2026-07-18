# UnifiedComms v1.0.4 — Bug Remediation Release

Signed release APK built from `fix/batch-a-sev1` @ `842323f` (post-remediation HEAD).

## What changed

### Real bugs fixed (verified against source + emulator)
- **OAuth token refresh**: no longer falls back to plaintext (HANDOFF #1).
- **Conversation inbox query**: `ConversationDao` now matches the JSON-encoded `participantIds`
  the app actually writes, so the inbox populates instead of staying empty (HANDOFF #3, the
  real inbox root cause — not the synthetic-identity red herring).
- **Biometric lock bypass**: removed the hardcoded "skip if no enrolled biometric" path that
  left the app unlocked on a no-biometric device (HANDOFF #5).
- **InviteActionReceiver**: declared `exported=false` and now matches the invite by attendee
  email before applying, instead of stamping the current user onto every NEEDS_ACTION attendee
  (HANDOFF #6).
- **Reminders**: `ReminderSystem` guards `setExactAndAllowWhileIdle` with
  `canScheduleExactAlarms()` (API 31+) and falls back to `set()`; `ReminderAlarmReceiver`
  wrapped in `goAsync()` (HANDOFF #7). Reboot-loss was a false alarm — `BootReceiver` already
  re-schedules on boot.
- **Notifications**: `NotificationHelper.notifySafe` now logs when `POST_NOTIFICATIONS` is denied
  instead of silently dropping (HANDOFF #8).
- **Email row tap**: was a dead `clickable { /* open */ }`; now opens a new `email_detail`
  route → read-only `EmailDetailScreen` (HANDOFF #10).
- **Blank-name crashes**: `String.first()` → `firstOrNull()?.uppercase() ?: "?"` at all three
  sites (EmailScreen, MessagesScreen, UnifiedInboxScreen) (HANDOFF #12).
- **Blank account name**: `AccountSettingsScreen` falls back to email / "Account" when `name`
  is blank (HANDOFF #14).

### Claims disproved during remediation (no churn on false premises)
- CryptoManager "downgrade attack" on `<12`-byte input — false; the >=12 path correctly throws
  on malformed/truncated ciphertext (#9).
- Move/delete Message-ID match — misattributed; `MainViewModel` passes the real `messageId`,
  not a synthetic uid (#11). Server-side no-op for Message-ID-less mail is a latent gap needing
  IMAP-UID persistence (out of scope for this pass).
- Batch C (#13, #15, #16, #26): reference files/classes that do not exist in the repo.
- Batch C (#17, #18, #19, #27, #28, #29): misread correct code or internally contradictory.
- Batch C tests (#20–#25, #30–#36): already correct (`runTest`, consistent defaults).

### Test harness fix
- `ScreenshotGalleryTest` bottom-nav tap coordinates had drifted (Calendar/Messages taps missed
  their tabs). Re-derived true tab centers from a uiautomator dump. Gallery now captures all
  13 screens correctly.

## Verification
- `:app:assembleDebug :app:assembleAndroidTest :app:testDebugUnitTest` — GREEN.
- 71 unit-test methods across 18 files, all pass.
- UI gallery (uc_01..uc_13, light + dark) — OK (2 tests), all tabs vision-verified, no crash.
- DAV E2E round-trips (Contact/Task/Calendar) vs host mocks — OK (3 tests) ×2 runs.
- `BackgroundSyncWorkerTest` — OK (1 test).
- Release APK: `apksigner verify` → v2 scheme true, 1 signer.

## Install note
Debug and release share the `com.unifiedcomms.authenticator` authority and cannot co-install.
Uninstall the debug build before sideloading the release APK.
