package io.johnsonlee.testpilot.renderer

import com.android.ide.common.rendering.api.ActionBarCallback
import com.android.ide.common.rendering.api.AdapterBinding
import com.android.ide.common.rendering.api.ILayoutPullParser
import com.android.ide.common.rendering.api.LayoutlibCallback
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.ResourceValue
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser

/**
 * Simple layoutlib callback implementation for basic rendering.
 */
class SimpleLayoutlibCallback : LayoutlibCallback() {

    private val actionBarCallback = ActionBarCallback()

    override fun loadView(
        name: String,
        constructorSignature: Array<out Class<*>>,
        constructorArgs: Array<out Any>
    ): Any? {
        return try {
            val clazz = Class.forName(name)
            val constructor = clazz.getConstructor(*constructorSignature)
            constructor.isAccessible = true
            constructor.newInstance(*constructorArgs)
        } catch (e: Exception) {
            null
        }
    }

    override fun resolveResourceId(id: Int): ResourceReference? = null

    override fun getOrGenerateResourceId(resource: ResourceReference): Int = 0

    override fun getParser(layoutResource: ResourceValue): ILayoutPullParser? = null

    override fun getAdapterItemValue(
        adapterView: ResourceReference,
        adapterCookie: Any,
        itemRef: ResourceReference,
        fullPosition: Int,
        positionPerType: Int,
        fullParentPosition: Int,
        parentPositionPerType: Int,
        viewRef: ResourceReference,
        viewAttribute: ViewAttribute,
        defaultValue: Any
    ): Any = defaultValue

    override fun getAdapterBinding(
        viewObject: Any?,
        attributes: MutableMap<String, String>?
    ): AdapterBinding? = null

    override fun getActionBarCallback(): ActionBarCallback = actionBarCallback

    override fun createXmlParserForPsiFile(fileName: String): XmlPullParser = KXmlParser()

    override fun createXmlParserForFile(fileName: String): XmlPullParser = KXmlParser()

    override fun createXmlParser(): XmlPullParser = KXmlParser()
}
