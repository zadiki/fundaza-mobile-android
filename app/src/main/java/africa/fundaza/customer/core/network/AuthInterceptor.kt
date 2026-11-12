package africa.fundaza.customer.core.network

import africa.fundaza.customer.core.security.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Attaches the bearer token, and ends the session when the server stops
 * accepting it — wherever in the app that happens to be noticed first.
 */
class AuthInterceptor @Inject constructor(
    private val session: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.currentToken()
        val request = chain.request().newBuilder()
            .header("Accept", "application/json")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()

        val response = chain.proceed(request)
        if (response.code == 401 && token != null) {
            session.onTokenRejected(token)
        }
        return response
    }
}
