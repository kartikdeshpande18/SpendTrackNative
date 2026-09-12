package com.spendtrack.app.ui.dashboard

import com.spendtrack.app.data.local.BudgetEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

data class TopBudgetItem(
    val categoryId: Long,
    val categoryName: String,
    val spentPaise: Long,
    val budgetPaise: Long,
    val utilization: Double,
    val visualProgress: Float,
    val utilizationPercentageFormatted: String,
)

object TopBudgetsCalculator {

    /**
     * Calculates the category spending for a given category and monthKey.
     * Uses the exact existing spending logic from BudgetsScreen:
     * - TransactionType.EXPENSE only
     * - Matches categoryId and monthKey (from dateEpochDay)
     * - Sums mySharePaise ?: amountPaise (shared expense handling)
     */
    fun calculateCategorySpendingPaise(
        transactions: List<TransactionEntity>,
        categoryId: Long,
        monthKey: String
    ): Long {
        return transactions.filter {
            it.type == TransactionType.EXPENSE &&
            it.categoryId == categoryId &&
            YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)).toString() == monthKey
        }.sumOf { it.mySharePaise ?: it.amountPaise }
    }

    /**
     * Computes the Top 3 Budget Categories for the current month.
     * Active budgets are defined as budgets matching monthKey with limitPaise > 0.
     * Ranking:
     * 1. Highest utilization descending
     * 2. Higher spent amount descending (first tie-breaker)
     * 3. Category name alphabetically ascending (second tie-breaker)
     * Returns maximum 3 items.
     */
    fun calculateTopBudgetCategories(
        budgets: List<BudgetEntity>,
        categories: List<CategoryEntity>,
        transactions: List<TransactionEntity>,
        monthKey: String = YearMonth.now().toString()
    ): List<TopBudgetItem> {
        val currentBudgets = budgets.filter { it.monthKey == monthKey && it.limitPaise > 0L }
        if (currentBudgets.isEmpty()) return emptyList()

        val items = currentBudgets.mapNotNull { budget ->
            val category = categories.find { it.id == budget.categoryId } ?: return@mapNotNull null
            val spentPaise = calculateCategorySpendingPaise(transactions, budget.categoryId, monthKey)
            val utilization = spentPaise.toDouble() / budget.limitPaise.toDouble()
            val visualProgress = (spentPaise.toFloat() / budget.limitPaise.toFloat()).coerceIn(0f, 1f)
            val percent = (utilization * 100.0).roundToInt()
            val percentFormatted = "$percent%"

            TopBudgetItem(
                categoryId = budget.categoryId,
                categoryName = category.name,
                spentPaise = spentPaise,
                budgetPaise = budget.limitPaise,
                utilization = utilization,
                visualProgress = visualProgress,
                utilizationPercentageFormatted = percentFormatted
            )
        }

        return items.sortedWith(
            compareByDescending<TopBudgetItem> { it.utilization }
                .thenByDescending { it.spentPaise }
                .thenBy { it.categoryName.lowercase(Locale.ROOT) }
        ).take(3)
    }

    /**
     * Formats currency cleanly without trailing .00 for whole rupees.
     * e.g. 2450_00L -> "₹2,450", 2450_50L -> "₹2,450.50"
     */
    fun formatCompactMoney(paise: Long): String {
        return if (paise % 100L == 0L) {
            "₹" + String.format(Locale.US, "%,d", paise / 100L)
        } else {
            "₹" + String.format(Locale.US, "%,.2f", paise / 100.0)
        }
    }
}
