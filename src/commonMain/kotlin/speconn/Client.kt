package speconn

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@PublishedApi
internal class KtorTransport(
    @PublishedApi internal val httpClient: HttpClient
) : Transport {

    override suspend fun call(
        url: String,
        contentType: String,
        headers: Map<String, String>,
        body: String
    ): TransportResponse {
        val response = httpClient.post(url) {
            this.contentType(ContentType.parse(contentType))
            for ((k, v) in headers) header(k, v)
            setBody(body)
        }
        return TransportResponse(
            response.status.value,
            response.headers.names().associateWith { name ->
                response.headers.getAll(name)?.joinToString(", ") ?: ""
            },
            readAllBytes(response.bodyAsChannel())
        )
    }
}

class UnaryClient(
    @PublishedApi internal val baseUrl: String,
    @PublishedApi internal val transport: Transport
) {
    constructor(url: String, httpClient: HttpClient) : this(url, KtorTransport(httpClient))

    suspend inline fun <reified Req, reified Res> call(
        path: String,
        req: Req,
        headers: Map<String, String> = emptyMap()
    ): Res =
        unaryCallImpl(transport, baseUrl, path, req, serializer<Req>(), serializer<Res>(), headers)

    suspend inline fun <reified Req, reified Res> stream(
        path: String,
        req: Req,
        headers: Map<String, String> = emptyMap()
    ): List<Res> =
        streamCallImpl(transport, baseUrl, path, req, serializer<Req>(), serializer<Res>(), headers)
}

@PublishedApi
internal suspend fun <Req, Res> unaryCallImpl(
    transport: Transport,
    baseUrl: String,
    path: String,
    req: Req,
    reqSer: KSerializer<Req>,
    resSer: KSerializer<Res>,
    headers: Map<String, String>
): Res {
    val url = baseUrl.trimEnd('/') + path
    val body = json.encodeToString(reqSer, req)
    val resp = transport.call(url, "application/json", headers, body)
    val text = resp.body.decodeToString()
    if (resp.status != 200) throw parseError(text)
    return json.decodeFromString(resSer, text)
}

@PublishedApi
internal suspend fun <Req, Res> streamCallImpl(
    transport: Transport,
    baseUrl: String,
    path: String,
    req: Req,
    reqSer: KSerializer<Req>,
    resSer: KSerializer<Res>,
    headers: Map<String, String>
): List<Res> {
    val url = baseUrl.trimEnd('/') + path
    val body = json.encodeToString(reqSer, req)
    val resp = transport.call(
        url,
        "application/connect+json",
        mapOf("Connect-Protocol-Version" to "1") + headers,
        body
    )
    if (resp.status != 200) {
        throw SpeconnError(Codes.UNKNOWN, resp.body.decodeToString())
    }
    val messages = mutableListOf<Res>()
    var offset = 0
    while (offset < resp.body.size) {
        val (flags, payload) = decodeEnvelope(resp.body, offset) ?: break
        offset += 5 + payload.size
        if (flags and FLAG_END_STREAM != 0) {
            val trailer = json.decodeFromString<JsonObject>(payload.decodeToString())
            val errObj = trailer["error"]?.jsonObject
            if (errObj != null) throw SpeconnError(
                errObj["code"]?.jsonPrimitive?.content ?: Codes.UNKNOWN,
                errObj["message"]?.jsonPrimitive?.content ?: ""
            )
            break
        }
        messages.add(json.decodeFromString(resSer, payload.decodeToString()))
    }
    return messages
}

@PublishedApi
internal suspend fun readAllBytes(channel: ByteReadChannel): ByteArray {
    val parts = mutableListOf<ByteArray>()
    var total = 0
    val tmp = ByteArray(8192)
    while (true) {
        val read = channel.readAvailable(tmp)
        if (read <= 0) break
        parts.add(tmp.copyOfRange(0, read))
        total += read
    }
    val result = ByteArray(total)
    var offset = 0
    for (part in parts) {
        part.copyInto(result, offset)
        offset += part.size
    }
    return result
}
