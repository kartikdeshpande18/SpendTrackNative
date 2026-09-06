package com.spendtrack.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Money is stored in minor units (paise) to avoid floating-point accounting errors. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bank: String = "",
    val maskedNumber: String = "",
    val balancePaise: Long = 0,
    val isCreditCard: Boolean = false,
    val creditLimitPaise: Long? = null,
    val statementCycleDay: Int? = null,
    val dueDay: Int? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isIncomeCategory: Boolean = false,
    val isSystemFallback: Boolean = false,
)

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val description: String,
    val amountPaise: Long,
    val categoryId: Long,
    val accountId: Long,
    val type: TransactionType = TransactionType.EXPENSE,
    val isShared: Boolean = false,
    val mySharePaise: Long? = null,
    val sharedWith: String = "",
)

enum class TransactionType { EXPENSE, INCOME }

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val monthKey: String,
    val limitPaise: Long,
)

@Entity(tableName = "loans")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val originalPrincipalPaise: Long,
    val currentOutstandingPaise: Long,
    val interestRateBps: Int,
    val emiPaise: Long,
    val nextDueEpochDay: Long,
    val remainingTenureMonths: Int,
    val principalPaidPaise: Long,
    val interestPaidPaise: Long,
    val includeInNetWorth: Boolean = true,
)

@Entity(tableName = "owed")
data class OwedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val amountPaise: Long,
    val direction: OwedDirection,
    val note: String = "",
    val sourceTransactionId: Long? = null,
)

enum class OwedDirection { OWED_TO_ME, I_OWE }

/** A transfer, intentionally separate from transactions so it never becomes spending or income. */
@Entity(tableName = "card_payments")
data class CardPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardAccountId: Long,
    val sourceAccountId: Long,
    val amountPaise: Long,
    val dateEpochDay: Long,
)
