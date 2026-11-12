package africa.fundaza.customer.ui

import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.data.repository.AuthRepository
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val session: SessionManager,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = session.state

    init {
        restore()
    }

    fun restore() {
        session.restoring()
        viewModelScope.launch { auth.restore() }
    }

    fun signOut() {
        viewModelScope.launch { auth.signOut() }
    }
}
