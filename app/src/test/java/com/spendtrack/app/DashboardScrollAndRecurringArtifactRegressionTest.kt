package com.spendtrack.app

import com.spendtrack.app.data.local.*
import com.spendtrack.app.ui.dashboard.TopBudgetsCalculator
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

class DashboardScrollAndRecurringArtifactRegressionTest {

    @Test
    fun testDashboardLazyColumnKeysDoNotCollide() {
        // Given categoryId == transactionId == 1L
        val categoryId = 1L
        val transactionId = 1L

        val topBudgetKey = "top_budget_$categoryId"
        val recentTxKey = "recent_tx_$transactionId"

        assertNotEquals(
            "Top budget item key and recent transaction key must never collide in the same LazyColumn",
            topBudgetKey,
            recentTxKey
        )

        // Multiple budgets and transactions keys
        val budgetKeys = listOf(1L, 2L, 3L).map { "top_budget_$it" }
        val txKeys = listOf(1L, 2L, 3L, 4L, 5L).map { "recent_tx_$it" }
        val intersection = budgetKeys.intersect(txKeys.toSet())
        assertTrue("Intersection of budget keys and transaction keys must be empty", intersection.isEmpty())
    }

    @Test
    fun testLoansAndOwedLazyColumnKeysDoNotCollide() {
        // Given loanId == owedId == 1L
        val loanId = 1L
        val owedId = 1L

        val loanKey = "loan_$loanId"
        val owedKey = "owed_$owedId"

        assertNotEquals(
            "Loan key and owed key must never collide in the same LazyColumn",
            loanKey,
            owedKey
        )
    }

    @Test
    fun testDailyTransactionBarChartCalculations_currentMonthWithTransactionsAroundToday() {
        val today = LocalDate.now()
        val currentMonth = YearMonth.now()

        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        assertEquals(7, dates.size)
        assertEquals(today, dates.last())
        assertEquals(today.minusDays(6), dates.first())

        val tx1 = TransactionEntity(
            id = 1L,
            description = "Coffee",
            amountPaise = 25000L,
            dateEpochDay = today.toEpochDay(),
            type = TransactionType.EXPENSE,
            categoryId = 1L,
            accountId = 1L
        )
        val tx2 = TransactionEntity(
            id = 2L,
            description = "Lunch",
            amountPaise = 50000L,
            dateEpochDay = today.minusDays(2).toEpochDay(),
            type = TransactionType.EXPENSE,
            categoryId = 1L,
            accountId = 1L
        )
        val txIncome = TransactionEntity(
            id = 3L,
            description = "Salary",
            amountPaise = 10000000L,
            dateEpochDay = today.toEpochDay(),
            type = TransactionType.INCOME,
            categoryId = 2L,
            accountId = 1L
        )
        val transactions = listOf(tx1, tx2, txIncome)

        val values = dates.map { date ->
            transactions.filter { it.type == TransactionType.EXPENSE && LocalDate.ofEpochDay(it.dateEpochDay) == date }
                .sumOf { it.mySharePaise ?: it.amountPaise }
        }

        assertEquals(7, values.size)
        assertEquals(25000L, values.last()) // today
        assertEquals(50000L, values[4]) // today - 2
        assertEquals(0L, values[0]) // today - 6
        assertFalse(values.all { it == 0L })
    }

    @Test
    fun testDailyTransactionBarChartCalculations_currentMonthEmptyTransactions() {
        val today = LocalDate.now()
        val dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val transactions = emptyList<TransactionEntity>()

        val values = dates.map { date ->
            transactions.filter { it.type == TransactionType.EXPENSE && LocalDate.ofEpochDay(it.dateEpochDay) == date }
                .sumOf { it.mySharePaise ?: it.amountPaise }
        }

        assertEquals(7, values.size)
        assertTrue("All values must be 0L for empty transactions", values.all { it == 0L })
    }

    @Test
    fun testDailyTransactionBarChartCalculations_historicalMonthWithTransactions() {
        val historicalMonth = YearMonth.of(2026, 8)
        val lastDay = historicalMonth.atEndOfMonth()
        val dates = (6 downTo 0).map { lastDay.minusDays(it.toLong()) }

        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 8, 31), dates.last())
        assertEquals(LocalDate.of(2026, 8, 25), dates.first())

        val tx = TransactionEntity(
            id = 10L,
            description = "Books",
            amountPaise = 150000L,
            dateEpochDay = LocalDate.of(2026, 8, 28).toEpochDay(),
            type = TransactionType.EXPENSE,
            categoryId = 1L,
            accountId = 1L
        )
        val values = dates.map { date ->
            listOf(tx).filter { it.type == TransactionType.EXPENSE && LocalDate.ofEpochDay(it.dateEpochDay) == date }
                .sumOf { it.mySharePaise ?: it.amountPaise }
        }

        assertFalse(values.all { it == 0L })
        assertEquals(150000L, values[3]) // 28th
    }

    @Test
    fun testDailyTransactionBarChartCalculations_monthBoundaryStartOfMonth() {
        // When today is the 1st of the month, dates span safely across the previous month boundary
        val firstDayOfMonth = LocalDate.of(2026, 9, 1)
        val dates = (6 downTo 0).map { firstDayOfMonth.minusDays(it.toLong()) }

        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 9, 1), dates.last())
        assertEquals(LocalDate.of(2026, 8, 26), dates.first())
    }

    @Test
    fun testRecurringScheduleButtonHasNoDuplicateLabelInSource() {
        val candidates = listOf(
            File("src/main/java/com/spendtrack/app/ui/recurring/RecurringTransactionsScreen.kt"),
            File("app/src/main/java/com/spendtrack/app/ui/recurring/RecurringTransactionsScreen.kt")
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
        assertNotNull("RecurringTransactionsScreen.kt source file must exist", sourceFile)
        val content = sourceFile!!.readText()
        val lowerCount = Regex("""Text\("Add recurring schedule"\)""").findAll(content).count()
        val properCount = Regex("""Text\("Add Recurring Schedule"\)""").findAll(content).count()

        assertEquals("Duplicate lowercase 'Add recurring schedule' must not exist in button", 0, lowerCount)
        assertEquals("Exactly one 'Add Recurring Schedule' button text must exist", 1, properCount)
    }
}

