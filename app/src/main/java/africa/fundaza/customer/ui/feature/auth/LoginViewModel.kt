package africa.fundaza.customer.ui.feature.auth

import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.repository.AuthRepository
import africa.fundaza.customer.data.repository.SignInResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    /** Shown above the button, verbatim from the server where it gave one. */
    val formError: String? = null,
    val submitting: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onEmailChange(value: String) = _state.update { it.copy(email = value, emailError = null, formError = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, passwordError = null, formError = null) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val emailError = when {
            current.email.isBlank() -> "Enter your email address"
            !EMAIL.matches(current.email.trim()) -> "That doesn't look like an email address"
            else -> null
        }
        val passwordError = if (current.password.isEmpty()) "Enter your password" else null
        if (emailError != null || passwordError != null) {
            _state.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            when (val result = auth.signIn(current.email, current.password)) {
                // The session drives navigation; nothing to do here but stop spinning.
                is ApiResult.Success -> when (result.value) {
                    is SignInResult.SignedIn, SignInResult.SigningUp ->
                        _state.update { it.copy(submitting = false, password = "") }
                    SignInResult.NotACustomer -> _state.update {
                        it.copy(
                            submitting = false,
                            password = "",
                            formError = "This app is for Fundaza customers. Agents and staff sign in on the web.",
                        )
                    }
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    _state.update {
                        it.copy(
                            submitting = false,
                            // The server answers both "wrong password" and "no such account" with the same field error.
                            formError = (error as? ApiError.Validation)?.firstErrorFor("email") ?: error.message,
                        )
                    }
                }
            }
        }
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}
