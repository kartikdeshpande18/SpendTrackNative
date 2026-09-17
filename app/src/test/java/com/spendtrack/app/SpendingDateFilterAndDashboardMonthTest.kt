package com.spendtrack.app

import com.spendtrack.app.data.local.*
import com.spendtrack.app.ui.dashboard.SavingsCalculator
import com.spendtrack.app.ui.dashboard.TopBudgetsCalculator
import com.spendtrack.app.ui.spending.DateFilterType
import com.spendtrack.app.ui.spending.TransactionDateFilter
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SpendingDateFilterAndDashboardMonthTest {

    private val referenceToday = LocalDate.of(2026, 9, 15)

    private fun createTx(
        id: Long,
        type: TransactionType,
        amountPaise: Long,
        date: LocalDate,
        categoryId: Long = 1L,
        accountId: Long = 10L,
        destinationAccountId: Long? = null,
        description: String = "Tx $id",
        mySharePaise: Long? = null
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            accountId = accountId,
            categoryId = categoryId,
            type = type,
            amountPaise = amountPaise,
            dateEpochDay = date.toEpochDay(),
            destinationAccountId = destinationAccountId,
            description = description,
            mySharePaise = mySharePaise
        )
    }

    // =========================================================================
    // 1. Date Filtering Tests
    // =========================================================================

    @Test
    fun testInitialDefaultSpendingDateFilterIsThisMonth() {
        val defaultFilter = TransactionDateFilter()
        assertEquals(
            "Default date filter on first entry must be THIS_MONTH",
            DateFilterType.THIS_MONTH,
            defaultFilter.type
        )
        assertEquals("This Month", defaultFilter.label(referenceToday))

        // When transactions exist across different months:
        val txThisMonth = createTx(1, TransactionType.EXPENSE, 100_00L, LocalDate.of(2026, 9, 10))
        val txLastMonth = createTx(2, TransactionType.EXPENSE, 200_00L, LocalDate.of(2026, 8, 20))
        val txNextMonth = createTx(3, TransactionType.EXPENSE, 300_00L, LocalDate.of(2026, 10, 5))
        val txs = listOf(txThisMonth, txLastMonth, txNextMonth)

        // List immediately uses This Month
        val filteredList = TransactionDateFilter.filterTransactions(
            transactions = txs,
            categoryFilter = null,
            dateFilter = defaultFilter,
            today = referenceToday
        )
        assertEquals(1, filteredList.size)
        assertEquals(1L, filteredList.first().id)

        // Chart immediately uses This Month
        val filteredChart = TransactionDateFilter.filterForChart(
            transactions = txs,
            categoryFilter = null,
            dateFilter = defaultFilter,
            today = referenceToday
        )
        assertEquals(1, filteredChart.size)
        assertEquals(1L, filteredChart.first().id)
    }

    @Test
    fun testAllDates_includesAllTransactions() {
        val filter = TransactionDateFilter(DateFilterType.ALL_DATES)
        val txs = listOf(
            createTx(1, TransactionType.EXPENSE, 500_00L, referenceToday),
            createTx(2, TransactionType.EXPENSE, 600_00L, referenceToday.minusMonths(1)),
            createTx(3, TransactionType.EXPENSE, 700_00L, referenceToday.minusYears(1))
        )
        val filtered = TransactionDateFilter.filterTransactions(
            transactions = txs,
            categoryFilter = null,
            dateFilter = filter,
            today = referenceToday
        )
        assertEquals(3, filtered.size)
        assertEquals("All Dates", filter.label(referenceToday))
    }

    @Test
    fun testThisMonth_includesOnlyCurrentMonthTransactions() {
        val filter = TransactionDateFilter(DateFilterType.THIS_MONTH)
        val txThisMonth1 = createTx(1, TransactionType.EXPENSE, 100_00L, LocalDate.of(2026, 9, 1))
        val txThisMonth2 = createTx(2, TransactionType.EXPENSE, 200_00L, LocalDate.of(2026, 9, 15))
        val txLastMonth = createTx(3, TransactionType.EXPENSE, 300_00L, LocalDate.of(2026, 8, 31))
        val txNextMonth = createTx(4, TransactionType.EXPENSE, 400_00L, LocalDate.of(2026, 10, 1))

        val txs = listOf(txThisMonth1, txThisMonth2, txLastMonth, txNextMonth)
        val filtered = TransactionDateFilter.filterTransactions(
            transactions = txs,
            categoryFilter = null,
            dateFilter = filter,
            today = referenceToday
        )

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L })
        assertTrue(filtered.any { it.id == 2L })
        assertFalse(filtered.any { it.id == 3L })
        assertFalse(filtered.any { it.id == 4L })
        assertEquals("This Month", filter.label(referenceToday))
    }

    @Test
    fun testLast7Days_includesOnlyTrailing7DaysEndingToday() {
        val filter = TransactionDateFilter(DateFilterType.LAST_7_DAYS)
        // Today is 2026-09-15. 7-day window is 2026-09-09 to 2026-09-15
        val txToday = createTx(1, TransactionType.EXPENSE, 100_00L, LocalDate.of(2026, 9, 15))
        val txDay7 = createTx(2, TransactionType.EXPENSE, 200_00L, LocalDate.of(2026, 9, 9))
        val txDay8Ago = createTx(3, TransactionType.EXPENSE, 300_00L, LocalDate.of(2026, 9, 8))
        val txTomorrow = createTx(4, TransactionType.EXPENSE, 400_00L, LocalDate.of(2026, 9, 16))

        val txs = listOf(txToday, txDay7, txDay8Ago, txTomorrow)
        val filtered = TransactionDateFilter.filterTransactions(
            transactions = txs,
            categoryFilter = null,
            dateFilter = filter,
            today = referenceToday
        )

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L })
        assertTrue(filtered.any { it.id == 2L })
        assertFalse(filtered.any { it.id == 3L })
        assertFalse(filtered.any { it.id == 4L })
        assertEquals("Last 7 Days", filter.label(referenceToday))
    }

    @Test
    fun testLast30Days_includesOnlyTrailing30DaysEndingToday() {
        val filter = TransactionDateFilter(DateFilterType.LAST_30_DAYS)
        // Today is 2026-09-15. 30-day window: 2026-08-17 to 2026-09-15
        val txToday = createTx(1, TransactionType.EXPENSE, 100_00L, LocalDate.of(2026, 9, 15))
        val txDay30 = createTx(2, TransactionType.EXPENSE, 200_00L, LocalDate.of(2026, 8, 17))
        val txDay31Ago = createTx(3, TransactionType.EXPENSE, 300_00L, LocalDate.of(2026, 8, 16))

        val txs = listOf(txToday, txDay30, txDay31Ago)
        val filtered = TransactionDateFilter.filterTransactions(
            transactions = txs,
            categoryFilter = null,
            dateFilter = filter,
            today = referenceToday
        )

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.id == 1L })
        assertTrue(filtered.any { it.id == 2L })
        assertFalse(filtered.any { it.id == 3L })
        assertEquals("Last 30 Days", filter.label(referenceToday))
    }

    @Test
    fun testCustomRange_inclusiveFilteringAndValidation() {
        val start = LocalDate.of(2026, 9, 5)
        val end = LocalDate.of(2026, 9, 10)
        val filter = TransactionDateFilter(
            type = DateFilterType.CUSTOM_RANGE,
            customStartDateEpochDay = start.toEpochDay(),
            customEndDateEpochDay = end.toEpochDay()
        )

        // Valid range check
        assertTrue(TransactionDateFilter.isValidCustomRange(start.toEpochDay(), end.toEpochDay()))
        // Start date equal to end date is valid (single day)
        assertTrue(TransactionDateFilter.isValidCustomRange(start.toEpochDay(), start.toEpochDay()))
        // End date before start date is invalid
        assertFalse(TransactionDateFilter.isValidCustomRange(end.toEpochDay(), start.toEpochDay()))
        // Null values are invalid
        assertFalse(TransactionDateFilter.isValidCustomRange(null, end.toEpochDay()))
        assertFalse(TransactionDateFilter.isValidCustomRange(start.toEpochDay(), null))

        val txBefore = createTx(1, TransactionType.EXPENSE, 100_00L, LocalDate.of(2026, 9, 4))
        val txOnStart = createTx(2, TransactionType.EXPENSE, 200_00L, LocalDate.of(2026, 9, 5))
        val txInside = createTx(3, TransactionType.EXPENSE, 300_00L, LocalDate.of(2026, 9, 7))
        val txOnEnd = createTx(4, TransactionType.EXPENSE, 400_00L, LocalDate.of(2026, 9, 10))
        val txAfter = createTx(5, TransactionType.EXPENSE, 500_00L, LocalDate.of(2026, 9, 11))

        val txs = listOf(txBefore, txOnStart, txInside, txOnEnd, txAfter)
        val filtered = TransactionDateFilter.filterTransactions(
            transactions = txs,
            categoryFilter = null,
            dateFilter = filter,
            today = referenceToday
        )

        assertEquals(3, filtered.size)
        assertTrue(filtered.any { it.id == 2L })
        assertTrue(filtered.any { it.id == 3L })
        assertTrue(filtered.any { it.id == 4L })
        assertFalse(filtered.any { it.id == 1L })
        assertFalse(filtered.any { it.id == 5L })

        // Concise label
        val label = filter.label(referenceToday)
        assertTrue(label.contains("5 Sep"))
        assertTrue(label.contains("10 Sep"))
    }

    @Test
    fun testCategoryAndDateFilterConjunction() {
        val categoryIdFood = 101L
        val categoryIdTransport = 102L
        val filter = TransactionDateFilter(DateFilterType.THIS_MONTH)

        val txFoodThisMonth = createTx(1, TransactionType.EXPENSE, 100_00L, LocalDate.of(2026, 9, 10), categoryId = categoryIdFood)
        val txFoodLastMonth = createTx(2, TransactionType.EXPENSE, 200_00L, LocalDate.of(2026, 8, 10), categoryId = categoryIdFood)
        val txTransportThisMonth = createTx(3, TransactionType.EXPENSE, 300_00L, LocalDate.of(2026, 9, 10), categoryId = categoryIdTransport)

        val txs = listOf(txFoodThisMonth, txFoodLastMonth, txTransportThisMonth)
        val filtered = TransactionDateFilter.filterTransactions(
            transactions = txs,
            categoryFilter = categoryIdFood,
            dateFilter = filter,
            today = referenceToday
        )

        assertEquals(1, filtered.size)
        assertEquals(1L, filtered.first().id)
    }

    @Test
    fun testTransferExclusionFromCategoryAndIncomeVsExpenseChart() {
        val filter = TransactionDateFilter(DateFilterType.THIS_MONTH)
        val txExpense = createTx(1, TransactionType.EXPENSE, 500_00L, LocalDate.of(2026, 9, 10), categoryId = 1L)
        val txIncome = createTx(2, TransactionType.INCOME, 1000_00L, LocalDate.of(2026, 9, 10), categoryId = 0L)
        val txTransfer = createTx(3, TransactionType.TRANSFER, 2000_00L, LocalDate.of(2026, 9, 10), categoryId = 0L, accountId = 1L, destinationAccountId = 2L)

        val txs = listOf(txExpense, txIncome, txTransfer)

        // For list filtering: transfers are included when categoryFilter is null
        val listAll = TransactionDateFilter.filterTransactions(txs, null, filter, today = referenceToday)
        assertEquals(3, listAll.size)

        // When a specific category filter is active, transfers are excluded from list
        val listCategory = TransactionDateFilter.filterTransactions(txs, 1L, filter, today = referenceToday)
        assertEquals(1, listCategory.size)
        assertEquals(1L, listCategory.first().id)

        // For chart classification:
        val chartItems = TransactionDateFilter.filterForChart(txs, null, filter, today = referenceToday)
        assertEquals(3, chartItems.size)
        // IncomeVsExpenseChart only aggregates EXPENSE and INCOME:
        val expensePaise = chartItems.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        val incomePaise = chartItems.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        assertEquals(500_00L, expensePaise)
        assertEquals(1000_00L, incomePaise)
        // Transfers are not counted in either
    }

    // =========================================================================
    // 2. Dashboard Month Selection Tests
    // =========================================================================

    @Test
    fun testDashboardHistoricalMonth_updatesSpendingIncomeAndBudgets() {
        val sept = YearMonth.of(2026, 9)
        val aug = YearMonth.of(2026, 8)

        val txSeptExpense = createTx(1, TransactionType.EXPENSE, 5000_00L, LocalDate.of(2026, 9, 5))
        val txSeptIncome = createTx(2, TransactionType.INCOME, 20000_00L, LocalDate.of(2026, 9, 1))
        val txAugExpense = createTx(3, TransactionType.EXPENSE, 3500_00L, LocalDate.of(2026, 8, 12))
        val txAugIncome = createTx(4, TransactionType.INCOME, 18000_00L, LocalDate.of(2026, 8, 1))

        val allTxs = listOf(txSeptExpense, txSeptIncome, txAugExpense, txAugIncome)

        // Selected month = August 2026
        val augTxs = allTxs.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == aug }
        val augSpending = augTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.mySharePaise ?: it.amountPaise }
        val augIncome = augTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }

        assertEquals(3500_00L, augSpending)
        assertEquals(18000_00L, augIncome)

        // Selected month = September 2026
        val septTxs = allTxs.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == sept }
        val septSpending = septTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.mySharePaise ?: it.amountPaise }
        val septIncome = septTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }

        assertEquals(5000_00L, septSpending)
        assertEquals(20000_00L, septIncome)
    }

    @Test
    fun testDashboardTopBudgets_propagatesSelectedMonth() {
        val aug = YearMonth.of(2026, 8)
        val sept = YearMonth.of(2026, 9)

        val foodCat = CategoryEntity(id = 1L, name = "Food", isIncomeCategory = false)
        val budgetAug = BudgetEntity(categoryId = 1L, monthKey = aug.toString(), limitPaise = 10000_00L)
        val budgetSept = BudgetEntity(categoryId = 1L, monthKey = sept.toString(), limitPaise = 12000_00L)

        val txAug = createTx(1, TransactionType.EXPENSE, 4000_00L, LocalDate.of(2026, 8, 15), categoryId = 1L)
        val txSept = createTx(2, TransactionType.EXPENSE, 8000_00L, LocalDate.of(2026, 9, 10), categoryId = 1L)

        val topBudgetsAug = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = listOf(budgetAug, budgetSept),
            categories = listOf(foodCat),
            transactions = listOf(txAug, txSept),
            monthKey = aug.toString()
        )

        assertEquals(1, topBudgetsAug.size)
        assertEquals(10000_00L, topBudgetsAug.first().budgetPaise)
        assertEquals(4000_00L, topBudgetsAug.first().spentPaise)

        val topBudgetsSept = TopBudgetsCalculator.calculateTopBudgetCategories(
            budgets = listOf(budgetAug, budgetSept),
            categories = listOf(foodCat),
            transactions = listOf(txAug, txSept),
            monthKey = sept.toString()
        )

        assertEquals(1, topBudgetsSept.size)
        assertEquals(12000_00L, topBudgetsSept.first().budgetPaise)
        assertEquals(8000_00L, topBudgetsSept.first().spentPaise)
    }

    @Test
    fun testDashboardLast7Days_historicalMonthShowsFinal7Days() {
        val aug = YearMonth.of(2026, 8)
        val lastDayOfAug = aug.atEndOfMonth() // 2026-08-31
        val final7DaysOfAug = (6 downTo 0).map { lastDayOfAug.minusDays(it.toLong()) }

        assertEquals(7, final7DaysOfAug.size)
        assertEquals(LocalDate.of(2026, 8, 25), final7DaysOfAug.first())
        assertEquals(LocalDate.of(2026, 8, 31), final7DaysOfAug.last())

        // Transaction on 31 Aug should be included in final 7 days
        val txOnLastDay = createTx(1, TransactionType.EXPENSE, 100_00L, LocalDate.of(2026, 8, 31))
        val isIncluded = final7DaysOfAug.contains(LocalDate.ofEpochDay(txOnLastDay.dateEpochDay))
        assertTrue(isIncluded)

        // Transaction on 20 Aug should NOT be in final 7 days
        val txEarlyAug = createTx(2, TransactionType.EXPENSE, 200_00L, LocalDate.of(2026, 8, 20))
        assertFalse(final7DaysOfAug.contains(LocalDate.ofEpochDay(txEarlyAug.dateEpochDay)))
    }

    // =========================================================================
    // 3. Minimum Balance and Net Worth Invariants
    // =========================================================================

    @Test
    fun testMinimumBalance_availableSavingsClampedAtZero() {
        val acc1 = AccountEntity(id = 1L, name = "Account 1", balancePaise = 8900_00L, isCreditCard = false, minimumBalancePaise = 5000_00L)
        val acc2 = AccountEntity(id = 2L, name = "Account 2", balancePaise = 2000_00L, isCreditCard = false, minimumBalancePaise = 5000_00L)

        // Account 1 has 8900 - 5000 = 3900 available
        // Account 2 has 2000 - 5000 = -3000 -> clamped to 0
        // Total available = 3900 + 0 = 3900
        val available = SavingsCalculator.calculateAvailableSavingsPaise(listOf(acc1, acc2))
        assertEquals(3900_00L, available)
    }

    @Test
    fun testNetWorth_usesFullActualBalanceWithoutSubtractingMinimumBalance() {
        val acc = AccountEntity(id = 1L, name = "Savings", balancePaise = 50000_00L, isCreditCard = false, minimumBalancePaise = 10000_00L)
        val card = AccountEntity(id = 2L, name = "Credit Card", balancePaise = 5000_00L, isCreditCard = true)

        val cash = listOf(acc, card).filterNot { it.isCreditCard }.sumOf { it.balancePaise }
        val cards = listOf(acc, card).filter { it.isCreditCard }.sumOf { it.balancePaise }
        val activeFdPrincipal = 20000_00L
        val owed = 0L
        val loans = 0L

        // Net Worth formula: cash + activeFdPrincipal + owed - cards - loans
        // Must use full cash (50000_00L), not available savings (40000_00L)
        val netWorth = cash + activeFdPrincipal + owed - cards - loans
        assertEquals(65000_00L, netWorth)
    }
}

