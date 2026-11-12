package africa.fundaza.customer.testing

import africa.fundaza.customer.core.money.Money
import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.repository.AuthRepository
import africa.fundaza.customer.data.repository.BeneficiaryRepository
import africa.fundaza.customer.data.repository.CorridorRepository
import africa.fundaza.customer.data.repository.CustomerRepository
import africa.fundaza.customer.core.location.CountryLocator
import africa.fundaza.customer.data.repository.DocumentRepository
import africa.fundaza.customer.data.repository.SignInResult
import africa.fundaza.customer.data.repository.TransferRepository
import africa.fundaza.customer.domain.model.AmountLimit
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.domain.model.BeneficiaryField
import africa.fundaza.customer.domain.model.BeneficiaryWay
import africa.fundaza.customer.domain.model.Corridor
import africa.fundaza.customer.domain.model.CorridorMethods
import africa.fundaza.customer.domain.model.Country
import africa.fundaza.customer.domain.model.CurrentUser
import africa.fundaza.customer.domain.model.Customer
import africa.fundaza.customer.domain.model.Kyc
import africa.fundaza.customer.domain.model.Method
import africa.fundaza.customer.domain.model.NewBeneficiary
import africa.fundaza.customer.domain.model.NewWay
import africa.fundaza.customer.domain.model.Quote
import africa.fundaza.customer.domain.model.QuoteRequest
import africa.fundaza.customer.domain.model.SendingPurpose
import africa.fundaza.customer.domain.model.Transfer
import africa.fundaza.customer.domain.model.TransferDetail
import africa.fundaza.customer.domain.model.TransferDocument
import africa.fundaza.customer.domain.model.TransferDocumentType
import africa.fundaza.customer.domain.model.TransferStatus
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(var now: Instant = Instant.parse("2026-11-11T10:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}

val CN_KE = Corridor(
    id = 3,
    code = "CN-KE-01",
    origin = Country("CN", "China"),
    destination = Country("KE", "Kenya"),
    sendCurrency = "CNY",
    receiveCurrency = "KES",
    sendCurrencies = listOf("CNY", "USD"),
    receiveCurrencies = listOf("KES"),
    limits = listOf(AmountLimit("CNY", BigDecimal("1000"), BigDecimal("500000"))),
    quoteValiditySeconds = 900,
)

val MOBILE_MONEY = Method("MOBILE_MONEY", "Mobile money")
val BANK_TRANSFER = Method("BANK_TRANSFER", "Bank transfer")
val MPESA_PAYIN = Method("MOBILE_MONEY", "Mobile money", currencies = listOf("KES"))
fun walletMethod(available: Boolean = true) =
    Method("WALLET", "Wallet", available = available, balance = Money(BigDecimal("50000"), "CNY"))

class FakeCorridorRepository(
    var corridors: List<Corridor> = listOf(CN_KE),
    var methods: CorridorMethods = CorridorMethods(payment = listOf(BANK_TRANSFER), delivery = listOf(MOBILE_MONEY)),
) : CorridorRepository {
    override suspend fun corridors(forceRefresh: Boolean) = ApiResult.Success(corridors)
    override suspend fun methods(corridorId: Long) = ApiResult.Success(methods)
    override suspend fun timeUntilStale(): java.time.Duration = java.time.Duration.ofHours(3)
    override suspend fun clear() = Unit
}

class FakeTransferRepository(private val clock: Clock) : TransferRepository {
    val quoteRequests = mutableListOf<QuoteRequest>()
    val created = mutableListOf<Triple<String, Long, SendingPurpose?>>()
    val passwords = mutableListOf<String?>()
    /** Only this password passes a wallet send; any other is refused on the field, as the server does. */
    var walletPassword: String = "right password"
    var quoteFailure: ApiError? = null
    var createFailure: ApiError? = null
    /** Receive units per send unit, per (send, receive) currency pair; 17.82 otherwise. */
    var rates: Map<Pair<String, String>, BigDecimal> = emptyMap()

    override suspend fun quote(request: QuoteRequest): ApiResult<Quote> {
        quoteRequests += request
        quoteFailure?.let { return ApiResult.Failure(it) }
        val rate = rates[request.sendCurrency to request.receiveCurrency] ?: BigDecimal("17.82")
        return ApiResult.Success(
            Quote(
                reference = "QT-${quoteRequests.size}",
                corridorId = 3,
                sendAmount = Money(request.sendAmount, request.sendCurrency),
                receiveAmount = Money(request.sendAmount * rate, request.receiveCurrency),
                totalPayable = Money(request.sendAmount + BigDecimal("50"), request.sendCurrency),
                customerRate = rate,
                charges = emptyList(),
                fastArrivalChosen = request.fastArrival,
                fastArrivalPrice = null,
                expiresAt = clock.instant().plusSeconds(900),
            ),
        )
    }

    override suspend fun create(
        quoteReference: String,
        beneficiaryId: Long,
        purpose: SendingPurpose?,
        documentIds: List<Long>,
        password: String?,
    ): ApiResult<Transfer> {
        passwords += password
        if (password != null && password != walletPassword) {
            return ApiResult.Failure(
                ApiError.Validation("That password is not right. Try again.", mapOf("password" to listOf("That password is not right. Try again."))),
            )
        }
        created += Triple(quoteReference, beneficiaryId, purpose)
        createFailure?.let { return ApiResult.Failure(it) }
        val money = Money(BigDecimal.ONE, "CNY")
        return ApiResult.Success(Transfer("TXN-1", TransferStatus.AWAITING_PAYMENT, false, money, money, money, BigDecimal.ONE, beneficiaryId, null, emptyList()))
    }

    override suspend fun transfers(): ApiResult<List<Transfer>> = ApiResult.Success(emptyList())
    override suspend fun transfer(reference: String): ApiResult<TransferDetail> = ApiResult.Failure(ApiError.NotFound())
    override suspend fun startMpesa(reference: String, phone: String?): ApiResult<String> = ApiResult.Success(phone ?: "254700111222")
}

fun beneficiary(id: Long, name: String, vararg ways: BeneficiaryWay) =
    Beneficiary(id, name, ways.firstOrNull()?.countryIso2 ?: "KE", "KES", "VERIFIED", ways.toList())

fun way(id: Long, country: String = "KE", method: String = "MOBILE_MONEY", currency: String = "KES") =
    BeneficiaryWay(id, country, method, method, currency, mapOf("wallet_msisdn" to "+254700111222"))

class FakeBeneficiaryRepository(var list: List<Beneficiary> = emptyList()) : BeneficiaryRepository {
    override suspend fun beneficiaries() = ApiResult.Success(list)
    override suspend fun beneficiary(id: Long): ApiResult<Beneficiary> =
        list.firstOrNull { it.id == id }?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiError.NotFound())
    override suspend fun fields(countryIso2: String, deliveryMethod: String) = ApiResult.Success(emptyList<BeneficiaryField>())
    override suspend fun create(new: NewBeneficiary): ApiResult<Beneficiary> = error("unused")
    override suspend fun addWay(beneficiaryId: Long, way: NewWay): ApiResult<Beneficiary> = error("unused")
    override suspend fun delete(id: Long) = ApiResult.Success(Unit)
}

class FakeDocumentRepository : DocumentRepository {
    override suspend fun upload(type: TransferDocumentType, contentUri: String) =
        ApiResult.Success(TransferDocument(41, type.name, type.label, "invoice.pdf", "PENDING"))
}

class FakeAuthRepository : AuthRepository {
    var result: ApiResult<SignInResult> = ApiResult.Success(SignInResult.SignedIn(customerUser()))
    val attempts = mutableListOf<Pair<String, String>>()
    override suspend fun restore() = Unit
    override suspend fun signIn(email: String, password: String): ApiResult<SignInResult> {
        attempts += email to password
        return result
    }
    override suspend fun signOut() = Unit
    override suspend fun refreshUser(): ApiResult<CurrentUser> = ApiResult.Success(customerUser())
}

fun customerUser(permissions: Set<String> = setOf("txn.view", "txn.create")) =
    CurrentUser(17, "Grace Wanjiru", "grace@example.com", "CUSTOMER", 12, listOf("CUSTOMER"), permissions)

class FakeCountryLocator(var country: String? = null) : CountryLocator {
    override suspend fun currentCountry() = country
}

class FakeCustomerRepository(var residenceIso2: String = "CN") : CustomerRepository {
    override suspend fun customer() = ApiResult.Success(
        Customer("CUS-1", "INDIVIDUAL", "Grace Wanjiru", "grace@example.com", null, residenceIso2, "ACTIVE", Kyc("VERIFIED", true, 1, null)),
    )
}
