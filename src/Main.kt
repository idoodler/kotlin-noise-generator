// Entry point for the Noise Generator server.
fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    NoiseServer(port).start()
}
