package com.spendtrack.app.data.local

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Calculates Fixed Deposit maturity amounts using the standard compound-interest
 * methodology (INDmoney FD calculator baseline).
 *
 * Formula:
 *   A = P * (1 + r / m) ^ (m * t)
 *
 * Where:
 *   - P = principal
 *   - r = annual interest rate as decimal (e.g. 7.75% = 0.0775)
 *   - m = compounding frequency per year (Monthly = 12, Quarterly = 4, Half-yearly = 2, Yearly = 1)
 *   - t = tenure in years derived from start and maturity dates
 */
object FixedDepositCalculator {

    fun calculateMaturityAmountPaise(
        principalPaise: Long,
        interestRateBps: Int,
        startDateEpochDay: Long,
        maturityDateEpochDay: Long,
        frequency: CompoundingFrequency = CompoundingFrequency.QUARTERLY
    ): Long {
        if (principalPaise <= 0L || maturityDateEpochDay <= startDateEpochDay) {
            return principalPaise.coerceAtLeast(0L)
        }
        if (interestRateBps <= 0) {
            return principalPaise
        }

        val tenureYears = calculateTenureYears(startDateEpochDay, maturityDateEpochDay)
        if (tenureYears <= 0.0) {
            return principalPaise
        }

        val r = (interestRateBps.toDouble() / 100.0) / 100.0
        val m = frequency.periodsPerYear.toDouble()

        val maturityPaise = (principalPaise.toDouble() * (1.0 + r / m).pow(m * tenureYears)).roundToLong()
        return maturityPaise.coerceAtLeast(principalPaise)
    }

    fun calculateEstimatedInterestPaise(
        principalPaise: Long,
        interestRateBps: Int,
        startDateEpochDay: Long,
        maturityDateEpochDay: Long,
        frequency: CompoundingFrequency = CompoundingFrequency.QUARTERLY
    ): Long {
        val maturity = calculateMaturityAmountPaise(
            principalPaise = principalPaise,
            interestRateBps = interestRateBps,
            startDateEpochDay = startDateEpochDay,
            maturityDateEpochDay = maturityDateEpochDay,
            frequency = frequency
        )
        return (maturity - principalPaise).coerceAtLeast(0L)
    }

    fun calculateTenureYears(
        startDateEpochDay: Long,
        maturityDateEpochDay: Long
    ): Double {
        if (maturityDateEpochDay <= startDateEpochDay) return 0.0
        val start = LocalDate.ofEpochDay(startDateEpochDay)
        val maturity = LocalDate.ofEpochDay(maturityDateEpochDay)

        val approxMonths = (maturity.year - start.year) * 12 + (maturity.monthValue - start.monthValue)
        for (m in approxMonths downTo maxOf(0, approxMonths - 1)) {
            if (m == 0) continue
            val target = start.plusMonths(m.toLong())
            if (target == maturity || target.plusDays(1) == maturity) {
                return m.toDouble() / 12.0
            }
        }

        val baseMonths = ChronoUnit.MONTHS.between(start, maturity)
        val afterBase = start.plusMonths(baseMonths)
        val remDays = ChronoUnit.DAYS.between(afterBase, maturity)

        return when {
            remDays == 0L -> baseMonths.toDouble() / 12.0
            remDays == 1L && baseMonths > 0L -> baseMonths.toDouble() / 12.0
            baseMonths == 0L -> ChronoUnit.DAYS.between(start, maturity).toDouble() / 365.0
            else -> (baseMonths.toDouble() / 12.0) + (remDays.toDouble() / 365.0)
        }
    }

    /**
     * Formats the maturity amount rounded to the nearest whole rupee with no decimals.
     * Rules:
     * - If decimal/fractional part is < ₹0.50 (paise % 100 < 50), round down.
     * - If decimal/fractional part is >= ₹0.50 (paise % 100 >= 50), round up.
     */
    fun formatMaturityAmount(maturityPaise: Long): String {
        val wholeRupees = (maxOf(0L, maturityPaise) + 50L) / 100L
        return "₹" + String.format(Locale.US, "%,d", wholeRupees)
    }
}
