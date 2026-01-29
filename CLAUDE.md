# CLAUDE.md

This file contains project-specific context and learnings for Claude Code.

## Project Overview

TestPilot is an Android APK testing framework that runs on pure JVM without emulators.

## Build & Test Commands

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run specific module tests
./gradlew :loader:test
./gradlew :simulator:test

# Download test APK fixtures
./gradlew downloadTestFixtures

# Run APK loader
./gradlew :loader:run --args="path/to/app.apk"
```

## Project Structure

```
testpilot/
├── simulator/     # Android API shim layer (View, Activity, etc.)
├── loader/        # APK loading, DEX conversion, bytecode rewriting
├── demo/          # Demo application
└── test-fixtures/ # Test APK files (gitignored, download via gradle task)
```

## Key Learnings

### Android Manifest Parsing

- Launcher activities can be defined in `<activity-alias>` elements, not just `<activity>`
- Must check both `activity` and `activity-alias` for `MAIN` + `LAUNCHER` intent-filters
- The `targetActivity` attribute in `activity-alias` points to the actual activity class

### JUnit Jupiter

- `@EnabledIf` annotation requires explicit dependency:
  ```kotlin
  testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.1")
  ```
- `kotlin("test")` alone doesn't include all JUnit Jupiter features

### Touch Event Dispatch

- Android touch event flow: `Window → ViewGroup.dispatchTouchEvent() → child.dispatchTouchEvent() → onTouchEvent()`
- ViewGroup must track touch target for subsequent MOVE/UP events after ACTION_DOWN
- Hit testing iterates children in reverse order (top-most first)
- `OnTouchListener` is called before `onTouchEvent()` and can consume the event

### Testing Best Practices

- Stub activities don't call `setContentView()`, so `window.contentView` is null
- For touch event integration tests, create test views manually and set as content
- Use `@EnabledIf("methodName")` for conditional tests (e.g., when test APK exists)

### Kotlin API Design

- Use `init` block for automatic initialization instead of separate `initialize()` method
- Use default parameters for optional arguments: `fun launch(activity: String? = null)`
- Return `this` for method chaining in builder-style APIs

### ASM Bytecode Rewriting

- Use `ClassWriter(reader, ClassWriter.COMPUTE_MAXS)` instead of `COMPUTE_FRAMES` to avoid frame computation errors
- Don't generate code body for native or abstract methods
- Use `ClassRemapper` for class reference rewriting

## Code Style

- Package: `io.johnsonlee.testpilot`
- Simulator classes mirror Android package structure: `io.johnsonlee.testpilot.simulator.view.View`
- Use AssertJ for test assertions
- Commit messages follow conventional commits format
