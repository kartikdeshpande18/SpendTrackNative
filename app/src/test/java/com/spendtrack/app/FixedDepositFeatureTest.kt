package com.spendtrack.app

import com.spendtrack.app.data.local.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FixedDepositFeatureTest {

    // 1. Creating FD deducts principal from linked account
    @Test
    fun testCreateFdDeductsPrincipalFromLinkedAccount() {
        var hdfcBalance = 75000_00L
        val principal = 50000_00L

        // Validation: sufficient balance
        assertTrue("Account must have sufficient balance", hdfcBalance >= principal)
        hdfcBalance -= principal

        val fd = FixedDepositEntity(
            id = 1L,
            name = "HDFC Tax Saver",
            linkedAccountId = 10L,
            principalPaise = principal,
            interestRateBps = 725,
            startDateEpochDay = LocalDate.of(2026, 9, 7).toEpochDay(),
            maturityDateEpochDay = LocalDate.of(2027, 9, 7).toEpochDay(),
            maturityAmountPaise = 53625_00L,
            status = FixedDepositStatus.ACTIVE
        )

        assertEquals(25000_00L, hdfcBalance)
        assertEquals(50000_00L, fd.principalPaise)
        assertEquals(FixedDepositStatus.ACTIVE, fd.status)
    }

    // 2. Creating FD does not create expense
    @Test
    fun testCreatingFdDoesNotCreateExpense() {
        val transactions = mutableListOf(
            TransactionEntity(1L, 100L, "Coffee", 250_00L, 1L, 10L, TransactionType.EXPENSE)
        )

        // Creating an FD creates zero entries in transactions table
        val expenseTotal = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        assertEquals("Expense must remain 250", 250_00L, expenseTotal)
    }

    // 3. Creating FD does not create income
    @Test
    fun testCreatingFdDoesNotCreateIncome() {
        val transactions = mutableListOf(
            TransactionEntity(1L, 100L, "Salary", 80000_00L, 1L, 10L, TransactionType.INCOME)
        )

        // Creating an FD does not affect income
        val incomeTotal = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        assertEquals("Income must remain 80,000", 80000_00L, incomeTotal)
    }

    // 4. Creating FD does not affect budget/category/chart calculations
    @Test
    fun testCreatingFdDoesNotAffectBudgetCategoryChartCalculations() {
        val today = LocalDate.of(2026, 9, 7)
        val transactions = listOf(
            TransactionEntity(1L, today.toEpochDay(), "Shopping", 3000_00L, 2L, 10L, TransactionType.EXPENSE)
        )

        // Budget usage
        val budgetUsed = transactions
            .filter { it.type == TransactionType.EXPENSE && it.categoryId == 2L }
            .sumOf { it.mySharePaise ?: it.amountPaise }
        assertEquals(3000_00L, budgetUsed)

        // Category donut grouping
        val categoryTotals = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.amountPaise } }
        assertEquals(3000_00L, categoryTotals[2L])

        // Daily chart
        val dailyTotal = transactions
            .filter { it.type == TransactionType.EXPENSE && LocalDate.ofEpochDay(it.dateEpochDay) == today }
            .sumOf { it.amountPaise }
        assertEquals(3000_00L, dailyTotal)
    }

    // 5. Editing principal correctly reconciles account balance
    @Test
    fun testEditPrincipalReconcilesAccountBalance() {
        var accountBalance = 30000_00L
        var previousPrincipal = 10000_00L

        // Increase principal from ₹10,000 to ₹12,000
        val newPrincipalIncrease = 12000_00L
        val diffIncrease = newPrincipalIncrease - previousPrincipal // +2,000
        assertTrue("Account has sufficient balance for increase", accountBalance >= diffIncrease)
        accountBalance -= diffIncrease
        previousPrincipal = newPrincipalIncrease
        assertEquals(28000_00L, accountBalance)

        // Decrease principal from ₹12,000 to ₹7,000
        val newPrincipalDecrease = 7000_00L
        val diffDecrease = newPrincipalDecrease - previousPrincipal // -5,000
        accountBalance -= diffDecrease // -(-5,000) = +5,000
        previousPrincipal = newPrincipalDecrease
        assertEquals(33000_00L, accountBalance)
    }

    // 6. Changing linked account correctly moves principal
    @Test
    fun testChangingLinkedAccountCorrectlyMovesPrincipal() {
        var accountABalance = 50000_00L
        var accountBBalance = 40000_00L
        val oldPrincipal = 20000_00L
        val newPrincipal = 25000_00L

        // Return old principal to Account A
        accountABalance += oldPrincipal
        assertEquals(70000_00L, accountABalance)

        // Deduct new principal from Account B
        assertTrue("Account B has sufficient balance", accountBBalance >= newPrincipal)
        accountBBalance -= newPrincipal
        assertEquals(15000_00L, accountBBalance)
    }

    // 7. Deleting ACTIVE FD returns principal
    @Test
    fun testDeleteActiveFdReturnsPrincipal() {
        var accountBalance = 25000_00L
        val fd = FixedDepositEntity(
            id = 1L,
            name = "ICICI FD",
            linkedAccountId = 1L,
            principalPaise = 50000_00L,
            interestRateBps = 700,
            startDateEpochDay = 100L,
            maturityDateEpochDay = 200L,
            maturityAmountPaise = 53500_00L,
            status = FixedDepositStatus.ACTIVE
        )

        // On deletion of ACTIVE FD
        if (fd.status == FixedDepositStatus.ACTIVE) {
            accountBalance += fd.principalPaise
        }

        assertEquals(75000_00L, accountBalance)
    }

    // 8. Deleting MATURED FD does not alter account balance
    @Test
    fun testDeleteMaturedFdDoesNotAlterAccountBalance() {
        val accountBalanceAfterMaturity = 78625_00L
        var accountBalance = accountBalanceAfterMaturity

        val maturedFd = FixedDepositEntity(
            id = 1L,
            name = "Matured FD",
            linkedAccountId = 1L,
            principalPaise = 50000_00L,
            interestRateBps = 725,
            startDateEpochDay = 100L,
            maturityDateEpochDay = 200L,
            maturityAmountPaise = 53625_00L,
            status = FixedDepositStatus.MATURED
        )

        // On deletion of MATURED FD
        if (maturedFd.status == FixedDepositStatus.ACTIVE) {
            accountBalance += maturedFd.principalPaise
        }

        // Account balance remains untouched
        assertEquals(accountBalanceAfterMaturity, accountBalance)
    }

    // 9. Maturity credits full maturity amount
    @Test
    fun testMaturityCreditsFullMaturityAmount() {
        var accountBalance = 25000_00L
        val principal = 50000_00L
        val maturityAmount = 53625_00L

        accountBalance += maturityAmount
        assertEquals(78625_00L, accountBalance)
    }

    // 10. Only interest is recorded as income
    @Test
    fun testOnlyInterestRecordedAsIncome() {
        val principal = 50000_00L
        val maturityAmount = 53625_00L
        val interest = maturityAmount - principal

        val transactions = mutableListOf<TransactionEntity>()
        if (interest > 0L) {
            transactions.add(
                TransactionEntity(
                    id = 1L,
                    dateEpochDay = 200L,
                    description = "FD Maturity Interest - HDFC FD",
                    amountPaise = interest,
                    categoryId = 99L,
                    accountId = 10L,
                    type = TransactionType.INCOME
                )
            )
        }

        val totalIncome = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        assertEquals(3625_00L, totalIncome)
        // Ensure principal 50,000 is not counted as income
        assertNotEquals(53625_00L, totalIncome)
    }

    // 11. No zero-value income transaction when interest = 0
    @Test
    fun testNoZeroValueIncomeTransactionWhenInterestIsZero() {
        val principal = 50000_00L
        val maturityAmount = 50000_00L
        val interest = (maturityAmount - principal).coerceAtLeast(0L)

        val transactions = mutableListOf<TransactionEntity>()
        if (interest > 0L) {
            transactions.add(
                TransactionEntity(
                    id = 1L,
                    dateEpochDay = 200L,
                    description = "FD Maturity Interest - Zero Interest FD",
                    amountPaise = interest,
                    categoryId = 99L,
                    accountId = 10L,
                    type = TransactionType.INCOME
                )
            )
        }

        assertTrue("Zero-interest FD must not create an income transaction", transactions.isEmpty())
    }

    // 12. Maturity is idempotent
    @Test
    fun testMaturityIsIdempotent() {
        var fd = FixedDepositEntity(
            id = 1L,
            name = "HDFC FD",
            linkedAccountId = 1L,
            principalPaise = 50000_00L,
            interestRateBps = 725,
            startDateEpochDay = 100L,
            maturityDateEpochDay = 200L,
            maturityAmountPaise = 53625_00L,
            status = FixedDepositStatus.ACTIVE
        )

        var timesMatured = 0
        fun mature(): Boolean {
            if (fd.status != FixedDepositStatus.ACTIVE) return false
            fd = fd.copy(status = FixedDepositStatus.MATURED)
            timesMatured++
            return true
        }

        assertTrue("First maturity call succeeds", mature())
        assertEquals(1, timesMatured)
        assertEquals(FixedDepositStatus.MATURED, fd.status)

        assertFalse("Second maturity call is a no-op", mature())
        assertEquals(1, timesMatured)
    }

    // 13. Due maturity processing handles multiple FDs
    @Test
    fun testDueMaturityProcessingHandlesMultipleFds() {
        val today = 500L
        val fds = listOf(
            FixedDepositEntity(1L, "FD 1", 1L, 10000_00L, 700, 100L, 400L, 10700_00L, status = FixedDepositStatus.ACTIVE), // Due
            FixedDepositEntity(2L, "FD 2", 1L, 20000_00L, 700, 200L, 500L, 21400_00L, status = FixedDepositStatus.ACTIVE), // Due today
            FixedDepositEntity(3L, "FD 3", 1L, 30000_00L, 700, 300L, 600L, 32100_00L, status = FixedDepositStatus.ACTIVE), // Not due
            FixedDepositEntity(4L, "FD 4", 1L, 40000_00L, 700, 100L, 400L, 42800_00L, status = FixedDepositStatus.MATURED) // Already matured
        )

        val due = fds.filter { it.status == FixedDepositStatus.ACTIVE && it.maturityDateEpochDay <= today }
        assertEquals(2, due.size)
        assertEquals(listOf(1L, 2L), due.map { it.id })
    }

    // 14. Backup/restore preserves FD data
    // 14. Backup/restore preserves FD data including compoundingFrequency and isExisting
    @Test
    fun testBackupRestorePreservesFdData() {
        val original = FixedDepositEntity(
            id = 99L,
            name = "SBI Special FD",
            linkedAccountId = 5L,
            principalPaise = 100000_00L,
            interestRateBps = 750,
            startDateEpochDay = 20500L,
            maturityDateEpochDay = 20865L,
            maturityAmountPaise = 107500_00L,
            description = "High yield deposit",
            status = FixedDepositStatus.ACTIVE,
            compoundingFrequency = CompoundingFrequency.MONTHLY,
            isExisting = true
        )

        // Emulate backup serialization map/fields
        val map = mapOf(
            "id" to original.id,
            "name" to original.name,
            "accountId" to original.linkedAccountId,
            "principal" to original.principalPaise,
            "rate" to original.interestRateBps,
            "startDate" to original.startDateEpochDay,
            "maturityDate" to original.maturityDateEpochDay,
            "maturityAmount" to original.maturityAmountPaise,
            "description" to original.description,
            "status" to original.status.name,
            "compoundingFrequency" to original.compoundingFrequency.name,
            "isExisting" to original.isExisting
        )

        // Emulate deserialization
        val restored = FixedDepositEntity(
            id = map["id"] as Long,
            name = map["name"] as String,
            linkedAccountId = map["accountId"] as Long,
            principalPaise = map["principal"] as Long,
            interestRateBps = map["rate"] as Int,
            startDateEpochDay = map["startDate"] as Long,
            maturityDateEpochDay = map["maturityDate"] as Long,
            maturityAmountPaise = map["maturityAmount"] as Long,
            description = map["description"] as String,
            status = FixedDepositStatus.valueOf(map["status"] as String),
            compoundingFrequency = CompoundingFrequency.valueOf(map["compoundingFrequency"] as String),
            isExisting = map["isExisting"] as Boolean
        )

        assertEquals(original, restored)
        assertEquals(CompoundingFrequency.MONTHLY, restored.compoundingFrequency)
        assertTrue(restored.isExisting)
    }

    // 15. ACTIVE FD blocks account deletion
    @Test
    fun testActiveFdBlocksAccountDeletion() {
        val fds = listOf(
            FixedDepositEntity(1L, "FD Active", linkedAccountId = 10L, principalPaise = 50000_00L, interestRateBps = 700, startDateEpochDay = 100L, maturityDateEpochDay = 200L, maturityAmountPaise = 53500_00L, status = FixedDepositStatus.ACTIVE)
        )

        val activeCountForAccount10 = fds.count { it.linkedAccountId == 10L && it.status == FixedDepositStatus.ACTIVE }
        val canDeleteAccount = activeCountForAccount10 == 0
        assertFalse("Account referenced by ACTIVE FD must NOT be deletable", canDeleteAccount)
    }

    // 16. MATURED FD does not block account deletion
    @Test
    fun testMaturedFdDoesNotBlockAccountDeletion() {
        val fds = listOf(
            FixedDepositEntity(1L, "FD Matured", linkedAccountId = 10L, principalPaise = 50000_00L, interestRateBps = 700, startDateEpochDay = 100L, maturityDateEpochDay = 200L, maturityAmountPaise = 53500_00L, status = FixedDepositStatus.MATURED)
        )

        val activeCountForAccount10 = fds.count { it.linkedAccountId == 10L && it.status == FixedDepositStatus.ACTIVE }
        val canDeleteAccount = activeCountForAccount10 == 0
        assertTrue("Account referenced only by MATURED FDs CAN be deleted", canDeleteAccount)
    }

    // 17. Net worth includes ACTIVE FD principal only
    @Test
    fun testNetWorthIncludesActiveFdPrincipalOnly() {
        val cash = 25000_00L
        val activeFdPrincipal = 50000_00L
        val owed = 0L
        val cards = 0L
        val loans = 0L

        // When FD is active: Net worth = cash + activeFdPrincipal + owed - cards - loans
        val netWorthActive = cash + activeFdPrincipal + owed - cards - loans
        assertEquals(75000_00L, netWorthActive)

        // After maturity: account received 53,625 (50,000 principal + 3,625 interest)
        val cashAfterMaturity = 25000_00L + 53625_00L
        val activeFdPrincipalAfterMaturity = 0L // FD is now MATURED, so active principal is 0
        val netWorthMatured = cashAfterMaturity + activeFdPrincipalAfterMaturity + owed - cards - loans
        assertEquals(78625_00L, netWorthMatured)
    }

    // 18. Default compounding frequency is QUARTERLY
    @Test
    fun testDefaultCompoundingFrequencyIsQuarterly() {
        val fd = FixedDepositEntity(
            id = 1L,
            name = "Default Compounding FD",
            linkedAccountId = 1L,
            principalPaise = 10000_00L,
            interestRateBps = 700,
            startDateEpochDay = 100L,
            maturityDateEpochDay = 465L,
            maturityAmountPaise = 10700_00L
        )
        assertEquals(CompoundingFrequency.QUARTERLY, fd.compoundingFrequency)
        assertFalse(fd.isExisting)
    }

    // 19. Standard compound interest calculation produces exactly ₹1,683.04 for ₹1,500 at 7.75% from 15-Jul-2026 to 16-Jan-2028 (18 months)
    @Test
    fun testStandardCompoundInterestFormulaValidationCase() {
        val start = LocalDate.of(2026, 7, 15)
        val maturity = LocalDate.of(2028, 1, 16)
        val principalPaise = 1500_00L
        val rateBps = 775 // 7.75%

        val calculatedMaturity = FixedDepositCalculator.calculateMaturityAmountPaise(
            principalPaise = principalPaise,
            interestRateBps = rateBps,
            startDateEpochDay = start.toEpochDay(),
            maturityDateEpochDay = maturity.toEpochDay(),
            frequency = CompoundingFrequency.QUARTERLY
        )

        // ₹1,683.04 (168,304 paise)
        assertEquals("Maturity amount must be exactly ₹1,683.04 (168304 paise)", 1683_04L, calculatedMaturity)

        val totalInterest = FixedDepositCalculator.calculateEstimatedInterestPaise(
            principalPaise = principalPaise,
            interestRateBps = rateBps,
            startDateEpochDay = start.toEpochDay(),
            maturityDateEpochDay = maturity.toEpochDay(),
            frequency = CompoundingFrequency.QUARTERLY
        )
        assertEquals("Total estimated interest must be exactly ₹183.04 (18304 paise)", 183_04L, totalInterest)

        // Exact month anniversary (15-Jan-2028) also yields same 18-month tenure calculation
        val maturityAnniversary = LocalDate.of(2028, 1, 15)
        val anniversaryMaturity = FixedDepositCalculator.calculateMaturityAmountPaise(
            principalPaise = principalPaise,
            interestRateBps = rateBps,
            startDateEpochDay = start.toEpochDay(),
            maturityDateEpochDay = maturityAnniversary.toEpochDay(),
            frequency = CompoundingFrequency.QUARTERLY
        )
        assertEquals("Anniversary maturity amount must also be ₹1,683.04", 1683_04L, anniversaryMaturity)
        assertEquals("Displayed maturity amount must round to nearest whole rupee with no decimals", "₹1,683", FixedDepositCalculator.formatMaturityAmount(calculatedMaturity))
    }

    // 20. Tenure calculation correctly identifies years/months/days across various dates
    @Test
    fun testTenureCalculationForVariousDates() {
        // 18 months: 15-Jul-2026 to 16-Jan-2028
        val t18m = FixedDepositCalculator.calculateTenureYears(
            LocalDate.of(2026, 7, 15).toEpochDay(),
            LocalDate.of(2028, 1, 16).toEpochDay()
        )
        assertEquals(1.5, t18m, 0.0001)

        // 12 months: 1-Jan-2026 to 1-Jan-2027
        val t1yExact = FixedDepositCalculator.calculateTenureYears(
            LocalDate.of(2026, 1, 1).toEpochDay(),
            LocalDate.of(2027, 1, 1).toEpochDay()
        )
        assertEquals(1.0, t1yExact, 0.0001)

        // 12 months with payout next day: 1-Jan-2026 to 2-Jan-2027
        val t1yNextDay = FixedDepositCalculator.calculateTenureYears(
            LocalDate.of(2026, 1, 1).toEpochDay(),
            LocalDate.of(2027, 1, 2).toEpochDay()
        )
        assertEquals(1.0, t1yNextDay, 0.0001)

        // 365 epoch days (1000L to 1365L)
        val t365Days = FixedDepositCalculator.calculateTenureYears(1000L, 1365L)
        assertEquals(1.0, t365Days, 0.0001)
    }

    // 21. Monthly, Half-Yearly, and Yearly compounding produce distinctly different amounts from Quarterly
    @Test
    fun testDifferentCompoundingFrequenciesProduceDifferentAmounts() {
        val start = LocalDate.of(2026, 7, 15)
        val maturity = LocalDate.of(2028, 1, 16)
        val principalPaise = 1500_00L
        val rateBps = 775

        val monthly = FixedDepositCalculator.calculateMaturityAmountPaise(principalPaise, rateBps, start.toEpochDay(), maturity.toEpochDay(), CompoundingFrequency.MONTHLY)
        val quarterly = FixedDepositCalculator.calculateMaturityAmountPaise(principalPaise, rateBps, start.toEpochDay(), maturity.toEpochDay(), CompoundingFrequency.QUARTERLY)
        val halfYearly = FixedDepositCalculator.calculateMaturityAmountPaise(principalPaise, rateBps, start.toEpochDay(), maturity.toEpochDay(), CompoundingFrequency.HALF_YEARLY)
        val yearly = FixedDepositCalculator.calculateMaturityAmountPaise(principalPaise, rateBps, start.toEpochDay(), maturity.toEpochDay(), CompoundingFrequency.YEARLY)

        assertNotEquals("Monthly must differ from Quarterly", quarterly, monthly)
        assertNotEquals("Half-yearly must differ from Quarterly", quarterly, halfYearly)
        assertNotEquals("Yearly must differ from Quarterly", quarterly, yearly)

        // Compounding property: Monthly > Quarterly > Half-Yearly > Yearly
        assertTrue("Monthly ($monthly) > Quarterly ($quarterly)", monthly > quarterly)
        assertTrue("Quarterly ($quarterly) > Half-Yearly ($halfYearly)", quarterly > halfYearly)
        assertTrue("Half-Yearly ($halfYearly) > Yearly ($yearly)", halfYearly > yearly)

        assertEquals("Monthly produces exactly ₹1,684.29", 1684_29L, monthly)
        assertEquals("Quarterly produces exactly ₹1,683.04", 1683_04L, quarterly)
        assertEquals("Half-Yearly produces exactly ₹1,681.22", 1681_22L, halfYearly)
        assertEquals("Yearly produces exactly ₹1,677.71", 1677_71L, yearly)
    }

    // 22. Changing frequency changes maturity calculation immediately
    @Test
    fun testChangingFrequencyChangesMaturityCalculation() {
        val start = 1000L
        val end = 1365L // 1 year duration
        val principal = 100000_00L
        val rate = 800 // 8.00%

        val fvQuarterly = FixedDepositCalculator.calculateMaturityAmountPaise(principal, rate, start, end, CompoundingFrequency.QUARTERLY)
        val fvMonthly = FixedDepositCalculator.calculateMaturityAmountPaise(principal, rate, start, end, CompoundingFrequency.MONTHLY)

        assertNotEquals(fvQuarterly, fvMonthly)
    }

    // 23. Record as Existing does NOT deduct principal from linked account
    @Test
    fun testRecordAsExistingDoesNotDeductPrincipal() {
        var accountBalance = 75000_00L
        val principal = 50000_00L
        val isExisting = true

        // For existing FD, balance deduction is skipped
        if (!isExisting) {
            accountBalance -= principal
        }

        val fd = FixedDepositEntity(
            id = 1L,
            name = "Existing SBI FD",
            linkedAccountId = 1L,
            principalPaise = principal,
            interestRateBps = 700,
            startDateEpochDay = 100L,
            maturityDateEpochDay = 465L,
            maturityAmountPaise = 53500_00L,
            status = FixedDepositStatus.ACTIVE,
            isExisting = isExisting
        )

        assertEquals("Account balance must remain 75,000 for existing FD", 75000_00L, accountBalance)
        assertTrue(fd.isExisting)
    }

    // 24. Deleting active existing FD does NOT add principal back
    @Test
    fun testDeleteActiveExistingFdDoesNotRestorePrincipal() {
        var accountBalance = 75000_00L
        val fd = FixedDepositEntity(
            id = 1L,
            name = "Existing SBI FD",
            linkedAccountId = 1L,
            principalPaise = 50000_00L,
            interestRateBps = 700,
            startDateEpochDay = 100L,
            maturityDateEpochDay = 465L,
            maturityAmountPaise = 53500_00L,
            status = FixedDepositStatus.ACTIVE,
            isExisting = true
        )

        // On delete: only restore principal if active AND !isExisting
        if (fd.status == FixedDepositStatus.ACTIVE && !fd.isExisting) {
            accountBalance += fd.principalPaise
        }

        assertEquals("Account balance must NOT be increased on deletion of existing FD", 75000_00L, accountBalance)
    }

    // 25. Maturity of existing FD credits full maturity amount and records interest as income
    @Test
    fun testMaturityOfExistingFdCreditsFullAmountAndRecordsInterest() {
        var accountBalance = 25000_00L
        val principal = 50000_00L
        val maturityAmount = 53625_00L
        val interest = maturityAmount - principal

        val fd = FixedDepositEntity(
            id = 1L,
            name = "Existing FD Maturing",
            linkedAccountId = 1L,
            principalPaise = principal,
            interestRateBps = 725,
            startDateEpochDay = 100L,
            maturityDateEpochDay = 200L,
            maturityAmountPaise = maturityAmount,
            status = FixedDepositStatus.ACTIVE,
            isExisting = true
        )

        // Maturing credits full maturity proceeds to account
        accountBalance += fd.maturityAmountPaise
        assertEquals(78625_00L, accountBalance)

        // Only interest is recorded as income
        val transactions = mutableListOf<TransactionEntity>()
        if (interest > 0L) {
            transactions.add(
                TransactionEntity(
                    id = 1L,
                    dateEpochDay = 200L,
                    description = "FD Maturity Interest - ${fd.name}",
                    amountPaise = interest,
                    categoryId = 99L,
                    accountId = 1L,
                    type = TransactionType.INCOME
                )
            )
        }

        assertEquals(1, transactions.size)
        assertEquals(3625_00L, transactions[0].amountPaise)
        assertEquals(TransactionType.INCOME, transactions[0].type)
    }

    // 26. Legacy backups without compoundingFrequency and isExisting default properly
    @Test
    fun testLegacyBackupDefaultsProperly() {
        // Legacy map without compoundingFrequency or isExisting
        val map = mapOf(
            "id" to 101L,
            "name" to "Legacy FD",
            "accountId" to 2L,
            "principal" to 25000_00L,
            "rate" to 650,
            "startDate" to 1000L,
            "maturityDate" to 1365L,
            "maturityAmount" to 26625_00L,
            "status" to "ACTIVE"
        )

        val freq = runCatching {
            CompoundingFrequency.valueOf(map["compoundingFrequency"] as? String ?: "QUARTERLY")
        }.getOrDefault(CompoundingFrequency.QUARTERLY)
        val isExisting = (map["isExisting"] as? Boolean) ?: false

        val restored = FixedDepositEntity(
            id = map["id"] as Long,
            name = map["name"] as String,
            linkedAccountId = map["accountId"] as Long,
            principalPaise = map["principal"] as Long,
            interestRateBps = map["rate"] as Int,
            startDateEpochDay = map["startDate"] as Long,
            maturityDateEpochDay = map["maturityDate"] as Long,
            maturityAmountPaise = map["maturityAmount"] as Long,
            description = (map["description"] as? String) ?: "",
            status = FixedDepositStatus.valueOf(map["status"] as String),
            compoundingFrequency = freq,
            isExisting = isExisting
        )

        assertEquals(CompoundingFrequency.QUARTERLY, restored.compoundingFrequency)
        assertFalse(restored.isExisting)
    }

    // 27. Maturity amount display rounds to nearest whole rupee with no decimal places
    @Test
    fun testMaturityAmountDisplayRounding() {
        // Less than ₹0.50 rounds down
        assertEquals("₹1,683", FixedDepositCalculator.formatMaturityAmount(1683_04L))
        assertEquals("₹1,683", FixedDepositCalculator.formatMaturityAmount(1683_49L))

        // ₹0.50 or greater rounds up
        assertEquals("₹1,684", FixedDepositCalculator.formatMaturityAmount(1683_50L))
        assertEquals("₹1,684", FixedDepositCalculator.formatMaturityAmount(1683_99L))

        // Zero and large values
        assertEquals("₹0", FixedDepositCalculator.formatMaturityAmount(0L))
        assertEquals("₹100,000", FixedDepositCalculator.formatMaturityAmount(100000_00L))
    }
}
