package africa.fundaza.customer.ui.feature.signup

import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.data.repository.SignUpRepository
import africa.fundaza.customer.domain.model.CountryOption
import africa.fundaza.customer.domain.model.IndividualDetails
import africa.fundaza.customer.domain.model.SignUpStep
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class SignUpStepsUiState(
    // Email
    val checking: Boolean = false,
    val resending: Boolean = false,
    /** A short note under the email step: "sent", or "not confirmed yet". */
    val emailNote: String? = null,
    // Phone
    val codeSent: Boolean = false,
    val sendingCode: Boolean = false,
    val code: String = "",
    val confirmingCode: Boolean = false,
    // Details
    val countries: List<CountryOption> = emptyList(),
    val firstName: String = "",
    val lastName: String = "",
    val dateOfBirth: LocalDate? = null,
    val nationality: CountryOption? = null,
    val addressLine1: String = "",
    val addressCity: String = "",
    val finishing: Boolean = false,
    /** Keyed by the server's field names. */
    val errors: Map<String, String> = emptyMap(),
    val formError: String? = null,
)

/**
 * The sign-up steps after the account exists. Which step shows is the
 * server's `next_step`, carried on the session; this holds only what is being
 * typed and what is in flight.
 */
@HiltViewModel
class SignUpStepsViewModel @Inject constructor(
    private val signUps: SignUpRepository,
    session: SessionManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpStepsUiState())
    val state: StateFlow<SignUpStepsUiState> = _state.asStateFlow()

    init {
        // The name given at sign-up, split the way the web does: last word is the surname.
        val signUp = (session.state.value as? SessionState.SigningUp)?.signUp
        val parts = signUp?.fullName?.trim()?.split(Regex("\\s+")).orEmpty().filter { it.isNotEmpty() }
        _state.update {
            it.copy(
                firstName = if (parts.size > 1) parts.dropLast(1).joinToString(" ") else parts.firstOrNull().orEmpty(),
                lastName = if (parts.size > 1) parts.last() else "",
            )
        }
        viewModelScope.launch {
            val countries = (signUps.countries() as? ApiResult.Success)?.value.orEmpty()
            _state.update { s ->
                s.copy(
                    countries = countries,
                    nationality = s.nationality ?: countries.firstOrNull { it.iso2 == signUp?.residenceIso2 },
                )
            }
        }
    }

    // --- email -------------------------------------------------------------

    /** After the link was followed in a browser. Quiet when [userAsked] is false (coming back to the app). */
    fun checkEmail(userAsked: Boolean = true) {
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, emailNote = if (userAsked) null else it.emailNote) }
        viewModelScope.launch {
            val result = signUps.refresh()
            _state.update {
                it.copy(
                    checking = false,
                    emailNote = when {
                        !userAsked -> it.emailNote
                        result is ApiResult.Failure -> result.error.message
                        (result as ApiResult.Success).value.nextStep == SignUpStep.EMAIL ->
                            "We haven't seen the confirmation yet. Open the link in the email, then try again."
                        else -> null
                    },
                )
            }
        }
    }

    fun resendEmail() {
        if (_state.value.resending) return
        _state.update { it.copy(resending = true, emailNote = null) }
        viewModelScope.launch {
            val result = signUps.resendEmail()
            _state.update {
                it.copy(
                    resending = false,
                    emailNote = when (result) {
                        is ApiResult.Success -> "We've sent a new link. It may take a minute to arrive."
                        is ApiResult.Failure -> result.error.message
                    },
                )
            }
        }
    }

    // --- phone -------------------------------------------------------------

    fun sendCode() {
        if (_state.value.sendingCode) return
        _state.update { it.copy(sendingCode = true, formError = null) }
        viewModelScope.launch {
            val result = signUps.sendPhoneCode()
            _state.update {
                it.copy(
                    sendingCode = false,
                    codeSent = it.codeSent || result is ApiResult.Success,
                    formError = (result as? ApiResult.Failure)?.error?.message,
                )
            }
        }
    }

    fun onCode(value: String) = _state.update {
        it.copy(code = value.filter(Char::isDigit).take(6), errors = it.errors - "code", formError = null)
    }

    fun confirmCode() {
        val s = _state.value
        if (s.confirmingCode) return
        if (s.code.length != 6) {
            _state.update { it.copy(errors = it.errors + ("code" to "Enter the 6-digit code we texted you")) }
            return
        }
        _state.update { it.copy(confirmingCode = true) }
        viewModelScope.launch {
            val result = signUps.confirmPhone(s.code)
            _state.update {
                it.copy(
                    confirmingCode = false,
                    errors = if (result is ApiResult.Failure) it.errors + ("code" to result.error.message) else it.errors,
                )
            }
        }
    }

    // --- details -----------------------------------------------------------

    fun onFirstName(v: String) = edit("first_name") { copy(firstName = v) }
    fun onLastName(v: String) = edit("last_name") { copy(lastName = v) }
    fun onDateOfBirth(v: LocalDate) = edit("date_of_birth") { copy(dateOfBirth = v) }
    fun onNationality(v: CountryOption) = edit("nationality_iso2") { copy(nationality = v) }
    fun onAddressLine1(v: String) = edit("address_line1") { copy(addressLine1 = v) }
    fun onAddressCity(v: String) = edit("address_city") { copy(addressCity = v) }

    private fun edit(field: String, change: SignUpStepsUiState.() -> SignUpStepsUiState) =
        _state.update { it.change().copy(errors = it.errors - field, formError = null) }

    fun finish() {
        val s = _state.value
        if (s.finishing) return
        val errors = buildMap {
            if (s.firstName.isBlank()) put("first_name", "Enter your first name")
            if (s.lastName.isBlank()) put("last_name", "Enter your last name")
            if (s.dateOfBirth == null) put("date_of_birth", "Choose your date of birth")
            if (s.nationality == null) put("nationality_iso2", "Choose your nationality")
            if (s.addressLine1.isNotBlank() && s.addressCity.isBlank()) put("address_city", "Add the town or city")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }

        _state.update { it.copy(finishing = true, formError = null) }
        viewModelScope.launch {
            val result = signUps.finishAsIndividual(
                IndividualDetails(
                    firstName = s.firstName,
                    lastName = s.lastName,
                    dateOfBirth = s.dateOfBirth!!,
                    nationalityIso2 = s.nationality!!.iso2,
                    addressLine1 = s.addressLine1,
                    addressCity = s.addressCity,
                ),
            )
            // On success the session becomes a signed-in customer and this screen is gone.
            if (result is ApiResult.Failure) {
                val fieldErrors = (result.error as? ApiError.Validation)?.fieldErrors.orEmpty()
                    .filterKeys { it in DETAIL_FIELDS }
                    .mapValues { it.value.first() }
                _state.update {
                    it.copy(
                        finishing = false,
                        errors = fieldErrors,
                        formError = if (fieldErrors.isEmpty()) result.error.message else null,
                    )
                }
                // A refusal such as "confirm your email first" may mean the step changed under us.
                if (fieldErrors.isEmpty()) signUps.refresh()
            }
        }
    }

    private companion object {
        val DETAIL_FIELDS = setOf("first_name", "last_name", "date_of_birth", "nationality_iso2", "address_line1", "address_city")
    }
}
