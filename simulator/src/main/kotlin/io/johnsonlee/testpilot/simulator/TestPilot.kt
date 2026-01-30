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

    // Parsed APK data
    private var manifest: BinaryXmlParser.XmlDocument? = null
    private var resourceTable: ResourcesParser.ResourceTable? = null

    /** Package name from AndroidManifest.xml. */
    var packageName: String? = null
        private set

    /** All activities declared in the manifest. */
    var activities: List<ActivityInfo> = emptyList()
        private set

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
     * @param activityClassName Fully qualified class name, or null for the default launcher activity.
     * @param configuration The device configuration for resource resolution.
     */
    fun launch(
        activityClassName: String? = null,
        configuration: DeviceConfiguration = DeviceConfiguration.DEFAULT
    ) {
        val targetActivity = activityClassName ?: findLauncherActivity()?.name
            ?: throw IllegalStateException("No launcher activity found in manifest")

        // Destroy previous activity if any
        controller?.destroy()
        controller = null

        println("[TestPilot] Launching: $targetActivity")

        // Try to load the activity class
        val activityClass = try {
            classLoader.loadClass(targetActivity)
        } catch (e: ClassNotFoundException) {
            println("[TestPilot] Warning: Activity class not found: $targetActivity")
            null
        } catch (e: VerifyError) {
            println("[TestPilot] Warning: Bytecode verification failed: ${e.message}")
            null
        } catch (e: Throwable) {
            println("[TestPilot] Warning: Failed to load class: ${e.message}")
            null
        }

        // Try to instantiate the actual activity class
        if (activityClass == null || !android.app.Activity::class.java.isAssignableFrom(activityClass)) {
            println("[TestPilot] Warning: Activity class not compatible with android.app.Activity: $targetActivity")
            return
        }

        val activity: android.app.Activity = try {
            val constructor = activityClass.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance() as android.app.Activity
        } catch (e: Throwable) {
            println("[TestPilot] Warning: Failed to instantiate activity: ${e.javaClass.simpleName}: ${e.message?.take(100)}")
            return
        }

        val newController = LayoutlibActivityController(activity)

        // Drive lifecycle
        newController.create().start().resume()

        controller = newController
    }

    /**
     * Releases all resources (destroys current activity, cleans up classloader).
     */
    override fun close() {
        controller?.destroy()
        controller = null
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
