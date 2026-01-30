package io.johnsonlee.testpilot.simulator

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Android resources.arsc file.
 *
 * This file contains compiled resources and resource ID mappings.
 *
 * Structure:
 * - Table header
 * - Global string pool
 * - Package chunks (one per package)
 *   - Type string pool (type names: string, drawable, layout, etc.)
 *   - Key string pool (resource names: app_name, ic_launcher, etc.)
 *   - Type spec chunks
 *   - Type chunks (actual resource values)
 */
class ResourcesParser {

    companion object {
        // Chunk types
        private const val RES_TABLE_TYPE = 0x0002
        private const val RES_STRING_POOL_TYPE = 0x0001
        private const val RES_TABLE_PACKAGE_TYPE = 0x0200
        private const val RES_TABLE_TYPE_SPEC_TYPE = 0x0202
        private const val RES_TABLE_TYPE_TYPE = 0x0201

        fun parse(file: File): ResourceTable = ResourcesParser().parseFile(file)
        fun parse(bytes: ByteArray): ResourceTable = ResourcesParser().parseBytes(bytes)
    }

    private lateinit var buffer: ByteBuffer
    private var globalStrings: List<String> = emptyList()

    /**
     * Parsed resource table.
     */
    data class ResourceTable(
        val packages: List<ResourcePackage>,
        val globalStrings: List<String>
    ) {
        /**
         * Gets a resource by ID.
         */
        fun getResource(resourceId: Int): ResourceEntry? {
            val packageId = (resourceId shr 24) and 0xFF
            val typeId = (resourceId shr 16) and 0xFF
            val entryId = resourceId and 0xFFFF

            val pkg = packages.find { it.id == packageId } ?: return null
            val type = pkg.types.find { it.id == typeId } ?: return null
            return type.entries.find { it.id == entryId }
        }

        /**
         * Gets all resources by type name.
         */
        fun getResourcesByType(packageName: String, typeName: String): List<ResourceEntry> {
            val pkg = packages.find { it.name == packageName } ?: return emptyList()
            val type = pkg.types.find { it.name == typeName } ?: return emptyList()
            return type.entries
        }

        /**
         * Gets all configuration variants for a resource ID.
         * Returns a list of (config, entry) pairs across all config chunks.
         */
        fun getResourceVariants(resourceId: Int): List<Pair<ResTableConfig, ResourceEntry>> {
            val packageId = (resourceId shr 24) and 0xFF
            val typeId = (resourceId shr 16) and 0xFF
            val entryId = resourceId and 0xFFFF

            val pkg = packages.find { it.id == packageId } ?: return emptyList()
            return pkg.types
                .filter { it.id == typeId }
                .mapNotNull { type ->
                    type.entries.find { it.id == entryId }?.let { entry ->
                        type.config to entry
                    }
                }
        }

        /**
         * Builds a map of resource ID to resource name (for R.java generation).
         */
        fun buildResourceMap(): Map<Int, String> {
            val map = mutableMapOf<Int, String>()
            for (pkg in packages) {
                for (type in pkg.types) {
                    for (entry in type.entries) {
                        val resourceId = (pkg.id shl 24) or (type.id shl 16) or entry.id
                        map[resourceId] = "${pkg.name}:${type.name}/${entry.name}"
                    }
                }
            }
            return map
        }
    }

    data class ResourcePackage(
        val id: Int,
        val name: String,
        val types: List<ResourceType>
    )

    /**
     * Parsed ResTable_config struct representing a device configuration.
     */
    data class ResTableConfig(
        val size: Int,
        val mcc: Int = 0,
        val mnc: Int = 0,
        val language: String = "",
        val country: String = "",
        val orientation: Int = 0,
        val touchscreen: Int = 0,
        val density: Int = 0,
        val keyboard: Int = 0,
        val navigation: Int = 0,
        val inputFlags: Int = 0,
        val screenWidth: Int = 0,
        val screenHeight: Int = 0,
        val sdkVersion: Int = 0,
        val minorVersion: Int = 0,
        val screenLayout: Int = 0,
        val uiMode: Int = 0,
        val smallestScreenWidthDp: Int = 0,
        val screenWidthDp: Int = 0,
        val screenHeightDp: Int = 0
    ) {
        val isDefault: Boolean
            get() = language.isEmpty() && country.isEmpty() && density == 0 &&
                    orientation == 0 && uiMode == 0 && screenLayout == 0 &&
                    sdkVersion == 0 && mcc == 0 && mnc == 0 &&
                    smallestScreenWidthDp == 0 && screenWidthDp == 0 && screenHeightDp == 0

        val nightMode: Int get() = (uiMode shr 4) and 0x3

        companion object {
            val DEFAULT = ResTableConfig(size = 0)
        }
    }

    data class ResourceType(
        val id: Int,
        val name: String,
        val config: ResTableConfig,
        val entries: List<ResourceEntry>
    )

    data class ResourceEntry(
        val id: Int,
        val name: String,
        val value: ResourceValue?
    )

    sealed class ResourceValue {
        data class StringValue(val value: String) : ResourceValue()
        data class IntValue(val value: Int) : ResourceValue()
        data class BoolValue(val value: Boolean) : ResourceValue()
        data class ColorValue(val value: Int) : ResourceValue()
        data class DimensionValue(val value: Float, val unit: String) : ResourceValue()
        data class ReferenceValue(val resourceId: Int) : ResourceValue()
        data class ComplexValue(val entries: Map<String, ResourceValue>) : ResourceValue()
        object NullValue : ResourceValue()
    }

    fun parseFile(file: File): ResourceTable {
        val bytes = file.readBytes()
        return parseBytes(bytes)
    }

    fun parseBytes(bytes: ByteArray): ResourceTable {
        buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read table header
        val type = buffer.short.toInt() and 0xFFFF
        val headerSize = buffer.short.toInt() and 0xFFFF
        val size = buffer.int
        val packageCount = buffer.int

        if (type != RES_TABLE_TYPE) {
            throw IllegalArgumentException("Not a valid resources.arsc file")
        }

        val packages = mutableListOf<ResourcePackage>()

        // Parse chunks
        while (buffer.position() < size) {
            val chunkStart = buffer.position()
            val chunkType = buffer.short.toInt() and 0xFFFF
            val chunkHeaderSize = buffer.short.toInt() and 0xFFFF
            val chunkSize = buffer.int

            when (chunkType) {
                RES_STRING_POOL_TYPE -> {
                    buffer.position(chunkStart)
                    globalStrings = parseStringPool(chunkSize)
                }
                RES_TABLE_PACKAGE_TYPE -> {
                    buffer.position(chunkStart)
                    packages.add(parsePackage(chunkSize))
                }
                else -> {
                    // Skip unknown chunk
                }
            }

            buffer.position(chunkStart + chunkSize)
        }

        return ResourceTable(packages, globalStrings)
    }

    private fun parseStringPool(chunkSize: Int): List<String> {
        val chunkStart = buffer.position()

        buffer.short  // type
        buffer.short  // headerSize
        buffer.int    // size
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        buffer.int    // stylesStart

        val isUtf8 = (flags and 0x100) != 0

        // Read string offsets
        val stringOffsets = (0 until stringCount).map { buffer.int }

        // Skip style offsets
        buffer.position(buffer.position() + styleCount * 4)

        val stringsDataStart = chunkStart + stringsStart
        val strings = stringOffsets.map { offset ->
            buffer.position(stringsDataStart + offset)
            try {
                if (isUtf8) {
                    readUtf8String()
                } else {
                    readUtf16String()
                }
            } catch (e: Exception) {
                ""
            }
        }

        buffer.position(chunkStart + chunkSize)
        return strings
    }

    private fun readUtf8String(): String {
        var charLen = buffer.get().toInt() and 0xFF
        if ((charLen and 0x80) != 0) {
            charLen = ((charLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        var byteLen = buffer.get().toInt() and 0xFF
        if ((byteLen and 0x80) != 0) {
            byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get().toInt() and 0xFF)
        }
        val bytes = ByteArray(byteLen)
        buffer.get(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun readUtf16String(): String {
        var charLen = buffer.short.toInt() and 0xFFFF
        if ((charLen and 0x8000) != 0) {
            charLen = ((charLen and 0x7FFF) shl 16) or (buffer.short.toInt() and 0xFFFF)
        }
        val chars = CharArray(charLen)
        for (i in 0 until charLen) {
            chars[i] = buffer.short.toInt().toChar()
        }
        return String(chars)
    }

    private fun parsePackage(chunkSize: Int): ResourcePackage {
        val chunkStart = buffer.position()

        buffer.short  // type
        val headerSize = buffer.short.toInt() and 0xFFFF
        buffer.int    // size
        val id = buffer.int

        // Package name (128 chars, UTF-16)
        val nameChars = CharArray(128)
        for (i in 0 until 128) {
            nameChars[i] = buffer.short.toInt().toChar()
        }
        val name = String(nameChars).trimEnd('\u0000')

        val typeStrings = buffer.int  // offset to type string pool
        val lastPublicType = buffer.int
        val keyStrings = buffer.int   // offset to key string pool
        val lastPublicKey = buffer.int

        // Parse type and key string pools
        var typeStringPool: List<String> = emptyList()
        var keyStringPool: List<String> = emptyList()
        val types = mutableListOf<ResourceType>()
        val typeSpecs = mutableMapOf<Int, IntArray>()

        // Move to after header to parse children
        buffer.position(chunkStart + headerSize)

        while (buffer.position() < chunkStart + chunkSize) {
            val childStart = buffer.position()
            val childType = buffer.short.toInt() and 0xFFFF
            val childHeaderSize = buffer.short.toInt() and 0xFFFF
            val childSize = buffer.int

            when (childType) {
                RES_STRING_POOL_TYPE -> {
                    buffer.position(childStart)
                    val pool = parseStringPool(childSize)
                    if (typeStringPool.isEmpty()) {
                        typeStringPool = pool
                    } else {
                        keyStringPool = pool
                    }
                }
                RES_TABLE_TYPE_SPEC_TYPE -> {
                    val typeId = buffer.get().toInt() and 0xFF
                    buffer.get()  // res0
                    buffer.short  // res1
                    val entryCount = buffer.int
                    val flags = IntArray(entryCount) { buffer.int }
                    typeSpecs[typeId] = flags
                }
                RES_TABLE_TYPE_TYPE -> {
                    buffer.position(childStart)
                    val type = parseType(childSize, typeStringPool, keyStringPool)
                    if (type != null) {
                        types.add(type)
                    }
                }
            }

            buffer.position(childStart + childSize)
        }

        return ResourcePackage(id, name, types)
    }

    private fun parseType(chunkSize: Int, typeStrings: List<String>, keyStrings: List<String>): ResourceType? {
        val chunkStart = buffer.position()

        buffer.short  // type
        val headerSize = buffer.short.toInt() and 0xFFFF
        buffer.int    // size
        val id = buffer.get().toInt() and 0xFF
        buffer.get()  // res0
        buffer.short  // res1
        val entryCount = buffer.int
        val entriesStart = buffer.int

        // Read ResTable_config
        val config = parseConfig(chunkStart + headerSize)

        // Read entry offsets
        val entryOffsets = (0 until entryCount).map { buffer.int }

        val typeName = if (id > 0 && id <= typeStrings.size) typeStrings[id - 1] else "unknown"
        val entries = mutableListOf<ResourceEntry>()

        val entriesDataStart = chunkStart + entriesStart
        for ((index, offset) in entryOffsets.withIndex()) {
            if (offset == -1) continue

            buffer.position(entriesDataStart + offset)
            val entry = parseEntry(index, keyStrings)
            if (entry != null) {
                entries.add(entry)
            }
        }

        return ResourceType(id, typeName, config, entries)
    }

    private fun parseConfig(endPosition: Int): ResTableConfig {
        val configStart = buffer.position()
        val configSize = buffer.int

        if (configSize < 28) {
            // Too small, return default
            buffer.position(endPosition)
            return ResTableConfig.DEFAULT
        }

        val mcc = buffer.short.toInt() and 0xFFFF
        val mnc = buffer.short.toInt() and 0xFFFF

        // Language and country are 2 bytes each
        val langBytes = ByteArray(2)
        buffer.get(langBytes)
        val language = if (langBytes[0] != 0.toByte()) String(langBytes).trimEnd('\u0000') else ""

        val countryBytes = ByteArray(2)
        buffer.get(countryBytes)
        val country = if (countryBytes[0] != 0.toByte()) String(countryBytes).trimEnd('\u0000') else ""

        val orientation = buffer.get().toInt() and 0xFF
        val touchscreen = buffer.get().toInt() and 0xFF
        val density = buffer.short.toInt() and 0xFFFF

        val keyboard = buffer.get().toInt() and 0xFF
        val navigation = buffer.get().toInt() and 0xFF
        val inputFlags = buffer.get().toInt() and 0xFF
        buffer.get() // inputPad0

        val screenWidth = buffer.short.toInt() and 0xFFFF
        val screenHeight = buffer.short.toInt() and 0xFFFF

        val sdkVersion = if (configSize >= 32) buffer.short.toInt() and 0xFFFF else 0
        val minorVersion = if (configSize >= 32) buffer.short.toInt() and 0xFFFF else 0

        val screenLayout = if (configSize >= 36) buffer.get().toInt() and 0xFF else 0
        val uiMode = if (configSize >= 36) buffer.get().toInt() and 0xFF else 0
        val smallestScreenWidthDp = if (configSize >= 36) buffer.short.toInt() and 0xFFFF else 0

        val screenWidthDp = if (configSize >= 40) buffer.short.toInt() and 0xFFFF else 0
        val screenHeightDp = if (configSize >= 40) buffer.short.toInt() and 0xFFFF else 0

        // Skip any remaining bytes in config
        buffer.position(endPosition)

        return ResTableConfig(
            size = configSize,
            mcc = mcc, mnc = mnc,
            language = language, country = country,
            orientation = orientation, touchscreen = touchscreen, density = density,
            keyboard = keyboard, navigation = navigation, inputFlags = inputFlags,
            screenWidth = screenWidth, screenHeight = screenHeight,
            sdkVersion = sdkVersion, minorVersion = minorVersion,
            screenLayout = screenLayout, uiMode = uiMode,
            smallestScreenWidthDp = smallestScreenWidthDp,
            screenWidthDp = screenWidthDp, screenHeightDp = screenHeightDp
        )
    }

    private fun parseEntry(entryId: Int, keyStrings: List<String>): ResourceEntry? {
        val size = buffer.short.toInt() and 0xFFFF
        val flags = buffer.short.toInt() and 0xFFFF
        val keyIndex = buffer.int

        val name = if (keyIndex >= 0 && keyIndex < keyStrings.size) keyStrings[keyIndex] else "entry_$entryId"

        val isComplex = (flags and 0x0001) != 0

        val value = if (isComplex) {
            // Complex entry (bag)
            val parent = buffer.int
            val count = buffer.int
            val map = mutableMapOf<String, ResourceValue>()
            for (i in 0 until count) {
                val mapKey = buffer.int
                val mapValue = parseValue()
                map["0x${mapKey.toString(16)}"] = mapValue
            }
            ResourceValue.ComplexValue(map)
        } else {
            parseValue()
        }

        return ResourceEntry(entryId, name, value)
    }

    private fun parseValue(): ResourceValue {
        val size = buffer.short.toInt() and 0xFFFF
        buffer.get()  // res0
        val type = buffer.get().toInt() and 0xFF
        val data = buffer.int

        return when (type) {
            0x00 -> ResourceValue.NullValue
            0x01 -> ResourceValue.ReferenceValue(data)
            0x03 -> ResourceValue.StringValue(
                if (data >= 0 && data < globalStrings.size) globalStrings[data] else ""
            )
            0x04 -> ResourceValue.IntValue(java.lang.Float.intBitsToFloat(data).toInt())
            0x05 -> parseDimensionValue(data)
            0x10 -> ResourceValue.IntValue(data)
            0x11 -> ResourceValue.IntValue(data)
            0x12 -> ResourceValue.BoolValue(data != 0)
            0x1c, 0x1d, 0x1e, 0x1f -> ResourceValue.ColorValue(data)
            else -> ResourceValue.IntValue(data)
        }
    }

    private fun parseDimensionValue(data: Int): ResourceValue.DimensionValue {
        val value = (data and 0xFFFFFF00.toInt()).ushr(8).toFloat()
        val unit = when (data and 0xF) {
            0 -> "px"
            1 -> "dp"
            2 -> "sp"
            3 -> "pt"
            4 -> "in"
            5 -> "mm"
            else -> ""
        }
        return ResourceValue.DimensionValue(value, unit)
    }
}
