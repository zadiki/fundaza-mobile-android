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
            is ApiResult.Success ->
                if (result.value.isCustomer) session.signedIn(result.value) else session.end()
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

    /** Named so the customer can tell which device to revoke. */
    private fun deviceName(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        val name = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model"
        return name.take(120)
    }
}
