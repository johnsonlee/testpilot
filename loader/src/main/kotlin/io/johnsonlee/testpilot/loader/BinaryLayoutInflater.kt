package io.johnsonlee.testpilot.loader

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.view.View
import io.johnsonlee.testpilot.simulator.view.ViewGroup
import io.johnsonlee.testpilot.simulator.widget.*
import java.io.File

/**
 * Inflates View hierarchy from Android binary XML layout files.
 *
 * This extends the basic LayoutInflater to support compiled binary XML
 * format used in APKs.
 */
class BinaryLayoutInflater(
    private val context: Context,
    private val resourceTable: ResourcesParser.ResourceTable?,
    private val resDirectory: File?
) {
    // Cache of parsed layout XML
    private val layoutCache = mutableMapOf<Int, BinaryXmlParser.XmlDocument>()

    /**
     * Inflate a layout from a resource ID.
     */
    fun inflate(layoutResId: Int, root: ViewGroup?): View? {
        // Try to find the layout file
        val layoutEntry = resourceTable?.getResource(layoutResId)
        if (layoutEntry == null) {
            println("[BinaryLayoutInflater] Layout resource not found: 0x${layoutResId.toString(16)}")
            return null
        }

        // Get the layout file path from resources
        val layoutValue = layoutEntry.value
        val layoutPath = when (layoutValue) {
            is ResourcesParser.ResourceValue.StringValue -> layoutValue.value
            else -> "res/layout/${layoutEntry.name}.xml"
        }

        return inflateFromPath(layoutPath, root)
    }

    /**
     * Inflate a layout from a file path relative to APK root.
     */
    fun inflateFromPath(layoutPath: String, root: ViewGroup?): View? {
        val layoutFile = resDirectory?.let { File(it.parentFile, layoutPath) }
        if (layoutFile == null || !layoutFile.exists()) {
            println("[BinaryLayoutInflater] Layout file not found: $layoutPath")
            return null
        }

        return inflateFromFile(layoutFile, root)
    }

    /**
     * Inflate a layout from a file.
     */
    fun inflateFromFile(layoutFile: File, root: ViewGroup?): View? {
        try {
            val document = BinaryXmlParser.parse(layoutFile)
            val rootElement = document.rootElement ?: return null
            return createViewFromElement(rootElement, root)
        } catch (e: Exception) {
            println("[BinaryLayoutInflater] Error inflating layout: ${e.message}")
            return null
        }
    }

    /**
     * Inflate a layout from a parsed XML document.
     */
    fun inflate(document: BinaryXmlParser.XmlDocument, root: ViewGroup?): View? {
        val rootElement = document.rootElement ?: return null
        return createViewFromElement(rootElement, root)
    }

    private fun createViewFromElement(element: BinaryXmlParser.XmlElement, parent: ViewGroup?): View {
        // Handle special elements
        if (element.name == "merge") {
            // <merge> tag - add children directly to parent
            if (parent == null) {
                throw IllegalArgumentException("<merge> can only be used with a valid ViewGroup root")
            }
            for (child in element.children) {
                if (child.name != "#text") {
                    val childView = createViewFromElement(child, parent)
                    parent.addView(childView)
                }
            }
            return parent
        }

        if (element.name == "include") {
            // <include> tag - include another layout
            val layoutAttr = element.attributes.find { it.name == "layout" }
            if (layoutAttr != null) {
                val layoutRef = layoutAttr.value?.toString() ?: ""
                if (layoutRef.startsWith("@layout/")) {
                    val layoutName = layoutRef.substringAfter("@layout/")
                    val included = inflateFromPath("res/layout/$layoutName.xml", parent)
                    if (included != null) {
                        return included
                    }
                }
            }
            // Fallback to empty View
            return View(context)
        }

        val view = createView(element.name)

        // Parse attributes
        parseAttributes(view, element)

        // Parse layout params
        val layoutParams = parseLayoutParams(element, parent)
        view.layoutParams = layoutParams

        // If this is a ViewGroup, parse children
        if (view is ViewGroup) {
            for (child in element.children) {
                if (child.name != "#text") {
                    val childView = createViewFromElement(child, view)
                    view.addView(childView)
                }
            }
        }

        return view
    }

    private fun createView(tagName: String): View {
        // Handle fully qualified class names
        val simpleName = tagName.substringAfterLast('.')

        return when (simpleName) {
            // Layouts
            "FrameLayout" -> FrameLayout(context)
            "LinearLayout" -> LinearLayout(context)
            "RelativeLayout" -> FrameLayout(context)  // Simplified to FrameLayout
            "ConstraintLayout" -> FrameLayout(context)  // Simplified to FrameLayout
            "CoordinatorLayout" -> FrameLayout(context)
            "ScrollView" -> ScrollView(context)
            "HorizontalScrollView" -> ScrollView(context)
            "NestedScrollView" -> ScrollView(context)

            // Basic widgets
            "View" -> View(context)
            "TextView" -> TextView(context)
            "Button" -> Button(context)
            "ImageView" -> ImageView(context)
            "ImageButton" -> ImageButton(context)
            "EditText" -> EditText(context)
            "CheckBox" -> CheckBox(context)
            "RadioButton" -> RadioButton(context)
            "Switch" -> Switch(context)
            "ProgressBar" -> ProgressBar(context)
            "SeekBar" -> SeekBar(context)

            // Container widgets
            "RecyclerView" -> RecyclerView(context)
            "ListView" -> ListView(context)
            "GridView" -> GridView(context)
            "ViewPager", "ViewPager2" -> ViewPager(context)
            "CardView" -> CardView(context)

            // AppCompat variants
            "AppCompatTextView" -> TextView(context)
            "AppCompatButton" -> Button(context)
            "AppCompatImageView" -> ImageView(context)
            "AppCompatEditText" -> EditText(context)
            "AppCompatCheckBox" -> CheckBox(context)
            "MaterialButton" -> Button(context)
            "MaterialCardView" -> CardView(context)

            // Toolbar
            "Toolbar", "MaterialToolbar" -> Toolbar(context)
            "AppBarLayout" -> LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            "CollapsingToolbarLayout" -> FrameLayout(context)

            // Other
            "Space" -> Space(context)
            "ViewStub" -> View(context)  // Simplified

            else -> {
                // Unknown view - create a basic View
                println("[BinaryLayoutInflater] Unknown view type: $tagName, using View")
                View(context)
            }
        }
    }

    private fun parseAttributes(view: View, element: BinaryXmlParser.XmlElement) {
        for (attr in element.attributes) {
            parseAttribute(view, attr)
        }
    }

    private fun parseAttribute(view: View, attr: BinaryXmlParser.XmlAttribute) {
        val name = attr.name
        val value = attr.value

        when (name) {
            // Common View attributes
            "id" -> view.id = parseId(value)
            "visibility" -> view.visibility = parseVisibility(value)
            "alpha" -> view.alpha = (value as? Number)?.toFloat() ?: 1f
            "background" -> parseBackground(view, value)
            "padding" -> parsePadding(view, value)
            "paddingLeft", "paddingStart" -> view.setPadding(parseDimension(value), view.paddingTop, view.paddingRight, view.paddingBottom)
            "paddingTop" -> view.setPadding(view.paddingLeft, parseDimension(value), view.paddingRight, view.paddingBottom)
            "paddingRight", "paddingEnd" -> view.setPadding(view.paddingLeft, view.paddingTop, parseDimension(value), view.paddingBottom)
            "paddingBottom" -> view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, parseDimension(value))
            "enabled" -> view.isEnabled = value == true || value == "true"
            "clickable" -> view.isClickable = value == true || value == "true"
            "focusable" -> view.isFocusable = value == true || value == "true"

            // TextView attributes
            "text" -> if (view is TextView) view.text = parseText(value)
            "textSize" -> if (view is TextView) view.textSize = parseDimensionFloat(value)
            "textColor" -> if (view is TextView) view.textColor = parseColor(value)
            "hint" -> if (view is EditText) view.hint = parseText(value)
            "maxLines" -> if (view is TextView) view.maxLines = (value as? Number)?.toInt() ?: Int.MAX_VALUE
            "singleLine" -> if (view is TextView && (value == true || value == "true")) view.maxLines = 1
            "gravity" -> if (view is TextView) view.gravity = parseGravity(value)
            "textStyle" -> if (view is TextView) parseTextStyle(view, value)

            // ImageView attributes
            "src" -> if (view is ImageView) parseImageSrc(view, value)
            "scaleType" -> if (view is ImageView) view.scaleType = parseScaleType(value)

            // LinearLayout attributes
            "orientation" -> if (view is LinearLayout) view.orientation = parseOrientation(value)
            "gravity" -> if (view is ViewGroup) view.setGravity(parseGravity(value))
            "weightSum" -> {}  // TODO

            // ScrollView attributes
            "fillViewport" -> if (view is ScrollView) view.isFillViewport = value == true || value == "true"

            // ProgressBar attributes
            "progress" -> if (view is ProgressBar) view.progress = (value as? Number)?.toInt() ?: 0
            "max" -> if (view is ProgressBar) view.max = (value as? Number)?.toInt() ?: 100
            "indeterminate" -> if (view is ProgressBar) view.isIndeterminate = value == true || value == "true"
        }
    }

    private fun parseLayoutParams(element: BinaryXmlParser.XmlElement, parent: ViewGroup?): View.LayoutParams {
        val widthAttr = element.attributes.find { it.name == "layout_width" }
        val heightAttr = element.attributes.find { it.name == "layout_height" }

        val width = parseLayoutDimension(widthAttr?.value)
        val height = parseLayoutDimension(heightAttr?.value)

        val params = when (parent) {
            is LinearLayout -> {
                val lp = LinearLayout.LayoutParams(width, height)
                element.attributes.find { it.name == "layout_weight" }?.value?.let {
                    lp.weight = (it as? Number)?.toFloat() ?: 0f
                }
                element.attributes.find { it.name == "layout_gravity" }?.value?.let {
                    lp.gravity = parseGravity(it)
                }
                lp
            }
            is FrameLayout -> {
                val lp = FrameLayout.LayoutParams(width, height)
                element.attributes.find { it.name == "layout_gravity" }?.value?.let {
                    lp.gravity = parseGravity(it)
                }
                lp
            }
            else -> View.LayoutParams(width, height)
        }

        // Parse margins
        element.attributes.find { it.name == "layout_margin" }?.value?.let {
            val margin = parseDimension(it)
            params.setMargins(margin, margin, margin, margin)
        }
        element.attributes.find { it.name == "layout_marginLeft" || it.name == "layout_marginStart" }?.value?.let {
            params.leftMargin = parseDimension(it)
        }
        element.attributes.find { it.name == "layout_marginTop" }?.value?.let {
            params.topMargin = parseDimension(it)
        }
        element.attributes.find { it.name == "layout_marginRight" || it.name == "layout_marginEnd" }?.value?.let {
            params.rightMargin = parseDimension(it)
        }
        element.attributes.find { it.name == "layout_marginBottom" }?.value?.let {
            params.bottomMargin = parseDimension(it)
        }

        return params
    }

    // ========== Parsing helpers ==========

    private fun parseId(value: Any?): Int {
        return when (value) {
            is Number -> value.toInt()
            is String -> {
                when {
                    value.startsWith("@+id/") -> value.substringAfter("/").hashCode()
                    value.startsWith("@id/") -> value.substringAfter("/").hashCode()
                    value.startsWith("@") -> value.hashCode()
                    else -> value.toIntOrNull() ?: View.NO_ID
                }
            }
            else -> View.NO_ID
        }
    }

    private fun parseVisibility(value: Any?): Int {
        return when (value) {
            0, "visible" -> View.VISIBLE
            4, "invisible" -> View.INVISIBLE
            8, "gone" -> View.GONE
            else -> View.VISIBLE
        }
    }

    private fun parseText(value: Any?): String {
        return when (value) {
            is String -> {
                if (value.startsWith("@string/")) {
                    // Try to resolve from resource table
                    value.substringAfter("/")
                } else {
                    value
                }
            }
            else -> value?.toString() ?: ""
        }
    }

    private fun parseDimension(value: Any?): Int {
        return parseDimensionFloat(value).toInt()
    }

    private fun parseDimensionFloat(value: Any?): Float {
        return when (value) {
            is Number -> value.toFloat()
            is String -> {
                when {
                    value.endsWith("dp") -> value.removeSuffix("dp").toFloatOrNull() ?: 0f
                    value.endsWith("sp") -> value.removeSuffix("sp").toFloatOrNull() ?: 0f
                    value.endsWith("px") -> value.removeSuffix("px").toFloatOrNull() ?: 0f
                    value.endsWith("pt") -> (value.removeSuffix("pt").toFloatOrNull() ?: 0f) * 1.33f
                    else -> value.toFloatOrNull() ?: 0f
                }
            }
            else -> 0f
        }
    }

    private fun parseLayoutDimension(value: Any?): Int {
        return when (value) {
            -1, "match_parent", "fill_parent" -> View.LayoutParams.MATCH_PARENT
            -2, "wrap_content" -> View.LayoutParams.WRAP_CONTENT
            is Number -> value.toInt()
            is String -> parseDimension(value)
            else -> View.LayoutParams.WRAP_CONTENT
        }
    }

    private fun parseColor(value: Any?): Int {
        return when (value) {
            is Number -> value.toInt()
            is String -> {
                when {
                    value.startsWith("#") -> {
                        val hex = value.substring(1)
                        when (hex.length) {
                            3 -> ("FF" + hex[0] + hex[0] + hex[1] + hex[1] + hex[2] + hex[2]).toLong(16).toInt()
                            4 -> (hex[0].toString() + hex[0] + hex[1] + hex[1] + hex[2] + hex[2] + hex[3] + hex[3]).toLong(16).toInt()
                            6 -> ("FF$hex").toLong(16).toInt()
                            8 -> hex.toLong(16).toInt()
                            else -> 0xFF000000.toInt()
                        }
                    }
                    value.startsWith("@color/") -> 0xFF000000.toInt()  // Default black
                    else -> 0xFF000000.toInt()
                }
            }
            else -> 0xFF000000.toInt()
        }
    }

    private fun parseBackground(view: View, value: Any?) {
        when (value) {
            is Number -> view.backgroundColor = value.toInt()
            is String -> {
                when {
                    value.startsWith("#") -> view.backgroundColor = parseColor(value)
                    value.startsWith("@color/") -> view.backgroundColor = parseColor(value)
                    value.startsWith("@drawable/") -> {}  // TODO: drawable support
                }
            }
        }
    }

    private fun parsePadding(view: View, value: Any?) {
        val padding = parseDimension(value)
        view.setPadding(padding, padding, padding, padding)
    }

    private fun parseGravity(value: Any?): Int {
        // Simplified gravity parsing
        return when (value) {
            is Number -> value.toInt()
            is String -> {
                var gravity = 0
                val parts = value.split("|")
                for (part in parts) {
                    gravity = gravity or when (part.trim()) {
                        "center" -> 0x11  // CENTER
                        "center_horizontal" -> 0x01
                        "center_vertical" -> 0x10
                        "top" -> 0x30
                        "bottom" -> 0x50
                        "left", "start" -> 0x03
                        "right", "end" -> 0x05
                        "fill" -> 0x77
                        "fill_horizontal" -> 0x07
                        "fill_vertical" -> 0x70
                        else -> 0
                    }
                }
                gravity
            }
            else -> 0
        }
    }

    private fun parseOrientation(value: Any?): Int {
        return when (value) {
            0, "horizontal" -> LinearLayout.HORIZONTAL
            1, "vertical" -> LinearLayout.VERTICAL
            else -> LinearLayout.VERTICAL
        }
    }

    private fun parseScaleType(value: Any?): ImageView.ScaleType {
        return when (value) {
            "center" -> ImageView.ScaleType.CENTER
            "centerCrop" -> ImageView.ScaleType.CENTER_CROP
            "centerInside" -> ImageView.ScaleType.CENTER_INSIDE
            "fitCenter" -> ImageView.ScaleType.FIT_CENTER
            "fitStart" -> ImageView.ScaleType.FIT_START
            "fitEnd" -> ImageView.ScaleType.FIT_END
            "fitXY" -> ImageView.ScaleType.FIT_XY
            else -> ImageView.ScaleType.FIT_CENTER
        }
    }

    private fun parseTextStyle(view: TextView, value: Any?) {
        // bold, italic, normal
        when (value) {
            "bold", 1 -> view.setTypeface(null, 1)  // BOLD
            "italic", 2 -> view.setTypeface(null, 2)  // ITALIC
            "bold|italic", 3 -> view.setTypeface(null, 3)  // BOLD_ITALIC
            else -> view.setTypeface(null, 0)  // NORMAL
        }
    }

    private fun parseImageSrc(view: ImageView, value: Any?) {
        // TODO: Load actual drawable
        // For now, just note the resource reference
        when (value) {
            is String -> {
                if (value.startsWith("@drawable/") || value.startsWith("@mipmap/")) {
                    // Would load drawable here
                }
            }
        }
    }

    companion object {
        fun from(context: Context, resourceTable: ResourcesParser.ResourceTable?, resDir: File?): BinaryLayoutInflater {
            return BinaryLayoutInflater(context, resourceTable, resDir)
        }
    }
}
