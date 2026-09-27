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
        return imported.map { newTx ->
            // Look for matching date, amount, account
            val exactMatch = existingTransactions.any { existing ->
                existing.dateEpochDay == newTx.dateEpochDay &&
                        existing.amountPaise == newTx.amountPaise &&
                        existing.accountId == newTx.sourceAccountId
            }

            // Fuzzy match (within 1 day)
            val fuzzyMatch = existingTransactions.any { existing ->
                abs(existing.dateEpochDay - newTx.dateEpochDay) <= 1 &&
                        existing.amountPaise == newTx.amountPaise &&
                        existing.accountId == newTx.sourceAccountId
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
