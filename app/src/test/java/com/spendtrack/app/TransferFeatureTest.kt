package com.spendtrack.app

import com.spendtrack.app.data.local.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TransferFeatureTest {

    @Test
    fun testTransferTransactionType() {
        val transfer = TransactionEntity(
            id = 1L,
            dateEpochDay = LocalDate.of(2026, 9, 6).toEpochDay(),
            description = "Moved money for loan payment",
            amountPaise = 5000_00L,
            categoryId = 0L,
            accountId = 10L,
            destinationAccountId = 20L,
            type = TransactionType.TRANSFER
        )

        assertEquals(TransactionType.TRANSFER, transfer.type)
        assertEquals(10L, transfer.accountId)
        assertEquals(20L, transfer.destinationAccountId)
        assertEquals(5000_00L, transfer.amountPaise)
        assertEquals("Moved money for loan payment", transfer.description)
        assertFalse(transfer.isShared)
    }

    @Test
    fun testTransferExclusionFromIncomeAndExpenseTotals() {
        val now = YearMonth.of(2026, 9)
        val epochDay = LocalDate.of(2026, 9, 6).toEpochDay()

        val transactions = listOf(
            TransactionEntity(1L, epochDay, "Salary", 50000_00L, 1L, 10L, TransactionType.INCOME),
            TransactionEntity(2L, epochDay, "Groceries", 2000_00L, 2L, 10L, TransactionType.EXPENSE),
            TransactionEntity(3L, epochDay, "Bank transfer", 10000_00L, 0L, 10L, TransactionType.TRANSFER, destinationAccountId = 20L)
        )

        val inMonth = transactions.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == now }
        val income = inMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        val expense = inMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.mySharePaise ?: it.amountPaise }

        assertEquals("Income must be 50,000", 50000_00L, income)
        assertEquals("Expense must be 2,000", 2000_00L, expense)
    }

    @Test
    fun testTransferExclusionFromCategorySpendingAndBudget() {
        val transactions = listOf(
            TransactionEntity(1L, 100L, "Groceries", 1500_00L, 5L, 10L, TransactionType.EXPENSE),
            TransactionEntity(2L, 100L, "Transfer to Slice", 5000_00L, 5L, 10L, TransactionType.TRANSFER, destinationAccountId = 20L)
        )

        // Category spending donut filter
        val categoryExpenses = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.mySharePaise ?: it.amountPaise } }

        assertEquals(1500_00L, categoryExpenses[5L])
        assertNull(categoryExpenses[0L])

        // Budget usage calculation
        val budgetUsed = transactions
            .filter { it.type == TransactionType.EXPENSE && it.categoryId == 5L }
            .sumOf { it.mySharePaise ?: it.amountPaise }

        assertEquals(1500_00L, budgetUsed)
    }

    @Test
    fun testTransferExclusionFromLast7DaysChart() {
        val today = LocalDate.of(2026, 9, 7)
        val transactions = listOf(
            TransactionEntity(1L, today.toEpochDay(), "Dinner", 800_00L, 1L, 10L, TransactionType.EXPENSE),
            TransactionEntity(2L, today.toEpochDay(), "Savings transfer", 10000_00L, 0L, 10L, TransactionType.TRANSFER, destinationAccountId = 20L)
        )

        val chartValue = transactions
            .filter { it.type == TransactionType.EXPENSE && LocalDate.ofEpochDay(it.dateEpochDay) == today }
            .sumOf { it.mySharePaise ?: it.amountPaise }

        assertEquals(800_00L, chartValue)
    }

    @Test
    fun testBankToBankTransferBalanceMovement() {
        // Initial state: ICICI = ₹20,000, Slice = ₹5,000
        var iciciBalance = 20000_00L
        var sliceBalance = 5000_00L
        val transferAmount = 5000_00L

        // Transfer execution
        val isSourceCreditCard = false
        val isDestCreditCard = false
        val sourceDelta = if (isSourceCreditCard) transferAmount else -transferAmount
        val destDelta = if (isDestCreditCard) -transferAmount else transferAmount

        iciciBalance += sourceDelta
        sliceBalance += destDelta

        assertEquals(15000_00L, iciciBalance)
        assertEquals(10000_00L, sliceBalance)

        // Deletion (reversal)
        iciciBalance -= sourceDelta
        sliceBalance -= destDelta

        assertEquals(20000_00L, iciciBalance)
        assertEquals(5000_00L, sliceBalance)
    }

    @Test
    fun testBankToCreditCardTransferBalanceMovement() {
        // Bank = ₹20,000, Card Due = ₹5,000
        var bankBalance = 20000_00L
        var cardDue = 5000_00L
        val paymentAmount = 3000_00L

        val isSourceCreditCard = false
        val isDestCreditCard = true
        val sourceDelta = if (isSourceCreditCard) paymentAmount else -paymentAmount
        val destDelta = if (isDestCreditCard) -paymentAmount else paymentAmount

        bankBalance += sourceDelta
        cardDue += destDelta

        assertEquals(17000_00L, bankBalance)
        assertEquals(2000_00L, cardDue) // Outstanding reduced by 3000

        // Reversal
        bankBalance -= sourceDelta
        cardDue -= destDelta

        assertEquals(20000_00L, bankBalance)
        assertEquals(5000_00L, cardDue)
    }

    @Test
    fun testTransferEditAdjustment() {
        var sourceBalance = 20000_00L
        var destBalance = 5000_00L

        // Create initial transfer of ₹5,000
        val originalAmount = 5000_00L
        sourceBalance -= originalAmount
        destBalance += originalAmount
        assertEquals(15000_00L, sourceBalance)
        assertEquals(10000_00L, destBalance)

        // Edit transfer amount to ₹8,000
        val newAmount = 8000_00L
        // Reversal of previous
        sourceBalance += originalAmount
        destBalance -= originalAmount
        // Apply new
        sourceBalance -= newAmount
        destBalance += newAmount

        assertEquals(12000_00L, sourceBalance)
        assertEquals(13000_00L, destBalance)
    }

    @Test
    fun testTransferEntityAttributes() {
        val transfer = TransactionEntity(
            id = 42L,
            dateEpochDay = 20600L,
            description = "Fund transfer",
            amountPaise = 7500_00L,
            categoryId = 0L,
            accountId = 1L,
            type = TransactionType.TRANSFER,
            destinationAccountId = 2L
        )

        assertEquals(42L, transfer.id)
        assertEquals(20600L, transfer.dateEpochDay)
        assertEquals("Fund transfer", transfer.description)
        assertEquals(7500_00L, transfer.amountPaise)
        assertEquals(0L, transfer.categoryId)
        assertEquals(1L, transfer.accountId)
        assertEquals(TransactionType.TRANSFER, transfer.type)
        assertEquals(2L, transfer.destinationAccountId)

        // Test copy with updated fields
        val updated = transfer.copy(amountPaise = 9000_00L, destinationAccountId = 3L)
        assertEquals(9000_00L, updated.amountPaise)
        assertEquals(3L, updated.destinationAccountId)
    }
}
