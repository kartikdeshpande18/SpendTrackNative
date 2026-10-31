package com.spendtrack.app.ui.spending

import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class DateFilterType {
    ALL_DATES,
    THIS_MONTH,
    LAST_7_DAYS,
    LAST_30_DAYS,
    CUSTOM_RANGE
}

data class TransactionDateFilter(
    val type: DateFilterType = DateFilterType.THIS_MONTH,
    val customStartDateEpochDay: Long? = null,
    val customEndDateEpochDay: Long? = null,
) {
    fun dateRangeEpochDays(today: LocalDate = LocalDate.now()): LongRange? {
        return when (type) {
            DateFilterType.ALL_DATES -> null
            DateFilterType.THIS_MONTH -> {
                val start = today.withDayOfMonth(1).toEpochDay()
                val end = today.withDayOfMonth(today.lengthOfMonth()).toEpochDay()
                start..end
            }
            DateFilterType.LAST_7_DAYS -> {
                val start = today.minusDays(6).toEpochDay()
                val end = today.toEpochDay()
                start..end
            }
            DateFilterType.LAST_30_DAYS -> {
                val start = today.minusDays(29).toEpochDay()
                val end = today.toEpochDay()
                start..end
            }
            DateFilterType.CUSTOM_RANGE -> {
                val start = customStartDateEpochDay ?: return null
                val end = customEndDateEpochDay ?: return null
                start..end
            }
        }
    }

    fun matches(dateEpochDay: Long, today: LocalDate = LocalDate.now()): Boolean {
        val range = dateRangeEpochDays(today) ?: return when (type) {
            DateFilterType.ALL_DATES -> true
            DateFilterType.CUSTOM_RANGE -> {
                val start = customStartDateEpochDay
                val end = customEndDateEpochDay
                if (start == null && end == null) true
                else if (start != null && end == null) dateEpochDay >= start
                else if (start == null && end != null) dateEpochDay <= end
                else dateEpochDay in start!!..end!!
            }
            else -> true
        }
        return dateEpochDay in range
    }

    fun label(today: LocalDate = LocalDate.now()): String {
        return when (type) {
            DateFilterType.ALL_DATES -> "All Dates"
            DateFilterType.THIS_MONTH -> "This Month"
            DateFilterType.LAST_7_DAYS -> "Last 7 Days"
            DateFilterType.LAST_30_DAYS -> "Last 30 Days"
            DateFilterType.CUSTOM_RANGE -> {
                val start = customStartDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                val end = customEndDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                if (start != null && end != null) {
                    val startStr = "${start.dayOfMonth} ${start.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
                    val endStr = "${end.dayOfMonth} ${end.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
                    "$startStr – $endStr"
                } else {
                    "Custom Range"
                }
            }
        }
    }

    companion object {
        fun isValidCustomRange(startEpochDay: Long?, endEpochDay: Long?): Boolean {
            return startEpochDay != null && endEpochDay != null && startEpochDay <= endEpochDay
        }

        fun filterTransactions(
            transactions: List<TransactionEntity>,
            categoryFilter: Long?,
            dateFilter: TransactionDateFilter,
            query: String = "",
            categories: List<CategoryEntity> = emptyList(),
            accounts: List<AccountEntity> = emptyList(),
            today: LocalDate = LocalDate.now()
        ): List<TransactionEntity> {
            val range = dateFilter.dateRangeEpochDays(today)
            val isCustomRange = dateFilter.type == DateFilterType.CUSTOM_RANGE
            val trimmedQuery = query.trim()
            val hasQuery = trimmedQuery.isNotEmpty()
            val categoryMap = if (hasQuery) categories.associateBy { it.id } else null
            val accountMap = if (hasQuery) accounts.associateBy { it.id } else null

            return transactions.filter { t ->
                val matchesCategory = categoryFilter == null || (t.type != TransactionType.TRANSFER && t.categoryId == categoryFilter)
                if (!matchesCategory) return@filter false

                val matchesDate = when {
                    range != null -> t.dateEpochDay in range
                    isCustomRange -> dateFilter.matches(t.dateEpochDay, today)
                    else -> true
                }
                if (!matchesDate) return@filter false

                if (!hasQuery) return@filter true

                t.description.contains(trimmedQuery, ignoreCase = true) ||
                    (t.type != TransactionType.TRANSFER && categoryMap?.get(t.categoryId)?.name?.contains(trimmedQuery, ignoreCase = true) == true) ||
                    (t.type == TransactionType.TRANSFER && (
                        "transfer".contains(trimmedQuery, ignoreCase = true) ||
                        accountMap?.get(t.accountId)?.name?.contains(trimmedQuery, ignoreCase = true) == true ||
                        accountMap?.get(t.destinationAccountId)?.name?.contains(trimmedQuery, ignoreCase = true) == true
                    ))
            }
        }

        fun filterForChart(
            transactions: List<TransactionEntity>,
            categoryFilter: Long?,
            dateFilter: TransactionDateFilter,
            today: LocalDate = LocalDate.now()
        ): List<TransactionEntity> {
            val range = dateFilter.dateRangeEpochDays(today)
            val isCustomRange = dateFilter.type == DateFilterType.CUSTOM_RANGE
            return transactions.filter { t ->
                if (t.isExcludedFromCalculations) return@filter false
                val matchesCategory = categoryFilter == null || t.categoryId == categoryFilter
                if (!matchesCategory) return@filter false
                when {
                    range != null -> t.dateEpochDay in range
                    isCustomRange -> dateFilter.matches(t.dateEpochDay, today)
                    else -> true
                }
            }
        }
    }
}

