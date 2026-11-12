package africa.fundaza.customer.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException

@Serializable
internal data class ErrorBody(
    val message: String? = null,
    val errors: Map<String, List<String>>? = null,
)

/**
 * Runs one API call and turns every way it can fail into an [ApiError].
 * Cancellation is rethrown so structured concurrency keeps working.
 */
class ApiCaller(private val json: Json) {

    suspend operator fun <T> invoke(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        ApiResult.Failure(errorFor(e.code(), e.response()?.errorBody()?.string()))
    } catch (e: IOException) {
        ApiResult.Failure(ApiError.Network())
    } catch (e: Exception) {
        ApiResult.Failure(ApiError.Unexpected())
    }

    fun errorFor(code: Int, rawBody: String?): ApiError {
        val body = rawBody
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { json.decodeFromString(ErrorBody.serializer(), it) }.getOrNull() }
        val message = body?.message?.takeIf { it.isNotBlank() }
        return when (code) {
            401 -> ApiError.Unauthenticated()
            403 -> ApiError.Forbidden(message ?: "You don't have permission to do this.")
            404 -> ApiError.NotFound(message ?: ApiError.NotFound().message)
            422 -> ApiError.Validation(
                message = message ?: "Please check the details and try again.",
                fieldErrors = body?.errors.orEmpty(),
            )
            429 -> ApiError.RateLimited()
            in 500..599 -> ApiError.Server(code)
            else -> ApiError.Server(code, message ?: ApiError.Server(code).message)
        }
    }
}
