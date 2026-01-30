package io.johnsonlee.testpilot.simulator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class TestPilotIntegrationTest {

    companion object {
        private val apkFile = listOf(
            File("test-fixtures/simple-app.apk"),        // from project root
            File("../test-fixtures/simple-app.apk")      // from module dir
        ).firstOrNull { it.exists() }
    }

    @BeforeEach
    fun assumeApk() {
        assumeTrue(apkFile != null, "Test APK not found")
    }

    @Test
    fun `should load APK and parse manifest`() {
        TestPilot.load(apkFile!!).use { app ->
            assertThat(app.packageName).isNotNull().isNotEmpty()
            assertThat(app.activities).isNotEmpty()
            println("Package: ${app.packageName}")
            println("Activities: ${app.activities.map { it.name }}")
        }
    }

    @Test
    fun `should find launcher activity`() {
        TestPilot.load(apkFile!!).use { app ->
            val launcher = app.activities.find { it.isLauncher && it.isMain }
            assertThat(launcher).isNotNull()
            println("Launcher: ${launcher!!.name}")
        }
    }

    @Test
    fun `should launch default activity`() {
        TestPilot.load(apkFile!!).use { app ->
            app.launch()
            assertThat(app.application).isNotNull()
        }
    }

    @Test
    fun `should launch specific activity by class name`() {
        TestPilot.load(apkFile!!).use { app ->
            val launcher = app.activities.find { it.isLauncher && it.isMain }
                ?: app.activities.first()
            app.launch(launcher.name)
            assertThat(app.application).isNotNull()
        }
    }

    @Test
    fun `should parse resources`() {
        val apkLoader = ApkLoader.load(apkFile!!)
        val contents = apkLoader.extract()
        try {
            assertThat(contents.resources).isNotNull()
            val table = ResourcesParser.parse(contents.resources!!)
            assertThat(table.packages).isNotEmpty()

            val resourceMap = table.buildResourceMap()
            assertThat(resourceMap).isNotEmpty()
            println("Total resources: ${resourceMap.size}")
            resourceMap.entries.take(10).forEach { (id, name) ->
                println("  0x${id.toString(16)} -> $name")
            }
        } finally {
            contents.outputDir.deleteRecursively()
        }
    }

    @Test
    fun `should resolve string resources with device config`() {
        val apkLoader = ApkLoader.load(apkFile!!)
        val contents = apkLoader.extract()
        try {
            val table = ResourcesParser.parse(contents.resources!!)
            val resolver = ResourceTableResolver(table, DeviceConfiguration.DEFAULT)

            // Find a string resource to test
            val resourceMap = table.buildResourceMap()
            val stringEntry = resourceMap.entries.find { it.value.contains(":string/") }
            assertThat(stringEntry).isNotNull()

            val resolved = resolver.resolveString(stringEntry!!.key)
            println("Resolved ${stringEntry.value} (0x${stringEntry.key.toString(16)}) -> $resolved")
            assertThat(resolved).isNotNull()
        } finally {
            contents.outputDir.deleteRecursively()
        }
    }

    @Test
    fun `should convert DEX to JVM bytecode`() {
        val apkLoader = ApkLoader.load(apkFile!!)
        val contents = apkLoader.extract()
        try {
            assertThat(contents.dexFiles).isNotEmpty()

            var totalClasses = 0
            for (dexFile in contents.dexFiles) {
                val result = DexConverter.convert(dexFile)
                totalClasses += result.classes.size
                println("${dexFile.name}: ${result.classes.size} classes, ${result.errors.size} errors")
            }
            assertThat(totalClasses).isGreaterThan(0)
            println("Total converted classes: $totalClasses")
        } finally {
            contents.outputDir.deleteRecursively()
        }
    }

    @Test
    fun `should parse binary AndroidManifest`() {
        val apkLoader = ApkLoader.load(apkFile!!)
        val contents = apkLoader.extract()
        try {
            assertThat(contents.manifest).isNotNull()
            val manifest = BinaryXmlParser.parse(contents.manifest!!)
            assertThat(manifest.rootElement).isNotNull()
            assertThat(manifest.rootElement!!.name).isEqualTo("manifest")

            val pkg = manifest.rootElement!!.attributes.find { it.name == "package" }?.value?.toString()
            assertThat(pkg).isNotNull().isNotEmpty()
            println("Manifest package: $pkg")
        } finally {
            contents.outputDir.deleteRecursively()
        }
    }
}
