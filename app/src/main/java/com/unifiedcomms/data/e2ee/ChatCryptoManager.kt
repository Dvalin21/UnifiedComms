package com.unifiedcomms.data.e2ee

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.KeyStore
import java.security.SecureRandom

/**
 * ChatCryptoManager — AES-256-GCM encryption for chat messages.
 *
 * Uses a pre-shared key (PSK) stored in Android Keystore.
 * For v1, users exchange a chat secret via QR code or copy-paste.
 * The PSK is derived from that secret via HKDF and used for all encrypt/decrypt.
 *
 * Wire format: Base64(nonce[12] + ciphertext[gcm_tag + body])
 */
class ChatCryptoManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "uc_chat_psk"
    }

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun generatePreSharedKey(): String {
        val key = ByteArray(32)
        SecureRandom.getInstanceStrong().nextBytes(key)
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    suspend fun installPreSharedKey(pskBase64: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val psk = Base64.decode(pskBase64, Base64.DEFAULT)
            if (psk.size != 32) return@withContext false
            val keySpec = SecretKeySpec(psk, "AES")
            keyStore.setEntry(KEY_ALIAS, KeyStore.SecretKeyEntry(keySpec), null)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun hasKey(): Boolean = keyStore.containsAlias(KEY_ALIAS)

    fun deleteKey() {
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    suspend fun encrypt(plaintext: String): String = withContext(Dispatchers.IO) {
        val key = getOrCreateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = nonce + ciphertext
        Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    suspend fun decrypt(ciphertextBase64: String): String = withContext(Dispatchers.IO) {
        val key = getOrCreateKey()
        val data = Base64.decode(ciphertextBase64, Base64.DEFAULT)
        if (data.size < 12) throw IllegalArgumentException("Ciphertext too short")
        val nonce = data.copyOfRange(0, 12)
        val ct = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        return if (keyStore.containsAlias(KEY_ALIAS)) {
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
                ?: throw IllegalStateException("Key alias exists but entry is not a SecretKey")
        } else {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
            generator.init(spec)
            generator.generateKey()
        }
    }
}
