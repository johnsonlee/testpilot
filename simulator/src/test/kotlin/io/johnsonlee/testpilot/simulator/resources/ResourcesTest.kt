package io.johnsonlee.testpilot.simulator.resources

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ResourcesTest {

    @Test
    fun `getString should return manually added string`() {
        val resources = Resources()
        resources.addString(0x7f010001, "Hello")

        assertThat(resources.getString(0x7f010001)).isEqualTo("Hello")
    }

    @Test
    fun `getString should prefer resolver over manual map`() {
        val resources = Resources()
        resources.addString(0x7f010001, "fallback")
        resources.resolver = object : ResourceResolver {
            override fun resolveString(resId: Int) = "resolved"
            override fun resolveLayout(resId: Int) = null
            override fun resolveInteger(resId: Int) = null
            override fun resolveBoolean(resId: Int) = null
            override fun resolveColor(resId: Int) = null
            override fun resolveDimension(resId: Int) = null
        }

        assertThat(resources.getString(0x7f010001)).isEqualTo("resolved")
    }

    @Test
    fun `getString should fall back to manual map when resolver returns null`() {
        val resources = Resources()
        resources.addString(0x7f010001, "fallback")
        resources.resolver = object : ResourceResolver {
            override fun resolveString(resId: Int) = null
            override fun resolveLayout(resId: Int) = null
            override fun resolveInteger(resId: Int) = null
            override fun resolveBoolean(resId: Int) = null
            override fun resolveColor(resId: Int) = null
            override fun resolveDimension(resId: Int) = null
        }

        assertThat(resources.getString(0x7f010001)).isEqualTo("fallback")
    }

    @Test
    fun `getString should throw when not found`() {
        val resources = Resources()

        assertThatThrownBy { resources.getString(0x7f010001) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `getLayout should return manually added layout`() {
        val resources = Resources()
        resources.addLayout(0x7f020001, "res/layout/main.xml")

        assertThat(resources.getLayout(0x7f020001)).isEqualTo("res/layout/main.xml")
    }

    @Test
    fun `getInteger should use resolver`() {
        val resources = Resources()
        resources.resolver = object : ResourceResolver {
            override fun resolveString(resId: Int) = null
            override fun resolveLayout(resId: Int) = null
            override fun resolveInteger(resId: Int) = 42
            override fun resolveBoolean(resId: Int) = null
            override fun resolveColor(resId: Int) = null
            override fun resolveDimension(resId: Int) = null
        }

        assertThat(resources.getInteger(0x7f030001)).isEqualTo(42)
    }

    @Test
    fun `getInteger should throw when no resolver`() {
        val resources = Resources()

        assertThatThrownBy { resources.getInteger(0x7f030001) }
            .isInstanceOf(ResourceNotFoundException::class.java)
    }

    @Test
    fun `getBoolean should use resolver`() {
        val resources = Resources()
        resources.resolver = object : ResourceResolver {
            override fun resolveString(resId: Int) = null
            override fun resolveLayout(resId: Int) = null
            override fun resolveInteger(resId: Int) = null
            override fun resolveBoolean(resId: Int) = true
            override fun resolveColor(resId: Int) = null
            override fun resolveDimension(resId: Int) = null
        }

        assertThat(resources.getBoolean(0x7f040001)).isTrue()
    }

    @Test
    fun `getColor should use resolver`() {
        val resources = Resources()
        resources.resolver = object : ResourceResolver {
            override fun resolveString(resId: Int) = null
            override fun resolveLayout(resId: Int) = null
            override fun resolveInteger(resId: Int) = null
            override fun resolveBoolean(resId: Int) = null
            override fun resolveColor(resId: Int) = 0xFFFF0000.toInt()
            override fun resolveDimension(resId: Int) = null
        }

        assertThat(resources.getColor(0x7f050001)).isEqualTo(0xFFFF0000.toInt())
    }

    @Test
    fun `getDimension should use resolver`() {
        val resources = Resources()
        resources.resolver = object : ResourceResolver {
            override fun resolveString(resId: Int) = null
            override fun resolveLayout(resId: Int) = null
            override fun resolveInteger(resId: Int) = null
            override fun resolveBoolean(resId: Int) = null
            override fun resolveColor(resId: Int) = null
            override fun resolveDimension(resId: Int) = 16.0f
        }

        assertThat(resources.getDimension(0x7f060001)).isEqualTo(16.0f)
    }

    @Test
    fun `configuration should default to DEFAULT`() {
        val resources = Resources()
        assertThat(resources.configuration).isEqualTo(Configuration.DEFAULT)
    }

    @Test
    fun `configuration should accept custom value`() {
        val config = Configuration(locale = "ja", density = Configuration.DENSITY_XXHDPI)
        val resources = Resources(config)
        assertThat(resources.configuration.locale).isEqualTo("ja")
        assertThat(resources.configuration.density).isEqualTo(Configuration.DENSITY_XXHDPI)
    }
}
