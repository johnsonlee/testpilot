package io.johnsonlee.testpilot.renderer

import com.android.layoutlib.bridge.Bridge
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

/**
 * Environment configuration for layoutlib rendering.
 *
 * Layoutlib native and resource data can be provided via:
 * - System properties: testpilot.layoutlib.runtime, testpilot.layoutlib.resources
 * - Or manual data/ directory in working directory
 *
 * Note: Android SDK is optional. Layoutlib runtime contains its own build.prop and resources.
 */
class RenderEnvironment(
    val androidHome: File? = detectAndroidHome(),
    val compileSdkVersion: Int = 31,
    val layoutlibRuntimeDir: File? = System.getProperty("testpilot.layoutlib.runtime")?.let { File(it) },
    val layoutlibResourcesDir: File? = System.getProperty("testpilot.layoutlib.resources")?.let { File(it) }
) {
    private val logger = LoggerFactory.getLogger(RenderEnvironment::class.java)

    val platformDir: File? = androidHome?.resolve("platforms/android-$compileSdkVersion")
    val platformDataResDir: File? = platformDir?.resolve("data/res")

    /**
     * The directory containing framework resources (res/).
     * Uses layoutlib-resources first, then falls back to platform directory.
     */
    val frameworkResDir: File by lazy {
        // First try layoutlib-resources
        layoutlibResourcesDir?.resolve("res")?.takeIf { it.exists() }
            // Then try platform
            ?: platformDataResDir?.takeIf { it.exists() }
            // Fallback
            ?: throw FileNotFoundException(
                "Framework resources not found. Provide layoutlib-resources or install Android SDK platform."
            )
    }

    private var bridge: Bridge? = null

    /**
     * Initializes the layoutlib Bridge.
     */
    fun initBridge(): Bridge {
        if (bridge != null) {
            return bridge!!
        }

        // Layoutlib runtime structure: data/fonts, data/icu, data/keyboards, data/{platform}/lib64
        // Layoutlib resources structure: res/, overlays/, resources*.bin
        val runtimeRootDir = layoutlibRuntimeDir ?: File(System.getProperty("user.dir"))
        val runtimeDataDir = if (layoutlibRuntimeDir != null) {
            layoutlibRuntimeDir.resolve("data")
        } else {
            File(System.getProperty("user.dir")).resolve("data")
        }
        val resourcesDir = layoutlibResourcesDir ?: runtimeDataDir

        val fontLocation = runtimeDataDir.resolve("fonts")
        val nativeLibLocation = runtimeDataDir.resolve(nativeLibDir)
        val icuLocation = findIcuFile(runtimeDataDir)
        val hyphenDir = runtimeDataDir.resolve("hyphen-data")
        val keyboardLocation = runtimeDataDir.resolve("keyboards/Generic.kcm")

        // Use build.prop from layoutlib runtime first, then fall back to platform
        val buildProp = runtimeRootDir.resolve("build.prop").takeIf { it.exists() }
            ?: platformDir?.resolve("build.prop")
            ?: throw FileNotFoundException("build.prop not found in layoutlib runtime or Android SDK")

        // Use attrs.xml from layoutlib resources first, then fall back to platform
        val attrsFile = resourcesDir.resolve("res/values/attrs.xml").takeIf { it.exists() }
            ?: platformDataResDir?.resolve("values/attrs.xml")
            ?: resourcesDir.resolve("res/values/attrs.xml") // Will fail later if not found

        val systemProperties = loadProperties(buildProp) + mapOf(
            "debug.choreographer.frametime" to "false"
        )

        val enumMap = parseEnumMap(attrsFile)

        logger.info("Initializing layoutlib Bridge...")
        logger.debug("  Font location: $fontLocation")
        logger.debug("  Native lib location: $nativeLibLocation")
        logger.debug("  ICU location: $icuLocation")

        bridge = Bridge().apply {
            val success = init(
                systemProperties,
                fontLocation,
                nativeLibLocation.path,
                icuLocation.path,
                hyphenDir.path,
                arrayOf(keyboardLocation.path),
                enumMap,
                RenderLogger
            )
            if (!success) {
                throw IllegalStateException("Failed to initialize layoutlib Bridge")
            }
        }

        return bridge!!
    }

    /**
     * Disposes the layoutlib Bridge.
     */
    fun dispose() {
        bridge?.dispose()
        bridge = null
    }

    private fun findIcuFile(resourcesDir: File): File {
        // Search in multiple locations
        val searchDirs = listOf(
            resourcesDir.resolve("icu"),
            resourcesDir,
            File(System.getProperty("user.dir")).resolve("data/icu")
        )

        for (dir in searchDirs) {
            val icuFile = dir.listFiles { f -> f.name.startsWith("icudt") && f.name.endsWith(".dat") }
                ?.firstOrNull()
            if (icuFile != null) {
                return icuFile
            }
        }

        // Default fallback
        return resourcesDir.resolve("icu/icudt76l.dat")
    }

    private fun loadProperties(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        val props = java.util.Properties()
        file.inputStream().use { props.load(it) }
        return props.stringPropertyNames().associateWith { props.getProperty(it) }
    }

    private fun parseEnumMap(attrsFile: File): Map<String, Map<String, Int>> {
        if (!attrsFile.exists()) return emptyMap()

        val map = mutableMapOf<String, MutableMap<String, Int>>()
        val factory = javax.xml.parsers.SAXParserFactory.newInstance()
        val parser = factory.newSAXParser()

        var currentAttr: String? = null

        parser.parse(attrsFile, object : org.xml.sax.helpers.DefaultHandler() {
            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: org.xml.sax.Attributes?
            ) {
                when (qName) {
                    "attr" -> currentAttr = attributes?.getValue("name")
                    "enum", "flag" -> {
                        val name = attributes?.getValue("name") ?: return
                        val value = attributes.getValue("value") ?: return
                        val intValue = java.lang.Long.decode(value).toInt()
                        currentAttr?.let { attr ->
                            map.getOrPut(attr) { mutableMapOf() }[name] = intValue
                        }
                    }
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                if (qName == "attr") {
                    currentAttr = null
                }
            }
        })

        return map
    }

    companion object {
        /**
         * Detects the Android SDK home directory.
         * Returns null if not found.
         */
        fun detectAndroidHome(): File? {
            // Check environment variables
            System.getenv("ANDROID_SDK_ROOT")?.let { path ->
                val file = File(path)
                if (file.exists()) return file
            }
            System.getenv("ANDROID_HOME")?.let { path ->
                val file = File(path)
                if (file.exists()) return file
            }

            // Check default locations
            val osName = System.getProperty("os.name").lowercase()
            val home = System.getProperty("user.home")

            val defaultPath = when {
                osName.startsWith("windows") -> "$home\\AppData\\Local\\Android\\Sdk"
                osName.startsWith("mac") -> "$home/Library/Android/sdk"
                else -> "$home/Android/Sdk"
            }

            val defaultFile = File(defaultPath)
            return if (defaultFile.exists()) defaultFile else null
        }

        /**
         * Returns the native library directory name for the current platform.
         */
        val nativeLibDir: String
            get() {
                val osName = System.getProperty("os.name").lowercase()
                val osLabel = when {
                    osName.startsWith("windows") -> "win"
                    osName.startsWith("mac") -> {
                        val arch = System.getProperty("os.arch").lowercase()
                        if (arch.startsWith("x86")) "mac" else "mac-arm"
                    }
                    else -> "linux"
                }
                return "$osLabel/lib64"
            }
    }
}
