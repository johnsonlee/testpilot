package io.johnsonlee.testpilot.simulator.graphics

import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/**
 * Rasterizes a [Canvas] command recording into a [BufferedImage].
 */
fun Canvas.toImage(): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d = image.createGraphics()
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

    val stateStack = ArrayDeque<AffineTransform>()

    for (cmd in getCommands()) {
        when (cmd) {
            is DrawCommand.Color -> {
                g2d.color = java.awt.Color(cmd.color, true)
                g2d.fillRect(0, 0, width, height)
            }
            is DrawCommand.Rect -> {
                g2d.color = java.awt.Color(cmd.paint.color, true)
                val x = cmd.left.toInt()
                val y = cmd.top.toInt()
                val w = (cmd.right - cmd.left).toInt()
                val h = (cmd.bottom - cmd.top).toInt()
                when (cmd.paint.style) {
                    Paint.Style.FILL -> g2d.fillRect(x, y, w, h)
                    Paint.Style.STROKE -> g2d.drawRect(x, y, w, h)
                    Paint.Style.FILL_AND_STROKE -> {
                        g2d.fillRect(x, y, w, h)
                        g2d.drawRect(x, y, w, h)
                    }
                }
            }
            is DrawCommand.RoundRect -> {
                g2d.color = java.awt.Color(cmd.paint.color, true)
                val shape = RoundRectangle2D.Float(
                    cmd.left, cmd.top,
                    cmd.right - cmd.left, cmd.bottom - cmd.top,
                    cmd.rx * 2, cmd.ry * 2
                )
                when (cmd.paint.style) {
                    Paint.Style.FILL -> g2d.fill(shape)
                    Paint.Style.STROKE -> g2d.draw(shape)
                    Paint.Style.FILL_AND_STROKE -> {
                        g2d.fill(shape)
                        g2d.draw(shape)
                    }
                }
            }
            is DrawCommand.Text -> {
                g2d.color = java.awt.Color(cmd.paint.color, true)
                g2d.font = Font(Font.SANS_SERIF, Font.PLAIN, cmd.paint.textSize.toInt())
                g2d.drawString(cmd.text, cmd.x, cmd.y)
            }
            is DrawCommand.Save -> {
                stateStack.addLast(g2d.transform.clone() as AffineTransform)
            }
            is DrawCommand.Restore -> {
                if (stateStack.isNotEmpty()) {
                    g2d.transform = stateStack.removeLast()
                }
            }
            is DrawCommand.Translate -> {
                g2d.translate(cmd.dx.toDouble(), cmd.dy.toDouble())
            }
        }
    }

    g2d.dispose()
    return image
}
