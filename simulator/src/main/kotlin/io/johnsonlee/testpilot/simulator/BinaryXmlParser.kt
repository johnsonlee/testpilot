package io.johnsonlee.testpilot.simulator

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parser for Android Binary XML format (AXML).
 *
 * Used for AndroidManifest.xml and compiled layout XML files.
 *
 * Binary XML structure:
 * - File header (magic + file size)
 * - String pool (all strings used in the XML)
 * - Resource ID table (optional, maps to R.attr.*)
 * - XML tree (namespaces, elements, attributes)
 */
class BinaryXmlParser {

    companion object {
        // Chunk types
        private const val CHUNK_AXML_FILE = 0x00080003
        private const val CHUNK_STRING_POOL = 0x001C0001
        private const val CHUNK_RESOURCE_IDS = 0x00080180
        private const val CHUNK_XML_START_NAMESPACE = 0x00100100
        private const val CHUNK_XML_END_NAMESPACE = 0x00100101
        private const val CHUNK_XML_START_ELEMENT = 0x00100102
        private const val CHUNK_XML_END_ELEMENT = 0x00100103
        private const val CHUNK_XML_TEXT = 0x00100104

        // Attribute types
        private const val TYPE_NULL = 0x00
        private const val TYPE_REFERENCE = 0x01
        private const val TYPE_ATTRIBUTE = 0x02
        private const val TYPE_STRING = 0x03
        private const val TYPE_FLOAT = 0x04
        private const val TYPE_DIMENSION = 0x05
        private const val TYPE_FRACTION = 0x06
        private const val TYPE_INT_DEC = 0x10
        private const val TYPE_INT_HEX = 0x11
        private const val TYPE_INT_BOOLEAN = 0x12

        fun parse(file: File): XmlDocument = BinaryXmlParser().parseFile(file)
        fun parse(bytes: ByteArray): XmlDocument = BinaryXmlParser().parseBytes(bytes)
    }

    private lateinit var buffer: ByteBuffer
    private var strings: List<String> = emptyList()
    private var resourceIds: List<Int> = emptyList()

    data class XmlDocument(
        val rootElement: XmlElement?,
        val namespaces: Map<String, String>  // prefix -> uri
    )

    data class XmlElement(
        val name: String,
        val namespace: String?,
        val attributes: List<XmlAttribute>,
        val children: MutableList<XmlElement> = mutableListOf(),
        val text: String? = null
    )

    data class XmlAttribute(
        val name: String,
        val namespace: String?,
        val value: Any?,
        val rawValue: String?,
        val resourceId: Int?
    )

    fun parseFile(file: File): XmlDocument {
        val bytes = file.readBytes()
        return parseBytes(bytes)
    }

    fun parseBytes(bytes: ByteArray): XmlDocument {
        buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Read file header
        val magic = buffer.int
        if (magic != CHUNK_AXML_FILE) {
            throw IllegalArgumentException("Not a valid binary XML file (magic: ${magic.toString(16)})")
        }
        val fileSize = buffer.int

        val namespaces = mutableMapOf<String, String>()
        var rootElement: XmlElement? = null
        val elementStack = mutableListOf<XmlElement>()

        // Parse chunks
        while (buffer.position() < fileSize) {
            val chunkStart = buffer.position()
            val chunkType = buffer.int
            val chunkSize = buffer.int

            when (chunkType) {
                CHUNK_STRING_POOL -> parseStringPool(chunkStart, chunkSize)
                CHUNK_RESOURCE_IDS -> parseResourceIds(chunkStart, chunkSize)
                CHUNK_XML_START_NAMESPACE -> {
                    val (prefix, uri) = parseNamespace()
                    namespaces[prefix] = uri
                }
                CHUNK_XML_END_NAMESPACE -> {
                    // Skip, we keep namespaces in map
                    buffer.position(chunkStart + chunkSize)
                }
                CHUNK_XML_START_ELEMENT -> {
                    val element = parseStartElement()
                    if (elementStack.isEmpty()) {
                        rootElement = element
                    } else {
                        elementStack.last().children.add(element)
                    }
                    elementStack.add(element)
                }
                CHUNK_XML_END_ELEMENT -> {
                    parseEndElement()
                    if (elementStack.isNotEmpty()) {
                        elementStack.removeLast()
                    }
                }
                CHUNK_XML_TEXT -> {
                    val text = parseText()
                    if (elementStack.isNotEmpty()) {
                        // Add text as a text-only child element
                        elementStack.last().children.add(
                            XmlElement(name = "#text", namespace = null, attributes = emptyList(), text = text)
                        )
                    }
                }
                else -> {
                    // Skip unknown chunk
                    buffer.position(chunkStart + chunkSize)
                }
            }
        }

        return XmlDocument(rootElement, namespaces)
    }

    private fun parseStringPool(chunkStart: Int, chunkSize: Int) {
        val stringCount = buffer.int
        val styleCount = buffer.int
        val flags = buffer.int
        val stringsStart = buffer.int
        @Suppress("UNUSED_VARIABLE")
        val stylesStart = buffer.int

        val isUtf8 = (flags and 0x100) != 0

        // Read string offsets
        val stringOffsets = (0 until stringCount).map { buffer.int }

        // Skip style offsets
        buffer.position(buffer.position() + styleCount * 4)

        // stringsStart is relative to the start of the StringPool chunk header
        val stringsDataStart = chunkStart + stringsStart
        strings = stringOffsets.map { offset ->
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
    }

    private fun readUtf8String(): String {
        // UTF-8 encoded string: length (1-2 bytes) + data
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
        // UTF-16 encoded string: length (2-4 bytes) + data
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

    private fun parseResourceIds(chunkStart: Int, chunkSize: Int) {
        val count = (chunkSize - 8) / 4
        resourceIds = (0 until count).map { buffer.int }
    }

    private fun parseNamespace(): Pair<String, String> {
        buffer.int  // lineNumber
        buffer.int  // comment
        val prefixIdx = buffer.int
        val uriIdx = buffer.int
        val prefix = if (prefixIdx >= 0 && prefixIdx < strings.size) strings[prefixIdx] else ""
        val uri = if (uriIdx >= 0 && uriIdx < strings.size) strings[uriIdx] else ""
        return prefix to uri
    }

    private fun parseStartElement(): XmlElement {
        buffer.int  // lineNumber
        buffer.int  // comment
        val nsIdx = buffer.int
        val nameIdx = buffer.int
        buffer.short  // attributeStart
        val attributeSize = buffer.short.toInt()
        val attributeCount = buffer.short.toInt()
        buffer.short  // idIndex
        buffer.short  // classIndex
        buffer.short  // styleIndex

        val name = if (nameIdx >= 0 && nameIdx < strings.size) strings[nameIdx] else ""
        val namespace = if (nsIdx >= 0 && nsIdx < strings.size) strings[nsIdx] else null

        val attributes = (0 until attributeCount).map {
            parseAttribute()
        }

        return XmlElement(name, namespace, attributes)
    }

    private fun parseAttribute(): XmlAttribute {
        val nsIdx = buffer.int
        val nameIdx = buffer.int
        val rawValueIdx = buffer.int
        val typedValueSize = buffer.short.toInt()
        buffer.get()  // res0
        val typedValueType = buffer.get().toInt() and 0xFF
        val typedValueData = buffer.int

        val name = if (nameIdx >= 0 && nameIdx < strings.size) strings[nameIdx] else ""
        val namespace = if (nsIdx >= 0 && nsIdx < strings.size) strings[nsIdx] else null
        val rawValue = if (rawValueIdx >= 0 && rawValueIdx < strings.size) strings[rawValueIdx] else null

        val resourceId = if (nameIdx >= 0 && nameIdx < resourceIds.size) resourceIds[nameIdx] else null

        val value: Any? = when (typedValueType) {
            TYPE_NULL -> null
            TYPE_REFERENCE -> "@${typedValueData.toString(16)}"
            TYPE_ATTRIBUTE -> "?${typedValueData.toString(16)}"
            TYPE_STRING -> if (typedValueData >= 0 && typedValueData < strings.size) strings[typedValueData] else rawValue
            TYPE_FLOAT -> java.lang.Float.intBitsToFloat(typedValueData)
            TYPE_DIMENSION -> parseDimension(typedValueData)
            TYPE_FRACTION -> parseFraction(typedValueData)
            TYPE_INT_DEC -> typedValueData
            TYPE_INT_HEX -> "0x${typedValueData.toString(16)}"
            TYPE_INT_BOOLEAN -> typedValueData != 0
            else -> typedValueData
        }

        return XmlAttribute(name, namespace, value, rawValue, resourceId)
    }

    private fun parseDimension(data: Int): String {
        val value = (data shr 8).toFloat() / (1 shl ((data shr 4) and 0xF))
        val unit = when (data and 0xF) {
            0 -> "px"
            1 -> "dp"
            2 -> "sp"
            3 -> "pt"
            4 -> "in"
            5 -> "mm"
            else -> ""
        }
        return "${value}${unit}"
    }

    private fun parseFraction(data: Int): String {
        val value = (data shr 8).toFloat() / (1 shl ((data shr 4) and 0xF))
        val type = if ((data and 0xF) == 0) "%" else "%p"
        return "${value * 100}${type}"
    }

    private fun parseEndElement() {
        buffer.int  // lineNumber
        buffer.int  // comment
        buffer.int  // nsIdx
        buffer.int  // nameIdx
    }

    private fun parseText(): String {
        buffer.int  // lineNumber
        buffer.int  // comment
        val textIdx = buffer.int
        buffer.int  // typedValue (size + res0)
        buffer.int  // typedValue (type + data)
        return if (textIdx >= 0 && textIdx < strings.size) strings[textIdx] else ""
    }
}
