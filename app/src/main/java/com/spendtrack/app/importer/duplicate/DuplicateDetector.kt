package com.spendtrack.app.importer.duplicate

import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.importer.model.ImportStatus
import com.spendtrack.app.importer.model.NormalizedTransaction
import kotlin.math.abs

object DuplicateDetector {
    fun flagDuplicates(
        imported: List<NormalizedTransaction>,
        existingTransactions: List<TransactionEntity>
    ): List<NormalizedTransaction> {
        val byAccount = existingTransactions.groupBy { it.accountId }
        return imported.map { newTx ->
            val candidates = byAccount[newTx.sourceAccountId] ?: emptyList()
            var exactMatch = false
            var fuzzyMatch = false

            for (existing in candidates) {
                if (existing.amountPaise == newTx.amountPaise) {
                    val dayDiff = abs(existing.dateEpochDay - newTx.dateEpochDay)
                    if (dayDiff == 0L) {
                        exactMatch = true
                        break
                    } else if (dayDiff == 1L) {
                        fuzzyMatch = true
                    }
                }
            }

            val status = when {
                exactMatch -> ImportStatus.DUPLICATE
                fuzzyMatch -> ImportStatus.POSSIBLE_DUPLICATE
                else -> newTx.status
            }

            newTx.copy(status = status)
        }
    }
}
