package com.unifiedcomms.security

import android.content.Context
import androidx.biometric.BiometricManager as AndroidBiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine

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
            AndroidBiometricManager.Authenticators.BIOMETRIC_STRONG or AndroidBiometricManager.Authenticators.DEVICE_CREDENTIAL
        ) == AndroidBiometricManager.BIOMETRIC_SUCCESS

    override val biometricType: BiometricType
        get() = when {
            !canAuthenticate -> BiometricType.NONE
            AndroidBiometricManager.from(context).canAuthenticate(AndroidBiometricManager.Authenticators.DEVICE_CREDENTIAL) == AndroidBiometricManager.BIOMETRIC_SUCCESS -> {
                BiometricType.STRONG
            }
            else -> BiometricType.WEAK
        }

    override fun hasEnrolledBiometrics(): Boolean = canAuthenticate

    override suspend fun authenticate(reason: String, activity: FragmentActivity): AuthenticationResult =
        awaitAuthentication(reason, activity)

    private suspend fun awaitAuthentication(reason: String, activity: FragmentActivity): AuthenticationResult =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(context)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("UnifiedComms Authentication")
                .setSubtitle(reason)
                .setDescription("Use your biometric to unlock UnifiedComms")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(
                    BiometricPrompt.Authenticators.BIOMETRIC_STRONG or
                        BiometricPrompt.Authenticators.DEVICE_CREDENTIAL
                )
                .build()

            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    cont.resume(AuthenticationResult.Success(result.cryptoObject))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED -> cont.resume(AuthenticationResult.UserCancel)
                        BiometricPrompt.ERROR_TIMEOUT -> cont.resume(AuthenticationResult.Timeout)
                        BiometricPrompt.ERROR_LOCKOUT -> cont.resume(AuthenticationResult.Lockout)
                        else -> cont.resume(AuthenticationResult.Error(errorCode, errString.toString()))
                    }
                }

                override fun onAuthenticationFailed() {
                    cont.resume(AuthenticationResult.Failure(
                        BiometricPrompt.ERROR_AUTHENTICATION_FAILED,
                        "Authentication failed"
                    ))
                }
            }

            val biometricPrompt = BiometricPrompt(activity, executor, callback)
            biometricPrompt.authenticate(promptInfo)

            cont.invokeOnCancellation {
                biometricPrompt.cancelAuthentication()
            }
        }
}