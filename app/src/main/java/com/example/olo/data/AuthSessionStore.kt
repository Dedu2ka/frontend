package com.example.olo.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class AuthSession(
    val username: String,
    val userId: String,
    val token: String,
    val refreshToken: String? = null
)

class AuthSessionStore(context: Context) {
    private val preferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    fun saveSession(session: AuthSession) {
        val data = JSONObject()
            .put("username", session.username)
            .put("userId", session.userId)
            .put("token", session.token)
            .put("refreshToken", session.refreshToken)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val encrypted = ByteBuffer.allocate(cipher.iv.size + ciphertext.size)
        encrypted.put(cipher.iv)
        encrypted.put(ciphertext)
        preferences.edit().putString(SESSION_KEY, Base64.encodeToString(encrypted.array(), Base64.NO_WRAP)).apply()
    }

    fun readSession(): AuthSession? {
        val stored = readData() ?: return null
        val token = stored.optString("token").takeIf { it.isNotBlank() } ?: return null
        val userId = stored.optString("userId").takeIf { it.isNotBlank() } ?: return null
        val username = stored.optString("username").takeIf { it.isNotBlank() } ?: return null
        return AuthSession(username = username, userId = userId, token = token, refreshToken = stored.opt("refreshToken")?.toString()?.takeIf { it.isNotBlank() && it != "null" })
    }

    fun readSavedUsername(): String? =
        readData()?.optString("username")?.takeIf { it.isNotBlank() }

    fun clearSession() {
        val username = readSavedUsername().orEmpty()
        val data = JSONObject().put("username", username).toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        val encrypted = ByteBuffer.allocate(cipher.iv.size + ciphertext.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        preferences.edit().putString(SESSION_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply()
    }

    private fun readData(): JSONObject? {
        val encoded = preferences.getString(SESSION_KEY, null) ?: return null
        return try {
            val encrypted = Base64.decode(encoded, Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(encrypted)
            val iv = ByteArray(IV_LENGTH)
            buffer.get(iv)
            val ciphertext = ByteArray(buffer.remaining())
            buffer.get(ciphertext)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            JSONObject(String(cipher.doFinal(ciphertext), Charsets.UTF_8))
        } catch (_: Exception) {
            preferences.edit().remove(SESSION_KEY).apply()
            null
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val keySpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(keySpec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val SESSION_KEY = "encrypted_session"
        const val KEY_ALIAS = "olo_auth_session_key"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_LENGTH_BITS = 128
    }
}
