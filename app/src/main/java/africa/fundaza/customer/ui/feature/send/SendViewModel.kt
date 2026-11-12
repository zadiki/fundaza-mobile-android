package africa.fundaza.customer.ui.feature.send

import africa.fundaza.customer.core.location.CountryLocator
import africa.fundaza.customer.core.money.Money
import africa.fundaza.customer.core.money.parseAmountInput
import africa.fundaza.customer.core.money.sanitiseAmountInput
import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.data.repository.CorridorRepository
import africa.fundaza.customer.data.repository.CustomerRepository
import africa.fundaza.customer.data.repository.DocumentRepository
import africa.fundaza.customer.data.repository.TransferRepository
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.domain.model.BeneficiaryWay
import africa.fundaza.customer.domain.model.Corridor
import africa.fundaza.customer.domain.model.CorridorMethods
import africa.fundaza.customer.domain.model.Method
import africa.fundaza.customer.domain.model.Quote
import africa.fundaza.customer.domain.model.QuoteRequest
import africa.fundaza.customer.domain.model.SendingPurpose
import africa.fundaza.customer.domain.model.TransferDocument
import africa.fundaza.customer.domain.model.TransferDocumentType
import africa.fundaza.customer.ui.components.LoadState
import africa.fundaza.customer.ui.components.toLoadState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.Clock
import javax.inject.Inject

enum class SendStep(val title: String) {
    Amount("How much?"),
    Recipient("Who to?"),
    Details("Why?"),
    Review("Check and send"),
}

data class SendUiState(
    val step: SendStep = SendStep.Amount,

    // Route and amount
    val corridors: LoadState<List<Corridor>> = LoadState.Loading,
    /** Where the sender is sending from: where the phone is, else where they live. */
    val senderCountry: String? = null,
    /** The customer picked the destination themselves (vs. our default). */
    val routeChosen: Boolean = false,
    val corridor: Corridor? = null,
    val sendCurrency: String? = null,
    val receiveCurrency: String? = null,
    val methods: LoadState<CorridorMethods>? = null,
    val deliveryMethod: Method? = null,
    val paymentMethod: Method? = null,
    val amountInput: String = "",
    /** The customer typed an amount; stop prefilling the ~100 USD suggestion. */
    val amountEdited: Boolean = false,
    val amountError: String? = null,
    val fastArrival: Boolean = false,
    val quote: Quote? = null,
    val quoting: Boolean = false,
    val quoteError: String? = null,

    // Recipient
    val recipients: LoadState<List<Beneficiary>> = LoadState.Loading,
    val recipientId: Long? = null,
    /** What the customer typed in the recipient search box. */
    val recipientQuery: String = "",

    // Details
    val purpose: SendingPurpose? = null,
    val documents: List<TransferDocument> = emptyList(),
    val uploading: Boolean = false,
    val documentError: String? = null,

    // Review
    val submitting: Boolean = false,
    val submitError: String? = null,
    /** The quote lapsed on the review screen and was priced again. */
    val repriced: Boolean = false,
) {
    val destination: String? get() = corridor?.destination?.iso2

    /** Routes out of the sender's country: the only choices offered. */
    val destinations: List<Corridor>
        get() = corridors.valueOrNull.orEmpty().filter { it.origin.iso2 == senderCountry }

    /** Recipients who can be paid exactly this way, today. */
    val readyRecipients: List<Pair<Beneficiary, BeneficiaryWay>>
        get() {
            val country = destination ?: return emptyList()
            val method = deliveryMethod?.code ?: return emptyList()
            val currency = receiveCurrency ?: return emptyList()
            return recipients.valueOrNull.orEmpty().mapNotNull { b -> b.wayFor(country, method, currency)?.let { b to it } }
        }

    /**
     * Everyone else, who could be paid once a way to receive here is added: people already
     * linked to the destination country first, then the rest, each group by name.
     */
    val recipientsNeedingDetails: List<Beneficiary>
        get() {
            val country = destination ?: return emptyList()
            val readyIds = readyRecipients.map { it.first.id }.toSet()
            return recipients.valueOrNull.orEmpty()
                .filter { it.id !in readyIds }
                .sortedWith(
                    compareBy<Beneficiary> { b -> if (b.countryIso2 == country || b.ways.any { it.countryIso2 == country }) 0 else 1 }
                        .thenBy { it.fullName.lowercase() },
                )
        }

    /** [readyRecipients] narrowed by the search box. */
    val shownReadyRecipients: List<Pair<Beneficiary, BeneficiaryWay>>
        get() = readyRecipients.filter { it.first.matches(recipientQuery) }

    /** [recipientsNeedingDetails] narrowed by the search box. */
    val shownRecipientsNeedingDetails: List<Beneficiary>
        get() = recipientsNeedingDetails.filter { it.matches(recipientQuery) }

    val selectedRecipient: Pair<Beneficiary, BeneficiaryWay>?
        get() = readyRecipients.firstOrNull { it.first.id == recipientId }

    val documentsRequired: Boolean get() = purpose?.requiresDocument == true

    val canContinue: Boolean
        get() = when (step) {
            SendStep.Amount -> quote != null && !quoting && amountError == null && quoteError == null
            SendStep.Recipient -> selectedRecipient != null
            SendStep.Details -> purpose != null && !uploading && (!documentsRequired || documents.isNotEmpty())
            SendStep.Review -> quote != null && selectedRecipient != null && !submitting && !quoting
        }
}

sealed interface SendEvent {
    data class Sent(val reference: String) : SendEvent
}

@OptIn(FlowPreview::class)
@HiltViewModel
class SendViewModel @Inject constructor(
    private val corridorRepository: CorridorRepository,
    private val customerRepository: CustomerRepository,
    private val countryLocator: CountryLocator,
    private val transferRepository: TransferRepository,
    private val beneficiaryRepository: BeneficiaryRepository,
    private val documentRepository: DocumentRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(SendUiState())
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private val _events = Channel<SendEvent>(Channel.BUFFERED)
    val events: Flow<SendEvent> = _events.receiveAsFlow()

    /** What the next quote should price; null while the input can't be priced. */
    private val quoteRequest = MutableStateFlow<QuoteRequest?>(null)

    private var prefillJob: Job? = null

    /** USD per one unit of a send currency, per corridor; learnt from a probe quote. */
    private val usdRates = mutableMapOf<Pair<Long, String>, BigDecimal>()

    init {
        loadCorridors()
        loadRecipients()
        viewModelScope.launch {
            quoteRequest
                .debounce(QUOTE_DEBOUNCE_MS)
                .collectLatest { request -> if (request != null) fetchQuote(request) }
        }
    }

    // --- route & amount ---------------------------------------------------------

    fun loadCorridors() {
        _state.update { it.copy(corridors = LoadState.Loading) }
        viewModelScope.launch {
            val result = corridorRepository.corridors()
            val corridors = (result as? ApiResult.Success)?.value.orEmpty()
            val origin = if (corridors.isEmpty()) null else resolveOrigin(corridors)
            _state.update { it.copy(corridors = result.toLoadState(), senderCountry = origin) }
            if (_state.value.corridor == null) corridors.firstOrNull { it.origin.iso2 == origin }?.let(::pickCorridor)
        }
    }

    /**
     * The sender sends from the country the phone is in. When that can't be told, or no route
     * leaves from there, fall back to where they live, then to the first route we serve.
     */
    private suspend fun resolveOrigin(corridors: List<Corridor>): String {
        val origins = corridors.map { it.origin.iso2 }.toSet()
        countryLocator.currentCountry()?.takeIf { it in origins }?.let { return it }
        val residence = (customerRepository.customer() as? ApiResult.Success)?.value?.residenceIso2?.uppercase()
        residence?.takeIf { it in origins }?.let { return it }
        return corridors.first().origin.iso2
    }

    /** Location access was just granted: a better fix on the country may now be possible. */
    fun onLocationPermissionGranted() {
        viewModelScope.launch {
            val located = countryLocator.currentCountry() ?: return@launch
            val s = _state.value
            val corridors = s.corridors.valueOrNull.orEmpty()
            // Never pull the route out from under someone who has already made choices.
            if (located == s.senderCountry || s.step != SendStep.Amount || s.routeChosen || s.amountEdited) return@launch
            val first = corridors.firstOrNull { it.origin.iso2 == located } ?: return@launch
            _state.update { it.copy(senderCountry = located) }
            pickCorridor(first)
        }
    }

    /** The customer picked a destination. */
    fun selectCorridor(corridor: Corridor) {
        _state.update { it.copy(routeChosen = true) }
        pickCorridor(corridor)
    }

    private fun pickCorridor(corridor: Corridor) {
        if (_state.value.corridor?.id == corridor.id) return
        _state.update {
            it.copy(
                corridor = corridor,
                sendCurrency = corridor.sendCurrency,
                receiveCurrency = corridor.receiveCurrency,
                methods = LoadState.Loading,
                deliveryMethod = null,
                paymentMethod = null,
                recipientId = null,
                // A suggestion priced in the old route's currency means nothing here.
                amountInput = if (it.amountEdited) it.amountInput else "",
            )
        }
        reprice()
        viewModelScope.launch {
            val result = corridorRepository.methods(corridor.id)
            if (_state.value.corridor?.id != corridor.id) return@launch
            val methods = (result as? ApiResult.Success)?.value
            _state.update {
                it.copy(
                    methods = result.toLoadState(),
                    deliveryMethod = methods?.delivery?.firstOrNull(),
                    paymentMethod = methods?.payment?.firstOrNull(),
                )
            }
            reprice()
            prefillAmount()
        }
    }

    fun selectSendCurrency(code: String) {
        _state.update { it.copy(sendCurrency = code, amountInput = if (it.amountEdited) it.amountInput else "") }
        reprice()
        prefillAmount()
    }

    fun selectReceiveCurrency(code: String) {
        _state.update { it.copy(receiveCurrency = code, recipientId = null) }
        reprice()
    }

    fun selectDeliveryMethod(method: Method) {
        _state.update { it.copy(deliveryMethod = method, recipientId = null) }
        reprice()
    }

    fun selectPaymentMethod(method: Method) {
        _state.update { it.copy(paymentMethod = method) }
        reprice()
    }

    fun onAmountChange(raw: String) {
        prefillJob?.cancel()
        _state.update { it.copy(amountInput = sanitiseAmountInput(raw), amountEdited = true) }
        reprice()
    }

    /**
     * Until the customer types their own figure, suggest about [DEFAULT_USD] USD in the
     * route's send currency, rounded to a friendly number and kept inside the route's limits.
     */
    private fun prefillAmount() {
        if (_state.value.amountEdited) return
        prefillJob?.cancel()
        prefillJob = viewModelScope.launch {
            val s = _state.value
            val corridor = s.corridor ?: return@launch
            val currency = s.sendCurrency ?: return@launch
            val amount = suggestedAmount(s, corridor, currency) ?: return@launch
            val now = _state.value
            if (now.amountEdited || now.corridor?.id != corridor.id || now.sendCurrency != currency) return@launch
            _state.update { it.copy(amountInput = amount.toPlainString()) }
            reprice()
        }
    }

    private suspend fun suggestedAmount(s: SendUiState, corridor: Corridor, currency: String): BigDecimal? {
        val raw = if (currency == USD) {
            DEFAULT_USD
        } else {
            val rate = usdRate(s, corridor, currency)?.takeIf { it.signum() > 0 } ?: return null
            DEFAULT_USD.divide(rate, MathContext.DECIMAL64)
        }
        val limit = corridor.limitFor(currency)
        var amount = friendlyAmount(raw)
        limit?.min?.let { if (amount < it) amount = it }
        limit?.max?.let { if (amount > it) amount = it }
        return amount.stripTrailingZeros()
    }

    /** Prices a small amount from [currency] into USD on this route to learn the rate. */
    private suspend fun usdRate(s: SendUiState, corridor: Corridor, currency: String): BigDecimal? {
        usdRates[corridor.id to currency]?.let { return it }
        if (USD !in corridor.receiveCurrencies) return null
        val delivery = s.deliveryMethod ?: return null
        val probe = QuoteRequest(
            sendCountry = corridor.origin.iso2,
            receiveCountry = corridor.destination.iso2,
            sendCurrency = currency,
            receiveCurrency = USD,
            sendAmount = corridor.limitFor(currency)?.min ?: PROBE_AMOUNT,
            deliveryMethod = delivery.code,
            paymentMethod = s.paymentMethod?.code,
            fastArrival = false,
        )
        val quote = (transferRepository.quote(probe) as? ApiResult.Success)?.value ?: return null
        return quote.customerRate.also { usdRates[corridor.id to currency] = it }
    }

    fun setFastArrival(enabled: Boolean) {
        _state.update { it.copy(fastArrival = enabled) }
        reprice()
    }

    fun retryQuote() {
        val request = quoteRequest.value ?: return
        viewModelScope.launch { fetchQuote(request) }
    }

    /** Recomputes what to price from the current input; the old quote no longer applies. */
    private fun reprice() {
        val s = _state.value
        val amount = parseAmountInput(s.amountInput)
        val amountError = amount?.let { limitError(s, it) }
        val corridor = s.corridor
        val request = if (amount != null && amountError == null && corridor != null &&
            s.sendCurrency != null && s.receiveCurrency != null && s.deliveryMethod != null
        ) {
            QuoteRequest(
                sendCountry = corridor.origin.iso2,
                receiveCountry = corridor.destination.iso2,
                sendCurrency = s.sendCurrency,
                receiveCurrency = s.receiveCurrency,
                sendAmount = amount,
                deliveryMethod = s.deliveryMethod.code,
                paymentMethod = s.paymentMethod?.code,
                fastArrival = s.fastArrival,
            )
        } else {
            null
        }
        if (request != null && request == quoteRequest.value) {
            // Nothing that affects the price changed; keep the quote (or the quote in flight).
            _state.update { it.copy(amountError = amountError) }
            return
        }
        _state.update {
            it.copy(
                amountError = amountError,
                quote = null,
                quoteError = null,
                quoting = request != null,
            )
        }
        quoteRequest.value = request
    }

    private fun limitError(s: SendUiState, amount: BigDecimal): String? {
        val currency = s.sendCurrency ?: return null
        val limit = s.corridor?.limitFor(currency) ?: return null
        limit.min?.let { if (amount < it) return "The least you can send on this route is ${Money(it, currency).format()}" }
        limit.max?.let { if (amount > it) return "The most you can send on this route is ${Money(it, currency).format()}" }
        return null
    }

    private suspend fun fetchQuote(request: QuoteRequest): ApiResult<Quote> {
        _state.update { it.copy(quoting = true, quoteError = null) }
        val result = transferRepository.quote(request)
        // A newer request supersedes this one; collectLatest cancels us, but retry doesn't.
        if (quoteRequest.value != request) return result
        when (result) {
            is ApiResult.Success -> _state.update { it.copy(quote = result.value, quoting = false) }
            is ApiResult.Failure -> _state.update { it.copy(quote = null, quoting = false, quoteError = result.error.message) }
        }
        return result
    }

    // --- recipient -----------------------------------------------------------------

    fun loadRecipients(select: Long? = null) {
        viewModelScope.launch {
            val result = beneficiaryRepository.beneficiaries()
            _state.update { it.copy(recipients = result.toLoadState()) }
            if (select != null) selectRecipient(select)
        }
    }

    fun selectRecipient(id: Long) {
        _state.update { s ->
            if (s.readyRecipients.any { it.first.id == id }) s.copy(recipientId = id) else s
        }
    }

    fun onRecipientQueryChange(query: String) = _state.update { it.copy(recipientQuery = query) }

    /** AddRecipient saved someone (or a new way for them): refresh and pick them. */
    fun onRecipientSaved(id: Long) = loadRecipients(select = id)

    // --- details ---------------------------------------------------------------------

    fun selectPurpose(purpose: SendingPurpose) = _state.update { it.copy(purpose = purpose) }

    fun uploadDocument(type: TransferDocumentType, contentUri: String) {
        _state.update { it.copy(uploading = true, documentError = null) }
        viewModelScope.launch {
            when (val result = documentRepository.upload(type, contentUri)) {
                is ApiResult.Success -> _state.update { it.copy(uploading = false, documents = it.documents + result.value) }
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        uploading = false,
                        documentError = (result.error as? ApiError.Validation)?.let { v ->
                            v.firstErrorFor("file") ?: v.firstErrorFor("document_type_code") ?: v.message
                        } ?: result.error.message,
                    )
                }
            }
        }
    }

    fun removeDocument(id: Long) = _state.update { s -> s.copy(documents = s.documents.filterNot { it.id == id }) }

    // --- steps & submit ----------------------------------------------------------------

    fun next() {
        val s = _state.value
        if (!s.canContinue) return
        val nextStep = SendStep.entries.getOrNull(s.step.ordinal + 1) ?: return
        _state.update { it.copy(step = nextStep, submitError = null, repriced = false) }
    }

    /** Returns false when already on the first step, so the screen should close. */
    fun back(): Boolean {
        val s = _state.value
        val previous = SendStep.entries.getOrNull(s.step.ordinal - 1) ?: return false
        _state.update { it.copy(step = previous, submitError = null) }
        return true
    }

    fun confirm() {
        val s = _state.value
        if (s.step != SendStep.Review || !s.canContinue) return
        val quote = s.quote ?: return
        val recipient = s.selectedRecipient?.first ?: return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, submitError = null) }

            if (quote.isExpired(clock.instant())) {
                // Never send on a lapsed price: re-quote and let the customer look again.
                val request = quoteRequest.value
                if (request == null) {
                    _state.update { it.copy(submitting = false, submitError = "This quote has expired. Go back and check the amount.") }
                    return@launch
                }
                val fresh = fetchQuote(request)
                _state.update {
                    it.copy(
                        submitting = false,
                        repriced = fresh is ApiResult.Success,
                        submitError = (fresh as? ApiResult.Failure)?.error?.message,
                    )
                }
                return@launch
            }

            when (val result = transferRepository.create(quote.reference, recipient.id, s.purpose, s.documents.map { it.id })) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false) }
                    _events.send(SendEvent.Sent(result.value.reference))
                }
                is ApiResult.Failure -> _state.update { it.copy(submitting = false, submitError = result.error.message) }
            }
        }
    }

    companion object {
        const val QUOTE_DEBOUNCE_MS = 450L
        const val USD = "USD"
        val DEFAULT_USD = BigDecimal("100")
        private val PROBE_AMOUNT = BigDecimal("1000")

        /** Two significant figures, whole units: 723.4 → 720, 12,903 → 13,000. */
        internal fun friendlyAmount(raw: BigDecimal): BigDecimal {
            val rounded = raw.round(MathContext(2, RoundingMode.HALF_UP))
            return if (rounded >= BigDecimal.ONE) rounded.setScale(0, RoundingMode.HALF_UP) else rounded
        }
    }
}

/** Name, or any saved payout detail (account number, phone, bank), ignoring case and spacing. */
internal fun Beneficiary.matches(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    if (q in fullName.lowercase()) return true
    val compact = q.filterNot { it.isWhitespace() || it == '-' }
    // "0700 111" should find "+254700111222": compare digits, without a local leading 0.
    val digits = compact.takeIf { c -> c.all { it.isDigit() || it == '+' } }?.filter { it.isDigit() }?.trimStart('0')
    return ways.any { way ->
        way.deliveryMethodName.lowercase().contains(q) ||
            way.fields.values.any { v ->
                val value = v.lowercase().filterNot { it.isWhitespace() || it == '-' }
                compact in value || (!digits.isNullOrEmpty() && digits.length >= 3 && digits in value.filter { it.isDigit() })
            }
    }
}
