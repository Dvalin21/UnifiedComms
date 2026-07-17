# ADDENDUM 2 — Deep Protocol Research (Round 3): wire-level handshake, TLS rules, OAuth, Android integration

Appended to KNOWLEDGEBASE_EMAIL_AUTODISCOVER.md. This is the painfully-deep
pass: the actual protocol handshakes, the exact RFC rules that explain the
user's "No login methods supported" + wrong CalDAV/CardDAV, OAuth for the
brand tiles, and the canonical Android integration pattern.

====================================================================
H. RFC 2595 — USING TLS WITH IMAP/POP3 (the smoking-gun RFC)
====================================================================
This RFC is WHY the user's bug happened. Exact rules:

§2.2 Privacy mode: clients SHOULD refuse to authenticate UNLESS an encryption
  layer is active before/during auth, and terminate if it's deactivated.
  => UC MUST NOT send credentials on a non-TLS connection. Ever.

§2.3 Clear-text passwords: clients MUST be configurable to refuse ALL clear-text
  login commands/mechanisms unless an encryption layer of adequate strength is
  active. => Directly supports the user's "never 143 unless specifically
  requested" rule. 143 (cleartext IMAP) + LOGIN = forbidden by default.

§2.4 Server identity check (THE cert rule, predates 6125, same intent):
  - Compare the hostname UC USED TO CONNECT against the cert's server identity.
  - MUST NOT derive the hostname from insecure DNS (no CNAME canonicalization).
  - If cert has a subjectAltName dNSName, use that as the identity source.
  - Matching is case-insensitive.
  - "*" wildcard allowed only as the LEFT-MOST label (*.example.com matches
    a.example.com, NOT example.com).  <-- same as RFC 6125.
  - If match fails: ask for explicit user confirmation OR terminate.
  => For houseofmanns.com: connect to imap.houseofmanns.com, cert must have
     SAN dNSName imap.houseofmanns.com (or *.houseofmanns.com). No CNAME games.

§3.1 IMAP STARTTLS: after STARTTLS succeeds, the client MUST DISCARD cached
  capabilities and RE-ISSUE CAPABILITY. The server MAY advertise DIFFERENT
  capabilities post-TLS. Classic example in the RFC:
    C: a001 CAPABILITY
    S: * CAPABILITY IMAP4rev1 STARTTLS LOGINDISABLED   <-- no AUTH shown
    C: a002 STARTTLS
    S: a002 OK Begin TLS negotiation now
    <TLS>
    C: a003 CAPABILITY
    S: * CAPABILITY IMAP4rev1 AUTH=PLAIN                <-- AUTH appears NOW
    C: a004 LOGIN joe password
  => THIS IS THE USER'S BUG. If UC reads CAPABILITY once (pre-TLS) and never
     re-issues it after STARTTLS, it sees no AUTH mechanism and reports
     "No login methods supported" — exactly what the user got. FIX: always
     CAPABILITY -> (STARTTLS if offered) -> CAPABILITY again -> then AUTH.

====================================================================
I. SMTP AUTH + STARTTLS (RFC 4954 + RFC 3207) — exact dialog
====================================================================
RFC 3207 (SMTP STARTTLS) dialog (from Opportunistic TLS, RFC 3207):
  S: 220 mail.example.org ESMTP
  C: EHLO client.example.org
  S: 250 STARTTLS
  C: STARTTLS
  S: 220 Go ahead
  <TLS negotiation>
  C: EHLO client.example.org          <-- RE-EHLO over TLS
  S: 250 AUTH PLAIN                   <-- AUTH appears only now
  ...
Rule (RFC 4954 §3.3): the AUTH mechanism list "MAY change after a successful
  STARTTLS command." => UC MUST re-EHLO after STARTTLS before reading AUTH.
Same class of bug as IMAP: no re-EHLO => empty AUTH list => "No login methods
supported" on SMTP too.

SASL PLAIN (RFC 4616) wire format, MUST be base64 of:
  authzid NUL authcid NUL passwd
  (authzid empty for "act as self"; so just: "\0user\0pass" base64).
  - RFC 4616 §5: PLAIN SHOULD NOT be used unless adequate data security (TLS)
    is in place. RFC 4954 §14: MUST only use PLAIN over a TLS-protected session.
  => UC's password-based fallback = AUTH PLAIN, but ONLY after TLS. If server
     offers LOGIN instead, use LOGIN (same semantics, also TLS-only).
  - OAUTHBEARER / XOAUTH2: the mechanisms for Gmail/Outlook/iCloud "OAuth"
    tiles. These require the OAuth 2.0 token (RFC 6749), not the password.

====================================================================
J. OAUTH 2.0 (RFC 6749) — the brand "OAuth" tiles
====================================================================
- Authorization Code grant (§4.1) is the right flow for native apps:
  browser/redirect to provider -> user approves -> redirect back with
  ?code= -> UC exchanges code at the token endpoint for access_token +
  refresh_token -> uses access_token as the SASL OAUTHBEARER/XOAUTH2 cred.
- TLS required for all token endpoints (§1.6). Tokens, not passwords.
- Refresh token lets UC renew access without re-prompting.
- For the "Google"/"Outlook"/"Yahoo"/"iCloud" tiles: use the provider's
  OAuth discovery + token endpoints, store the refresh token (encrypted),
  and authenticate IMAP/SMTP via OAUTHBEARER. Fall back to password PLAIN
  (TLS) only if the provider/account doesn't support OAuth (e.g. generic).
- Android: use androidx Credential Manager / Authorization UI for the
  browser redirect so the password is never typed into UC for OAuth accounts.

====================================================================
K. CALDAV / CARDDAV PRINCIPAL DISCOVERY (RFC 4791 / RFC 6352) — why DAV was wrong
====================================================================
The user's CalDAV/CardDAV pulled wrong/missing data because UC skipped
principal discovery. The correct sequence (HTTPS, cert-validated):

1. Context path (RFC 6764): GET https://<host>/.well-known/caldav (and
   /carddav). Follow 301/303/307 to the server's DAV root.
2. PROPFIND Depth:0 on the DAV root -> <current-user-principal> (the user's
   principal URL).
3. PROPFIND on the principal URL:
     CalDAV: <calendar-home-set>  (RFC 4791 §6.2.1)
     CardDAV: <addressbook-home-set> (RFC 6352 §7.1.1)
   These give the HOME collections — NOT a guessed serverURL.
4. PROPFIND / REPORT on the home-set to enumerate child collections:
     CalDAV: calendar collections (REPORT calendar-query / calendar-multiget
             for sync, RFC 4791 §8.2.1.3 documents the sync process using
             ctag / sync-token).
     CardDAV: addressbook collections (REPORT addressbook-query /
              addressbook-multiget, RFC 6352 §8.7).
5. Sync each collection into the Android providers (see L).

=> If step 3 returns no home-set, there is no DAV for this account. UC must
   then EITHER disable calendar/contact sync (with a notice) OR fail the whole
   add — NOT save a half-broken account that "failed to pull CalDAV/CardDAV."

====================================================================
L. ANDROID INTEGRATION PATTERN (the DAVx5 / system pattern)
====================================================================
- Register an Android Account via AccountManager (one account per email
  identity). This is what makes the account visible in OS settings + other apps.
- Implement SyncAdapters:
    * Contacts SyncAdapter -> writes into RawContacts / ContactsContract.
    * Calendar SyncAdapter -> writes into the CalendarProvider.
  This is the canonical Android way (DAVx5 does exactly this). It means the
  user's contacts/calendars show in the system People/Calendar apps and sync
  respects the OS sync toggle + battery optimizations.
- DAVx5 FAQ operational notes:
    * HTTPS cert rejection => explicit "why" + setup-time trust option (pin).
    * Sync not running => check battery optimization / sync toggle / account
      enabled. UC must request REQUEST_IGNORE_BATTERY_OPTIMIZATIONS or
      document it, and ensure the Android sync toggle is on for the account.
- UC should NOT roll a private DB for contacts/calendars if it wants them to
  appear system-wide. Use the providers (or depend on DAVx5 for DAV and only
  do mail itself — a pragmatic split many clients use).

====================================================================
M. THE FIX, MAPPED TO RFC TEXT (so it's defensible)
====================================================================
User's exact failure: keith@houseofmanns.com.
  Symptom 1: pulled mail.houseofmanns.com, not imap./smtp.
    Cause: no SRV + heuristic only tried mail.  Fix: heuristic order
    [imap, smtp, pop, pop3, mail] (Thunderbird) + SRV (RFC 6186).
  Symptom 2: CalDAV/CardDAV wrong/missing.
    Cause: skipped principal discovery (RFC 4791/6352 §6.2.1/§7.1.1).
    Fix: well-known + PROPFIND current-user-principal -> home-set -> collections.
  Symptom 3: "Account Saved, but sync failed: No login methods supported."
    Cause: read CAPABILITY/AUTH pre-TLS, never re-issued after STARTTLS
    (RFC 2595 §3.1, RFC 4954 §3.3).  Fix: CAPABILITY -> STARTTLS -> CAPABILITY
    (IMAP) and EHLO -> STARTTLS -> EHLO (SMTP) before selecting AUTH.
    Then pick AUTH PLAIN/LOGIN (TLS-only) or OAUTHBEARER.
  Symptom 4: account was saved at all after failure.
    Cause: optimistic save.  Fix (RFC 2595 §2.2/§2.3): do NOT persist until a
    real authenticated TLS session is proven. Save only on success.

Port/cert policy (user mandate + RFC 8314 + RFC 2595):
  - IMAP 993 implicit TLS, SMTP 465/587.  143/25 cleartext = OPT-IN ONLY.
  - Every TLS port: validate cert (SAN dNSName, wildcard = 1 label, no CNAME
    canonicalization — RFC 2595 §2.4 / RFC 6125).  No "trust all".
  - Privacy mode: never authenticate without an active encryption layer.

This addendum + the earlier two files are the complete, RFC-grounded spec for
the UnifiedComms connection engine. Code work must follow it verbatim.
