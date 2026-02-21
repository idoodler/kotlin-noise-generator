import com.sun.net.httpserver.HttpExchange
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

private const val AUTH_REALM = "NoiseGenerator"
private const val AUTH_USERNAME = "user"
private const val AUTH_PASSWORD = "password"
private const val AUTH_NONCE = "noise-generator-nonce"
private const val AUTH_OPAQUE = "noise-generator-opaque"

/**
 * Applies Basic or Digest auth when requested via the `auth` query param.
 */
fun authenticate(exchange: HttpExchange, params: Map<String, String>): Boolean {
    return when (params["auth"]?.lowercase()) {
        "basic" -> authenticateBasic(exchange)
        "digest" -> authenticateDigest(exchange)
        else -> true
    }
}

private fun authenticateBasic(exchange: HttpExchange): Boolean {
    val header = exchange.requestHeaders.getFirst("Authorization") ?: return respondBasicChallenge(exchange)
    if (!header.startsWith("Basic ")) return respondBasicChallenge(exchange)
    val token = header.removePrefix("Basic ").trim()
    val decoded = try {
        String(Base64.getDecoder().decode(token), StandardCharsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        return respondBasicChallenge(exchange)
    }
    val expected = "$AUTH_USERNAME:$AUTH_PASSWORD"
    return if (decoded == expected) {
        true
    } else {
        respondBasicChallenge(exchange)
    }
}

private fun respondBasicChallenge(exchange: HttpExchange): Boolean {
    exchange.responseHeaders.add("WWW-Authenticate", "Basic realm=\"$AUTH_REALM\"")
    exchange.sendResponseHeaders(401, -1)
    exchange.close()
    return false
}

private fun authenticateDigest(exchange: HttpExchange): Boolean {
    val header = exchange.requestHeaders.getFirst("Authorization") ?: return respondDigestChallenge(exchange)
    if (!header.startsWith("Digest ")) return respondDigestChallenge(exchange)
    val fields = parseDigestHeader(header.removePrefix("Digest "))
    val username = fields["username"] ?: return respondDigestChallenge(exchange)
    if (username != AUTH_USERNAME) return respondDigestChallenge(exchange)
    val nonce = fields["nonce"] ?: return respondDigestChallenge(exchange)
    val uri = fields["uri"] ?: return respondDigestChallenge(exchange)
    val response = fields["response"] ?: return respondDigestChallenge(exchange)
    val qop = fields["qop"] ?: return respondDigestChallenge(exchange)
    val nc = fields["nc"] ?: return respondDigestChallenge(exchange)
    val cnonce = fields["cnonce"] ?: return respondDigestChallenge(exchange)

    val ha1 = md5("$AUTH_USERNAME:$AUTH_REALM:$AUTH_PASSWORD")
    val ha2 = md5("${exchange.requestMethod}:$uri")
    val expected = md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
    return if (nonce == AUTH_NONCE && response == expected) {
        true
    } else {
        respondDigestChallenge(exchange)
    }
}

private fun respondDigestChallenge(exchange: HttpExchange): Boolean {
    val header = "Digest realm=\"$AUTH_REALM\", qop=\"auth\", nonce=\"$AUTH_NONCE\", opaque=\"$AUTH_OPAQUE\""
    exchange.responseHeaders.add("WWW-Authenticate", header)
    exchange.sendResponseHeaders(401, -1)
    exchange.close()
    return false
}

private fun parseDigestHeader(header: String): Map<String, String> {
    val result = LinkedHashMap<String, String>()
    header.split(",").forEach { part ->
        val trimmed = part.trim()
        val idx = trimmed.indexOf('=')
        if (idx <= 0) return@forEach
        val key = trimmed.substring(0, idx).trim()
        var value = trimmed.substring(idx + 1).trim()
        if (value.startsWith("\"") && value.endsWith("\"") && value.length >= 2) {
            value = value.substring(1, value.length - 1)
        }
        result[key] = value
    }
    return result
}

private fun md5(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(StandardCharsets.ISO_8859_1))
    return digest.joinToString("") { "%02x".format(it) }
}
