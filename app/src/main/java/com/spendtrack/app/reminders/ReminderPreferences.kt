package com.spendtrack.app.reminders

import android.content.Context

data class ReminderLeadTimes(val firstDays: Int, val secondDays: Int) {
    fun summary(): String = "$firstDays and $secondDays days before due date"
}

/** Persistent, local-only reminder lead times. The worker reads these values on every daily run. */
object ReminderPreferences {
    private const val FILE_NAME = "spendtrack_reminders"
    private const val KEY_ENABLED = "notifications_enabled"
    private const val CARD_FIRST = "card_first_days"
    private const val CARD_SECOND = "card_second_days"
    private const val LOAN_FIRST = "loan_first_days"
    private const val LOAN_SECOND = "loan_second_days"
    private const val DEFAULT_FIRST = 10
    private const val DEFAULT_SECOND = 5

    fun isNotificationsEnabled(context: Context): Boolean {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
    }

    fun setNotificationsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun hasBeenNotified(context: Context, notificationKey: String): Boolean {
        return context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).getBoolean("notif_$notificationKey", false)
    }

    fun markNotified(context: Context, notificationKey: String) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("notif_$notificationKey", true)
            .apply()
    }

    fun cardLeadTimes(context: Context): ReminderLeadTimes = read(context, CARD_FIRST, CARD_SECOND)

    fun loanLeadTimes(context: Context): ReminderLeadTimes = read(context, LOAN_FIRST, LOAN_SECOND)

    fun saveCardLeadTimes(context: Context, leadTimes: ReminderLeadTimes) = save(context, CARD_FIRST, CARD_SECOND, leadTimes)

    fun saveLoanLeadTimes(context: Context, leadTimes: ReminderLeadTimes) = save(context, LOAN_FIRST, LOAN_SECOND, leadTimes)

    private fun read(context: Context, firstKey: String, secondKey: String): ReminderLeadTimes {
        val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        val first = preferences.getInt(firstKey, DEFAULT_FIRST)
        val second = preferences.getInt(secondKey, DEFAULT_SECOND)
        return if (first > 0 && second > 0 && first != second) ReminderLeadTimes(first, second) else ReminderLeadTimes(DEFAULT_FIRST, DEFAULT_SECOND)
    }

    private fun save(context: Context, firstKey: String, secondKey: String, leadTimes: ReminderLeadTimes) {
        require(leadTimes.firstDays > 0 && leadTimes.secondDays > 0 && leadTimes.firstDays != leadTimes.secondDays)
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE).edit()
            .putInt(firstKey, leadTimes.firstDays)
            .putInt(secondKey, leadTimes.secondDays)
            .apply()
    }
}
