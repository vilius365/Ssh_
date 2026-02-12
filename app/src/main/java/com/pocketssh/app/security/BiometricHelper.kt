package com.pocketssh.app.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Wrapper around AndroidX BiometricPrompt for gating access to SSH key material.
 *
 * Biometric authentication gates the Android Keystore wrapping key, which in turn
 * protects the encrypted SSH private keys. This provides real cryptographic binding,
 * not just a UI gate.
 */
interface BiometricHelper {

    /** Check if biometric authentication is available on this device. */
    fun canAuthenticate(): BiometricAvailability

    /**
     * Prompt the user for biometric authentication.
     *
     * @param activity the hosting FragmentActivity (required by BiometricPrompt)
     * @param title the title shown in the biometric prompt
     * @param subtitle optional subtitle shown in the biometric prompt
     * @return the authentication result
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String = "",
    ): BiometricResult
}

enum class BiometricAvailability {
    AVAILABLE,
    NO_HARDWARE,
    NOT_ENROLLED,
    UNAVAILABLE,
}

sealed class BiometricResult {
    data object Success : BiometricResult()
    data class Error(val code: Int, val message: String) : BiometricResult()
    data object Cancelled : BiometricResult()
}
