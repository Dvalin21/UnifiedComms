# Android Development Knowledge Base
Hard constraints for UnifiedComms. Consult before any edit.

## Toolchain
- Android Gradle Plugin 8.x
- Kotlin 1.9.x+
- Compose BOM/material3 stable
- Room for persistence
- Coroutines + Flow for async
- Material3 design system only; do not mix Material2 APIs

## Compose Hard Rules
1. Every composable is a function; imports must resolve to actual API surface.
2. StateFlow bridging: `val x by viewModel.flow.collectAsStateWithLifecycle()`
3. String resources: never hardcode user-visible strings in production files.
4. Theming: use `MaterialTheme.colorScheme.*`; never raw ints for colors.
5. Modifiers are immutable chains; do not call `.widthIn(max = size.dp)` and then `.maxWidth` on `Constraints`.
6. Experimental APIs require either `@OptIn` or stable replacement.

## Architecture
- Single Activity, Compose Navigation
- ViewModel per screen; state flows down, events up
- Repository pattern; DAO/Room in data layer
- Manual DI in MainActivity via ViewModel factory

## Email Client UX Patterns
- Inbox surface: avatar, sender, subject, preview, time
- Conversation/detail: bubbles for in/out, input anchored at bottom
- Actions: reply, forward, delete, archive, read/unread, star
- Compose: recipient, subject, body, attachments placeholder, send

## What To Avoid
- `remember { mutableStateOf(list) }` for shared screen state
- Raw `Color(int)` construction
- `ImageVector` inside `material.icons.filled.*`
- `Scaffold` inside non-composable context
