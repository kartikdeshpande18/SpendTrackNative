package com.spendtrack.app.data.local

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

object RecurringTransactionScheduler {

    /**
     * Calculates the next occurrence strictly after [currentOccurrence],
     * permanently anchored to [startDate].
     */
    fun calculateNextOccurrence(
        startDate: LocalDate,
        currentOccurrence: LocalDate,
        frequency: RecurrenceFrequency
    ): LocalDate {
        return when (frequency) {
            RecurrenceFrequency.DAILY -> {
                currentOccurrence.plusDays(1)
            }
            RecurrenceFrequency.WEEKLY -> {
                currentOccurrence.plusWeeks(1)
            }
            RecurrenceFrequency.MONTHLY -> {
                // Permanently anchored to startDate.dayOfMonth
                val startMonth = YearMonth.from(startDate)
                val currentMonth = YearMonth.from(currentOccurrence)
                var monthsAhead = ChronoUnit.MONTHS.between(startMonth, currentMonth) + 1
                var candidate = computeMonthlyOccurrence(startDate, monthsAhead)
                while (!candidate.isAfter(currentOccurrence)) {
                    monthsAhead++
                    candidate = computeMonthlyOccurrence(startDate, monthsAhead)
                }
                candidate
            }
            RecurrenceFrequency.YEARLY -> {
                // Permanently anchored to startDate.month and startDate.dayOfMonth
                var yearsAhead = (currentOccurrence.year - startDate.year).toLong() + 1
                var candidate = computeYearlyOccurrence(startDate, yearsAhead)
                while (!candidate.isAfter(currentOccurrence)) {
                    yearsAhead++
                    candidate = computeYearlyOccurrence(startDate, yearsAhead)
                }
                candidate
            }
        }
    }

    /**
     * Computes the occurrence date [monthsAhead] from [startDate],
     * clipping the day to the maximum days in that target month without permanently losing the anchor.
     * E.g., startDate = 31 Jan -> 28 Feb -> 31 Mar -> 30 Apr -> 31 May.
     */
    fun computeMonthlyOccurrence(startDate: LocalDate, monthsAhead: Long): LocalDate {
        val targetMonth = YearMonth.from(startDate).plusMonths(monthsAhead)
        val day = minOf(startDate.dayOfMonth, targetMonth.lengthOfMonth())
        return targetMonth.atDay(day)
    }

    /**
     * Computes the occurrence date [yearsAhead] from [startDate],
     * correctly handling leap years for Feb 29 without losing the Feb 29 anchor.
     * E.g., 29 Feb 2028 -> 28 Feb 2029 -> 28 Feb 2030 -> 28 Feb 2031 -> 29 Feb 2032.
     */
    fun computeYearlyOccurrence(startDate: LocalDate, yearsAhead: Long): LocalDate {
        val targetYear = startDate.year + yearsAhead.toInt()
        val isLeap = java.time.Year.of(targetYear).isLeap
        val day = if (startDate.monthValue == 2 && startDate.dayOfMonth == 29 && !isLeap) {
            28
        } else {
            startDate.dayOfMonth
        }
        return LocalDate.of(targetYear, startDate.month, day)
    }

    /**
     * Formats a concise, human-readable summary of the schedule.
     * e.g., "Repeats monthly on the 8th", "Repeats weekly on Tuesday", "Repeats daily".
     */
    fun formatRecurrenceSummary(
        frequency: RecurrenceFrequency,
        startDate: LocalDate,
        endDate: LocalDate? = null
    ): String {
        val base = when (frequency) {
            RecurrenceFrequency.DAILY -> "Repeats daily"
            RecurrenceFrequency.WEEKLY -> {
                val dayName = startDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
                "Repeats weekly on $dayName"
            }
            RecurrenceFrequency.MONTHLY -> {
                val day = startDate.dayOfMonth
                val suffix = when {
                    day in 11..13 -> "th"
                    day % 10 == 1 -> "st"
                    day % 10 == 2 -> "nd"
                    day % 10 == 3 -> "rd"
                    else -> "th"
                }
                "Repeats monthly on the $day$suffix"
            }
            RecurrenceFrequency.YEARLY -> {
                val monthName = startDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                "Repeats yearly on ${startDate.dayOfMonth} $monthName"
            }
        }
        return if (endDate != null) {
            val endMonth = endDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            "$base until ${endDate.dayOfMonth} $endMonth ${endDate.year}"
        } else {
            base
        }
    }
}
