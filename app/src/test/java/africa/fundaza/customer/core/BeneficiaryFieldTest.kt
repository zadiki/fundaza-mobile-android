package africa.fundaza.customer.core

import africa.fundaza.customer.domain.model.BeneficiaryField
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BeneficiaryFieldTest {

    private val msisdn = BeneficiaryField("wallet_msisdn", "Mobile number", "STRING", required = true, pattern = "^\\+254[0-9]{9}$")

    @Test
    fun `applies the server's pattern as given`() {
        assertNull(msisdn.validate("+254700111222"))
        assertEquals("Enter a valid mobile number", msisdn.validate("0700111222"))
    }

    @Test
    fun `requires required fields and lets optional ones be empty`() {
        assertEquals("Mobile number is required", msisdn.validate("  "))
        assertNull(msisdn.copy(required = false).validate(""))
    }

    @Test
    fun `a malformed server pattern does not block the customer`() {
        assertNull(msisdn.copy(pattern = "[unclosed").validate("anything"))
    }
}
