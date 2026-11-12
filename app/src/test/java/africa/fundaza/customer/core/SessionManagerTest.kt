package africa.fundaza.customer.core

import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.core.security.StoredToken
import africa.fundaza.customer.core.security.TokenStore
import africa.fundaza.customer.testing.customerUser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private class MemoryTokenStore(var stored: StoredToken? = null) : TokenStore {
        override suspend fun load() = stored
        override suspend fun save(token: StoredToken) { stored = token }
        override suspend fun clear() { stored = null }
    }

    @Test
    fun `a rejected token ends the session and says why`() = runTest {
        val store = MemoryTokenStore()
        val session = SessionManager(store, this)
        session.beginWith(StoredToken("17|abc", null))
        session.signedIn(customerUser())

        session.onTokenRejected("17|abc")
        advanceUntilIdle()

        assertNull(session.currentToken())
        assertNull(store.stored)
        assertEquals(SessionState.SignedOut("Your session has ended. Please sign in again."), session.state.value)
    }

    @Test
    fun `a late 401 for an old token does not sign out the new one`() = runTest {
        val session = SessionManager(MemoryTokenStore(), this)
        session.beginWith(StoredToken("18|new", null))
        session.signedIn(customerUser())

        session.onTokenRejected("17|old")

        assertEquals("18|new", session.currentToken())
        assertTrue(session.state.value is SessionState.SignedIn)
    }

    @Test
    fun `an expired stored token is discarded at launch`() = runTest {
        val store = MemoryTokenStore(StoredToken("17|abc", Instant.now().minusSeconds(60)))
        val session = SessionManager(store, TestScope(testScheduler))

        assertFalse(session.loadStoredToken())
        assertNull(store.stored)
    }
}
