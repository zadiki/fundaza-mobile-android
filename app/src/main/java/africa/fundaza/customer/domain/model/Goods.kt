package africa.fundaza.customer.domain.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/** A kind of product a payment for goods to China can list (the server's `GoodsCategory`). */
data class GoodsCategory(val code: String, val label: String)

/**
 * Whether a send must list the goods it pays for (`GET /quotes/{reference}/goods`): a payment
 * for goods to a company in China. [currency] is the only currency a price may be in.
 */
data class GoodsRequirement(
    val needed: Boolean,
    val currency: String,
    val categories: List<GoodsCategory>,
)

/** What an attached invoice seemed to bill (`GET /documents/{id}/goods`). Every field is a suggestion. */
data class InvoiceGoods(
    val read: Boolean,
    val merchantName: String?,
    val tradeDate: String?,
    val currencyCode: String?,
    val lines: List<InvoiceGoodsLine>,
)

data class InvoiceGoodsLine(val productName: String, val unitPrice: String, val quantity: String)

/** One product as typed. Its total is worked out, never typed. */
data class GoodsLineDraft(
    val key: Int,
    val productName: String = "",
    val categoryCode: String = "",
    val unitPrice: String = "",
    val quantity: String = "",
) {
    /**
     * Unit price times quantity, half-up to the cent, as the server works it out
     * (`TransferGoods::lineTotal`); null until both are figures above zero.
     */
    val total: BigDecimal?
        get() {
            val price = unitPrice.trim().takeIf { PRICE.matches(it) }?.toBigDecimal() ?: return null
            val count = quantity.trim().takeIf { QUANTITY.matches(it) }?.toBigDecimal() ?: return null
            return (price * count).setScale(2, RoundingMode.HALF_UP).takeIf { it.signum() > 0 }
        }

    val complete: Boolean
        get() = productName.isNotBlank() && categoryCode.isNotBlank() && total != null

    val isBlank: Boolean
        get() = productName.isBlank() && unitPrice.isBlank() && quantity.isBlank()

    companion object {
        /** At most two decimal places: a price is given to the cent. */
        val PRICE = Regex("""^\d+(\.\d{1,2})?$""")
        val QUANTITY = Regex("""^\d+(\.\d{1,4})?$""")
    }
}

/** The goods of a payment for goods to China, as typed on the Details step. */
data class GoodsDraft(
    val merchantName: String = "",
    val tradeDate: String = "",
    val salesLink: String = "",
    val logisticsCompany: String = "",
    val trackingNumber: String = "",
    val lines: List<GoodsLineDraft> = listOf(GoodsLineDraft(key = 0)),
) {
    /** What the goods add up to once every line is priced: what the company receives. */
    val total: BigDecimal?
        get() {
            if (lines.isEmpty()) return null
            var sum = BigDecimal.ZERO.setScale(2)
            for (line in lines) sum += line.total ?: return null
            return sum.takeIf { it.signum() > 0 }
        }

    /** Everything the server requires is filled in. */
    fun complete(today: LocalDate): Boolean =
        merchantName.isNotBlank() &&
            tradeDate.isNotBlank() &&
            runCatching { !LocalDate.parse(tradeDate.trim()).isAfter(today) }.getOrDefault(false) &&
            (salesLink.isBlank() || salesLink.trim().startsWith("https://")) &&
            lines.isNotEmpty() && lines.all { it.complete }

    val touched: Boolean
        get() = listOf(merchantName, tradeDate, salesLink, logisticsCompany, trackingNumber).any { it.isNotBlank() } ||
            lines.any { !it.isBlank || it.categoryCode.isNotBlank() }

    fun withLine(): GoodsDraft =
        if (lines.size >= MAX_LINES) this else copy(lines = lines + GoodsLineDraft(key = (lines.maxOfOrNull { it.key } ?: -1) + 1))

    fun withoutLine(key: Int): GoodsDraft = if (lines.size <= 1) this else copy(lines = lines.filterNot { it.key == key })

    fun withLine(key: Int, change: (GoodsLineDraft) -> GoodsLineDraft): GoodsDraft =
        copy(lines = lines.map { if (it.key == key) change(it) else it })

    /**
     * What the invoice says, filled in where the customer left a field empty. Its lines replace
     * the form's only while no line has been typed. Prices in another currency than the payout's
     * are left out: the customer types those in [payoutCurrency].
     */
    fun withInvoice(invoice: InvoiceGoods, payoutCurrency: String): GoodsDraft {
        val pricesUsable = invoice.currencyCode == null || invoice.currencyCode == payoutCurrency
        val linesEmpty = lines.all { it.isBlank }
        return copy(
            merchantName = merchantName.ifBlank { invoice.merchantName.orEmpty() },
            tradeDate = tradeDate.ifBlank { invoice.tradeDate.orEmpty() },
            lines = if (linesEmpty && invoice.lines.isNotEmpty()) {
                invoice.lines.take(MAX_LINES).mapIndexed { index, line ->
                    GoodsLineDraft(
                        key = index,
                        productName = line.productName,
                        unitPrice = if (pricesUsable) line.unitPrice else "",
                        quantity = line.quantity,
                    )
                }
            } else {
                lines
            },
        )
    }

    companion object {
        /** The most lines one send takes, as the server allows. */
        const val MAX_LINES = 100
    }
}
