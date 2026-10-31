package com.spendtrack.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        LoanEntity::class,
        OwedEntity::class,
        CardPaymentEntity::class,
        FixedDepositEntity::class,
        RecurringTransactionEntity::class,
        RecurringOccurrenceEntity::class,
        ImportHistoryEntity::class,
        PersonEntity::class
    ],
    version = 12,
    exportSchema = false,
)
@TypeConverters(SpendTrackConverters::class)
abstract class SpendTrackDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun categories(): CategoryDao
    abstract fun transactions(): TransactionDao
    abstract fun budgets(): BudgetDao
    abstract fun loans(): LoanDao
    abstract fun owed(): OwedDao
    abstract fun cardPayments(): CardPaymentDao
    abstract fun fixedDeposits(): FixedDepositDao
    abstract fun recurringTransactions(): RecurringTransactionDao
    abstract fun recurringOccurrences(): RecurringOccurrenceDao
    abstract fun importHistory(): ImportHistoryDao
    abstract fun people(): PersonDao
}
