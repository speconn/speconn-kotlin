package speconn

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object Codes {
    const val CANCELED = "canceled"
    const val UNKNOWN = "unknown"
    const val INVALID_ARGUMENT = "invalid_argument"
    const val DEADLINE_EXCEEDED = "deadline_exceeded"
    const val NOT_FOUND = "not_found"
    const val ALREADY_EXISTS = "already_exists"
    const val PERMISSION_DENIED = "permission_denied"
    const val RESOURCE_EXHAUSTED = "resource_exhausted"
    const val FAILED_PRECONDITION = "failed_precondition"
    const val ABORTED = "aborted"
    const val OUT_OF_RANGE = "out_of_range"
    const val UNIMPLEMENTED = "unimplemented"
    const val INTERNAL = "internal"
    const val UNAVAILABLE = "unavailable"
    const val DATA_LOSS = "data_loss"
    const val UNAUTHENTICATED = "unauthenticated"
}

@Serializable
data class SpeconnError(
    val code: String,
    override val message: String
) : Exception("$code: $message") {

    fun httpStatus(): Int = codeToHttpStatus(code)

    companion object {
        fun fromHttpStatus(status: Int, message: String = ""): SpeconnError =
            SpeconnError(httpStatusToCode(status), message)
    }
}

@PublishedApi
internal val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@PublishedApi
internal fun codeToHttpStatus(code: String): Int = when (code) {
    Codes.CANCELED -> 499
    Codes.UNKNOWN -> 500
    Codes.INVALID_ARGUMENT -> 400
    Codes.DEADLINE_EXCEEDED -> 504
    Codes.NOT_FOUND -> 404
    Codes.ALREADY_EXISTS -> 409
    Codes.PERMISSION_DENIED -> 403
    Codes.RESOURCE_EXHAUSTED -> 429
    Codes.FAILED_PRECONDITION -> 400
    Codes.ABORTED -> 409
    Codes.OUT_OF_RANGE -> 400
    Codes.UNIMPLEMENTED -> 501
    Codes.INTERNAL -> 500
    Codes.UNAVAILABLE -> 503
    Codes.DATA_LOSS -> 500
    Codes.UNAUTHENTICATED -> 401
    else -> 500
}

@PublishedApi
internal fun httpStatusToCode(status: Int): String = when (status) {
    400 -> Codes.INVALID_ARGUMENT
    401 -> Codes.UNAUTHENTICATED
    403 -> Codes.PERMISSION_DENIED
    404 -> Codes.NOT_FOUND
    409 -> Codes.ABORTED
    429 -> Codes.RESOURCE_EXHAUSTED
    499 -> Codes.CANCELED
    500 -> Codes.INTERNAL
    501 -> Codes.UNIMPLEMENTED
    503 -> Codes.UNAVAILABLE
    504 -> Codes.DEADLINE_EXCEEDED
    else -> Codes.UNKNOWN
}

@PublishedApi
internal fun parseError(body: String): SpeconnError = json.decodeFromString(body)
