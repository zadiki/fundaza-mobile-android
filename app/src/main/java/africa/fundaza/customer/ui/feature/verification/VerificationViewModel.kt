package africa.fundaza.customer.ui.feature.verification

import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.repository.KycRepository
import africa.fundaza.customer.data.repository.SignUpRepository
import africa.fundaza.customer.domain.model.CountryOption
import africa.fundaza.customer.domain.model.KycDocumentType
import africa.fundaza.customer.domain.model.KycState
import africa.fundaza.customer.domain.model.NewIdentityDocument
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
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import javax.inject.Inject

data class VerificationUiState(
    val kyc: LoadState<KycState> = LoadState.Loading,
    val refreshing: Boolean = false,
    /** Something in flight (opening the provider, uploading, submitting, restarting). */
    val busy: Boolean = false,
    val message: String? = null,
    // The identity upload form (DOCUMENTS mode).
    val countries: List<CountryOption> = emptyList(),
    val type: KycDocumentType? = null,
    val number: String = "",
    val issuingCountry: CountryOption? = null,
    val expiresAt: LocalDate? = null,
    val fileUri: String? = null,
    val fileName: String? = null,
    val errors: Map<String, String> = emptyMap(),
)

/** A token to hand to the provider's SDK, once. */
data class ProviderLaunch(val token: String)

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val kyc: KycRepository,
    private val signUps: SignUpRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VerificationUiState())
    val state: StateFlow<VerificationUiState> = _state.asStateFlow()

    private val _launch = MutableStateFlow<ProviderLaunch?>(null)
    val launch: StateFlow<ProviderLaunch?> = _launch.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            val countries = (signUps.countries() as? ApiResult.Success)?.value.orEmpty()
            _state.update { it.copy(countries = countries) }
        }
    }

    fun load(refresh: Boolean = false) {
        _state.update { it.copy(refreshing = refresh, kyc = if (refresh) it.kyc else LoadState.Loading) }
        viewModelScope.launch {
            val result = kyc.state().toLoadState()
            _state.update { s ->
                s.copy(
                    kyc = if (refresh && result is LoadState.Failed && s.kyc is LoadState.Loaded) s.kyc else result,
                    refreshing = false,
                    type = s.type ?: result.valueOrNull?.documentTypes?.firstOrNull(),
                )
            }
        }
    }

    private fun applied(result: ApiResult<KycState>, success: String? = null) {
        _state.update {
            when (result) {
                is ApiResult.Success -> it.copy(busy = false, kyc = LoadState.Loaded(result.value), message = success)
                is ApiResult.Failure -> it.copy(busy = false, message = result.error.message)
            }
        }
    }

    // --- hosted (provider SDK) --------------------------------------------------

    fun startHosted() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val token = kyc.providerToken()) {
                is ApiResult.Success -> {
                    _state.update { it.copy(busy = false) }
                    _launch.value = ProviderLaunch(token.value)
                }
                is ApiResult.Failure -> _state.update { it.copy(busy = false, message = token.error.message) }
            }
        }
    }

    fun launched() {
        _launch.value = null
    }

    /** Called by the SDK on its own background thread when the token runs out. */
    fun freshTokenBlocking(): String? = runBlocking { (kyc.providerToken() as? ApiResult.Success)?.value }

    /** The SDK reports the applicant has sent everything: put them forward, which opens screening. */
    fun providerSubmitted() {
        viewModelScope.launch {
            applied(kyc.submit(), success = "Thanks. We're checking your details and will tell you when it's done.")
        }
    }

    fun providerFailed(message: String) = _state.update { it.copy(message = message) }

    // --- documents ----------------------------------------------------------------

    fun onType(v: KycDocumentType) = edit("document_type") { copy(type = v) }
    fun onNumber(v: String) = edit("document_number") { copy(number = v) }
    fun onIssuingCountry(v: CountryOption) = edit("issuing_country_iso2") { copy(issuingCountry = v) }
    fun onExpiry(v: LocalDate) = edit("expires_at") { copy(expiresAt = v) }
    fun onFile(uri: String, name: String?) = edit("file") { copy(fileUri = uri, fileName = name ?: "Photo chosen") }

    private fun edit(field: String, change: VerificationUiState.() -> VerificationUiState) =
        _state.update { it.change().copy(errors = it.errors - field, message = null) }

    fun upload() {
        val s = _state.value
        if (s.busy) return
        val errors = buildMap {
            if (s.type == null) put("document_type", "Choose the kind of document")
            if (s.number.trim().length < 3) put("document_number", "Enter the number printed on it")
            if (s.issuingCountry == null) put("issuing_country_iso2", "Choose the country that issued it")
            if (s.expiresAt == null) put("expires_at", "Choose the expiry date")
            if (s.fileUri == null) put("file", "Add a clear photo or scan of it")
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors) }
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val result = kyc.uploadIdentity(
                NewIdentityDocument(s.type!!.code, s.number, s.issuingCountry!!.iso2, s.expiresAt!!, s.fileUri!!),
            )
            if (result is ApiResult.Failure && result.error is ApiError.Validation) {
                val fieldErrors = (result.error as ApiError.Validation).fieldErrors.mapValues { it.value.first() }
                _state.update { it.copy(busy = false, errors = fieldErrors, message = if (fieldErrors.isEmpty()) result.error.message else null) }
                return@launch
            }
            applied(result, success = "Your document is saved. Submit it when you're ready.")
            if (result is ApiResult.Success) {
                _state.update { it.copy(number = "", expiresAt = null, fileUri = null, fileName = null) }
            }
        }
    }

    fun submit() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            applied(kyc.submit(), success = "Thanks. We're checking your details and will tell you when it's done.")
        }
    }

    fun restart() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch { applied(kyc.restart(), success = "Verification reopened. Send your documents again.") }
    }
}
