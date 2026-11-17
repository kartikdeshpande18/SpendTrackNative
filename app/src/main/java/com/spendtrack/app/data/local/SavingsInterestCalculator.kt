package com.spendtrack.app.data.local

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class InterestCalculationMethod(val displayName: String) {
    DAILY("Daily balance"),
    MONTHLY_AVERAGE("Average monthly balance"),
    QUARTERLY_AVERAGE("Quarterly balance")
}

enum class InterestCreditFrequency(val displayName: String) {
    DAILY("Daily"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly")
}

data class SavingsInterestPeriod(val start: LocalDate, val end: LocalDate)

object SavingsInterestPeriods {
    private val formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)

    fun containing(date: LocalDate, frequency: InterestCreditFrequency): SavingsInterestPeriod = when (frequency) {
        InterestCreditFrequency.DAILY -> SavingsInterestPeriod(date, date)
        InterestCreditFrequency.MONTHLY -> SavingsInterestPeriod(
            date.withDayOfMonth(1), date.with(TemporalAdjusters.lastDayOfMonth())
        )
        InterestCreditFrequency.QUARTERLY -> {
            val startMonth = ((date.monthValue - 1) / 3) * 3 + 1
            val start = date.withMonth(startMonth).withDayOfMonth(1)
            SavingsInterestPeriod(start, start.plusMonths(3).minusDays(1))
        }
    }

    fun lastCompletedThrough(today: LocalDate, frequency: InterestCreditFrequency): LocalDate = when (frequency) {
        InterestCreditFrequency.DAILY -> today.minusDays(1)
        InterestCreditFrequency.MONTHLY -> today.withDayOfMonth(1).minusDays(1)
        InterestCreditFrequency.QUARTERLY -> containing(today, frequency).start.minusDays(1)
    }

    fun description(frequency: InterestCreditFrequency, period: SavingsInterestPeriod): String =
        if (frequency == InterestCreditFrequency.DAILY) "Interest for ${period.end.format(formatter)}"
        else "Interest for ${period.start.format(formatter)} - ${period.end.format(formatter)}"
}

object SavingsInterestCalculator {

    private val interestDateRegex = Regex("""(\d{2}-[A-Za-z]{3}-\d{4})""")
    private val interestDateFormatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH)

    /**
     * Extracts and parses the date for which interest was earned from transaction descriptions
     * such as "Interest for 15-Nov-2026", "Interest Cr. for 15-Nov-2026", or
     * "Daily interest cr. for 15-Sep-2026".
     */
    fun parseInterestDate(description: String): LocalDate? {
        val match = interestDateRegex.find(description) ?: return null
        return runCatching { LocalDate.parse(match.value, interestDateFormatter) }.getOrNull()
    }

    /**
     * Checks if a transaction represents an interest credit for a specific date and account,
     * matching both SpendTrack automated entries and bank statement imports (e.g. Slice).
     */
    fun isDuplicateInterest(
        tx: TransactionEntity,
        accountId: Long,
        date: LocalDate,
        frequency: InterestCreditFrequency,
        periodDescription: String
    ): Boolean {
        if (tx.accountId != accountId || tx.type != TransactionType.INCOME) return false
        if (tx.description == periodDescription) return true
        if (frequency == InterestCreditFrequency.DAILY) {
            val dateStr = date.format(interestDateFormatter)
            val descLower = tx.description.lowercase(Locale.ENGLISH)
            if (descLower.contains("interest") && descLower.contains(dateStr.lowercase(Locale.ENGLISH))) {
                return true
            }
        }
        return false
    }

    /**
     * Calculates daily interest using month-to-date cumulative sub-paisa carryover:
     * Payout_t = round(sum_{k=1}^t Accrued_k) - sum_{k=1}^{t-1} Payout_k
     *
     * Mirrors the Core Banking System (CBS) ledger used by Slice Small Finance Bank.
     * Prevents sub-paisa truncation loss (e.g. 0.60 vs 0.61 on Atom, 147p vs 148p on SFB).
     *
     * @param dailyBalances map of epochDay to balancePaise on that day
     * @param fallbackBalancePaise fallback balance if a day is absent from dailyBalances
     * @param interestRateBps annual interest rate in basis points (e.g. 550 = 5.50%)
     * @param targetEpochDay epoch day for which daily interest payout is being calculated
     * @param alreadyPaidInMonthPaise total interest already paid for prior days in this calendar month
     */
    fun calculateCumulativeDailyInterestPaise(
        dailyBalances: Map<Long, Long>,
        fallbackBalancePaise: Long,
        interestRateBps: Int,
        targetEpochDay: Long,
        alreadyPaidInMonthPaise: Long
    ): Long {
        if (interestRateBps <= 0) return 0L

        val targetDate = LocalDate.ofEpochDay(targetEpochDay)
        val monthStartDate = targetDate.withDayOfMonth(1)
        val startEpochDay = monthStartDate.toEpochDay()

        if (targetEpochDay < startEpochDay) return 0L

        val rate = BigDecimal(interestRateBps).divide(BigDecimal(10000), 12, RoundingMode.HALF_UP)
        var totalAccruedPaise = BigDecimal.ZERO

        for (epochDay in startEpochDay..targetEpochDay) {
            val balance = dailyBalances[epochDay] ?: fallbackBalancePaise
            if (balance > 0L) {
                val date = LocalDate.ofEpochDay(epochDay)
                val daysInYear = if (date.isLeapYear) 366 else 365
                val dayAccrual = BigDecimal(balance)
                    .multiply(rate)
                    .divide(BigDecimal(daysInYear), 12, RoundingMode.HALF_UP)
                totalAccruedPaise = totalAccruedPaise.add(dayAccrual)
            }
        }

        val targetCumulativePaise = totalAccruedPaise.setScale(0, RoundingMode.HALF_UP).toLong()
        return (targetCumulativePaise - alreadyPaidInMonthPaise).coerceAtLeast(0L)
    }

    /**
     * Calculates interest for an account over a specific date range.
     * @param dailyBalances map of dateEpochDay to balancePaise on that day
     * @param interestRateBps annual interest rate in basis points (100 bps = 1.00%)
     * @param method interest calculation method (DAILY, MONTHLY_AVERAGE, QUARTERLY_AVERAGE)
     * @param startEpochDay start of calculation period (inclusive)
     * @param endEpochDay end of calculation period (inclusive)
     */
    fun calculateInterestPaise(
        dailyBalances: Map<Long, Long>,
        fallbackBalancePaise: Long,
        interestRateBps: Int,
        method: InterestCalculationMethod,
        startEpochDay: Long,
        endEpochDay: Long
    ): Long {
        if (interestRateBps <= 0 || endEpochDay < startEpochDay) return 0L

        val daysCount = (endEpochDay - startEpochDay + 1).toInt()
        if (daysCount <= 0) return 0L

        val rate = BigDecimal(interestRateBps).divide(BigDecimal(10000), 8, RoundingMode.HALF_UP)
        val daysInYear = BigDecimal(365)

        return when (method) {
            InterestCalculationMethod.DAILY -> {
                var totalInterestPaise = BigDecimal.ZERO
                for (epochDay in startEpochDay..endEpochDay) {
                    val balance = dailyBalances[epochDay] ?: fallbackBalancePaise
                    if (balance > 0L) {
                    val year = LocalDate.ofEpochDay(epochDay)
                    val daysInYear = if (year.isLeapYear) 366 else 365
                    val dailyInterest = BigDecimal(balance)
                            .multiply(rate)
                        .divide(BigDecimal(daysInYear), 8, RoundingMode.HALF_UP)
                        .setScale(0, RoundingMode.HALF_UP)
                    totalInterestPaise = totalInterestPaise.add(dailyInterest)
                    }
                }
                totalInterestPaise.setScale(0, RoundingMode.HALF_UP).toLong()
            }
            InterestCalculationMethod.MONTHLY_AVERAGE,
            InterestCalculationMethod.QUARTERLY_AVERAGE -> {
                var totalBalance = BigDecimal.ZERO
                for (epochDay in startEpochDay..endEpochDay) {
                    val balance = dailyBalances[epochDay] ?: fallbackBalancePaise
                    if (balance > 0L) {
                        totalBalance = totalBalance.add(BigDecimal(balance))
                    }
                }
                val averageBalance = totalBalance.divide(BigDecimal(daysCount), 8, RoundingMode.HALF_UP)
                val periodInterest = averageBalance
                    .multiply(rate)
                    .multiply(BigDecimal(daysCount))
                    .divide(daysInYear, 8, RoundingMode.HALF_UP)
                periodInterest.setScale(0, RoundingMode.HALF_UP).toLong()
            }
        }
    }

    /**
     * Reconstructs daily end-of-day balances between [startEpochDay] and [endEpochDay].
     * Given the [currentBalancePaise] as of [todayEpochDay] and all transactions on the account.
     */
    fun reconstructDailyBalances(
        currentBalancePaise: Long,
        todayEpochDay: Long,
        accountTransactions: List<TransactionEntity>,
        startEpochDay: Long,
        endEpochDay: Long
    ): Map<Long, Long> {
        val result = mutableMapOf<Long, Long>()
        if (endEpochDay < startEpochDay) return result

        // Net change on each epoch day
        val netChangeByDay = mutableMapOf<Long, Long>()
        for (tx in accountTransactions) {
            val delta = when (tx.type) {
                TransactionType.INCOME -> tx.amountPaise
                TransactionType.EXPENSE -> -tx.amountPaise
                TransactionType.TRANSFER -> {
                    if (tx.destinationAccountId == tx.accountId) 0L
                    else -tx.amountPaise
                }
            }
            netChangeByDay[tx.dateEpochDay] = (netChangeByDay[tx.dateEpochDay] ?: 0L) + delta
        }

        // Reconstruct backwards from today's balance
        var runningBalance = currentBalancePaise
        for (day in todayEpochDay downTo startEpochDay) {
            if (day in startEpochDay..endEpochDay) {
                result[day] = runningBalance.coerceAtLeast(0L)
            }
            val dayChange = netChangeByDay[day] ?: 0L
            runningBalance -= dayChange
        }

        return result
    }
}
