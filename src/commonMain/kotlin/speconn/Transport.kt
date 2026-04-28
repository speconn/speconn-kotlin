package speconn

class TransportResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: ByteArray
)

interface Transport {
    suspend fun call(
        url: String,
        contentType: String,
        headers: Map<String, String>,
        body: String
    ): TransportResponse
}
