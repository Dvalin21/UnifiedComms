# Encryption: at-rest on device, E2E between app users, and Mailcow

Date: 2026-09-25. Research only — **no encryption code has been written**. This document is the
decision input. Every claim marked *verified* is backed by code we read, a Mailcow/Dovecot/NIST/
Proton/IETF source, or a device observation. Claims marked **unverified** were not confirmed and
must not be relied on.

Related: `docs/DEFERRED_EMAIL_RENDERING.md` (renderer work), and the `refactor(crypto)` commit that
deleted the unused P-256 chat stack.

---

## 1. What the app encrypts today (verified from source)

| Component | Algorithm | Scope | Key custody |
|---|---|---|---|
| `security/CryptoManager.kt` | AES-256-GCM, 12-byte IV, 128-bit tag | `AuthConfig` only: `passwordEncrypted`, `clientKey`, `clientCertificate`, `oauthAccessToken`, `oauthRefreshToken` | 256-bit AES key generated in `AndroidKeyStore`, non-exportable, never leaves the device |
| Room database | **none** | messages, tasks, calendar, contacts — all plaintext | — |
| `cacheDir/attachments` | **none** | downloaded attachment files stay on disk as plaintext | — |
| TLS in transit | whatever JavaMail negotiates | IMAP/SMTP/CalDAV/CardDAV | — |

Two gaps that are cheap to close and matter more than any new feature:

1. **The database is plaintext.** 299 messages sit in `unifiedcomms.db` as readable rows. A lost
   or unencrypted-backed-up device hands over the entire mailbox. Credentials are protected; the
   mail they unlock is not.
2. **Downloaded attachments are plaintext on disk.** `EmailSyncEngineImpl.fetchAttachment` (~line
   1232) writes each fetched part to `cacheDir/attachments/<id>_<name>` and opens it. Nothing ever
   removes it, so an attachment you opened once is readable by anything with filesystem access until
   the OS clears cache. This is the smallest, highest-value fix in this document: encrypt that
   directory with the existing Keystore key and delete on app start. Half a day, no UX change, no
   dependency.

---

## 2. Mailcow already encrypts your mail at rest — and the app already benefits

This is the most useful finding and it requires no app code. *Verified* from
`docs.mailcow.email/manual-guides/Dovecot/u_e-dovecot-mail-crypt/` and `doc.dovecot.org`:

- **Mailcow enables Dovecot `mail_crypt` by default.** Messages are stored **lz4-compressed and
  encrypted with AES-256-GCM**, with per-file symmetric keys wrapped by a provisioned EC key pair
  held in the `crypt-vol-1` volume.
- **It is transparent to IMAP/SMTP clients.** Encryption and decryption happen inside Dovecot. Your
  app receives and sends ordinary plaintext mail over TLS and never learns the difference. The
  server-side storage layer is already encrypted on your behalf.
- **Limits worth knowing** (`doc.dovecot.org/2.3/configuration_manual/mail_crypt_plugin/`): AES-GCM
  caps a single message at 64 GiB after compression, and the `fs-crypt` layer requires *all* files
  in the store to be encrypted — mixing encrypted and plaintext maildirs is not supported.

The residual risk is key custody: the EC private key lives in the same Docker host as the data, so
whoever gets root gets both. The Mailcow community's own answer is host-level encryption — LUKS on
`mailcowdockerized_crypt-vol-1` — at the price of a manual unlock step at every boot. That is an
infrastructure task, not an app task, and it is the correct place to spend the effort if the threat
is disk theft or a decommissioned drive.

### Mailcow measures we can actually use from the app

| Mailcow measure | What it protects | App-side action |
|---|---|---|
| `mail_crypt` at-rest encryption (default on) | Server disk/maildir at rest | **None** — already transparent. Document it so nobody re-derives it |
| 2FA: TOTP, WebAuthn, Yubico OTP | Account takeover via password theft | The main password is **rejected for IMAP/SMTP** once TFA is on; only an **app password** works (mailcow 2025-03 release). The app already models this as `AuthConfig.AppPassword` — keep it, and stop suggesting the account password in onboarding/UI copy |
| Per-mailbox "allowed protocols" | Reducing the attack surface per user | None; but it means the app must handle "auth rejected because the wrong password type" gracefully and say *app password required* rather than "wrong password" |
| DANE/TLSA via `postfix-tlspol`, MTA-STS policy UI, DNSSEC via Unbound, per-domain TLS policies | Downgrade and MITM on the server side | None. Note the app's `acceptAllCerts` field: several live tests set it `true`. That flag is a footgun in user-facing code — strict verification should be the default and the flag should not be reachable from the UI |
| ManageSieve on 4190 | Server-side filtering without downloading everything | Genuinely useful: server-side rules for newsletters/filing, driven by a small Sieve editor. Not encryption, but it is Mailcow capability the app ignores |
| ClamAV + quarantine, Rspamd, per-user spam score, blocklists | Inbound malware and spam | Expose quarantine as a folder so quarantined mail is visible in-app instead of vanishing |
| Dovecot full-text (Flatcurve) | Server-side search | **Becomes unavailable for E2E mail** — see §5 |

---

## 3. Encrypting mail at rest on the device — four real options

| Option | Mechanism | Protects against | Costs / breaks |
|---|---|---|---|
| **A. Android file-based encryption** (already on) | OS-level, per-credential class | Casual access while the device is locked; a lost device that is later wiped | Not app-controlled; useless against root, ADB backup, or an unlocked-running device. Zero work |
| **B. SQLCipher for the whole Room DB** | `SupportFactory` wrapping SQLite; **new native dependency** | Anything reading the file: device theft, unencrypted backups, forensic image | SQL still works (pages are decrypted in memory), so your indexes and search keep functioning. Needs an in-place migration of the existing DB, a second key-derivation path, and it must be measured for query latency. Largest single win available |
| **C. Field-level encryption of `bodyHtml`/`bodyText`** | Reuse the existing Keystore key and `CryptoManager.encrypt` | Same as B, minus the dependency | **Breaks message-body search** — there is an `index_emails_subject_sender_bodyText` index and in-app search over bodies. You would need a blind index (HMAC token per word) to keep search working, which leaks equality/frequency of terms to anyone holding the DB. Surgical, but the search story gets ugly fast |
| **D. Proton-style passphrase vault** | Argon2id(passphrase) → wraps a random DB key; device stores only the wrapped key | Device theft *even if the Keystore is dumped*; nothing recoverable without the passphrase | Passcode UX, lockout/recovery design, no widgets/quick-glance, backup rules become ciphertext. This is the only option that survives "attacker has the phone *and* runs code as your app UID" |

**Recommendation: B now (whole-DB encryption, search keeps working), plus the attachment-cache fix,
plus C never.** D is the strongest but it is a product change disguised as a security change — it
needs a recovery story before it is worth shipping, and it is strictly worse for daily use than
B. Sequence: attachment cache → B → revisit D only if the threat is a stolen-and-unlocked device
with app-level code execution.

Two non-obvious consequences of B to plan for: `autoBackup` must be considered (a Room file that
cannot be decrypted on restore is a brick, not a security feature — decide explicitly whether
backups are excluded), and the widget/quick-glance path that reads the DB outside the unlock flow
will need to stop.

---

## 4. End-to-end encrypted mail between two UnifiedComms users

**The key architectural fact: E2E mail needs nothing from the server.** The sender encrypts to the
recipient's public key; Postfix and Dovecot only ever carry ciphertext. It works identically on
Mailcow, Gmail, or Proton. So this is a client feature, not a server negotiation — which is good
news for the cost and bad news for key distribution (see below).

### 4.1 What "sending encrypted mail at most" would take

1. **Crypto layer.** `org.bouncycastle:bcpg-jdk18on` for OpenPGP (generate/encrypt/decrypt/sign),
   plus MIME assembly/parsing for RFC 3156 PGP/MIME. The app already parses MIME with JavaMail
   (`javax.mail.internet.MimeMessage`); the PGP/MIME wrapper is `multipart/encrypted;
   protocol="application/pgp-encrypted"` wrapping an armored `multipart/mixed`. Proton explicitly
   recommends PGP/MIME over inline PGP for the extra privacy (*verified*, `proton.me/support/how-to-use-pgp`).
2. **Key management.** Generate a Curve25519 keypair per address, protected at rest by the existing
   Keystore key, plus a user-exportable backup (Proton's "recovery file" pattern — the key is
   useless if the user cannot restore it).
3. **Key distribution — the real cost.** This is not a crypto problem, it is a trust problem. If
   the public key arrives via the same IMAP server you are trying to distrust, an operator who
   controls the server can substitute their own key and MITM you. Proton solved this with Key
   Transparency (audited append-only log + inclusion proofs). A credible minimum for us:
   publish a key per address, show the fingerprint, let the user verify it out of band, and **warn
   loudly on every key change** (Proton's Address Verification 2 is the manual version of this).
   Fetching keys from `WKD` or `keys.openpgp.org` works for non-app recipients, but such keys are
   by definition unverifiable by KT.
4. **UX.** "Send encrypted" toggle, a lock badge, key-change warnings, "encrypted but I can't read
   this" states for a contact without a key. Most of the cost is here, not in the crypto.
5. **Failure modes to design for:** multi-device key sync, lost phone with no backup, a contact who
   loses their key, and a contact who simply doesn't do PGP.

### 4.2 Interoperability with Proton Mail — the important finding

- Proton supports PGP with non-Proton recipients. Their own guidance: you send PGP/MIME to a
  non-Proton contact by sharing your public key, and the recipient needs a PGP-capable client
  (*verified*, `proton.me/support/how-to-use-pgp`). **So a UnifiedComms PGP implementation would
  interoperate with Proton Mail users.** That is the only realistic cross-provider path.
- Proton's import requirements tell you what a usable key must look like: a primary key with a
  single matching user ID, **at least one encryption-capable subkey**, RSA ≥ 2048 or ECC on
  x25519/P-256/P-384/P-521, AES-256 + SHA-256 + ZLIB advertised, no ElGamal, no DSA
  (*verified*, `proton.me/support/importing-openpgp-private-key`).
- **S/MIME interop with Proton is UNVERIFIED.** We did not find authoritative confirmation of what
  Proton's clients can send or accept. Do not design around it. SOGo (the Mailcow webmail) does have
  S/MIME, but a 2018 EFF analysis found critical S/MIME parsing vulnerabilities ("EFAIL"), and
  self-issued certificates are weaker provenance than self-generated PGP keys anyway — so S/MIME is
  the weaker choice on two independent grounds.
- Proton's PGP key format after their 2023 crypto refresh protects each private key with **two**
  symmetric keys (an unlock passphrase plus the account password) and adds designated-revoker,
  superseded-key and attestation mechanisms (*verified*,
  `proton.me/blog/openpgp-crypto-refresh`). If we interoperate with Proton, our key handling
  should be conservative and boring: Curve25519 + AES-256, and a user-exportable backup.

### 4.3 What E2E mail does **not** fix — be honest with the user

- **Metadata stays in the clear.** PGP/MIME must leave headers unencrypted or the mail system
  breaks. Sender, recipient, subject, timestamps, sizes, and IP addresses are all visible. For most
  threat models that is the difference between "private" and "anonymous".
- **No forward secrecy.** PGP is not forward-secret. A device whose private key is later compromised
  exposes its entire historical mailbox. Proton has the same property.
- **Server-side full-text search dies** (Mailcow's Flatcurve cannot index ciphertext), so search
  becomes client-side only — which loops back to §3B: to search you need the plaintext on the
  device, so at-rest encryption and E2E are complements, not alternatives.
- **Size blow-up.** PGP-armored text inflates ~33%, and binary attachments re-encoded through
  base64-inside-MIME-inside-armor can approach 2.7×. Mailcow's configured message-size limit must be
  checked before promising large attachments; Proton chunks oversized messages and we would need an
  equivalent strategy.
- **Spam filters and gateways** see `multipart/encrypted` and may quarantine. Expect support load.
- **DKIM/DMARC still work** — the signed body is the ciphertext, so Mailcow's outbound signing is
  unaffected.

---

## 5. Threat model: which layer stops what

| Threat | Current | + attachment-cache fix | + §3B (SQLCipher) | + §4 (PGP/MIME) |
|---|---|---|---|---|
| Stolen, locked device | Mostly (Android FBE) | Same | Same | Same |
| Stolen device, attacker reads app data as your UID | **No** | Attachments only | DB unreadable | DB unreadable |
| Stolen device *and* Keystore dumped | No | No | No (Keystore key is hardware-bound, so still OK) | No |
| Cloud photo/backup of app data | **No** | Attachments only | Yes | Yes |
| Mailcow host compromise / stolen disk | Yes (mail_crypt) | Yes | Yes | Yes for stored mail, **No for mail in flight and for metadata** |
| Malicious/curious mail provider (incl. your own server admin) | **No** | No | No | **Yes for body, no for metadata** |
| Legal compulsion of the provider | **No** | No | No | **Yes for body** |
| MITM on the wire | TLS only (ECDHE ⇒ forward-secret) | Same | Same | Same + signed/encrypted payload |
| Account takeover via password theft | App password + 2FA on the server side | Same | Same | Same |

Read the table as two orthogonal axes: **at-rest encryption protects the device; E2E protects the
mail from everyone else.** Proton is strong on both. Doing only one is a coherent, useful
improvement — it is not half a solution, it is a different solution to a different problem.

---

## 6. Ranked recommendation

1. **Encrypt the attachment cache** (§1 gap 2) — smallest change, no UX, closes a real hole.
   Half a day.
2. **Adopt Mailcow's existing measures properly** (§2) — app passwords for IMAP/SMTP, stop
   surfacing `acceptAllCerts`, consider an LUKS volume on the server, and surface quarantine. No new
   crypto; mostly copy and defaults. One day.
3. **Whole-database encryption, SQLCipher** (§3B) — the single biggest device-side win, keeps
   search working, needs a migration and a measured latency check. Several days including the
   migration and a real-device test on the 4 GB device class.
4. **PGP/MIME E2E mail** (§4) — only if there is a real user for it. Largest project in this
   document: key lifecycle, backup, verification UX, interop testing against Proton, and honest
   copy about metadata. Weeks, not days, and it needs a written key-management spec first.
5. **Passphrase vault (D)** — only after #3, and only with a recovery design.

**Do not do:** add post-quantum KEMs to the credential store (§ nothing there to break — AES-256 is
already quantum-resistant and the key never leaves the Keystore); use S/MIME as the E2E path (weaker
provenance, and Proton interop unverified); build a bespoke "UnifiedComms-to-UnifiedComms only"
encryption scheme that non-app clients cannot read (that is not encryption, it is a walled garden
with extra steps).

---

## 7. Open questions, unanswered

- **UNVERIFIED** — Proton S/MIME send/receive support. Blocks nothing in the recommended order.
- **UNVERIFIED** — the current Mailcow message-size limit on your instance. Needed before promising
  encrypted attachments; measurable in one command on the server.
- **UNVERIFIED** — whether your JavaMail stack negotiates TLS 1.3 with ephemeral ECDHE against
  `imap.*` / `smtp.*`. If it does, transit is forward-secret against a future quantum attacker; if
  it falls back to an RSA key-exchange suite, that is a real (future) risk worth fixing now.
- **UNVERIFIED** — GnuPG/Thunderbird support for RFC 9980 PQ algorithms. Relevant only if we ever
  ship hybrid ML-KEM keys; the spec exists, the ecosystem is what it is.
- Not investigated: Android Backup/autoBackup interaction with a SQLCipher database, and whether the
  home-screen widget reads the DB outside the unlock flow. Both are prerequisites for #3.
