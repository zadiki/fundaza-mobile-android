package africa.fundaza.customer.ui

import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.data.repository.SignInResult
import africa.fundaza.customer.testing.FakeAuthRepository
import africa.fundaza.customer.testing.MainDispatcherRule
import africa.fundaza.customer.ui.feature.auth.LoginViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule val main = MainDispatcherRule()
    private val auth = FakeAuthRepository()

    private fun LoginViewModel.fill(email: String, password: String) {
        onEmailChange(email)
        onPasswordChange(password)
    }

    @Test
    fun `checks the form before calling the server`() = runTest(main.dispatcher) {
        val vm = LoginViewModel(auth)
        vm.fill("not-an-email", "")
        vm.submit()
        advanceUntilIdle()

        assertTrue(auth.attempts.isEmpty())
        assertEquals("That doesn't look like an email address", vm.state.value.emailError)
        assertEquals("Enter your password", vm.state.value.passwordError)
    }

    @Test
    fun `shows the credential refusal the server wrote`() = runTest(main.dispatcher) {
        auth.result = ApiResult.Failure(
            ApiError.Validation("The given data was invalid.", mapOf("email" to listOf("These credentials do not match our records."))),
        )
        val vm = LoginViewModel(auth)
        vm.fill("grace@example.com", "wrong")
        vm.submit()
        advanceUntilIdle()

        assertEquals("These credentials do not match our records.", vm.state.value.formError)
        assertFalse(vm.state.value.submitting)
    }

    @Test
    fun `turns away agent and staff logins`() = runTest(main.dispatcher) {
        auth.result = ApiResult.Success(SignInResult.NotACustomer)
        val vm = LoginViewModel(auth)
        vm.fill("teller@agent.example", "secret")
        vm.submit()
        advanceUntilIdle()

        assertEquals("This app is for Fundaza customers. Agents and staff sign in on the web.", vm.state.value.formError)
        assertEquals("", vm.state.value.password)
    }
}
