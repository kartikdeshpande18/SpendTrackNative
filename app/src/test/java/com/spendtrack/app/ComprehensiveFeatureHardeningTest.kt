package com.spendtrack.app

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.spendtrack.app.data.local.*
import com.spendtrack.app.data.repository.BackupRepository
import com.spendtrack.app.data.repository.FinanceRepository
import com.spendtrack.app.ui.animation.ChartAnimationState
import com.spendtrack.app.ui.dashboard.TopBudgetsCalculator
import com.spendtrack.app.ui.spending.DateFilterType
import com.spendtrack.app.ui.spending.TransactionDateFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ComprehensiveFeatureHardeningTest {

    @Test
    fun testSharedExpensePeopleAreUniqueRenamePreservesHistoryAndDeactivateBlocksSelection() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository
        val personId = repo.createPerson("Shubhankar")
        assertEquals(personId, repo.createPerson(" shubhankar "))
        assertEquals(1, env.database.people().observeAll().first().size)
        val accountId = env.accountsDao.insert(AccountEntity(id = 1L, name = "Cash", balancePaise = 10_000L))
        val categoryId = env.categoriesDao.insert(CategoryEntity(id = 1L, name = "Food"))
        val split = SharedExpenseHelper.serialize(
            SplitDetails(
                participants = listOf("You", "Shubhankar"),
                shares = mapOf("You" to 500L, "Shubhankar" to 500L)
            )
        )
        val txId = repo.addTransaction(
            TransactionEntity(
                dateEpochDay = LocalDate.of(2026, 10, 31).toEpochDay(), description = "Dinner", amountPaise = 1_000L,
                categoryId = categoryId, accountId = accountId, isShared = true, splitDetails = split
            )
        )
        val balanceBeforeRename = env.accountsDao.getById(accountId)!!.balancePaise

        repo.renamePerson(personId, "Shubh")

        val updatedTransaction = env.transactionsDao.getById(txId)!!
        assertEquals(listOf("You", "Shubh"), SharedExpenseHelper.deserialize(updatedTransaction.splitDetails)!!.participants)
        assertEquals("Shubh", env.owedDao.observeAll().first().single().personName)
        assertEquals(1_000L, updatedTransaction.amountPaise)
        assertEquals(balanceBeforeRename, env.accountsDao.getById(accountId)!!.balancePaise)
        assertTrue(runCatching { repo.deletePerson(personId) }.isFailure)

        repo.setPersonActive(personId, false)
        repo.syncPeopleFromHistory()
        assertTrue(env.database.people().observeActive().first().isEmpty())
        assertEquals("Shubh", env.owedDao.observeAll().first().single().personName)
        assertTrue(runCatching {
            repo.addOwed(OwedEntity(personName = "Unknown", amountPaise = 100L, direction = OwedDirection.OWED_TO_ME))
        }.isFailure)
    }

    @Test
    fun testSavingsInterestPeriodFormattingAcrossMonthAndLeapYearBoundaries() {
        assertEquals(
            "Interest for 31-Oct-2026",
            SavingsInterestPeriods.description(
                InterestCreditFrequency.DAILY,
                SavingsInterestPeriods.containing(LocalDate.of(2026, 10, 31), InterestCreditFrequency.DAILY)
            )
        )
        assertEquals(
            "Interest for 01-Feb-2024 - 29-Feb-2024",
            SavingsInterestPeriods.description(
                InterestCreditFrequency.MONTHLY,
                SavingsInterestPeriods.containing(LocalDate.of(2024, 2, 12), InterestCreditFrequency.MONTHLY)
            )
        )
        assertEquals(
            SavingsInterestPeriod(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 12, 31)),
            SavingsInterestPeriods.containing(LocalDate.of(2026, 12, 31), InterestCreditFrequency.QUARTERLY)
        )
        assertEquals(
            LocalDate.of(2026, 9, 30),
            SavingsInterestPeriods.lastCompletedThrough(LocalDate.of(2026, 10, 1), InterestCreditFrequency.QUARTERLY)
        )
    }

    // =========================================================================
    // 1. SHARED EXPENSE HELPER TESTS
    // =========================================================================

    @Test
    fun testSharedExpenseParticipantsAreNotHardcoded() {
        val members = SharedExpenseHelper.DEFAULT_PARTICIPANTS
        assertTrue("Default participants must be empty (not hardcoded)", members.isEmpty())
    }

    @Test
    fun testSharedExpenseWithDynamicOwedParticipants() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository
        val bankId = env.accountsDao.insert(AccountEntity(id = 1L, name = "Bank", balancePaise = 50000L))
        val catId = env.categoriesDao.insert(CategoryEntity(id = 1L, name = "Dining", isIncomeCategory = false))

        // Create a shared expense with participants "You" and "Dev" (added dynamically)
        val splitDetails = SharedExpenseHelper.serialize(
            SplitDetails(
                payer = "You",
                splitMethod = SplitMethod.EQUAL,
                participants = listOf("You", "Dev"),
                shares = mapOf("You" to 2500L, "Dev" to 2500L)
            )
        )
        val tx = TransactionEntity(
            dateEpochDay = LocalDate.now().toEpochDay(),
            description = "Dinner with Dev",
            amountPaise = 5000L,
            categoryId = catId,
            accountId = bankId,
            type = TransactionType.EXPENSE,
            isShared = true,
            mySharePaise = 2500L,
            sharedWith = "Dev",
            splitDetails = splitDetails
        )
        val txId = repo.addTransaction(tx)
        assertTrue(txId > 0)

        // Verify Dev was inserted into Owed with OWED_TO_ME
        val owedEntries = env.owedDao.getByPerson("Dev")
        assertEquals(1, owedEntries.size)
        assertEquals(2500L, owedEntries[0].amountPaise)
        assertEquals(OwedDirection.OWED_TO_ME, owedEntries[0].direction)
        assertEquals("Dinner with Dev", owedEntries[0].note)

        // Verify "You" was not added as an owed entry
        val youEntries = env.owedDao.getByPerson("You")
        assertTrue(youEntries.isEmpty())
    }

    @Test
    fun testPayCardFromAccountsSectionCreatesTransactionEntry() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository
        val bankId = env.accountsDao.insert(AccountEntity(id = 1L, name = "HDFC Bank", balancePaise = 25000L, isCreditCard = false))
        val cardId = env.accountsDao.insert(AccountEntity(id = 2L, name = "ICICI Credit Card", balancePaise = 8000L, isCreditCard = true))

        repo.payCard(cardId, bankId, 8000L, LocalDate.of(2026, 10, 25).toEpochDay())

        // 1. Account balances updated
        val updatedBank = env.accountsDao.getById(bankId)!!
        val updatedCard = env.accountsDao.getById(cardId)!!
        assertEquals(17000L, updatedBank.balancePaise)
        assertEquals(0L, updatedCard.balancePaise)

        // 2. Transaction entry must be created
        val allTx = env.transactionsDao.observeAll().first()
        val payTx = allTx.firstOrNull { it.accountId == bankId && it.destinationAccountId == cardId }
        assertNotNull(payTx)
        assertEquals(8000L, payTx!!.amountPaise)
        assertEquals(TransactionType.TRANSFER, payTx.type)
        assertEquals("Bill Payment: ICICI Credit Card", payTx.description)
        assertFalse(payTx.isExcludedFromCalculations)
    }

    @Test
    fun testEqualSplit_exactDivisibility() {
        val shares = SharedExpenseHelper.calculateEqualSplit(6000L, listOf("Yash", "Kartik", "Aryan"))
        assertEquals(3, shares.size)
        assertEquals(2000L, shares["Yash"])
        assertEquals(2000L, shares["Kartik"])
        assertEquals(2000L, shares["Aryan"])
        assertEquals(6000L, shares.values.sum())
    }

    @Test
    fun testEqualSplit_remainderPaiseDistribution() {
        // 1000 paise divided among 3 people: 1000 / 3 = 333 rem 1 -> First person gets 334
        val shares = SharedExpenseHelper.calculateEqualSplit(1000L, listOf("Kartik", "Yash", "Sanket"))
        assertEquals(334L, shares["Kartik"])
        assertEquals(333L, shares["Yash"])
        assertEquals(333L, shares["Sanket"])
        assertEquals(1000L, shares.values.sum())
    }

    @Test
    fun testCustomSplitValidation() {
        val customValid = mapOf("Kartik" to 500L, "Yash" to 300L, "Nilesh" to 200L)
        assertTrue(SharedExpenseHelper.validateCustomSplit(1000L, customValid))

        val customMismatched = mapOf("Kartik" to 500L, "Yash" to 300L, "Nilesh" to 100L)
        assertFalse(SharedExpenseHelper.validateCustomSplit(1000L, customMismatched))

        val customNegative = mapOf("Kartik" to 1200L, "Yash" to -200L)
        assertFalse(SharedExpenseHelper.validateCustomSplit(1000L, customNegative))
    }

    @Test
    fun testSplitDetailsJsonRoundTrip() {
        val details = SplitDetails(
            payer = "Kartik",
            splitMethod = SplitMethod.CUSTOM,
            participants = listOf("Kartik", "Aryan"),
            shares = mapOf("Kartik" to 400L, "Aryan" to 600L)
        )
        val json = SharedExpenseHelper.serialize(details)
        val parsed = SharedExpenseHelper.deserialize(json)
        assertNotNull(parsed)
        assertEquals(details.payer, parsed?.payer)
        assertEquals(details.splitMethod, parsed?.splitMethod)
        assertEquals(details.participants, parsed?.participants)
        assertEquals(details.shares, parsed?.shares)
    }

    // =========================================================================
    // 2. SAVINGS INTEREST CALCULATOR TESTS
    // =========================================================================

    @Test
    fun testDailyBalanceInterestCalculation() {
        // Daily balance 1,000,000 paise (Rs 10,000) at 365 bps (3.65% p.a.) for 10 days
        // Formula: balance * (rateBps / 10000) * (days / 365) = 1,000,000 * 0.0365 * (10 / 365) = 1000 paise (Rs 10)
        val dailyBalances = (1L..10L).associateWith { 1_000_000L }
        val interest = SavingsInterestCalculator.calculateInterestPaise(
            dailyBalances = dailyBalances,
            fallbackBalancePaise = 1_000_000L,
            interestRateBps = 365,
            method = InterestCalculationMethod.DAILY,
            startEpochDay = 1L,
            endEpochDay = 10L
        )
        assertEquals(1000L, interest)
    }

    @Test
    fun testMonthlyAverageInterestCalculation() {
        val daily = mutableMapOf<Long, Long>()
        for (day in 1L..20L) daily[day] = 10000L
        for (day in 21L..30L) daily[day] = 20000L

        val interest = SavingsInterestCalculator.calculateInterestPaise(
            dailyBalances = daily,
            fallbackBalancePaise = 10000L,
            interestRateBps = 1200,
            method = InterestCalculationMethod.MONTHLY_AVERAGE,
            startEpochDay = 1L,
            endEpochDay = 30L
        )
        assertTrue(interest > 0)
    }

    @Test
    fun testDailyBalanceReconstruction() {
        // Start with opening balance of 1000 paise on day 10
        // Day 12: credit 500 paise
        // Day 14: debit 300 paise
        val txs = listOf(
            TransactionEntity(id = 1L, dateEpochDay = 12L, description = "Deposit", amountPaise = 500L, categoryId = 0L, accountId = 1L, type = TransactionType.INCOME),
            TransactionEntity(id = 2L, dateEpochDay = 14L, description = "Expense", amountPaise = 300L, categoryId = 1L, accountId = 1L, type = TransactionType.EXPENSE)
        )
        val balances = SavingsInterestCalculator.reconstructDailyBalances(
            currentBalancePaise = 1200L, // 1000 + 500 - 300 = 1200 on day 15
            todayEpochDay = 15L,
            accountTransactions = txs,
            startEpochDay = 10L,
            endEpochDay = 15L
        )

        assertEquals(6, balances.size) // days 10, 11, 12, 13, 14, 15
        assertEquals(1000L, balances[10L])
        assertEquals(1000L, balances[11L])
        assertEquals(1500L, balances[12L])
        assertEquals(1500L, balances[13L])
        assertEquals(1200L, balances[14L])
        assertEquals(1200L, balances[15L])
    }

    // =========================================================================
    // 3. TRANSACTION REPORTING EXCLUSION TESTS
    // =========================================================================

    @Test
    fun testTransactionExclusionFromTotalsAndCharts() {
        val today = LocalDate.of(2026, 10, 15).toEpochDay()
        val cat1 = CategoryEntity(id = 1L, name = "Food", isIncomeCategory = false)
        val cat2 = CategoryEntity(id = 2L, name = "Salary", isIncomeCategory = true)

        val txNormalExpense = TransactionEntity(id = 1L, dateEpochDay = today, description = "Dinner", amountPaise = 500L, categoryId = cat1.id, accountId = 1L, type = TransactionType.EXPENSE, isExcludedFromCalculations = false)
        val txExcludedExpense = TransactionEntity(id = 2L, dateEpochDay = today, description = "Owed Settlement Out", amountPaise = 1000L, categoryId = cat1.id, accountId = 1L, type = TransactionType.EXPENSE, isExcludedFromCalculations = true)
        val txNormalIncome = TransactionEntity(id = 3L, dateEpochDay = today, description = "Salary", amountPaise = 5000L, categoryId = cat2.id, accountId = 1L, type = TransactionType.INCOME, isExcludedFromCalculations = false)
        val txExcludedIncome = TransactionEntity(id = 4L, dateEpochDay = today, description = "Owed Settlement In", amountPaise = 2000L, categoryId = cat2.id, accountId = 1L, type = TransactionType.INCOME, isExcludedFromCalculations = true)

        val all = listOf(txNormalExpense, txExcludedExpense, txNormalIncome, txExcludedIncome)

        // Spending total should only include non-excluded expenses
        val spending = all.filter { it.type == TransactionType.EXPENSE && !it.isExcludedFromReports }.sumOf { it.mySharePaise ?: it.amountPaise }
        assertEquals(500L, spending)

        // Income total should only include non-excluded incomes
        val income = all.filter { it.type == TransactionType.INCOME && !it.isExcludedFromReports }.sumOf { it.amountPaise }
        assertEquals(5000L, income)

        // Chart filtering
        val filteredForChart = TransactionDateFilter.filterForChart(all, null, TransactionDateFilter(DateFilterType.ALL_DATES), LocalDate.ofEpochDay(today))
        assertEquals(2, filteredForChart.size) // txNormalExpense and txNormalIncome
        assertTrue(filteredForChart.contains(txNormalExpense))
        assertTrue(filteredForChart.contains(txNormalIncome))
        assertFalse(filteredForChart.contains(txExcludedExpense))
        assertFalse(filteredForChart.contains(txExcludedIncome))

        // Budget calculation should exclude excluded transactions
        val budget = BudgetEntity(id = 1L, categoryId = cat1.id, monthKey = "2026-10", limitPaise = 2000L)
        val topBudgets = TopBudgetsCalculator.calculateTopBudgetCategories(listOf(budget), listOf(cat1), all, "2026-10")
        assertEquals(1, topBudgets.size)
        assertEquals(500L, topBudgets[0].spentPaise) // only 500L, excluded 1000L omitted!
    }

    @Test
    fun testExcludeFromCalculationsDoesNotAffectAccountBalance() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository
        val accId = env.accountsDao.insert(AccountEntity(id = 1L, name = "Test Bank", balancePaise = 10000L))
        val catId = env.categoriesDao.insert(CategoryEntity(id = 1L, name = "General", isIncomeCategory = false))

        // 1. Add expense with isExcludedFromCalculations = true -> balance must remain 10000
        val tx1 = TransactionEntity(
            dateEpochDay = LocalDate.now().toEpochDay(),
            description = "Excluded Expense",
            amountPaise = 3000L,
            categoryId = catId,
            accountId = accId,
            type = TransactionType.EXPENSE,
            isExcludedFromCalculations = true
        )
        val id1 = repo.addTransaction(tx1)
        val accAfterTx1 = env.accountsDao.getById(accId)!!
        assertEquals(10000L, accAfterTx1.balancePaise) // Balance untouched!

        // 2. Add income with isExcludedFromCalculations = true -> balance must remain 10000
        val tx2 = TransactionEntity(
            dateEpochDay = LocalDate.now().toEpochDay(),
            description = "Excluded Income",
            amountPaise = 5000L,
            categoryId = catId,
            accountId = accId,
            type = TransactionType.INCOME,
            isExcludedFromCalculations = true
        )
        val id2 = repo.addTransaction(tx2)
        val accAfterTx2 = env.accountsDao.getById(accId)!!
        assertEquals(10000L, accAfterTx2.balancePaise) // Balance untouched!

        // 3. Delete excluded transaction -> balance must still remain 10000
        repo.deleteTransaction(id1)
        val accAfterDelete = env.accountsDao.getById(accId)!!
        assertEquals(10000L, accAfterDelete.balancePaise) // Still 10000!

        // 4. Update tx2 to isExcludedFromCalculations = false -> balance should now increase by 5000 to 15000
        val savedTx2 = env.transactionsDao.getById(id2)!!
        repo.updateTransaction(savedTx2.copy(isExcludedFromCalculations = false))
        val accAfterUpdate = env.accountsDao.getById(accId)!!
        assertEquals(15000L, accAfterUpdate.balancePaise) // Now applied!
    }

    // =========================================================================
    // 4. CHART ANIMATION STATE TEST
    // =========================================================================

    @Test
    fun testChartAnimationStateExecutesOnlyOncePerSession() {
        ChartAnimationState.resetForTesting()
        assertTrue(ChartAnimationState.shouldAnimate("test_chart_1"))
        // Second call must return false (no re-animation on returning to screen)
        assertFalse(ChartAnimationState.shouldAnimate("test_chart_1"))
        assertFalse(ChartAnimationState.shouldAnimate("test_chart_1"))

        // Independent chart id animates once
        assertTrue(ChartAnimationState.shouldAnimate("test_chart_2"))
        assertFalse(ChartAnimationState.shouldAnimate("test_chart_2"))

        // After reset, can animate again
        ChartAnimationState.resetForTesting()
        assertTrue(ChartAnimationState.shouldAnimate("test_chart_1"))
    }

    // =========================================================================
    // 5. IN-MEMORY REPOSITORY ACCOUNTING TESTS (OWED, LOAN, SAVINGS INTEREST)
    // =========================================================================

    @Test
    fun testFullRepaymentOfOwedEntry() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository

        // Initial setup: Account has 5,000 paise
        val accountId = env.accountsDao.insert(AccountEntity(id = 1L, name = "Savings Account", balancePaise = 5000L))

        // Person owes me 1,500 paise
        val owedId = env.owedDao.insert(
            OwedEntity(
                id = 1L,
                personName = "Kartik",
                amountPaise = 1500L,
                direction = OwedDirection.OWED_TO_ME,
                note = "Dinner split",
                sourceTransactionId = 100L,
                originalAmountPaise = 1500L,
                repaidAmountPaise = 0L
            )
        )

        // Settle full repayment of 1,500 paise into accountId
        val txId = repo.settleOwed(owedId, 1500L, accountId, LocalDate.of(2026, 10, 15).toEpochDay(), "Full repayment")
        assertTrue(txId > 0)

        // 1. Account must be credited by 1,500 paise (5000 + 1500 = 6500)
        val updatedAccount = env.accountsDao.getById(accountId)!!
        assertEquals(6500L, updatedAccount.balancePaise)

        // 2. Owed record must be updated: remaining = 0, repaid = 1500
        val updatedOwed = env.owedDao.getById(owedId)!!
        assertEquals(0L, updatedOwed.amountPaise)
        assertEquals(1500L, updatedOwed.repaidAmountPaise)
        assertEquals(1500L, updatedOwed.originalAmountPaise)

        // 3. Linked transaction must exist with originalExpenseId = 100, isExcludedFromReports = true
        val tx = env.transactionsDao.getById(txId)!!
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals(1500L, tx.amountPaise)
        assertEquals(100L, tx.originalExpenseId)
        assertTrue(tx.isExcludedFromReports)
    }

    @Test
    fun testPartialRepaymentOfOwedEntry() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository

        val accountId = env.accountsDao.insert(AccountEntity(id = 1L, name = "HDFC Bank", balancePaise = 10000L))
        val owedId = env.owedDao.insert(
            OwedEntity(
                id = 1L,
                personName = "Yash",
                amountPaise = 2000L,
                direction = OwedDirection.OWED_TO_ME,
                note = "Hotel stay",
                sourceTransactionId = 200L,
                originalAmountPaise = 2000L,
                repaidAmountPaise = 0L
            )
        )

        // First partial repayment: 800 paise
        val tx1 = repo.settleOwed(owedId, 800L, accountId, LocalDate.of(2026, 10, 10).toEpochDay())
        assertTrue(tx1 > 0)

        var account = env.accountsDao.getById(accountId)!!
        assertEquals(10800L, account.balancePaise)

        var owed = env.owedDao.getById(owedId)!!
        assertEquals(1200L, owed.amountPaise)
        assertEquals(800L, owed.repaidAmountPaise)

        // Second partial repayment: 1200 paise (remaining)
        val tx2 = repo.settleOwed(owedId, 1200L, accountId, LocalDate.of(2026, 10, 15).toEpochDay())
        assertTrue(tx2 > 0)

        account = env.accountsDao.getById(accountId)!!
        assertEquals(12000L, account.balancePaise)

        owed = env.owedDao.getById(owedId)!!
        assertEquals(0L, owed.amountPaise)
        assertEquals(2000L, owed.repaidAmountPaise)

        // Attempting to repay more than outstanding must fail
        try {
            repo.settleOwed(owedId, 100L, accountId)
            fail("Expected exception when settling more than outstanding")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("cannot exceed outstanding"))
        }
    }

    @Test
    fun testSettlementWhenIOweSomeone() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository

        // I have 5,000 paise in bank
        val accountId = env.accountsDao.insert(AccountEntity(id = 1L, name = "SBI", balancePaise = 5000L))

        // I owe Sanket 1,000 paise
        val owedId = env.owedDao.insert(
            OwedEntity(
                id = 1L,
                personName = "Sanket",
                amountPaise = 1000L,
                direction = OwedDirection.I_OWE,
                note = "Cab fare"
            )
        )

        // I repay Sanket 1,000 paise from SBI
        val txId = repo.settleOwed(owedId, 1000L, accountId, LocalDate.of(2026, 10, 20).toEpochDay())
        assertTrue(txId > 0)

        // Account must be debited: 5000 - 1000 = 4000
        val account = env.accountsDao.getById(accountId)!!
        assertEquals(4000L, account.balancePaise)

        // Transaction must be recorded as EXPENSE and excluded from reports
        val tx = env.transactionsDao.getById(txId)!!
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(1000L, tx.amountPaise)
        assertTrue(tx.isExcludedFromReports)
    }

    @Test
    fun testLoanRepaymentAccounting() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository

        val accountId = env.accountsDao.insert(AccountEntity(id = 1L, name = "Salary Account", balancePaise = 50000L))
        val cardId = env.accountsDao.insert(AccountEntity(id = 2L, name = "Credit Card", balancePaise = 0L, isCreditCard = true))

        val loanId = env.loansDao.insert(
            LoanEntity(
                id = 1L,
                name = "Home Loan",
                originalPrincipalPaise = 100000L,
                currentOutstandingPaise = 80000L,
                interestRateBps = 850,
                emiPaise = 15000L,
                nextDueEpochDay = LocalDate.now().plusMonths(1).toEpochDay(),
                remainingTenureMonths = 24,
                principalPaidPaise = 20000L,
                interestPaidPaise = 0L,
                includeInNetWorth = true
            )
        )

        // Cannot repay loan from credit card
        try {
            repo.repayLoan(loanId, cardId, 15000L)
            fail("Expected exception when repaying loan from credit card")
        } catch (e: Exception) {
            assertTrue(e.message!!.contains("cannot be paid from a credit card"))
        }

        // Repay from Salary Account
        val txId = repo.repayLoan(loanId, accountId, 15000L)
        assertTrue(txId > 0)

        // Account debited
        val account = env.accountsDao.getById(accountId)!!
        assertEquals(35000L, account.balancePaise)

        // Loan outstanding reduced and principal paid increased
        val loan = env.loansDao.getById(loanId)!!
        assertEquals(65000L, loan.currentOutstandingPaise)
        assertEquals(35000L, loan.principalPaidPaise)

        // Linked transaction created, excluded from reports
        val tx = env.transactionsDao.getById(txId)!!
        assertEquals(15000L, tx.amountPaise)
        assertEquals(loanId, tx.originalExpenseId)
        assertTrue(tx.isLoanRepayment)
        assertTrue(tx.isExcludedFromReports)
    }

    @Test
    fun testDeletingLoanRepaymentReversesAccountAndLoanBalances() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository
        val accountId = env.accountsDao.insert(AccountEntity(id = 1L, name = "Salary Account", balancePaise = 50000L))
        val loanId = env.loansDao.insert(
            LoanEntity(
                id = 1L,
                name = "Home Loan",
                originalPrincipalPaise = 100000L,
                currentOutstandingPaise = 80000L,
                interestRateBps = 850,
                emiPaise = 15000L,
                nextDueEpochDay = LocalDate.now().plusMonths(1).toEpochDay(),
                remainingTenureMonths = 24,
                principalPaidPaise = 20000L,
                interestPaidPaise = 0L,
                includeInNetWorth = true
            )
        )

        val txId = repo.repayLoan(loanId, accountId, 15000L)
        repo.deleteTransaction(txId)

        assertEquals(50000L, env.accountsDao.getById(accountId)!!.balancePaise)
        assertEquals(80000L, env.loansDao.getById(loanId)!!.currentOutstandingPaise)
        assertEquals(20000L, env.loansDao.getById(loanId)!!.principalPaidPaise)
        assertNull(env.transactionsDao.getById(txId))
    }

    @Test
    fun testEditingLoanRepaymentKeepsAccountAndLoanBalancesLinked() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository
        val accountId = env.accountsDao.insert(AccountEntity(id = 1L, name = "Salary Account", balancePaise = 50000L))
        val loanId = env.loansDao.insert(
            LoanEntity(
                id = 1L,
                name = "Home Loan",
                originalPrincipalPaise = 100000L,
                currentOutstandingPaise = 80000L,
                interestRateBps = 850,
                emiPaise = 15000L,
                nextDueEpochDay = LocalDate.now().plusMonths(1).toEpochDay(),
                remainingTenureMonths = 24,
                principalPaidPaise = 20000L,
                interestPaidPaise = 0L,
                includeInNetWorth = true
            )
        )
        val txId = repo.repayLoan(loanId, accountId, 15000L)

        repo.updateTransaction(env.transactionsDao.getById(txId)!!.copy(amountPaise = 10000L))

        assertEquals(40000L, env.accountsDao.getById(accountId)!!.balancePaise)
        assertEquals(70000L, env.loansDao.getById(loanId)!!.currentOutstandingPaise)
        assertEquals(30000L, env.loansDao.getById(loanId)!!.principalPaidPaise)
        assertTrue(env.transactionsDao.getById(txId)!!.isLoanRepayment)

        repo.deleteTransaction(txId)

        assertEquals(50000L, env.accountsDao.getById(accountId)!!.balancePaise)
        assertEquals(80000L, env.loansDao.getById(loanId)!!.currentOutstandingPaise)
        assertEquals(20000L, env.loansDao.getById(loanId)!!.principalPaidPaise)
    }

    @Test
    fun testPostSavingsInterestAccounting() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository

        val accountId = env.accountsDao.insert(AccountEntity(id = 1L, name = "Kotak 811", balancePaise = 10000L, interestRateBps = 350, interestCreditFrequency = InterestCreditFrequency.DAILY.name))
        val date = LocalDate.of(2026, 10, 31).toEpochDay()

        val txId = repo.postSavingsInterest(accountId, 350L, date)
        assertTrue(txId > 0)

        // Account credited: 10000 + 350 = 10350
        val account = env.accountsDao.getById(accountId)!!
        assertEquals(10350L, account.balancePaise)
        assertEquals(date, account.lastInterestPostingEpochDay)

        // Transaction description matches convention "Interest for {{date}}"
        val tx = env.transactionsDao.getById(txId)!!
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals(350L, tx.amountPaise)
        assertEquals("Interest for 31-Oct-2026", tx.description)
        assertEquals(accountId, tx.accountId)
        assertEquals("Interest", env.categoriesDao.getById(tx.categoryId)!!.name)
    }

    @Test
    fun testAutomaticDailyInterestUsesAccrualDateAndActualPostingDate() = runBlocking {
        val env = createTestEnvironment()
        val accountId = env.accountsDao.insert(
            AccountEntity(
                id = 1L,
                name = "Daily account",
                balancePaise = 1_000_000L,
                interestRateBps = 365,
                interestCreditFrequency = InterestCreditFrequency.DAILY.name,
                lastInterestPostingEpochDay = LocalDate.of(2026, 10, 30).toEpochDay()
            )
        )
        val postingDate = LocalDate.of(2026, 11, 1)

        assertEquals(1, env.repository.processDueSavingsInterest(postingDate))

        val tx = env.transactionsDao.observeAll().first().single()
        assertEquals("Interest for 31-Oct-2026", tx.description)
        assertEquals(postingDate.toEpochDay(), tx.dateEpochDay)
        assertEquals(accountId, tx.accountId)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Interest", env.categoriesDao.getById(tx.categoryId)!!.name)
        assertEquals(1_000_100L, env.accountsDao.getById(accountId)!!.balancePaise)
    }

    @Test
    fun testAutomaticMonthlyInterestUsesFullPeriodAcrossAccountsAndPreventsRetryDuplicates() = runBlocking {
        val env = createTestEnvironment()
        val postingDate = LocalDate.of(2026, 11, 1)
        val periodEnd = LocalDate.of(2026, 10, 31).toEpochDay()
        val firstAccountId = env.accountsDao.insert(
            AccountEntity(
                id = 1L,
                name = "First account",
                balancePaise = 1_000_000L,
                interestRateBps = 365,
                interestCreditFrequency = InterestCreditFrequency.MONTHLY.name,
                lastInterestPostingEpochDay = LocalDate.of(2026, 9, 30).toEpochDay()
            )
        )
        val secondAccountId = env.accountsDao.insert(
            AccountEntity(
                id = 2L,
                name = "Second account",
                balancePaise = 2_000_000L,
                interestRateBps = 365,
                interestCreditFrequency = InterestCreditFrequency.MONTHLY.name,
                lastInterestPostingEpochDay = LocalDate.of(2026, 9, 30).toEpochDay()
            )
        )

        assertEquals(2, env.repository.processDueSavingsInterest(postingDate))

        val firstTx = env.transactionsDao.observeAll().first().single { it.accountId == firstAccountId }
        val secondTx = env.transactionsDao.observeAll().first().single { it.accountId == secondAccountId }
        assertEquals("Interest for 01-Oct-2026 - 31-Oct-2026", firstTx.description)
        assertEquals(firstTx.description, secondTx.description)
        assertEquals(postingDate.toEpochDay(), firstTx.dateEpochDay)
        assertEquals(postingDate.toEpochDay(), secondTx.dateEpochDay)
        assertEquals(TransactionType.INCOME, firstTx.type)
        assertEquals(TransactionType.INCOME, secondTx.type)
        assertEquals("Interest", env.categoriesDao.getById(firstTx.categoryId)!!.name)
        assertEquals(firstTx.categoryId, secondTx.categoryId)
        assertEquals(1_000_000L + firstTx.amountPaise, env.accountsDao.getById(firstAccountId)!!.balancePaise)
        assertEquals(2_000_000L + secondTx.amountPaise, env.accountsDao.getById(secondAccountId)!!.balancePaise)

        // Simulate a retry with a stale cursor and a fresh repository after application restart.
        env.accountsDao.update(env.accountsDao.getById(firstAccountId)!!.copy(lastInterestPostingEpochDay = LocalDate.of(2026, 9, 30).toEpochDay()))
        val restartedRepository = FinanceRepository(env.database)
        assertEquals(0, restartedRepository.processDueSavingsInterest(postingDate))
        assertEquals(1, env.transactionsDao.observeAll().first().count { it.accountId == firstAccountId })
        assertEquals(periodEnd, env.accountsDao.getById(firstAccountId)!!.lastInterestPostingEpochDay)
        assertEquals(1_000_000L + firstTx.amountPaise, env.accountsDao.getById(firstAccountId)!!.balancePaise)
    }

    @Test
    fun testAutomaticQuarterlyInterestUsesCalendarQuarterAndPostingDate() = runBlocking {
        val env = createTestEnvironment()
        val accountId = env.accountsDao.insert(
            AccountEntity(
                id = 1L,
                name = "Quarterly account",
                balancePaise = 1_000_000L,
                interestRateBps = 365,
                interestCreditFrequency = InterestCreditFrequency.QUARTERLY.name,
                lastInterestPostingEpochDay = LocalDate.of(2026, 9, 30).toEpochDay()
            )
        )
        val postingDate = LocalDate.of(2027, 1, 1)

        assertEquals(1, env.repository.processDueSavingsInterest(postingDate))

        val tx = env.transactionsDao.observeAll().first().single()
        assertEquals("Interest for 01-Oct-2026 - 31-Dec-2026", tx.description)
        assertEquals(postingDate.toEpochDay(), tx.dateEpochDay)
        assertEquals(accountId, tx.accountId)
        assertEquals(TransactionType.INCOME, tx.type)
        assertEquals("Interest", env.categoriesDao.getById(tx.categoryId)!!.name)
        assertEquals(1_000_000L + tx.amountPaise, env.accountsDao.getById(accountId)!!.balancePaise)
    }

    @Test
    fun testCategorySafeDisableOnDelete() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository

        val catId = env.categoriesDao.insert(CategoryEntity(id = 1L, name = "Utilities", isIncomeCategory = false))
        val category = env.categoriesDao.getById(catId)!!

        // If category is used by transaction, deleteCategory should mark isDisabled = true rather than deleting
        env.transactionsDao.insert(TransactionEntity(id = 1L, dateEpochDay = 100L, description = "Power bill", amountPaise = 1000L, categoryId = catId, accountId = 1L, type = TransactionType.EXPENSE))

        val deleted = repo.deleteCategory(category)
        assertTrue(deleted)

        val updatedCategory = env.categoriesDao.getById(catId)!!
        assertTrue(updatedCategory.isDisabled)
    }

    @Test
    fun testCreateRecurringScheduleFromExistingTransaction() = runBlocking {
        val env = createTestEnvironment()
        val repo = env.repository

        val accountId = env.accountsDao.insert(AccountEntity(id = 1L, name = "HDFC", balancePaise = 50000L))
        val catId = env.categoriesDao.insert(CategoryEntity(id = 1L, name = "Subscriptions", isIncomeCategory = false))

        // Existing transaction in the transaction log
        val txId = env.transactionsDao.insert(
            TransactionEntity(
                id = 10L,
                dateEpochDay = 20000L,
                description = "Netflix",
                amountPaise = 64900L,
                categoryId = catId,
                accountId = accountId,
                type = TransactionType.EXPENSE
            )
        )

        // Make recurring schedule from existing transaction
        val existingTx = env.transactionsDao.getById(txId)!!
        val recurringSchedule = RecurringTransactionEntity(
            description = existingTx.description,
            amountPaise = existingTx.amountPaise,
            categoryId = existingTx.categoryId,
            accountId = existingTx.accountId,
            type = existingTx.type,
            frequency = RecurrenceFrequency.MONTHLY,
            startDateEpochDay = existingTx.dateEpochDay,
            nextOccurrenceEpochDay = existingTx.dateEpochDay + 30,
            isEnabled = true
        )
        repo.addRecurringTransaction(recurringSchedule)

        val schedules = env.recurringTransactionsDao.observeAll().first()
        assertEquals(1, schedules.size)
        assertEquals("Netflix", schedules[0].description)
        assertEquals(64900L, schedules[0].amountPaise)
        assertEquals(RecurrenceFrequency.MONTHLY, schedules[0].frequency)
        assertTrue(schedules[0].isEnabled)
    }

    @Test
    fun testBackupAndRestoreSupportsNewFields() = runBlocking {
        val env = createTestEnvironment()
        val backupRepo = BackupRepository(env.database)

        // Insert records with new fields
        val accountId = env.accountsDao.insert(
            AccountEntity(
                id = 1L,
                name = "ICICI",
                balancePaise = 25000L,
                interestRateBps = 400,
                interestCalculationMethod = "DAILY",
                interestCreditFrequency = "QUARTERLY",
                lastInterestPostingEpochDay = 20000L
            )
        )
        val catId = env.categoriesDao.insert(CategoryEntity(id = 1L, name = "Groceries", isIncomeCategory = false, isDisabled = true, colorHex = "#FF5722"))
        val txId = env.transactionsDao.insert(
            TransactionEntity(
                id = 1L,
                dateEpochDay = 20000L,
                description = "Dinner with team",
                amountPaise = 6000L,
                categoryId = catId,
                accountId = accountId,
                type = TransactionType.EXPENSE,
                isExcludedFromCalculations = true,
                originalExpenseId = 999L,
                isLoanRepayment = true,
                splitDetails = """{"Kartik":3000,"Yash":3000}"""
            )
        )
        val owedId = env.owedDao.insert(
            OwedEntity(
                id = 1L,
                personName = "Aryan",
                amountPaise = 1000L,
                direction = OwedDirection.OWED_TO_ME,
                originalAmountPaise = 2500L,
                repaidAmountPaise = 1500L
            )
        )

        // Export backup
        val jsonString = backupRepo.export()
        assertNotNull(jsonString)

        // Clear in-memory tables
        env.accountsDao.clear()
        env.categoriesDao.clear()
        env.transactionsDao.clear()
        env.owedDao.clear()

        // Restore backup
        backupRepo.restore(jsonString)

        // Verify restored records retain new fields
        val restoredAccount = env.accountsDao.getById(accountId)!!
        assertEquals(400, restoredAccount.interestRateBps)
        assertEquals("DAILY", restoredAccount.interestCalculationMethod)
        assertEquals("QUARTERLY", restoredAccount.interestCreditFrequency)
        assertEquals(20000L, restoredAccount.lastInterestPostingEpochDay)

        val restoredCategory = env.categoriesDao.getById(catId)!!
        assertTrue(restoredCategory.isDisabled)
        assertEquals("#FF5722", restoredCategory.colorHex)

        val restoredTx = env.transactionsDao.getById(txId)!!
        assertTrue(restoredTx.isExcludedFromReports)
        assertEquals(999L, restoredTx.originalExpenseId)
        assertTrue(restoredTx.isLoanRepayment)
        assertEquals("""{"Kartik":3000,"Yash":3000}""", restoredTx.splitDetails)

        val restoredOwed = env.owedDao.getById(owedId)!!
        assertEquals(2500L, restoredOwed.originalAmountPaise)
        assertEquals(1500L, restoredOwed.repaidAmountPaise)
    }

    // =========================================================================
    // IN-MEMORY TEST ENVIRONMENT SETUP
    // =========================================================================

    private class TestEnvironment(
        val accountsDao: InMemoryAccountDao,
        val transactionsDao: InMemoryTransactionDao,
        val categoriesDao: InMemoryCategoryDao,
        val owedDao: InMemoryOwedDao,
        val loansDao: InMemoryLoanDao,
        val budgetsDao: InMemoryBudgetDao,
        val fixedDepositsDao: InMemoryFixedDepositDao,
        val recurringTransactionsDao: InMemoryRecurringTransactionDao,
        val cardPaymentsDao: InMemoryCardPaymentDao,
        val database: SpendTrackDatabase,
        val repository: FinanceRepository
    )

    private fun createTestEnvironment(): TestEnvironment {
        val accountsList = mutableListOf<AccountEntity>()
        val transactionsList = mutableListOf<TransactionEntity>()
        val categoriesList = mutableListOf<CategoryEntity>()
        val owedList = mutableListOf<OwedEntity>()
        val loansList = mutableListOf<LoanEntity>()
        val budgetsList = mutableListOf<BudgetEntity>()
        val fixedDepositsList = mutableListOf<FixedDepositEntity>()
        val recurringTransactionsList = mutableListOf<RecurringTransactionEntity>()
        val recurringOccurrencesList = mutableListOf<RecurringOccurrenceEntity>()
        val cardPaymentsList = mutableListOf<CardPaymentEntity>()

        val accountsDao = InMemoryAccountDao(accountsList, transactionsList, fixedDepositsList, recurringTransactionsList)
        val transactionsDao = InMemoryTransactionDao(transactionsList)
        val categoriesDao = InMemoryCategoryDao(categoriesList, transactionsList, budgetsList, recurringTransactionsList)
        val owedDao = InMemoryOwedDao(owedList)
        val loansDao = InMemoryLoanDao(loansList)
        val budgetsDao = InMemoryBudgetDao(budgetsList)
        val fixedDepositsDao = InMemoryFixedDepositDao(fixedDepositsList)
        val recurringDao = InMemoryRecurringTransactionDao(recurringTransactionsList)
        val recurringOccurrencesDao = InMemoryRecurringOccurrenceDao(recurringOccurrencesList)
        val cardPaymentsDao = InMemoryCardPaymentDao(cardPaymentsList)
        val peopleDao = InMemoryPersonDao()

        val dummyDb = java.lang.reflect.Proxy.newProxyInstance(
            androidx.sqlite.db.SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "inTransaction" -> true
                "isOpen" -> true
                "isWriteAheadLoggingEnabled" -> false
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        } as androidx.sqlite.db.SupportSQLiteDatabase

        val dummyHelper = java.lang.reflect.Proxy.newProxyInstance(
            SupportSQLiteOpenHelper::class.java.classLoader,
            arrayOf(SupportSQLiteOpenHelper::class.java)
        ) { _, method, _ ->
            if (method.name == "getWritableDatabase" || method.name == "getReadableDatabase") dummyDb
            else null
        } as SupportSQLiteOpenHelper

        val mockDb = object : SpendTrackDatabase() {
            override val openHelper: SupportSQLiteOpenHelper = dummyHelper

            init {
                val directExecutor = java.util.concurrent.Executor { it.run() }
                androidx.room.RoomDatabase::class.java.declaredFields.forEach { f ->
                    f.isAccessible = true
                    when (f.name) {
                        "mAllowMainThreadQueries", "allowMainThreadQueries" -> runCatching { f.set(this, true) }
                        "mOpenHelper", "openHelper" -> runCatching { f.set(this, dummyHelper) }
                        "internalTransactionExecutor", "mTransactionExecutor", "transactionExecutor" -> runCatching { f.set(this, directExecutor) }
                        "internalQueryExecutor", "mQueryExecutor", "queryExecutor" -> runCatching { f.set(this, directExecutor) }
                    }
                }
            }

            override fun accounts(): AccountDao = accountsDao
            override fun transactions(): TransactionDao = transactionsDao
            override fun categories(): CategoryDao = categoriesDao
            override fun budgets(): BudgetDao = budgetsDao
            override fun loans(): LoanDao = loansDao
            override fun owed(): OwedDao = owedDao
            override fun cardPayments(): CardPaymentDao = cardPaymentsDao
            override fun fixedDeposits(): FixedDepositDao = fixedDepositsDao
            override fun recurringTransactions(): RecurringTransactionDao = recurringDao
            override fun recurringOccurrences(): RecurringOccurrenceDao = recurringOccurrencesDao
            override fun importHistory(): ImportHistoryDao = error("Not needed")
            override fun people(): PersonDao = peopleDao

            override fun inTransaction(): Boolean = true
            override val isOpen: Boolean = true

            override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
                return dummyHelper
            }

            override fun createInvalidationTracker(): InvalidationTracker {
                return InvalidationTracker(this, emptyMap(), emptyMap(), "accounts")
            }

            override fun clearAllTables() {}
        }

        val repository = FinanceRepository(mockDb)

        return TestEnvironment(
            accountsDao,
            transactionsDao,
            categoriesDao,
            owedDao,
            loansDao,
            budgetsDao,
            fixedDepositsDao,
            recurringDao,
            cardPaymentsDao,
            mockDb,
            repository
        )
    }

    private class InMemoryCardPaymentDao(private val list: MutableList<CardPaymentEntity>) : CardPaymentDao {
        override fun observeAll(): Flow<List<CardPaymentEntity>> = flowOf(list.toList())
        override suspend fun insert(entity: CardPaymentEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<CardPaymentEntity>) { list.addAll(entities) }
        override suspend fun clear() { list.clear() }
    }

    private class InMemoryAccountDao(
        private val list: MutableList<AccountEntity>,
        private val transactionsList: List<TransactionEntity>,
        private val fdsList: List<FixedDepositEntity>,
        private val recurringList: List<RecurringTransactionEntity>
    ) : AccountDao {
        override fun observeAll(): Flow<List<AccountEntity>> = flowOf(list.toList())
        override suspend fun insert(entity: AccountEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<AccountEntity>) { list.addAll(entities) }
        override suspend fun update(entity: AccountEntity) {
            val idx = list.indexOfFirst { it.id == entity.id }
            if (idx >= 0) list[idx] = entity
        }
        override suspend fun delete(entity: AccountEntity) { list.removeAll { it.id == entity.id } }
        override suspend fun clear() { list.clear() }
        override suspend fun getById(id: Long): AccountEntity? = list.firstOrNull { it.id == id }
        override suspend fun transactionCount(accountId: Long): Int = transactionsList.count { it.accountId == accountId || it.destinationAccountId == accountId }
        override suspend fun recurringCount(accountId: Long): Int = recurringList.count { it.accountId == accountId }
    }

    private class InMemoryTransactionDao(private val list: MutableList<TransactionEntity>) : TransactionDao {
        override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(list.toList())
        override suspend fun insert(entity: TransactionEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<TransactionEntity>) { list.addAll(entities) }
        override suspend fun update(entity: TransactionEntity) {
            val idx = list.indexOfFirst { it.id == entity.id }
            if (idx >= 0) list[idx] = entity
        }
        override suspend fun delete(entity: TransactionEntity) { list.removeAll { it.id == entity.id } }
        override suspend fun clear() { list.clear() }
        override suspend fun getById(id: Long): TransactionEntity? = list.firstOrNull { it.id == id }
        override suspend fun getByOriginalExpenseId(originalExpenseId: Long): List<TransactionEntity> =
            list.filter { it.originalExpenseId == originalExpenseId }
        override fun observeDateRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TransactionEntity>> =
            flowOf(list.filter { it.dateEpochDay in startEpochDay..endEpochDay })
        override suspend fun getDateRange(startEpochDay: Long, endEpochDay: Long): List<TransactionEntity> =
            list.filter { it.dateEpochDay in startEpochDay..endEpochDay }
        override fun observeRecent(limit: Int): Flow<List<TransactionEntity>> =
            flowOf(list.sortedByDescending { it.dateEpochDay }.take(limit))
    }

    private class InMemoryCategoryDao(
        private val list: MutableList<CategoryEntity>,
        private val txList: List<TransactionEntity>,
        private val budgetList: List<BudgetEntity>,
        private val recurringList: List<RecurringTransactionEntity>
    ) : CategoryDao {
        override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(list.toList())
        override suspend fun count(): Int = list.size
        override suspend fun insert(entity: CategoryEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<CategoryEntity>) { list.addAll(entities) }
        override suspend fun update(entity: CategoryEntity) {
            val idx = list.indexOfFirst { it.id == entity.id }
            if (idx >= 0) list[idx] = entity
        }
        override suspend fun delete(entity: CategoryEntity) { list.removeAll { it.id == entity.id } }
        override suspend fun clear() { list.clear() }
        override suspend fun transactionCount(categoryId: Long): Int = txList.count { it.categoryId == categoryId }
        override suspend fun budgetCount(categoryId: Long): Int = budgetList.count { it.categoryId == categoryId }
        override suspend fun recurringCount(categoryId: Long): Int = recurringList.count { it.categoryId == categoryId }
        override suspend fun getById(id: Long): CategoryEntity? = list.firstOrNull { it.id == id }
        override suspend fun getByName(name: String): CategoryEntity? = list.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    private class InMemoryOwedDao(private val list: MutableList<OwedEntity>) : OwedDao {
        override fun observeAll(): Flow<List<OwedEntity>> = flowOf(list.toList())
        override suspend fun insert(entity: OwedEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<OwedEntity>) { list.addAll(entities) }
        override suspend fun update(entity: OwedEntity) {
            val idx = list.indexOfFirst { it.id == entity.id }
            if (idx >= 0) list[idx] = entity
        }
        override suspend fun delete(entity: OwedEntity) { list.removeAll { it.id == entity.id } }
        override suspend fun clear() { list.clear() }
        override suspend fun deleteForTransaction(transactionId: Long) {
            list.removeAll { it.sourceTransactionId == transactionId }
        }
        override suspend fun getById(id: Long): OwedEntity? = list.firstOrNull { it.id == id }
        override suspend fun getBySourceTransactionId(transactionId: Long): List<OwedEntity> =
            list.filter { it.sourceTransactionId == transactionId }
        override suspend fun getByPerson(personName: String): List<OwedEntity> =
            list.filter { it.personName.equals(personName, ignoreCase = true) }
    }

    private class InMemoryLoanDao(private val list: MutableList<LoanEntity>) : LoanDao {
        override fun observeAll(): Flow<List<LoanEntity>> = flowOf(list.toList())
        override suspend fun insert(entity: LoanEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<LoanEntity>) { list.addAll(entities) }
        override suspend fun update(entity: LoanEntity) {
            val idx = list.indexOfFirst { it.id == entity.id }
            if (idx >= 0) list[idx] = entity
        }
        override suspend fun delete(entity: LoanEntity) { list.removeAll { it.id == entity.id } }
        override suspend fun clear() { list.clear() }
        override suspend fun getById(id: Long): LoanEntity? = list.firstOrNull { it.id == id }
    }

    private class InMemoryBudgetDao(private val list: MutableList<BudgetEntity>) : BudgetDao {
        override fun observeAll(): Flow<List<BudgetEntity>> = flowOf(list.toList())
        override suspend fun forMonth(monthKey: String): List<BudgetEntity> = list.filter { it.monthKey == monthKey }
        override suspend fun latestMonthBefore(monthKey: String): String? = null
        override suspend fun find(categoryId: Long, monthKey: String): BudgetEntity? =
            list.firstOrNull { it.categoryId == categoryId && it.monthKey == monthKey }
        override suspend fun insert(entity: BudgetEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<BudgetEntity>) { list.addAll(entities) }
        override suspend fun update(entity: BudgetEntity) {
            val idx = list.indexOfFirst { it.id == entity.id }
            if (idx >= 0) list[idx] = entity
        }
        override suspend fun delete(entity: BudgetEntity) { list.removeAll { it.id == entity.id } }
        override suspend fun clear() { list.clear() }
    }

    private class InMemoryFixedDepositDao(private val list: MutableList<FixedDepositEntity>) : FixedDepositDao {
        override fun observeAll(): Flow<List<FixedDepositEntity>> = flowOf(list.toList())
        override suspend fun getById(id: Long): FixedDepositEntity? = list.firstOrNull { it.id == id }
        override suspend fun getDueActiveDeposits(todayEpochDay: Long): List<FixedDepositEntity> =
            list.filter { it.status == FixedDepositStatus.ACTIVE && it.maturityDateEpochDay <= todayEpochDay }
        override suspend fun insert(entity: FixedDepositEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<FixedDepositEntity>) { list.addAll(entities) }
        override suspend fun update(entity: FixedDepositEntity) {
            val idx = list.indexOfFirst { it.id == entity.id }
            if (idx >= 0) list[idx] = entity
        }
        override suspend fun delete(entity: FixedDepositEntity) { list.removeAll { it.id == entity.id } }
        override suspend fun clear() { list.clear() }
        override suspend fun activeCountForAccount(accountId: Long): Int =
            list.count { it.linkedAccountId == accountId && it.status == FixedDepositStatus.ACTIVE }
    }

    private class InMemoryRecurringTransactionDao(private val list: MutableList<RecurringTransactionEntity>) : RecurringTransactionDao {
        override fun observeAll(): Flow<List<RecurringTransactionEntity>> = flowOf(list.toList())
        override suspend fun getById(id: Long): RecurringTransactionEntity? = list.firstOrNull { it.id == id }
        override suspend fun getDueSchedules(todayEpochDay: Long): List<RecurringTransactionEntity> =
            list.filter { it.isEnabled && it.nextOccurrenceEpochDay <= todayEpochDay }
        override suspend fun activeCount(): Int = list.count { it.isEnabled }
        override suspend fun insert(entity: RecurringTransactionEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<RecurringTransactionEntity>) { list.addAll(entities) }
        override suspend fun update(entity: RecurringTransactionEntity) {
            val idx = list.indexOfFirst { it.id == entity.id }
            if (idx >= 0) list[idx] = entity
        }
        override suspend fun delete(entity: RecurringTransactionEntity) { list.removeAll { it.id == entity.id } }
        override suspend fun clear() { list.clear() }
    }

    private class InMemoryRecurringOccurrenceDao(private val list: MutableList<RecurringOccurrenceEntity>) : RecurringOccurrenceDao {
        override suspend fun hasOccurrence(scheduleId: Long, occurrenceEpochDay: Long): Boolean =
            list.any { it.scheduleId == scheduleId && it.occurrenceEpochDay == occurrenceEpochDay }
        override suspend fun insert(entity: RecurringOccurrenceEntity): Long {
            val id = if (entity.id == 0L) (list.size + 1).toLong() else entity.id
            list.add(entity.copy(id = id))
            return id
        }
        override suspend fun insertAll(entities: List<RecurringOccurrenceEntity>) { list.addAll(entities) }
        override suspend fun forSchedule(scheduleId: Long): List<RecurringOccurrenceEntity> =
            list.filter { it.scheduleId == scheduleId }
        override suspend fun getAll(): List<RecurringOccurrenceEntity> = list.toList()
        override suspend fun deleteForSchedule(scheduleId: Long) {
            list.removeAll { it.scheduleId == scheduleId }
        }
        override suspend fun clear() { list.clear() }
    }
}
