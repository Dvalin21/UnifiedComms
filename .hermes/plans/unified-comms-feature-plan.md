# UnifiedComms Feature Plan — Chat + Fixes + Polish + Relay Server

**Created:** 2026-08-16 | **Updated:** 2026-08-17 (relay server research added)
**Based on:** Full codebase review (19 Kotlin files) + BlueMail APK decompilation (341 Java classes + 5.4MB JS bundle) + open-source relay server research + ManCom relay audit

---

## Context

User wants:
1. Full review of UnifiedComms: bugs, layouts, colors, aesthetics, functionality, account icons
2. Reverse-engineer BlueMail's chat feature (APK at /home/keith/bluemail/bluemail.apk)
3. Add chat feature to UnifiedComms — **NOW WITH SELF-HOSTED RELAY SERVER ALLOWED** (user approved: "even if we had to create our own security encrypt relay server")
4. Structured plan for all fixes, changes, and chat implementation

**Key change from v1:** User has approved a lightweight self-hosted relay server. This unlocks remote messaging, push notifications, and offline delivery. The LAN-only P2P approach is now a fallback/alternative, not the primary path.

---

## Part 1: Code Review Summary

### What's Solid
- Clean data layer, Room DB with 5 migrations, proper indexes
- UID-based IMAP sync with UIDVALIDITY invalidation
- CalDAV/CardDAV discovery and sync, recurrence expansion
- Biometric lock, AES-256-GCM encryption, Material3 theme with 19 colors
- OAuth token refresh, iTIP REPLY for calendar invites (working)

### Real Bugs Found
1. **EmailSyncEngineImpl.kt:93** — Log.d dumps full account config every sync; should be Log.v
2. **EmailSyncEngineImpl.kt** — Syncs by IMAP sequence number, not UID. Breaks on reconnect (dupes/loss). Latent bug.
3. **AccountColors.kt:104** — `getColor(index: Int)` is dead code, never called
4. **SyncManager.kt:219** — `testAllConnections` returns hardcoded `chat` success — dead reference to removed feature
5. **SyncManager.kt:110** — Creates `CoroutineScope(Dispatchers.IO + Job())` per sync instead of `coroutineScope { }`
6. **MainViewModel.kt:329** — `respondToInvite` creates second CalendarSyncEngineImpl instance unnecessarily
7. **SettingsScreen.kt** — Theme mode chip doesn't observe `themeModeFlow`, shows stale value
8. **EmailScreen.kt:101** — Hard-coded 100 message limit, no pagination
9. **AddAccountScreen.kt:462** — IMAP host fallback to "domain.com" (not valid)
10. **res/layout/activity_add_account_manual.xml** — Dead layout file, never inflated
11. **network_security_config.xml** — Trusts user CAs (fine for debug, flag for release)

### UI/Aesthetic Assessment
- 14 provider VectorDrawables (clean white on brand-tinted tile in AddAccount)
- Account avatars = colored circle + initials (first letter of name or account name)
- Colors from UIConfig.color via AccountColors.getColorForAccount() — deterministic per accountId
- Calendar color picker has only 8 swatches — should expand to 19
- Missing: provider icon inside avatar circle (BlueMail does this)

### Feature Completeness
- All core features work: add account, email sync/list/compose/reply/forward, calendar, contacts, tasks, search, notifications, encryption toggle
- Chat feature: Message entity + table exists, UI is search-only, no chat screen, no Messages tab in nav
- 4 bottom nav tabs: Inbox, Calendar, Tasks, People (no Chats tab)

---

## Part 2: BlueMail Chat Feature — Decompilation Findings

### APK: me.bluemail.mail v2.2.346, React Native 0.76.9 + 341 decompiled Java classes

**CRITICAL FINDING: BlueMail's chat is server-mediated, NOT peer-to-peer.**

From decompiled code and JS bundle strings:
- WebSocket to `wss://cha.onblix.com:443` (their chat server)
- REST API to `bluemailx.com` endpoints
- Firebase Cloud Messaging for push notifications
- Group/channel-based: `group_jid`, `room_id`, `channelId`, `GroupCreate`, `GroupAddMembers`
- Identity = email account ("Chat + Email in one place")
- Real-time via persistent socket: `sendOverSocket`, `sendMessageToServer`, `onmessage`, `pingCache`
- PGP encryption via `EncModule` (OpenPGP.js, hardcoded demo key)

**The "Ad-Hoc Chat"** — their closest thing to 1:1 — still routes through their servers.

**Conclusion:** BlueMail's chat REQUIRES their servers. Cannot be replicated without a server by copying their approach. However, UX patterns are transferable (conversation list, message states, typing indicators, read receipts, contact invite model).

---

## Part 3: Relay Server Research — Open Source Landscape

### What You Described Is Real and Common

The pattern: **client encrypts → sends ciphertext to relay → relay stores/forwards ciphertext → recipient fetches + decrypts**. Relay NEVER sees plaintext. This is exactly how Signal works, and dozens of open-source projects implement variations.

### Open-Source Options Evaluated

**1. Signal-Server (Java/Scala, 10,672 stars)**
Full Signal Protocol (X3DH + Double Ratchet + Sealed Sender). PostgreSQL required. Heavy. Overkill — not what you want.

**2. Berty (Go + Kotlin, P2P mesh)**
No central server, mesh networking, complex. Not a fit — you want a relay server, not P2P mesh.

**3. Matrix/Element (Python/Dovecot, federated)**
Full federated ecosystem. Complex. Server + client + homeserver + federation. Overkill for a lightweight chat.

**4. Simple encrypted relay (Python/Go/Rust) — THE PATTERN YOU WANT**
Store-and-forward ciphertext. WebSocket push. Bearer token auth. Message TTL. No key material stored. This is the lightweight pattern. Languages:
- **Python + FastAPI + SQLite** — ~500 lines, runs anywhere, easy to maintain. BEST FIT for self-hosting.
- **Go** — single binary, faster, no runtime deps. Good for high-traffic.
- **Rust** — fastest, safest, but more complex.
- **Node.js** — viable, large ecosystem.

**For self-hosted, low-traffic, portable:** Python + FastAPI + SQLite. This is what ManCom uses and it works.

### The ManCom Relay Already Exists and Is a Perfect Template

`/home/keith/ManCom/relay/` — 541-line Python FastAPI server. Already implements:

**API:**
- `POST /v1/register` — device registration → bearer token
- `POST /v1/send` — submit encrypted envelope (multi-recipient)
- `GET /v1/inbox` — fetch undelivered messages (cursor + mark_read)
- `GET /v1/identity/{id}` — fetch peer's identity keys
- `WS /ws` — real-time push (auth as first JSON)
- `POST /v1/register/rotate` — rotate bearer token
- `DELETE /v1/account` — wipe account + data
- `GET /healthz` — health check

**Security:**
- Zero-trust: stores ciphertext only, never has keys
- Bearer token auth (HMAC + SHA256)
- Rate limiting (60 req/min/account)
- Message TTL (7 days default, configurable)
- Auto-cleanup of expired messages
- Device tokens expire (365 days default)

**Schema (3 tables):**
- `accounts` — identity_pub, identity_sig, signed_prekey_pub, one_time_prekey
- `tokens` — bearer tokens per device, with expiry
- `messages` — msg_id, sender, recipient, ts, ciphertext, ephemeral_pub, chain_index, group_id, delivered_ts

**Runtime:** Python 3.11+, FastAPI + uvicorn + pydantic + websockets, SQLite. ~50MB RAM, negligible CPU, one port (8443), one process.

**This is exactly the architecture you described.** Lightweight, encrypted, store-and-forward, WebSocket push, self-hostable.

### Encryption Architecture (Client Side)

**Full X3DH + AES-256-GCM (Signal-like, v2):**
1. Generate identity key pair (Ed25519 or RSA-4096) — long-term, Android Keystore
2. Generate signed prekey (X25519) — rotated periodically, uploaded to relay
3. Generate batch of one-time prekeys (10-100) — uploaded to relay, consumed on use
4. Send: fetch recipient's keys → X3DH key agreement → AES-256-GCM session key → encrypt → POST ciphertext + ephemeral_pub
5. Receive: fetch ciphertext → try one-time prekey → fall back to signed prekey → X3DH → decrypt → verify signature

**Simpler Pre-Shared Key + AES-256-GCM (v1, faster to ship):**
1. Users exchange a "chat secret" via QR code or copy-paste (base64 string)
2. Derive AES-256-GCM key via HKDF
3. Encrypt/decrypt with that key
4. No forward secrecy, no deniability — but works and is much simpler

**Recommendation:** Start with pre-shared key + AES-256-GCM for v1. Add X3DH later as an upgrade path (not a rewrite — just swap the key derivation).

---

## Part 4: UnifiedComms Chat Architecture — Self-Hosted Relay

### Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                 RELAY SERVER (Python + FastAPI)         │
│  Stores ciphertext only. No keys. Self-hosted.          │
│  POST /v1/register    → device token                    │
│  POST /v1/send        → queue ciphertext                │
│  GET  /v1/inbox       → fetch ciphertext                │
│  GET  /v1/identity/{id} → peer keys                     │
│  WS   /ws             → real-time push                  │
│  Auto-cleanup after TTL                                 │
└─────────────────────────────────────────────────────────┘
      ▲                    ▲                    ▲
      │                    │                    │
      │ HTTPS (OkHttp)     │ HTTPS (OkHttp)     │ HTTPS (OkHttp)
      │ Bearer token       │ Bearer token       │ Bearer token
      │                    │                    │
┌─────┴─────┐      ┌──────┴──────┐      ┌──────┴──────┐
│UC Device A│      │UC Device B  │      │UC Device C  │
│Encrypts   │      │Encrypts     │      │Encrypts     │
│with AES-  │      │with AES-    │      │with AES-    │
│256-GCM    │      │256-GCM      │      │256-GCM      │
│           │      │             │      │             │
│Keys in    │      │Keys in      │      │Keys in      │
│Android    │      │Android      │      │Android      │
│Keystore   │      │Keystore     │      │Keystore     │
└───────────┘      └─────────────┘      └─────────────┘
```

### Identity Model

Two options:
- **Option A (phone-based):** Use phone number as chat identity (like ManCom). Clean, stable identifier. User registers with phone, receives bearer token.
- **Option B (email-based):** Use email address as chat identity. Fits UnifiedComms' email-centric model. Requires email verification or proof of ownership.

**Recommendation:** Option A (phone-based). Phone numbers are better chat identifiers — more stable, easier to discover. Email remains for IMAP/SMTP. Chat identity is separate from email account.

### Client-Side Components (Android)

1. **ChatRelayManager** — OkHttp client for relay API. Manages bearer token, handles registration, send, inbox poll, WebSocket connection.
2. **ChatCryptoManager** — Key generation (Android Keystore), X3DH or pre-shared key + AES-256-GCM encrypt/decrypt. Wraps existing CryptoManager patterns.
3. **ChatRepository** — Room DAO for local message cache (already exists: MessageDao, MessagingRepositoryImpl). Reuse.
4. **ChatSyncManager** — Polls /v1/inbox on timer, maintains WebSocket connection, handles reconnect.
5. **ChatUI** — Conversation list, chat detail, peer browser, message composer.

### Relay Adaptation from ManCom

The ManCom relay is phone-number-based. For UnifiedComms:

1. Change `phone` → `email` in accounts table (or add email as alias, keep phone as primary)
2. `POST /v1/register` takes `email` field
3. `GET /v1/identity/{email}` instead of `{number}`
4. Everything else (send, inbox, WebSocket, tokens, TTL, rate limiting) stays the same

Or keep phone-based and have UnifiedComms register with phone number as chat identity. Simpler with less schema change.

### Self-Hosting Model

Same as mailcow/SOGo:
- User provisions a VPS (or uses existing server)
- `pip install fastapi uvicorn pydantic websockets`
- `RELAY_TOKEN=changeme python -m uvicorn main:app --host 0.0.0.0 --port 8443`
- Configure UnifiedComms with relay URL (e.g., `https://chat relay.example.com`)
- One port, one process, ~50MB RAM

No third-party dependency. User controls the server. Matches the app's privacy philosophy.

---

## Part 5: Account Icon / Aesthetic Improvements

1. **Add provider icon to avatar**: For known providers (Google, Outlook, Yahoo, iCloud, etc.), show 16-20dp provider icon centered in colored circle + initials. For generic/custom, initials only. Makes accounts visually scannable.
2. **Expand calendar color picker**: 8 → 19 swatches (full AccountColors palette)
3. **Avatar depth**: Optional 1-2dp darker border or slight drop shadow on avatar circle
4. **Provider icon caching**: Load and cache provider drawables once
5. **Initials**: Keep current `getInitials()` fallback. For 2-letter, use first + last char.

---

## Part 6: Structured Plan

### Phase 1: Quick Fixes (1-2 days)
1. Remove dead `AccountColors.getColor(index: Int)`
2. Remove dead `res/layout/activity_add_account_manual.xml`
3. Remove dead `chat` entry from `SyncManager.testAllConnections`
4. Fix `SyncManager` to use `coroutineScope { }`
5. Fix `MainViewModel.respondToInvite` to reuse engine instance
6. Fix `SettingsScreen` theme mode to observe flow
7. Fix `AddAccountScreen` IMAP host fallback
8. Remove user CA trust from production network security config
9. Change account dump Log.d to Log.v
10. Expand calendar color picker to 19 swatches

### Phase 2: Relay Server (2-3 days)

**Option A: Adapt ManCom relay (RECOMMENDED — fastest path)**
1. Copy `/home/keith/ManCom/relay/` to new repo `unifiedcomms-relay/`
2. Change identity field from `phone` → `email` (or keep phone + add email alias)
3. Update README with UnifiedComms-specific setup instructions
4. Test locally: register two devices, send message, verify inbox poll + WebSocket push
5. Deploy to a test VPS or run locally for Android testing (port forward via adb reverse)

**Option B: Build from scratch (if ManCom adaptation doesn't fit)**
1. Create new `unifiedcomms-relay/` Python + FastAPI + SQLite project
2. Implement: register, send, inbox, identity, WebSocket, token rotation, account deletion, TTL cleanup
3. Same API surface as ManCom relay

### Phase 3: Android Chat Client — Crypto + Relay (3-5 days)

1. **ChatCryptoManager**: Key generation in Android Keystore, AES-256-GCM encrypt/decrypt, pre-shared key derivation (HKDF) for v1, X3DH skeleton for v2
2. **ChatRelayManager**: OkHttp client, bearer token storage (EncryptedSharedPreferences), register/send/inbox/identity API calls, WebSocket connection management, reconnection logic
3. **ChatSyncManager**: Timer-based inbox polling (WorkManager or Coroutine), WebSocket push handler, message intake → Room DB insert
4. Wire everything together: send message → encrypt → POST to relay → poll inbox → decrypt → display

### Phase 4: Chat UI (3-5 days)

1. Chat list screen (conversations, last message, unread badge, participant name + avatar)
2. Chat detail screen (message bubbles, composer, send button, read receipts)
3. Peer discovery/invite screen (share chat secret via QR or copy-paste, or enter email/phone to invite)
4. Message status indicators (sending/sent/delivered/read)
5. Add "Chats" tab to bottom nav (5th tab)
6. Conversation list with avatar, name, last message preview, unread count, timestamp

### Phase 5: Polish + Edge Cases (2-3 days)

1. Handle relay downtime (queue messages locally, retry on reconnect)
2. Token expiry handling (re-register on 401)
3. Multi-device support (same account, multiple tokens)
4. Local notifications for new messages (when app backgrounded, via WebSocket push)
5. Conversation search
6. Message delete (local only, or "delete for everyone" if both online)
7. Battery optimization (poll interval, WebSocket keep-alive)

### Phase 6: Account Icon Polish (1 day)

1. Provider icon overlay on avatars
2. Cache provider drawables
3. Avatar rendering with optional icon + initials

---

## Part 7: Feasibility

**With self-hosted relay — achievable:**
- Remote messaging (any internet connection) ✓
- Push notifications via WebSocket ✓
- Offline message delivery (store-and-forward) ✓
- Both users need the app ✓ (your requirement)
- End-to-end encryption (AES-256-GCM) ✓
- Self-hosted, no third-party dependency ✓
- Zero telemetry ✓

**Without relay — NOT achievable:**
- Remote messaging across networks
- Push when app fully closed
- Offline store-and-forward

**LAN P2P (fallback):** Still viable as a no-server option for same-network chat. Can coexist with relay — prefer relay when configured, fall back to LAN when no relay and same network.

---

## Part 8: Relay Server Quick Start (for testing)

```bash
# On the relay host (VPS or local machine)
cd /path/to/unifiedcomms-relay
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt

# Start
RELAY_TOKEN=mysecret python -m uvicorn main:app --host 0.0.0.0 --port 8443

# On Android test device, forward port for local testing
adb reverse tcp:8443 tcp:8443
# Now UnifiedComms connects to http://127.0.0.1:8443

# Test: register device A
curl -X POST http://localhost:8443/v1/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user1@test.com", "identity_pub": "<base64>", ...}'

# Test: send message from A to B
curl -X POST http://localhost:8443/v1/send \
  -H "Authorization: Bearer <token-A>" \
  -H "Content-Type: application/json" \
  -d '{"from": "user1@test.com", "to": ["user2@test.com"], "ciphertext": "<base64>", ...}'
```

---

## Part 9: Open Questions for You

1. **Identity model:** Phone-based (like ManCom) or email-based?
2. **Encryption v1:** Pre-shared key + AES-256-GCM (simpler, faster) or full X3DH + AES-256-GCM (more secure, longer to implement)?
3. **Relay repo:** Adapt ManCom relay as base, or start fresh?
4. **Relay hosting:** Your existing server, a new VPS, or local testing only for now?
5. **Chat tab:** "Chats" as 5th bottom nav tab, or separate section?
6. **Test accounts:** Do you have two email accounts + a phone number we can use for testing the relay?

---

## Appendix: ManCom Relay File Listing

```
/home/keith/ManCom/relay/
├── main.py          (541 lines, FastAPI server, full implementation)
├── schema.sql       (36 lines, 3 tables: accounts/tokens/messages)
├── requirements.txt (4 deps: fastapi, uvicorn, pydantic, websockets)
├── README.md        (API docs, quick start, protocol)
├── relay.db         (SQLite, created at runtime)
└── .git/           (independent git repo)
```

The relay is production-ready for the core send/receive flow. Groups API is stub (501). No SMS enrollment in the simplified register path (self-hosted, no SMS needed).
