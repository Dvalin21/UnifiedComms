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
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return cipher.doFinal(data)
    }

    override fun decrypt(encrypted: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey())
        return cipher.doFinal(encrypted)
    }

    @Throws(java.security.UnrecoverableKeyException::class)
    override fun decryptAuthConfig(encrypted: com.unifiedcomms.data.model.AuthConfig): com.unifiedcomms.data.model.AuthConfig {
        return encrypted
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
    fun decryptAuthConfig(encrypted: com.unifiedcomms.data.model.AuthConfig): com.unifiedcomms.data.model.AuthConfig
}

object CryptoManagerFactory {
    fun create(context: android.content.Context): CryptoManager = CryptoManagerImpl(context)
}
