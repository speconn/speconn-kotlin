package speconn

data class HttpRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

data class HttpResponse(
    val status: Int,
    val body: ByteArray,
) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

interface SpeconnTransport {
    suspend fun send(request: HttpRequest): HttpResponse
    fun close() {}
}
