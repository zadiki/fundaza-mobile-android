package africa.fundaza.customer.ui.feature.recipients

import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.network.getOrNull
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.data.repository.CorridorRepository
import africa.fundaza.customer.domain.model.BeneficiaryField
import africa.fundaza.customer.domain.model.Corridor
import africa.fundaza.customer.domain.model.Country
import africa.fundaza.customer.domain.model.Method
import africa.fundaza.customer.domain.model.NewBeneficiary
import africa.fundaza.customer.domain.model.NewWay
import africa.fundaza.customer.domain.model.RecipientType
import africa.fundaza.customer.domain.model.Relationship
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.toLoadState
import africa.fundaza.customer.ui.navigation.AddRecipientRoute
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddRecipientUiState(
    /** Set when adding a way for someone already saved; hides name, type and relationship. */
    val existingId: Long? = null,
    val existingName: String? = null,

    val corridors: LoadState<List<Corridor>> = LoadState.Loading,
    val country: Country? = null,
    val countryLocked: Boolean = false,
    val methods: LoadState<List<Method>>? = null,
    val deliveryMethod: Method? = null,
    val methodLocked: Boolean = false,
    val currency: String? = null,
    val currencyLocked: Boolean = false,

    val fullName: String = "",
    val type: RecipientType = RecipientType.PERSON,
    val relationship: Relationship? = null,

    val fields: LoadState<List<BeneficiaryField>>? = null,
    val values: Map<String, String> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),

    val saving: Boolean = false,
    val formError: String? = null,
) {
    val isNewRecipient: Boolean get() = existingId == null

    val countries: List<Country>
        get() = corridors.valueOrNull.orEmpty().map { it.destination }.distinctBy { it.iso2 }.sortedBy { it.name }

    val currencies: List<String>
        get() = corridors.valueOrNull.orEmpty()
            .filter { it.destination.iso2 == country?.iso2 }
            .flatMap { it.receiveCurrencies }
            .distinct()

    val relationshipOptions: List<Relationship> get() = Relationship.forType(type)
}

sealed interface AddRecipientEvent {
    data class Saved(val beneficiaryId: Long) : AddRecipientEvent
}

@HiltViewModel
class AddRecipientViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val corridors: CorridorRepository,
    private val beneficiaries: BeneficiaryRepository,
) : ViewModel() {

    private val args = AddRecipientRoute(
        country = savedStateHandle["country"],
        deliveryMethod = savedStateHandle["deliveryMethod"],
        currency = savedStateHandle["currency"],
        beneficiaryId = savedStateHandle["beneficiaryId"] ?: AddRecipientRoute.NO_BENEFICIARY,
    )

    private val _state = MutableStateFlow(
        AddRecipientUiState(existingId = args.beneficiaryId.takeIf { it != AddRecipientRoute.NO_BENEFICIARY }),
    )
    val state: StateFlow<AddRecipientUiState> = _state.asStateFlow()

    private val _events = Channel<AddRecipientEvent>(Channel.BUFFERED)
    val events: Flow<AddRecipientEvent> = _events.receiveAsFlow()

    init {
        loadCorridors()
        _state.value.existingId?.let { id ->
            viewModelScope.launch {
                beneficiaries.beneficiary(id).getOrNull()?.let { b -> _state.update { it.copy(existingName = b.fullName) } }
            }
        }
    }

    fun loadCorridors() {
        _state.update { it.copy(corridors = LoadState.Loading) }
        viewModelScope.launch {
            val result = corridors.corridors()
            _state.update { it.copy(corridors = result.toLoadState()) }
            val list = result.getOrNull() ?: return@launch
            val preset = args.country?.let { iso -> list.firstOrNull { it.destination.iso2 == iso }?.destination }
            if (preset != null) {
                _state.update { it.copy(countryLocked = true) }
                selectCountry(preset)
            }
        }
    }

    fun selectCountry(country: Country) {
        val currencies = _state.value.corridors.valueOrNull.orEmpty()
            .filter { it.destination.iso2 == country.iso2 }
            .flatMap { it.receiveCurrencies }
            .distinct()
        val presetCurrency = args.currency?.takeIf { it in currencies }
        _state.update {
            it.copy(
                country = country,
                currency = presetCurrency ?: currencies.firstOrNull(),
                currencyLocked = presetCurrency != null,
                methods = LoadState.Loading,
                deliveryMethod = null,
                fields = null,
                values = emptyMap(),
                errors = emptyMap(),
            )
        }
        loadMethods(country)
    }

    /** Every delivery method offered on any corridor into [country]. */
    private fun loadMethods(country: Country) {
        viewModelScope.launch {
            val into = _state.value.corridors.valueOrNull.orEmpty().filter { it.destination.iso2 == country.iso2 }
            val results = into.map { c -> async { corridors.methods(c.id) } }.awaitAll()
            if (_state.value.country?.iso2 != country.iso2) return@launch
            val failure = results.firstOrNull { it is ApiResult.Failure } as? ApiResult.Failure
            val methods = results.mapNotNull { it.getOrNull() }.flatMap { it.delivery }.distinctBy { it.code }
            if (methods.isEmpty() && failure != null) {
                _state.update { it.copy(methods = LoadState.Failed(failure.error.message)) }
                return@launch
            }
            _state.update { it.copy(methods = LoadState.Loaded(methods)) }
            val preset = args.deliveryMethod?.let { code -> methods.firstOrNull { it.code == code } }
            if (preset != null) {
                _state.update { it.copy(methodLocked = true) }
                selectDeliveryMethod(preset)
            } else if (methods.size == 1) {
                selectDeliveryMethod(methods.first())
            }
        }
    }

    fun retryMethods() = _state.value.country?.let { loadMethods(it) }

    fun selectDeliveryMethod(method: Method) {
        val country = _state.value.country ?: return
        _state.update { it.copy(deliveryMethod = method, fields = LoadState.Loading, values = emptyMap(), errors = emptyMap()) }
        viewModelScope.launch {
            val result = beneficiaries.fields(country.iso2, method.code)
            if (_state.value.deliveryMethod?.code != method.code) return@launch
            _state.update { it.copy(fields = result.toLoadState()) }
        }
    }

    fun retryFields() = _state.value.deliveryMethod?.let { selectDeliveryMethod(it) }

    fun selectCurrency(code: String) = _state.update { it.copy(currency = code) }

    fun onNameChange(value: String) = _state.update { it.copy(fullName = value, errors = it.errors - "full_name") }

    fun selectType(type: RecipientType) = _state.update {
        it.copy(type = type, relationship = it.relationship?.takeIf { r -> r in Relationship.forType(type) })
    }

    fun selectRelationship(r: Relationship) = _state.update { it.copy(relationship = r, errors = it.errors - "relationship") }

    fun onFieldChange(key: String, value: String) = _state.update {
        it.copy(values = it.values + (key to value), errors = it.errors - key)
    }

    fun save() {
        val s = _state.value
        if (s.saving) return
        val fields = s.fields?.valueOrNull.orEmpty()

        val errors = buildMap {
            if (s.isNewRecipient && s.fullName.isBlank()) put("full_name", "Enter their full name, as on their ID")
            if (s.country == null) put("country", "Choose a country")
            if (s.deliveryMethod == null) put("delivery_method", "Choose how they receive money")
            fields.forEach { f -> f.validate(s.values[f.key].orEmpty())?.let { put(f.key, it) } }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, formError = null) }
            return
        }
        val country = s.country ?: return
        val method = s.deliveryMethod ?: return
        val values = fields.associate { it.key to s.values[it.key].orEmpty().trim() }.filterValues { it.isNotEmpty() }

        _state.update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            val result = if (s.existingId != null) {
                beneficiaries.addWay(s.existingId, NewWay(method.code, country.iso2, s.currency, values))
            } else {
                beneficiaries.create(
                    NewBeneficiary(
                        fullName = s.fullName,
                        type = s.type,
                        relationship = s.relationship,
                        countryIso2 = country.iso2,
                        currency = s.currency,
                        deliveryMethod = method.code,
                        fields = values,
                    ),
                )
            }
            when (result) {
                is ApiResult.Success -> {
                    _state.update { it.copy(saving = false) }
                    _events.send(AddRecipientEvent.Saved(result.value.id))
                }
                is ApiResult.Failure -> {
                    val error = result.error
                    // Server field errors come back as "full_name", "relationship" or "fields.<key>".
                    val fieldErrors = (error as? ApiError.Validation)?.fieldErrors.orEmpty()
                        .mapNotNull { (k, v) -> v.firstOrNull()?.let { k.removePrefix("fields.") to it } }
                        .toMap()
                    _state.update { it.copy(saving = false, errors = fieldErrors, formError = error.message) }
                }
            }
        }
    }
}
