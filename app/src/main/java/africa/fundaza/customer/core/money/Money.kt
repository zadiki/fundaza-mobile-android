package africa.fundaza.customer.core.money

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Currency
import java.util.Locale

/**
 * An amount and its currency. The API sends amounts as strings precisely so
 * that "449790.0000" survives the trip; we parse straight into [BigDecimal]
 * and never pass through a Double.
 */
data class Money(val amount: BigDecimal, val currency: String) {

    /** "KES 449,790.00" — grouped, at the currency's own minor-unit precision. */
    fun format(locale: Locale = Locale.getDefault()): String =
        "$currency ${formatAmount(locale)}"

    fun formatAmount(locale: Locale = Locale.getDefault()): String {
        val digits = fractionDigits(currency)
        val pattern = if (digits == 0) "#,##0" else "#,##0." + "0".repeat(digits)
        val format = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale))
        return format.format(amount.setScale(digits, RoundingMode.HALF_UP))
    }

    val isZero: Boolean get() = amount.signum() == 0

    companion object {
        fun parse(amount: String, currency: String): Money =
            Money(BigDecimal(amount.trim()), currency)

        fun fractionDigits(currency: String): Int =
            runCatching { Currency.getInstance(currency).defaultFractionDigits }
                .getOrDefault(2)
                .coerceAtLeast(0)
    }
}

/** "1 CNY = 17.82 KES" — enough digits to be honest, not so many it's noise. */
fun formatRate(rate: BigDecimal, from: String, to: String): String {
    val shown = rate.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros()
    val text = if (shown.scale() < 2) shown.setScale(2).toPlainString() else shown.toPlainString()
    return "1 $from = $text $to"
}

/**
 * What the customer typed in an amount field, cleaned for the API: digits and
 * one decimal point, at most four decimal places (the API's own limit).
 * Returns null when the input is not a positive amount yet.
 */
fun parseAmountInput(raw: String): BigDecimal? {
    val cleaned = raw.replace(",", "").replace(" ", "").trim()
    if (cleaned.isEmpty()) return null
    if (!Regex("^\\d+(\\.\\d{0,4})?$").matches(cleaned)) return null
    val value = cleaned.toBigDecimalOrNull() ?: return null
    return value.takeIf { it.signum() > 0 }
}

/** Keep what can still become a valid amount while the customer is typing. */
fun sanitiseAmountInput(raw: String): String {
    val cleaned = raw.filter { it.isDigit() || it == '.' }
    val firstDot = cleaned.indexOf('.')
    if (firstDot < 0) return cleaned.take(12)
    val whole = cleaned.substring(0, firstDot).take(12)
    val fraction = cleaned.substring(firstDot + 1).replace(".", "").take(4)
    return "$whole.$fraction"
}
