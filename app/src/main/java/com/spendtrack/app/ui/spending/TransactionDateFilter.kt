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
    val type: DateFilterType = DateFilterType.ALL_DATES,
    val customStartDateEpochDay: Long? = null,
    val customEndDateEpochDay: Long? = null,
) {
    fun matches(dateEpochDay: Long, today: LocalDate = LocalDate.now()): Boolean {
        return when (type) {
            DateFilterType.ALL_DATES -> true
            DateFilterType.THIS_MONTH -> {
                val txDate = LocalDate.ofEpochDay(dateEpochDay)
                txDate.year == today.year && txDate.month == today.month
            }
            DateFilterType.LAST_7_DAYS -> {
                val txDate = LocalDate.ofEpochDay(dateEpochDay)
                val start = today.minusDays(6)
                !txDate.isBefore(start) && !txDate.isAfter(today)
            }
            DateFilterType.LAST_30_DAYS -> {
                val txDate = LocalDate.ofEpochDay(dateEpochDay)
                val start = today.minusDays(29)
                !txDate.isBefore(start) && !txDate.isAfter(today)
            }
            DateFilterType.CUSTOM_RANGE -> {
                val start = customStartDateEpochDay ?: return true
                val end = customEndDateEpochDay ?: return true
                dateEpochDay in start..end
            }
        }
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
            return transactions.filter { t ->
                val matchesCategory = categoryFilter == null || (t.type != TransactionType.TRANSFER && t.categoryId == categoryFilter)
                val matchesDate = dateFilter.matches(t.dateEpochDay, today)
                val matchesQuery = query.isBlank() ||
                    t.description.contains(query, ignoreCase = true) ||
                    (t.type != TransactionType.TRANSFER && categories.find { it.id == t.categoryId }?.name?.contains(query, ignoreCase = true) == true) ||
                    (t.type == TransactionType.TRANSFER && (
                        "transfer".contains(query, ignoreCase = true) ||
                        accounts.find { it.id == t.accountId }?.name?.contains(query, ignoreCase = true) == true ||
                        accounts.find { it.id == t.destinationAccountId }?.name?.contains(query, ignoreCase = true) == true
                    ))
                matchesCategory && matchesDate && matchesQuery
            }
        }

        fun filterForChart(
            transactions: List<TransactionEntity>,
            categoryFilter: Long?,
            dateFilter: TransactionDateFilter,
            today: LocalDate = LocalDate.now()
        ): List<TransactionEntity> {
            return transactions.filter { t ->
                val matchesCategory = categoryFilter == null || t.categoryId == categoryFilter
                val matchesDate = dateFilter.matches(t.dateEpochDay, today)
                matchesCategory && matchesDate
            }
        }
    }
}

