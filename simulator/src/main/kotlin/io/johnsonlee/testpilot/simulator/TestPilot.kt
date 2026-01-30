package io.johnsonlee.testpilot.simulator

import java.io.File

/**
 * TestPilot SDK entry point.
 *
 * Usage:
 * ```kotlin
 * TestPilot.load("app.apk").use { app ->
 *     app.launch()  // launches the default launcher activity
 *     app.launch("com.example.MainActivity")  // launches a specific activity
 * }
 * ```
 */
class TestPilot private constructor(
    private val apkFile: File,
    private val outputDir: File
) : AutoCloseable {
    private val classLoader: ApkClassLoader
    private var controller: LayoutlibActivityController? = null
    private var applicationController: LayoutlibApplicationController? = null

    // Parsed APK data
    private var manifest: BinaryXmlParser.XmlDocument? = null
    private var resourceTable: ResourcesParser.ResourceTable? = null

    /** Package name from AndroidManifest.xml. */
    var packageName: String? = null
        private set

    /** Application class name from AndroidManifest.xml (android:name on <application>). */
    var applicationClassName: String? = null
        private set

    /** All activities declared in the manifest. */
    var activities: List<ActivityInfo> = emptyList()
        private set

    /** The Application instance, or null if not yet initialized (call [launch] first). */
    val application: android.app.Application?
        get() = applicationController?.get()

    data class ActivityInfo(
        val name: String,
        val isLauncher: Boolean,
        val isMain: Boolean
    )

    init {

        println("[TestPilot] Loading APK: ${apkFile.name}")

        // Step 1: Extract APK
        println("[TestPilot] Step 1: Extracting APK...")
        val apkLoader = ApkLoader.load(apkFile)
        val contents = apkLoader.extract(outputDir)
        println("[TestPilot]   Found ${contents.dexFiles.size} DEX file(s)")

        // Step 2: Parse AndroidManifest.xml
        println("[TestPilot] Step 2: Parsing AndroidManifest...")
        contents.manifest?.let { manifestFile ->
            manifest = BinaryXmlParser.parse(manifestFile)
            parseManifest()
            println("[TestPilot]   Package: $packageName")
            println("[TestPilot]   Activities: ${activities.size}")
        }

        // Step 3: Parse resources.arsc
        println("[TestPilot] Step 3: Parsing resources...")
        contents.resources?.let { resourcesFile ->
            resourceTable = ResourcesParser.parse(resourcesFile)
            println("[TestPilot]   Resources: ${resourceTable?.buildResourceMap()?.size ?: 0}")
        }

        // Step 4: Convert DEX to JVM bytecode
        println("[TestPilot] Step 4: Converting DEX to JVM bytecode...")
        val allClasses = mutableMapOf<String, ByteArray>()
        for (dexFile in contents.dexFiles) {
            val result = DexConverter.convert(dexFile)
            allClasses.putAll(result.classes)
            if (result.errors.isNotEmpty()) {
                println("[TestPilot]   Warnings: ${result.errors.size} conversion errors")
            }
        }
        println("[TestPilot]   Converted ${allClasses.size} classes")

        // Step 5: Create custom ClassLoader
        // No bytecode rewriting needed — layoutlib provides all android.* classes,
        // so APK code references resolve to real layoutlib classes via parent classloader.
        println("[TestPilot] Step 5: Creating ClassLoader...")
        classLoader = ApkClassLoader(allClasses, javaClass.classLoader)
        println("[TestPilot] APK loaded successfully!")
    }

    private fun parseManifest() {
        val root = manifest?.rootElement ?: return

        // Get package name
        packageName = root.attributes.find { it.name == "package" }?.value?.toString()

        // Find activities
        val application = root.children.find { it.name == "application" }
        val activityMap = mutableMapOf<String, ActivityInfo>()

        // Helper to check intent-filter for MAIN and LAUNCHER
        fun checkIntentFilters(element: BinaryXmlParser.XmlElement): Pair<Boolean, Boolean> {
            var isLauncher = false
            var isMain = false

            element.children.filter { it.name == "intent-filter" }.forEach { intentFilter ->
                intentFilter.children.forEach { child ->
                    when (child.name) {
                        "action" -> {
                            val action = child.attributes.find { it.name == "name" }?.value?.toString()
                            if (action == "android.intent.action.MAIN") {
                                isMain = true
                            }
                        }
                        "category" -> {
                            val category = child.attributes.find { it.name == "name" }?.value?.toString()
                            if (category == "android.intent.category.LAUNCHER") {
                                isLauncher = true
                            }
                        }
                    }
                }
            }

            return isLauncher to isMain
        }

        // Helper to resolve full class name
        fun resolveClassName(name: String): String {
            return if (name.startsWith(".")) {
                "$packageName$name"
            } else if (!name.contains(".")) {
                "$packageName.$name"
            } else {
                name
            }
        }

        // Extract Application class name (android:name on <application>)
        val appName = application?.attributes?.find { it.name == "name" }?.value?.toString()
        applicationClassName = appName?.let { resolveClassName(it) }

        // Parse activities
        application?.children?.filter { it.name == "activity" }?.forEach { activityElement ->
            val name = activityElement.attributes.find { it.name == "name" }?.value?.toString() ?: return@forEach
            val fullName = resolveClassName(name)
            val (isLauncher, isMain) = checkIntentFilters(activityElement)
            activityMap[fullName] = ActivityInfo(fullName, isLauncher, isMain)
        }

        // Parse activity-alias (can define launcher for target activity)
        application?.children?.filter { it.name == "activity-alias" }?.forEach { aliasElement ->
            val targetActivity = aliasElement.attributes.find { it.name == "targetActivity" }?.value?.toString() ?: return@forEach
            val fullTargetName = resolveClassName(targetActivity)
            val (isLauncher, isMain) = checkIntentFilters(aliasElement)

            // If this alias has launcher intent, update the target activity
            if (isLauncher || isMain) {
                val existing = activityMap[fullTargetName]
                if (existing != null) {
                    activityMap[fullTargetName] = existing.copy(
                        isLauncher = existing.isLauncher || isLauncher,
                        isMain = existing.isMain || isMain
                    )
                }
            }
        }

        activities = activityMap.values.toList()
    }

    private fun findLauncherActivity(): ActivityInfo? {
        return activities.find { it.isLauncher && it.isMain }
            ?: activities.find { it.isMain }
            ?: activities.firstOrNull()
    }

    /**
     * Launches an activity. Lifecycle is managed internally
     * (previous activity is destroyed, new one is created, started, and resumed).
     *
     * Follows the AOSP launch sequence:
     * 1. Ensure Application (handleBindApplication): attach + onCreate
     * 2. Instantiate Activity (Instrumentation.newActivity)
     * 3. Activity.attach(context, activityThread, instrumentation, ...)
     * 4. Activity.performCreate → performStart → performResume
     *
     * @param activityClassName Fully qualified class name, or null for the default launcher activity.
     */
    fun launch(activityClassName: String? = null) {
        val targetActivity = activityClassName ?: findLauncherActivity()?.name
            ?: throw IllegalStateException("No launcher activity found in manifest")

        // Destroy previous activity if any
        controller?.destroy()
        controller = null

        println("[TestPilot] Launching: $targetActivity")

        // AOSP step 1: Ensure Application (handleBindApplication)
        ensureApplication()

        // AOSP step 2: Instantiate Activity (Instrumentation.newActivity)
        val activity = instantiateActivity(targetActivity) ?: return

        // AOSP step 3: activity.attach(...)
        try {
            attachActivity(activity, targetActivity)
        } catch (e: Exception) {
            println("[TestPilot] Warning: Activity.attach() failed: ${e.message}")
            // Continue — some fields may still be partially set
        }

        // AOSP step 4: Instrumentation.callActivityOnCreate → performCreate → onCreate
        val newController = LayoutlibActivityController(activity)
        newController.create().start().resume()

        controller = newController
    }

    /**
     * Follows AOSP `handleBindApplication` sequence:
     * 1. `LoadedApk.makeApplicationInner()` — instantiate + `app.attach(context)`
     * 2. `mInstrumentation.callApplicationOnCreate(app)` — `app.onCreate()`
     */
    private fun ensureApplication() {
        if (applicationController != null) return

        val controller = LayoutlibApplicationController.create(applicationClassName, classLoader)
        val context = StubContext(packageName ?: "", classLoader)
        controller.attach(context)
        controller.onCreate()

        applicationController = controller
        println("[TestPilot] Application initialized: ${applicationClassName ?: "android.app.Application"}")
    }

    /**
     * Follows AOSP `performLaunchActivity` step 4: `activity.attach(...)`.
     *
     * Provides the Activity with a base context, ActivityThread, Instrumentation,
     * Application reference, and other framework objects needed before `onCreate()`.
     */
    private fun attachActivity(activity: android.app.Activity, activityClassName: String) {
        val context = StubContext(packageName ?: "", classLoader)
        val activityThread = android.app.ActivityThread::class.java
            .getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val instrumentation = android.app.Instrumentation()
        val activityInfo = android.content.pm.ActivityInfo().apply {
            name = activityClassName
            packageName = this@TestPilot.packageName
        }
        val intent = android.content.Intent().apply {
            component = android.content.ComponentName(
                this@TestPilot.packageName ?: "", activityClassName
            )
        }
        val config = android.content.res.Configuration()
        val app = applicationController!!.get()

        // AOSP performLaunchActivity step 4: activity.attach(...)
        // Find the 19-param attach() by scanning declared methods, because several
        // parameter types (NonConfigurationInstances, IVoiceInteractor, ActivityConfigCallback)
        // are package-private and can't be referenced directly from Kotlin.
        val attachMethod = android.app.Activity::class.java.declaredMethods
            .filter { it.name == "attach" }
            .maxByOrNull { it.parameterCount }
            ?: throw NoSuchMethodException("Activity.attach() not found")
        attachMethod.isAccessible = true

        // Build args matching the parameter types positionally:
        // context, activityThread, instrumentation, token, ident,
        // application, intent, activityInfo, title, parent,
        // embeddedID, lastNCI, config, referrer, voiceInteractor,
        // window, configCallback, assistToken, shareableActivityToken
        val args = arrayOfNulls<Any>(attachMethod.parameterCount)
        val paramTypes = attachMethod.parameterTypes
        for (i in paramTypes.indices) {
            args[i] = when (paramTypes[i]) {
                android.content.Context::class.java -> context
                android.app.ActivityThread::class.java -> activityThread
                android.app.Instrumentation::class.java -> instrumentation
                android.app.Application::class.java -> app
                android.content.Intent::class.java -> intent
                android.content.pm.ActivityInfo::class.java -> activityInfo
                android.content.res.Configuration::class.java -> config
                Int::class.javaPrimitiveType -> 0
                else -> {
                    // For IBinder params (token, assistToken, shareableActivityToken),
                    // provide Binder instances; everything else gets null.
                    if (android.os.IBinder::class.java.isAssignableFrom(paramTypes[i])) {
                        android.os.Binder()
                    } else {
                        null
                    }
                }
            }
        }
        attachMethod.invoke(activity, *args)
    }

    /**
     * Instantiates an Activity class from the APK classloader.
     * Follows AOSP `Instrumentation.newActivity(cl, className, intent)`.
     */
    private fun instantiateActivity(className: String): android.app.Activity? {
        val activityClass = try {
            classLoader.loadClass(className)
        } catch (e: ClassNotFoundException) {
            println("[TestPilot] Warning: Activity class not found: $className")
            return null
        } catch (e: VerifyError) {
            println("[TestPilot] Warning: Bytecode verification failed: ${e.message}")
            return null
        } catch (e: Throwable) {
            println("[TestPilot] Warning: Failed to load class: ${e.message}")
            return null
        }

        if (!android.app.Activity::class.java.isAssignableFrom(activityClass)) {
            println("[TestPilot] Warning: $className is not an Activity")
            return null
        }

        return try {
            val constructor = activityClass.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance() as android.app.Activity
        } catch (e: Throwable) {
            println("[TestPilot] Warning: Failed to instantiate: ${e.message?.take(100)}")
            null
        }
    }

    /**
     * Releases all resources (destroys current activity, cleans up classloader).
     */
    override fun close() {
        controller?.destroy()
        controller = null
        applicationController?.onTerminate()
        applicationController = null
        outputDir.deleteRecursively()
    }

    companion object {
        /**
         * Loads an APK file.
         */
        fun load(apkPath: String): TestPilot = load(File(apkPath))

        /**
         * Loads an APK file.
         */
        fun load(apkFile: File): TestPilot {
            require(apkFile.exists()) { "APK file does not exist: ${apkFile.absolutePath}" }

            val outputDir = File(System.getProperty("java.io.tmpdir"), "testpilot-${System.currentTimeMillis()}")
            outputDir.mkdirs()

            return TestPilot(apkFile, outputDir)
        }
    }
}

/**
 * Custom ClassLoader that loads classes from transformed APK bytecode.
 */
internal class ApkClassLoader(
    private val classes: Map<String, ByteArray>,
    parent: ClassLoader?
) : ClassLoader(parent) {

    override fun findClass(name: String): Class<*> {
        // Convert class name to internal name
        val internalName = name.replace('.', '/')

        // Check if we have this class
        val bytecode = classes[internalName]
            ?: throw ClassNotFoundException(name)

        return defineClass(name, bytecode, 0, bytecode.size)
    }

    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        // Check if already loaded
        var c = findLoadedClass(name)

        if (c == null) {
            // Try to find in our classes first (for app classes)
            val internalName = name.replace('.', '/')
            if (classes.containsKey(internalName)) {
                c = findClass(name)
            } else {
                // Delegate to parent
                c = parent?.loadClass(name) ?: throw ClassNotFoundException(name)
            }
        }

        if (resolve) {
            resolveClass(c)
        }

        return c
    }

    /**
     * Lists all loaded class names.
     */
    fun listClasses(): List<String> = classes.keys.map { it.replace('/', '.') }
}
