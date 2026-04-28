package speconn

const val FLAG_COMPRESSED = 1
const val FLAG_END_STREAM = 2

@PublishedApi
internal fun encodeEnvelope(flags: Int, payload: ByteArray): ByteArray {
    val result = ByteArray(5 + payload.size)
    result[0] = flags.toByte()
    val len = payload.size
    result[1] = ((len shr 24) and 0xFF).toByte()
    result[2] = ((len shr 16) and 0xFF).toByte()
    result[3] = ((len shr 8) and 0xFF).toByte()
    result[4] = (len and 0xFF).toByte()
    payload.copyInto(result, 5)
    return result
}

@PublishedApi
internal fun decodeEnvelope(data: ByteArray, offset: Int): Pair<Int, ByteArray>? {
    if (data.size - offset < 5) return null
    val flags = data[offset].toInt() and 0xFF
    val length = ((data[offset + 1].toLong() and 0xFF) shl 24) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 8) or
            (data[offset + 4].toLong() and 0xFF)
    if (data.size - offset < 5 + length) return null
    val payload = data.sliceArray(offset + 5 until offset + 5 + length.toInt())
    return flags to payload
}
