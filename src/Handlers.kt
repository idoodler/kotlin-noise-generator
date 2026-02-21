import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

private const val LEGACY_BOUNDARY = "--noiseGeneratorBoundary"
private const val LEGACY_PADDING_BYTES = 512
private const val MIN_MJPG_INTERVAL_MS = 100
private const val MAX_MJPG_INTERVAL_MS = 10_000
private val REQUEST_COUNTER = AtomicLong(0)

/**
 * Serves the rendered README page.
 */
class RootHandler : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendMethodNotAllowed(exchange)
            return
        }
        val params = parseQuery(exchange.requestURI.rawQuery)
        applyCorsHeaders(exchange, params)
        if (!authenticate(exchange, params)) return

        val type = params["type"]?.lowercase()
        if (type == null) {
            val html = ReadmePage.render()
            sendHtml(exchange, 200, html)
            return
        }
        val cnt = REQUEST_COUNTER.getAndIncrement().toString()
        val overlay = overlayParams(params, mapOf("cnt" to cnt, "type" to type))
        if (type == "mjpg" || type == ".mjpg") {
            streamMjpg(exchange, params, overlay, useLegacy = true)
        } else {
            val width = clampInt(params["width"], DEFAULT_WIDTH, MIN_WIDTH, MAX_JPG_WIDTH)
            val height = clampInt(params["height"], DEFAULT_HEIGHT, MIN_HEIGHT, MAX_JPG_HEIGHT)
            val overlayWithDims = overlay + mapOf("width" to width.toString(), "height" to height.toString())
            sendJpg(exchange, width, height, overlayWithDims)
        }
    }
}

/**
 * Generates a single noise JPEG.
 */
class NoiseJpgHandler : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendMethodNotAllowed(exchange)
            return
        }
        val params = parseQuery(exchange.requestURI.rawQuery)
        applyCorsHeaders(exchange, params)
        if (!authenticate(exchange, params)) return
        val width = clampInt(params["width"], DEFAULT_WIDTH, MIN_WIDTH, MAX_JPG_WIDTH)
        val height = clampInt(params["height"], DEFAULT_HEIGHT, MIN_HEIGHT, MAX_JPG_HEIGHT)
        val cnt = REQUEST_COUNTER.getAndIncrement().toString()
        val overlay = overlayParams(params, mapOf("cnt" to cnt, "type" to "jpg", "width" to width.toString(), "height" to height.toString()))
        sendJpg(exchange, width, height, overlay)
    }
}

/**
 * Streams MJPG frames with configurable framing variants.
 */
class NoiseMjpgHandler : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendMethodNotAllowed(exchange)
            return
        }
        val params = parseQuery(exchange.requestURI.rawQuery)
        applyCorsHeaders(exchange, params)
        if (!authenticate(exchange, params)) return
        val width = clampInt(params["width"], DEFAULT_WIDTH, MIN_WIDTH, MAX_STREAM_WIDTH)
        val height = clampInt(params["height"], DEFAULT_HEIGHT, MIN_HEIGHT, MAX_STREAM_HEIGHT)
        val fps = clampInt(params["fps"], DEFAULT_FPS, MIN_FPS, MAX_FPS)
        val boundary = "frame"
        val variant = MjpegVariant.from(params["variant"])
        val padBytes = clampInt(params["pad"], 16, 0, 256)
        val chunkSize = clampInt(params["chunk"], 1024, 128, 8192)
        val preambleSize = clampInt(params["pre"], 16, 0, 256)
        val postambleSize = clampInt(params["post"], 16, 0, 256)
        val wireBoundary = if (variant == MjpegVariant.WRONG_BOUNDARY) "wrongframe" else boundary
        val renderer = NoiseRenderer(width, height)
        val streamer = MjpegStreamer(boundary, wireBoundary, variant, padBytes, chunkSize, preambleSize, postambleSize)

        val cnt = REQUEST_COUNTER.getAndIncrement().toString()
        val overlay = overlayParams(
            params,
            mapOf(
                "cnt" to cnt,
                "type" to "mjpg",
                "width" to width.toString(),
                "height" to height.toString(),
                "fps" to fps.toString()
            )
        )
        streamMjpg(exchange, params, overlay, fps, renderer, streamer)
    }
}

/**
 * Health check handler.
 */
class HealthHandler : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendMethodNotAllowed(exchange)
            return
        }
        val params = parseQuery(exchange.requestURI.rawQuery)
        applyCorsHeaders(exchange, params)
        if (!authenticate(exchange, params)) return
        sendText(exchange, 200, "ok")
    }
}

/**
 * Legacy MJPG stream endpoint that mimics the Express example.
 */
class StreamCgiHandler : HttpHandler {
    override fun handle(exchange: HttpExchange) {
        if (!exchange.requestMethod.equals("GET", ignoreCase = true)) {
            sendMethodNotAllowed(exchange)
            return
        }
        val params = parseQuery(exchange.requestURI.rawQuery)
        applyCorsHeaders(exchange, params)
        if (!authenticate(exchange, params)) return
        val cnt = REQUEST_COUNTER.getAndIncrement().toString()
        val overlay = overlayParams(params, mapOf("cnt" to cnt, "type" to "mjpg", "isCGI" to "true"))
        streamMjpg(exchange, params, overlay, useLegacy = true)
    }
}

private fun sendJpg(exchange: HttpExchange, width: Int, height: Int, overlay: Map<String, String>) {
    val renderer = NoiseRenderer(width, height)
    val jpgBytes = renderer.renderJpeg(overlay)
    exchange.responseHeaders.add("Content-Type", "image/jpeg")
    setNoCacheHeaders(exchange)
    exchange.sendResponseHeaders(200, jpgBytes.size.toLong())
    exchange.responseBody.use { it.write(jpgBytes) }
}

private fun streamMjpg(
    exchange: HttpExchange,
    params: Map<String, String>,
    overlay: Map<String, String>,
    fps: Int = DEFAULT_FPS,
    renderer: NoiseRenderer? = null,
    streamer: MjpegStreamer? = null,
    useLegacy: Boolean = false
) {
    val legacyMods = params["mjpgMod"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val legacyHeaderMods = params["mjpgHeaderMod"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val legacyInterval = params["mjpgInterval"]?.toIntOrNull()
    val legacyZeroLength = legacyHeaderMods.any { it.equals("zeroLength", ignoreCase = true) }
    val legacyOffset = legacyMods.any { it.equals("offset", ignoreCase = true) }
    val legacyPadd = legacyMods.any { it.equals("padd", ignoreCase = true) }

    val mappedVariant = when {
        legacyOffset -> MjpegVariant.OFFSET
        legacyPadd -> MjpegVariant.POSTAMBLE
        else -> null
    }
    val mappedPost = if (legacyPadd) LEGACY_PADDING_BYTES else null
    val mappedFps = legacyInterval?.let { max(1, 1000 / max(it, 1)) }

    val useLegacyStream = useLegacy || legacyZeroLength
    val resolvedFps = mappedFps ?: fps

    setNoCacheHeaders(exchange)
    val contentBoundary = if (useLegacyStream) LEGACY_BOUNDARY else "frame"
    exchange.responseHeaders.add("Content-Type", "multipart/x-mixed-replace; boundary=$contentBoundary")
    exchange.sendResponseHeaders(200, 0)

    val out = exchange.responseBody
    try {
        if (useLegacyStream) {
            streamLegacy(out, params, overlay)
        } else {
            val activeRenderer = renderer ?: return
            val activeStreamer = if (mappedVariant != null || mappedPost != null || streamer == null) {
                MjpegStreamer(
                    boundary = "frame",
                    wireBoundary = "frame",
                    variant = mappedVariant ?: MjpegVariant.STANDARD,
                    padBytes = 16,
                    chunkSize = 1024,
                    preambleBytes = 16,
                    postambleBytes = mappedPost ?: 16
                )
            } else {
                streamer
            }
            val frameDelayMs = max(1, 1000 / resolvedFps)
            while (true) {
                val jpgBytes = activeRenderer.renderJpeg(overlay)
                activeStreamer.writeFrame(out, jpgBytes)
                out.flush()
                Thread.sleep(frameDelayMs.toLong())
            }
        }
    } catch (_: IOException) {
        // Client disconnected.
    } catch (_: InterruptedException) {
        Thread.currentThread().interrupt()
    } finally {
        out.close()
    }
}

private fun streamLegacy(out: java.io.OutputStream, params: Map<String, String>, overlay: Map<String, String>) {
    val width = clampInt(params["width"], DEFAULT_WIDTH, MIN_WIDTH, MAX_STREAM_WIDTH)
    val height = clampInt(params["height"], DEFAULT_HEIGHT, MIN_HEIGHT, MAX_STREAM_HEIGHT)
    val intervalMs = clampInt(params["mjpgInterval"], 100, MIN_MJPG_INTERVAL_MS, MAX_MJPG_INTERVAL_MS)
    val mjpgMod = params["mjpgMod"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val mjpgHeaderMod = params["mjpgHeaderMod"]?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
    val headerMode = when {
        mjpgHeaderMod.any { it.equals("noLength", ignoreCase = true) } -> LegacyHeaderMode.LENGTH
        mjpgHeaderMod.any { it.equals("zeroLength", ignoreCase = true) } -> LegacyHeaderMode.ZERO_LENGTH
        else -> LegacyHeaderMode.NONE
    }
    val addPadding = mjpgMod.any { it.equals("padd", ignoreCase = true) }
    val useOffset = mjpgMod.any { it.equals("offset", ignoreCase = true) }
    val overlayWithDims = overlay + mapOf("width" to width.toString(), "height" to height.toString(), "mjpgInterval" to intervalMs.toString())
    val renderer = NoiseRenderer(width, height)
    val streamer = LegacyMjpegStreamer(LEGACY_BOUNDARY)
    var carry = ByteArray(0)

    if (useOffset) {
        val initial = renderer.renderJpeg(overlayWithDims)
        carry = initial.copyOfRange(initial.size / 2, initial.size)
    }

    while (true) {
        val frame = renderer.renderJpeg(overlayWithDims)
        val combined = ByteArray(carry.size + frame.size)
        if (carry.isNotEmpty()) {
            System.arraycopy(carry, 0, combined, 0, carry.size)
        }
        System.arraycopy(frame, 0, combined, carry.size, frame.size)
        var dataToSend = combined.copyOfRange(0, frame.size)
        carry = combined.copyOfRange(frame.size, combined.size)

        if (addPadding) {
            val padded = ByteArrayOutputStream()
            padded.write(dataToSend)
            padded.write(ByteArray(LEGACY_PADDING_BYTES))
            dataToSend = padded.toByteArray()
        }

        streamer.writeFrame(out, dataToSend, headerMode)
        out.flush()
        Thread.sleep(intervalMs.toLong())
    }
}

private fun overlayParams(params: Map<String, String>, extras: Map<String, String>): Map<String, String> {
    val merged = LinkedHashMap<String, String>()
    val keys = listOf("cnt", "type", "isCGI")
    keys.forEach { key ->
        val extra = extras[key]
        if (extra != null) merged[key] = extra
    }
    val ordered = params.toSortedMap()
    ordered.forEach { (key, value) ->
        merged[key] = value
    }
    extras.forEach { (key, value) ->
        if (!merged.containsKey(key)) merged[key] = value
    }
    return merged
}
