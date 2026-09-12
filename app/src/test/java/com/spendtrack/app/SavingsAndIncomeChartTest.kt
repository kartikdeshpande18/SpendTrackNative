package com.spendtrack.app

import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import com.spendtrack.app.ui.dashboard.SavingsCalculator
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SavingsAndIncomeChartTest {

    // =========================================================================
    // 1. Available Savings Tests
    // =========================================================================

    @Test
    fun testAvailableSavings_subtractsMinimumBalance() {
        // Balance = ₹8,900, Min Balance = ₹5,000 -> Available Savings = ₹3,900
        val account = AccountEntity(
            id = 1L,
            name = "HDFC Savings",
            balancePaise = 8900_00L,
            isCreditCard = false,
            minimumBalancePaise = 5000_00L
        )
        val available = SavingsCalculator.calculateAvailableSavingsPaise(listOf(account))
        assertEquals(3900_00L, available)
    }

    @Test
    fun testAvailableSavings_clampsToZeroWhenBalanceBelowMinimum() {
        // Balance = ₹4,000, Min Balance = ₹5,000 -> Available Savings = ₹0
        val account = AccountEntity(
            id = 1L,
            name = "SBI Savings",
            balancePaise = 4000_00L,
            isCreditCard = false,
            minimumBalancePaise = 5000_00L
        )
        val available = SavingsCalculator.calculateAvailableSavingsPaise(listOf(account))
        assertEquals(0L, available)
    }

    @Test
    fun testAvailableSavings_fullBalanceWhenMinimumBalanceIsZero() {
        // Min balance = 0 -> Available Savings = full balance ₹10,000
        val account = AccountEntity(
            id = 1L,
            name = "Axis Savings",
            balancePaise = 10000_00L,
            isCreditCard = false,
            minimumBalancePaise = 0L
        )
        val available = SavingsCalculator.calculateAvailableSavingsPaise(listOf(account))
        assertEquals(10000_00L, available)
    }

    @Test
    fun testAvailableSavings_multipleAccountsAggregatedIndependently() {
        // Account 1: ₹8,900 with ₹5,000 min -> ₹3,900
        // Account 2: ₹4,000 with ₹5,000 min -> ₹0 (clamped, does not deduct from other accounts)
        // Account 3: ₹20,000 with ₹10,000 min -> ₹10,000
        // Total = ₹13,900
        val a1 = AccountEntity(id = 1L, name = "HDFC", balancePaise = 8900_00L, isCreditCard = false, minimumBalancePaise = 5000_00L)
        val a2 = AccountEntity(id = 2L, name = "SBI", balancePaise = 4000_00L, isCreditCard = false, minimumBalancePaise = 5000_00L)
        val a3 = AccountEntity(id = 3L, name = "ICICI", balancePaise = 20000_00L, isCreditCard = false, minimumBalancePaise = 10000_00L)

        val available = SavingsCalculator.calculateAvailableSavingsPaise(listOf(a1, a2, a3))
        assertEquals(13900_00L, available)
    }

    @Test
    fun testAvailableSavings_creditCardsAreExcluded() {
        val savings = AccountEntity(id = 1L, name = "HDFC Savings", balancePaise = 10000_00L, isCreditCard = false, minimumBalancePaise = 2000_00L)
        val card = AccountEntity(id = 2L, name = "HDFC Millennia", balancePaise = 5000_00L, isCreditCard = true, minimumBalancePaise = 0L)

        val available = SavingsCalculator.calculateAvailableSavingsPaise(listOf(savings, card))
        // Only savings account contributes: 10,000 - 2,000 = 8,000
        assertEquals(8000_00L, available)
    }

    @Test
    fun testAvailableSavings_doesNotModifyNetWorthOrAccountBalance() {
        val a1 = AccountEntity(id = 1L, name = "HDFC Savings", balancePaise = 8900_00L, isCreditCard = false, minimumBalancePaise = 5000_00L)
        val card = AccountEntity(id = 2L, name = "Credit Card", balancePaise = 2000_00L, isCreditCard = true, minimumBalancePaise = 0L)
        val accounts = listOf(a1, card)

        // Available savings calculation
        val available = SavingsCalculator.calculateAvailableSavingsPaise(accounts)
        assertEquals(3900_00L, available)

        // Critical Financial Invariant: Net Worth uses full actual balance!
        val cash = accounts.filterNot { it.isCreditCard }.sumOf { it.balancePaise }
        val cardDues = accounts.filter { it.isCreditCard }.sumOf { it.balancePaise }
        val netWorth = cash - cardDues // Without loans/FDs for this test

        assertEquals(8900_00L, cash)
        assertEquals(6900_00L, netWorth)
        assertEquals(8900_00L, a1.balancePaise)
    }

    // =========================================================================
    // 2. Income vs Expense Calculation Tests
    // =========================================================================

    @Test
    fun testIncomeVsExpense_calculatesTotalsCorrectly() {
        val today = LocalDate.now().toEpochDay()
        val t1 = TransactionEntity(id = 1L, dateEpochDay = today, description = "Salary", amountPaise = 50000_00L, categoryId = 1L, accountId = 1L, type = TransactionType.INCOME)
        val t2 = TransactionEntity(id = 2L, dateEpochDay = today, description = "Freelance", amountPaise = 15000_00L, categoryId = 1L, accountId = 1L, type = TransactionType.INCOME)
        val t3 = TransactionEntity(id = 3L, dateEpochDay = today, description = "Groceries", amountPaise = 5000_00L, categoryId = 2L, accountId = 1L, type = TransactionType.EXPENSE)
        val t4 = TransactionEntity(id = 4L, dateEpochDay = today, description = "Rent", amountPaise = 20000_00L, categoryId = 3L, accountId = 1L, type = TransactionType.EXPENSE)

        val transactions = listOf(t1, t2, t3, t4)
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.mySharePaise ?: it.amountPaise }

        assertEquals(65000_00L, income)
        assertEquals(25000_00L, expense)
    }

    @Test
    fun testIncomeVsExpense_excludesTransfers() {
        val today = LocalDate.now().toEpochDay()
        val incomeT = TransactionEntity(id = 1L, dateEpochDay = today, description = "Salary", amountPaise = 40000_00L, categoryId = 1L, accountId = 1L, type = TransactionType.INCOME)
        val expenseT = TransactionEntity(id = 2L, dateEpochDay = today, description = "Utilities", amountPaise = 3000_00L, categoryId = 2L, accountId = 1L, type = TransactionType.EXPENSE)
        val transferT = TransactionEntity(id = 3L, dateEpochDay = today, description = "Transfer to Savings", amountPaise = 15000_00L, categoryId = 0L, accountId = 1L, destinationAccountId = 2L, type = TransactionType.TRANSFER)

        val transactions = listOf(incomeT, expenseT, transferT)
        val income = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        val expense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.mySharePaise ?: it.amountPaise }

        assertEquals(40000_00L, income)
        assertEquals(3000_00L, expense)
    }

    @Test
    fun testIncomeVsExpense_respectsSharedExpenses() {
        val today = LocalDate.now().toEpochDay()
        // Dinner bill ₹4,000, but my share is ₹1,500
        val sharedExpense = TransactionEntity(
            id = 1L,
            dateEpochDay = today,
            description = "Team Dinner",
            amountPaise = 4000_00L,
            categoryId = 2L,
            accountId = 1L,
            type = TransactionType.EXPENSE,
            isShared = true,
            mySharePaise = 1500_00L,
            sharedWith = "Colleagues"
        )
        // Normal expense ₹2,000
        val normalExpense = TransactionEntity(
            id = 2L,
            dateEpochDay = today,
            description = "Fuel",
            amountPaise = 2000_00L,
            categoryId = 3L,
            accountId = 1L,
            type = TransactionType.EXPENSE,
            isShared = false,
            mySharePaise = null
        )

        val transactions = listOf(sharedExpense, normalExpense)
        val totalExpense = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.mySharePaise ?: it.amountPaise }

        // 1,500 + 2,000 = 3,500
        assertEquals(3500_00L, totalExpense)
    }
}
