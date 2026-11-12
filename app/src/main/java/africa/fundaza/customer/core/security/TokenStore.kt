package africa.fundaza.customer.core.security

import java.time.Instant

data class StoredToken(val token: String, val expiresAt: Instant?) {
    fun isExpired(now: Instant = Instant.now()): Boolean = expiresAt?.isBefore(now) ?: false
}

/** Where the bearer token lives between launches. */
interface TokenStore {
    suspend fun load(): StoredToken?
    suspend fun save(token: StoredToken)
    suspend fun clear()
}
