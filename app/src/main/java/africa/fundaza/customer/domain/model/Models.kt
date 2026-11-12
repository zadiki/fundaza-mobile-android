package africa.fundaza.customer.domain.model

import africa.fundaza.customer.core.money.Money
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** Action codes from the server's permission matrix that this app acts on. */
object Permissions {
    const val TXN_VIEW = "txn.view"
    const val TXN_CREATE = "txn.create"
}

data class CurrentUser(
    val id: Long,
    val name: String,
    val email: String,
    val principalType: String?,
    val principalId: Long?,
    val roles: List<String>,
    val permissions: Set<String>,
) {
    val isCustomer: Boolean get() = principalType == "CUSTOMER"
    fun can(action: String): Boolean = action in permissions
}

data class Kyc(
    val status: String,
    /** Derived from expiry by the server — use this, not [status]. */
    val verified: Boolean,
    val tier: Int?,
    val expiresAt: LocalDate?,
)

data class Customer(
    val reference: String,
    val type: String,
    val name: String,
    val email: String,
    val phone: String?,
    val residenceIso2: String,
    val status: String,
    val kyc: Kyc,
)

data class Country(val iso2: String, val name: String)

data class AmountLimit(val currency: String, val min: BigDecimal?, val max: BigDecimal?)

data class Corridor(
    val id: Long,
    val code: String,
    val origin: Country,
    val destination: Country,
    val sendCurrency: String,
    val receiveCurrency: String,
    val sendCurrencies: List<String>,
    val receiveCurrencies: List<String>,
    val limits: List<AmountLimit>,
    val quoteValiditySeconds: Int?,
) {
    fun limitFor(currency: String): AmountLimit? = limits.firstOrNull { it.currency == currency }
}

data class Method(
    val code: String,
    val name: String,
    /** The send currencies this method takes; null means any the route does. */
    val currencies: List<String>? = null,
    /** False for a wallet that can't pay: empty, or not in this currency. */
    val available: Boolean = true,
    /** Wallet only: what is in it. */
    val balance: Money? = null,
) {
    val isWallet: Boolean get() = code == WALLET

    fun takes(currency: String?): Boolean = currencies == null || currency == null || currency in currencies

    companion object {
        const val WALLET = "WALLET"
        const val MOBILE_MONEY = "MOBILE_MONEY"
    }
}

data class CorridorMethods(val payment: List<Method>, val delivery: List<Method>)

enum class ChargeType { TRANSFER_FEE, PROCESSING_FEE, PARTNER_FEE, AGENT_FEE, PAYMENT_METHOD_FEE, DELIVERY_METHOD_FEE, FAST_ARRIVAL, TAX, OTHER }

data class Charge(val type: ChargeType, val label: String, val amount: Money)

data class Quote(
    val reference: String,
    val corridorId: Long,
    val sendAmount: Money,
    val receiveAmount: Money,
    val totalPayable: Money,
    /** What the customer was given — the one rate to show. */
    val customerRate: BigDecimal,
    val charges: List<Charge>,
    val fastArrivalChosen: Boolean,
    /** What fast arrival would add; null when the route offers no choice. */
    val fastArrivalPrice: Money?,
    val expiresAt: Instant,
) {
    fun isExpired(now: Instant = Instant.now()): Boolean = !now.isBefore(expiresAt)
}

data class QuoteRequest(
    val sendCountry: String,
    val receiveCountry: String,
    val sendCurrency: String,
    val receiveCurrency: String,
    val sendAmount: BigDecimal,
    val deliveryMethod: String,
    val paymentMethod: String?,
    val fastArrival: Boolean,
)

data class BeneficiaryField(
    val key: String,
    val label: String,
    val dataType: String,
    val required: Boolean,
    /** Server-supplied; applied as given so operations can fix it without a release. */
    val pattern: String?,
) {
    /** Null when [value] is acceptable, otherwise what to tell the customer. */
    fun validate(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return if (required) "$label is required" else null
        val regex = pattern?.let { runCatching { Regex(it) }.getOrNull() } ?: return null
        return if (regex.containsMatchIn(trimmed)) null else "Enter a valid ${label.lowercase()}"
    }
}

/** One way a beneficiary can receive: a country, delivery method and payout currency. */
data class BeneficiaryWay(
    val id: Long,
    val countryIso2: String,
    val deliveryMethodCode: String,
    val deliveryMethodName: String,
    val currency: String,
    val fields: Map<String, String>,
) {
    fun matches(country: String, deliveryMethod: String, currency: String): Boolean =
        countryIso2 == country && deliveryMethodCode == deliveryMethod && this.currency == currency

    /** The detail a person would recognise this way by: a phone or account number. */
    val summary: String
        get() = fields.entries.firstOrNull { (k, _) -> k.contains("msisdn") || k.contains("account") || k.contains("number") }?.value
            ?: fields.values.firstOrNull().orEmpty()
}

data class Beneficiary(
    val id: Long,
    val fullName: String,
    val countryIso2: String,
    val currency: String,
    val verificationStatus: String,
    val ways: List<BeneficiaryWay>,
) {
    fun wayFor(country: String, deliveryMethod: String, currency: String): BeneficiaryWay? =
        ways.firstOrNull { it.matches(country, deliveryMethod, currency) }

    val initials: String
        get() = fullName.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
}

enum class RecipientType(val code: String, val label: String) {
    PERSON("PERSON", "A person"),
    BUSINESS("BUSINESS", "A business"),
}

/** Who the recipient is to the sender, as the server's enum spells it. */
enum class Relationship(val label: String, val forPerson: Boolean, val forBusiness: Boolean) {
    SELF("Myself", true, false),
    SPOUSE("Spouse", true, false),
    PARENT("Parent", true, false),
    CHILD("Child", true, false),
    SIBLING("Brother or sister", true, false),
    PATERNAL_GRANDPARENT("Paternal grandparent", true, false),
    MATERNAL_GRANDPARENT("Maternal grandparent", true, false),
    PATERNAL_GRANDCHILD("Paternal grandchild", true, false),
    MATERNAL_GRANDCHILD("Maternal grandchild", true, false),
    OTHER_RELATIVE("Other relative", true, false),
    FRIEND("Friend", true, false),
    EMPLOYEE("Employee", true, false),
    BUSINESS_PARTNER("Business partner", true, true),
    OWN_BUSINESS("My own business", false, true),
    SUPPLIER("Supplier", false, true),
    CLIENT("Client", false, true),
    OTHER("Other", true, true);

    companion object {
        fun forType(type: RecipientType): List<Relationship> = entries.filter {
            if (type == RecipientType.BUSINESS) it.forBusiness else it.forPerson
        }
    }
}

data class NewBeneficiary(
    val fullName: String,
    val type: RecipientType,
    val relationship: Relationship?,
    val countryIso2: String,
    val currency: String?,
    val deliveryMethod: String?,
    val fields: Map<String, String>,
)

data class NewWay(
    val deliveryMethod: String,
    val countryIso2: String?,
    val currency: String?,
    val fields: Map<String, String>,
)

/** Why the customer is sending, as the server's sending_purposes table seeds it. */
enum class SendingPurpose(val label: String, val requiresDocument: Boolean) {
    FAMILY_SUPPORT("Family support", false),
    EDUCATION("Education", false),
    MEDICAL("Medical", false),
    SALARY("Salary", false),
    TRADE_PAYMENT("Trade payment", true),
    INVESTMENT("Investment", true),
}

/** Document types a customer can attach to a transfer (owner_type TRANSACTION). */
enum class TransferDocumentType(val label: String) {
    INVOICE("Invoice"),
    SOURCE_OF_FUNDS("Source of funds evidence"),
    SALE_AGREEMENT("Sale agreement"),
    BILL_OF_LADING("Bill of lading or logistics document"),
}

data class TransferDocument(
    val id: Long,
    val typeCode: String,
    val typeName: String,
    val fileName: String,
    val status: String,
)

enum class StatusTone { InProgress, Success, Problem, Returned }

enum class TransferStatus(val label: String, val tone: StatusTone) {
    DRAFT("Draft", StatusTone.InProgress),
    QUOTED("Quoted", StatusTone.InProgress),
    AWAITING_PAYMENT("Waiting for your payment", StatusTone.InProgress),
    PAYMENT_CONFIRMED("Payment received", StatusTone.InProgress),
    SENT_TO_PARTNER("With payout partner", StatusTone.InProgress),
    PROCESSING("Being paid out", StatusTone.InProgress),
    PAID("Paid out", StatusTone.Success),
    COMPLETED("Completed", StatusTone.Success),
    REJECTED("Rejected", StatusTone.Problem),
    FAILED("Failed", StatusTone.Problem),
    CANCELLED("Cancelled", StatusTone.Returned),
    REFUNDED("Refunded", StatusTone.Returned),
    REVERSED("Reversed", StatusTone.Returned),
    UNKNOWN("Updating", StatusTone.InProgress);

    companion object {
        fun from(code: String): TransferStatus = entries.firstOrNull { it.name == code } ?: UNKNOWN

        /** The path a transfer normally takes, for drawing the tracker. */
        val happyPath = listOf(AWAITING_PAYMENT, PAYMENT_CONFIRMED, SENT_TO_PARTNER, PROCESSING, PAID, COMPLETED)
    }
}

data class Transfer(
    val reference: String,
    val status: TransferStatus,
    val isFinanciallyComplete: Boolean,
    val sendAmount: Money,
    val receiveAmount: Money,
    val totalPayable: Money,
    val exchangeRate: BigDecimal,
    val beneficiaryId: Long?,
    val date: LocalDate?,
    val charges: List<Charge>,
)

data class StatusStep(val status: TransferStatus, val note: String?, val changedAt: Instant?)

/** Where and how to pay a transfer that is still awaiting payment. */
data class PayIn(
    val method: String,
    val label: String,
    /** Paybill, till or bank account. */
    val account: String,
    /** What the customer must quote as the account number / reference. */
    val reference: String,
    val amount: Money,
    val note: String,
    val businessName: String?,
) {
    val isMobileMoney: Boolean get() = method == Method.MOBILE_MONEY
}

data class TransferDetail(
    val transfer: Transfer,
    val history: List<StatusStep>,
    val paymentMethodCode: String? = null,
    val payIn: PayIn? = null,
)

data class WalletMovement(
    val incoming: Boolean,
    val amount: Money,
    val description: String,
    val transactionReference: String?,
    val postedAt: Instant?,
)

data class Wallet(val balances: List<Money>, val movements: List<WalletMovement>)
