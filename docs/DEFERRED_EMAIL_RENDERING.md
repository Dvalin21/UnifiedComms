# Deferred: Email body rendering (inline images, HTML fidelity, collapsible quotes)

Status: **intentionally deferred, not a bug**. Written 2026-09-25 after the Email/Tasks UI
polish pass. If someone asks "why don't inline images show in the message body?" or "why is a
newsletter hard to read?", the answer is in this file — do not re-derive it.

Authoritative current behaviour: `app/src/main/java/com/unifiedcomms/ui/main/EmailScreen.kt`
→ `EmailDetailScreen` body block (~line 565) + `internal fun squashBlankLines` (~line 768).

---

## 1. What the app does today (the baseline to change)

Body render chain, in order:

1. `e.bodyText` if non-blank → used as-is.
2. Else `e.bodyHtml` → `android.text.Html.fromHtml(html, FROM_HTML_MODE_LEGACY).toString()`.
3. Result passed through `squashBlankLines()` and rendered with Compose `Text(bodyLarge)`.

`squashBlankLines` does three things and nothing else:

```kotlin
internal fun squashBlankLines(raw: String): String =
    raw.replace(Regex("\r\n?"), "\n")            // CR-only and CRLF -> LF
        .trimEnd('\n', ' ', '\u00A0')              // drop trailing blank/nbsp lines
        .replace(Regex("\n{3,}"), "\n\n")         // 3+ blank lines -> one blank line
```

Covered by `app/src/test/java/com/unifiedcomms/ui/main/EmailBodyTest.kt` (2 tests). The same
helper is applied to the quoted body built for **reply** (~line 283) and **forward**
(~line 294), so quoted history is squashed identically there.

Consequences, stated plainly:

- **No images render in the body.** Compose `Text` cannot draw images. That is true today and
  was true of the previous WebView implementation too (see §3).
- **HTML formatting is lost** (bold, colour, links, tables, CSS layout) because the fallback
  flattens HTML to a plain string. Links survive only as visible URL-ish text.
- **No user setting** exists for any of this. There is no "always show remote images" flag,
  no "load images" button, no per-message image proxy.

---

## 2. Evidence snapshot (why this is deferred, not broken)

Pulled off the test device (`run-as com.unifiedcomms.debug cat databases/unifiedcomms.db`,
account `user@example.com`), 299 messages in the `emails` table:

| Query | Result |
|---|---|
| `bodyHtml` containing `<img` | **0** |
| messages with `bodyHtml` present | 218 (81 have no HTML at all) |
| messages with any attachment | 193 |
| attachments with `isInline: true` | **2** |
| attachments carrying a `contentId` | 6 (4 of them are PDFs/`invite.ics`, i.e. false positives) |

The two real inline-image messages:

- `Fwd: phillip jenkins shared "You've Been So Faithful" with you` — 4 inline PNGs
  (tracking/social icons, 1.7–7 KB each).
- `New Device Logged In From Chrome` — 2 inline PNGs, `logo-gray.png` and `mail-github.png`
  (a GitHub security notice; the *only* inline images in the mailbox are decorative logos).

Neither is content the user needs to read. In both cases the PNG is still listed in the
Attachments section and opens in the system viewer via `FileProvider`, so nothing is lost —
only the in-body placement is missing.

**Conclusion recorded at the time:** zero HTML mails in this mailbox contain images, so an
image pipeline would ship a mechanism with no payload. Do not build it speculatively.

---

## 3. Why the old WebView did not show inline images either (do not "restore" it blindly)

The pre-2026-09-25 implementation was:

```kotlin
AndroidView(factory = { ctx -> WebView(ctx).apply {
    setBackgroundColor(surfaceArgb)
    settings.javaScriptEnabled = false
    settings.blockNetworkImage = false
    settings.blockNetworkLoads = true          // <- kills every remote https image
    settings.forceDark = WebSettings.FORCE_DARK_OFF
    loadDataWithBaseURL(null, html, "text/html", "utf-8", null)   // <- no cid: resolution
}})
```

Three independent reasons inline images could never appear:

1. `blockNetworkLoads = true` blocks remote images (it overrides `blockNetworkImage = false`).
2. `cid:` references need a mapping from Content-ID to a real URI. `loadDataWithBaseURL(null, …)`
   with `baseUrl = null` provides no such origin, and no `ContentProvider` was registered for
   the message's parts. `cid:` therefore resolved to nothing (broken/empty box at best).
3. The WebView was sized with `heightIn(min = 80.dp, max = 600.dp)` because **Compose cannot
   measure a WebView's content height**. Every message — short or long — occupied a 600dp band.
   That empty block was the visible defect that prompted the change, and it is also why a naive
   "put the WebView back" reintroduces a regression.

Observed defects that motivated the change: white body block on the dark theme, dim/incorrect
text colour, and the fixed 600dp dead space.

---

## 4. Feature A — Inline images in the body (deferred)

### Trigger to build it
- A real (non-decorative) mail arrives whose text is unreadable without its inline image, **or**
- the `bodyHtml like '%<img%'` count on a real account goes from 0 to a meaningful number, **or**
- a user reports "the picture in the email doesn't show".

### What already exists (do not rebuild)
- `Attachment` model carries `contentId: String?` and `isInline: Boolean`
  (`app/src/main/java/com/unifiedcomms/data/model/Email.kt:213,215`).
- Sync populates both from the MIME part
  (`EmailSyncEngineImpl.extractAttachments` ~line 1139: `contentId` from
  `MimeBodyPart.contentID`, `isInline = Part.INLINE == part.disposition`).
- `MainViewModel.downloadAttachment(accountId, folder, uid, attachment)` → `fetchAttachment`
  writes the part to `cacheDir/attachments/<attachmentId>_<safeName>` and returns the path
  (`EmailSyncEngineImpl.fetchAttachment` ~line 1196).
- Size ceiling already enforced: `account.syncConfig.maxAttachmentSizeMb` (default 25,
  `data/model/Account.kt:313`) — `fetchAttachment` returns `null` above it.
- `FileProvider` is registered for `cacheDir` (`AndroidManifest.xml:135`, `res/xml/file_paths.xml`),
  so downloaded parts can already be handed to an external viewer as `content://`.

### Two viable designs (pick the smaller one that passes acceptance)

**A1 — Compose-native, no WebView (preferred).**
Render HTML through `Html.fromHtml` and map image spans into Compose inline content:

1. `HtmlCompat`/`Html.fromHtml` with a custom `ImageGetter` that, for each `src`:
   - resolves `cid:<id>` against `e.attachments` (`contentId == id`),
   - calls `viewModel.downloadAttachment(...)` (already exists) on first use,
   - returns a `BitmapDrawable`/`Bitmap` from the cached file.
2. Convert the `Spanned` to `AnnotatedString` by walking the `URLSpan`-style image spans and
   inserting `AnnotatedString.Range` entries whose `item` is a
   `Map<String, InlineTextContent>` — `InlineTextContent` is the supported hook for
   `drawText` + `drawInlineImage` inside a Compose `Text`.
3. **This is the only route that supports images with the current render chain** (no WebView,
   no 600dp problem, theme colours for free). Cost is real: async image loading, a
   `LinkedHashMap` cache keyed by `attachmentId`, and a placeholder box for not-yet-loaded
   images. The previous session verified `androidx.core.text.Spanned.toAnnotatedString()` is
   **not** available in `core-ktx:1.13.1` (checked: `androidx/core/text/` contains only
   `HtmlKt`, `SpannableStringBuilderKt`, `SpannedStringKt`, …) — the span walk has to be
   hand-written or done via `buildAnnotatedString { append(spanned) }` +
   `pushStyle` from the spans.
4. Known trap, already hit once: `buildAnnotatedString { append(spanned) }` on a
   `Spanned` produced visibly dim text in the app (the HTML carried colour spans that
   resolved against the dark theme). If images are added this way, pin the text colour
   explicitly or strip colour spans, or the body will regress again.

**A2 — WebView with a content bridge (only if CSS/layout fidelity is also required).**
See Feature B; images come along for the ride once `cid:` is resolvable. Do not do A2 just
for images.

### Acceptance criteria (Feature A)
- An inline PNG from `New Device Logged In From Chrome` renders in the body at its natural
  size, at its original position, on the dark theme, and does not shift surrounding text.
- The same message with remote-image blocking off/on behaves predictably (see §6 setting).
- Tapping a rendered inline image opens the existing system viewer path.
- No regression: body still fills the viewport with no fixed-height band; short messages
  are short.
- `maxAttachmentSizeMb` still respected; oversized inline images show a placeholder, not a
  crash.
- Test seam: the `cid → Attachment` resolution and the `Attachment → file` fetch are pure
  enough to unit test with a fake attachment list (no device needed).

---

## 5. Feature B — HTML fidelity via WebView (deferred)

### Trigger to build it
- A newsletter / HTML-only marketing or transactional mail arrives that is genuinely
  unreadable as flattened text (tables, two-column layout, styled signature blocks).
- A user asks for "HTML mail to look like it does in Gmail".

### The blocker, stated precisely
`AndroidView` in a Compose `Column` gets unbounded height, so the WebView must be given one.
Options, worst to best:

1. **Fixed height** (`heightIn(min = 80.dp, max = 600.dp)`) — what the app used; reintroduces
   the empty 600dp band. Rejected.
2. **Nested scrolling** — `Modifier.nestedScroll` / a `WebView` inside a `LazyColumn` item.
   Works, but nested scroll containers fighting over vertical drag is where this class of bug
   lives; measure before shipping.
3. **JS height bridge** — inject `loadDataWithBaseURL` with a script that posts
   `document.body.scrollHeight` and set the Compose height from that state.
   `settings.javaScriptEnabled` must flip to `true` *for this bridge only*, which is a
   meaningful security change for a mail renderer and needs an explicit decision, not an
   accident.
4. **Prefer a table-free path**: if the complaint is "the layout is ugly", try a styled
   `AnnotatedString`/`SelectionContainer` renderer first. Cheaper than any WebView option and
   keeps images off the network.

### If a WebView is ever added, these are non-negotiable requirements
- `cid:` resolution via a `content://` bridge (reuse the existing `FileProvider` over
  `cacheDir/attachments`, or a dedicated `ContentProvider` that streams from the MIME part on
  demand — do **not** pre-download every inline image during sync).
- Remote images stay blocked by default. Loading remote images means a tracking-pixel and
  beacon re-identification channel; the correct shape is a per-user opt-in (§6) plus a visible
  "images blocked — tap to load" placeholder, and the loaded state must not survive process
  death silently.
- JavaScript off unless the height bridge is chosen; `blockNetworkLoads = true` otherwise.
- Theme sync: inject `color-scheme` meta + a `!important` background/foreground override, or
  the dark-mode white block returns. The helper that did this (`themeEmailHtml`) was deleted
  with the WebView; if reintroduced it must be unit-tested for the `<head>` / `<html>` /
   neither cases (it originally mis-called `String.replaceFirst(Regex)` — that overload takes
   a `String` replacement, not a lambda; the working form is
   `html.replace(Regex(...)) { match -> ... }`).
- `webView` recycling: never hold a WebView across a message change; the old code used
   `key(dark, html) { … }` to force a fresh instance — keep that if the WebView returns.

### Acceptance criteria (Feature B)
- Short message: no dead space. Long message: scrolls, does not clip.
- Dark theme: no white block, text legible in both themes.
- Images: inline `cid:` renders; remote images blocked unless opted in.
- No WebView instance leaks across navigation (check with `adb shell dumpsys meminfo` or a
  heap snapshot after opening/closing 20 messages).

---

## 6. Optional companion setting (only meaningful once A or B exists)

If either feature lands, a per-account preference is the right shape:

- `syncConfig` already carries the analogous knobs (`maxAttachmentSizeMb`,
  `data/model/Account.kt`). A new field there follows the existing pattern and gets
  per-account semantics for free.
- Suggested field: `loadRemoteImages: Boolean = false` ("Always load images from the
  internet" — off by default, described in plain language next to the toggle, with the
  privacy consequence in the summary line).
- Do not add a "show remote images" toggle while the renderer cannot show any images; a
  setting that does nothing is worse than no setting.

---

## 7. Feature C — Collapsible quote blocks (deferred, lowest value)

### Current state
Reply and forward quote the full history, with blank runs squashed
(`EmailScreen.kt` ~line 283 / ~line 294). Nothing collapses, nothing is hidden.

### Trigger to build it
- Threads where the quoted history is many screens long and the user actually scrolls
  through it looking for their own reply, **or** a direct complaint about long threads.

### Design sketch (when it is worth it)
- Split the body at the first quote marker. Recognised markers, in priority order:
  `On <date>, <sender> wrote:` (what this app generates for reply),
  `-----Original Message-----`, `Forwarded message:`, and a line starting with `>`.
- Render the new text normally; render the quote collapsed behind a `TextButton`
  ("Show quoted text" / "Hide").
- Per-message state only — `rememberSaveable(messageId) { mutableStateOf(false) }`; do not
  persist a "collapsed" preference globally.
- Accessibility: the toggle must be a real button with a content description, and the
  collapsed state must be announced, otherwise a screen-reader user loses the thread.

### Acceptance criteria (Feature C)
- Tapping "Show quoted text" reveals exactly the quoted remainder, nothing else changes.
- State survives rotation but not navigation away and back (or the reverse — pick one and
  test it).
- No message whose body legitimately starts with a `>` character is mis-split; if the split
  is ambiguous, show everything (fail open, never hide content).

---

## 8. Do not regress (regression list from the 2026-09-25 pass)

- Body must not occupy a fixed 600dp band (that was the WebView defect).
- Body must follow the dark theme (that was the white-block defect).
- Blank-line runs and CR-only line endings must stay normalised (`squashBlankLines`).
- Reply/forward must keep prefilling `To` (reply) / `Subject` (`Fwd:` prefix) and the quote
  header lines.
- Attachment size display stays human-readable (`formatAttachmentSize`, `EmailScreen.kt`).
- Every list/detail action needs a `contentDescription`; the task Save icon and delete icon
  are the reference points.

## 9. Verification commands used for this pass

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug \
          :app:assembleRelease :app:assembleDebugAndroidTest --rerun-tasks
git diff --check
adb -s 10.0.0.242:40947 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 10.0.0.242:40947 shell screencap -p > /tmp/x.png 2>/dev/null   # NOT exec-out
```

Never run `connectedDebugAndroidTest` against the user's device — it can wipe app data.
Screenshots: `/tmp/v4-detail.png`, `/tmp/v4-sent.png`, `/tmp/v6-tasks.png`,
`/tmp/v7-blanktitle.png` (ephemeral; recapture rather than trusting these).
