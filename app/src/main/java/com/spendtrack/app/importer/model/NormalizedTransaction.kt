package com.spendtrack.app.importer.model

import java.util.UUID

data class NormalizedTransaction(
    val dateEpochDay: Long,
    val amountPaise: Long,
    val direction: Direction,
    val rawNarration: String,
    val normalizedMerchant: String,
    val referenceNumber: String?,
    val transactionType: ImporterTransactionType,
    val sourceAccountId: Long,
    val destinationAccountId: Long? = null,
    val balanceAfterPaise: Long? = null,
    val sourceFilename: String,
    val parserIdentifier: String,
    val duplicateFingerprint: String,
    val status: ImportStatus = ImportStatus.NEW,
    val categoryId: Long? = null,
    val id: String = UUID.randomUUID().toString(),
    val reviewStatus: ReviewStatus = ReviewStatus.PENDING,
    val isEdited: Boolean = false,
    val existingMatchDescription: String? = null,
    val existingMatchAmountPaise: Long? = null,
    val existingMatchDateEpochDay: Long? = null,
) {
    val isAccepted: Boolean
        get() = reviewStatus == ReviewStatus.ACCEPTED || reviewStatus == ReviewStatus.EDITED
}

enum class Direction { DEBIT, CREDIT }
typealias ImporterDirection = Direction

enum class ImporterTransactionType {
    EXPENSE,
    INCOME,
    TRANSFER,
    CARD_PAYMENT,
    REFUND,
    INVESTMENT,
    INTEREST,
    FEE,
    CASH_WITHDRAWAL,
    UNKNOWN
}

enum class ImportStatus {
    NEW,
    DUPLICATE,
    POSSIBLE_DUPLICATE,
    NEEDS_REVIEW,
    EXCLUDED
}

enum class ReviewStatus {
    PENDING,
    ACCEPTED,
    REMOVED,
    EDITED
}
