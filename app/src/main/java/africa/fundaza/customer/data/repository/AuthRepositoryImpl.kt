package africa.fundaza.customer.data.repository

import android.os.Build
import africa.fundaza.customer.core.network.ApiCaller
import africa.fundaza.customer.core.network.ApiError
import africa.fundaza.customer.core.network.ApiResult
import africa.fundaza.customer.core.security.SessionManager
import africa.fundaza.customer.core.security.StoredToken
import africa.fundaza.customer.data.remote.FundazaApi
import africa.fundaza.customer.data.remote.dto.TokenRequest
import africa.fundaza.customer.data.remote.parseInstant
import africa.fundaza.customer.data.remote.toDomain
import africa.fundaza.customer.domain.model.CurrentUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: FundazaApi,
    private val call: ApiCaller,
    private val session: SessionManager,
) : AuthRepository {

    override suspend fun restore() {
        if (!session.loadStoredToken()) {
            session.end()
            return
        }
        when (val result = call { api.me().data.toDomain() }) {
            is ApiResult.Success -> when {
                result.value.isCustomer -> session.signedIn(result.value)
                result.value.principalType == null -> resumeSignUp(onNone = { session.end() })
                else -> session.end()
            }
            is ApiResult.Failure -> when (result.error) {
                // The interceptor has already ended the session.
                is ApiError.Unauthenticated -> Unit
                // Offline at launch: keep the token (it is still good) and let them retry.
                else -> session.unreachable(result.error.message)
            }
        }
    }

    override suspend fun signIn(email: String, password: String): ApiResult<SignInResult> {
        val issued = call {
            api.issueToken(TokenRequest(email.trim(), password, deviceName())).data
        }
        if (issued is ApiResult.Failure) return issued
        val token = (issued as ApiResult.Success).value
        session.beginWith(StoredToken(token.token, parseInstant(token.expiresAt)))

        return when (val me = call { api.me().data.toDomain() }) {
            is ApiResult.Failure -> {
                session.end()
                me
            }
            is ApiResult.Success -> {
                if (me.value.isCustomer) {
                    session.signedIn(me.value)
                    ApiResult.Success(SignInResult.SignedIn(me.value))
                } else if (me.value.principalType == null && resumeSignUp(onNone = {})) {
                    // Signed up on this or another device and not finished: pick up where they left off.
                    ApiResult.Success(SignInResult.SigningUp)
                } else {
                    // Don't leave a staff or agent token lying on a customer app.
                    call { api.revokeCurrentToken() }
                    session.end()
                    ApiResult.Success(SignInResult.NotACustomer)
                }
            }
        }
    }

    override suspend fun signOut() {
        // Revoke server-side first; sign out locally whatever the answer.
        call { api.revokeCurrentToken() }
        session.end()
    }

    override suspend fun refreshUser(): ApiResult<CurrentUser> {
        val result = call { api.me().data.toDomain() }
        if (result is ApiResult.Success) session.signedIn(result.value)
        return result
    }

    /**
     * A login with no principal is either somebody mid-sign-up or an account
     * nobody has provisioned. Returns true when it was a sign-up and the
     * session now follows it. A network failure leaves the app "unreachable"
     * with the token kept, as for any other launch.
     */
    private suspend fun resumeSignUp(onNone: suspend () -> Unit): Boolean =
        when (val signUp = call { api.currentSignUp().data.toDomain() }) {
            is ApiResult.Success -> {
                session.signingUp(signUp.value)
                true
            }
            is ApiResult.Failure -> {
                when (signUp.error) {
                    is ApiError.NotFound -> onNone()
                    is ApiError.Unauthenticated -> Unit
                    else -> session.unreachable(signUp.error.message)
                }
                false
            }
        }

    /** Named so the customer can tell which device to revoke. */
    internal fun deviceName(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val name = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
        return name.take(120)
    }
}
