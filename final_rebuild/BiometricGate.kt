package com.nameemrooz.journal.privacy

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.nameemrooz.journal.data.AppLanguage

class BiometricGate(private val activity: FragmentActivity) {
    fun unlock(
        language: AppLanguage,
        onSuccess: () -> Unit,
        onUnavailable: () -> Unit,
        onCancelled: () -> Unit = onUnavailable,
    ) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            onUnavailable()
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancelled()
                override fun onAuthenticationFailed() {}
            },
        )
        val fa = language == AppLanguage.FA
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(if (fa) "ورود به امروز" else "Unlock Emrooz")
                .setSubtitle(if (fa) "فقط با اثر انگشت" else "Fingerprint only")
                .setNegativeButtonText(if (fa) "لغو" else "Cancel")
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }
}
