# TestPilot

## Overview

TestPilot enables **testing any Android APK** on standard JVM without emulators or physical devices. By converting DEX bytecode to JVM bytecode and using Android's official layoutlib for framework classes, developers can run fast, reliable tests directly on their development machines or CI servers.

## Core Idea

**"Test any APK, anywhere JVM runs"**

Input: APK file → Output: Test results on pure JVM

```kotlin
// Load APK and launch the default launcher activity
val app = TestPilot.load("app.apk")
val session = app.launch()

// Access the real android.app.Activity instance
val activity = session.getActivity()

// Lifecycle control
session.pause().resume().stop().destroy()

// Render any layout XML as a pixel-perfect screenshot (via layoutlib)
val screenshot = session.takeScreenshot("""
    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Hello World" />
    </LinearLayout>
""")
ImageIO.write(screenshot, "PNG", File("screenshot.png"))
```

## Product Direction

Replace Maestro + Emulator with pure JVM execution for faster, more reliable testing.

### Architecture Evolution

**Current State:**
```
┌─────────────┐     ┌─────────┐     ┌───────────────────┐
│ Test Cases  │ ──▶ │ Maestro │ ──▶ │ Emulator / Device │
└─────────────┘     └─────────┘     └───────────────────┘
                                              │
                              Slow startup, resource heavy, CI struggles
```

**Target State:**
```
┌─────────────┐     ┌───────────────┐     ┌───────────────┐
│ Test Cases  │ ──▶ │ TestPilot SDK │ ──▶ │ App Simulator │
└─────────────┘     └───────────────┘     └───────────────┘
                            │                     │
                      APK as input      Pure JVM, instant startup
```

### Value Proposition

| Pain Point | Current State | With TestPilot |
|------------|---------------|----------------|
| CI Startup Time | Emulator cold start 30-60s | JVM process < 2s |
| Parallelization | Limited by KVM/machine resources | Dozens of instances on single machine |
| Flakiness | System animations, popup interference | Fully controlled environment |
| Debug Capability | adb logcat log retrieval | Direct breakpoints, same process |
| Input | Source code + build | **Just APK** |

## How It Works

```
┌─────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   APK   │ ──▶│   Unpack    │ ──▶│ DEX → JVM   │ ──▶│   Run on   │
│         │    │             │    │  (dex2jar)  │    │    JVM      │
└─────────┘    └─────────────┘    └─────────────┘    └─────────────┘
                    │                                      │
                    ▼                                      ▼
              resources/                          android.* references
              AndroidManifest.xml                 resolve to layoutlib
                                                  via parent classloader
```

### Pipeline Steps

1. **APK Unpacking**: Extract classes.dex, resources, AndroidManifest.xml
2. **DEX → JVM Conversion**: Convert Dalvik bytecode to JVM bytecode using dexlib2 + ASM
3. **ClassLoader Setup**: Load converted classes with layoutlib as parent classloader — `android.*` references resolve to real layoutlib framework classes
4. **Activity Lifecycle**: Drive `android.app.Activity` lifecycle via reflection on package-private `performCreate()`, `performStart()`, etc.

## Technical Challenges

### 1. DEX → JVM Bytecode Conversion
- Android uses DEX/ART bytecode format, JVM uses class files
- Solution: Use dexlib2 for DEX parsing + ASM for JVM bytecode generation
- Risk: Some edge cases may not convert perfectly

### 2. Android Framework Classes
- APK code references `android.*` packages that don't exist on standard JVM
- Solution: Use **layoutlib** (Android Studio's rendering engine, 57MB, 26K+ classes) which contains the full Android framework (`android.app.Activity`, `android.widget.*`, `android.view.*`, etc.)
- APK classes resolve `android.*` references to layoutlib via parent classloader delegation — no bytecode rewriting needed

### 3. Native Code (JNI) - Biggest Risk
- Many APKs contain .so native libraries
- These cannot run on standard JVM
- Solution: Stub/mock JNI calls, or provide pure-Java alternatives
- Scope limitation: Apps heavily dependent on native code may not be fully testable

### 4. Resources System
- Binary XML parsing (AndroidManifest, layouts)
- resources.arsc qualifier resolution (density, locale, night mode)
- R.java constant mapping

## Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                          TestPilot SDK                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐  │
│  │ APK Loader  │  │  Assertions │  │  Lifecycle Control      │  │
│  │             │  │             │  │  (create/start/resume)  │  │
│  └─────────────┘  └─────────────┘  └─────────────────────────┘  │
├───────────────────────────────────────────────────────────────────┤
│                         App Simulator                            │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    Converted APK Code                       │ │
│  │         (DEX → JVM bytecode, loaded via ApkClassLoader)     │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                     Layoutlib (Android Framework)           │ │
│  │   android.app.Activity | android.widget.* | android.view.* │ │
│  │         Real framework classes, not shims                   │ │
│  └─────────────────────────────────────────────────────────────┘ │
├───────────────────────────────────────────────────────────────────┤
│                       Rendering (Layoutlib)                      │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                  Android Official Layoutlib                 │ │
│  │              Pixel-perfect rendering on JVM                 │ │
│  └─────────────────────────────────────────────────────────────┘ │
├───────────────────────────────────────────────────────────────────┤
│                            JVM (JDK 21+)                         │
└───────────────────────────────────────────────────────────────────┘
```

### Layoutlib-First Design

TestPilot uses **layoutlib** as both the Android framework provider and the rendering engine:

| Concern | Technology | Purpose |
|---------|------------|---------|
| **Framework Classes** | Layoutlib | Provides real `android.app.Activity`, `android.widget.*`, etc. |
| **Activity Lifecycle** | Reflection | Drives `performCreate()`, `performStart()`, etc. on real Activity instances |
| **Rendering** | Layoutlib | Screenshot capture, visual regression testing |
| **Layout Inflation** | BinaryLayoutInflater | Creates real `android.widget.*` instances from binary XML |

This is the same approach used by [Paparazzi](https://github.com/cashapp/paparazzi) (layoutlib for rendering) combined with [Robolectric](https://github.com/robolectric/robolectric)'s pattern of reflection-based lifecycle driving.

## Key Components

| Component | Approach |
|-----------|----------|
| APK Processing | Unzip + dexlib2 + ASM bytecode generation |
| Activity Lifecycle | Reflection on `android.app.Activity.performXxx()` methods |
| Layout Inflation | Binary XML parsing + real `android.widget.*` construction via layoutlib |
| Resources | resources.arsc parsing + qualifier resolution (locale, density, night mode, etc.) |
| Rendering | Android Layoutlib (official Android rendering library) |

## Scope & Limitations

### Supported
- Pure Kotlin/Java APKs
- Standard UI components (all `android.widget.*` classes via layoutlib)
- Activity lifecycle testing
- Layout verification
- Screenshot / visual regression testing

### Not Supported (Initially)
- Native code (JNI/.so libraries)
- Hardware features (Camera, Bluetooth, sensors)
- System services requiring real Android (ContentProvider with system data)
- Compose UI (future consideration)

## Testing Strategy

### 1. APK Loading & Activity Lifecycle
```kotlin
val app = TestPilot.load("test-fixtures/simple-app.apk")

// Launch a specific activity by class name
val session = app.launch("com.example.MainActivity")

// getActivity() returns android.app.Activity? (nullable if instantiation fails)
val activity: android.app.Activity? = session.getActivity()

// Drive lifecycle — each method returns the session for chaining
session.pause().resume().stop().destroy()
```

### 2. Layout Rendering & Visual Regression
```kotlin
// takeScreenshot() renders arbitrary layout XML via layoutlib — it does NOT
// capture the launched activity's content view. It is independent of the APK.
val session = app.launch()

val screenshot: BufferedImage = session.takeScreenshot("""
    <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical">
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="Hello World" />
    </LinearLayout>
""")

// Golden image comparison (extension from renderer module)
val snapshots = SnapshotManager(File("src/test/resources/golden"))
screenshot.assertMatchesSnapshot(snapshots, "layout_activity", tolerance = 0.01)
```

### 3. Resource Resolution
```kotlin
// Parse resources.arsc, then resolve with a specific device configuration
val resourceTable: ResourcesParser.ResourceTable = ResourcesParser.parse(arscFile)
val resolver = ResourceTableResolver(resourceTable, DeviceConfiguration(locale = "es"))

// resolveString() returns String? (null if resource ID not found)
val appName: String? = resolver.resolveString(0x7f010000)
```

## Roadmap

### Phase 1: POC ✅ COMPLETE
- [x] Basic APK unpacking (extract DEX, resources, manifest)
- [x] DEX → JVM conversion (using dexlib2 + ASM)
- [x] AndroidManifest.xml binary parsing
- [x] Resources.arsc parsing (resource ID mapping)

### Phase 2: MVP ✅ COMPLETE
- [x] DEX instruction to JVM bytecode translation
- [x] LayoutInflater with binary XML
- [x] Common widgets via layoutlib
- [x] TestPilot SDK basic API
- [x] Resource configuration resolution (locale, density, night mode, etc.)

### Phase 3: Layoutlib Integration ✅ COMPLETE
- [x] Layoutlib as Android framework provider (no hand-written shims)
- [x] Reflection-based Activity lifecycle (`LayoutlibActivityController`)
- [x] Real `android.widget.*` instances in `BinaryLayoutInflater`
- [x] Renderer module for pixel-perfect screenshots
- [x] Visual comparison utilities (ImageComparator, SnapshotManager, assertions)

### Phase 4: Production Ready
- [ ] Touch event dispatch via layoutlib's view system
- [ ] `android.view.Window` API support
- [ ] Performance optimization (caching, incremental processing)
- [ ] CI/CD integration guide
- [ ] Documentation & examples
- [ ] Edge case handling

## Prior Art & Inspiration

- **Robolectric**: Shadow-based Android testing (requires source, TestPilot takes APK)
- **Paparazzi**: Screenshot testing using Layoutlib (requires source, TestPilot takes APK)
- **Android Layoutlib**: Official Android rendering library used by Android Studio
- **dexlib2**: DEX parsing library
- **ASM**: JVM bytecode generation library

## Alternatives Considered

| Approach | Pros | Cons | Decision |
|----------|------|------|----------|
| DEX → JVM conversion + layoutlib | Mature tools, real framework classes | Large layoutlib dependency | ✅ Chosen |
| DEX interpreter | 100% fidelity | Huge effort, slow execution | ❌ Too complex |
| Source-based (like Robolectric) | Simple | Can't test arbitrary APK | ❌ Different goal |
| Optimized emulator | Real Android | Still slow, needs KVM | ❌ No differentiation |

---

*TestPilot - Test any Android APK, anywhere JVM runs*
