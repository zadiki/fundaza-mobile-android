package africa.fundaza.customer.core.push

import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.StoredToken
import africa.fundaza.customer.core.security.TokenStore
import africa.fundaza.customer.data.repository.DeviceRepository
import africa.fundaza.customer.testing.customerUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceRegistrationTest {

    private class MemoryTokenStore : TokenStore {
        override suspend fun load(): StoredToken? = null
        override suspend fun save(token: StoredToken) = Unit
        override suspend fun clear() = Unit
    }

    private class FakeTokens(var token: String? = "fcm-1") : PushTokenSource {
        override suspend fun token() = token
    }

    private class FakeDevices : DeviceRepository {
        val registered = mutableListOf<String>()
        var result: ApiResult<Unit> = ApiResult.Success(Unit)
        override suspend fun register(pushToken: String): ApiResult<Unit> {
            registered += pushToken
            return result
        }
    }

    private fun TestScope.fixture(tokens: FakeTokens = FakeTokens()): Triple<SessionManager, DeviceRegistration, FakeDevices> {
        val session = SessionManager(MemoryTokenStore(), backgroundScope)
        val devices = FakeDevices()
        val registration = DeviceRegistration(session, tokens, devices, backgroundScope)
        registration.start()
        return Triple(session, registration, devices)
    }

    @Test
    fun `signing in registers this device`() = runTest {
        val (session, _, devices) = fixture()
        runCurrent()
        assertEquals(emptyList<String>(), devices.registered)

        session.signedIn(customerUser())
        runCurrent()

        assertEquals(listOf("fcm-1"), devices.registered)
    }

    @Test
    fun `a profile refresh does not register again`() = runTest {
        val (session, _, devices) = fixture()
        session.signedIn(customerUser())
        runCurrent()

        session.signedIn(customerUser().copy(name = "Grace W."))
        runCurrent()

        assertEquals(listOf("fcm-1"), devices.registered)
    }

    @Test
    fun `signing out and back in registers again, so a shared phone follows its user`() = runTest {
        val (session, _, devices) = fixture()
        session.signedIn(customerUser())
        runCurrent()

        session.end()
        runCurrent()
        session.signedIn(customerUser().copy(id = 99))
        runCurrent()

        assertEquals(listOf("fcm-1", "fcm-1"), devices.registered)
    }

    @Test
    fun `a rotated token is registered while signed in`() = runTest {
        val (session, registration, devices) = fixture()
        session.signedIn(customerUser())
        runCurrent()

        registration.onNewToken("fcm-2")
        runCurrent()

        assertEquals(listOf("fcm-1", "fcm-2"), devices.registered)
    }

    @Test
    fun `a rotated token is ignored while signed out`() = runTest {
        val (_, registration, devices) = fixture()
        registration.onNewToken("fcm-2")
        runCurrent()

        assertEquals(emptyList<String>(), devices.registered)
    }

    @Test
    fun `no push on this build means nothing is sent`() = runTest {
        val (session, _, devices) = fixture(FakeTokens(token = null))
        session.signedIn(customerUser())
        runCurrent()

        assertEquals(emptyList<String>(), devices.registered)
    }

    @Test
    fun `a failed registration is tried again when the token next arrives`() = runTest {
        val (session, registration, devices) = fixture()
        devices.result = ApiResult.Failure(ApiError.Network())
        session.signedIn(customerUser())
        runCurrent()

        devices.result = ApiResult.Success(Unit)
        registration.onNewToken("fcm-1")
        runCurrent()

        assertEquals(listOf("fcm-1", "fcm-1"), devices.registered)
    }
}
