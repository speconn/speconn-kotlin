package speconn

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SpeconnError(val code: String, val message: String) : Exception("$code: $message")

val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

object Codes {
    const val CANCELED = "canceled"
    const val UNKNOWN = "unknown"
    const val INVALID_ARGUMENT = "invalid_argument"
    const val NOT_FOUND = "not_found"
    const val INTERNAL = "internal"
    const val UNAVAILABLE = "unavailable"
    const val UNAUTHENTICATED = "unauthenticated"
}
