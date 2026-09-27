package com.spendtrack.app.importer.repository

import androidx.room.withTransaction
import com.spendtrack.app.data.local.CardPaymentEntity
import com.spendtrack.app.data.local.ImportHistoryEntity
import com.spendtrack.app.data.local.SpendTrackDatabase
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImportBatch
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ImportRepository(private val db: SpendTrackDatabase) {

    suspend fun checkIsAlreadyImported(fileHash: String): Boolean = withContext(Dispatchers.IO) {
        db.importHistory().getByFileHash(fileHash) != null
    }

    /**
     * Commits ONLY accepted transactions into SpendTrack Room database within a single transaction.
     * Updates account balances, writes card payments, transfers, expenses, incomes, and records import history.
     * This is the ONLY method that permanently writes imported data to Room.
     */
    suspend fun commitAcceptedBatch(
        batch: ImportBatch,
        acceptedTransactions: List<NormalizedTransaction>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            db.withTransaction {
                for (tx in acceptedTransactions) {
                    val account = db.accounts().getById(tx.sourceAccountId)
                    val isCardPayment = tx.transactionType == ImporterTransactionType.CARD_PAYMENT ||
                            (account?.isCreditCard == true && tx.direction == Direction.CREDIT && 
                             (tx.normalizedMerchant.contains("payment", ignoreCase = true) || 
                              tx.normalizedMerchant.contains("repayment", ignoreCase = true) ||
                              tx.rawNarration.contains("payment", ignoreCase = true) ||
                              tx.rawNarration.contains("repayment", ignoreCase = true) ||
                              tx.rawNarration.contains("auto debit", ignoreCase = true)))

                    when {
                        isCardPayment -> {
                            val cardAccountId = tx.destinationAccountId ?: tx.sourceAccountId
                            val sourceAccountId = tx.sourceAccountId
                            val cardPayment = CardPaymentEntity(
                                cardAccountId = cardAccountId,
                                sourceAccountId = sourceAccountId,
                                amountPaise = tx.amountPaise,
                                dateEpochDay = tx.dateEpochDay
                            )
                            db.cardPayments().insert(cardPayment)

                            // Update balances: card dues decrease, source balance decreases (if distinct bank account)
                            val card = db.accounts().getById(cardAccountId)
                            if (card != null) {
                                db.accounts().update(card.copy(balancePaise = card.balancePaise - tx.amountPaise))
                            }
                            if (sourceAccountId != cardAccountId) {
                                val source = db.accounts().getById(sourceAccountId)
                                if (source != null && !source.isCreditCard) {
                                    db.accounts().update(source.copy(balancePaise = source.balancePaise - tx.amountPaise))
                                }
                            }
                        }
                        tx.transactionType == ImporterTransactionType.TRANSFER -> {
                            val destAccountId = tx.destinationAccountId
                            val transactionEntity = TransactionEntity(
                                dateEpochDay = tx.dateEpochDay,
                                description = tx.normalizedMerchant,
                                amountPaise = tx.amountPaise,
                                categoryId = tx.categoryId ?: 0L,
                                accountId = tx.sourceAccountId,
                                type = TransactionType.TRANSFER,
                                destinationAccountId = destAccountId
                            )
                            db.transactions().insert(transactionEntity)

                            val source = db.accounts().getById(tx.sourceAccountId)
                            if (source != null) {
                                val srcDelta = when {
                                    source.isCreditCard && tx.direction == Direction.DEBIT -> tx.amountPaise
                                    source.isCreditCard -> -tx.amountPaise
                                    tx.direction == Direction.DEBIT -> -tx.amountPaise
                                    else -> tx.amountPaise
                                }
                                db.accounts().update(source.copy(balancePaise = source.balancePaise + srcDelta))
                            }
                            if (destAccountId != null) {
                                val dest = db.accounts().getById(destAccountId)
                                if (dest != null) {
                                    val dstDelta = if (dest.isCreditCard) -tx.amountPaise else tx.amountPaise
                                    db.accounts().update(dest.copy(balancePaise = dest.balancePaise + dstDelta))
                                }
                            }
                        }
                        else -> {
                            val type = when {
                                account?.isCreditCard != true && tx.direction == Direction.CREDIT -> TransactionType.INCOME
                                tx.transactionType == ImporterTransactionType.INCOME ||
                                tx.transactionType == ImporterTransactionType.REFUND ||
                                tx.transactionType == ImporterTransactionType.INTEREST -> TransactionType.INCOME
                                else -> TransactionType.EXPENSE
                            }

                            val transactionEntity = TransactionEntity(
                                dateEpochDay = tx.dateEpochDay,
                                description = tx.normalizedMerchant,
                                amountPaise = tx.amountPaise,
                                categoryId = tx.categoryId ?: 0L,
                                accountId = tx.sourceAccountId,
                                type = type,
                                destinationAccountId = tx.destinationAccountId
                            )
                            db.transactions().insert(transactionEntity)

                            if (account != null) {
                                val delta = when {
                                    account.isCreditCard && type == TransactionType.EXPENSE -> tx.amountPaise
                                    account.isCreditCard -> -tx.amountPaise
                                    type == TransactionType.EXPENSE -> -tx.amountPaise
                                    else -> tx.amountPaise
                                }
                                db.accounts().update(account.copy(balancePaise = account.balancePaise + delta))
                            }
                        }
                    }
                }

                // Insert import history for each statement file in the batch
                for (file in batch.files) {
                    val fileAcceptedCount = acceptedTransactions.count { it.sourceFilename == file.filename }
                    val fileTotalCount = batch.transactions.count { it.sourceFilename == file.filename }
                    val duplicatesSkipped = fileTotalCount - fileAcceptedCount
                    val accountId = batch.accountMappings[file.filename] ?: 0L

                    val history = ImportHistoryEntity(
                        filename = file.filename,
                        fileHash = file.fileHash,
                        format = file.format.name,
                        accountId = accountId,
                        importTimestampEpochMs = System.currentTimeMillis(),
                        transactionsImported = fileAcceptedCount,
                        duplicatesSkipped = duplicatesSkipped
                    )
                    db.importHistory().insert(history)
                }
            }
            Result.success(acceptedTransactions.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
