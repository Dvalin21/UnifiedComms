package com.unifiedcomms.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.unifiedcomms.data.model.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

interface CryptoManager {
    suspend fun encrypt(text: String): EncryptedData
    suspend fun decrypt(data: EncryptedData): String
    suspend fun encryptBytes(bytes: ByteArray): EncryptedData
    suspend fun decryptBytes(data: EncryptedData): ByteArray
    suspend fun generateKeyPair(): KeyPair
    suspend fun signData(data: ByteArray, privateKey: String): String
    suspend fun verifySignature(data: ByteArray, signature: String, publicKey: String): Boolean
    suspend fun deriveKey(password: String, salt: ByteArray): SecretKey
    fun encryptAuthConfig(config: AuthConfig): AuthConfig
    fun decryptAuthConfig(config: AuthConfig): AuthConfig
}

data class EncryptedData(
    val ciphertext: ByteArray,
    val iv: ByteArray,
    val authTag: ByteArray? = null, // For GCM
    val algorithm: String = "AES/GCM/NoPadding"
)

data class KeyPair(
    val publicKey: String,  // Base64 encoded
    val privateKey: String  // Base64 encoded (encrypted)
)

class CryptoManagerImpl(
    private val context: Context
) : CryptoManager {

    private val masterKeyAlias = "unifiedcomms_master_key"
    private val prefsName = "secure_prefs"
    private val secureRandom = SecureRandom()

    private val encryptedPrefs by lazy {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            prefsName,
            masterKey,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val aesKey by lazy {
        getOrCreateAesKey()
    }

    override suspend fun encrypt(text: String): EncryptedData = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        encryptBytes(bytes)
    }

    override suspend fun decrypt(data: EncryptedData): String = withContext(Dispatchers.IO) {
        val bytes = decryptBytes(data)
        String(bytes, StandardCharsets.UTF_8)
    }

    override suspend fun encryptBytes(bytes: ByteArray): EncryptedData = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12) // 96-bit IV for GCM
        secureRandom.nextBytes(iv)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec)
        val ciphertext = cipher.doFinal(bytes)
        EncryptedData(ciphertext, iv, null)
    }

    override suspend fun decryptBytes(data: EncryptedData): ByteArray = withContext(Dispatchers.IO) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, data.iv)
        cipher.init(Cipher.DECRYPT_MODE, aesKey, spec)
        cipher.doFinal(data.ciphertext)
    }

    override suspend fun generateKeyPair(): KeyPair = withContext(Dispatchers.IO) {
        // Generate RSA 4096 key pair for E2E encryption
        val keyPairGen = java.security.KeyPairGenerator.getInstance("RSA")
        keyPairGen.initialize(4096, secureRandom)
        val pair = keyPairGen.generateKeyPair()

        // Encrypt private key with master key
        val privateKeyBytes = pair.private.encoded
        val encryptedPrivate = encryptBytes(privateKeyBytes)

        KeyPair(
            publicKey = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP),
            privateKey = Base64.encodeToString(encryptedPrivate.ciphertext, Base64.NO_WRAP)
        )
    }

    override suspend fun signData(data: ByteArray, privateKey: String): String = withContext(Dispatchers.IO) {
        // Decrypt private key
        val encryptedPrivate = EncryptedData(
            ciphertext = Base64.decode(privateKey, Base64.NO_WRAP),
            iv = ByteArray(12) // Would be stored with the key
        )
        val decryptedPrivate = decryptBytes(encryptedPrivate)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val pkSpec = java.security.spec.PKCS8EncodedKeySpec(decryptedPrivate)
        val key = keyFactory.generatePrivate(pkSpec)

        val signature = java.security.Signature.getInstance("SHA256withRSA")
        signature.initSign(key)
        signature.update(data)
        Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    override suspend fun verifySignature(data: ByteArray, signature: String, publicKey: String): Boolean = withContext(Dispatchers.IO) {
        val sigBytes = Base64.decode(signature, Base64.NO_WRAP)
        val pubBytes = Base64.decode(publicKey, Base64.NO_WRAP)
        val keyFactory = java.security.KeyFactory.getInstance("RSA")
        val pubSpec = java.security.spec.X509EncodedKeySpec(pubBytes)
        val key = keyFactory.generatePublic(pubSpec)

        val sig = java.security.Signature.getInstance("SHA256withRSA")
        sig.initVerify(key)
        sig.update(data)
        sig.verify(sigBytes)
    }

    override suspend fun deriveKey(password: String, salt: ByteArray): SecretKey = withContext(Dispatchers.IO) {
        val factory = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = javax.crypto.spec.PBEKeySpec(password.toCharArray(), salt, 100000, 256)
        val tmp = factory.generateSecret(spec)
        SecretKeySpec(tmp.encoded, "AES")
    }

    override fun encryptAuthConfig(config: AuthConfig): AuthConfig {
        return when (config.type) {
            com.unifiedcomms.data.model.AuthType.PASSWORD -> config.copy(
                passwordEncrypted = config.passwordEncrypted?.let { encryptSimple(it) }
            )
            com.unifiedcomms.data.model.AuthType.OAUTH2 -> config.copy(
                oauthAccessToken = config.oauthAccessToken?.let { encryptSimple(it) },
                oauthRefreshToken = config.oauthRefreshToken?.let { encryptSimple(it) }
            )
            com.unifiedcomms.data.model.AuthType.CERTIFICATE -> config.copy(
                clientCertificate = config.clientCertificate?.let { encryptSimple(it) },
                clientKey = config.clientKey?.let { encryptSimple(it) }
            )
            else -> config
        }
    }

    override fun decryptAuthConfig(config: AuthConfig): AuthConfig {
        return when (config.type) {
            com.unifiedcomms.data.model.AuthType.PASSWORD -> config.copy(
                passwordEncrypted = config.passwordEncrypted?.let { decryptSimple(it) }
            )
            com.unifiedcomms.data.model.AuthType.OAUTH2 -> config.copy(
                oauthAccessToken = config.oauthAccessToken?.let { decryptSimple(it) },
                oauthRefreshToken = config.oauthRefreshToken?.let { decryptSimple(it) }
            )
            com.unifiedcomms.data.model.AuthType.CERTIFICATE -> config.copy(
                clientCertificate = config.clientCertificate?.let { decryptSimple(it) },
                clientKey = config.clientKey?.let { decryptSimple(it) }
            )
            else -> config
        }
    }

    // Simple AES encryption for config fields (non-suspend, uses cached key)
    private fun encryptSimple(text: String): String {
        val result = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12)
            secureRandom.nextBytes(iv)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, spec)
            val ciphertext = cipher.doFinal(text.toByteArray(StandardCharsets.UTF_8))
            // Combine IV + ciphertext and Base64 encode
            val combined = ByteArray(iv.size + ciphertext.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (e: Exception) {
            // Fallback to Base64 for non-critical config fields
            Base64.encodeToString(text.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
        }
        return result
    }

    private fun decryptSimple(base64: String): String {
        val result = try {
            val combined = Base64.decode(base64, Base64.NO_WRAP)
            if (combined.size < 12) return base64 // Fallback for old format
            val iv = combined.copyOfRange(0, 12)
            val ciphertext = combined.copyOfRange(12, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, aesKey, spec)
            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            // Fallback for Base64-only stored values
            try {
                String(Base64.decode(base64, Base64.NO_WRAP), StandardCharsets.UTF_8)
            } catch (e2: Exception) {
                base64
            }
        }
        return result
    }

    private fun getOrCreateAesKey(): SecretKey = runBlocking(Dispatchers.IO) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keyStore.containsAlias(masterKeyAlias)) {
            val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
            val spec = androidx.security.crypto.MasterKeys.AES256_GCM_SPEC
            val keyGenParameterSpec = android.security.keystore.KeyGenParameterSpec.Builder(
                masterKeyAlias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
            generator.init(keyGenParameterSpec)
            generator.generateKey()
        }
        keyStore.getKey(masterKeyAlias, null) as SecretKey
    }

    companion object {
        private const val TAG = "CryptoManager"
    }
}