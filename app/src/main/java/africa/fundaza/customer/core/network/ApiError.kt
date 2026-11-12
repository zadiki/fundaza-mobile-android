package africa.fundaza.customer.core.network

/**
 * Why a call to the Fundaza API did not succeed.
 *
 * The server writes refusal copy for the person who hit it (docs/mobile-api.md:
 * "Clients display `message` verbatim"), so [message] is shown as-is wherever
 * the server supplied one, and only falls back to our own words when it did not.
 */
sealed interface ApiError {
    val message: String

    /** No token, or one that expired or was revoked. The session is over. */
    data class Unauthenticated(
        override val message: String = "Your session has ended. Please sign in again.",
    ) : ApiError

    /** Signed in, but not allowed to do this. */
    data class Forbidden(override val message: String) : ApiError

    /** A rejected body or a domain refusal (422). [fieldErrors] is keyed by request field. */
    data class Validation(
        override val message: String,
        val fieldErrors: Map<String, List<String>> = emptyMap(),
    ) : ApiError {
        fun firstErrorFor(field: String): String? = fieldErrors[field]?.firstOrNull()
    }

    /** No such record, or not one this caller may see — deliberately indistinguishable. */
    data class NotFound(override val message: String = "We couldn't find that.") : ApiError

    /** Too many attempts in a short time (login is limited to five a minute). */
    data class RateLimited(
        override val message: String = "Too many attempts. Please wait a minute and try again.",
    ) : ApiError

    data class Server(
        val code: Int,
        override val message: String = "Something went wrong on our side. Please try again shortly.",
    ) : ApiError

    data class Network(
        override val message: String = "We couldn't reach Fundaza. Check your connection and try again.",
    ) : ApiError

    data class Unexpected(
        override val message: String = "Something unexpected happened. Please try again.",
    ) : ApiError
}
