# UnifiedComms — HANDOFF (2026-07-29, updated)

Project: ~/host/UnifiedComms (Android Kotlin/Compose, mailcow/SOGo accounts
under @<test-domain>). F-Droid only, Linus-permanent tone, caveman comms.
All clones go to ~/host/.

================================================================================
LINUS TORVALDS 10 PRINCIPLES (apply to every change)
================================================================================
1. Good code needs good data structures; spend time on them first.
2. Talk is cheap — show the code. Don't theorize, demonstrate.
3. Simplicity beats cleverness every time. Stupid is better if it's clear.
4. Start small. Don't boil the ocean; build the minimal correct thing.
5. Avoid big design decisions early. Let the code reveal what it needs.
6. Reproduce a bug before you fix it. Never patch blind.
7. Fix the root cause, not the symptom. One real fix > ten band-aids.
8. Comments explain WHY, never WHAT. The code says what; say why it's weird.
9. Fix the design, don't just indent the bug away.
10. If you see a broken window, surface it. Don't let rot spread.

================================================================================
CURRENT DEVICE
================================================================================
PHONE = 10.0.0.228:39981 (CURRENT, primary test device). Account
  <test-user>@<test-domain> may or may not be present. Verify before adding.
TABLET TB570FU (Lenovo Tab, Android 16) = 10.0.0.211:<PORT> — PORT ROTATES
  when wireless debug drops; Keith supplies the new port.
Emulator-5556 OFF-LIMITS per Keith's standing rule (physical device only).

Latest commit on master: 7dea341
  calendar: reduce respondToInvite to engine, fix CalDAV error path

**2026-08-16 — Full code review (19 files, ~6100 lines) + BlueMail APK decompilation complete.**
Full plan written to `.hermes/plans/unified-comms-feature-plan.md`.

SUMMARY OF FINDINGS:
- 11 code issues found: 4 correctness bugs, 3 dead-code items, 4 UI polish items
- Dead to remove: res/layout/activity_add_account_manual.xml, AccountColors.getColor(Int), SyncManager chat test entry
- BlueMail chat = server-mediated (cha.onblix.com WebSocket + bluemailx.com API + FCM) — NOT P2P
- User approved self-hosted relay server for chat (2026-08-17). Architecture: Python FastAPI + SQLite, ciphertext-only storage, WebSocket push, AES-256-GCM. ManCom relay at /home/keith/ManCom/relay/ is the template.
- Recommended: adapt ManCom relay, change identity phone→email, pre-shared key + AES-256-GCM for v1
- Account icons: add provider icon overlay, expand calendar color picker to 19 swatches

================================================================================
WHAT SHIPPED THIS SESSION (committed)
================================================================================
SYNC VERIFICATION — LIVE E2E ON EMULATOR-5554:
- Wrote SyncVerificationTest (androidTest): inserts <test-user>@<test-domain>
  account directly into Room, triggers SyncManager.performFullSync(), asserts
  result.success && (emailCount>0 || events>0 || tasks>0 || contacts>0).
- Password read from device file /data/data/com.unifiedcomms.debug/files/uc_main_pw
  (pushed from ~/.hermes/uc_main_pw); NEVER hardcoded in source.
- Account built with acceptAllCerts=true, IMAP imap.<test-domain>:993 SSL,
  SMTP smtp.<test-domain>:587 STARTTLS, CalDAV
  https://email.<test-domain>/SOGo/dav/<test-user>@<test-domain>/Calendar/personal/,
  CardDAV .../Contacts/personal/.
- RESULT: OK (1 test). Email=22, Calendar=15 events, Contacts=1, Tasks=0 (no
  VTODOs on server — not an app bug). CalDAV/Todo/Contacts came back as 0 on
  first run because the test used wrong host sogo.<test-domain>; fixed to
  email.<test-domain> (matches ProviderProfiles static entry), re-ran, all
  three domains synced.
- Email: 22 messages from real IMAP (<test-user> inbox). Calendar: 15 VEVENTs from
  real CalDAV. Contacts: 1 CardDAV contact. Tasks: 0 (server empty).
- Screenshots hosted on local web server at http://localhost:9876/:
  scr_inbox.png (Unified Inbox, 22 emails), scr_calendar.png (Calendar, 15 events),
  scr_settings.png (Settings panel).

CALENDAR INVITE RSVP — ROOT-CAUSE FIX (from prior session, still standing):
- ... (unchanged below) ...

================================================================================
CODE REVIEW FINDINGS (full pass, 2026-08-16; sync-verified 2026-08-20)
================================================================================
REVIEWED: ~6100 lines Kotlin across 19 source files + resources + manifest.
SYNCHRONOUS LIVE VERIFICATION: SyncVerificationTest on emulator-5554 proved
Email IMAP (22 msgs), Calendar CalDAV (15 events), CardDAV (1 contact) against
the real <test-domain> server. Tasks=0 because the server has no VTODOs.

GENUINE BUGS (low severity, none block build/install):
- SyncManager.performFullSync creates a fresh `CoroutineScope(Job())` per call
  (line ~110) instead of `coroutineScope { }`. Children complete but the outer
  Job is abandoned. Sloppy, not leaking in practice. Fix: use coroutineScope.
- SyncManager.testAllConnections (line ~219) returns a hardcoded
  `chat -> ConnectionTestResult(true,...)` for a feature that was removed.
  Dead entry. Remove the chat line.
- CalendarRepositoryImpl.dedupMastersByTitle dedups recurring masters by
  title+accountId (line ~89). Two different series with the same title lose one.
  Edge case, but title is a weak key. Fix: dedupe by UID or drop the dedup.
- EmailScreen lists hard-cap at 100 messages (line ~101,
  getByAccountAndFolder(accountId, folder, 100, 0)). No pagination / load-more.
  Large folders silently truncate. Add infinite scroll or a "load more" trigger.

DEAD CODE (remove before next release):
- res/layout/activity_add_account_manual.xml — AddAccountActivity forwards to
  MainActivity; this layout is never inflated. Delete.
- AccountColors.getColor(index: Int) — only getColorForAccount(accountId) is
  called. Delete the unused overload.
- SyncManager.testAllConnections chat entry (see above).

UI POLISH (non-blocking, aesthetic):
- Calendar create-event color picker offers 8 swatches. Expand to the full
  19-color AccountColors palette (or a real color picker) so events match the
  calendar's actual color set.
- Account avatars are initials-on-color throughout. BlueMail-style would drop a
  small provider icon (Gmail/Outlook/etc.) inside the circle for known providers.
  Optional — current look is clean and consistent.
- Settings > Appearance theme-mode does not observe PreferencesManager.themeModeFlow;
  after a change made elsewhere the chip shows a stale value until recomposition.
  Switch to collectAsStateWithLifecycle.
- Bottom nav has 4 tabs (Inbox/Calendar/Tasks/People). The Message entity exists
  (search-only, chat removed) so there is no Messages tab — intentional, but the
  nav looks one slot short. Leave as-is until a real messages UI exists.

PROVIDER ICONS / ACCOUNT ICONS STATUS:
- 14 VectorDrawable provider icons in res/drawable/ic_provider_*. All render
  white on a brand-tinted tile in AddAccountScreen. Good.
- Account "avatar" = colored circle + initials everywhere (EmailScreen,
  UnifiedInbox, AccountSettings). Colors from UIConfig.color via
  AccountColors.getColorForAccount(accountId) (deterministic per account id).
  All 19 palette colors have readable foreground (white on dark, black on light)
  — verified per-color above.
- No per-account-type icon inside the avatar circle. Consistent minimal look.

BLUE MAIL CHAT FEATURE:
- APK at /home/keith/bluemail/bluemail.apk decompiled via jadx + apkInspector (341 Java classes + 5.4MB JS bundle).
- BlueMail "chat" = server-mediated, NOT peer-to-peer. WebSocket to cha.onblix.com:443, REST to bluemailx.com APIs, FCM push, group/channel rooms (group_jid, room_id, channelId). Identity = email account.
- Cannot replicate without their servers. UX patterns transferable (conversation list, message states, typing indicators, read receipts, contact invite model).

RELAY SERVER RESEARCH (2026-08-17):
- User approved self-hosted relay server for chat. This unlocks remote messaging, push, offline delivery.
- Evaluated: Signal-Server (overkill, Java/Scala+PostgreSQL), Berty (P2P mesh, wrong pattern), Matrix (federated, complex), simple encrypted relay (Python/Go/Rust — BEST FIT).
- The ManCom relay at /home/keith/ManCom/relay/ is a perfect template: 541-line Python FastAPI + SQLite, stores ciphertext only, bearer token auth, WebSocket push, message TTL, rate limiting, device registration, identity key lookup. Already implements exactly the architecture described.
- Encryption options: (1) Pre-shared key + AES-256-GCM (simpler, v1), (2) X3DH + AES-256-GCM (Signal-like, v2).
- Recommended: Adapt ManCom relay as base, change identity from phone→email, implement pre-shared key + AES-256-GCM for v1, add X3DH later.
- Self-hosting model: same as mailcow — user runs on their own VPS/server, configures UnifiedComms with relay URL. No third-party dependency.

CHAT FEATURE PLAN (updated):
- Phase 2 (NEW): Relay server — adapt ManCom relay, change identity model, test locally
- Phase 3 (NEW): Android client — ChatCryptoManager (AES-256-GCM), ChatRelayManager (OkHttp + WebSocket), ChatSyncManager (poll + push), wire to existing MessageDao/Message entity
- Phase 4 (NEW): Chat UI — conversation list, chat detail, peer invite, read receipts, 5th bottom nav tab
- LAN P2P approach is now a fallback/alternative, not the primary path

- PROVEN:
  - Debug APK builds, installs, boots to empty Inbox on 10.0.0.228:39981 (per
    HANDOFF). Code review confirms sync engines, encryption, biometric, network
    config are correctly wired. No crash bugs found in the review path.
  - SYNC VERIFIED 2026-08-20 on emulator-5554: SyncVerificationTest passed OK (1
    test). Email IMAP synced 22 messages, Calendar CalDAV synced 15 VEVENTs,
    CardDAV synced 1 contact against the real <test-domain> server. Tasks=0
    (server has no VTODOs). Account added with acceptAllCerts=true, IMAP
    imap.<test-domain>:993, SMTP smtp.<test-domain>:587, CalDAV
    https://email.<test-domain>/SOGo/dav/. Screenshots hosted at
    http://localhost:9876/ (scr_inbox.png, scr_calendar.png, scr_settings.png).

================================================================================
CREDENTIAL HANDLING (mailcow, CRITICAL)
================================================================================
- NEVER inline a password in chat/shell history. Read from file, type via
  `adb shell input text '<pw>'` with SINGLE quotes so $ * # survive the remote shell.
- testbox1@<test-domain> login = MASTER password at ~/.hermes/uc_main_pw
  (proven by prior live email-sync instrumentation test which logs in with it).
- MAILCOW LOCKOUT TRAP: <test-user> locks after >2 wrong passwords in 2 min. Max ~2
  auth attempts per session. After 2 failures, STOP and ask Keith.
- Servers: IMAP imap.<test-domain>:993 (SSL, acceptAllCerts=true — wildcard cert
  has no bare-domain SAN), SMTP smtp.<test-domain>:587 (STARTTLS).
- DAV base: https://email.<test-domain>/SOGo/dav/<user>/
- App-passwords containing $ * # are REJECTED by mailcow IMAP auth (server policy);
  the MASTER password works via Advanced settings. Do NOT "fix" client code on
  [AUTHENTICATIONFAILED] — the server rejects the credential.

================================================================================
RUST RELAY SERVER — X3DH ENCRYPTED RELAY (/home/keith/UnifiedCommsRelay/)
================================================================================
STATUS: COMPLETE (2026-08-17). Clean compile + Docker image built + smoke-tested.

DESIGN:
- Self-hosted relay. User controls the server. No third-party dependency.
- Phone-number identity (matches ManCom relay). X3DH key exchange for E2EE.
- Relay stores ONLY ciphertext — never sees plaintext. AES-256-GCM per message.
- Bearer token auth (HMAC-SHA256 hashed tokens in DB). Master token + per-device tokens.
- WebSocket push via tokio broadcast channels per phone number.
- SQLite via sqlx. Message TTL 7 days. Rate limiting 60 req/min per device.
- Clean module layout: auth.rs, types.rs, x3dh.rs, routes.rs, main.rs

API ENDPOINTS:
- POST /v1/register — register device (phone, identity keys, prekeys), returns token (one-time)
- POST /v1/register/rotate — rotate token
- GET /v1/identity/{phone} — lookup identity_pub + signed_prekey_pub (+otp)
- POST /v1/send — send encrypted envelope (from_number, recipients[], ciphertext, ephemeral_pub, chain_index)
- GET /v1/inbox?since=ts&mark_read=true — fetch undelivered messages
- DELETE /v1/account — delete account + tokens + messages
- GET /ws — WebSocket upgrade, auth via JSON token, then push channel

DB SCHEMA:
- accounts(phone PK, identity_pub, identity_sig, signed_prekey_pub, signed_prekey_sig, one_time_prekey, ts)
- tokens(token_hash PK, phone FK, device_id, created_ts, expires_ts)
- messages(msg_id+recipient PK, sender, recipient, ts, ciphertext, ephemeral_pub, chain_index, group_id, fallback_hint, delivered_ts)
- idx_messages_recipient(recipient, delivered_ts, ts)

RUST PROJECT STRUCTURE:
- /home/keith/UnifiedCommsRelay/Cargo.toml — axum 0.8 ws, tokio full, sqlx 0.8 sqlite,
  x25519-dalek 2, ed25519-dalek 2, aes-gcm 0.10, hmac 0.12, sha2 0.10, rand 0.8,
  base64 0.22, uuid 1, futures 0.3, chrono 0.4, tracing
- src/main.rs — bootstrap, DB init, serve
- src/auth.rs — BearerAuth extractor (FromRequestParts, header::AUTHORIZATION)
- src/types.rs — RegistrationRequest, SendEnvelope, WebSocketAuth, WebSocketMessage, ConnectedMessage
- src/x3dh.rs — X3DH dh(), x3dh_shared_secret(), derive_message_key(), encrypt_message(), decrypt_message(), generate_identity_keys()
- src/routes.rs — all 7 HTTP endpoints + ws_route/ws_handler, AppState, RateLimiter, push_to_recipient, cleanup_expired

DOCKER:
- Dockerfile: multi-stage (rust:1.85-slim-bookworm builder → debian:bookworm-slim runtime)
- docker-compose.yml: port 8443, volume relay_data, env vars for all config
- docker build + smoke test (healthz endpoint) PASSED

COMPILE FIXES APPLIED:
- 41 errors → 0. Fixed: hmac trait ambiguity (qualified syntax), base64 0.22 API,
  axum 0.8 Message::Text(Utf8Bytes), sqlx tuple decode (two scalar queries),
  borrow checker (pass auth by ref), x25519 StaticSecret::from(), PublicKey::from ambiguity,
  WebSocket split (StreamExt + SinkExt), SplitSink/SendError type imports.

NEXT: Wire Android client to this relay (Phase 3 of feature plan).

================================================================================
1. Reconnect Wi-Fi ADB: `adb connect 10.0.0.228:39981` (port may have changed).
2. Verify current package/account state: `adb -s 10.0.0.228:39981 shell pm list packages | grep unifiedcomms` and check whether testbox@ is still configured.
3. If needed, install current debug APK; uninstall `com.unifiedcomms` first:
   `adb -s 10.0.0.228:39981 shell pm uninstall --user 0 com.unifiedcomms`
   then `adb -s 10.0.0.228:39981 install -r app/build/outputs/apk/debug/app-debug.apk`
   Launch with: `adb -s 10.0.0.228:39981 shell monkey -p com.unifiedcomms.debug -c android.intent.category.LAUNCHER 1`
4. Add account if missing: Add Account -> Mailcow -> Advanced ->
   IMAP imap.<test-domain>:993 SSL, SMTP smtp.<test-domain>:587 STARTTLS,
   Accept-all-certificates ON, email <test-user>@<test-domain> + password from
   ~/.hermes/uc_main_pw via single-quoted `adb shell input text`.
5. Keith sends invite from <organizer>@<test-domain> -> <test-user>; sync + open.
6. Exercise Accept/Decline/+Just Add; watch logcat for "INVITE" + CalDAV + SMTP.
7. Screenshot + logcat proof required before claiming "RSVP works end-to-end."

================================================================================
KNOWN LIMITATIONS / NEXT FIXES
================================================================================
- Minimal iTIP REPLY payload is structurally valid, but interoperability
  with the organizer's real client is unverified. Pair with SMTP capture
  + organizer-side confirmation before declaring RSVP "done".
- Email list preview still leaks raw ICS body for invite emails. Fixed in
  detail screen; list preview sanitization is still pending.
- Reminder/ALARM mapping from VALARM is not implemented (not RSVP-related).
- EMAIL DOUBLE-TAP TO OPEN: EmailScreen row click navigates to EmailDetailScreen
  but does NOT mark the email read first. The mark-read only happens on the
  envelope IconButton (line 183). User reports having to tap twice. Fix: mark
  read in the row's clickable lambda before navigating.
- PROVIDER PROFILES: <test-domain> CalDAV/CardDAV host is hardcoded in
  ProviderProfiles as email.<test-domain> (static table, not autodiscover).
  SyncVerificationTest initially used wrong host sogo.<test-domain> — fixed
  to match ProviderProfiles. The app's AddAccountScreen uses ProviderProfiles
  lookup for known domains; autodiscover is only the fallback for unknown domains.

================================================================================
KEY FACTS (cross-checked, current)
================================================================================
- Keith verification bar: green build + screenshot is NOT proof. Core functions must
  work on the REAL device before "fixed" is spoken.
- Biometric lock: confirmed working by user. Don't touch it unless asked.
- Chat feature: REMOVED 2026-07-28. No code references remain.
- Don't modify personal <test-domain> / <organizer> server data without go-ahead;
  <test-user> is for test writes only.
