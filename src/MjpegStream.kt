import java.io.OutputStream

/**
 * Known MJPG framing variants for stress-testing parsers.
 */
enum class MjpegVariant {
    STANDARD,
    OFFSET,
    SPLIT,
    NOCL,
    NO_CRLF,
    WRONG_BOUNDARY,
    PREAMBLE,
    POSTAMBLE;

    companion object {
        fun from(raw: String?): MjpegVariant {
            return when (raw?.lowercase()) {
                "offset" -> OFFSET
                "split" -> SPLIT
                "nocl" -> NOCL
                "no-crlf" -> NO_CRLF
                "wrong-boundary" -> WRONG_BOUNDARY
                "preamble" -> PREAMBLE
                "postamble" -> POSTAMBLE
                else -> STANDARD
            }
        }
    }
}

/**
 * Writes MJPG frames using the selected variant.
 */
class MjpegStreamer(
    private val boundary: String,
    private val wireBoundary: String,
    private val variant: MjpegVariant,
    private val padBytes: Int,
    private val chunkSize: Int,
    private val preambleBytes: Int,
    private val postambleBytes: Int
) {
    private val boundaryBytes = "--$wireBoundary\r\n".toByteArray()
    private val headerPrefix = "Content-Type: image/jpeg\r\nContent-Length: ".toByteArray()
    private val headerSuffix = "\r\n\r\n".toByteArray()
    private val frameSuffix = "\r\n".toByteArray()
    private val noCrlfHeaderPrefix = "Content-Type: image/jpeg\nContent-Length: ".toByteArray()
    private val noCrlfHeaderSuffix = "\n\n".toByteArray()
    private val noCrlfFrameSuffix = "\n".toByteArray()
    private val paddingBytes = ByteArray(padBytes) { '.'.code.toByte() }
    private val preamble = ByteArray(preambleBytes) { 'p'.code.toByte() }
    private val postamble = ByteArray(postambleBytes) { 't'.code.toByte() }
    private var preambleWritten = false

    /**
     * Writes a single frame using the configured variant.
     */
    fun writeFrame(out: OutputStream, jpgBytes: ByteArray) {
        if (!preambleWritten && variant == MjpegVariant.PREAMBLE && preamble.isNotEmpty()) {
            out.write(preamble)
            preambleWritten = true
        }
        when (variant) {
            MjpegVariant.STANDARD -> writeStandard(out, jpgBytes)
            MjpegVariant.OFFSET -> writeOffset(out, jpgBytes)
            MjpegVariant.SPLIT -> writeSplit(out, jpgBytes)
            MjpegVariant.NOCL -> writeNoContentLength(out, jpgBytes)
            MjpegVariant.NO_CRLF -> writeNoCrlf(out, jpgBytes)
            MjpegVariant.WRONG_BOUNDARY -> writeStandard(out, jpgBytes)
            MjpegVariant.PREAMBLE -> writeStandard(out, jpgBytes)
            MjpegVariant.POSTAMBLE -> writePostamble(out, jpgBytes)
        }
    }

    private fun writeStandard(out: OutputStream, jpgBytes: ByteArray) {
        out.write(boundaryBytes)
        out.write(headerPrefix)
        out.write(jpgBytes.size.toString().toByteArray())
        out.write(headerSuffix)
        out.write(jpgBytes)
        out.write(frameSuffix)
    }

    private fun writeOffset(out: OutputStream, jpgBytes: ByteArray) {
        if (paddingBytes.isNotEmpty()) {
            out.write(paddingBytes)
        }
        writeStandard(out, jpgBytes)
    }

    private fun writeSplit(out: OutputStream, jpgBytes: ByteArray) {
        val boundaryHalf = boundaryBytes.size / 2
        out.write(boundaryBytes, 0, boundaryHalf)
        out.write(boundaryBytes, boundaryHalf, boundaryBytes.size - boundaryHalf)
        out.write(headerPrefix)
        val lengthBytes = jpgBytes.size.toString().toByteArray()
        out.write(lengthBytes)
        out.write(headerSuffix)
        writeChunks(out, jpgBytes, chunkSize)
        out.write(frameSuffix)
    }

    private fun writeNoContentLength(out: OutputStream, jpgBytes: ByteArray) {
        out.write(boundaryBytes)
        out.write("Content-Type: image/jpeg\r\n\r\n".toByteArray())
        out.write(jpgBytes)
        out.write(frameSuffix)
    }

    private fun writeNoCrlf(out: OutputStream, jpgBytes: ByteArray) {
        val boundaryNoCrlf = "--$wireBoundary\n".toByteArray()
        out.write(boundaryNoCrlf)
        out.write(noCrlfHeaderPrefix)
        out.write(jpgBytes.size.toString().toByteArray())
        out.write(noCrlfHeaderSuffix)
        out.write(jpgBytes)
        out.write(noCrlfFrameSuffix)
    }

    private fun writePostamble(out: OutputStream, jpgBytes: ByteArray) {
        writeStandard(out, jpgBytes)
        if (postamble.isNotEmpty()) {
            out.write(postamble)
        }
    }

    private fun writeChunks(out: OutputStream, data: ByteArray, chunk: Int) {
        var offset = 0
        while (offset < data.size) {
            val len = minOf(chunk, data.size - offset)
            out.write(data, offset, len)
            offset += len
        }
    }
}

enum class LegacyHeaderMode {
    NONE,
    LENGTH,
    ZERO_LENGTH
}

/**
 * Legacy MJPG framing to match the Express sample behavior.
 */
class LegacyMjpegStreamer(private val boundaryLine: String) {
    fun writeFrame(out: OutputStream, jpgBytes: ByteArray, headerMode: LegacyHeaderMode) {
        val headers = mutableListOf(boundaryLine, "Content-Type: image/jpeg")
        when (headerMode) {
            LegacyHeaderMode.LENGTH -> headers.add("Content-length: ${jpgBytes.size}")
            LegacyHeaderMode.ZERO_LENGTH -> headers.add("Content-length: 0")
            LegacyHeaderMode.NONE -> Unit
        }
        val headerText = headers.joinToString("\n") + "\n\n"
        out.write(headerText.toByteArray())
        out.write(jpgBytes)
    }
}
