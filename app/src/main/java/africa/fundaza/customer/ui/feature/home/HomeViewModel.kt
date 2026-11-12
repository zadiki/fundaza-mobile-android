package africa.fundaza.customer.ui.feature.home

import africa.fundaza.customer.core.network.getOrNull
import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.data.repository.CustomerRepository
import africa.fundaza.customer.data.repository.TransferRepository
import africa.fundaza.customer.data.repository.WalletRepository
import africa.fundaza.customer.domain.model.Customer
import africa.fundaza.customer.domain.model.Permissions
import africa.fundaza.customer.domain.model.Transfer
import africa.fundaza.customer.domain.model.Wallet
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.toLoadState
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

data class HomeUiState(
    val firstName: String = "",
    val canSend: Boolean = false,
    val canViewTransfers: Boolean = false,
    val customer: Customer? = null,
    /** Null when the login may not see the wallet at all. */
    val wallet: LoadState<Wallet>? = null,
    val recent: LoadState<List<Transfer>> = LoadState.Loading,
    val recipientNames: Map<Long, String> = emptyMap(),
    val refreshing: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val session: SessionManager,
    private val customers: CustomerRepository,
    private val wallets: WalletRepository,
    private val transfers: TransferRepository,
    private val beneficiaries: BeneficiaryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        val user = (session.state.value as? SessionState.SignedIn)?.user ?: return
        val canView = user.can(Permissions.TXN_VIEW)
        _state.update {
            it.copy(
                firstName = user.name.substringBefore(' '),
                canSend = user.can(Permissions.TXN_CREATE),
                canViewTransfers = canView,
                refreshing = !initial,
                wallet = if (canView) (if (initial) LoadState.Loading else it.wallet) else null,
            )
        }
        viewModelScope.launch {
            val customer = async { customers.customer() }
            val wallet = if (canView) async { wallets.wallet() } else null
            val recent = if (canView) async { transfers.transfers() } else null
            val people = async { beneficiaries.beneficiaries() }

            val names = people.await().getOrNull().orEmpty().associate { it.id to it.fullName }
            _state.update { s ->
                s.copy(
                    customer = customer.await().getOrNull() ?: s.customer,
                    wallet = wallet?.await()?.toLoadState(),
                    recent = recent?.await()?.toLoadState()?.let { st ->
                        if (st is LoadState.Loaded) LoadState.Loaded(st.value.take(5)) else st
                    } ?: LoadState.Loaded(emptyList()),
                    recipientNames = names.ifEmpty { s.recipientNames },
                    refreshing = false,
                )
            }
        }
    }
}
