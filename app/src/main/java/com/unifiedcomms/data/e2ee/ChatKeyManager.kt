package com.unifiedcomms.data.e2ee

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Signal-lite ECDH key management for UnifiedComms chat.
 *
 * Device holds:
 * - identity keypair (EC P-256) — long-term, identifies the device
 * - signed prekey (EC P-256) — medium-term, used as DH root for incoming messages
 * - ephemeral keypair per outgoing message — in-memory only, provides forward secrecy
 *
 * On send:
 *   1. Fetch recipient's signed_prekey_pub from relay (/v1/identity/{phone})
 *   2. Generate ephemeral keypair (in-memory)
 *   3. ECDH(ephemeral_priv, recipient_prekey_pub) → shared secret
 *   4. HKDF-SHA256(shared_secret, salt=ephemeral_pub_bytes, info="UnifiedComms") → AES-256 key
 *   5. Encrypt payload with AES-256-GCM (reuses ChatCryptoManager.encryptWithKey)
 *   6. POST envelope: ciphertext, ephemeral_pub (Base64 X.509), chain_index, ...
 *
 * On receive:
 *   1. Fetch envelope (ciphertext, ephemeral_pub, sender) from relay inbox
 *   2. ECDH(local_prekey_priv, sender_ephemeral_pub) → shared secret
 *   3. Same HKDF → same AES-256 key
 *   4. Decrypt with ChatCryptoManager.decryptWithKey
 *
 * The relay never sees key material — it stores ciphertext + ephemeral_pub verbatim.
 */
class ChatKeyManager(
    private val context: Context,
) {
    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val IDENTITY_ALIAS = "uc_chat_identity"
        private const val PREKEY_ALIAS = "uc_chat_prekey"
        private const val EC_CURVE = "secp256r1"
        private const val HKDF_INFO = "UnifiedComms-chat-v1"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    // ── Identity ───────────────────────────────────────────────────────

    /** Ensure identity keypair exists. Returns Base64 X.509 DER of public key. */
    @JvmName("ensureIdentityPublicKey")
    suspend fun ensureIdentity(): String = withContext(Dispatchers.IO) {
        if (!keyStore.containsAlias(IDENTITY_ALIAS)) {
            generateEcKeyPair(IDENTITY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_AGREE_KEY)
        }
        getPublicKeyBase64(IDENTITY_ALIAS)
    }

    /** Sign the prekey public key bytes with the identity private key. */
    suspend fun signPreKey(preKeyPubBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val identityPriv = getPrivateKey(IDENTITY_ALIAS)
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(identityPriv)
        sig.update(preKeyPubBytes)
        Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
    }

    // ── Prekey ─────────────────────────────────────────────────────────

    /** Ensure prekey exists. Returns Base64 X.509 DER of public key. */
    @JvmName("ensurePreKeyPublicKey")
    suspend fun ensurePreKey(): String = withContext(Dispatchers.IO) {
        if (!keyStore.containsAlias(PREKEY_ALIAS)) {
            generateEcKeyPair(PREKEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_AGREE_KEY)
        }
        getPublicKeyBase64(PREKEY_ALIAS)
    }

    /** Return the prekey private key (for ECDH on incoming messages). */
    suspend fun getPreKeyPrivateKey(): PrivateKey = withContext(Dispatchers.IO) {
        getPrivateKey(PREKEY_ALIAS)
    }

    // ── Ephemeral (per-message, in-memory) ────────────────────────────

    /**
     * Generate an ephemeral EC P-256 keypair for a single outgoing message.
     * Returns the private key (for ECDH) and the Base64-encoded X.509 public key
     * (for the relay envelope's ephemeral_pub field).
     * The caller must wipe the private key after use — we cannot persist it.
     */
    fun generateEphemeralKeyPair(): EphemeralKeypair {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec(EC_CURVE), SecureRandom())
        val kp: KeyPair = gen.generateKeyPair()
        return EphemeralKeypair(
            privateKey = kp.private,
            publicKeyBase64 = Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP),
        )
    }

    // ── ECDH + HKDF ────────────────────────────────────────────────────

    /**
     * ECDH: local private key (identity or prekey) × remote public key (Base64 X.509 DER)
     * → raw shared secret bytes.
     */
    fun ecdh(localPriv: PrivateKey, remotePubBase64: String): ByteArray {
        val remotePubBytes = Base64.decode(remotePubBase64, Base64.NO_WRAP)
        val kf = java.security.KeyFactory.getInstance("EC")
        val remotePub = kf.generatePublic(java.security.spec.X509EncodedKeySpec(remotePubBytes))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(localPriv)
        ka.doPhase(remotePub, true)
        return ka.generateSecret()
    }

    /**
     * HKDF-SHA256 extract-then-expand → 32-byte AES-256 key.
     * Salt = ephemeral public key bytes (unique per message → per-message forward secrecy).
     * Info = hardcoded string, both sides must agree.
     */
    fun hkdfSha256(sharedSecret: ByteArray, saltBytes: ByteArray): SecretKeySpec {
        // Extract
        val prk = hmacSha256(
            if (saltBytes.isEmpty()) ByteArray(32) else saltBytes,
            sharedSecret,
        )
        // Expand
        val info = HKDF_INFO.toByteArray(Charsets.UTF_8)
        val keyLen = 32
        val blocks = (keyLen + 31) / 32
        val ret = ByteArray(keyLen)
        var t = ByteArray(0)
        var offset = 0
        for (i in 1..blocks) {
            val blockInput = java.io.ByteArrayOutputStream().use { os ->
                os.write(t)
                os.write(info)
                os.write(i)
                os.toByteArray()
            }
            t = hmacSha256(prk, blockInput)
            val toCopy = minOf(32, keyLen - offset)
            System.arraycopy(t, 0, ret, offset, toCopy)
            offset += toCopy
        }
        return SecretKeySpec(ret, "AES")
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private suspend fun generateEcKeyPair(alias: String, purposes: Int) = withContext(Dispatchers.IO) {
        val gen = KeyPairGenerator.getInstance("EC", KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(alias, purposes)
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        gen.initialize(spec)
        gen.generateKeyPair()
    }

    private fun getPublicKeyBase64(alias: String): String {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Keystore missing alias: $alias")
        return Base64.encodeToString(entry.certificate.publicKey.encoded, Base64.NO_WRAP)
    }

    private fun getPrivateKey(alias: String): PrivateKey {
        val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            ?: throw IllegalStateException("Keystore missing alias: $alias")
        return entry.privateKey
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    // ── 데이터 클래스 ────────────────────────────────────────────────

    data class EphemeralKeypair(
        val privateKey: PrivateKey,
        val publicKeyBase64: String,
    )
}
