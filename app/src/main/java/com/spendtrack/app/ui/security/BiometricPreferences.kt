package com.spendtrack.app.ui.security

import android.content.Context

object BiometricPreferences {
    private const val FILE_NAME = "spendtrack_security"
    private const val KEY_BIOMETRIC_LOCK = "biometric_lock_enabled"

    fun isEnabled(context: Context): Boolean = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_BIOMETRIC_LOCK, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BIOMETRIC_LOCK, enabled)
            .apply()
    }
}
