package io.johnsonlee.testpilot.simulator

import io.johnsonlee.testpilot.renderer.DeviceConfig
import io.johnsonlee.testpilot.renderer.LayoutRenderer
import io.johnsonlee.testpilot.renderer.RenderEnvironment
import io.johnsonlee.testpilot.renderer.RenderResult
import java.awt.image.BufferedImage
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
    private var currentController: LayoutlibActivityController? = null

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
     * @param configuration The device configuration for resource resolution.
     */
    fun launch(
        activityClassName: String? = null,
        configuration: DeviceConfiguration = DeviceConfiguration.DEFAULT
    ): ActivitySession {
        val targetActivity = activityClassName ?: getLauncherActivity()?.name
            ?: throw IllegalStateException("No launcher activity found in manifest")

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

        val resources = AppResources(configuration)
        resourceTable?.let {
            resources.resolver = ResourceTableResolver(it, configuration)
        }

        // Try to instantiate the actual activity class
        if (activityClass == null || !android.app.Activity::class.java.isAssignableFrom(activityClass)) {
            println("[TestPilot] Warning: Activity class not compatible with android.app.Activity: $targetActivity")
            return ActivitySession(this, null, resourceTable, resources)
        }

        val activity: android.app.Activity = try {
            val constructor = activityClass.getDeclaredConstructor()
            constructor.isAccessible = true
            constructor.newInstance() as android.app.Activity
        } catch (e: Throwable) {
            println("[TestPilot] Warning: Failed to instantiate activity: ${e.javaClass.simpleName}: ${e.message?.take(100)}")
            return ActivitySession(this, null, resourceTable, resources)
        }

        val controller = LayoutlibActivityController(activity)

        // Drive lifecycle
        controller.create().start().resume()

        currentController = controller
        return ActivitySession(this, controller, resourceTable, resources)
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
        private val controller: LayoutlibActivityController?,
        private val resourceTable: ResourcesParser.ResourceTable?,
        private val appResources: AppResources
    ) {
        fun getActivity(): android.app.Activity? = controller?.get()

        fun getResourceTable(): ResourcesParser.ResourceTable? = resourceTable

        fun getAppResources(): AppResources = appResources

        /**
         * Gets a resource by ID.
         */
        fun getResource(resourceId: Int): ResourcesParser.ResourceEntry? {
            return resourceTable?.getResource(resourceId)
        }

        fun pause(): ActivitySession {
            controller?.pause()
            return this
        }

        fun resume(): ActivitySession {
            controller?.resume()
            return this
        }

        fun stop(): ActivitySession {
            controller?.stop()
            return this
        }

        fun destroy(): ActivitySession {
            controller?.destroy()
            return this
        }

        // ==================== Screenshot Methods ====================

        /**
         * Takes a screenshot by rendering the provided layout XML using layoutlib.
         *
         * @param layoutXml The layout XML string to render.
         * @param deviceConfig The device configuration (screen size, density, etc.).
         * @param theme The theme to use for rendering.
         * @return The rendered image.
         */
        fun takeScreenshot(
            layoutXml: String,
            deviceConfig: DeviceConfig = DeviceConfig.DEFAULT,
            theme: String = "Theme.Material.Light.NoActionBar"
        ): BufferedImage {
            val environment = RenderEnvironment()
            return LayoutRenderer(environment, deviceConfig).use { renderer ->
                renderer.render(layoutXml, theme).image
            }
        }

        /**
         * Takes a screenshot by rendering the provided layout XML using layoutlib.
         *
         * @param layoutXml The layout XML string to render.
         * @param deviceConfig The device configuration (screen size, density, etc.).
         * @param theme The theme to use for rendering.
         * @return The full render result including view hierarchy information.
         */
        fun renderLayout(
            layoutXml: String,
            deviceConfig: DeviceConfig = DeviceConfig.DEFAULT,
            theme: String = "Theme.Material.Light.NoActionBar"
        ): RenderResult {
            val environment = RenderEnvironment()
            return LayoutRenderer(environment, deviceConfig).use { renderer ->
                renderer.render(layoutXml, theme)
            }
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
