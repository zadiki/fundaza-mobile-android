package africa.fundaza.customer.data.remote

import africa.fundaza.customer.core.money.Money
import africa.fundaza.customer.data.remote.dto.BeneficiaryDto
import africa.fundaza.customer.data.remote.dto.BeneficiaryFieldDto
import africa.fundaza.customer.data.remote.dto.ChargeDto
import africa.fundaza.customer.data.remote.dto.CountryOptionDto
import africa.fundaza.customer.data.remote.dto.KycStateDto
import africa.fundaza.customer.data.remote.dto.SignUpDto
import africa.fundaza.customer.data.remote.dto.CorridorDto
import africa.fundaza.customer.data.remote.dto.CorridorMethodsDto
import africa.fundaza.customer.data.remote.dto.CurrentUserDto
import africa.fundaza.customer.data.remote.dto.CustomerDto
import africa.fundaza.customer.data.remote.dto.GoodsDto
import africa.fundaza.customer.data.remote.dto.GoodsLineDto
import africa.fundaza.customer.data.remote.dto.GoodsRequirementDto
import africa.fundaza.customer.data.remote.dto.InvoiceGoodsDto
import africa.fundaza.customer.data.remote.dto.MoneyDto
import africa.fundaza.customer.data.remote.dto.QuoteDto
import africa.fundaza.customer.data.remote.dto.TransactionDto
import africa.fundaza.customer.data.remote.dto.TransferDocumentDto
import africa.fundaza.customer.data.remote.dto.WalletDto
import africa.fundaza.customer.domain.model.CountryOption
import africa.fundaza.customer.domain.model.KycDocument
import africa.fundaza.customer.domain.model.KycDocumentType
import africa.fundaza.customer.domain.model.KycMode
import africa.fundaza.customer.domain.model.KycRestart
import africa.fundaza.customer.domain.model.KycState
import africa.fundaza.customer.domain.model.SignUp
import africa.fundaza.customer.domain.model.SignUpStep
import africa.fundaza.customer.domain.model.AmountLimit
import africa.fundaza.customer.domain.model.Beneficiary
import africa.fundaza.customer.domain.model.BeneficiaryField
import africa.fundaza.customer.domain.model.BeneficiaryWay
import africa.fundaza.customer.domain.model.Charge
import africa.fundaza.customer.domain.model.ChargeType
import africa.fundaza.customer.domain.model.Corridor
import africa.fundaza.customer.domain.model.CorridorMethods
import africa.fundaza.customer.domain.model.Country
import africa.fundaza.customer.domain.model.CurrentUser
import africa.fundaza.customer.domain.model.Customer
import africa.fundaza.customer.domain.model.GoodsCategory
import africa.fundaza.customer.domain.model.GoodsDraft
import africa.fundaza.customer.domain.model.GoodsRequirement
import africa.fundaza.customer.domain.model.InvoiceGoods
import africa.fundaza.customer.domain.model.InvoiceGoodsLine
import africa.fundaza.customer.domain.model.Kyc
import africa.fundaza.customer.domain.model.Method
import africa.fundaza.customer.domain.model.Quote
import africa.fundaza.customer.domain.model.StatusStep
import africa.fundaza.customer.domain.model.PayIn
import africa.fundaza.customer.domain.model.Transfer
import africa.fundaza.customer.domain.model.TransferDetail
import africa.fundaza.customer.domain.model.TransferDocument
import africa.fundaza.customer.domain.model.TransferStatus
import africa.fundaza.customer.domain.model.Wallet
import africa.fundaza.customer.domain.model.WalletMovement
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

internal fun MoneyDto.toDomain() = Money.parse(amount, currency)

internal fun String.toDecimalOrNull(): BigDecimal? = trim().toBigDecimalOrNull()

internal fun parseInstant(value: String?): Instant? = value?.let {
    runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull()
        ?: runCatching { Instant.parse(it) }.getOrNull()
}

internal fun parseDate(value: String?): LocalDate? = value?.let {
    runCatching { LocalDate.parse(it.take(10)) }.getOrNull()
}

internal fun CurrentUserDto.toDomain() = CurrentUser(
    id = id,
    name = name.ifBlank { username.orEmpty() },
    email = email,
    principalType = principal?.type,
    principalId = principal?.id,
    roles = roles,
    permissions = permissions.toSet(),
)

internal fun CustomerDto.toDomain() = Customer(
    reference = reference,
    type = type,
    name = name,
    email = email,
    phone = phone,
    residenceIso2 = residenceIso2,
    status = status,
    kyc = Kyc(kyc.status, kyc.verified, kyc.tier, parseDate(kyc.expiresAt)),
)

internal fun CorridorDto.toDomain(): Corridor {
    val explicit = limits.map { AmountLimit(it.currency, it.minAmount?.toDecimalOrNull(), it.maxAmount?.toDecimalOrNull()) }
    // Older servers only send the default-currency pair; fold it in so callers use one shape.
    val withDefault = if (explicit.any { it.currency == sendCurrencyCode }) explicit else {
        explicit + AmountLimit(sendCurrencyCode, minAmount?.toDecimalOrNull(), maxAmount?.toDecimalOrNull())
    }
    return Corridor(
        id = id,
        code = code,
        origin = Country(origin.iso2, origin.name),
        destination = Country(destination.iso2, destination.name),
        sendCurrency = sendCurrencyCode,
        receiveCurrency = receiveCurrencyCode,
        sendCurrencies = sendCurrencies.ifEmpty { listOf(sendCurrencyCode) },
        receiveCurrencies = receiveCurrencies.ifEmpty { listOf(receiveCurrencyCode) },
        limits = withDefault,
        quoteValiditySeconds = quoteValiditySeconds,
    )
}

internal fun CorridorMethodsDto.toDomain() = CorridorMethods(
    payment = paymentMethods.map { Method(it.code, it.name, it.currencies, it.available, it.balance?.toDomain()) },
    delivery = deliveryMethods.map { Method(it.code, it.name) },
)

internal fun ChargeDto.toDomain() = Charge(
    type = ChargeType.entries.firstOrNull { it.name == type } ?: ChargeType.OTHER,
    label = label.ifBlank { type.prettyCode() },
    amount = amount.toDomain(),
)

internal fun QuoteDto.toDomain() = Quote(
    reference = reference,
    corridorId = corridorId,
    sendAmount = sendAmount.toDomain(),
    receiveAmount = receiveAmount.toDomain(),
    totalPayable = totalPayable.toDomain(),
    customerRate = BigDecimal(customerRate),
    charges = charges.map { it.toDomain() },
    fastArrivalChosen = fastArrival.chosen,
    fastArrivalPrice = fastArrival.price?.toDomain(),
    expiresAt = parseInstant(expiresAt) ?: Instant.now(),
)

internal fun BeneficiaryFieldDto.toDomain() = BeneficiaryField(key, label, dataType, required, pattern)

internal fun BeneficiaryDto.toDomain() = Beneficiary(
    id = id,
    fullName = fullName,
    countryIso2 = countryIso2,
    currency = currencyCode,
    verificationStatus = verificationStatus,
    ways = ways.map {
        BeneficiaryWay(
            id = it.id,
            countryIso2 = it.countryIso2,
            deliveryMethodCode = it.deliveryMethodCode,
            deliveryMethodName = it.deliveryMethod.ifBlank { it.deliveryMethodCode.prettyCode() },
            currency = it.currencyCode,
            fields = it.fields,
        )
    },
)

internal fun TransactionDto.toTransfer() = Transfer(
    reference = reference,
    status = TransferStatus.from(status),
    isFinanciallyComplete = isFinanciallyComplete,
    sendAmount = sendAmount.toDomain(),
    receiveAmount = receiveAmount.toDomain(),
    totalPayable = totalPayable.toDomain(),
    exchangeRate = exchangeRate.toDecimalOrNull() ?: BigDecimal.ZERO,
    beneficiaryId = beneficiaryId,
    date = parseDate(transactionDate),
    charges = charges.map { it.toDomain() },
)

internal fun TransactionDto.toDetail() = TransferDetail(
    transfer = toTransfer(),
    history = history.map { StatusStep(TransferStatus.from(it.toStatus), it.note, parseInstant(it.changedAt)) },
    paymentMethodCode = paymentMethodCode,
    payIn = payIn?.let {
        PayIn(
            method = it.method,
            label = it.label,
            account = it.account,
            reference = it.reference,
            amount = Money.parse(it.amount, it.currency),
            note = it.note,
            businessName = it.businessName,
        )
    },
)

internal fun TransferDocumentDto.toDomain() = TransferDocument(id, typeCode, typeName, fileName, status)

internal fun GoodsRequirementDto.toDomain() = GoodsRequirement(
    needed = needed,
    currency = currency,
    categories = categories.map { GoodsCategory(it.code, it.label) },
)

internal fun InvoiceGoodsDto.toDomain() = InvoiceGoods(
    read = read,
    merchantName = merchantName,
    tradeDate = tradeDate,
    currencyCode = currencyCode,
    lines = lines.map { InvoiceGoodsLine(it.productName, it.unitPrice, it.quantity) },
)

/** The goods as the server takes them; every price in [currency], the payout's. */
internal fun GoodsDraft.toDto(currency: String) = GoodsDto(
    merchantName = merchantName.trim(),
    tradeDate = tradeDate.trim(),
    lines = lines.map {
        GoodsLineDto(
            productName = it.productName.trim(),
            category = it.categoryCode,
            unitPrice = it.unitPrice.trim(),
            currencyCode = currency,
            quantity = it.quantity.trim(),
        )
    },
    salesLink = salesLink.trim().ifEmpty { null },
    logisticsCompany = logisticsCompany.trim().ifEmpty { null },
    trackingNumber = trackingNumber.trim().ifEmpty { null },
)

internal fun WalletDto.toDomain() = Wallet(
    balances = balances.map { Money.parse(it.amount, it.currency) },
    movements = movements.map {
        WalletMovement(
            incoming = it.direction.equals("in", ignoreCase = true),
            amount = Money.parse(it.amount, it.currency),
            description = it.description,
            transactionReference = it.transactionReference,
            postedAt = parseInstant(it.postedAt),
        )
    },
)

/** MOBILE_MONEY → "Mobile money". */
fun String.prettyCode(): String =
    lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

internal fun SignUpDto.toDomain() = SignUp(
    fullName = fullName,
    email = email,
    phone = phone,
    residenceIso2 = residenceIso2,
    emailVerified = emailVerified,
    phoneVerificationRequired = phoneVerificationRequired,
    phoneVerified = phoneVerified,
    nextStep = SignUpStep.entries.firstOrNull { it.name == nextStep } ?: SignUpStep.EMAIL,
)

internal fun CountryOptionDto.toDomain() = CountryOption(iso2 = iso2, name = name, dialCode = dialCode)

internal fun KycStateDto.toDomain() = KycState(
    status = status,
    verified = verified,
    tier = tier,
    expiresAt = parseDate(expiresAt),
    mode = if (mode == "HOSTED") KycMode.HOSTED else KycMode.DOCUMENTS,
    canStartVerification = canStartVerification,
    canAddIdentity = canAddIdentity,
    canSubmit = canSubmit,
    restart = restart?.let { KycRestart(rejected = it.reason == "REJECTED", attemptsLeft = it.attemptsLeft) },
    documents = documents.map {
        KycDocument(it.id, it.typeCode, it.typeName, it.issuingCountryIso2, parseDate(it.expiresAt))
    },
    documentTypes = documentTypes.map { KycDocumentType(it.code, it.name) },
)
