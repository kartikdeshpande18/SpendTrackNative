package com.spendtrack.app.importer.matching

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import kotlin.math.abs

object TransferMatcher {
    fun matchTransfers(transactions: List<NormalizedTransaction>): List<NormalizedTransaction> {
        val result = transactions.toMutableList()
        val processedIndices = mutableSetOf<Int>()

        for (i in result.indices) {
            if (i in processedIndices) continue
            val tx1 = result[i]

            // Try to find a matching transaction in the opposite direction
            val matchIndex = result.indices.firstOrNull { j ->
                j != i && j !in processedIndices &&
                        result[j].amountPaise == tx1.amountPaise &&
                        result[j].direction != tx1.direction &&
                        abs(result[j].dateEpochDay - tx1.dateEpochDay) <= 2
            }

            if (matchIndex != null) {
                val tx2 = result[matchIndex]

                val debitTx = if (tx1.direction == Direction.DEBIT) tx1 else tx2
                val creditTx = if (tx1.direction == Direction.CREDIT) tx1 else tx2

                // Transform the debitTx into a TRANSFER
                val matchedTransfer = debitTx.copy(
                    transactionType = ImporterTransactionType.TRANSFER,
                    destinationAccountId = creditTx.sourceAccountId
                )

                // Mark creditTx as duplicate or excluded so we don't import it twice
                val excludedCredit = creditTx.copy(
                    status = com.spendtrack.app.importer.model.ImportStatus.EXCLUDED
                )

                result[if (tx1.direction == Direction.DEBIT) i else matchIndex] = matchedTransfer
                result[if (tx1.direction == Direction.CREDIT) i else matchIndex] = excludedCredit
                
                processedIndices.add(i)
                processedIndices.add(matchIndex)
            }
        }
        return result
    }
}
