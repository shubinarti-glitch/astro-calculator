package ru.astrosmap.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.authDataStore by preferencesDataStore(name = "auth")

/**
 * Хранилище Bearer-токена: DataStore + шифрование AES-GCM ключом из Android Keystore
 * (androidx security-crypto устарела — шифруем сами, это ~30 строк).
 */
interface SessionIdentity {
    val owners: StateFlow<String?>
    suspend fun owner(): String?
}

class TokenStore(private val context: Context) : SessionIdentity {

    private val tokenKey = stringPreferencesKey("token")
    private val ownerKey = stringPreferencesKey("verified_owner")
    private val mutex = Mutex()
    private val _owners = MutableStateFlow<String?>(null)
    /** null: authenticated identity unresolved (or still loading); never falls back to guest. */
    override val owners: StateFlow<String?> = _owners.asStateFlow()
    @Volatile private var cached: String? = null
    @Volatile private var loaded = false

    private suspend fun loadLocked() {
        if (loaded) return
        val prefs = context.authDataStore.data.first()
        cached = prefs[tokenKey]?.let(::decrypt)
        _owners.value = if (cached == null) GUEST else prefs[ownerKey]?.takeIf { it.matches(Regex("user:[1-9][0-9]*")) }
        loaded = true
    }

    suspend fun get(): String? = mutex.withLock { loadLocked(); cached }
    override suspend fun owner(): String? = mutex.withLock { loadLocked(); _owners.value }

    suspend fun save(token: String) = mutex.withLock {
        // A new token has no trusted owner until /me has verified it.
        context.authDataStore.edit { it[tokenKey] = encrypt(token); it.remove(ownerKey) }
        cached = token
        loaded = true
        _owners.value = null
    }

    suspend fun bindOwner(expectedToken: String, userId: Long): Boolean = mutex.withLock {
        loadLocked()
        if (cached != expectedToken || userId <= 0) return@withLock false
        val owner = "user:$userId"
        context.authDataStore.edit { it[ownerKey] = owner }
        _owners.value = owner
        true
    }

    suspend fun withCurrentSession(expectedOwner: String?, expectedToken: String?, action: () -> Unit): Boolean = mutex.withLock {
        loadLocked()
        if (_owners.value != expectedOwner || cached != expectedToken) false else { action(); true }
    }

    suspend fun clearIf(expectedToken: String) = mutex.withLock {
        loadLocked()
        if (cached == expectedToken) clearLocked()
    }

    suspend fun clear() = mutex.withLock { clearLocked() }

    private suspend fun clearLocked() {
        context.authDataStore.edit { it.remove(tokenKey); it.remove(ownerKey) }
        cached = null
        loaded = true
        _owners.value = GUEST
    }


    // ------------------------------------------------------------------ #

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val bytes = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String? = runCatching {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_LEN))
        String(cipher.doFinal(bytes, IV_LEN, bytes.size - IV_LEN))
    }.getOrNull() // ключ Keystore мог «протухнуть» (сброс устройства) — просто разлогин

    companion object {
        const val GUEST = "guest"
        private const val ALIAS = "astro_token_key"
        private const val IV_LEN = 12
    }
}
