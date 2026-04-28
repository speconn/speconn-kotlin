package speconn

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class SpeconnClient(
    private val url: String,
    private val transport: SpeconnTransport,
) {
    constructor(baseUrl: String, path: String, transport: SpeconnTransport)
            : this(baseUrl.trimEnd('/') + path, transport)

    suspend inline fun <reified Req, reified Res> call(
        req: Req,
        headers: Map<String, String> = emptyMap(),
    ): Res {
        val body = Json.encodeToString(req).encodeToByteArray()
        val reqHeaders = listOf("content-type" to "application/json") + headers.entries.map { it.key to it.value }
        val resp = transport.send(HttpRequest(url = url, method = "POST", headers = reqHeaders, body = body))
        if (resp.status >= 400) {
            val err = Json.decodeFromString<Map<String, String>>(resp.body.decodeToString())
            throw SpeconnError(err["code"] ?: "unknown", err["message"] ?: "")
        }
        return Json.decodeFromString<Res>(resp.body.decodeToString())
    }

    suspend inline fun <reified Req, reified Res> stream(
        req: Req,
        headers: Map<String, String> = emptyMap(),
    ): List<Res> {
        val body = Json.encodeToString(req).encodeToByteArray()
        val reqHeaders = listOf(
            "content-type" to "application/connect+json",
            "connect-protocol-version" to "1",
        ) + headers.entries.map { it.key to it.value }
        val resp = transport.send(HttpRequest(url = url, method = "POST", headers = reqHeaders, body = body))
        if (resp.status >= 400) {
            val err = Json.decodeFromString<Map<String, String>>(resp.body.decodeToString())
            throw SpeconnError(err["code"] ?: "unknown", err["message"] ?: "")
        }
        val results = mutableListOf<Res>()
        var pos = 0
        while (pos < resp.body.size) {
            if (resp.body.size - pos < 5) break
            val flags = resp.body[pos]
            val length = ((resp.body[pos + 1].toInt() and 0xFF) shl 24) or
                    ((resp.body[pos + 2].toInt() and 0xFF) shl 16) or
                    ((resp.body[pos + 3].toInt() and 0xFF) shl 8) or
                    (resp.body[pos + 4].toInt() and 0xFF)
            pos += 5
            val payload = resp.body.sliceArray(pos until pos + length)
            pos += length
            if ((flags.toInt() and 2) != 0) {
                val trailer = Json.decodeFromString<JsonObject>(payload.decodeToString())
                val error = trailer["error"]?.jsonObject
                if (error != null) {
                    throw SpeconnError(
                        error["code"]?.jsonPrimitive?.content ?: "unknown",
                        error["message"]?.jsonPrimitive?.content ?: "",
                    )
                }
                break
            }
            results.add(Json.decodeFromString<Res>(payload.decodeToString()))
        }
        return results
    }
}
