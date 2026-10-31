package com.spendtrack.app.data.local

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth

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

object SavingsInterestCalculator {

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
