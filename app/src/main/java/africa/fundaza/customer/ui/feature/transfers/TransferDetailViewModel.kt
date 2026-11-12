package africa.fundaza.customer.ui.feature.transfers

import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.network.getOrNull
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.data.repository.TransferRepository
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.domain.model.TransferDetail
import africa.fundaza.customer.domain.model.TransferStatus
import africa.fundaza.customer.ui.components.LoadState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransferDetailUiState(
    val reference: String,
    val detail: LoadState<TransferDetail> = LoadState.Loading,
    val recipient: Beneficiary? = null,
    val refreshing: Boolean = false,
    /** The number to send an M-Pesa prompt to; blank means the customer's own. */
    val mpesaPhone: String = "",
    val prompting: Boolean = false,
    /** The number Safaricom prompted, once it has been. */
    val promptedPhone: String? = null,
    val promptError: String? = null,
)

@HiltViewModel
class TransferDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val transfers: TransferRepository,
    private val beneficiaries: BeneficiaryRepository,
) : ViewModel() {

    // Type-safe routes store each property under its own name.
    private val reference: String = checkNotNull(savedStateHandle["reference"])

    private val _state = MutableStateFlow(TransferDetailUiState(reference))
    val state: StateFlow<TransferDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() = load(refreshing = true)

    private var watchJob: Job? = null

    fun onMpesaPhoneChange(phone: String) =
        _state.update { it.copy(mpesaPhone = phone.filter { c -> c.isDigit() || c == '+' }.take(16), promptError = null) }

    /** Asks Safaricom to prompt the phone, then watches for the payment to land. */
    fun startMpesa() {
        if (_state.value.prompting) return
        _state.update { it.copy(prompting = true, promptError = null) }
        viewModelScope.launch {
            when (val result = transfers.startMpesa(reference, _state.value.mpesaPhone)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(prompting = false, promptedPhone = result.value) }
                    watchForPayment()
                }
                is ApiResult.Failure -> _state.update { it.copy(prompting = false, promptError = result.error.message) }
            }
        }
    }

    /** Re-reads the transfer every few seconds for a while, until it is no longer awaiting payment. */
    private fun watchForPayment() {
        watchJob?.cancel()
        watchJob = viewModelScope.launch {
            repeat(WATCH_ATTEMPTS) {
                delay(WATCH_INTERVAL_MS)
                fetch(refreshing = true)
                val status = (_state.value.detail as? LoadState.Loaded)?.value?.transfer?.status
                if (status != null && status != TransferStatus.AWAITING_PAYMENT) return@launch
            }
        }
    }

    private companion object {
        const val WATCH_INTERVAL_MS = 5_000L
        const val WATCH_ATTEMPTS = 24
    }

    fun load(refreshing: Boolean = false) {
        _state.update { if (refreshing) it.copy(refreshing = true) else it.copy(detail = LoadState.Loading) }
        viewModelScope.launch { fetch(refreshing) }
    }

    private suspend fun fetch(refreshing: Boolean) {
        val result = transfers.transfer(reference)
        _state.update {
            it.copy(
                detail = when (result) {
                    is ApiResult.Success -> LoadState.Loaded(result.value)
                    is ApiResult.Failure -> if (refreshing && it.detail is LoadState.Loaded) it.detail else LoadState.Failed(result.error.message)
                },
                refreshing = false,
            )
        }
        val beneficiaryId = (result as? ApiResult.Success)?.value?.transfer?.beneficiaryId
        if (beneficiaryId != null && _state.value.recipient?.id != beneficiaryId) {
            val person = beneficiaries.beneficiary(beneficiaryId).getOrNull()
            _state.update { it.copy(recipient = person) }
        }
    }
}
