package africa.fundaza.customer.domain

import africa.fundaza.customer.domain.model.GoodsDraft
import africa.fundaza.customer.domain.model.GoodsLineDraft
import africa.fundaza.customer.domain.model.InvoiceGoods
import africa.fundaza.customer.domain.model.InvoiceGoodsLine
import africa.fundaza.customer.ui.feature.send.decimalInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class GoodsDraftTest {

    private val today = LocalDate.parse("2026-11-19")

    @Test
    fun `each line is totalled half-up to the cent, as the server totals it`() {
        val goods = GoodsDraft(
            lines = listOf(
                GoodsLineDraft(0, "Buttons", "OTHER", "0.33", "3"),
                GoodsLineDraft(1, "Ribbon", "OTHER", "1.05", "0.3333"),
            ),
        )

        assertEquals(BigDecimal("0.35"), goods.lines[1].total)
        assertEquals(BigDecimal("1.34"), goods.total)
    }

    @Test
    fun `there is no total until every line has a price to the cent and a quantity`() {
        assertNull(GoodsDraft(lines = listOf(GoodsLineDraft(0, "Shirts", "APPAREL", "2.505", "1"))).total)
        assertNull(GoodsDraft(lines = listOf(GoodsLineDraft(0, "Shirts", "APPAREL", "2.50", ""))).total)
        assertNull(GoodsDraft(lines = listOf(GoodsLineDraft(0, "Shirts", "APPAREL", "0", "4"))).total)
    }

    @Test
    fun `goods are complete with a seller, a past sale date and every line described`() {
        val goods = GoodsDraft(
            merchantName = "Yiwu",
            tradeDate = "2026-11-10",
            lines = listOf(GoodsLineDraft(0, "Shirts", "APPAREL", "2.50", "4")),
        )

        assertTrue(goods.complete(today))
        assertFalse(goods.copy(tradeDate = "2026-12-01").complete(today))
        assertFalse(goods.copy(salesLink = "http://shop.example.cn").complete(today))
        assertFalse(goods.withLine().complete(today))
    }

    @Test
    fun `an invoice fills only what was left empty`() {
        val invoice = InvoiceGoods(true, "Shenzhen Widget", "2026-11-05", "CNY", listOf(InvoiceGoodsLine("Jeans", "68.50", "120")))

        val fresh = GoodsDraft().withInvoice(invoice, "CNY")
        val typed = GoodsDraft(merchantName = "My seller", lines = listOf(GoodsLineDraft(0, "Shirts"))).withInvoice(invoice, "CNY")

        assertEquals("Shenzhen Widget", fresh.merchantName)
        assertEquals("68.50", fresh.lines.single().unitPrice)
        assertEquals("My seller", typed.merchantName)
        assertEquals("2026-11-05", typed.tradeDate)
        assertEquals("Shirts", typed.lines.single().productName)
    }

    @Test
    fun `a price box takes a figure to the cent and a quantity box to four places`() {
        assertEquals("12.34", decimalInput("12.345", 2))
        assertEquals("1.5", decimalInput("1,5", 4))
        assertEquals("400", decimalInput("400 pcs", 4))
    }
}
