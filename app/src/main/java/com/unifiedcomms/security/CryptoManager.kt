package com.unifiedcomms.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class CryptoManagerImpl(private val context: android.content.Context) : CryptoManager {
    private val masterKeyAlias = "_androidx_security_master_key_"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun encrypt(data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getOrCreateKey()
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(data)
        return iv + ciphertext
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        if (encrypted.size < 12) throw IllegalArgumentException("Ciphertext too short for AES/GCM")
        val iv = encrypted.copyOfRange(0, 12)
        val ciphertext = encrypted.copyOfRange(12, encrypted.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = getOrCreateKey()
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    override fun encryptAuthConfig(config: com.unifiedcomms.data.model.AuthConfig): com.unifiedcomms.data.model.AuthConfig {
        return config.copy(
            passwordEncrypted = config.passwordEncrypted?.let { encryptField(it) },
            clientKey = config.clientKey?.let { encryptField(it) },
            clientCertificate = config.clientCertificate?.let { encryptField(it) },
            oauthAccessToken = config.oauthAccessToken?.let { encryptField(it) },
            oauthRefreshToken = config.oauthRefreshToken?.let { encryptField(it) }
        )
    }

    override fun decryptAuthConfig(config: com.unifiedcomms.data.model.AuthConfig): com.unifiedcomms.data.model.AuthConfig {
        return config.copy(
            passwordEncrypted = decryptField(config.passwordEncrypted),
            clientKey = decryptField(config.clientKey),
            clientCertificate = decryptField(config.clientCertificate),
            oauthAccessToken = decryptField(config.oauthAccessToken),
            oauthRefreshToken = decryptField(config.oauthRefreshToken)
        )
    }

    private fun decryptField(value: String?): String? {
        if (value == null) return null
        return try {
            val bytes = android.util.Base64.decode(value, android.util.Base64.DEFAULT)
            String(decrypt(bytes), Charsets.UTF_8)
        } catch (e: Exception) {
            value
        }
    }

    private fun encryptField(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        return android.util.Base64.encodeToString(encrypt(bytes), android.util.Base64.NO_WRAP)
    }

    @Throws(java.security.UnrecoverableKeyException::class)
    private fun getOrCreateKey(): SecretKey {
        return if (keyStore.containsAlias(masterKeyAlias)) {
            (keyStore.getEntry(masterKeyAlias, null) as? java.security.KeyStore.SecretKeyEntry)?.secretKey
                ?: createKey()
        } else {
            createKey()
        }
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(masterKeyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
}

interface CryptoManager {
    fun encrypt(data: ByteArray): ByteArray
    fun decrypt(encrypted: ByteArray): ByteArray
    fun encryptAuthConfig(config: com.unifiedcomms.data.model.AuthConfig): com.unifiedcomms.data.model.AuthConfig
    fun decryptAuthConfig(encrypted: com.unifiedcomms.data.model.AuthConfig): com.unifiedcomms.data.model.AuthConfig
}

object CryptoManagerFactory {
    fun create(context: android.content.Context): CryptoManager = CryptoManagerImpl(context)
}
