package com.spendtrack.app.widget

import android.content.Context
import com.spendtrack.app.data.local.DatabaseProvider
import com.spendtrack.app.data.local.FixedDepositStatus
import com.spendtrack.app.data.local.OwedDirection
import com.spendtrack.app.data.local.TransactionType
import com.spendtrack.app.ui.dashboard.SavingsCalculator
import com.spendtrack.app.ui.dashboard.TopBudgetsCalculator
import com.spendtrack.app.ui.security.BiometricPreferences
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WidgetTransaction(
    val id: Long,
    val description: String,
    val categoryName: String,
    val amountPaise: Long,
    val isExpense: Boolean,
    val dateText: String,
    val transactionType: TransactionType = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME
)

data class WidgetBudgetItem(
    val categoryName: String,
    val spentPaise: Long,
    val budgetPaise: Long,
    val progress: Float,
    val percentFormatted: String
)

data class WidgetSnapshot(
    val todaySpentPaise: Long = 0L,
    val monthSpentPaise: Long = 0L,
    val monthIncomePaise: Long = 0L,
    val netWorthPaise: Long = 0L,
    val availableSavingsPaise: Long = 0L,
    val monthName: String = "",
    val topBudgetName: String = "",
    val topBudgetSpentPaise: Long = 0L,
    val topBudgetTotalPaise: Long = 0L,
    val topBudgetProgress: Float = 0f,
    val topBudgetPercentFormatted: String = "",
    val topBudgets: List<WidgetBudgetItem> = emptyList(),
    val recentTransactions: List<WidgetTransaction> = emptyList(),
    val isLocked: Boolean = false
)

object WidgetDataProvider {

    suspend fun loadSnapshot(context: Context): WidgetSnapshot {
        return runCatching {
            val currentYm = YearMonth.now()
            if (BiometricPreferences.isEnabled(context)) {
                return WidgetSnapshot(
                    monthName = currentYm.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())),
                    isLocked = true
                )
            }
            val db = DatabaseProvider.get(context)
            val today = LocalDate.now()
            val todayEpoch = today.toEpochDay()
            val startOfMonthEpoch = currentYm.atDay(1).toEpochDay()
            val endOfMonthEpoch = currentYm.atEndOfMonth().toEpochDay()

            val accounts = db.accounts().observeAll().first()
            val categories = db.categories().observeAll().first()
            val categoryMap = categories.associateBy { it.id }
            val budgets = db.budgets().observeAll().first()
            val fds = db.fixedDeposits().observeAll().first()
            val loans = db.loans().observeAll().first()
            val owed = db.owed().observeAll().first()

            val monthTransactions = db.transactions().getDateRange(startOfMonthEpoch, endOfMonthEpoch)

            var todaySpent = 0L
            var monthSpent = 0L
            var monthIncome = 0L

            for (t in monthTransactions) {
                if (t.isExcludedFromCalculations) continue
                val amt = t.mySharePaise ?: t.amountPaise
                if (t.type == TransactionType.EXPENSE) {
                    monthSpent += amt
                    if (t.dateEpochDay == todayEpoch) {
                        todaySpent += amt
                    }
                } else if (t.type == TransactionType.INCOME) {
                    monthIncome += t.amountPaise
                }
            }

            val cash = accounts.filterNot { it.isCreditCard }.sumOf { it.balancePaise }
            val activeFdPrincipal = fds.filter { it.status == FixedDepositStatus.ACTIVE }.sumOf { it.principalPaise }
            val owedNet = owed.sumOf { if (it.direction == OwedDirection.OWED_TO_ME) it.amountPaise else -it.amountPaise }
            val cards = accounts.filter { it.isCreditCard }.sumOf { it.balancePaise.coerceAtLeast(0L) }
            val totalLoans = loans.filter { it.includeInNetWorth }.sumOf { it.currentOutstandingPaise }
            val netWorth = cash + activeFdPrincipal + owedNet - cards - totalLoans
            val availableSavings = SavingsCalculator.calculateAvailableSavingsPaise(accounts)

            val topBudgets = TopBudgetsCalculator.calculateTopBudgetCategories(
                budgets = budgets,
                categories = categories,
                transactions = monthTransactions,
                monthKey = currentYm.toString()
            )
            val topBudget = topBudgets.firstOrNull()
            val widgetTopBudgets = topBudgets.take(3).map {
                WidgetBudgetItem(
                    categoryName = it.categoryName,
                    spentPaise = it.spentPaise,
                    budgetPaise = it.budgetPaise,
                    progress = it.visualProgress,
                    percentFormatted = it.utilizationPercentageFormatted
                )
            }

            val recentTxs = db.transactions().getDateRange(
                startOfMonthEpoch.coerceAtMost(todayEpoch - 30),
                todayEpoch
            ).take(3).map { t ->
                val cat = categoryMap[t.categoryId]?.name ?: "General"
                val date = LocalDate.ofEpochDay(t.dateEpochDay)
                val dateStr = if (t.dateEpochDay == todayEpoch) "Today" else date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
                WidgetTransaction(
                    id = t.id,
                    description = t.description.ifBlank { cat },
                    categoryName = cat,
                    amountPaise = t.mySharePaise ?: t.amountPaise,
                    isExpense = t.type == TransactionType.EXPENSE,
                    dateText = dateStr,
                    transactionType = t.type
                )
            }

            val monthNameStr = currentYm.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

            WidgetSnapshot(
                todaySpentPaise = todaySpent,
                monthSpentPaise = monthSpent,
                monthIncomePaise = monthIncome,
                netWorthPaise = netWorth,
                availableSavingsPaise = availableSavings,
                monthName = monthNameStr,
                topBudgetName = topBudget?.categoryName.orEmpty(),
                topBudgetSpentPaise = topBudget?.spentPaise ?: 0L,
                topBudgetTotalPaise = topBudget?.budgetPaise ?: 0L,
                topBudgetProgress = topBudget?.visualProgress ?: 0f,
                topBudgetPercentFormatted = topBudget?.utilizationPercentageFormatted.orEmpty(),
                topBudgets = widgetTopBudgets,
                recentTransactions = recentTxs
            )
        }.getOrElse {
            WidgetSnapshot(monthName = YearMonth.now().format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())))
        }
    }

    fun formatMoney(paise: Long, isLocked: Boolean = false): String {
        if (isLocked) return "₹••••"
        val rupees = paise / 100.0
        return "₹" + String.format(Locale.US, "%,.0f", rupees)
    }

    fun formatCompactMoney(paise: Long, isLocked: Boolean = false): String {
        if (isLocked) return "₹••••"
        val rupees = paise / 100.0
        return when {
            rupees >= 10_000_000 -> "₹" + String.format(Locale.US, "%.1fCr", rupees / 10_000_000)
            rupees >= 100_000 -> "₹" + String.format(Locale.US, "%.1fL", rupees / 100_000)
            rupees >= 10_000 -> "₹" + String.format(Locale.US, "%.1fK", rupees / 1_000)
            else -> "₹" + String.format(Locale.US, "%.0f", rupees)
        }
    }
}
