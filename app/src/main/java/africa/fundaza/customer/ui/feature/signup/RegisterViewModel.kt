package africa.fundaza.customer.ui.feature.signup

import africa.fundaza.customer.core.location.CountryLocator
import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.repository.SignUpRepository
import africa.fundaza.customer.domain.model.CountryOption
import africa.fundaza.customer.domain.model.NewSignUp
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

data class RegisterUiState(
    val countries: LoadState<List<CountryOption>> = LoadState.Loading,
    val fullName: String = "",
    val email: String = "",
    val residence: CountryOption? = null,
    /** Without the country code; the residence's dialling code is put in front. */
    val phone: String = "",
    val password: String = "",
    val confirmation: String = "",
    /** Keyed by the server's field names, so its refusals land on the right input. */
    val errors: Map<String, String> = emptyMap(),
    val formError: String? = null,
    val submitting: Boolean = false,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val signUps: SignUpRepository,
    private val locator: CountryLocator,
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    init {
        loadCountries()
    }

    fun loadCountries() {
        _state.update { it.copy(countries = LoadState.Loading) }
        viewModelScope.launch {
            val here = async { locator.currentCountry() }
            val countries = signUps.countries().toLoadState()
            val guess = here.await()
            _state.update { s ->
                s.copy(
                    countries = countries,
                    residence = s.residence
                        ?: countries.valueOrNull?.firstOrNull { it.iso2.equals(guess, ignoreCase = true) },
                )
            }
        }
    }

    fun onFullName(v: String) = edit("full_name") { copy(fullName = v) }
    fun onEmail(v: String) = edit("email") { copy(email = v) }
    fun onResidence(v: CountryOption) = edit("residence_iso2") { copy(residence = v) }
    fun onPhone(v: String) = edit("phone") { copy(phone = v.filter { it.isDigit() || it == '+' || it == ' ' }) }
    fun onPassword(v: String) = edit("password") { copy(password = v) }
    fun onConfirmation(v: String) = edit("password") { copy(confirmation = v) }

    private fun edit(field: String, change: RegisterUiState.() -> RegisterUiState) =
        _state.update { it.change().copy(errors = it.errors - field, formError = null) }

    fun submit() {
        val s = _state.value
        if (s.submitting) return

        val errors = buildMap {
            if (s.fullName.isBlank()) put("full_name", "Enter your full name, as on your ID")
            if (s.email.isBlank()) put("email", "Enter your email address")
            else if (!EMAIL.matches(s.email.trim())) put("email", "That doesn't look like an email address")
            if (s.residence == null) put("residence_iso2", "Choose the country you live in")
            if (s.phone.count { it.isDigit() } < 6) put("phone", "Enter your mobile number")
            if (s.password.isEmpty()) put("password", "Choose a password")
            else if (s.password != s.confirmation) put("password", "The two passwords don't match")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        _state.update { it.copy(submitting = true, formError = null) }
        viewModelScope.launch {
            val result = signUps.register(
                NewSignUp(
                    fullName = s.fullName,
                    email = s.email,
                    phone = fullPhone(s.phone, s.residence!!),
                    residenceIso2 = s.residence.iso2,
                    password = s.password,
                    passwordConfirmation = s.confirmation,
                ),
            )
            // On success the session moves to the sign-up steps; this screen is left behind.
            if (result is ApiResult.Failure) {
                val error = result.error
                val fieldErrors = (error as? ApiError.Validation)?.fieldErrors.orEmpty()
                    .filterKeys { it in FIELDS }
                    .mapValues { it.value.first() }
                _state.update {
                    it.copy(
                        submitting = false,
                        errors = fieldErrors,
                        formError = if (fieldErrors.isEmpty()) error.message else null,
                    )
                }
            }
        }
    }

    private fun fullPhone(typed: String, residence: CountryOption): String {
        val digits = typed.filterNot { it.isWhitespace() }
        if (digits.startsWith("+")) return digits
        val code = residence.dialCode.filter { it.isDigit() }
        return "+$code${digits.trimStart('0')}"
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        val FIELDS = setOf("full_name", "email", "phone", "residence_iso2", "password")
    }
}
