package speconn

import specodec.SpecCodec
import specodec.dispatch
import specodec.respond

class SpeconnClient(
    private val url: String,
    private val transport: SpeconnTransport,
) {
    constructor(baseUrl: String, path: String, transport: SpeconnTransport)
            : this(baseUrl.trimEnd('/') + path, transport)

    private fun getContentType(headers: Map<String, String>): String =
        headers.entries.find { it.key.lowercase() == "content-type" }?.value ?: "application/json"

    private fun getAccept(headers: Map<String, String>): String =
        headers.entries.find { it.key.lowercase() == "accept" }?.value ?: getContentType(headers)

    private fun extractFormat(mime: String): String =
        if (mime.contains("msgpack")) "msgpack" else "json"

    suspend fun <T> call(
        reqCodec: SpecCodec<T>,
        req: T,
        resCodec: SpecCodec<T>,
        headers: Map<String, String> = emptyMap(),
    ): T {
        val reqFmt = extractFormat(getContentType(headers))
        val resFmt = extractFormat(getAccept(headers))
        val body = respond(reqCodec, req, reqFmt).body
        val resp = transport.send(HttpRequest(url = url, method = "POST",
            headers = headers.entries.map { it.key to it.value }, body = body))
        if (resp.status >= 400) throw decodeError(resp.body, "json")
        return dispatch(resCodec, resp.body, resFmt)
    }

    suspend fun <T> stream(
        reqCodec: SpecCodec<T>,
        req: T,
        resCodec: SpecCodec<T>,
        headers: Map<String, String> = emptyMap(),
    ): List<T> {
        val reqFmt = extractFormat(getContentType(headers))
        val resFmt = extractFormat(getAccept(headers))
        val streamHeaders = if (!headers.keys.any { it.lowercase() == "connect-protocol-version" })
            headers + ("connect-protocol-version" to "1") else headers
        val body = respond(reqCodec, req, reqFmt).body
        val resp = transport.send(HttpRequest(url = url, method = "POST",
            headers = streamHeaders.entries.map { it.key to it.value }, body = body))
        if (resp.status >= 400) throw decodeError(resp.body, "json")

        val results = mutableListOf<T>()
        var pos = 0
        while (pos < resp.body.size) {
            if (resp.body.size - pos < 5) break
            val flags = resp.body[pos].toInt()
            val length = ((resp.body[pos + 1].toInt() and 0xFF) shl 24) or
                    ((resp.body[pos + 2].toInt() and 0xFF) shl 16) or
                    ((resp.body[pos + 3].toInt() and 0xFF) shl 8) or
                    (resp.body[pos + 4].toInt() and 0xFF)
            pos += 5
            val payload = resp.body.sliceArray(pos until pos + length)
            pos += length
            if ((flags and 2) != 0) {
                if (payload.isNotEmpty()) throw decodeError(payload, resFmt)
                break
            }
            results.add(dispatch(resCodec, payload, resFmt))
        }
        return results
    }
}
