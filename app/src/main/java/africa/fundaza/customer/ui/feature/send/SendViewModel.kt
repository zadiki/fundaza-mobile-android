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
import africa.fundaza.customer.domain.model.GoodsDraft
import africa.fundaza.customer.domain.model.GoodsRequirement
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class SendStep(val title: String) {
    Amount("How much?"),
    Recipient("Who to?"),
    Details("Why?"),
    Review("Check and pay"),
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
    /** The customer tapped a way to pay; from then on it's theirs, never swapped for a default. */
    val paymentPicked: Boolean = false,
    /** How their last transfer was paid; the default whenever this route takes it. */
    val recentPaymentCode: String? = null,
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

    // Goods: a payment for goods to a company in China lists what it pays for, and the
    // company receives the goods' total, so the price is asked on it.
    /** Null until asked for the chosen recipient, or when it could not be told. */
    val goodsRequirement: GoodsRequirement? = null,
    val checkingGoods: Boolean = false,
    val goods: GoodsDraft = GoodsDraft(),
    val readingInvoice: Boolean = false,
    val invoiceNote: String? = null,
    /** Today where the customer is, for the sale date. */
    val today: LocalDate = LocalDate.now(),

    // Review
    val submitting: Boolean = false,
    val submitError: String? = null,
    /** Paying from the wallet: the password sheet is open. */
    val passwordPrompt: Boolean = false,
    val passwordError: String? = null,
    /** The quote lapsed on the review screen and was priced again. */
    val repriced: Boolean = false,
) {
    val destination: String? get() = corridor?.destination?.iso2

    /** Ways to pay that take the chosen send currency (M-Pesa takes KES only, for instance). */
    val paymentChoices: List<Method>
        get() = methods?.valueOrNull?.payment.orEmpty().filter { it.takes(sendCurrency) }

    val paysFromWallet: Boolean get() = paymentMethod?.isWallet == true

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

    val goodsNeeded: Boolean get() = goodsRequirement?.needed == true

    /** What the goods add up to, in the payout currency; null until every line is priced. */
    val goodsTotal: BigDecimal? get() = if (goodsNeeded) goods.total else null

    /** The price held pays the company exactly the goods' total (or no goods are needed). */
    val goodsPriced: Boolean
        get() {
            if (!goodsNeeded) return true
            val total = goodsTotal ?: return false
            return quote != null && quote.receiveAmount.amount.compareTo(total) == 0
        }

    /** The latest invoice attached, the one the goods are read from. */
    val latestInvoiceId: Long? get() = documents.lastOrNull { it.typeCode == TransferDocumentType.INVOICE.name }?.id

    val canContinue: Boolean
        get() = when (step) {
            SendStep.Amount -> quote != null && !quoting && amountError == null && quoteError == null
            SendStep.Recipient -> selectedRecipient != null
            SendStep.Details -> purpose != null && !uploading && (!documentsRequired || documents.isNotEmpty()) &&
                (!goodsNeeded || (goods.complete(today) && goodsPriced && !quoting))
            SendStep.Review -> quote != null && selectedRecipient != null && !submitting && !quoting && goodsPriced
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

    private val _state = MutableStateFlow(SendUiState(today = LocalDate.now(clock.withZone(ZoneId.systemDefault()))))
    val state: StateFlow<SendUiState> = _state.asStateFlow()

    private val _events = Channel<SendEvent>(Channel.BUFFERED)
    val events: Flow<SendEvent> = _events.receiveAsFlow()

    /**
     * What the next quote should price; null while the input can't be priced. With no way to pay
     * settled, [Pricing.compare] lists the ways to price side by side, and the cheapest is taken.
     */
    private val pricing = MutableStateFlow<Pricing?>(null)

    private var prefillJob: Job? = null

    /** USD per one unit of a send currency, per corridor; learnt from a probe quote. */
    private val usdRates = mutableMapOf<Pair<Long, String>, BigDecimal>()

    init {
        loadCorridors()
        loadRecipients()
        viewModelScope.launch { loadRecentPayment() }
        viewModelScope.launch {
            pricing
                .debounce(QUOTE_DEBOUNCE_MS)
                .collectLatest { p -> if (p != null) fetchQuote(p) }
        }
    }

    /** How the customer paid their latest transfer (the list is newest first). */
    private suspend fun loadRecentPayment() {
        val latest = (transferRepository.transfers() as? ApiResult.Success)?.value?.firstOrNull() ?: return
        val code = (transferRepository.transfer(latest.reference) as? ApiResult.Success)?.value?.paymentMethodCode ?: return
        _state.update { it.copy(recentPaymentCode = code) }
        reprice()
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
                )
            }
            reprice()
            prefillAmount()
        }
    }

    fun selectSendCurrency(code: String) {
        _state.update {
            it.copy(
                sendCurrency = code,
                amountInput = if (it.amountEdited) it.amountInput else "",
            )
        }
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
        if (!method.available) return
        _state.update { it.copy(paymentMethod = method, paymentPicked = true) }
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
        val p = pricing.value ?: return
        viewModelScope.launch { fetchQuote(p) }
    }

    /** Recomputes what to price from the current input; the old quote no longer applies. */
    private fun reprice() {
        // A way to pay that doesn't take this route or currency is no choice at all.
        val compare = settlePayment()
        val s = _state.value
        // Past the amount, a payment for goods is priced on the goods' total: what the company receives.
        val goodsTotal = s.goodsTotal?.takeIf { s.step >= SendStep.Details }
        val amount = parseAmountInput(s.amountInput) ?: goodsTotal?.let { s.quote?.sendAmount?.amount }
        val amountError = if (goodsTotal != null) null else amount?.let { limitError(s, it) }
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
                paymentMethod = if (compare.isEmpty()) s.paymentMethod?.code else null,
                fastArrival = s.fastArrival,
                receiveAmount = goodsTotal,
            )
        } else {
            null
        }
        val next = request?.let { Pricing(it, compare.map(Method::code)) }
        if (next != null && next == pricing.value) {
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
        pricing.value = next
    }

    /**
     * Settles the way to pay, in order: what the customer tapped, how they paid last time, the
     * only option there is. Returns the options to price against each other when none of those
     * settles it (the cheapest wins; the wallet is never compared in, spending a balance is the
     * customer's call); empty when the way to pay is settled.
     */
    private fun settlePayment(): List<Method> {
        val s = _state.value
        val usable = s.paymentChoices.filter { it.available }
        val settled = s.paymentMethod?.takeIf { s.paymentPicked }?.let { m -> usable.firstOrNull { it.code == m.code } }
            ?: usable.firstOrNull { it.code == s.recentPaymentCode }
        val candidates = usable.filterNot { it.isWallet }.ifEmpty { usable }
        val shown = settled
            ?: candidates.singleOrNull()
            // While the comparison runs, keep what is showing if it's still in the running.
            ?: s.paymentMethod?.let { m -> candidates.firstOrNull { it.code == m.code } }
            ?: candidates.firstOrNull()
        if (shown != s.paymentMethod) _state.update { it.copy(paymentMethod = shown) }
        return if (settled == null && candidates.size > 1) candidates else emptyList()
    }

    private fun limitError(s: SendUiState, amount: BigDecimal): String? {
        val currency = s.sendCurrency ?: return null
        val limit = s.corridor?.limitFor(currency) ?: return null
        limit.min?.let { if (amount < it) return "The least you can send on this route is ${Money(it, currency).format()}" }
        limit.max?.let { if (amount > it) return "The most you can send on this route is ${Money(it, currency).format()}" }
        return null
    }

    private suspend fun fetchQuote(p: Pricing): ApiResult<Quote> {
        _state.update { it.copy(quoting = true, quoteError = null) }
        var cheapest: String? = null
        val result = if (p.compare.isEmpty()) {
            transferRepository.quote(p.request)
        } else {
            val priced = coroutineScope {
                p.compare.map { code -> async { code to transferRepository.quote(p.request.copy(paymentMethod = code)) } }.awaitAll()
            }
            // Ties go to the route's own order.
            val best = priced
                .mapNotNull { (code, r) -> (r as? ApiResult.Success)?.let { code to it } }
                .minByOrNull { it.second.value.totalPayable.amount }
            cheapest = best?.first
            best?.second ?: priced.first().second
        }
        // A newer request supersedes this one; collectLatest cancels us, but retry doesn't.
        if (pricing.value != p) return result
        when (result) {
            is ApiResult.Success -> _state.update { s ->
                s.copy(
                    quote = result.value,
                    quoting = false,
                    // Priced on the goods: the send box shows what that costs.
                    amountInput = if (p.request.receiveAmount != null) {
                        result.value.sendAmount.amount.stripTrailingZeros().toPlainString()
                    } else {
                        s.amountInput
                    },
                    paymentMethod = cheapest?.let { code -> s.paymentChoices.firstOrNull { it.code == code } } ?: s.paymentMethod,
                )
            }
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
            when {
                s.readyRecipients.none { it.first.id == id } -> s
                // Whether goods are needed is asked again for whoever is chosen.
                s.recipientId != id -> s.copy(recipientId = id, goodsRequirement = null)
                else -> s
            }
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
                is ApiResult.Success -> {
                    _state.update { it.copy(uploading = false, documents = it.documents + result.value) }
                    if (_state.value.goodsNeeded && type == TransferDocumentType.INVOICE) readInvoice(result.value.id)
                }
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

    // --- goods -------------------------------------------------------------------------

    /** Asks whether this send must list its goods; called on reaching Details. */
    private fun loadGoodsRequirement() {
        val s = _state.value
        val quote = s.quote ?: return
        val recipient = s.selectedRecipient?.first ?: return
        if (s.goodsRequirement != null) {
            reprice()
            return
        }
        _state.update { it.copy(checkingGoods = true) }
        viewModelScope.launch {
            val result = transferRepository.goodsRequirement(quote.reference, recipient.id)
            if (_state.value.recipientId != recipient.id) return@launch
            val requirement = (result as? ApiResult.Success)?.value
            _state.update {
                it.copy(
                    checkingGoods = false,
                    // Unknown counts as not needed: the server still refuses a send without them.
                    goodsRequirement = requirement,
                    // A company in China is paid only for goods.
                    purpose = if (requirement?.needed == true && it.purpose == null) SendingPurpose.TRADE_PAYMENT else it.purpose,
                    // Moved on before the answer came: the goods are listed on Details.
                    step = if (requirement?.needed == true && it.step > SendStep.Details) SendStep.Details else it.step,
                )
            }
            reprice()
        }
    }

    /** The customer changed the goods; a new total is priced once it settles. */
    fun updateGoods(change: (GoodsDraft) -> GoodsDraft) {
        _state.update { it.copy(goods = change(it.goods)) }
        reprice()
    }

    /** Fills in what the customer left empty from an attached PDF invoice; they check it. */
    fun readInvoice(documentId: Long? = _state.value.latestInvoiceId) {
        val id = documentId ?: return
        val currency = _state.value.goodsRequirement?.currency ?: return
        _state.update { it.copy(readingInvoice = true, invoiceNote = null) }
        viewModelScope.launch {
            val invoice = (documentRepository.invoiceGoods(id) as? ApiResult.Success)?.value
            if (invoice == null || !invoice.read || (invoice.merchantName == null && invoice.tradeDate == null && invoice.lines.isEmpty())) {
                _state.update { it.copy(readingInvoice = false, invoiceNote = UNREADABLE_INVOICE) }
                return@launch
            }
            _state.update { s ->
                val note = buildString {
                    append(
                        if (s.goods.touched) {
                            "Filled in what you had left empty from the invoice. Check every field before you send."
                        } else {
                            "Filled in from the invoice. Check every field, and choose a category for each product."
                        },
                    )
                    if (invoice.currencyCode != null && invoice.currencyCode != currency) {
                        append(" The invoice is priced in ${invoice.currencyCode}. Enter each price in $currency.")
                    }
                }
                s.copy(readingInvoice = false, goods = s.goods.withInvoice(invoice, currency), invoiceNote = note)
            }
            reprice()
        }
    }

    // --- steps & submit ----------------------------------------------------------------

    fun next() {
        val s = _state.value
        if (!s.canContinue) return
        val nextStep = SendStep.entries.getOrNull(s.step.ordinal + 1) ?: return
        _state.update { it.copy(step = nextStep, submitError = null, repriced = false) }
        if (nextStep == SendStep.Details) loadGoodsRequirement()
    }

    /** Returns false when already on the first step, so the screen should close. */
    fun back(): Boolean {
        val s = _state.value
        val previous = SendStep.entries.getOrNull(s.step.ordinal - 1) ?: return false
        _state.update { it.copy(step = previous, submitError = null) }
        return true
    }

    /** Sends, or — paying from the wallet — first asks for the password. */
    fun confirm() {
        val s = _state.value
        if (s.step != SendStep.Review || !s.canContinue) return
        if (s.paysFromWallet) {
            _state.update { it.copy(passwordPrompt = true, passwordError = null) }
            return
        }
        submit(password = null)
    }

    /** The wallet's money moves only once the customer has said their password again. */
    fun confirmWithPassword(password: String) {
        if (password.isBlank()) {
            _state.update { it.copy(passwordError = "Enter your password.") }
            return
        }
        submit(password)
    }

    fun dismissPasswordPrompt() {
        if (_state.value.submitting) return
        _state.update { it.copy(passwordPrompt = false, passwordError = null) }
    }

    private fun submit(password: String?) {
        val s = _state.value
        if (s.step != SendStep.Review || !s.canContinue) return
        val quote = s.quote ?: return
        val recipient = s.selectedRecipient?.first ?: return

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, submitError = null, passwordError = null) }

            if (quote.isExpired(clock.instant())) {
                // Never send on a lapsed price: re-quote and let the customer look again.
                val request = pricing.value
                if (request == null) {
                    _state.update { it.copy(submitting = false, submitError = "This quote has expired. Go back and check the amount.") }
                    return@launch
                }
                val fresh = fetchQuote(request)
                _state.update {
                    it.copy(
                        submitting = false,
                        passwordPrompt = false,
                        repriced = fresh is ApiResult.Success,
                        submitError = (fresh as? ApiResult.Failure)?.error?.message,
                    )
                }
                return@launch
            }

            val goods = s.goods.takeIf { s.goodsNeeded }
            when (val result = transferRepository.create(quote.reference, recipient.id, s.purpose, s.documents.map { it.id }, password, goods, s.goodsRequirement?.currency)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(submitting = false, passwordPrompt = false) }
                    _events.send(SendEvent.Sent(result.value.reference))
                }
                is ApiResult.Failure -> {
                    val passwordProblem = (result.error as? ApiError.Validation)?.firstErrorFor("password")
                    _state.update {
                        if (passwordProblem != null && it.passwordPrompt) {
                            // Wrong password: keep the sheet open and say so there.
                            it.copy(submitting = false, passwordError = passwordProblem)
                        } else {
                            it.copy(submitting = false, passwordPrompt = false, submitError = result.error.message)
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val QUOTE_DEBOUNCE_MS = 450L
        const val UNREADABLE_INVOICE = "We could not read the goods from this invoice. Type them in below."
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

/** One pricing: the request, and the ways to pay to price against each other (none when settled). */
private data class Pricing(val request: QuoteRequest, val compare: List<String> = emptyList())
