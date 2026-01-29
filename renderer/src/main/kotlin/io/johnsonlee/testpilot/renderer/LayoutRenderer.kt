package io.johnsonlee.testpilot.renderer

import com.android.ide.common.rendering.api.ResourceNamespace
import com.android.ide.common.rendering.api.ResourceReference
import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.resources.ResourceResolver
import com.android.ide.common.resources.getConfiguredResources
import com.android.layoutlib.bridge.Bridge
import com.android.layoutlib.bridge.BridgeRenderSession
import com.android.layoutlib.bridge.impl.RenderSessionImpl
import com.android.resources.ResourceType
import com.android.resources.aar.FrameworkResourceRepository
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.File

/**
 * Renders Android layouts using layoutlib.
 *
 * Usage:
 * ```kotlin
 * LayoutRenderer(environment).use { renderer ->
 *     val result = renderer.render(layoutXml)
 *     ImageIO.write(result.image, "PNG", File("screenshot.png"))
 * }
 * ```
 */
class LayoutRenderer(
    private val environment: RenderEnvironment,
    private val deviceConfig: DeviceConfig = DeviceConfig.DEFAULT
) : Closeable {

    private val logger = LoggerFactory.getLogger(LayoutRenderer::class.java)
    private val bridge: Bridge = environment.initBridge()

    private val frameworkResources: FrameworkResourceRepository by lazy {
        FrameworkResourceRepository.create(
            environment.frameworkResDir.toPath(),
            emptySet(),
            null,
            false
        )
    }

    /**
     * Renders a layout XML string.
     *
     * @param layoutXml The layout XML content.
     * @param theme The theme to use (e.g., "Theme.Material.Light.NoActionBar").
     * @return The render result containing the image and view information.
     */
    fun render(
        layoutXml: String,
        theme: String = "Theme.Material.Light.NoActionBar"
    ): RenderResult {
        logger.debug("Rendering layout with theme: $theme")

        val folderConfiguration = deviceConfig.folderConfiguration
        val platformResources = mapOf(
            ResourceNamespace.ANDROID to frameworkResources
                .getConfiguredResources(folderConfiguration)
                .row(ResourceNamespace.ANDROID)
        )

        val resourceResolver = ResourceResolver.create(
            platformResources,
            ResourceReference(ResourceNamespace.ANDROID, ResourceType.STYLE, theme)
        )

        val layoutParser = XmlLayoutParser.fromString(layoutXml)

        val sessionParams = SessionParams(
            layoutParser,
            SessionParams.RenderingMode.NORMAL,
            null, // projectKey
            deviceConfig.hardwareConfig,
            resourceResolver,
            SimpleLayoutlibCallback(),
            0, // minSdkVersion
            31, // targetSdkVersion
            RenderLogger
        )

        sessionParams.fontScale = deviceConfig.fontScale
        sessionParams.setForceNoDecor()

        Bridge.prepareThread()

        var renderSession: RenderSessionImpl? = null
        try {
            renderSession = RenderSessionImpl(sessionParams)
            renderSession.init(sessionParams.timeout)

            val inflateResult = renderSession.inflate()
            if (!inflateResult.isSuccess) {
                throw RenderException("Failed to inflate layout: ${inflateResult.errorMessage}")
            }

            val renderResult = renderSession.render(true)
            if (!renderResult.isSuccess) {
                throw RenderException("Failed to render layout: ${renderResult.errorMessage}")
            }

            val bridgeSession = createBridgeRenderSession(renderSession, renderResult)
            val image = bridgeSession.image
                ?: throw RenderException("Render produced null image")

            return RenderResult(
                image = image,
                rootViews = bridgeSession.rootViews?.toList() ?: emptyList(),
                systemViews = bridgeSession.systemRootViews?.toList() ?: emptyList()
            )
        } finally {
            renderSession?.release()
            Bridge.cleanupThread()
        }
    }

    /**
     * Renders a layout XML file.
     */
    fun render(layoutFile: File, theme: String = "Theme.Material.Light.NoActionBar"): RenderResult {
        return render(layoutFile.readText(), theme)
    }

    private fun createBridgeRenderSession(
        renderSession: RenderSessionImpl,
        result: com.android.ide.common.rendering.api.Result
    ): BridgeRenderSession {
        val constructor = BridgeRenderSession::class.java.getDeclaredConstructor(
            RenderSessionImpl::class.java,
            com.android.ide.common.rendering.api.Result::class.java
        )
        constructor.isAccessible = true
        return constructor.newInstance(renderSession, result)
    }

    override fun close() {
        // Resources will be cleaned up when environment is disposed
    }
}

/**
 * Exception thrown when rendering fails.
 */
class RenderException(message: String, cause: Throwable? = null) : Exception(message, cause)
