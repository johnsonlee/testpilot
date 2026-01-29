package io.johnsonlee.testpilot.loader

import io.johnsonlee.testpilot.simulator.resources.Configuration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ResourceTableResolverTest {

    private fun makeResourceTable(
        types: List<ResourcesParser.ResourceType>,
        packageId: Int = 0x7f,
        packageName: String = "com.example"
    ): ResourcesParser.ResourceTable {
        val pkg = ResourcesParser.ResourcePackage(packageId, packageName, types)
        return ResourcesParser.ResourceTable(listOf(pkg), emptyList())
    }

    private fun stringEntry(id: Int, name: String, value: String) =
        ResourcesParser.ResourceEntry(id, name, ResourcesParser.ResourceValue.StringValue(value))

    private fun intEntry(id: Int, name: String, value: Int) =
        ResourcesParser.ResourceEntry(id, name, ResourcesParser.ResourceValue.IntValue(value))

    private fun boolEntry(id: Int, name: String, value: Boolean) =
        ResourcesParser.ResourceEntry(id, name, ResourcesParser.ResourceValue.BoolValue(value))

    private fun colorEntry(id: Int, name: String, value: Int) =
        ResourcesParser.ResourceEntry(id, name, ResourcesParser.ResourceValue.ColorValue(value))

    private fun dimEntry(id: Int, name: String, value: Float) =
        ResourcesParser.ResourceEntry(id, name, ResourcesParser.ResourceValue.DimensionValue(value, "dp"))

    private fun config(language: String = "", density: Int = 0) =
        ResourcesParser.ResTableConfig(size = 28, language = language, density = density)

    @Test
    fun `resolveString should return string from default config`() {
        val table = makeResourceTable(
            listOf(
                ResourcesParser.ResourceType(1, "string", config(), listOf(
                    stringEntry(0, "app_name", "MyApp")
                ))
            )
        )
        val resolver = ResourceTableResolver(table, Configuration.DEFAULT)

        // resId = 0x7f010000 (package 0x7f, type 1, entry 0)
        assertThat(resolver.resolveString(0x7f010000)).isEqualTo("MyApp")
    }

    @Test
    fun `resolveString should select localized variant`() {
        val table = makeResourceTable(
            listOf(
                ResourcesParser.ResourceType(1, "string", config(), listOf(
                    stringEntry(0, "app_name", "MyApp")
                )),
                ResourcesParser.ResourceType(1, "string", config(language = "es"), listOf(
                    stringEntry(0, "app_name", "MiApp")
                ))
            )
        )
        val resolver = ResourceTableResolver(table, Configuration(locale = "es"))

        assertThat(resolver.resolveString(0x7f010000)).isEqualTo("MiApp")
    }

    @Test
    fun `resolveString should fall back to default when locale not matched`() {
        val table = makeResourceTable(
            listOf(
                ResourcesParser.ResourceType(1, "string", config(), listOf(
                    stringEntry(0, "app_name", "MyApp")
                )),
                ResourcesParser.ResourceType(1, "string", config(language = "es"), listOf(
                    stringEntry(0, "app_name", "MiApp")
                ))
            )
        )
        val resolver = ResourceTableResolver(table, Configuration(locale = "ja"))

        assertThat(resolver.resolveString(0x7f010000)).isEqualTo("MyApp")
    }

    @Test
    fun `resolveInteger should return int value`() {
        val table = makeResourceTable(
            listOf(
                ResourcesParser.ResourceType(1, "integer", config(), listOf(
                    intEntry(0, "max_count", 100)
                ))
            )
        )
        val resolver = ResourceTableResolver(table, Configuration.DEFAULT)

        assertThat(resolver.resolveInteger(0x7f010000)).isEqualTo(100)
    }

    @Test
    fun `resolveBoolean should return bool value`() {
        val table = makeResourceTable(
            listOf(
                ResourcesParser.ResourceType(1, "bool", config(), listOf(
                    boolEntry(0, "is_tablet", false)
                ))
            )
        )
        val resolver = ResourceTableResolver(table, Configuration.DEFAULT)

        assertThat(resolver.resolveBoolean(0x7f010000)).isFalse()
    }

    @Test
    fun `resolveColor should return color value`() {
        val table = makeResourceTable(
            listOf(
                ResourcesParser.ResourceType(1, "color", config(), listOf(
                    colorEntry(0, "primary", 0xFF0000FF.toInt())
                ))
            )
        )
        val resolver = ResourceTableResolver(table, Configuration.DEFAULT)

        assertThat(resolver.resolveColor(0x7f010000)).isEqualTo(0xFF0000FF.toInt())
    }

    @Test
    fun `resolveDimension should return dimension value`() {
        val table = makeResourceTable(
            listOf(
                ResourcesParser.ResourceType(1, "dimen", config(), listOf(
                    dimEntry(0, "padding", 16.0f)
                ))
            )
        )
        val resolver = ResourceTableResolver(table, Configuration.DEFAULT)

        assertThat(resolver.resolveDimension(0x7f010000)).isEqualTo(16.0f)
    }

    @Test
    fun `should return null for non-existent resource`() {
        val table = makeResourceTable(emptyList())
        val resolver = ResourceTableResolver(table, Configuration.DEFAULT)

        assertThat(resolver.resolveString(0x7f010000)).isNull()
        assertThat(resolver.resolveInteger(0x7f010000)).isNull()
    }

    @Test
    fun `resolveString should return null for wrong type`() {
        val table = makeResourceTable(
            listOf(
                ResourcesParser.ResourceType(1, "integer", config(), listOf(
                    intEntry(0, "count", 42)
                ))
            )
        )
        val resolver = ResourceTableResolver(table, Configuration.DEFAULT)

        // Trying to resolve as string should return null since it's an IntValue
        assertThat(resolver.resolveString(0x7f010000)).isNull()
    }
}
