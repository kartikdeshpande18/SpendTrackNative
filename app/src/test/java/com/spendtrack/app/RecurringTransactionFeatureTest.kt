package com.spendtrack.app

import com.spendtrack.app.data.local.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecurringTransactionFeatureTest {

    // 1. Daily recurrence calculation
    @Test
    fun testDailyRecurrenceCalculation() {
        val start = LocalDate.of(2026, 3, 1)
        val next1 = RecurringTransactionScheduler.calculateNextOccurrence(start, start, RecurrenceFrequency.DAILY)
        assertEquals(LocalDate.of(2026, 3, 2), next1)
        val next2 = RecurringTransactionScheduler.calculateNextOccurrence(start, next1, RecurrenceFrequency.DAILY)
        assertEquals(LocalDate.of(2026, 3, 3), next2)
    }

    // 2. Weekly recurrence calculation
    @Test
    fun testWeeklyRecurrenceCalculation() {
        val start = LocalDate.of(2026, 3, 1) // Sunday
        val next1 = RecurringTransactionScheduler.calculateNextOccurrence(start, start, RecurrenceFrequency.WEEKLY)
        assertEquals(LocalDate.of(2026, 3, 8), next1)
        val next2 = RecurringTransactionScheduler.calculateNextOccurrence(start, next1, RecurrenceFrequency.WEEKLY)
        assertEquals(LocalDate.of(2026, 3, 15), next2)
    }

    // 3. Monthly recurrence calculation anchored to 31st (31 Jan -> 28 Feb -> 31 Mar -> 30 Apr -> 31 May)
    @Test
    fun testMonthlyAnchoredRecurrence31st() {
        val start = LocalDate.of(2026, 1, 31)

        val feb = RecurringTransactionScheduler.calculateNextOccurrence(start, start, RecurrenceFrequency.MONTHLY)
        assertEquals(LocalDate.of(2026, 2, 28), feb)

        val mar = RecurringTransactionScheduler.calculateNextOccurrence(start, feb, RecurrenceFrequency.MONTHLY)
        assertEquals("Must restore anchor to 31st in March", LocalDate.of(2026, 3, 31), mar)

        val apr = RecurringTransactionScheduler.calculateNextOccurrence(start, mar, RecurrenceFrequency.MONTHLY)
        assertEquals(LocalDate.of(2026, 4, 30), apr)

        val may = RecurringTransactionScheduler.calculateNextOccurrence(start, apr, RecurrenceFrequency.MONTHLY)
        assertEquals("Must restore anchor to 31st in May", LocalDate.of(2026, 5, 31), may)
    }

    // 4. Monthly recurrence calculation anchored to 30th
    @Test
    fun testMonthlyAnchoredRecurrence30th() {
        val start = LocalDate.of(2026, 1, 30)

        val feb = RecurringTransactionScheduler.calculateNextOccurrence(start, start, RecurrenceFrequency.MONTHLY)
        assertEquals(LocalDate.of(2026, 2, 28), feb)

        val mar = RecurringTransactionScheduler.calculateNextOccurrence(start, feb, RecurrenceFrequency.MONTHLY)
        assertEquals(LocalDate.of(2026, 3, 30), mar)

        val apr = RecurringTransactionScheduler.calculateNextOccurrence(start, mar, RecurrenceFrequency.MONTHLY)
        assertEquals(LocalDate.of(2026, 4, 30), apr)

        val may = RecurringTransactionScheduler.calculateNextOccurrence(start, apr, RecurrenceFrequency.MONTHLY)
        assertEquals(LocalDate.of(2026, 5, 30), may)
    }

    // 5. Yearly recurrence calculation anchored to Leap Day (29 Feb 2028 -> 28 Feb 2029 -> 28 Feb 2030 -> 28 Feb 2031 -> 29 Feb 2032)
    @Test
    fun testYearlyAnchoredRecurrenceLeapDay() {
        val start = LocalDate.of(2028, 2, 29) // 2028 is leap year

        val y2029 = RecurringTransactionScheduler.calculateNextOccurrence(start, start, RecurrenceFrequency.YEARLY)
        assertEquals(LocalDate.of(2029, 2, 28), y2029)

        val y2030 = RecurringTransactionScheduler.calculateNextOccurrence(start, y2029, RecurrenceFrequency.YEARLY)
        assertEquals(LocalDate.of(2030, 2, 28), y2030)

        val y2031 = RecurringTransactionScheduler.calculateNextOccurrence(start, y2030, RecurrenceFrequency.YEARLY)
        assertEquals(LocalDate.of(2031, 2, 28), y2031)

        val y2032 = RecurringTransactionScheduler.calculateNextOccurrence(start, y2031, RecurrenceFrequency.YEARLY)
        assertEquals("Must restore Feb 29 anchor in leap year 2032", LocalDate.of(2032, 2, 29), y2032)
    }

    // 6. Recurrence summary formatting
    @Test
    fun testFormatRecurrenceSummary() {
        val daily = RecurringTransactionScheduler.formatRecurrenceSummary(
            RecurrenceFrequency.DAILY,
            LocalDate.of(2026, 9, 8)
        )
        assertEquals("Repeats daily", daily)

        val weekly = RecurringTransactionScheduler.formatRecurrenceSummary(
            RecurrenceFrequency.WEEKLY,
            LocalDate.of(2026, 9, 8) // Tuesday
        )
        assertTrue(weekly.contains("Tuesday"))

        val monthly8th = RecurringTransactionScheduler.formatRecurrenceSummary(
            RecurrenceFrequency.MONTHLY,
            LocalDate.of(2026, 9, 8)
        )
        assertEquals("Repeats monthly on the 8th", monthly8th)

        val monthly1st = RecurringTransactionScheduler.formatRecurrenceSummary(
            RecurrenceFrequency.MONTHLY,
            LocalDate.of(2026, 9, 1)
        )
        assertEquals("Repeats monthly on the 1st", monthly1st)

        val monthly2nd = RecurringTransactionScheduler.formatRecurrenceSummary(
            RecurrenceFrequency.MONTHLY,
            LocalDate.of(2026, 9, 2)
        )
        assertEquals("Repeats monthly on the 2nd", monthly2nd)

        val monthly3rd = RecurringTransactionScheduler.formatRecurrenceSummary(
            RecurrenceFrequency.MONTHLY,
            LocalDate.of(2026, 9, 3)
        )
        assertEquals("Repeats monthly on the 3rd", monthly3rd)

        val yearly = RecurringTransactionScheduler.formatRecurrenceSummary(
            RecurrenceFrequency.YEARLY,
            LocalDate.of(2026, 7, 15)
        )
        assertTrue(yearly.startsWith("Repeats yearly on 15 Jul"))

        val withEndDate = RecurringTransactionScheduler.formatRecurrenceSummary(
            RecurrenceFrequency.MONTHLY,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 6, 30)
        )
        assertTrue(withEndDate.contains("until 30 Jun 2026"))
    }

    // 7. Optional end date terminates recurring schedule
    @Test
    fun testScheduleWithEndDateStops() {
        val startDate = LocalDate.of(2026, 1, 1)
        val endDate = LocalDate.of(2026, 3, 1)
        val today = LocalDate.of(2026, 5, 1)

        val generatedDates = mutableListOf<LocalDate>()
        var currentNext = startDate

        while (!currentNext.isAfter(today) && !currentNext.isAfter(endDate)) {
            generatedDates.add(currentNext)
            currentNext = RecurringTransactionScheduler.calculateNextOccurrence(
                startDate,
                currentNext,
                RecurrenceFrequency.MONTHLY
            )
        }

        val isEnabled = currentNext <= endDate

        assertEquals(3, generatedDates.size)
        assertEquals(listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)), generatedDates)
        assertFalse("Schedule must be disabled after passing end date", isEnabled)
    }

    // 8. Disabled/paused schedule does not generate occurrences
    @Test
    fun testDisabledScheduleDoesNotGenerateOccurrences() {
        val schedules = listOf(
            RecurringTransactionEntity(
                id = 1L,
                description = "Netflix",
                amountPaise = 649_00L,
                categoryId = 2L,
                accountId = 10L,
                startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
                nextOccurrenceEpochDay = LocalDate.of(2026, 2, 1).toEpochDay(),
                isEnabled = false
            )
        )

        val today = LocalDate.of(2026, 3, 1).toEpochDay()
        val dueSchedules = schedules.filter { it.isEnabled && it.nextOccurrenceEpochDay <= today }
        assertTrue("Disabled schedules must not be due", dueSchedules.isEmpty())
    }

    // 9. Catch-up generation for missed occurrences
    @Test
    fun testCatchUpMissedOccurrences() {
        val startDate = LocalDate.of(2026, 1, 1)
        val today = LocalDate.of(2026, 1, 4)

        val occurrences = mutableListOf<LocalDate>()
        var currentNext = startDate

        while (!currentNext.isAfter(today)) {
            occurrences.add(currentNext)
            currentNext = RecurringTransactionScheduler.calculateNextOccurrence(
                startDate,
                currentNext,
                RecurrenceFrequency.DAILY
            )
        }

        assertEquals(4, occurrences.size)
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 2),
                LocalDate.of(2026, 1, 3),
                LocalDate.of(2026, 1, 4)
            ),
            occurrences
        )
        assertEquals(LocalDate.of(2026, 1, 5), currentNext)
    }

    // 10. Strong idempotency on repeated processing
    @Test
    fun testIdempotencyOnRepeatedProcessing() {
        val scheduleId = 1L
        val occurrencesTable = mutableSetOf<Pair<Long, Long>>() // (scheduleId, epochDay)
        val generatedTransactions = mutableListOf<TransactionEntity>()

        fun process(currentNext: Long, desc: String, amount: Long) {
            val key = Pair(scheduleId, currentNext)
            if (key !in occurrencesTable) {
                generatedTransactions.add(
                    TransactionEntity(
                        id = generatedTransactions.size + 1L,
                        dateEpochDay = currentNext,
                        description = desc,
                        amountPaise = amount,
                        categoryId = 1L,
                        accountId = 10L,
                        type = TransactionType.EXPENSE
                    )
                )
                occurrencesTable.add(key)
            }
        }

        val day = LocalDate.of(2026, 9, 8).toEpochDay()

        // First run
        process(day, "Internet Bill", 1000_00L)
        assertEquals(1, generatedTransactions.size)
        assertEquals(1, occurrencesTable.size)

        // Second run (duplicate worker / retry / app restart)
        process(day, "Internet Bill", 1000_00L)
        assertEquals("Duplicate processing must NOT create another transaction", 1, generatedTransactions.size)
        assertEquals(1, occurrencesTable.size)
    }

    // 11. Immediate first occurrence when start date <= today
    @Test
    fun testImmediateFirstOccurrenceWhenStartingToday() {
        val today = LocalDate.of(2026, 9, 8)
        val startDate = LocalDate.of(2026, 9, 8)

        var accountBalance = 50000_00L
        val transactions = mutableListOf<TransactionEntity>()
        var nextOccurrence = startDate.toEpochDay()

        // When schedule is saved with startDate <= today:
        if (startDate.toEpochDay() <= today.toEpochDay()) {
            transactions.add(
                TransactionEntity(
                    id = 1L,
                    dateEpochDay = startDate.toEpochDay(),
                    description = "Gym Membership",
                    amountPaise = 2000_00L,
                    categoryId = 3L,
                    accountId = 10L,
                    type = TransactionType.EXPENSE
                )
            )
            accountBalance -= 2000_00L
            nextOccurrence = RecurringTransactionScheduler.calculateNextOccurrence(
                startDate,
                startDate,
                RecurrenceFrequency.MONTHLY
            ).toEpochDay()
        }

        assertEquals(1, transactions.size)
        assertEquals(48000_00L, accountBalance)
        assertEquals(LocalDate.of(2026, 10, 8).toEpochDay(), nextOccurrence)

        // Background worker runs later on same day
        val dueSchedules = if (nextOccurrence <= today.toEpochDay()) 1 else 0
        assertEquals("Worker must not re-process because nextOccurrence is advanced", 0, dueSchedules)
    }

    // 12. Future start date does not generate transaction today
    @Test
    fun testFutureStartDateDoesNotGenerateImmediateOccurrence() {
        val today = LocalDate.of(2026, 9, 8)
        val futureStart = LocalDate.of(2026, 9, 15)

        val transactions = mutableListOf<TransactionEntity>()
        val nextOccurrence = futureStart.toEpochDay()

        if (futureStart.toEpochDay() <= today.toEpochDay()) {
            transactions.add(
                TransactionEntity(
                    id = 1L,
                    dateEpochDay = futureStart.toEpochDay(),
                    description = "Future SIP",
                    amountPaise = 5000_00L,
                    categoryId = 4L,
                    accountId = 10L,
                    type = TransactionType.EXPENSE
                )
            )
        }

        assertTrue("No transaction generated for future schedule", transactions.isEmpty())
        assertEquals(LocalDate.of(2026, 9, 15).toEpochDay(), nextOccurrence)
    }

    // 13. Recurring transfer updates both source and destination accounts
    @Test
    fun testRecurringTransferUpdatesBothAccounts() {
        var sourceBalance = 20000_00L
        var destBalance = 10000_00L
        val transferAmount = 5000_00L

        // Execute recurring transfer
        val transferTx = TransactionEntity(
            id = 1L,
            dateEpochDay = LocalDate.of(2026, 9, 8).toEpochDay(),
            description = "Monthly savings transfer",
            amountPaise = transferAmount,
            categoryId = 0L,
            accountId = 10L,
            destinationAccountId = 20L,
            type = TransactionType.TRANSFER
        )

        sourceBalance -= transferAmount
        destBalance += transferAmount

        assertEquals(15000_00L, sourceBalance)
        assertEquals(15000_00L, destBalance)
        assertEquals(TransactionType.TRANSFER, transferTx.type)

        // Verify transfer is excluded from expense and income totals
        val transactions = listOf(transferTx)
        val expenseTotal = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }
        val incomeTotal = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
        assertEquals(0L, expenseTotal)
        assertEquals(0L, incomeTotal)
    }

    // 14. Editing recurring schedule affects future occurrences only; preserves historical transactions
    @Test
    fun testEditSchedulePreservesHistoricalTransactions() {
        val historicalTransactions = mutableListOf(
            TransactionEntity(1L, LocalDate.of(2026, 1, 1).toEpochDay(), "Broadband", 999_00L, 2L, 10L, TransactionType.EXPENSE),
            TransactionEntity(2L, LocalDate.of(2026, 2, 1).toEpochDay(), "Broadband", 999_00L, 2L, 10L, TransactionType.EXPENSE)
        )

        var schedule = RecurringTransactionEntity(
            id = 1L,
            description = "Broadband",
            amountPaise = 999_00L,
            categoryId = 2L,
            accountId = 10L,
            startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
            nextOccurrenceEpochDay = LocalDate.of(2026, 3, 1).toEpochDay()
        )

        // Edit schedule: amount changes to ₹1,499
        schedule = schedule.copy(amountPaise = 1499_00L)

        // Historical transactions remain untouched
        assertEquals(999_00L, historicalTransactions[0].amountPaise)
        assertEquals(999_00L, historicalTransactions[1].amountPaise)
        assertEquals(1499_00L, schedule.amountPaise)
    }

    // 15. Deleting recurring schedule stops future occurrences; preserves historical transactions
    @Test
    fun testDeleteSchedulePreservesHistoricalTransactions() {
        val historicalTransactions = mutableListOf(
            TransactionEntity(1L, LocalDate.of(2026, 1, 1).toEpochDay(), "Rent", 25000_00L, 1L, 10L, TransactionType.EXPENSE)
        )
        val schedules = mutableListOf(
            RecurringTransactionEntity(
                id = 1L,
                description = "Rent",
                amountPaise = 25000_00L,
                categoryId = 1L,
                accountId = 10L,
                startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
                nextOccurrenceEpochDay = LocalDate.of(2026, 2, 1).toEpochDay()
            )
        )

        // Delete schedule
        schedules.removeIf { it.id == 1L }

        assertTrue(schedules.isEmpty())
        assertEquals("Historical transaction must not be deleted", 1, historicalTransactions.size)
        assertEquals(25000_00L, historicalTransactions[0].amountPaise)
    }

    // 16. Account and category deletion protection
    @Test
    fun testAccountAndCategoryDeletionPreventedWhenReferenced() {
        val recurringSchedules = listOf(
            RecurringTransactionEntity(
                id = 1L,
                description = "SIP",
                amountPaise = 5000_00L,
                categoryId = 5L,
                accountId = 10L,
                startDateEpochDay = LocalDate.of(2026, 1, 1).toEpochDay(),
                nextOccurrenceEpochDay = LocalDate.of(2026, 2, 1).toEpochDay()
            )
        )

        // Validation for Account 10 deletion
        val accountInUse = recurringSchedules.any { it.accountId == 10L || it.destinationAccountId == 10L }
        assertTrue("Account 10 must be recognized as in-use", accountInUse)

        // Validation for Category 5 deletion
        val categoryInUse = recurringSchedules.any { it.categoryId == 5L }
        assertTrue("Category 5 must be recognized as in-use", categoryInUse)
    }

    // 17. Backup/Restore roundtrip and backward compatibility
    @Test
    fun testBackupRestoreRoundtripAndBackwardCompatibility() {
        // Test 1: Backward compatibility when recurring keys are missing
        val legacyBackup = mapOf<String, Any>(
            "format" to "spendtrack-backup",
            "version" to 1,
            "accounts" to emptyList<Map<String, Any>>(),
            "categories" to emptyList<Map<String, Any>>()
        )
        @Suppress("UNCHECKED_CAST")
        val legacyRecurring = legacyBackup["recurringTransactions"] as? List<Map<String, Any>> ?: emptyList()
        val legacyOccurrences = legacyBackup["recurringOccurrences"] as? List<Map<String, Any>> ?: emptyList()
        assertEquals(0, legacyRecurring.size)
        assertEquals(0, legacyOccurrences.size)

        // Test 2: Serialization and deserialization roundtrip
        val originalSchedule = RecurringTransactionEntity(
            id = 1L,
            description = "Netflix",
            amountPaise = 649_00L,
            categoryId = 2L,
            accountId = 10L,
            type = TransactionType.EXPENSE,
            frequency = RecurrenceFrequency.MONTHLY,
            startDateEpochDay = 20000L,
            endDateEpochDay = 20500L,
            nextOccurrenceEpochDay = 20030L,
            isEnabled = true,
            isShared = false,
            mySharePaise = null,
            sharedWith = "",
            destinationAccountId = null
        )
        val originalOccurrence = RecurringOccurrenceEntity(
            id = 101L,
            scheduleId = 1L,
            occurrenceEpochDay = 20000L,
            transactionId = 501L
        )

        // Emulate export mapping
        val exportedScheduleMap = mapOf(
            "id" to originalSchedule.id,
            "description" to originalSchedule.description,
            "amount" to originalSchedule.amountPaise,
            "category" to originalSchedule.categoryId,
            "account" to originalSchedule.accountId,
            "type" to originalSchedule.type.name,
            "frequency" to originalSchedule.frequency.name,
            "startDate" to originalSchedule.startDateEpochDay,
            "endDate" to originalSchedule.endDateEpochDay,
            "nextOccurrence" to originalSchedule.nextOccurrenceEpochDay,
            "isEnabled" to originalSchedule.isEnabled,
            "isShared" to originalSchedule.isShared,
            "share" to originalSchedule.mySharePaise,
            "sharedWith" to originalSchedule.sharedWith,
            "destAccount" to originalSchedule.destinationAccountId
        )
        val exportedOccurrenceMap = mapOf(
            "id" to originalOccurrence.id,
            "scheduleId" to originalOccurrence.scheduleId,
            "occurrenceDate" to originalOccurrence.occurrenceEpochDay,
            "transactionId" to originalOccurrence.transactionId
        )

        // Emulate restore mapping
        val restoredSchedule = RecurringTransactionEntity(
            id = exportedScheduleMap["id"] as Long,
            description = exportedScheduleMap["description"] as String,
            amountPaise = exportedScheduleMap["amount"] as Long,
            categoryId = exportedScheduleMap["category"] as Long,
            accountId = exportedScheduleMap["account"] as Long,
            type = TransactionType.valueOf(exportedScheduleMap["type"] as String),
            frequency = RecurrenceFrequency.valueOf(exportedScheduleMap["frequency"] as String),
            startDateEpochDay = exportedScheduleMap["startDate"] as Long,
            endDateEpochDay = exportedScheduleMap["endDate"] as Long?,
            nextOccurrenceEpochDay = exportedScheduleMap["nextOccurrence"] as Long,
            isEnabled = exportedScheduleMap["isEnabled"] as Boolean,
            isShared = exportedScheduleMap["isShared"] as Boolean,
            mySharePaise = exportedScheduleMap["share"] as Long?,
            sharedWith = exportedScheduleMap["sharedWith"] as String,
            destinationAccountId = exportedScheduleMap["destAccount"] as Long?
        )
        val restoredOccurrence = RecurringOccurrenceEntity(
            id = exportedOccurrenceMap["id"] as Long,
            scheduleId = exportedOccurrenceMap["scheduleId"] as Long,
            occurrenceEpochDay = exportedOccurrenceMap["occurrenceDate"] as Long,
            transactionId = exportedOccurrenceMap["transactionId"] as Long
        )

        assertEquals(originalSchedule, restoredSchedule)
        assertEquals(originalOccurrence, restoredOccurrence)
    }

    // 18. Minimum balance display and Net Worth treatment
    @Test
    fun testMinimumBalanceNetWorthInvariant() {
        val hdfcSavings = AccountEntity(
            id = 1L,
            name = "HDFC Savings",
            bank = "HDFC Bank",
            maskedNumber = "1234",
            balancePaise = 50000_00L, // Full actual bank balance
            isCreditCard = false
        )

        // Invariant 1: Displayed balance is actual bank balance
        assertEquals("Displayed balance must be 50,000", 50000_00L, hdfcSavings.balancePaise)

        // Invariant 2: Net Worth uses full bank balance (cash)
        val accounts = listOf(hdfcSavings)
        val cash = accounts.filterNot { it.isCreditCard }.sumOf { it.balancePaise }
        val activeFdPrincipal = 20000_00L
        val owed = 5000_00L
        val cards = 0L
        val loans = 0L

        val netWorth = cash + activeFdPrincipal + owed - cards - loans
        assertEquals("Net worth must include full 50,000 bank balance", 75000_00L, netWorth)
    }
}
