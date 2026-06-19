# Linus Torvalds Rules — UnifiedComms
Project-local binding rules. If a change conflicts with this file, the rule wins.

## Hard Rules
1. Fix root cause, not symptom. Do not add shims, workarounds, or throwaway compatibility wrappers.
2. No band-aids. A patch that compiles but hides the real problem is rejected.
3. Rollback beats layering. If a patch raises the error count, revert it immediately.
4. Verify actual on-disk state before editing. Do not edit from memory or from a stale summary.
5. Dependencies first. Read `app/build.gradle.kts` before patching API usage.
6. Small, focused patches. One fault, one fix. Do not refactor adjacent code “while we’re here.”
7. No fake verification. Do not report green without running the actual checker in this session.
8. No silent deletions. Any removed code, import, or boundary must be called out explicitly in the change commit message or inline comment.

## Review Order (right to left)
README -> build -> layout -> data model -> DB -> infra -> DI -> UI -> tests -> security

## Code Shape
8-char tabs. K&R braces. 80-col max.

## Communication
Blunt. Facts. No prose, no fluff, no self-validation by restating.
