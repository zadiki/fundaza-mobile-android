package africa.fundaza.customer.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "session")

/**
 * The API is explicit: the token is "shown once and never retrievable again;
 * store it in the platform keystore, not in preferences". So the token is
 * encrypted with an AES-GCM key that never leaves the Android Keystore, and
 * only the ciphertext touches disk.
 */
@Singleton
class KeystoreTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : TokenStore {

    override suspend fun load(): StoredToken? {
        val prefs = context.sessionStore.data.first()
        val cipherText = prefs[CIPHER_TEXT] ?: return null
        val iv = prefs[IV] ?: return null
        val token = runCatching { decrypt(cipherText, iv) }.getOrNull()
        if (token == null) {
            // Key was invalidated (e.g. device restored); the token is unrecoverable.
            clear()
            return null
        }
        val expiresAt = prefs[EXPIRES_AT]?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return StoredToken(token, expiresAt)
    }

    override suspend fun save(token: StoredToken) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(token.token.toByteArray(Charsets.UTF_8))
        context.sessionStore.edit {
            it[CIPHER_TEXT] = Base64.encodeToString(encrypted, Base64.NO_WRAP)
            it[IV] = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            if (token.expiresAt != null) it[EXPIRES_AT] = token.expiresAt.toString() else it.remove(EXPIRES_AT)
        }
    }

    override suspend fun clear() {
        context.sessionStore.edit { it.clear() }
    }

    private fun decrypt(cipherText: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)), Charsets.UTF_8)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "fundaza_session_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        val CIPHER_TEXT = stringPreferencesKey("token_ct")
        val IV = stringPreferencesKey("token_iv")
        val EXPIRES_AT = stringPreferencesKey("expires_at")
    }
}
