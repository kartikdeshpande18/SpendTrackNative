package com.spendtrack.app.importer.reconciliation

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.NormalizedTransaction

object Reconciler {
    fun reconcile(transactions: List<NormalizedTransaction>): ReconciliationResult {
        // Assume transactions are sorted by date or naturally ordered from the parser.
        // For reconciliation, we just check if the sequence of balances is consistent.
        
        var failures = 0
        var expectedBalance: Long? = null
        
        for (tx in transactions) {
            val balance = tx.balanceAfterPaise
            if (balance != null) {
                if (expectedBalance != null) {
                    val actualChange = if (tx.direction == Direction.CREDIT) tx.amountPaise else -tx.amountPaise
                    val expectedNew = expectedBalance + actualChange
                    if (expectedNew != balance) {
                        failures++
                    }
                }
                expectedBalance = balance
            }
        }
        
        return ReconciliationResult(
            isSuccessful = failures == 0,
            failureCount = failures
        )
    }
}

data class ReconciliationResult(
    val isSuccessful: Boolean,
    val failureCount: Int
)
