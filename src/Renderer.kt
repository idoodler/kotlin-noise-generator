import java.awt.Color
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.imageio.ImageIO
import kotlin.random.Random

/**
 * Reusable renderer that produces noisy JPEG frames with a timestamp overlay.
 */
class NoiseRenderer(width: Int, height: Int) {
    private val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    private val pixels = (image.raster.dataBuffer as DataBufferInt).data
    private val rng = Random.Default
    private val output = ByteArrayOutputStream(width * height / 2)
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneOffset.UTC)
    private val infoFontSize = 14f
    private val timeFontSize = 32f

    /**
     * Renders a new JPEG frame into a reused buffer.
     */
    fun renderJpeg(overlay: Map<String, String> = emptyMap()): ByteArray {
        fillNoise()
        drawOverlay(overlay)
        output.reset()
        ImageIO.write(image, "jpg", output)
        return output.toByteArray()
    }

    private fun fillNoise() {
        for (i in pixels.indices) {
            pixels[i] = rng.nextInt(0x1000000)
        }
    }

    private fun drawOverlay(overlay: Map<String, String>) {
        val g = image.createGraphics()
        try {
            val timestamp = timeFormatter.format(Instant.now())
            g.font = g.font.deriveFont(infoFontSize)
            val infoMetrics = g.fontMetrics
            val lineHeight = infoMetrics.height
            val sorted = overlay.entries.sortedBy { it.key }
            var y = lineHeight
            val infoLines = sorted.map { (key, value) -> "$key: $value" }
            if (infoLines.isNotEmpty()) {
                val maxWidth = infoLines.maxOf { infoMetrics.stringWidth(it) }
                val padding = 8
                val boxHeight = lineHeight * infoLines.size + padding
                g.color = Color(0, 0, 0, 170)
                g.fillRoundRect(6, 6, maxWidth + padding * 2, boxHeight, 8, 8)
                y = lineHeight + padding
                infoLines.forEach { line ->
                    drawShadowedText(g, line, 6 + padding, y)
                    y += lineHeight
                }
            }

            g.font = g.font.deriveFont(timeFontSize)
            val timeMetrics = g.fontMetrics
            val timeWidth = timeMetrics.stringWidth(timestamp)
            val timeX = (image.width - timeWidth) / 2
            val timeY = (image.height + timeMetrics.ascent) / 2
            val timePaddingX = 14
            val timePaddingY = 10
            g.color = Color(0, 0, 0, 170)
            g.fillRoundRect(
                timeX - timePaddingX,
                timeY - timeMetrics.ascent - timePaddingY,
                timeWidth + timePaddingX * 2,
                timeMetrics.height + timePaddingY * 2,
                12,
                12
            )
            drawShadowedText(g, timestamp, timeX, timeY)
        } finally {
            g.dispose()
        }
    }

    private fun drawShadowedText(g: java.awt.Graphics2D, text: String, x: Int, y: Int) {
        g.color = Color(0, 0, 0, 160)
        g.drawString(text, x + 1, y + 1)
        g.color = Color.WHITE
        g.drawString(text, x, y)
    }
}
