# ADDENDUM — Deep Research (Round 2): Client Formats, Exchange, Cert Matching, DAV Integration

Appended to KNOWLEDGEBASE_EMAIL_AUTODISCOVER.md. Painfully deep pass:
real config formats used by shipping clients, the Exchange path, the
exact cert-matching algorithm, and how the reference Android DAV client works.

====================================================================
A. THUNDERBIRD / MOZILLA AUTOCONFIG (config-v1.1.xml) — THE DE FACTO STANDARD
====================================================================
Adopted by K-9 Mail, FairEmail, Evolution, KMail, Kontact, NextCloud Mail.
This is the "config file" tier every serious client supports. UC MUST parse it.

Locations tried (in order), per Mozilla wiki:
  1. autoconfig.<domain>/mail/config-v1.1.xml?emailaddress=user@domain
  2. <domain>/.well-known/autoconfig/mail/config-v1.1.xml
  3. Mozilla ISPDB (fallback if provider didn't publish): autoconfig.thunderbird.net/<domain>
  4. Heuristic (see B).

XML shape (the parts UC needs):
  <clientConfig version="1.1">
    <emailProvider id="example.com">
      <domain>example.com</domain>
      <incomingServer type="imap">           <!-- or pop3 -->
        <hostname>imap.example.com</hostname>
        <port>993</port>
        <socketType>SSL</socketType>         <!-- SSL | STARTTLS | plain -->
        <authentication>password-cleartext</authentication>  <!-- or OAuth2, password-encrypted, ... -->
        <username>%EMAILADDRESS%</username>  <!-- %EMAILLOCALPART% also valid -->
      </incomingServer>
      <outgoingServer type="smtp">
        <hostname>smtp.example.com</hostname>
        <port>587</port>
        <socketType>STARTTLS</socketType>
        <authentication>password-cleartext</authentication>
        <username>%EMAILADDRESS%</username>
      </outgoingServer>
      <addressBook type="carddav">            <!-- CalDAV/CardDAV block -->
        <username>%EMAILADDRESS%</username>
        <authentication>http-basic</authentication>
        <serverURL>https://contacts.example.com/remote.php/dav</serverURL>
      </addressBook>
      <calendar type="caldav">
        <username>%EMAILADDRESS%</username>
        <authentication>http-basic</authentication>
        <serverURL>https://calendar.example.com/remote.php/dav</serverURL>
      </calendar>
    </emailProvider>
  </clientConfig>

RULES DERIVED:
  - socketType "SSL" => implicit TLS (993/465/995). "STARTTLS" => 587/143/110
    with upgrade. "plain" => NO encryption (UC MUST reject by default).
  - When a provider publishes this, UC should PREFER the published secure port
    even if the user typed manual values. Thunderbird: "if SSL is available we
    configure SSL" even if the ISP's doc didn't recommend it.
  - CardDAV/CalDAV serverURL is given directly here — no need to SRV-probe when
    this block exists. This is likely WHY the user's CalDAV/CardDAV were wrong:
    UC's Generic path didn't consult this format NOR do RFC 6764, so it guessed
    and guessed wrong.

====================================================================
B. HEURISTIC FALLBACK (Thunderbird's exact order) — FIXES THE mail. BUG
====================================================================
When no SRV / no config file, Thunderbird tries, PER CANDIDATE HOST:
  imap.<domain>, pop.<domain>, pop3.<domain>, smtp.<domain>, mail.<domain>
and for each, the common 2-3 ports, checking SSL availability + CAPABILITIES.

CRITICAL: the order puts imap. BEFORE mail. UC's bug (only tried
mail.houseofmanns.com) would have been avoided by this exact ordering.
UC heuristic MUST iterate: [imap, smtp, pop, pop3, mail] x [993/465/995/587/143]
and PROBE each (open socket, read CAPABILITY/EHLO, detect TLS) rather than
guessing a single hostname.

====================================================================
C. MICROSOFT EXCHANGE AUTODISCOVER (for the "Exchange" tile)
====================================================================
3-phase candidate pool (Microsoft Learn, Autodiscover for Exchange):
  Phase 1 (candidate list):
    - SCP in AD DS  -> SKIP on Android (not domain-joined, no AD access).
    - From email domain:
        https://<domain>/autodiscover/autodiscover.xml
        https://autodiscover.<domain>/autodiscover/autodiscover.xml
  Phase 2: try each candidate in order, send request, validate.
  Phase 3: if all fail, DNS CNAME lookup for autodiscover.<domain> ->
           autodiscover.outlook.com (Microsoft 365 redirect).

Security (Microsoft, explicit):
  - Endpoint MUST be HTTPS.
  - SSL cert MUST be valid + from a trusted authority.
  - Never authenticate/send credentials to a non-SSL endpoint.
  - POX: HTTP POST an Autodiscover request body; response XML gives
    <EwsUrl>, <OABUrl>, server, etc.

For UC: the Exchange tile should (a) try the domain autodiscover.xml over
HTTPS with cert validation, (b) fall back to the well-known EWS endpoint,
(c) if Microsoft 365, follow the CNAME to autodiscover.outlook.com.
(Note: full EWS is heavy; for a mail client, often IMAP/SMTP via the same
 autodiscover XML is enough. Support at least the IMAP/SMTP fields from the
 Exchange autodiscover response.)

====================================================================
D. RFC 6125 §6 — EXACT CERT-IDENTITY MATCHING (what "validate cert" means)
====================================================================
"Validates cert" is NOT "trust anything." The algorithm (§6):
  1. Build REFERENCE IDENTIFIERS from the server hostname the client
     connected to (e.g. imap.houseofmanns.com).
  2. Get PRESENTED IDENTIFIERS from the cert's SubjectAltName (SAN):
     - DNS-ID  (dNSName)            <- primary
     - URI-ID  (uniformResourceIdentifier)  <- for https/DAV
     - SRV-ID   (for SRV-targeted)  <- optional
  3. Match (§6.4): compare reference DNS name to cert DNS-IDs.
     - Case-insensitive.
     - WILDCARD (*.example.com) matches exactly ONE label only
       (not *.*.example.com; not across a dot).
     - CN (common name) is checked ONLY as a fallback IF the cert has NO
       SAN (§6.4.4). Modern certs always have SAN; CN-only is legacy.
  4. Outcome (§6.6):
     - #1 Match found => accept.
     - #2/#3 No match, pinned cert present => accept only if pinned (setup-time).
     - #3 No match, no pin => REJECT (this is the failure UC must surface).
  Plus: cert must chain to a trusted CA (PKIX / RFC 5280), not be expired,
  key usage appropriate.

UC IMPLEMENTATION NOTE:
  - Do NOT implement cert validation by hand. Use the platform: for IMAP/SMTP
    over SSLSocket, set a TrustManager that uses the system trust store; let
    the JVM/Android validate the chain + hostname. For HTTPS (DAV), use
    HttpsURLConnection (validates system trust + hostname automatically).
  - Self-signed/home-lab certs => system trust fails => surface
    "certificate not trusted for <host>". Offer setup-time-only pin
    (RFC 8314 §5.4): bind cert to that hostname, per-account, revocable,
    MUST NOT count as "minimum confidentiality", MUST NOT auto-accept.
  - NEVER set a TrustManager that returns true for all certs. That is the
    "click through" anti-pattern RFC 8314 §5.2 forbids.

====================================================================
E. DAVx5 — REFERENCE ANDROID CalDAV/CardDAV CLIENT (integration pattern)
====================================================================
- DAVx5 (bitfire, GPLv3, on F-Droid) is THE reference open-source Android
  CalDAV/CardDAV client. If UC rolls its own DAV sync, copy its architecture:
    * Registers an Android Account via AccountManager.
    * Implements SyncAdapters for Contacts (RawContacts) and Calendars
      (through the CalendarProvider / icsdroid-style).
    * Syncs DAV collections discovered via the principal-URL into the
      system providers so other apps + the OS see them.
- Principal discovery (the part UC got wrong for CalDAV/CardDAV):
    1. From config: serverURL (from Thunderbird XML, or user entry).
    2. RFC 6764: GET https://<host>/.well-known/caldav (and /carddav),
       follow 301/303/307 to the context path.
    3. PROPFIND Depth:0 on the principal collection -> <current-user-principal>
       gives the user's principal URL.
    4. PROPFIND on principal -> <calendar-home-set> / <addressbook-home-set>
       gives the home collections.
    5. Enumerate child collections (calendar/addressbook) -> sync each.
  A wrong principal URL (or skipping steps 3-4) = "failed to pull CalDAV/
  CardDAV" exactly as the user reported. UC must complete principal discovery
  and verify at least one calendar + one addressbook collection BEFORE saving;
  if none found, either disable that sync (with notice) or fail the add.

- DAVx5 FAQ operational facts (relevant if UC integrates or competes):
  * HTTPS cert rejection => explicit "why" + setup-time trust option.
  * Sync not running => check battery optimization / sync toggle / account
    enabled. (UC must request REQUEST_IGNORE_BATTERY_OPTIMIZATIONS or at least
    document it, and ensure the Android sync toggle is on.)

====================================================================
F. SYNTHESIS — WHY THE USER'S SPECIFIC FAILURE HAPPENED
====================================================================
User: keith@houseofmanns.com, servers imap.houseofmanns.com + smtp.houseofmanns.com
(both behind SSL certs), CalDAV/CardDAV also hosted. UC pulled ONLY
mail.houseofmanns.com and never imap./smtp., then CalDAV/CardDAV were wrong,
then "Account Saved, but sync failed: No login methods supported."

Root causes, mapped to research:
  1. No SRV lookup AND heuristic only tried mail. -> B/Thunderbird ordering
     fixes (try imap./smtp. first).
  2. No Thunderbird XML parse, no RFC 6764 well-known for DAV -> CalDAV/CardDAV
     URLs wrong/missing. -> A + E.3.
  3. No principal discovery (PROPFIND current-user-principal) -> DAV had no
     valid collection to sync -> "failed to pull CalDAV/CardDAV". -> E.3.
  4. Account persisted before any authenticated TLS connection -> "Saved but
     sync failed / No login methods supported". -> gate persistence on a
     proven IMAP AUTH (and DAV principal) over validated TLS. RFC 8314 §5.1.
  5. "No login methods supported" = connected but zero SASL/DAV auth matched.
     That is a hard setup-time FAILURE, not a post-save complaint.

====================================================================
G. REVISED SETUP ALGORITHM (drop-in spec for the code rewrite)
====================================================================
Inputs: email, password, optional manual override.
Tier 1 (config file): fetch Thunderbird config-v1.1.xml from autoconfig.<d>,
  <d>/.well-known/autoconfig, ISPDB. Parse incoming/outgoing + carddav/caldav.
Tier 2 (SRV): _imaps/_submissions/_imap/_submission (RFC 6186) +
  _caldavs/_carddavs/_caldav/_carddav (RFC 6764). Honor priority/weight.
Tier 3 (well-known): GET /.well-known/caldav + /carddav, follow redirects.
Tier 4 (heuristic): for host in [imap, smtp, pop, pop3, mail]:
  probe 993/465/995/587/143; detect TLS + CAPABILITIES.
Tier 5 (Exchange): https://<d>/autodiscover/autodiscover.xml +
  https://autodiscover.<d>/... ; follow CNAME to outlook.com for M365.
Tier 6 (manual): user enters everything.

For EACH candidate (email + calendar + contacts):
  - Open TLS (implicit on 993/465/443; STARTTLS on 587/143/80 only if user
    opted insecure).
  - Validate cert per RFC 6125 (system trust; pin only at setup-time).
  - IMAP: CAPABILITY + AUTHENTICATE/LOGIN. SMTP: EHLO + AUTH.
    DAV: PROPFIND -> current-user-principal -> home-set -> collections.
  - If auth/principal SUCCEEDS -> mark candidate CONFIRMED.
If email leg confirmed:
  - Persist account (IMAP+SMTP).
  - If DAV confirmed -> enable calendar/contacts sync.
  - If DAV failed -> either disable those syncs with an explicit user notice,
    OR fail the whole add (configurable; default: disable + notify, because
    email-only is still useful). NEVER save a half-broken account silently.
If email leg FAILED:
  - Discard in-memory draft. Save NOTHING. Show specific reason:
    "Could not connect to <host>:<port> (cert/timeout/refused)",
    "No supported login method on <host>",
    "CalDAV/CardDAV principal not found at <url>".
No "Saved, but sync failed." That string must become impossible.
