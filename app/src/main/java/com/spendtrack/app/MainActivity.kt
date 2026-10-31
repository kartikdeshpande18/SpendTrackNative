package com.spendtrack.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.spendtrack.app.ui.SpendTrackApp
import com.spendtrack.app.reminders.ReminderWorker
import com.spendtrack.app.ui.security.BiometricPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var biometricEnabled by mutableStateOf(false)
    private var isUnlocked by mutableStateOf(true)
    private var promptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        biometricEnabled = BiometricPreferences.isEnabled(this)
        isUnlocked = !biometricEnabled
        lifecycleScope.launch(Dispatchers.Default) {
            ReminderWorker.schedule(applicationContext)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
        setContent {
            SpendTrackApp(
                isAppLocked = biometricEnabled && !isUnlocked,
                onUnlockRequest = { authenticate(allowDeviceCredential = true) { isUnlocked = true } },
                biometricEnabled = biometricEnabled,
                onBiometricPreferenceChange = ::updateBiometricPreference
            )
        }
    }

    override fun onStop() {
        super.onStop()
        if (biometricEnabled && !promptShowing) isUnlocked = false
    }

    override fun onPause() {
        if (biometricEnabled && !promptShowing) isUnlocked = false
        super.onPause()
    }

    private fun updateBiometricPreference(enabled: Boolean) {
        if (!enabled) {
            BiometricPreferences.setEnabled(this, false)
            biometricEnabled = false
            isUnlocked = true
            return
        }
        val manager = BiometricManager.from(this)
        if (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Set up a biometric in device settings first.", Toast.LENGTH_SHORT).show()
            return
        }
        authenticate(allowDeviceCredential = false) {
            BiometricPreferences.setEnabled(this, true)
            biometricEnabled = true
            isUnlocked = true
        }
    }

    private fun authenticate(allowDeviceCredential: Boolean, onSuccess: () -> Unit) {
        if (promptShowing || isFinishing || isDestroyed) return
        promptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    promptShowing = false
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    promptShowing = false
                    if (biometricEnabled) isUnlocked = false
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (allowDeviceCredential) "Unlock SpendTrack" else "Enable biometric authentication")
            .setSubtitle(if (allowDeviceCredential) "Authenticate to continue" else "Confirm your identity to enable app lock")
            .setAllowedAuthenticators(
                if (allowDeviceCredential) {
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                } else {
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                }
            )
        if (!allowDeviceCredential) info.setNegativeButtonText("Cancel")
        prompt.authenticate(info.build())
    }
}
