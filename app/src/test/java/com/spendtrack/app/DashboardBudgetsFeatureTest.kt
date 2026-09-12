package com.spendtrack.app

import com.spendtrack.app.data.local.BudgetEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import com.spendtrack.app.ui.dashboard.TopBudgetsCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DashboardBudgetsFeatureTest {

    private val monthKey = "2026-09"
    private val dateEpoch = LocalDate.of(2026, 9, 15).toEpochDay()
    private val otherMonthEpoch = LocalDate.of(2026, 8, 15).toEpochDay()

    // 1. Ranks categories by highest budget utilization descending
    @Test
    fun testRankByHighestUtilization() {
        val cat1 = CategoryEntity(id = 1L, name = "Groceries")
        val cat2 = CategoryEntity(id = 2L, name = "Dining")
        val cat3 = CategoryEntity(id = 3L, name = "Shopping")

        val budgets = listOf(
            BudgetEntity(id = 101L, categoryId = 1L, monthKey = monthKey, limitPaise = 10000_00L), // ₹10,000
            BudgetEntity(id = 102L, categoryId = 2L, monthKey = monthKey, limitPaise = 5000_00L),  // ₹5,000
            BudgetEntity(id = 103L, categoryId = 3L, monthKey = monthKey, limitPaise = 20000_00L), // ₹20,000
        )

        val transactions = listOf(
            // Groceries: ₹5,000 / ₹10,000 = 50%
            TransactionEntity(id = 1L, description = "Groceries", accountId = 1L, categoryId = 1L, amountPaise = 5000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
            // Dining: ₹4,000 / ₹5,000 = 80%
            TransactionEntity(id = 2L, description = "Dining", accountId = 1L, categoryId = 2L, amountPaise = 4000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
            // Shopping: ₹6,000 / ₹20,000 = 30%
            TransactionEntity(id = 3L, description = "Shopping", accountId = 1L, categoryId = 3L, amountPaise = 6000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
        )

        val result = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = budgets,
            categories = listOf(cat1, cat2, cat3),
            transactions = transactions,
            monthKey = monthKey
        )

        assertEquals(3, result.size)
        // Highest utilization first: Dining (80%) -> Groceries (50%) -> Shopping (30%)
        assertEquals("Dining", result[0].categoryName)
        assertEquals("80%", result[0].utilizationPercentageFormatted)
        assertEquals(0.8f, result[0].visualProgress, 0.001f)

        assertEquals("Groceries", result[1].categoryName)
        assertEquals("50%", result[1].utilizationPercentageFormatted)
        assertEquals(0.5f, result[1].visualProgress, 0.001f)

        assertEquals("Shopping", result[2].categoryName)
        assertEquals("30%", result[2].utilizationPercentageFormatted)
        assertEquals(0.3f, result[2].visualProgress, 0.001f)
    }

    // 2. Only includes categories with active budgets (limitPaise > 0 and matching monthKey)
    @Test
    fun testOnlyIncludesActiveBudgetsForMonth() {
        val cat1 = CategoryEntity(id = 1L, name = "Groceries")
        val cat2 = CategoryEntity(id = 2L, name = "Inactive Budget")

        val budgets = listOf(
            BudgetEntity(id = 101L, categoryId = 1L, monthKey = monthKey, limitPaise = 10000_00L),
            BudgetEntity(id = 102L, categoryId = 2L, monthKey = monthKey, limitPaise = 0L), // limit is 0
            BudgetEntity(id = 103L, categoryId = 1L, monthKey = "2026-08", limitPaise = 8000_00L), // previous month
        )

        val transactions = listOf(
            TransactionEntity(id = 1L, description = "Groceries", accountId = 1L, categoryId = 1L, amountPaise = 2000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
            TransactionEntity(id = 2L, description = "Inactive", accountId = 1L, categoryId = 2L, amountPaise = 1000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
        )

        val result = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = budgets,
            categories = listOf(cat1, cat2),
            transactions = transactions,
            monthKey = monthKey
        )

        assertEquals(1, result.size)
        assertEquals("Groceries", result[0].categoryName)
    }

    // 3. Excludes categories without budgets
    @Test
    fun testExcludesCategoriesWithoutBudgets() {
        val cat1 = CategoryEntity(id = 1L, name = "Groceries")
        val cat2 = CategoryEntity(id = 2L, name = "Entertainment") // No budget

        val budgets = listOf(
            BudgetEntity(id = 101L, categoryId = 1L, monthKey = monthKey, limitPaise = 10000_00L),
        )

        val transactions = listOf(
            TransactionEntity(id = 1L, description = "Groceries", accountId = 1L, categoryId = 1L, amountPaise = 3000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
            TransactionEntity(id = 2L, description = "Movie", accountId = 1L, categoryId = 2L, amountPaise = 9000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
        )

        val result = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = budgets,
            categories = listOf(cat1, cat2),
            transactions = transactions,
            monthKey = monthKey
        )

        assertEquals(1, result.size)
        assertEquals("Groceries", result[0].categoryName)
    }

    // 4. Returns at most 3 categories when more than 3 budgets exist
    @Test
    fun testReturnsMaxThreeCategories() {
        val categories = (1..5).map { CategoryEntity(id = it.toLong(), name = "Cat $it") }
        val budgets = (1..5).map {
            BudgetEntity(id = 100L + it, categoryId = it.toLong(), monthKey = monthKey, limitPaise = 10000_00L)
        }
        val transactions = (1..5).map {
            TransactionEntity(
                id = it.toLong(),
                description = "Desc $it",
                accountId = 1L,
                categoryId = it.toLong(),
                amountPaise = it * 1000_00L, // 10%, 20%, 30%, 40%, 50%
                type = TransactionType.EXPENSE,
                dateEpochDay = dateEpoch
            )
        }

        val result = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = budgets,
            categories = categories,
            transactions = transactions,
            monthKey = monthKey
        )

        assertEquals(3, result.size)
        assertEquals("Cat 5", result[0].categoryName) // 50%
        assertEquals("Cat 4", result[1].categoryName) // 40%
        assertEquals("Cat 3", result[2].categoryName) // 30%
    }

    // 5. Handles fewer than 3 categories gracefully
    @Test
    fun testHandlesFewerThanThreeBudgets() {
        val cat1 = CategoryEntity(id = 1L, name = "Cat 1")
        val cat2 = CategoryEntity(id = 2L, name = "Cat 2")
        val budgets = listOf(
            BudgetEntity(id = 101L, categoryId = 1L, monthKey = monthKey, limitPaise = 5000_00L),
            BudgetEntity(id = 102L, categoryId = 2L, monthKey = monthKey, limitPaise = 5000_00L),
        )
        val transactions = listOf(
            TransactionEntity(id = 1L, description = "Test", accountId = 1L, categoryId = 1L, amountPaise = 2500_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch)
        )

        val result = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = budgets,
            categories = listOf(cat1, cat2),
            transactions = transactions,
            monthKey = monthKey
        )

        assertEquals(2, result.size)
        assertEquals("Cat 1", result[0].categoryName)
        assertEquals("50%", result[0].utilizationPercentageFormatted)
        assertEquals("Cat 2", result[1].categoryName)
        assertEquals("0%", result[1].utilizationPercentageFormatted)
    }

    // 6. Returns empty list when no budgets exist for the month
    @Test
    fun testEmptyWhenNoBudgets() {
        val cat1 = CategoryEntity(id = 1L, name = "Cat 1")
        val transactions = listOf(
            TransactionEntity(id = 1L, description = "Test", accountId = 1L, categoryId = 1L, amountPaise = 2500_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch)
        )

        val result = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = emptyList(),
            categories = listOf(cat1),
            transactions = transactions,
            monthKey = monthKey
        )

        assertTrue(result.isEmpty())
    }

    // 7. Visual progress is clamped to 1.0f (100%) when over budget
    @Test
    fun testVisualProgressClampedToOne() {
        val cat = CategoryEntity(id = 1L, name = "Overspent Category")
        val budget = BudgetEntity(id = 101L, categoryId = 1L, monthKey = monthKey, limitPaise = 5000_00L)
        val transactions = listOf(
            TransactionEntity(id = 1L, description = "Overspent", accountId = 1L, categoryId = 1L, amountPaise = 7500_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch)
        )

        val result = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = listOf(budget),
            categories = listOf(cat),
            transactions = transactions,
            monthKey = monthKey
        )

        assertEquals(1, result.size)
        assertEquals(1.0f, result[0].visualProgress, 0.0f)
        assertEquals(1.5, result[0].utilization, 0.001)
        assertEquals("150%", result[0].utilizationPercentageFormatted)
    }

    // 8. Shared expense and month filtering correctness
    @Test
    fun testSharedExpenseAndMonthFiltering() {
        val cat = CategoryEntity(id = 1L, name = "Shared Dinner")
        val budget = BudgetEntity(id = 101L, categoryId = 1L, monthKey = monthKey, limitPaise = 10000_00L)
        val transactions = listOf(
            // Total was ₹4,000, my share was ₹2,000
            TransactionEntity(
                id = 1L, description = "Dinner", accountId = 1L, categoryId = 1L,
                amountPaise = 4000_00L, mySharePaise = 2000_00L, isShared = true,
                type = TransactionType.EXPENSE, dateEpochDay = dateEpoch
            ),
            // Transfer should NOT count towards budget
            TransactionEntity(
                id = 2L, description = "Transfer", accountId = 1L, categoryId = 1L,
                amountPaise = 5000_00L, destinationAccountId = 2L,
                type = TransactionType.TRANSFER, dateEpochDay = dateEpoch
            ),
            // Expense from another month should NOT count
            TransactionEntity(
                id = 3L, description = "Old expense", accountId = 1L, categoryId = 1L,
                amountPaise = 3000_00L,
                type = TransactionType.EXPENSE, dateEpochDay = otherMonthEpoch
            ),
            // Income should NOT count towards budget
            TransactionEntity(
                id = 4L, description = "Salary", accountId = 1L, categoryId = 1L,
                amountPaise = 10000_00L,
                type = TransactionType.INCOME, dateEpochDay = dateEpoch
            )
        )

        val result = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = listOf(budget),
            categories = listOf(cat),
            transactions = transactions,
            monthKey = monthKey
        )

        assertEquals(1, result.size)
        assertEquals(2000_00L, result[0].spentPaise) // Only mySharePaise counts
        assertEquals("20%", result[0].utilizationPercentageFormatted)
    }

    // 9. Deterministic tie-breaking: utilization -> spent amount -> category name
    @Test
    fun testTieBreakingLogic() {
        val catA = CategoryEntity(id = 1L, name = "Apparel")
        val catB = CategoryEntity(id = 2L, name = "Books")
        val catC = CategoryEntity(id = 3L, name = "Cinema")

        val budgets = listOf(
            // All 50% utilization, but different absolute spent amounts:
            BudgetEntity(id = 101L, categoryId = 1L, monthKey = monthKey, limitPaise = 10000_00L), // ₹5,000 spent
            BudgetEntity(id = 102L, categoryId = 2L, monthKey = monthKey, limitPaise = 20000_00L), // ₹10,000 spent
            BudgetEntity(id = 103L, categoryId = 3L, monthKey = monthKey, limitPaise = 10000_00L), // ₹5,000 spent
        )

        val transactions = listOf(
            TransactionEntity(id = 1L, description = "Apparel", accountId = 1L, categoryId = 1L, amountPaise = 5000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
            TransactionEntity(id = 2L, description = "Books", accountId = 1L, categoryId = 2L, amountPaise = 10000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
            TransactionEntity(id = 3L, description = "Cinema", accountId = 1L, categoryId = 3L, amountPaise = 5000_00L, type = TransactionType.EXPENSE, dateEpochDay = dateEpoch),
        )

        val result = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = budgets,
            categories = listOf(catA, catB, catC),
            transactions = transactions,
            monthKey = monthKey
        )

        assertEquals(3, result.size)
        // 1st: Books (50%, spent ₹10,000 - higher spent tie-breaker)
        assertEquals("Books", result[0].categoryName)
        // 2nd: Apparel (50%, spent ₹5,000 - alphabetical tie-breaker before Cinema)
        assertEquals("Apparel", result[1].categoryName)
        // 3rd: Cinema (50%, spent ₹5,000 - alphabetical tie-breaker after Apparel)
        assertEquals("Cinema", result[2].categoryName)
    }

    // 10. Clean currency formatting
    @Test
    fun testCurrencyFormatting() {
        assertEquals("₹2,450", TopBudgetsCalculator.formatCompactMoney(2450_00L))
        assertEquals("₹0", TopBudgetsCalculator.formatCompactMoney(0L))
        assertEquals("₹10,000", TopBudgetsCalculator.formatCompactMoney(10000_00L))
        assertEquals("₹2,450.50", TopBudgetsCalculator.formatCompactMoney(2450_50L))
    }
}
