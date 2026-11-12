package africa.fundaza.customer.core.push

import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.core.util.ApplicationScope
import africa.fundaza.customer.data.repository.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the server's record of this phone's push token current.
 *
 * The token is registered against whoever is signed in: on every sign-in (the
 * server moves a token that belonged to someone else, so a shared phone follows
 * its current user), and again whenever Firebase rotates the token. Signing out
 * needs nothing here — the server stops pushing to a principal with no live token.
 *
 * A failed registration is not retried on a timer: the next sign-in, app launch
 * or token rotation tries again, and until then the customer is emailed and texted.
 */
@Singleton
class DeviceRegistration @Inject constructor(
    private val session: SessionManager,
    private val tokens: PushTokenSource,
    private val devices: DeviceRepository,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val lock = Mutex()

    /** The (user, token) pair the server last accepted, so a profile refresh doesn't re-register. */
    private var registered: Pair<Long, String>? = null

    fun start() {
        scope.launch {
            session.state
                .map { (it as? SessionState.SignedIn)?.user?.id }
                .distinctUntilChanged()
                .collect { userId ->
                    if (userId == null) {
                        lock.withLock { registered = null }
                    } else {
                        tokens.token()?.let { register(userId, it) }
                    }
                }
        }
    }

    /** Firebase issued a new token. */
    fun onNewToken(token: String) {
        scope.launch {
            val userId = (session.state.value as? SessionState.SignedIn)?.user?.id ?: return@launch
            register(userId, token)
        }
    }

    private suspend fun register(userId: Long, token: String) = lock.withLock {
        if (registered == userId to token) return@withLock
        // Signed out while the token was being fetched.
        if ((session.state.value as? SessionState.SignedIn)?.user?.id != userId) return@withLock
        if (devices.register(token) is ApiResult.Success) registered = userId to token
    }
}
