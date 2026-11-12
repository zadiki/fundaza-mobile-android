package africa.fundaza.customer.ui

import africa.fundaza.customer.testing.walletMethod
import africa.fundaza.customer.testing.MPESA_PAYIN
import africa.fundaza.customer.testing.MOBILE_MONEY
import africa.fundaza.customer.testing.BANK_TRANSFER
import africa.fundaza.customer.domain.model.CorridorMethods
import africa.fundaza.customer.domain.model.Method
import africa.fundaza.customer.domain.model.SendingPurpose
import africa.fundaza.customer.testing.FakeBeneficiaryRepository
import africa.fundaza.customer.domain.model.Country
import africa.fundaza.customer.testing.CN_KE
import africa.fundaza.customer.testing.FakeCorridorRepository
import africa.fundaza.customer.testing.FakeCountryLocator
import africa.fundaza.customer.testing.FakeCustomerRepository
import africa.fundaza.customer.testing.FakeDocumentRepository
import africa.fundaza.customer.testing.FakeTransferRepository
import africa.fundaza.customer.testing.MainDispatcherRule
import africa.fundaza.customer.testing.MutableClock
import africa.fundaza.customer.testing.beneficiary
import africa.fundaza.customer.testing.way
import africa.fundaza.customer.ui.feature.send.SendEvent
import africa.fundaza.customer.ui.feature.send.SendStep
import africa.fundaza.customer.ui.feature.send.SendViewModel
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val clock = MutableClock()
    private val transfers = FakeTransferRepository(clock)
    private val mary = beneficiary(88, "Mary Atieno", way(12))
    private val people = FakeBeneficiaryRepository(
        listOf(
            mary,
            beneficiary(89, "Otieno Bank-Only", way(13, method = "BANK_TRANSFER")),
            beneficiary(90, "Li Wei", way(14, country = "CN", currency = "CNY")),
        ),
    )

    private val corridors = FakeCorridorRepository()
    private val locator = FakeCountryLocator()
    private val customers = FakeCustomerRepository()

    private fun viewModel() =
        SendViewModel(corridors, customers, locator, transfers, people, FakeDocumentRepository(), clock)

    private fun corridor(id: Long, from: String, to: String, send: String, receive: String, vararg extraSend: String) =
        CN_KE.copy(
            id = id,
            code = "$from-$to",
            origin = Country(from, from),
            destination = Country(to, to),
            sendCurrency = send,
            receiveCurrency = receive,
            sendCurrencies = listOf(send, *extraSend),
            receiveCurrencies = listOf(receive, "USD"),
            limits = emptyList(),
        )

    private val keCn = corridor(10, "KE", "CN", "KES", "CNY", "USD")
    private val keSo = corridor(11, "KE", "SO", "KES", "USD")
    private val soCn = corridor(12, "SO", "CN", "USD", "CNY")

    @Test
    fun `only routes out of the country the phone is in are offered`() = runTest(main.dispatcher) {
        corridors.corridors = listOf(soCn, keCn, keSo)
        locator.country = "KE"
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("KE", vm.state.value.senderCountry)
        assertEquals(listOf("KE-CN", "KE-SO"), vm.state.value.destinations.map { it.code })
        assertEquals("KE-CN", vm.state.value.corridor?.code)
    }

    @Test
    fun `with no usable location the sender's home country is used`() = runTest(main.dispatcher) {
        corridors.corridors = listOf(soCn, keCn)
        locator.country = "US" // no routes leave from here
        customers.residenceIso2 = "KE"
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("KE", vm.state.value.senderCountry)
    }

    @Test
    fun `the amount is prefilled with about 100 USD in the local currency`() = runTest(main.dispatcher) {
        corridors.corridors = listOf(keCn)
        locator.country = "KE"
        transfers.rates = mapOf(("KES" to "USD") to BigDecimal("0.0077519379"))
        val vm = viewModel()
        advanceUntilIdle()

        // 100 / 0.0077519379 = 12,900.00 → 13,000
        assertEquals("13000", vm.state.value.amountInput)
        assertFalse(vm.state.value.amountEdited)
        assertEquals(BigDecimal("13000"), vm.state.value.quote?.sendAmount?.amount)
    }

    @Test
    fun `sending in dollars prefills exactly 100`() = runTest(main.dispatcher) {
        corridors.corridors = listOf(soCn)
        locator.country = "SO"
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("100", vm.state.value.amountInput)
    }

    @Test
    fun `a typed amount is never replaced by the suggestion`() = runTest(main.dispatcher) {
        corridors.corridors = listOf(keCn, keSo)
        locator.country = "KE"
        transfers.rates = mapOf(("KES" to "USD") to BigDecimal("0.0077519379"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAmountChange("5000")
        vm.selectCorridor(keSo)
        advanceUntilIdle()

        assertEquals("5000", vm.state.value.amountInput)
    }

    @Test
    fun `going back a step keeps everything already chosen`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAmountChange("25000"); advanceUntilIdle(); vm.next()
        vm.selectRecipient(88); vm.next()
        vm.selectPurpose(SendingPurpose.EDUCATION)
        val before = vm.state.value

        assertTrue(vm.back())
        assertTrue(vm.back())

        val s = vm.state.value
        assertEquals(SendStep.Amount, s.step)
        assertEquals("25000", s.amountInput)
        assertEquals(before.corridor, s.corridor)
        assertEquals(before.deliveryMethod, s.deliveryMethod)
        assertEquals(before.quote, s.quote)
        assertEquals(88L, s.recipientId)
        assertEquals(SendingPurpose.EDUCATION, s.purpose)

        vm.next(); vm.next()
        assertEquals(SendStep.Details, vm.state.value.step)
        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `starts on the first corridor with its first methods chosen`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals("CN-KE-01", s.corridor?.code)
        assertEquals("CNY", s.sendCurrency)
        assertEquals("KES", s.receiveCurrency)
        assertEquals("MOBILE_MONEY", s.deliveryMethod?.code)
        assertEquals("BANK_TRANSFER", s.paymentMethod?.code)
    }

    @Test
    fun `an amount outside the corridor limits is refused without asking for a quote`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onAmountChange("500")
        advanceUntilIdle()

        assertEquals("The least you can send on this route is CNY 1,000.00", vm.state.value.amountError)
        assertTrue(transfers.quoteRequests.isEmpty())
        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun `typing is debounced into a single quote for the final amount`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        listOf("2", "25", "250", "2500", "25000").forEach { vm.onAmountChange(it) }
        advanceUntilIdle()

        assertEquals(1, transfers.quoteRequests.size)
        assertEquals(BigDecimal("25000"), transfers.quoteRequests.single().sendAmount)
        assertNotNull(vm.state.value.quote)
        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `reselecting the same option keeps the quote rather than leaving it spinning`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAmountChange("25000")
        advanceUntilIdle()

        vm.selectSendCurrency("CNY")
        advanceUntilIdle()

        assertNotNull(vm.state.value.quote)
        assertFalse(vm.state.value.quoting)
        assertEquals(1, transfers.quoteRequests.size)
    }

    @Test
    fun `only recipients who can receive this way are offered, others need details`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val s = vm.state.value
        assertEquals(listOf(88L), s.readyRecipients.map { it.first.id })
        // Everyone else is offered too: those already in Kenya first.
        assertEquals(listOf(89L, 90L), s.recipientsNeedingDetails.map { it.id })
    }

    @Test
    fun `a trade payment cannot continue without a supporting document`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAmountChange("25000"); advanceUntilIdle(); vm.next()
        vm.selectRecipient(88); vm.next()

        vm.selectPurpose(SendingPurpose.TRADE_PAYMENT)
        assertFalse(vm.state.value.canContinue)

        vm.uploadDocument(africa.fundaza.customer.domain.model.TransferDocumentType.INVOICE, "content://docs/invoice.pdf")
        advanceUntilIdle()
        assertTrue(vm.state.value.canContinue)
    }

    @Test
    fun `confirming sends the quote, recipient and purpose, then reports the reference`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAmountChange("25000"); advanceUntilIdle(); vm.next()
        vm.selectRecipient(88); vm.next()
        vm.selectPurpose(SendingPurpose.FAMILY_SUPPORT); vm.next()
        assertEquals(SendStep.Review, vm.state.value.step)

        vm.events.test {
            vm.confirm()
            advanceUntilIdle()
            assertEquals(SendEvent.Sent("TXN-1"), awaitItem())
        }
        assertEquals(Triple("QT-1", 88L, SendingPurpose.FAMILY_SUPPORT), transfers.created.single())
    }

    @Test
    fun `an expired quote is priced again instead of being sent`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAmountChange("25000"); advanceUntilIdle(); vm.next()
        vm.selectRecipient(88); vm.next()
        vm.selectPurpose(SendingPurpose.EDUCATION); vm.next()

        clock.now = clock.now.plusSeconds(901)
        vm.confirm()
        advanceUntilIdle()

        assertTrue(transfers.created.isEmpty())
        assertEquals(2, transfers.quoteRequests.size)
        assertEquals("QT-2", vm.state.value.quote?.reference)
        assertTrue(vm.state.value.repriced)
        assertNull(vm.state.value.submitError)
    }

    @Test
    fun `a refused transfer shows the server's own sentence`() = runTest(main.dispatcher) {
        transfers.createFailure = africa.fundaza.customer.core.network.ApiError.Validation("This transfer needs a verified identity.")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAmountChange("25000"); advanceUntilIdle(); vm.next()
        vm.selectRecipient(88); vm.next()
        vm.selectPurpose(SendingPurpose.MEDICAL); vm.next()

        vm.confirm()
        advanceUntilIdle()

        assertEquals("This transfer needs a verified identity.", vm.state.value.submitError)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `searching narrows both lists by name or account details without losing the choice`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onAmountChange("25000"); advanceUntilIdle(); vm.next()
        vm.selectRecipient(88)

        vm.onRecipientQueryChange("li w")
        assertTrue(vm.state.value.shownReadyRecipients.isEmpty())
        assertEquals(listOf(90L), vm.state.value.shownRecipientsNeedingDetails.map { it.id })
        assertTrue(vm.state.value.canContinue) // Mary stays chosen while hidden

        vm.onRecipientQueryChange("0700 111")
        assertEquals(3, vm.state.value.shownReadyRecipients.size + vm.state.value.shownRecipientsNeedingDetails.size)

        vm.onRecipientQueryChange("nobody")
        assertTrue(vm.state.value.shownReadyRecipients.isEmpty() && vm.state.value.shownRecipientsNeedingDetails.isEmpty())
    }

    private suspend fun kotlinx.coroutines.test.TestScope.onReviewPayingBy(vararg payment: Method): SendViewModel {
        corridors.methods = CorridorMethods(payment = payment.toList(), delivery = listOf(MOBILE_MONEY))
        val vm = viewModel()
        advanceUntilIdle()
        payment.firstOrNull { it.isWallet }?.let(vm::selectPaymentMethod)
        vm.onAmountChange("25000"); advanceUntilIdle(); vm.next()
        vm.selectRecipient(88); vm.next()
        vm.selectPurpose(SendingPurpose.FAMILY_SUPPORT); vm.next()
        assertEquals(SendStep.Review, vm.state.value.step)
        return vm
    }

    @Test
    fun `paying from the wallet asks for the password before anything is sent`() = runTest(main.dispatcher) {
        val vm = onReviewPayingBy(BANK_TRANSFER, walletMethod())

        vm.confirm()
        advanceUntilIdle()

        assertTrue(vm.state.value.passwordPrompt)
        assertTrue(transfers.passwords.isEmpty())

        vm.events.test {
            vm.confirmWithPassword("right password")
            advanceUntilIdle()
            assertEquals(SendEvent.Sent("TXN-1"), awaitItem())
        }
        assertEquals(listOf<String?>("right password"), transfers.passwords)
        assertFalse(vm.state.value.passwordPrompt)
    }

    @Test
    fun `a wrong wallet password keeps the prompt open and says so there`() = runTest(main.dispatcher) {
        val vm = onReviewPayingBy(BANK_TRANSFER, walletMethod())

        vm.confirm()
        vm.confirmWithPassword("wrong")
        advanceUntilIdle()

        assertTrue(vm.state.value.passwordPrompt)
        assertEquals("That password is not right. Try again.", vm.state.value.passwordError)
        assertNull(vm.state.value.submitError)
        assertTrue(transfers.created.isEmpty())
    }

    @Test
    fun `a send paid any other way asks for no password`() = runTest(main.dispatcher) {
        val vm = onReviewPayingBy(BANK_TRANSFER, walletMethod())
        vm.selectPaymentMethod(BANK_TRANSFER)
        advanceUntilIdle() // a new way to pay is priced again first

        vm.events.test {
            vm.confirm()
            advanceUntilIdle()
            assertEquals(SendEvent.Sent("TXN-1"), awaitItem())
        }
        assertFalse(vm.state.value.passwordPrompt)
        assertEquals(listOf<String?>(null), transfers.passwords)
    }

    @Test
    fun `mobile money is offered for a shilling send and never chosen for another currency`() = runTest(main.dispatcher) {
        locator.country = "KE"
        corridors.corridors = listOf(keCn)
        corridors.methods = CorridorMethods(payment = listOf(MPESA_PAYIN, BANK_TRANSFER, walletMethod()), delivery = listOf(MOBILE_MONEY))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("MOBILE_MONEY", vm.state.value.paymentMethod?.code)
        assertTrue(vm.state.value.paymentChoices.any { it.code == "MOBILE_MONEY" })

        vm.selectSendCurrency("USD")
        assertEquals("BANK_TRANSFER", vm.state.value.paymentMethod?.code)
        assertFalse(vm.state.value.paymentChoices.any { it.code == "MOBILE_MONEY" })
    }

    @Test
    fun `the wallet is never the default and an empty one cannot be chosen`() = runTest(main.dispatcher) {
        val empty = walletMethod(available = false)
        corridors.methods = CorridorMethods(payment = listOf(empty, BANK_TRANSFER), delivery = listOf(MOBILE_MONEY))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("BANK_TRANSFER", vm.state.value.paymentMethod?.code)
        vm.selectPaymentMethod(empty)
        assertEquals("BANK_TRANSFER", vm.state.value.paymentMethod?.code)
    }
}
