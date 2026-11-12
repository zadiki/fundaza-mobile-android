package africa.fundaza.customer.ui.components

import africa.fundaza.customer.core.network.ApiResult

/** What a screen that reads one thing from the server is showing. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Loaded<T>(val value: T, val refreshing: Boolean = false) : LoadState<T>
    data class Failed(val message: String) : LoadState<Nothing>

    val valueOrNull: T? get() = (this as? Loaded<T>)?.value
}

fun <T> ApiResult<T>.toLoadState(): LoadState<T> = when (this) {
    is ApiResult.Success -> LoadState.Loaded(value)
    is ApiResult.Failure -> LoadState.Failed(error.message)
}
