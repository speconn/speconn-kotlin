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

/** Default HttpClient implementation using Ktor CIO. */
class KtorHttpClient(private val httpClient: HttpClient_) : speconn.HttpClient {
    override suspend fun send(request: HttpRequest): HttpResponse {
        val response = httpClient.request(request.url) {
            method = HttpMethod.Post
            for ((k, v) in request.headers) header(k, v)
            setBody(request.body)
        }
        val body = readAllBytes(response.bodyAsChannel())
        return HttpResponse(
            status = response.status.value,
            headers = response.headers.names().associateWith { name ->
                response.headers.getAll(name)?.joinToString(", ") ?: ""
            },
            body = body,
        )
    }
}

// Type alias to avoid conflict with speconn.HttpClient
private typealias HttpClient_ = io.ktor.client.HttpClient

class UnaryClient(
    @PublishedApi internal val baseUrl: String,
    @PublishedApi internal val httpClient: speconn.HttpClient,
) {
    constructor(url: String, ktorClient: io.ktor.client.HttpClient) : this(url, KtorHttpClient(ktorClient))

    suspend inline fun <reified Req, reified Res> call(
        path: String,
        req: Req,
        headers: Map<String, String> = emptyMap(),
    ): Res = unaryCallImpl(httpClient, baseUrl, path, req, serializer<Req>(), serializer<Res>(), headers)

    suspend inline fun <reified Req, reified Res> stream(
        path: String,
        req: Req,
        headers: Map<String, String> = emptyMap(),
    ): List<Res> = streamCallImpl(httpClient, baseUrl, path, req, serializer<Req>(), serializer<Res>(), headers)
}

@PublishedApi
internal suspend fun <Req, Res> unaryCallImpl(
    httpClient: speconn.HttpClient,
    baseUrl: String,
    path: String,
    req: Req,
    reqSer: KSerializer<Req>,
    resSer: KSerializer<Res>,
    headers: Map<String, String>,
): Res {
    val url = baseUrl.trimEnd('/') + path
    val body = json.encodeToString(reqSer, req).encodeToByteArray()
    val reqHeaders = mapOf("content-type" to "application/json") + headers
    val resp = httpClient.send(HttpRequest(url = url, method = "POST", headers = reqHeaders, body = body))
    val text = resp.body.decodeToString()
    if (resp.status != 200) throw parseError(text)
    return json.decodeFromString(resSer, text)
}

@PublishedApi
internal suspend fun <Req, Res> streamCallImpl(
    httpClient: speconn.HttpClient,
    baseUrl: String,
    path: String,
    req: Req,
    reqSer: KSerializer<Req>,
    resSer: KSerializer<Res>,
    headers: Map<String, String>,
): List<Res> {
    val url = baseUrl.trimEnd('/') + path
    val body = json.encodeToString(reqSer, req).encodeToByteArray()
    val reqHeaders = mapOf(
        "content-type" to "application/connect+json",
        "Connect-Protocol-Version" to "1",
    ) + headers
    val resp = httpClient.send(HttpRequest(url = url, method = "POST", headers = reqHeaders, body = body))
    if (resp.status != 200) throw SpeconnError(Codes.UNKNOWN, resp.body.decodeToString())

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
                errObj["message"]?.jsonPrimitive?.content ?: "",
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
