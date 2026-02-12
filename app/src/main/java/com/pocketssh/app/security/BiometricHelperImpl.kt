package com.pocketssh.app.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private const val AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

/**
 * AndroidX BiometricPrompt wrapper that exposes a coroutine-friendly API.
 *
 * Uses `BIOMETRIC_STRONG | DEVICE_CREDENTIAL` so the user can fall back to
 * PIN/pattern/password when biometrics are unavailable or fail.
 */
@Singleton
class BiometricHelperImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : BiometricHelper {

    override fun canAuthenticate(): BiometricAvailability {
        val result = BiometricManager.from(context).canAuthenticate(AUTHENTICATORS)
        return when (result) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricAvailability.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricAvailability.NO_HARDWARE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricAvailability.NOT_ENROLLED
            else -> BiometricAvailability.UNAVAILABLE
        }
    }

    override suspend fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
    ): BiometricResult = suspendCancellableCoroutine { cont ->
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (cont.isActive) cont.resume(BiometricResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (cont.isActive) {
                    val biometricResult = when (errorCode) {
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_CANCELED ->
                            BiometricResult.Cancelled
                        else ->
                            BiometricResult.Error(errorCode, errString.toString())
                    }
                    cont.resume(biometricResult)
                }
            }

            override fun onAuthenticationFailed() {
                // Biometric didn't match -- system allows retry automatically, nothing to do
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)

        if (subtitle.isNotEmpty()) {
            promptInfoBuilder.setSubtitle(subtitle)
        }

        prompt.authenticate(promptInfoBuilder.build())

        cont.invokeOnCancellation {
            prompt.cancelAuthentication()
        }
    }
}
