package africa.fundaza.customer.ui.feature.transfers

import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.network.getOrNull
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.data.repository.TransferRepository
import africa.fundaza.customer.domain.model.StatusTone
import africa.fundaza.customer.domain.model.Transfer
import africa.fundaza.customer.ui.components.LoadState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class TransferFilter(val label: String) {
    All("All"),
    Active("In progress"),
    Done("Delivered"),
    Other("Returned or failed");

    fun accepts(t: Transfer): Boolean = when (this) {
        All -> true
        Active -> t.status.tone == StatusTone.InProgress
        Done -> t.status.tone == StatusTone.Success
        Other -> t.status.tone == StatusTone.Problem || t.status.tone == StatusTone.Returned
    }
}

data class TransfersUiState(
    val transfers: LoadState<List<Transfer>> = LoadState.Loading,
    val filter: TransferFilter = TransferFilter.All,
    val recipientNames: Map<Long, String> = emptyMap(),
    val refreshing: Boolean = false,
) {
    val visible: List<Transfer> get() = transfers.valueOrNull.orEmpty().filter(filter::accepts)
}

@HiltViewModel
class TransfersViewModel @Inject constructor(
    private val transfers: TransferRepository,
    private val beneficiaries: BeneficiaryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TransfersUiState())
    val state: StateFlow<TransfersUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun setFilter(filter: TransferFilter) = _state.update { it.copy(filter = filter) }

    fun refresh() = load(refreshing = true)

    fun load(refreshing: Boolean = false) {
        _state.update {
            if (refreshing && it.transfers is LoadState.Loaded) it.copy(refreshing = true) else it.copy(transfers = LoadState.Loading)
        }
        viewModelScope.launch {
            val list = async { transfers.transfers() }
            val people = async { beneficiaries.beneficiaries() }
            val result = list.await()
            val names = people.await().getOrNull().orEmpty().associate { it.id to it.fullName }
            _state.update {
                it.copy(
                    transfers = when (result) {
                        is ApiResult.Success -> LoadState.Loaded(result.value)
                        // Keep what's on screen if a refresh fails.
                        is ApiResult.Failure -> if (refreshing && it.transfers is LoadState.Loaded) it.transfers else LoadState.Failed(result.error.message)
                    },
                    recipientNames = names.ifEmpty { it.recipientNames },
                    refreshing = false,
                )
            }
        }
    }
}
