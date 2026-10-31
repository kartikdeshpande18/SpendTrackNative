package com.spendtrack.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.os.Build
import android.widget.Toast
import android.net.Uri
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
    private var restoreBackupUri by mutableStateOf<Uri?>(null)
    private var selectedStatementUris by mutableStateOf<List<Uri>>(emptyList())
    private var pendingBackupText: String? = null

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
                onBiometricPreferenceChange = ::updateBiometricPreference,
                restoreBackupUri = restoreBackupUri,
                onRestoreBackupUriConsumed = { restoreBackupUri = null },
                onPickRestoreBackup = ::pickRestoreBackup,
                onCreateBackup = ::saveBackup,
                selectedStatementUris = selectedStatementUris,
                onStatementUrisConsumed = { selectedStatementUris = emptyList() },
                onPickStatementFiles = ::pickStatementFiles
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            RESTORE_BACKUP_REQUEST -> if (resultCode == RESULT_OK) restoreBackupUri = data?.data
            STATEMENT_FILES_REQUEST -> if (resultCode == RESULT_OK) {
                selectedStatementUris = data?.clipData?.let { clip ->
                    List(clip.itemCount) { clip.getItemAt(it).uri }
                } ?: listOfNotNull(data?.data)
            }
            CREATE_BACKUP_REQUEST -> {
                val text = pendingBackupText
                pendingBackupText = null
                val uri = data?.data
                if (resultCode == RESULT_OK && text != null && uri != null) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        val result = runCatching {
                            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                                ?: error("Unable to write backup file")
                        }
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, if (result.isSuccess) "Backup saved" else "Backup failed: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    private fun pickRestoreBackup() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain"))
                },
                RESTORE_BACKUP_REQUEST
            )
        } catch (error: Exception) {
            Toast.makeText(this, error.localizedMessage ?: "Unable to open file picker", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveBackup(text: String) {
        pendingBackupText = text
        try {
            startActivityForResult(
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/json"
                    putExtra(Intent.EXTRA_TITLE, "spendtrack-backup.json")
                },
                CREATE_BACKUP_REQUEST
            )
        } catch (error: Exception) {
            pendingBackupText = null
            Toast.makeText(this, error.localizedMessage ?: "Unable to open save dialog", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickStatementFiles() {
        try {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                },
                STATEMENT_FILES_REQUEST
            )
        } catch (error: Exception) {
            Toast.makeText(this, error.localizedMessage ?: "Unable to open file picker", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        if (biometricEnabled && !promptShowing) isUnlocked = false
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
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED
                    ) Toast.makeText(this@MainActivity, errString, Toast.LENGTH_LONG).show()
                }
            }
        )
        val useDeviceCredential = allowDeviceCredential && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(if (allowDeviceCredential) "Unlock SpendTrack" else "Enable biometric authentication")
            .setSubtitle(if (allowDeviceCredential) "Authenticate to continue" else "Confirm your identity to enable app lock")
            .setAllowedAuthenticators(
                if (useDeviceCredential) {
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                } else {
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
                }
            )
        if (!useDeviceCredential) info.setNegativeButtonText("Cancel")
        try {
            prompt.authenticate(info.build())
        } catch (error: Exception) {
            promptShowing = false
            Toast.makeText(this, error.localizedMessage ?: "Unable to open biometric prompt", Toast.LENGTH_LONG).show()
        }
    }

    private companion object {
        const val RESTORE_BACKUP_REQUEST = 0x2451
        const val CREATE_BACKUP_REQUEST = 0x2452
        const val STATEMENT_FILES_REQUEST = 0x2453
    }
}
