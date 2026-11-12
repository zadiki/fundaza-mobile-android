package africa.fundaza.customer.core

import africa.fundaza.customer.core.money.Money
import africa.fundaza.customer.core.money.formatRate
import africa.fundaza.customer.core.money.parseAmountInput
import africa.fundaza.customer.core.money.sanitiseAmountInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.util.Locale

class MoneyTest {

    @Test
    fun `parses the API's decimal strings exactly, never through a double`() {
        val money = Money.parse("449790.0000", "KES")
        assertEquals(BigDecimal("449790.0000"), money.amount)
        // 0.1 + 0.2 is the classic double failure; a decimal string must survive intact.
        assertEquals(BigDecimal("0.3000"), Money.parse("0.1000", "USD").amount + Money.parse("0.2000", "USD").amount)
    }

    @Test
    fun `formats at the currency's own precision with grouping`() {
        assertEquals("KES 449,790.00", Money.parse("449790.0000", "KES").format(Locale.US))
        assertEquals("JPY 1,235", Money.parse("1234.5", "JPY").format(Locale.US))
    }

    @Test
    fun `formats a rate without noise`() {
        assertEquals("1 CNY = 17.82 KES", formatRate(BigDecimal("17.8200000000"), "CNY", "KES"))
        assertEquals("1 USD = 129.4567 KES", formatRate(BigDecimal("129.456712"), "USD", "KES"))
    }

    @Test
    fun `accepts only positive amounts with at most four decimals`() {
        assertEquals(BigDecimal("25000"), parseAmountInput("25,000"))
        assertEquals(BigDecimal("12.5"), parseAmountInput("12.5"))
        assertNull(parseAmountInput(""))
        assertNull(parseAmountInput("0"))
        assertNull(parseAmountInput("1.23456"))
        assertNull(parseAmountInput("abc"))
    }

    @Test
    fun `sanitises typing into something that can become an amount`() {
        assertEquals("1234.56", sanitiseAmountInput("1,234.56"))
        assertEquals("12.3456", sanitiseAmountInput("12.345678"))
        assertEquals("1.23", sanitiseAmountInput("1.2.3"))
    }
}
