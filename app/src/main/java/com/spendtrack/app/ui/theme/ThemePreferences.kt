package com.spendtrack.app.ui.theme

import android.content.Context

enum class AppThemeMode(val label: String, val description: String) {
    LIGHT("Light", "Use a light appearance"),
    DARK("Dark", "Use a dark appearance"),
    SYSTEM("System", "Match your device settings")
}

object ThemePreferences {
    private const val FILE_NAME = "spendtrack_appearance"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_TRUE_BLACK = "true_black"

    fun mode(context: Context): AppThemeMode = runCatching {
        AppThemeMode.valueOf(context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getString(KEY_MODE, null) ?: "SYSTEM")
    }.getOrDefault(AppThemeMode.SYSTEM)

    fun setMode(context: Context, mode: AppThemeMode) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun isTrueBlackEnabled(context: Context): Boolean =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getBoolean(KEY_TRUE_BLACK, false)

    fun setTrueBlackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_TRUE_BLACK, enabled).apply()
    }
}
