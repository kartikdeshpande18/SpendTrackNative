package com.spendtrack.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendtrack.app.data.local.DatabaseProvider
import com.spendtrack.app.data.repository.FinanceRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val today = LocalDate.now()
        val database = DatabaseProvider.get(applicationContext)
        val repository = FinanceRepository(database)
        repository.matureDueDeposits(today.toEpochDay())
        repository.processDueRecurringTransactions(today.toEpochDay())
        repository.processDueSavingsInterest(today)

        if (!ReminderPreferences.isNotificationsEnabled(applicationContext)) {
            return Result.success()
        }

        val cardLeadTimes = ReminderPreferences.cardLeadTimes(applicationContext)
        val loanLeadTimes = ReminderPreferences.loanLeadTimes(applicationContext)
        database.accounts().observeAll().first().filter { it.isCreditCard && it.dueDay != null }.forEach { card ->
            // Skip already paid credit card bills
            if (card.balancePaise <= 0L) return@forEach

            var due = today.withDayOfMonth(card.dueDay!!.coerceAtMost(today.lengthOfMonth()))
            if (due.isBefore(today)) {
                val nextMonth = due.plusMonths(1)
                due = nextMonth.withDayOfMonth(card.dueDay!!.coerceAtMost(nextMonth.lengthOfMonth()))
            }
            val daysUntil = ChronoUnit.DAYS.between(today, due)
            val notifKey = "card_${card.id}_${due}_${daysUntil}"
            notifyIfNeeded("Card payment due", "${card.name} payment is due $due", daysUntil, cardLeadTimes, "card-${card.id}".hashCode(), notifKey)
        }
        database.loans().observeAll().first().forEach { loan ->
            // Skip already paid loans
            if (loan.currentOutstandingPaise <= 0L) return@forEach

            val due = LocalDate.ofEpochDay(loan.nextDueEpochDay)
            val daysUntil = ChronoUnit.DAYS.between(today, due)
            val notifKey = "loan_${loan.id}_${due}_${daysUntil}"
            notifyIfNeeded("Loan payment due", "${loan.name} payment is due $due", daysUntil, loanLeadTimes, "loan-${loan.id}".hashCode(), notifKey)
        }
        return Result.success()
    }

    private fun notifyIfNeeded(title: String, text: String, daysUntil: Long, leadTimes: ReminderLeadTimes, id: Int, notifKey: String) {
        if (daysUntil !in setOf(leadTimes.firstDays.toLong(), leadTimes.secondDays.toLong())) return
        if (ReminderPreferences.hasBeenNotified(applicationContext, notifKey)) return

        val manager = applicationContext.getSystemService<NotificationManager>() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "SpendTrack reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) android.app.Notification.Builder(applicationContext, CHANNEL_ID) else android.app.Notification.Builder(applicationContext)
        manager.notify(id, builder.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setAutoCancel(true).build())
        ReminderPreferences.markNotified(applicationContext, notifKey)
    }

    companion object {
        private const val CHANNEL_ID = "spendtrack_reminders"
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("spendtrack-reminders", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS).build())
        }
    }
}
