package io.johnsonlee.testpilot.simulator.view

import io.johnsonlee.testpilot.simulator.content.Context
import io.johnsonlee.testpilot.simulator.widget.Button
import io.johnsonlee.testpilot.simulator.widget.FrameLayout
import io.johnsonlee.testpilot.simulator.widget.LinearLayout
import io.johnsonlee.testpilot.simulator.widget.TextView
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.InputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Instantiates a layout XML file into its corresponding View objects.
 */
class LayoutInflater(private val context: Context) {

    /**
     * Inflate a new view hierarchy from the specified XML resource.
     */
    fun inflate(resource: Int, root: ViewGroup?): View {
        val layoutPath = context.resources.getLayout(resource)
        val inputStream = javaClass.classLoader?.getResourceAsStream(layoutPath)
            ?: throw IllegalArgumentException("Layout resource not found: $layoutPath")
        return inflate(inputStream, root)
    }

    /**
     * Inflate a new view hierarchy from the specified XML input stream.
     */
    fun inflate(inputStream: InputStream, root: ViewGroup?): View {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(inputStream)
        val rootElement = document.documentElement
        return createViewFromElement(rootElement, root)
    }

    /**
     * Inflate from XML string (useful for testing).
     */
    fun inflate(xml: String, root: ViewGroup?): View {
        return inflate(xml.byteInputStream(), root)
    }

    private fun createViewFromElement(element: Element, parent: ViewGroup?): View {
        val view = createView(element.tagName)

        // Parse common attributes
        parseAttributes(view, element)

        // Parse layout params
        val layoutParams = parseLayoutParams(element, parent)
        view.layoutParams = layoutParams

        // If this is a ViewGroup, parse children
        if (view is ViewGroup) {
            val childNodes = element.childNodes
            for (i in 0 until childNodes.length) {
                val childNode = childNodes.item(i)
                if (childNode.nodeType == Node.ELEMENT_NODE) {
                    val childView = createViewFromElement(childNode as Element, view)
                    view.addView(childView)
                }
            }
        }

        return view
    }

    private fun createView(tagName: String): View {
        val simpleName = tagName.substringAfterLast('.')
        return when (simpleName) {
            "FrameLayout" -> FrameLayout(context)
            "LinearLayout" -> LinearLayout(context)
            "TextView" -> TextView(context)
            "Button" -> Button(context)
            "View" -> View(context)
            else -> throw IllegalArgumentException("Unknown view type: $tagName")
        }
    }

    private fun parseAttributes(view: View, element: Element) {
        // android:id
        element.getAttribute("android:id")?.takeIf { it.isNotEmpty() }?.let { idStr ->
            view.id = parseId(idStr)
        }

        // android:visibility
        element.getAttribute("android:visibility")?.takeIf { it.isNotEmpty() }?.let { visibility ->
            view.visibility = when (visibility) {
                "visible" -> View.VISIBLE
                "invisible" -> View.INVISIBLE
                "gone" -> View.GONE
                else -> View.VISIBLE
            }
        }

        // TextView/Button specific attributes
        if (view is TextView) {
            element.getAttribute("android:text")?.takeIf { it.isNotEmpty() }?.let { text ->
                view.text = parseText(text)
            }
            element.getAttribute("android:textSize")?.takeIf { it.isNotEmpty() }?.let { size ->
                view.textSize = parseDimension(size)
            }
        }

        // LinearLayout specific attributes
        if (view is LinearLayout) {
            element.getAttribute("android:orientation")?.takeIf { it.isNotEmpty() }?.let { orientation ->
                view.orientation = when (orientation) {
                    "horizontal" -> LinearLayout.HORIZONTAL
                    "vertical" -> LinearLayout.VERTICAL
                    else -> LinearLayout.VERTICAL
                }
            }
        }
    }

    private fun parseLayoutParams(element: Element, parent: ViewGroup?): View.LayoutParams {
        val widthStr = element.getAttribute("android:layout_width") ?: "wrap_content"
        val heightStr = element.getAttribute("android:layout_height") ?: "wrap_content"

        val width = parseDimensionOrLayoutParam(widthStr)
        val height = parseDimensionOrLayoutParam(heightStr)

        return View.LayoutParams(width, height)
    }

    private fun parseId(idStr: String): Int {
        // Handle @+id/name or @id/name format
        return if (idStr.startsWith("@+id/") || idStr.startsWith("@id/")) {
            idStr.substringAfter("/").hashCode()
        } else {
            idStr.toIntOrNull() ?: View.NO_ID
        }
    }

    private fun parseText(text: String): String {
        // Handle @string/name format
        return if (text.startsWith("@string/")) {
            // For now, just return the key
            text.substringAfter("/")
        } else {
            text
        }
    }

    private fun parseDimension(value: String): Float {
        return when {
            value.endsWith("sp") -> value.removeSuffix("sp").toFloatOrNull() ?: 14f
            value.endsWith("dp") -> value.removeSuffix("dp").toFloatOrNull() ?: 0f
            value.endsWith("px") -> value.removeSuffix("px").toFloatOrNull() ?: 0f
            else -> value.toFloatOrNull() ?: 0f
        }
    }

    private fun parseDimensionOrLayoutParam(value: String): Int {
        return when (value) {
            "match_parent", "fill_parent" -> View.LayoutParams.MATCH_PARENT
            "wrap_content" -> View.LayoutParams.WRAP_CONTENT
            else -> parseDimension(value).toInt()
        }
    }
}
