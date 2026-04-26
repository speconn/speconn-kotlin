package speconn

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class UnaryClient(private val httpClient: OkHttpClient, private val baseUrl: String) {
    inline fun <reified Req, reified Res> call(path: String, req: Req): Res {
        val body = json.encodeToString(serializer<Req>(), req)
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        val response = httpClient.newCall(request).execute()
        val respBody = response.body?.string() ?: throw SpeconnError(Codes.INTERNAL, "empty response")
        if (!response.isSuccessful) {
            throw json.decodeFromString<SpeconnError>(respBody)
        }
        return json.decodeFromString(serializer<Res>(), respBody)
    }
}

class StreamClient(private val httpClient: OkHttpClient, private val baseUrl: String) {
    inline fun <reified Req, reified Res> call(path: String, req: Req): Sequence<Res> {
        val body = json.encodeToString(serializer<Req>(), req)
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + path)
            .post(body.toRequestBody("application/connect+json".toMediaType()))
            .header("Connect-Protocol-Version", "1")
            .build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw SpeconnError(Codes.UNKNOWN, errBody)
        }
        val responseBody = response.body?.byteStream() ?: throw SpeconnError(Codes.INTERNAL, "no body")

        return sequence {
            val buf = ByteArrayOutputStream()
            val tmp = ByteArray(4096)
            while (true) {
                val n = responseBody.read(tmp)
                if (n > 0) buf.write(tmp, 0, n)

                val data = buf.toByteArray()
                var pos = 0
                while (data.size - pos >= 5) {
                    val flags = data[pos]
                    val length = ((data[pos+1].toLong() and 0xFF) shl 24) or
                                 ((data[pos+2].toLong() and 0xFF) shl 16) or
                                 ((data[pos+3].toLong() and 0xFF) shl 8) or
                                  (data[pos+4].toLong() and 0xFF)
                    if (data.size - pos < 5 + length) break

                    val payload = data.sliceArray(pos+5 until pos+5+length.toInt())
                    pos += 5 + length.toInt()

                    if (flags and FLAG_END_STREAM != 0.toByte()) {
                        val trailer = json.decodeFromString< kotlin.collections.Map<String, Any?> >(String(payload))
                        val err = trailer["error"] as? Map<*, *>
                        if (err != null) throw SpeconnError(err["code"] as? String ?: Codes.UNKNOWN, err["message"] as? String ?: "")
                        return@sequence
                    }
                    yield(json.decodeFromString(serializer<Res>(), String(payload)))
                }
                buf.reset()
                if (pos < data.size) buf.write(data, pos, data.size - pos)
                if (n < 0) break
            }
        }
    }
}

private const val FLAG_END_STREAM: Byte = 0x02
