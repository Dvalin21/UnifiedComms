# Email Client UX — Compose Patterns
Concrete patterns, not generic prose.

## Surface Contracts
- Inbox — list of conversation/message rows. Readable density > beauty.
- Conversation — message bubbles with timestamp.
- Compose — form scaffold; send button prominent and reachable; cancel is destructive/discrete.
- Detail — action bar, back first, then actions.

## Interaction Rules
- Swipe to archive/delete.
- Pull-to-refresh.
- Selection mode via long-press; contextual actions in top bar.
- Empty states with illustration and one clear CTA.

## Accessibility
- contentDescription on every icon button.
- Touch target >= 48dp.
- Color not the only indicator; include icon/text for unread.

## Search/Filter
- Search field in top app bar expands.
- Filter chips for starred/unread/attachments.
