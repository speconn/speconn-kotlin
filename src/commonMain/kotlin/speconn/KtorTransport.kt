package speconn

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*

class KtorTransport(private val client: HttpClient, private val ownsClient: Boolean = false) : SpeconnTransport {

    constructor(client: HttpClient) : this(client, false)

    override suspend fun send(request: HttpRequest): HttpResponse {
        val resp = client.request(request.url) {
            method = HttpMethod.parse(request.method)
            request.headers.forEach { (k, v) -> headers.append(k, v) }
            setBody(request.body)
        }
        val respHeaders = resp.headers.entries().flatMap { (k, vs) -> vs.map { v -> Pair(k, v) } }
        return HttpResponse(status = resp.status.value, headers = respHeaders, body = resp.readRawBytes())
    }

    override fun close() {
        if (ownsClient) client.close()
    }
}

fun KtorTransport(): KtorTransport = KtorTransport(HttpClient(), ownsClient = true)
