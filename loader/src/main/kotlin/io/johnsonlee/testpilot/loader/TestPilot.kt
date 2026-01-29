package io.johnsonlee.testpilot.loader

import io.johnsonlee.testpilot.simulator.activity.Activity
import io.johnsonlee.testpilot.simulator.activity.ActivityController
import io.johnsonlee.testpilot.simulator.resources.Resources
import io.johnsonlee.testpilot.simulator.window.Window
import java.io.File

/**
 * TestPilot SDK entry point.
 *
 * Usage:
 * ```kotlin
 * val app = TestPilot.load("app.apk")
 * app.launch()  // launches the default launcher activity
 * app.launch("com.example.MainActivity")  // launches a specific activity
 * ```
 */
class TestPilot private constructor(
    private val apkFile: File,
    private val outputDir: File
) {
    private val classLoader: ApkClassLoader
    private var currentController: ActivityController<out Activity>? = null

    // Parsed APK data
    private var manifest: BinaryXmlParser.XmlDocument? = null
    private var resourceTable: ResourcesParser.ResourceTable? = null
    private var packageName: String? = null
    private var activities: List<ActivityInfo> = emptyList()

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

        // Step 5: Rewrite bytecode (android.* -> shim)
        println("[TestPilot] Step 5: Rewriting bytecode...")
        val rewrittenClasses = BytecodeRewriter.rewriteAll(allClasses)
        println("[TestPilot]   Rewritten ${rewrittenClasses.size} classes")

        // Step 6: Create custom ClassLoader
        println("[TestPilot] Step 6: Creating ClassLoader...")
        classLoader = ApkClassLoader(rewrittenClasses, javaClass.classLoader)
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

    /**
     * Gets the package name from the manifest.
     */
    fun getPackageName(): String? = packageName

    /**
     * Gets all declared activities.
     */
    fun getActivities(): List<ActivityInfo> = activities

    /**
     * Gets the launcher activity (main entry point).
     */
    fun getLauncherActivity(): ActivityInfo? {
        return activities.find { it.isLauncher && it.isMain }
            ?: activities.find { it.isMain }
            ?: activities.firstOrNull()
    }

    /**
     * Launches an Activity.
     *
     * @param activityClassName The fully qualified class name of the activity to launch.
     *                          If null, launches the default launcher activity.
     */
    fun launch(activityClassName: String? = null): ActivitySession {
        val targetActivity = activityClassName ?: getLauncherActivity()?.name
            ?: throw IllegalStateException("No launcher activity found in manifest")

        println("[TestPilot] Launching: $targetActivity")

        // Try to load the activity class
        val activityClass = try {
            classLoader.loadClass(targetActivity)
        } catch (e: ClassNotFoundException) {
            println("[TestPilot] Warning: Activity class not found, using stub")
            null
        } catch (e: VerifyError) {
            println("[TestPilot] Warning: Bytecode verification failed: ${e.message}")
            null
        } catch (e: Throwable) {
            println("[TestPilot] Warning: Failed to load class: ${e.message}")
            null
        }

        val window = Window(width = 480, height = 800)
        val resources = Resources()

        // Try to instantiate the actual activity class
        val activity: Activity = if (activityClass != null && Activity::class.java.isAssignableFrom(activityClass)) {
            try {
                @Suppress("UNCHECKED_CAST")
                val constructor = activityClass.getDeclaredConstructor()
                constructor.isAccessible = true
                constructor.newInstance() as Activity
            } catch (e: Throwable) {
                // Catch all errors including VerifyError during class initialization
                println("[TestPilot] Warning: Failed to instantiate activity: ${e.javaClass.simpleName}: ${e.message?.take(100)}")
                createStubActivity(targetActivity)
            }
        } else {
            println("[TestPilot] Note: Using stub activity (class not compatible)")
            createStubActivity(targetActivity)
        }

        val controller = ActivityController.of(activity, window, resources)

        // Drive lifecycle
        controller.create().start().resume()

        currentController = controller
        return ActivitySession(this, controller, window, resourceTable)
    }

    private fun createStubActivity(name: String): Activity {
        return object : Activity() {
            override fun onCreate(savedInstanceState: io.johnsonlee.testpilot.simulator.os.Bundle?) {
                super.onCreate(savedInstanceState)
                println("[TestPilot] Stub Activity.onCreate() - $name")
            }
        }
    }

    /**
     * Gets the resource table.
     */
    fun getResources(): ResourcesParser.ResourceTable? = resourceTable

    /**
     * Gets the custom ClassLoader for this APK.
     */
    fun getClassLoader(): ClassLoader = classLoader

    /**
     * Represents a running Activity session.
     */
    class ActivitySession(
        private val testPilot: TestPilot,
        private val controller: ActivityController<out Activity>,
        private val window: Window,
        private val resources: ResourcesParser.ResourceTable?
    ) {
        fun getActivity(): Activity = controller.get()

        fun getWindow(): Window = window

        fun getResources(): ResourcesParser.ResourceTable? = resources

        /**
         * Gets a resource by ID.
         */
        fun getResource(resourceId: Int): ResourcesParser.ResourceEntry? {
            return resources?.getResource(resourceId)
        }

        fun pause(): ActivitySession {
            controller.pause()
            return this
        }

        fun resume(): ActivitySession {
            controller.resume()
            return this
        }

        fun stop(): ActivitySession {
            controller.stop()
            return this
        }

        fun destroy(): ActivitySession {
            controller.destroy()
            return this
        }
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
class ApkClassLoader(
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
