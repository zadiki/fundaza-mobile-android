package africa.fundaza.customer.ui.feature.transfers

import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.network.getOrNull
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.data.repository.TransferRepository
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.domain.model.TransferDetail
import africa.fundaza.customer.ui.components.LoadState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun load(refreshing: Boolean = false) {
        _state.update { if (refreshing) it.copy(refreshing = true) else it.copy(detail = LoadState.Loading) }
        viewModelScope.launch {
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
}
