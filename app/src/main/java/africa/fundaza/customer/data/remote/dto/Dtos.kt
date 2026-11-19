package africa.fundaza.customer.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every single resource and collection arrives wrapped as `{"data": ...}`. */
@Serializable
data class Envelope<T>(val data: T)

@Serializable
data class MoneyDto(val amount: String, val currency: String)

// --- auth ---------------------------------------------------------------

@Serializable
data class TokenRequest(
    val email: String,
    val password: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class TokenDto(
    val token: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class PrincipalDto(
    val type: String,
    val id: Long? = null,
    @SerialName("agent_ids") val agentIds: List<Long> = emptyList(),
)

@Serializable
data class CurrentUserDto(
    val id: Long,
    val username: String? = null,
    val email: String = "",
    val name: String = "",
    val principal: PrincipalDto? = null,
    val roles: List<String> = emptyList(),
    val permissions: List<String> = emptyList(),
)

// --- customer -------------------------------------------------------------

@Serializable
data class KycDto(
    val status: String = "NONE",
    val verified: Boolean = false,
    val tier: Int? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class CustomerDto(
    val reference: String,
    val type: String = "INDIVIDUAL",
    val name: String,
    val email: String = "",
    val phone: String? = null,
    @SerialName("residence_iso2") val residenceIso2: String = "",
    val status: String = "",
    val kyc: KycDto = KycDto(),
)

// --- corridors -----------------------------------------------------------

@Serializable
data class CountryDto(val iso2: String, val name: String)

@Serializable
data class LimitDto(
    val currency: String,
    @SerialName("min_amount") val minAmount: String? = null,
    @SerialName("max_amount") val maxAmount: String? = null,
)

@Serializable
data class CorridorDto(
    val id: Long,
    val code: String,
    val origin: CountryDto,
    val destination: CountryDto,
    @SerialName("send_currency_code") val sendCurrencyCode: String,
    @SerialName("receive_currency_code") val receiveCurrencyCode: String,
    @SerialName("send_currencies") val sendCurrencies: List<String> = emptyList(),
    @SerialName("receive_currencies") val receiveCurrencies: List<String> = emptyList(),
    @SerialName("min_amount") val minAmount: String? = null,
    @SerialName("max_amount") val maxAmount: String? = null,
    val limits: List<LimitDto> = emptyList(),
    @SerialName("quote_validity_seconds") val quoteValiditySeconds: Int? = null,
)

@Serializable
data class MethodDto(
    val code: String,
    val name: String,
    /** The send currencies this method takes; null means any the route does. */
    val currencies: List<String>? = null,
    /** Wallet only: whether the balance can pay. */
    val available: Boolean = true,
    /** Wallet only: what is in it, in the route's send currency. */
    val balance: MoneyDto? = null,
)

@Serializable
data class CorridorMethodsDto(
    @SerialName("payment_methods") val paymentMethods: List<MethodDto> = emptyList(),
    @SerialName("delivery_methods") val deliveryMethods: List<MethodDto> = emptyList(),
)

// --- quotes ----------------------------------------------------------------

@Serializable
data class QuoteRequestDto(
    @SerialName("send_country_iso2") val sendCountryIso2: String,
    @SerialName("receive_country_iso2") val receiveCountryIso2: String,
    @SerialName("send_currency_code") val sendCurrencyCode: String,
    @SerialName("receive_currency_code") val receiveCurrencyCode: String,
    /** A decimal string, never a number. Left out when the price is asked on [receiveAmount]. */
    @SerialName("send_amount") val sendAmount: String? = null,
    @SerialName("delivery_method_code") val deliveryMethodCode: String,
    /** `receive` prices [receiveAmount], what the recipient gets; `send` (the default) prices [sendAmount]. */
    @SerialName("amount_basis") val amountBasis: String? = null,
    @SerialName("receive_amount") val receiveAmount: String? = null,
    @SerialName("payment_method_code") val paymentMethodCode: String? = null,
    @SerialName("fast_arrival") val fastArrival: Boolean = false,
)

@Serializable
data class ChargeDto(val type: String, val label: String = "", val amount: MoneyDto)

@Serializable
data class FastArrivalDto(val chosen: Boolean = false, val price: MoneyDto? = null)

@Serializable
data class QuoteDto(
    val reference: String,
    @SerialName("corridor_id") val corridorId: Long,
    @SerialName("send_amount") val sendAmount: MoneyDto,
    @SerialName("receive_amount") val receiveAmount: MoneyDto,
    @SerialName("total_payable") val totalPayable: MoneyDto,
    @SerialName("source_rate") val sourceRate: String? = null,
    @SerialName("markup_rate") val markupRate: String? = null,
    @SerialName("customer_rate") val customerRate: String,
    val charges: List<ChargeDto> = emptyList(),
    @SerialName("fast_arrival") val fastArrival: FastArrivalDto = FastArrivalDto(),
    @SerialName("expires_at") val expiresAt: String,
)

// --- beneficiaries -----------------------------------------------------------

@Serializable
data class BeneficiaryFieldDto(
    val key: String,
    val label: String,
    @SerialName("data_type") val dataType: String = "STRING",
    val required: Boolean = false,
    val pattern: String? = null,
)

@Serializable
data class BeneficiaryWayDto(
    val id: Long,
    @SerialName("country_iso2") val countryIso2: String,
    @SerialName("delivery_method_code") val deliveryMethodCode: String,
    @SerialName("delivery_method") val deliveryMethod: String = "",
    @SerialName("currency_code") val currencyCode: String,
    @Serializable(with = LenientStringMapSerializer::class) val fields: Map<String, String> = emptyMap(),
)

@Serializable
data class BeneficiaryDto(
    val id: Long,
    @SerialName("full_name") val fullName: String,
    @SerialName("country_iso2") val countryIso2: String,
    @SerialName("currency_code") val currencyCode: String = "",
    @SerialName("verification_status") val verificationStatus: String = "UNVERIFIED",
    @Serializable(with = LenientStringMapSerializer::class) val fields: Map<String, String> = emptyMap(),
    val ways: List<BeneficiaryWayDto> = emptyList(),
)

@Serializable
data class CreateBeneficiaryDto(
    @SerialName("full_name") val fullName: String,
    val type: String,
    val relationship: String? = null,
    @SerialName("country_iso2") val countryIso2: String,
    @SerialName("currency_code") val currencyCode: String? = null,
    @SerialName("delivery_method_code") val deliveryMethodCode: String? = null,
    val fields: Map<String, String>? = null,
)

@Serializable
data class SaveInstrumentDto(
    @SerialName("delivery_method_code") val deliveryMethodCode: String,
    @SerialName("country_iso2") val countryIso2: String? = null,
    @SerialName("currency_code") val currencyCode: String? = null,
    val fields: Map<String, String>,
)

// --- transactions -------------------------------------------------------------

@Serializable
data class CreateTransactionDto(
    @SerialName("quote_reference") val quoteReference: String,
    @SerialName("beneficiary_id") val beneficiaryId: Long,
    @SerialName("sending_purpose_code") val sendingPurposeCode: String? = null,
    @SerialName("document_ids") val documentIds: List<Long>? = null,
    /** Required by the server when the quote is paid from the wallet. */
    val password: String? = null,
    /** Required for a payment for goods to a company in China. */
    val goods: GoodsDto? = null,
)

@Serializable
data class GoodsDto(
    @SerialName("merchant_name") val merchantName: String,
    @SerialName("trade_date") val tradeDate: String,
    val lines: List<GoodsLineDto>,
    @SerialName("sales_link") val salesLink: String? = null,
    @SerialName("logistics_company") val logisticsCompany: String? = null,
    @SerialName("tracking_number") val trackingNumber: String? = null,
)

@Serializable
data class GoodsLineDto(
    @SerialName("product_name") val productName: String,
    val category: String,
    /** Decimal strings, never numbers. */
    @SerialName("unit_price") val unitPrice: String,
    @SerialName("currency_code") val currencyCode: String,
    val quantity: String,
)

@Serializable
data class GoodsRequirementDto(
    val needed: Boolean,
    val currency: String,
    val categories: List<GoodsCategoryDto> = emptyList(),
)

@Serializable
data class GoodsCategoryDto(val code: String, val label: String)

@Serializable
data class InvoiceGoodsDto(
    val read: Boolean,
    @SerialName("merchant_name") val merchantName: String? = null,
    @SerialName("trade_date") val tradeDate: String? = null,
    @SerialName("currency_code") val currencyCode: String? = null,
    val lines: List<InvoiceGoodsLineDto> = emptyList(),
)

@Serializable
data class InvoiceGoodsLineDto(
    @SerialName("product_name") val productName: String,
    @SerialName("unit_price") val unitPrice: String,
    val quantity: String,
)

/** Where and how to pay a transfer still awaiting payment. */
@Serializable
data class PayInDto(
    val method: String,
    val label: String,
    val account: String,
    val reference: String,
    val amount: String,
    val currency: String,
    val note: String = "",
    @SerialName("business_name") val businessName: String? = null,
)

@Serializable
data class MpesaRequest(val phone: String? = null)

@Serializable
data class MpesaSessionDto(val phone: String, val stub: Boolean = false)

@Serializable
data class HistoryDto(
    @SerialName("to_status") val toStatus: String,
    val note: String? = null,
    @SerialName("changed_at") val changedAt: String? = null,
)

@Serializable
data class TransactionDto(
    val reference: String,
    val status: String,
    @SerialName("is_financially_complete") val isFinanciallyComplete: Boolean = false,
    @SerialName("send_amount") val sendAmount: MoneyDto,
    @SerialName("receive_amount") val receiveAmount: MoneyDto,
    @SerialName("total_payable") val totalPayable: MoneyDto,
    @SerialName("exchange_rate") val exchangeRate: String = "0",
    @SerialName("beneficiary_id") val beneficiaryId: Long? = null,
    @SerialName("transaction_date") val transactionDate: String? = null,
    val charges: List<ChargeDto> = emptyList(),
    val history: List<HistoryDto> = emptyList(),
    @SerialName("payment_method_code") val paymentMethodCode: String? = null,
    @SerialName("pay_in") val payIn: PayInDto? = null,
)

@Serializable
data class TransferDocumentDto(
    val id: Long,
    @SerialName("type_code") val typeCode: String,
    @SerialName("type_name") val typeName: String = "",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("uploaded_at") val uploadedAt: String? = null,
    val status: String = "",
)

// --- wallet & devices -----------------------------------------------------------

@Serializable
data class BalanceDto(val currency: String, val amount: String)

@Serializable
data class MovementDto(
    val direction: String,
    val amount: String,
    val currency: String,
    val description: String = "",
    @SerialName("transaction_reference") val transactionReference: String? = null,
    @SerialName("posted_at") val postedAt: String? = null,
)

@Serializable
data class WalletDto(
    val balances: List<BalanceDto> = emptyList(),
    val movements: List<MovementDto> = emptyList(),
)

@Serializable
data class DeviceRequest(val token: String, val platform: String = "ANDROID")

// --- sign-up --------------------------------------------------------------

@Serializable
data class SignUpRequest(
    @SerialName("full_name") val fullName: String,
    val email: String,
    val phone: String,
    @SerialName("residence_iso2") val residenceIso2: String,
    val password: String,
    @SerialName("password_confirmation") val passwordConfirmation: String,
    @SerialName("device_name") val deviceName: String,
)

@Serializable
data class SignUpDto(
    @SerialName("full_name") val fullName: String = "",
    val email: String = "",
    val phone: String = "",
    @SerialName("residence_iso2") val residenceIso2: String = "",
    @SerialName("email_verified") val emailVerified: Boolean = false,
    @SerialName("phone_verification_required") val phoneVerificationRequired: Boolean = false,
    @SerialName("phone_verified") val phoneVerified: Boolean = false,
    @SerialName("next_step") val nextStep: String = "EMAIL",
)

@Serializable
data class SignedUpDto(
    val token: String,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("sign_up") val signUp: SignUpDto,
)

@Serializable
data class PhoneCodeRequest(val code: String)

@Serializable
data class IndividualDetailsRequest(
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("date_of_birth") val dateOfBirth: String,
    @SerialName("nationality_iso2") val nationalityIso2: String,
    @SerialName("address_line1") val addressLine1: String? = null,
    @SerialName("address_city") val addressCity: String? = null,
)

@Serializable
data class CountryOptionDto(
    val iso2: String,
    val name: String,
    @SerialName("dial_code") val dialCode: String = "",
)

// --- verification -----------------------------------------------------------

@Serializable
data class KycRestartDto(
    val reason: String,
    @SerialName("attempts_left") val attemptsLeft: Int? = null,
)

@Serializable
data class KycDocumentDto(
    val id: Long,
    @SerialName("type_code") val typeCode: String,
    @SerialName("type_name") val typeName: String,
    @SerialName("issuing_country_iso2") val issuingCountryIso2: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
)

@Serializable
data class KycDocumentTypeDto(val code: String, val name: String)

@Serializable
data class KycStateDto(
    val status: String = "UNVERIFIED",
    val verified: Boolean = false,
    val tier: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    val mode: String = "DOCUMENTS",
    @SerialName("can_start_verification") val canStartVerification: Boolean = false,
    @SerialName("can_add_identity") val canAddIdentity: Boolean = false,
    @SerialName("can_submit") val canSubmit: Boolean = false,
    val restart: KycRestartDto? = null,
    val documents: List<KycDocumentDto> = emptyList(),
    @SerialName("document_types") val documentTypes: List<KycDocumentTypeDto> = emptyList(),
)

@Serializable
data class KycSessionDto(
    val token: String,
    val level: String = "",
    @SerialName("expires_in") val expiresIn: Int = 600,
)
