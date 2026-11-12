package africa.fundaza.customer.core.security

import africa.fundaza.customer.core.util.ApplicationScope
import africa.fundaza.customer.domain.model.CurrentUser
import africa.fundaza.customer.domain.model.SignUp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    /** Reading the stored token at launch; the splash screen stays up. */
    data object Restoring : SessionState

    /** [notice] explains why, when the app ended the session rather than the person. */
    data class SignedOut(val notice: String? = null) : SessionState

    /** [justJoined] is set when this session began by finishing a sign-up, so the app opens verification. */
    data class SignedIn(val user: CurrentUser, val justJoined: Boolean = false) : SessionState

    /**
     * A login with no customer behind it yet: it signed up and has not finished
     * (confirm the email, perhaps the phone, then give their details).
     */
    data class SigningUp(val signUp: SignUp) : SessionState

    /** A stored token exists but the server couldn't be reached to confirm who it is. */
    data class Unreachable(val message: String) : SessionState
}

/**
 * The one place that knows whether someone is signed in. The token is held in
 * memory for the network layer (an interceptor cannot suspend) and mirrored to
 * [TokenStore] so it survives a restart.
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    private var token: String? = null

    fun currentToken(): String? = token

    /** Loads a stored, unexpired token into memory. Returns false if there is none. */
    suspend fun loadStoredToken(): Boolean {
        val stored = tokenStore.load()
        if (stored == null || stored.isExpired()) {
            if (stored != null) tokenStore.clear()
            token = null
            return false
        }
        token = stored.token
        return true
    }

    /** A token the server just issued, before we know who it belongs to. */
    suspend fun beginWith(stored: StoredToken) {
        tokenStore.save(stored)
        token = stored.token
    }

    fun signedIn(user: CurrentUser, justJoined: Boolean = false) {
        _state.value = SessionState.SignedIn(user, justJoined)
    }

    fun signingUp(signUp: SignUp) {
        _state.value = SessionState.SigningUp(signUp)
    }

    fun unreachable(message: String) {
        _state.value = SessionState.Unreachable(message)
    }

    fun restoring() {
        _state.value = SessionState.Restoring
    }

    suspend fun end(notice: String? = null) {
        token = null
        tokenStore.clear()
        _state.value = SessionState.SignedOut(notice)
    }

    /** Called from the network thread when the server refuses [rejected]. */
    fun onTokenRejected(rejected: String) {
        if (token != rejected) return
        token = null
        val wasSignedIn = _state.value is SessionState.SignedIn || _state.value is SessionState.SigningUp
        _state.value = SessionState.SignedOut(
            if (wasSignedIn) "Your session has ended. Please sign in again." else null,
        )
        scope.launch { tokenStore.clear() }
    }
}
