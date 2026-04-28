package speconn

data class HttpRequest(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

data class HttpResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
)

/** HttpClient is the interface Speconn expects HTTP clients to implement. */
interface HttpClient {
    suspend fun send(request: HttpRequest): HttpResponse
}
