package africa.fundaza.customer.ui.feature.wallet

import africa.fundaza.customer.data.repository.WalletRepository
import africa.fundaza.customer.domain.model.Wallet
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.toLoadState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class WalletViewModel @Inject constructor(
    private val wallets: WalletRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<LoadState<Wallet>>(LoadState.Loading)
    val state: StateFlow<LoadState<Wallet>> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.value = LoadState.Loading
        viewModelScope.launch { _state.value = wallets.wallet().toLoadState() }
    }
}
