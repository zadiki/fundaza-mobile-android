package africa.fundaza.customer.ui.feature.profile

import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.data.repository.CustomerRepository
import africa.fundaza.customer.domain.model.CurrentUser
import africa.fundaza.customer.domain.model.Customer
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.toLoadState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: CurrentUser? = null,
    val customer: LoadState<Customer> = LoadState.Loading,
    val signingOut: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    session: SessionManager,
    private val customers: CustomerRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState(user = (session.state.value as? SessionState.SignedIn)?.user))
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(customer = LoadState.Loading) }
        viewModelScope.launch { _state.update { it.copy(customer = customers.customer().toLoadState()) } }
    }

    fun markSigningOut() = _state.update { it.copy(signingOut = true) }
}
