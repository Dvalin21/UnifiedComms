# Contributing to UnifiedComms

Thank you for your interest in contributing! This document outlines the process and standards for contributing to UnifiedComms.

## Code of Conduct

By participating, you agree to maintain a respectful and inclusive environment. Harassment, discrimination, or abusive behavior will not be tolerated.

## How to Contribute

### Reporting Bugs
1. Check [existing issues](https://github.com/Dvalin21/UnifiedComms/issues) first
2. Create a new issue with:
   - Clear title and description
   - Steps to reproduce
   - Expected vs actual behavior
   - Device info (model, Android version)
   - Logs/screenshots if applicable

### Suggesting Features
1. Check existing issues and discussions
2. Create a feature request with:
   - Problem statement
   - Proposed solution
   - Alternatives considered
   - Mockups if UI-related

### Pull Requests
1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Make your changes following the guidelines below
4. Run tests: `./gradlew test`
5. Run lint: `./gradlew ktlintCheck detekt`
6. Submit PR with clear description

## Development Setup

### Prerequisites
- Android Studio Ladybug+ or IntelliJ IDEA
- JDK 17 (Temurin recommended)
- Android SDK 34
- Gradle 8.5+ (wrapper included)

### Environment Variables
Create `.env` or set in shell:
```bash
export GOOGLE_CLIENT_ID="your_id"
export MICROSOFT_CLIENT_ID="your_id"
export YAHOO_CLIENT_ID="your_id"
export APPLE_CLIENT_ID="your_id"
export PUSH_API_KEY="your_key"
```

### Build
```bash
./gradlew assembleDebug
```

## Code Standards

### Kotlin Style
- **8-char tabs** (not spaces)
- K&R braces: `if (condition) {`
- **80 column limit**
- Functions do ONE thing
- Types over macros/clever code
- No trailing whitespace

### Commit Messages
Follow Linus-style:
```
Subject line: imperative, ≤ 50 chars

Body: explain WHAT and WHY, not HOW
- Wrap at 72 chars
- Reference issues: Fixes #123
```

Examples:
```
Email: Fix IMAP folder sync for nested folders

SyncEngine now properly handles delimited folder names
when LIST returns nested structures with custom delimiters.
Fixes #45

Calendar: Preserve event colors from CalDAV server

Store EventColor entity separately, apply on event creation.
Prevents color reset on sync.
```

### Architecture Principles (Linus Philosophy)
1. **Data structures first** — Models before logic
2. **Pragmatism** — Measure, don't guess
3. **Simplicity** — >3 indent = fix design
4. **No broken windows** — Fix properly, document reasoning
5. **Small, focused patches** — One logical change per commit

### File Organization
```
data/
  model/     # Pure data classes (Account, Email, CalendarEvent, etc.)
  db/        # Room: Database, DAOs, Converters
  repository/ # Interfaces + impls
sync/        # Sync engines (Email, Calendar, Task, Contact)
security/    # Crypto, Biometric
ui/          # Compose screens, theme
widgets/     # Glance widgets
```

## Testing

### Unit Tests
```bash
./gradlew test
```

### Instrumented Tests (requires device/emulator)
```bash
./gradlew connectedAndroidTest
```

### Lint
```bash
./gradlew ktlintCheck detekt
```

### Coverage
```bash
./gradlew jacocoTestReport
```

## Adding New Providers

### Email/Calendar/Tasks Provider
1. Add `AccountType` enum value in `Account.kt`
2. Add defaults in `ServerConfig` companion
3. Implement sync engine: `*SyncEngine` + `*SyncEngineImpl`
4. Register in `SyncModule.kt`
5. Add OAuth config (if applicable) in `AddAccountActivity.kt`
6. Add strings in `strings.xml`
7. Update README with provider instructions

### Security Considerations
- All auth tokens encrypted via `CryptoManager`
- Certificate pinning in `network_security_config.xml`
- No auth data in logs
- Biometric re-auth for sensitive actions

## Release Process

1. Version bump in `app/build.gradle.kts` (`versionCode`, `versionName`)
2. Update `CHANGELOG.md`
3. Create release branch: `git checkout -b release/v1.x.x`
4. Build release: `./gradlew bundleRelease` (requires signing keys)
5. Create GitHub Release with AAB
6. Merge to master, tag: `git tag v1.x.x`

## Questions?

- [GitHub Discussions](https://github.com/Dvalin21/UnifiedComms/discussions)
- [Issues](https://github.com/Dvalin21/UnifiedComms/issues)

---

**Remember:** "Talk is cheap, show me the code." — Linus Torvalds