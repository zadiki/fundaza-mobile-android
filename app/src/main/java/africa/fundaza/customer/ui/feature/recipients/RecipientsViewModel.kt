package africa.fundaza.customer.ui.feature.recipients

import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.ui.components.LoadState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipientsUiState(
    val recipients: LoadState<List<Beneficiary>> = LoadState.Loading,
    val query: String = "",
    val refreshing: Boolean = false,
) {
    val visible: List<Beneficiary>
        get() = recipients.valueOrNull.orEmpty()
            .filter { query.isBlank() || it.fullName.contains(query.trim(), ignoreCase = true) }
            .sortedBy { it.fullName.lowercase() }
}

@HiltViewModel
class RecipientsViewModel @Inject constructor(
    private val beneficiaries: BeneficiaryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipientsUiState())
    val state: StateFlow<RecipientsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onQueryChange(q: String) = _state.update { it.copy(query = q) }

    fun refresh() = load(refreshing = true)

    fun load(refreshing: Boolean = false) {
        _state.update { if (refreshing && it.recipients is LoadState.Loaded) it.copy(refreshing = true) else it.copy(recipients = LoadState.Loading) }
        viewModelScope.launch {
            val result = beneficiaries.beneficiaries()
            _state.update {
                it.copy(
                    recipients = when (result) {
                        is ApiResult.Success -> LoadState.Loaded(result.value)
                        is ApiResult.Failure -> if (refreshing && it.recipients is LoadState.Loaded) it.recipients else LoadState.Failed(result.error.message)
                    },
                    refreshing = false,
                )
            }
        }
    }
}
