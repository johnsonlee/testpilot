package io.johnsonlee.testpilot.renderer

import com.android.ide.common.rendering.api.HardwareConfig
import com.android.ide.common.resources.configuration.DensityQualifier
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.ide.common.resources.configuration.KeyboardStateQualifier
import com.android.ide.common.resources.configuration.LayoutDirectionQualifier
import com.android.ide.common.resources.configuration.LocaleQualifier
import com.android.ide.common.resources.configuration.NavigationMethodQualifier
import com.android.ide.common.resources.configuration.NightModeQualifier
import com.android.ide.common.resources.configuration.ScreenDimensionQualifier
import com.android.ide.common.resources.configuration.ScreenOrientationQualifier
import com.android.ide.common.resources.configuration.ScreenRatioQualifier
import com.android.ide.common.resources.configuration.ScreenRoundQualifier
import com.android.ide.common.resources.configuration.ScreenSizeQualifier
import com.android.ide.common.resources.configuration.TextInputMethodQualifier
import com.android.ide.common.resources.configuration.TouchScreenQualifier
import com.android.ide.common.resources.configuration.UiModeQualifier
import com.android.resources.Density
import com.android.resources.Keyboard
import com.android.resources.KeyboardState
import com.android.resources.LayoutDirection
import com.android.resources.Navigation
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.ScreenRatio
import com.android.resources.ScreenRound
import com.android.resources.ScreenSize
import com.android.resources.TouchScreen
import com.android.resources.UiMode

/**
 * Device configuration for rendering.
 */
data class DeviceConfig(
    val name: String,
    val screenWidth: Int,
    val screenHeight: Int,
    val density: Density,
    val xdpi: Int = density.dpiValue,
    val ydpi: Int = density.dpiValue,
    val orientation: ScreenOrientation = ScreenOrientation.PORTRAIT,
    val uiMode: UiMode = UiMode.NORMAL,
    val nightMode: NightMode = NightMode.NOTNIGHT,
    val fontScale: Float = 1.0f,
    val layoutDirection: LayoutDirection = LayoutDirection.LTR,
    val locale: String? = null,
    val ratio: ScreenRatio = ScreenRatio.NOTLONG,
    val size: ScreenSize = ScreenSize.NORMAL,
    val keyboard: Keyboard = Keyboard.NOKEY,
    val touchScreen: TouchScreen = TouchScreen.FINGER,
    val keyboardState: KeyboardState = KeyboardState.SOFT,
    val softButtons: Boolean = true,
    val navigation: Navigation = Navigation.NONAV,
    val screenRound: ScreenRound = ScreenRound.NOTROUND
) {
    val hardwareConfig: HardwareConfig
        get() = HardwareConfig(
            screenWidth,
            screenHeight,
            density,
            xdpi.toFloat(),
            ydpi.toFloat(),
            size,
            orientation,
            screenRound,
            softButtons
        )

    val folderConfiguration: FolderConfiguration
        get() = FolderConfiguration.createDefault().apply {
            densityQualifier = DensityQualifier(density)
            navigationMethodQualifier = NavigationMethodQualifier(navigation)
            screenDimensionQualifier = when {
                screenWidth > screenHeight -> ScreenDimensionQualifier(screenWidth, screenHeight)
                else -> ScreenDimensionQualifier(screenHeight, screenWidth)
            }
            screenRatioQualifier = ScreenRatioQualifier(ratio)
            screenSizeQualifier = ScreenSizeQualifier(size)
            textInputMethodQualifier = TextInputMethodQualifier(keyboard)
            touchTypeQualifier = TouchScreenQualifier(touchScreen)
            keyboardStateQualifier = KeyboardStateQualifier(keyboardState)
            screenOrientationQualifier = ScreenOrientationQualifier(orientation)
            screenRoundQualifier = ScreenRoundQualifier(screenRound)
            updateScreenWidthAndHeight()
            uiModeQualifier = UiModeQualifier(uiMode)
            nightModeQualifier = NightModeQualifier(nightMode)
            layoutDirectionQualifier = LayoutDirectionQualifier(layoutDirection)
            localeQualifier = locale?.let(LocaleQualifier::getQualifier) ?: LocaleQualifier()
        }

    companion object {
        /**
         * Pixel 5 device configuration.
         */
        val PIXEL_5 = DeviceConfig(
            name = "Pixel 5",
            screenWidth = 1080,
            screenHeight = 2340,
            density = Density.create(440),
            xdpi = 442,
            ydpi = 444,
            ratio = ScreenRatio.LONG
        )

        /**
         * Pixel 4 device configuration.
         */
        val PIXEL_4 = DeviceConfig(
            name = "Pixel 4",
            screenWidth = 1080,
            screenHeight = 2280,
            density = Density.create(440),
            xdpi = 444,
            ydpi = 444,
            ratio = ScreenRatio.LONG
        )

        /**
         * Default device for testing (480x800, mdpi).
         */
        val DEFAULT = DeviceConfig(
            name = "Default",
            screenWidth = 480,
            screenHeight = 800,
            density = Density.MEDIUM
        )
    }
}
