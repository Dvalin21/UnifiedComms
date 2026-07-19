package com.unifiedcomms.security

import android.content.Context
import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.CompletableDeferred

interface BiometricAuthManager {
    val canAuthenticate: Boolean
    val biometricType: BiometricType
    suspend fun authenticate(reason: String, activity: FragmentActivity): AuthenticationResult
    fun hasEnrolledBiometrics(): Boolean
}

enum class BiometricType {
    NONE,
    FINGERPRINT,
    FACE,
    IRIS,
    STRONG,
    WEAK,
    DEVICE_CREDENTIAL
}

sealed interface AuthenticationResult {
    data class Success(val cryptoObject: BiometricPrompt.CryptoObject? = null) : AuthenticationResult
    data class Failure(val errorCode: Int, val errorString: String) : AuthenticationResult
    data class Error(val errorCode: Int, val errorString: String) : AuthenticationResult
    object UserCancel : AuthenticationResult
    object Timeout : AuthenticationResult
    object Lockout : AuthenticationResult
}

class BiometricManagerImpl(
    private val context: Context
) : BiometricAuthManager {

    override val canAuthenticate: Boolean
        get() = AndroidBiometricManager.from(context).canAuthenticate(
            // ponytail: must match the level the prompt actually requests
            // (BIOMETRIC_WEAK or DEVICE_CREDENTIAL). Requiring BIOMETRIC_STRONG
            // here while the prompt allows WEAK made the gate disagree with the
            // prompt and trapped devices whose enrolled biometric is WEAK (most
            // OEM fingerprint implementations) behind an "unavailable" lock.
            AndroidBiometricManager.Authenticators.BIOMETRIC_WEAK or AndroidBiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == AndroidBiometricManager.BIOMETRIC_SUCCESS

    override val biometricType: BiometricType
        get() = when {
            !canAuthenticate -> BiometricType.NONE
            AndroidBiometricManager.from(context).canAuthenticate(AndroidBiometricManager.Authenticators.DEVICE_CREDENTIAL) == AndroidBiometricManager.BIOMETRIC_SUCCESS -> {
                // ponytail: a device that can ONLY satisfy DEVICE_CREDENTIAL has no
                // biometric enrolled; labeling it STRONG was a lie. Report the precise
                // capability so callers don't promise a fingerprint prompt that won't come.
                BiometricType.DEVICE_CREDENTIAL
            }
            else -> BiometricType.WEAK
        }

    override fun hasEnrolledBiometrics(): Boolean = canAuthenticate

    override suspend fun authenticate(reason: String, activity: FragmentActivity): AuthenticationResult =
        awaitAuthentication(reason, activity)

    private suspend fun awaitAuthentication(reason: String, activity: FragmentActivity): AuthenticationResult {
        val deferred = CompletableDeferred<AuthenticationResult>()

        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("UnifiedComms Authentication")
            .setSubtitle(reason)
            .setDescription("Use your biometric to unlock UnifiedComms")
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(
                Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                deferred.complete(AuthenticationResult.Success(result.cryptoObject))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED -> deferred.complete(AuthenticationResult.UserCancel)
                    BiometricPrompt.ERROR_TIMEOUT -> deferred.complete(AuthenticationResult.Timeout)
                    BiometricPrompt.ERROR_LOCKOUT -> deferred.complete(AuthenticationResult.Lockout)
                    else -> deferred.complete(AuthenticationResult.Error(errorCode, errString.toString()))
                }
            }

            override fun onAuthenticationFailed() {
                // Use integer constant for ERROR_AUTHENTICATION_FAILED (value = 12)
                // Not available in biometric 1.2.0-alpha04
                deferred.complete(AuthenticationResult.Failure(
                    12,  // ERROR_AUTHENTICATION_FAILED
                    "Authentication failed"
                ))
            }
        }

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)

        return deferred.await()
    }
}