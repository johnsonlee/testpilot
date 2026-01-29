# TestPilot

## Overview

TestPilot enables **testing any Android APK** on standard JVM without emulators or physical devices. By converting DEX bytecode to JVM bytecode and providing an Android API shim layer, developers can run fast, reliable UI tests directly on their development machines or CI servers.

## Core Idea

**"Test any APK, anywhere JVM runs"**

Input: APK file → Output: Test results on pure JVM

```kotlin
val session = TestPilot.load("app.apk").launch()

// Tap by coordinates
session.tap(100f, 200f)

// Tap by view ID
session.tap(R.id.login_button)

// Lifecycle control
session.pause().resume().stop().destroy()
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
┌─────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────┐
│   APK   │ ──▶│   Unpack    │ ──▶│ DEX → JVM   │ ──▶│  Bytecode   │ ──▶│   Run   │
│         │    │             │    │  (dex2jar)  │    │  Rewrite    │    │ on JVM  │
└─────────┘    └─────────────┘    └─────────────┘    └─────────────┘    └─────────┘
                    │                                      │
                    ▼                                      ▼
              resources/                          android.view.View
              AndroidManifest.xml                        ↓
                                               io.johnsonlee.testpilot.simulator.View
```

### Pipeline Steps

1. **APK Unpacking**: Extract classes.dex, resources, AndroidManifest.xml
2. **DEX → JVM Conversion**: Convert Dalvik bytecode to JVM bytecode using dex2jar/enjarify
3. **Bytecode Rewriting**: Replace `android.*` references with TestPilot shim classes
4. **Execution**: Load transformed classes and run on standard JVM

## Technical Challenges

### 1. DEX → JVM Bytecode Conversion
- Android uses DEX/ART bytecode format, JVM uses class files
- Solution: Use proven tools (dex2jar, enjarify) for conversion
- Risk: Some edge cases may not convert perfectly

### 2. Android Framework Dependencies
- `android.*` packages rely heavily on native implementations
- Solution: Implement shim layer that mimics Android API behavior
- Priority: Focus on UI-related APIs first (View, Activity, Resources)

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
┌─────────────────────────────────────────────────────────────┐
│                      TestPilot SDK                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ APK Loader  │  │  Assertions │  │  UI Actions         │  │
│  │             │  │             │  │  (tap/swipe/input)  │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
├─────────────────────────────────────────────────────────────┤
│                     App Simulator                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Transformed APK Code                    │    │
│  │         (android.* → io.johnsonlee.testpilot.simulator.*)      │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Android API Shim Layer                  │    │
│  │     Activity | View | Resources | Intent | ...      │    │
│  └─────────────────────────────────────────────────────┘    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              Rendering Backend                       │    │
│  │              Skiko / AWT Graphics2D                  │    │
│  └─────────────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────────────┤
│                        JVM (JDK 17+)                        │
└─────────────────────────────────────────────────────────────┘
```

## Key Components

| Component | Approach |
|-----------|----------|
| APK Processing | Unzip + dex2jar + ASM bytecode rewriting |
| Activity Lifecycle | State machine + callback chain |
| View System | measure/layout/draw pipeline implementation |
| LayoutInflater | Binary XML parsing + reflection-based construction |
| Resources | resources.arsc parsing + qualifier resolution |
| Event Dispatch | TouchEvent simulation via View hierarchy |
| Rendering Backend | Skiko or AWT Graphics2D |

## Scope & Limitations

### Supported
- Pure Kotlin/Java APKs
- Standard UI components (View, ViewGroup, common widgets)
- Activity lifecycle testing
- UI interaction testing (tap, swipe, text input)
- Layout verification

### Not Supported (Initially)
- Native code (JNI/.so libraries)
- Hardware features (Camera, Bluetooth, sensors)
- System services requiring real Android (ContentProvider with system data)
- Compose UI (future consideration)

## Testing Strategy

### 1. Unit Tests - Shim API Contract Verification
```kotlin
class ActivityLifecycleTest {
    @Test
    fun `onCreate should be called before onStart`() {
        val calls = mutableListOf<String>()
        val activity = TestActivity { calls += it }

        activityController.create().start()

        assertThat(calls).containsExactly("onCreate", "onStart")
    }
}
```

### 2. Integration Tests - Real APK Execution
```kotlin
class ApkLoadingTest {
    @Test
    fun `should load and launch simple APK`() {
        val app = TestPilot.load("test-fixtures/simple-app.apk")

        app.launch("com.example.MainActivity")

        assertThat(app.currentActivity).isNotNull()
        assertThat(app.findView<TextView>(R.id.title).text).isEqualTo("Hello")
    }
}
```

### 3. Visual Regression - Screenshot Comparison
```kotlin
class LayoutRenderingTest {
    @Test
    fun `LinearLayout vertical should match Android rendering`() {
        val app = TestPilot.load("test-fixtures/layout-test.apk")
        app.launch("com.example.LayoutActivity")

        val screenshot = app.takeScreenshot()
        val golden = loadGolden("layout_activity.png")

        assertThat(screenshot).isVisuallyEqualTo(golden, tolerance = 0.01)
    }
}
```

### 4. Behavioral Tests - Golden Data Comparison
```kotlin
class ViewMeasureSpecTest {
    @Test
    fun `MeasureSpec packing should match Android`() {
        val androidResults = loadGoldenData("measure_spec_cases.json")

        androidResults.forEach { case ->
            val jvmResult = MeasureSpec.makeMeasureSpec(case.size, case.mode)
            assertThat(jvmResult).isEqualTo(case.expected)
        }
    }
}
```

## Roadmap

### Phase 1: POC (1-2 days) ✅ COMPLETE
- [x] Basic Activity lifecycle state machine
- [x] Basic View/ViewGroup implementation
- [x] Simple Canvas rendering
- [x] APK unpacking (extract DEX, resources, manifest)
- [x] DEX → JVM conversion (using dexlib2 + ASM)
- [x] Basic bytecode rewriting framework (android.* → shim mapping)

**Result**: Successfully loaded a real APK (7.5MB, 3713 classes) with 0 conversion errors

### Phase 2: MVP (1-2 weeks) ✅ COMPLETE
- [x] DEX instruction to JVM bytecode translation
- [x] AndroidManifest.xml binary parsing
- [x] Resources.arsc parsing (resource ID mapping)
- [x] LayoutInflater with binary XML
- [x] Common widgets (TextView, Button, ImageView, EditText, ScrollView, ProgressBar, etc.)
- [x] Touch event dispatch
- [x] TestPilot SDK basic API

**Result**: Full APK loading pipeline with touch event dispatch. Supports tap interactions and event listeners.

### Phase 3: Real App Support (3-4 weeks)
- [ ] Complete Resources system with qualifiers
- [ ] Fragment support
- [ ] RecyclerView
- [ ] ViewPager
- [ ] More widgets coverage
- [ ] Screenshot capture & visual comparison

**Goal**: Test medium-complexity real-world APKs

### Phase 4: Production Ready (4-6 weeks)
- [ ] Performance optimization (caching, incremental processing)
- [ ] Comprehensive widget support
- [ ] CI/CD integration guide
- [ ] Documentation & examples
- [ ] Edge case handling

## Effort Estimation

| Module | Estimated Effort |
|--------|------------------|
| APK Processing | 1-2 days |
| DEX → JVM Conversion | 1 day |
| Bytecode Rewriting | 2-3 days |
| Android API Shim | 15-25 days |
| Rendering Backend | 3-5 days |
| TestPilot SDK | 3-5 days |
| **Total** | **25-40 days** |

> Note: Shim layer is the long tail - core 20% APIs cover 80% of use cases.

## Use Cases

1. **Fast UI Testing**: Test any APK on pure JVM, no emulator needed
2. **CI/CD Optimization**: Parallel test execution without KVM overhead
3. **Quick Verification**: Instant APK testing without device deployment
4. **Reliable Tests**: Eliminate flakiness from system animations and popups
5. **Better Debugging**: Same-process debugging with standard IDE tools

## Prior Art & Inspiration

- **Robolectric**: Shadow-based Android testing (requires source, TestPilot takes APK)
- **dex2jar**: DEX to JAR conversion (core dependency)
- **Maestro**: Test orchestration API inspiration
- **ASM**: Bytecode manipulation library

## Alternatives Considered

| Approach | Pros | Cons | Decision |
|----------|------|------|----------|
| DEX → JVM conversion | Mature tools, cacheable | Some conversion edge cases | ✅ Chosen |
| DEX interpreter | 100% fidelity | Huge effort, slow execution | ❌ Too complex |
| Source-based (like Robolectric) | Simple | Can't test arbitrary APK | ❌ Different goal |
| Optimized emulator | Real Android | Still slow, needs KVM | ❌ No differentiation |

---

*TestPilot - Test any Android APK, anywhere JVM runs*
