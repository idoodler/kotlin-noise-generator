import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * HTTP server wiring for the noise endpoints.
 */
class NoiseServer(private val port: Int, private val basePath: String? = null) {
    fun start() {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        val normalizedBasePath = normalizeBasePath(basePath)
        val prefixes = if (normalizedBasePath == null) listOf("") else listOf("", normalizedBasePath)
        for (prefix in prefixes) {
            server.createContext(joinPath(prefix, "/"), RootHandler())
            server.createContext(joinPath(prefix, "/health"), HealthHandler())
            server.createContext(joinPath(prefix, "/stream.cgi"), StreamCgiHandler())
            server.createContext(joinPath(prefix, "/api/noise.jpg"), NoiseJpgHandler())
            server.createContext(joinPath(prefix, "/api/noise.mjpg"), NoiseMjpgHandler())
        }
        server.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        server.start()
        println("Noise server running on http://localhost:$port")
    }
}

private fun normalizeBasePath(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty() || trimmed == "/") return null
    val withLeading = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    return withLeading.trimEnd('/')
}

private fun joinPath(prefix: String, suffix: String): String {
    if (prefix.isEmpty()) return suffix
    return if (suffix == "/") "$prefix/" else "$prefix$suffix"
}
