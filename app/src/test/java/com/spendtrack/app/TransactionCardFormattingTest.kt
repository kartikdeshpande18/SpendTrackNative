package com.spendtrack.app

import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import com.spendtrack.app.ui.formatIndianMoney
import com.spendtrack.app.ui.money
import org.junit.Assert.*
import org.junit.Test

class TransactionCardFormattingTest {

    // =========================================================================
    // 1. Currency Formatting & Integrity Tests
    // =========================================================================

    @Test
    fun testRequiredCurrencyAmounts_formatCorrectlyWithoutWrap() {
        // Required test amounts:
        // ₹0.71
        // ₹10.00
        // ₹100.00
        // ₹1,500.00
        // ₹15,000.00
        // ₹1,54,999.99

        assertEquals("₹0.71", formatIndianMoney(71L))
        assertEquals("₹10.00", formatIndianMoney(10_00L))
        assertEquals("₹100.00", formatIndianMoney(100_00L))
        assertEquals("₹1,500.00", formatIndianMoney(1500_00L))
        assertEquals("₹15,000.00", formatIndianMoney(15000_00L))
        assertEquals("₹1,54,999.99", formatIndianMoney(154999_99L))

        // Also verify money() helper delegates identically
        assertEquals("₹0.71", money(71L))
        assertEquals("₹10.00", money(10_00L))
        assertEquals("₹100.00", money(100_00L))
        assertEquals("₹1,500.00", money(1500_00L))
        assertEquals("₹15,000.00", money(15000_00L))
        assertEquals("₹1,54,999.99", money(154999_99L))
    }

    @Test
    fun testCurrencyFormatting_zeroAndLargeLakhsCrores() {
        assertEquals("₹0.00", formatIndianMoney(0L))
        assertEquals("₹1,00,000.00", formatIndianMoney(100000_00L))
        assertEquals("₹10,00,000.00", formatIndianMoney(1000000_00L))
        assertEquals("₹1,00,00,000.00", formatIndianMoney(10000000_00L))
    }

    @Test
    fun testCurrencyFormatting_negativeAmounts() {
        assertEquals("−₹100.00", formatIndianMoney(-100_00L))
        assertEquals("−₹1,54,999.99", formatIndianMoney(-154999_99L))
    }

    @Test
    fun testCurrencyFormatting_symbolAndValueRemainTogether() {
        val testPaiseValues = listOf(
            71L,
            10_00L,
            100_00L,
            1500_00L,
            15000_00L,
            154999_99L
        )

        for (paise in testPaiseValues) {
            val formatted = money(paise)
            // Starts with the rupee symbol
            assertTrue("Must start with ₹: $formatted", formatted.startsWith("₹"))
            // Contains no spaces that would permit soft wrapping between symbol and number
            assertFalse("Must contain no spaces: $formatted", formatted.contains(" "))
            // Contains no line breaks
            assertFalse("Must contain no line breaks: $formatted", formatted.contains("\n") || formatted.contains("\r"))
            // Ends with 2 decimal digits
            assertTrue("Must have 2 decimal places: $formatted", formatted.matches(Regex("""^₹[0-9,]+\.[0-9]{2}$""")))
        }
    }

    // =========================================================================
    // 2. Transaction Card Data Model & Layout Scenarios
    // =========================================================================

    @Test
    fun testTransactionCardScenarios_incomeExpenseTransfer() {
        val testAccounts = listOf(
            AccountEntity(id = 1L, name = "State Bank of India Platinum Salary Account", balancePaise = 250000_00L),
            AccountEntity(id = 2L, name = "HDFC Diners Black Credit Card", balancePaise = 45000_00L, isCreditCard = true)
        )
        val testCategories = listOf(
            CategoryEntity(id = 1L, name = "Home Improvement, Furniture & Maintenance", isIncomeCategory = false),
            CategoryEntity(id = 2L, name = "Freelance Software Consulting & Advisory", isIncomeCategory = true)
        )

        // 1. Short Description Expense
        val shortExpense = TransactionEntity(
            id = 101L,
            description = "Tea",
            amountPaise = 10_00L,
            categoryId = 1L,
            accountId = 1L,
            type = TransactionType.EXPENSE,
            dateEpochDay = 20600L
        )
        val shortExpenseAmount = "−" + money(shortExpense.amountPaise)
        assertEquals("−₹10.00", shortExpenseAmount)
        assertFalse(shortExpenseAmount.contains(" "))

        // 2. Long Description Expense
        val longExpense = TransactionEntity(
            id = 102L,
            description = "Annual comprehensive health insurance premium payment for whole family including critical illness and tax benefits under section 80D",
            amountPaise = 154999_99L,
            categoryId = 1L,
            accountId = 1L,
            type = TransactionType.EXPENSE,
            dateEpochDay = 20600L
        )
        val longExpenseAmount = "−" + money(longExpense.amountPaise)
        assertEquals("−₹1,54,999.99", longExpenseAmount)
        assertTrue(longExpense.description.length > 50)

        // 3. Long Category and Long Account Income
        val incomeTx = TransactionEntity(
            id = 103L,
            description = "Monthly retainer consulting fee",
            amountPaise = 15000_00L,
            categoryId = 2L,
            accountId = 1L,
            type = TransactionType.INCOME,
            dateEpochDay = 20600L
        )
        val incomeAmount = "+" + money(incomeTx.amountPaise)
        assertEquals("+₹15,000.00", incomeAmount)
        val categoryName = testCategories.find { it.id == incomeTx.categoryId }?.name
        assertEquals("Freelance Software Consulting & Advisory", categoryName)

        // 4. Transfer Transaction between long account names
        val transferTx = TransactionEntity(
            id = 104L,
            description = "Transfer from SBI to HDFC Card bill payment",
            amountPaise = 1500_00L,
            categoryId = 0L,
            accountId = 1L,
            destinationAccountId = 2L,
            type = TransactionType.TRANSFER,
            dateEpochDay = 20600L
        )
        val transferAmount = money(transferTx.amountPaise)
        assertEquals("₹1,500.00", transferAmount)
        val srcName = testAccounts.find { it.id == transferTx.accountId }?.name
        val dstName = testAccounts.find { it.id == transferTx.destinationAccountId }?.name
        assertEquals("State Bank of India Platinum Salary Account", srcName)
        assertEquals("HDFC Diners Black Credit Card", dstName)
    }

    @Test
    fun testTransactionCard_exactPaisaAmountFractionalIntegrity() {
        val paise = 71L // ₹0.71
        val formatted = money(paise)
        assertEquals("₹0.71", formatted)
        assertEquals('7', formatted[formatted.length - 2])
        assertEquals('1', formatted[formatted.length - 1])
    }
}

