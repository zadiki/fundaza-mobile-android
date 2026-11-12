package africa.fundaza.customer.ui.feature.recipients

import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.toLoadState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecipientDetailUiState(
    val recipient: LoadState<Beneficiary> = LoadState.Loading,
    val deleting: Boolean = false,
    val deleteError: String? = null,
)

@HiltViewModel
class RecipientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val beneficiaries: BeneficiaryRepository,
) : ViewModel() {

    val id: Long = checkNotNull(savedStateHandle["id"])

    private val _state = MutableStateFlow(RecipientDetailUiState())
    val state: StateFlow<RecipientDetailUiState> = _state.asStateFlow()

    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    val deleted: Flow<Unit> = _deleted.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_state.value.recipient !is LoadState.Loaded) _state.update { it.copy(recipient = LoadState.Loading) }
            _state.update { it.copy(recipient = beneficiaries.beneficiary(id).toLoadState()) }
        }
    }

    fun delete() {
        _state.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            when (val result = beneficiaries.delete(id)) {
                is ApiResult.Success -> _deleted.send(Unit)
                is ApiResult.Failure -> _state.update { it.copy(deleting = false, deleteError = result.error.message) }
            }
        }
    }
}
