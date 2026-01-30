# CLAUDE.md

This file contains project-specific context and learnings for Claude Code.

## Project Overview

TestPilot is an Android APK testing framework that runs on pure JVM without emulators.

## Architecture

TestPilot uses **layoutlib** (Android Studio's rendering engine, 57MB, 26K+ classes) to provide the complete Android framework (`android.app.Activity`, `android.widget.*`, etc.) on the JVM. No hand-written shims or bytecode rewriting is needed — APK classes reference `android.*` types that resolve to real layoutlib classes via the parent classloader.

Key design decisions:
- **Layoutlib provides `android.*` classes** — the same library Paparazzi uses for view rendering
- **No bytecode rewriting** — APK code loads directly; `android.*` references resolve to layoutlib via parent classloader delegation
- **Reflection-based lifecycle** — Activity lifecycle is driven via reflection on package-private `performCreate()`, `performStart()`, etc. methods (same pattern as Robolectric's `ActivityController`)
- **`BinaryLayoutInflater` creates real `android.widget.*` instances** — `FrameLayout(context)`, `TextView(context)`, etc. from layoutlib, not shims
- **AndroidX types use FrameLayout placeholders** — `RecyclerView`, `ViewPager`, `CardView` are AndroidX (bundled in APK), not framework; `BinaryLayoutInflater` uses `FrameLayout` as fallback for binary XML inflation

## Build & Test Commands

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Run specific module tests
./gradlew :simulator:test
./gradlew :renderer:test

# Record golden images for visual regression tests
./gradlew :renderer:test -Dtestpilot.record=true

# Download test APK fixtures
./gradlew downloadTestFixtures

# Run APK loader
./gradlew :simulator:run --args="path/to/app.apk"
```

## Project Structure

```
testpilot/
├── simulator/     # APK loading, DEX conversion, resource resolution, activity lifecycle
├── renderer/      # Layoutlib-based rendering for screenshots
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

### Testing Best Practices

- Use `@EnabledIf("methodName")` for conditional tests (e.g., when test APK exists)

### Kotlin API Design

- Use `init` block for automatic initialization instead of separate `initialize()` method
- Use default parameters for optional arguments: `fun launch(activityClassName: String? = null)`
- `TestPilot` implements `AutoCloseable` — use `use {}` blocks for automatic cleanup
- `TestPilot` is the session: no separate `ActivitySession` class; lifecycle is managed internally by `launch()` and `close()`
- Kotlin nested block comments: `/*` inside `/** */` KDoc opens a nested comment level

### Layoutlib Integration

#### Why Copy Internal Classes?

The `renderer` module contains copied classes from `com.android.tools:sdk-common` (under `com/android/resources/`). This is necessary because:

1. **Layoutlib is Android Studio's internal component** - not designed for external use, no stable public API
2. **Version coupling** - layoutlib, layoutlib-api, and sdk-common versions must match exactly
3. **Missing classes** - published Maven artifacts may not include all required internal classes
4. **Community pattern** - Paparazzi uses the same approach, indicating this is a known pain point

#### Dependency Chain Complexity

A single `FrameworkResourceRepository.create()` call requires 26+ classes:

```
LayoutRenderer (our code)
└── FrameworkResourceRepository
    └── AarSourceResourceRepository
        └── AbstractAarResourceRepository
            └── AarResourceRepository (interface)
└── RepositoryLoader
    └── 15+ Basic*ResourceItem classes
    └── ValueResourceXmlParser, CommentTrackingXmlPullParser, etc.
```

#### Lessons Learned

1. **Static analysis is insufficient** - must verify with compilation before deleting "unused" files
2. **Interface dependencies are easily missed** - `implements`/`extends` relationships matter, not just `import` statements
3. **Safe deletion workflow**:
   ```bash
   # Move to backup first, don't delete directly
   mkdir -p /tmp/unused-backup && mv file.java /tmp/unused-backup/
   # Verify compilation
   ./gradlew compileJava compileKotlin
   # Delete backup only after confirmation
   ```

#### Technical Debt

This architecture is a "hack standing on giants' shoulders". When Google changes internal implementations, we must follow. This explains why Paparazzi version updates often lag behind Android Studio.

### Layoutlib as Android Framework Provider

- layoutlib (`com.android.tools.layoutlib:layoutlib`) is ~57MB and contains 26,000+ classes including the full Android framework (`android.app.Activity`, `android.widget.*`, `android.view.*`, etc.)
- No hand-written shims needed — layoutlib classes are the real framework implementation
- Paparazzi architecture: layoutlib + `BridgeContext` for view rendering
- Robolectric architecture: `android-all.jar` + `SandboxClassLoader` + Shadow system for lifecycle
- TestPilot's approach: layoutlib (like Paparazzi) + reflection lifecycle (like Robolectric)

### Reflection-Based Activity Lifecycle

- `LayoutlibActivityController` drives `android.app.Activity` lifecycle via reflection
- `performCreate(Bundle)`, `performStart()`, `performResume()`, `performPause()`, `performStop()`, `performDestroy()` are package-private methods on `android.app.Activity`
- Use `setAccessible(true)` to invoke them
- If a method is not found (API version differences), log a warning and skip

### Resource Configuration & Resolution

- `resources.arsc` contains multiple `RES_TABLE_TYPE_TYPE` chunks per type ID — one per configuration (e.g., `string` type appears once for default, once for `values-es`, once for `values-fr`, etc.)
- Each type chunk carries a `ResTable_config` binary struct (minimum 28 bytes) encoding locale, density, orientation, night mode, screen layout, SDK version, etc.
- Language/country are stored as 2-byte char arrays, not null-terminated strings — must trim `\u0000` after conversion
- `configSize` field at the start of the struct indicates how many bytes to read; later fields (screenLayout, uiMode, smallestScreenWidthDp, screenWidthDp/HeightDp) only exist if configSize is large enough (≥32, ≥36, ≥40)
- Night mode is encoded in `uiMode` bits: `(uiMode shr 4) and 0x3` — value 1 = not night, 2 = night
- Android's best-match algorithm is an elimination process with qualifier priority: locale → nightMode → density → orientation → screenSize → sdkVersion
- Density matching penalizes scaling up (lower density is worse than higher density for the same distance from target)
- Screen layout size matching: config size must not exceed device size (contradiction), and larger matching sizes are preferred

### Golden Image Testing

- Use record mode (`-Dtestpilot.record=true`) to capture baseline images
- Store golden images in `src/test/resources/golden/`
- Allow small tolerance (0.1%) for anti-aliasing differences across platforms
- Save actual + diff images on failure for debugging

## Code Style

- Package: `io.johnsonlee.testpilot`
- Simulator types use `DeviceConfiguration`, `AppResources`, `AppResourceResolver` (not Android SDK names, to avoid clashing with layoutlib)
- Use AssertJ for test assertions
- Commit messages follow conventional commits format
