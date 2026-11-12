package africa.fundaza.customer.data

import africa.fundaza.customer.data.remote.dto.BeneficiaryDto
import africa.fundaza.customer.data.remote.dto.Envelope
import africa.fundaza.customer.data.remote.dto.TransactionDto
import africa.fundaza.customer.data.remote.toDomain
import africa.fundaza.customer.data.remote.toTransfer
import africa.fundaza.customer.di.NetworkModule
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Shapes the staging server really sends, which broke parsing before. */
class PayloadShapeTest {

    private val json = NetworkModule.json()

    @Test
    fun `transaction charges without a label still parse and get a readable label`() {
        val body = """
            {"data":[{"reference":"TXN-1","status":"AWAITING_PAYMENT","is_financially_complete":false,
              "send_amount":{"amount":"5000.0000","currency":"USD"},
              "receive_amount":{"amount":"645000.0000","currency":"KES"},
              "total_payable":{"amount":"5015.0000","currency":"USD"},
              "exchange_rate":"129.0000000000","beneficiary_id":46,"transaction_date":"2026-11-11 00:00:00",
              "charges":[{"type":"TRANSFER_FEE","amount":{"amount":"15.0000","currency":"USD"}}],
              "history":[{"to_status":"DRAFT","note":null,"changed_at":"2026-11-11 08:52:56.691486"}]}]}
        """.trimIndent()

        val transfers = json.decodeFromString(Envelope.serializer(ListSerializer(TransactionDto.serializer())), body)
            .data.map { it.toTransfer() }

        assertEquals("Transfer fee", transfers.single().charges.single().label)
    }

    @Test
    fun `beneficiary fields with nulls and numbers still parse`() {
        val body = """
            {"data":[{"id":1,"full_name":"Test Person","country_iso2":"KE","currency_code":null,
              "verification_status":"PENDING",
              "fields":{"account_number":"0012345","swift_code":null,"branch":102},
              "ways":[{"id":2,"country_iso2":"KE","delivery_method_code":"BANK_TRANSFER",
                "delivery_method":"Bank Transfer","currency_code":"KES",
                "fields":{"account_number":"0012345","address":null}}]}]}
        """.trimIndent()

        val beneficiary = json.decodeFromString(Envelope.serializer(ListSerializer(BeneficiaryDto.serializer())), body)
            .data.single()

        assertEquals(mapOf("account_number" to "0012345", "branch" to "102"), beneficiary.fields)
        assertFalse(beneficiary.ways.single().fields.containsKey("address"))
        assertEquals("KES", beneficiary.toDomain().ways.single().currency)
    }
}
