# Android / Compose Knowledge Base
Facts extracted from the project and stable Android constraints.
If this file contradicts an instinctive guess, the file wins.

## Project Constraints
- AGP: 8.x style `build.gradle.kts`
- Kotlin coroutines lifecycle API: `collectAsStateWithLifecycle` is available
- Compose Material3 component library present
- Manual DI style; no Hilt

## Verified Authoritative Imports
- `androidx.lifecycle.compose.collectAsStateWithLifecycle`
- `androidx.compose.material3.*`
- `androidx.compose.material.icons.Icons` and `Icons.Default.*`
- `androidx.compose.ui.text.input.KeyboardOptions`, `KeyboardType`
- `androidx.compose.ui.text.style.TextOverflow`
- `androidx.compose.foundation.layout.*`
- `androidx.compose.foundation.background`

## Known API Validity From Compile Errors
False positives to remove:
- `ImageVector` alias placed into `material.icons.filled.ImageVector` (wrong import/usage)
- `size` without qualifier in Icon params (must be `Modifier.size`, not an Icon param)
- `overflow` without qualifier (must be `TextOverflow.Ellipsis`)
- `fillMaxHeight` exists, but missing import causes unresolved ref
- `widthIn` exists; missing import or wrong lib causes unresolved ref
- `aspectRatio` exists in foundation layout

## UnifiedComms Layout Rules
- Use Material3 Scaffold + TopAppBar pattern consistently.
- Account colors are `com.unifiedcomms.ui.theme.AccountColor` and `AccountColors.getColorForAccount(id)`; it returns a typed object, not Int.
- State belongs in ViewModel. Compose should read `StateFlow`/`Flow`, not `remember { mutableStateOf(list) }`.

## Email UX Facts
- Inbox: avatar/sender row + subject + time; unread = bold.
- Detail: top bar with back/star; body; action bar reply/forward/delete.
- Compose: To/Subject/Body with send FAB.
- Thread: bubbles, input anchored bottom.
