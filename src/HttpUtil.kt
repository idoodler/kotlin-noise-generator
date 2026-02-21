import com.sun.net.httpserver.HttpExchange
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.max
import kotlin.math.min

/**
 * Parses the query string into a decoded key/value map.
 */
fun parseQuery(query: String?): Map<String, String> {
    if (query.isNullOrBlank()) return emptyMap()
    return query.split("&").mapNotNull { pair ->
        val parts = pair.split("=", limit = 2)
        if (parts.isEmpty()) return@mapNotNull null
        val key = parts[0].trim()
        if (key.isEmpty()) return@mapNotNull null
        val value = if (parts.size > 1) parts[1] else ""
        val decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8)
        val decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8)
        decodedKey to decodedValue
    }.toMap()
}

/**
 * Parses an int with bounds and fallback.
 */
fun clampInt(value: String?, fallback: Int, min: Int, max: Int): Int {
    val parsed = value?.toIntOrNull() ?: fallback
    return min(max(parsed, min), max)
}

/**
 * Disables caching for dynamic image responses.
 */
fun setNoCacheHeaders(exchange: HttpExchange) {
    exchange.responseHeaders.add("Cache-Control", "no-cache, no-store, must-revalidate")
    exchange.responseHeaders.add("Pragma", "no-cache")
    exchange.responseHeaders.add("Expires", "0")
}

/**
 * Sends a 405 response for non-GET requests.
 */
fun sendMethodNotAllowed(exchange: HttpExchange) {
    exchange.responseHeaders.add("Allow", "GET")
    exchange.sendResponseHeaders(405, -1)
    exchange.close()
}

/**
 * Sends a plain text response.
 */
fun sendText(exchange: HttpExchange, status: Int, body: String) {
    exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
    val bytes = body.toByteArray()
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

/**
 * Sends an HTML response.
 */
fun sendHtml(exchange: HttpExchange, status: Int, body: String) {
    exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
    val bytes = body.toByteArray()
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

/**
 * Parses a boolean query parameter with a default.
 */
fun parseBoolean(value: String?, defaultValue: Boolean): Boolean {
    if (value.isNullOrBlank()) return defaultValue
    return when (value.lowercase()) {
        "1", "true", "yes", "y", "on" -> true
        "0", "false", "no", "n", "off" -> false
        else -> defaultValue
    }
}

/**
 * Adds CORS headers when requested via query params.
 */
fun applyCorsHeaders(exchange: HttpExchange, params: Map<String, String>) {
    val cors = parseBoolean(params["cors"], false)
    val exposeAuthHeader = parseBoolean(params["exposeAuthHeader"], true)
    if (cors) {
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
    }
    if (exposeAuthHeader) {
        exchange.responseHeaders.add("Access-Control-Expose-Headers", "WWW-Authenticate")
    }
}
