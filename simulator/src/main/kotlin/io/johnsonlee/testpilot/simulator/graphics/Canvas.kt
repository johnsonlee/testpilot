package io.johnsonlee.testpilot.simulator.graphics

/**
 * The Canvas class holds the "draw" calls.
 * To draw something, you need 4 basic components: a Bitmap to hold the pixels,
 * a Canvas to host the draw calls, a drawing primitive (e.g. Rect, Path, etc.),
 * and a paint (to describe the colors and styles for the drawing).
 */
class Canvas(val width: Int, val height: Int) {
    private val commands = mutableListOf<DrawCommand>()

    fun drawColor(color: Int) {
        commands.add(DrawCommand.Color(color))
    }

    fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        commands.add(DrawCommand.Rect(left, top, right, bottom, paint.copy()))
    }

    fun drawText(text: String, x: Float, y: Float, paint: Paint) {
        commands.add(DrawCommand.Text(text, x, y, paint.copy()))
    }

    fun drawRoundRect(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float, paint: Paint) {
        commands.add(DrawCommand.RoundRect(left, top, right, bottom, rx, ry, paint.copy()))
    }

    fun save(): Int {
        commands.add(DrawCommand.Save)
        return commands.size
    }

    fun restore() {
        commands.add(DrawCommand.Restore)
    }

    fun translate(dx: Float, dy: Float) {
        commands.add(DrawCommand.Translate(dx, dy))
    }

    fun getCommands(): List<DrawCommand> = commands.toList()
}

sealed class DrawCommand {
    data class Color(val color: Int) : DrawCommand()
    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float, val paint: Paint) : DrawCommand()
    data class RoundRect(val left: Float, val top: Float, val right: Float, val bottom: Float, val rx: Float, val ry: Float, val paint: Paint) : DrawCommand()
    data class Text(val text: String, val x: Float, val y: Float, val paint: Paint) : DrawCommand()
    data class Translate(val dx: Float, val dy: Float) : DrawCommand()
    data object Save : DrawCommand()
    data object Restore : DrawCommand()
}

/**
 * The Paint class holds the style and color information about how to draw geometries, text and bitmaps.
 */
data class Paint(
    var color: Int = Color.BLACK,
    var textSize: Float = 14f,
    var style: Style = Style.FILL,
    var textAlign: Align = Align.LEFT
) {
    enum class Style { FILL, STROKE, FILL_AND_STROKE }
    enum class Align { LEFT, CENTER, RIGHT }

    fun copy(): Paint = Paint(color, textSize, style, textAlign)
}

/**
 * Color utilities.
 */
object Color {
    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()
    const val RED = 0xFFFF0000.toInt()
    const val GREEN = 0xFF00FF00.toInt()
    const val BLUE = 0xFF0000FF.toInt()
    const val GRAY = 0xFF888888.toInt()
    const val LTGRAY = 0xFFCCCCCC.toInt()
    const val DKGRAY = 0xFF444444.toInt()
    const val TRANSPARENT = 0x00000000

    fun rgb(red: Int, green: Int, blue: Int): Int {
        return 0xFF000000.toInt() or (red shl 16) or (green shl 8) or blue
    }

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    fun alpha(color: Int): Int = (color shr 24) and 0xFF
    fun red(color: Int): Int = (color shr 16) and 0xFF
    fun green(color: Int): Int = (color shr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF
}
