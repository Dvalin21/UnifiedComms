# Knowledgebase: Email Client Autodiscovery, Ports, Certs & Sync

Self-reference for UnifiedComms (Android email/calendar/contacts client).
Compiled from authoritative specs + reference OSS clients. Read before touching
account setup, autodiscover, port/cert selection, or the sync engine.

====================================================================
1. AUTHORITATIVE SPEC SOURCES (these are the ground truth)
====================================================================
- RFC 6186 — SRV records for email submission/access (IMAP/POP/SMTP).
- RFC 6764 — SRV + TXT + .well-known URI for CalDAV/CardDAV.
- RFC 8314 — "Cleartext Considered Obsolete": Implicit TLS preferred; port
             table; MUA cert/cleartext rules. OVERRIDES RFC 2595/6186 on
             TLS preference. This is the decisive doc for the user's port/cert
             demand.
- RFC 6125 / RFC 7817 / RFC 5280 — TLS server cert validation (identifier
  match + chain of trust).
- Reference clients: Thunderbird/K-9 Mail (setup wizard does SRV + heuristic
  fallback + connection test before saving), DAVx5 (the reference Android
  CalDAV/CardDAV client; integrates via Android AccountManager; syncs
  calendars/contacts into native providers).

====================================================================
2. AUTODISCOVERY — HOW REAL CLIENTS DO IT (MANDATORY BEHAVIOR)
====================================================================
From only an email address (local-part@domain), a client MUST derive config
with this priority order. ALL connectors (Google/OAuth excluded) MUST support
this — including Generic IMAP/SMTP and Generic CalDAV/CardDAV. Never assume
mail.<domain>.

ORDER (per RFC 6186 + 6764 + K-9 practice):
  1. DNS SRV lookup on the email's domain:
       Email:  _imaps._tcp.<domain>  -> IMAP SSL  (port 993)
               _submissions._tcp.<domain> -> SMTP SSL (port 465)
               _submission._tcp.<domain>  -> SMTP STARTTLS (port 587)
               _imap._tcp.<domain>   -> IMAP STARTTLS (port 143) [fallback only]
       CalDAV: _caldavs._tcp.<domain> -> https (443)
               _caldav._tcp.<domain>  -> http (80) [fallback only]
       CardDAV:_carddavs._tcp.<domain>-> https (443)
               _carddav._tcp.<domain> -> http (80) [fallback only]
  2. DNS TXT for CalDAV/CardDAV "path=" key (RFC 6764 §4) -> context path.
  3. HTTP .well-known URI redirect (RFC 6764 §5):
       GET https://<domain>/.well-known/caldav  -> 301/303/307 -> real path
       GET https://<domain>/.well-known/carddav -> same
       Client MUST follow redirects.
  4. Heuristic fallback ONLY if SRV/.well-known absent:
       imap.<domain>, mail.<domain>  on 993
       smtp.<domain>, mail.<domain>  on 465/587
       dav.<domain>/caldav, <domain>/caldav on 443
  NOTE: heuristic must try BOTH imap. AND mail. — the user's server is
  imap.houseofmanns.com / smtp.houseofmanns.com, NOT mail.houseofmanns.com.
  Pulling only mail.<domain> is a BUG (this is what bit the user).

- For OAuth providers (Google/MS/ Yahoo), config comes from the provider's
  documented endpoints, not SRV. Keep those separate.

====================================================================
3. PORTS & CERTS — EXPLICIT POLICY (user mandate + RFC 8314)
====================================================================
RFC 8314 says: implicit TLS preferred over STARTTLS; cleartext deprecated;
TLS 1.2+ required.

DEFAULTS (never ask, just do):
  IMAP    993  implicit TLS (SSL/TLS on connect)   — NOT 143 unless explicitly requested
  SMTP    465  implicit TLS (SSL/TLS on connect)   AND/OR 587 STARTTLS
           User said: "smtp 587 and imap 993, never 143 unless specifically
           requested by the connector." So: prefer 993/587(STARTTLS) or
           993/465(SSL). 143 (cleartext STARTTLS) is OPT-IN ONLY, gated behind
           an explicit "use insecure port 143" toggle the user must turn on.
  POP3    995  implicit TLS (if ever supported)
  CalDAV/CardDAV  443 https (never 80 plain) unless user explicitly opts in.

- Every port that is TLS MUST validate the server certificate (RFC 6125:
  hostname match + valid chain). On cert failure: FAIL THE SETUP, do not
  save, do not fall back to cleartext silently.
- "All ports should be using certs" = every connection is TLS; no plaintext
  auth. If a server genuinely has no cert, that's an explicit opt-in insecure
  mode, never the default.

====================================================================
4. THE FATAL BUG CLASS — "SAVED BUT SYNC FAILED"
====================================================================
User hit: "Account Saved, but sync failed: No login methods supported."
And: Generic pulled mail.houseofmanns.com only, never imap./smtp., and CalDAV/
CardDAV info was wrong/missing.

RULE (non-negotiable, from RFC 8314 §5.1 + §5.3):
  - An account MUST NOT be persisted until a REAL connection is established
    AND authenticated successfully (IMAP CAPABILITY / SMTP AUTH / DAV
    PROPFIND principal) over a TLS session that passes cert validation.
  - NEVER write credentials or account rows to the DB "optimistically" and
    sync later. If the connection test fails -> show the error, discard the
    in-memory draft, save NOTHING.
  - "No login methods supported" means the client connected but negotiated
    zero SASL mechanisms / no DAV principal. That is a hard failure at setup
    time, not a post-save sync complaint.

Correct setup flow (K-9-equivalent):
  1. User enters email + password (+ optional manual override).
  2. Autodiscover (SRV -> TXT -> well-known -> heuristic) builds candidate
     server list.
  3. For each candidate: open socket (implicit TLS on 993/465/443), validate
     cert, issue CAPABILITY (IMAP) / EHLO (SMTP) / PROPFIND (DAV).
  4. If a candidate authenticates (LOGIN/AUTH success, or DAV principal
     found), PROMOTE it to the confirmed config.
  5. Only after at least the email (IMAP) leg authenticates -> persist account.
     Calendar/Contacts legs are confirmed in the same flow; if DAV fails,
     either disable those syncs (with explicit user notice) or fail the whole
     add — DO NOT save a half-broken account silently.
  6. On ANY failure in step 3-4: report which server/port/cert/mech failed.
     Save nothing.

====================================================================
5. ANDROID-SPECIFIC GOTCHAS (cause of silent failures)
====================================================================
- minSdk 31 (Android 12). Default network security: CLEARTEXT DISABLED.
  Connecting to port 143/80/plain will be BLOCKED by the OS unless
  <uses-cleartext-traffic> or a domain-specific network-security-config
  allows it. So "default secure only" is also enforced by the platform — good.
  Do NOT add a blanket cleartext allowance; scope any exception to the exact
  domain the user opts into.
- Cert validation on Android: HttpsURLConnection / Mail libs use the system
  trust store. Self-signed servers (e.g. a home lab) will fail validation ->
  surface as a clear "certificate not trusted" error with an explicit
  (opt-in, setup-time-only, per RFC 8314 §5.4) "trust this cert" pin — NEVER
  auto-accept, NEVER click-through by default.
- CalDAV/CardDAV on Android: DAVx5 pattern = sync into the system
  Calendar/Contacts providers via SyncAdapter + AccountManager. If UnifiedComms
  rolls its own DAV sync, it must request READ/WRITE_CALENDAR and
  READ/WRITE_CONTACTS and register a SyncAdapter; missing permissions or a
  wrong principal URL = "failed to pull CalDAV/CardDAV" exactly like the user
  saw.
- Implicit-TLS sockets: open TLS immediately (SSLSocket on 993/465/443), do
  NOT send STARTTLS on those ports. STARTTLS only on 587/143/80 when the user
  opted into them.

====================================================================
6. WHAT THE USER ACTUALLY SAW (root causes to fix)
====================================================================
- Generic connector used only mail.houseofmanns.com -> should have tried
  imap.houseofmanns.com (993) and smtp.houseofmanns.com (465/587) via SRV/
  heuristic. Fix: heuristic must probe imap./smtp. subdomains, not just mail.
- CalDAV/CardDAV pulled wrong/empty info -> either no .well-known/SRV lookup,
  or wrong principal URL, or missing Android calendar/contact permission.
  Fix: run the RFC 6764 flow and verify a DAV principal before saving.
- "Saved, but sync failed" -> account was persisted before connection was
  proven. Fix: gate persistence on successful authenticated TLS connection.
- Certs present on both imap/smtp (user stated) -> so 993/465 implicit TLS is
  exactly right; the old code likely used 143/STARTTLS or plaintext and the
  OS blocked it, OR it skipped cert validation and "saved" a dead config.

====================================================================
7. TESTING CONTRACT (verify-by-doing, like the UI work)
====================================================================
- Reproduce the user's scenario against houseofmanns.com BEFORE claiming fixed:
  add a Generic account with user@houseofmanns.com, confirm the client
  discovers imap.houseofmanns.com:993 and smtp.houseofmanns.com:465/587,
  validates certs, authenticates, and ONLY THEN persists. If it cannot, the
  add must fail loudly with the specific reason — no silent save.
- Mock SRV in a test (e.g. dnsjava or a local resolver) to prove the SRV path
  is taken, not the mail. heuristic.

====================================================================
8. QUICK REFERENCE TABLE
====================================================================
Service   SRV label            Implicit-TLS port   STARTTLS fallback   Cleartext
IMAP      _imaps._tcp          993 (DEFAULT)        143 (opt-in only)   NEVER default
SMTP sub  _submissions._tcp    465 (DEFAULT)        587 (also fine)     NEVER
POP3      _pop3s._tcp          995                   110 (opt-in)        NEVER
CalDAV    _caldavs._tcp        443 (https)          80 (opt-in)         NEVER
CardDAV   _carddavs._tcp       443 (https)          80 (opt-in)         NEVER

Cert: validate on every TLS port. No validation -> fail setup.
Persist: ONLY after authenticated TLS connection proven. Never optimistic.
