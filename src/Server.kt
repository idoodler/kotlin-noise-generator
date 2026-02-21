import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
 * HTTP server wiring for the noise endpoints.
 */
class NoiseServer(private val port: Int) {
    fun start() {
        val server = HttpServer.create(InetSocketAddress(port), 0)
        server.createContext("/", RootHandler())
        server.createContext("/health", HealthHandler())
        server.createContext("/stream.cgi", StreamCgiHandler())
        server.createContext("/api/noise.jpg", NoiseJpgHandler())
        server.createContext("/api/noise.mjpg", NoiseMjpgHandler())
        server.executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
        server.start()
        println("Noise server running on http://localhost:$port")
    }
}
