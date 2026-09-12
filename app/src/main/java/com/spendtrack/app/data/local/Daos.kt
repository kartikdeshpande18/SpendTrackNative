package com.spendtrack.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isCreditCard ASC, name ASC")
    fun observeAll(): Flow<List<AccountEntity>>
    @Insert suspend fun insert(entity: AccountEntity): Long
    @Insert suspend fun insertAll(entities: List<AccountEntity>)
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun getById(id: Long): AccountEntity?
    @Update suspend fun update(entity: AccountEntity)
    @Delete suspend fun delete(entity: AccountEntity)
    @Query("DELETE FROM accounts") suspend fun clear()
    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR destinationAccountId = :accountId") suspend fun transactionCount(accountId: Long): Int
    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE accountId = :accountId OR destinationAccountId = :accountId") suspend fun recurringCount(accountId: Long): Int
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isSystemFallback DESC, name ASC")
    fun observeAll(): Flow<List<CategoryEntity>>
    @Query("SELECT COUNT(*) FROM categories") suspend fun count(): Int
    @Insert suspend fun insert(entity: CategoryEntity): Long
    @Insert suspend fun insertAll(entities: List<CategoryEntity>)
    @Update suspend fun update(entity: CategoryEntity)
    @Delete suspend fun delete(entity: CategoryEntity)
    @Query("DELETE FROM categories") suspend fun clear()
    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId") suspend fun transactionCount(categoryId: Long): Int
    @Query("SELECT COUNT(*) FROM budgets WHERE categoryId = :categoryId") suspend fun budgetCount(categoryId: Long): Int
    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE categoryId = :categoryId") suspend fun recurringCount(categoryId: Long): Int
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<TransactionEntity>>
    @Insert suspend fun insert(entity: TransactionEntity): Long
    @Insert suspend fun insertAll(entities: List<TransactionEntity>)
    @Update suspend fun update(entity: TransactionEntity)
    @Delete suspend fun delete(entity: TransactionEntity)
    @Query("DELETE FROM transactions") suspend fun clear()
    @Query("SELECT * FROM transactions WHERE id = :id") suspend fun getById(id: Long): TransactionEntity?
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY monthKey DESC")
    fun observeAll(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM budgets WHERE monthKey = :monthKey")
    suspend fun forMonth(monthKey: String): List<BudgetEntity>
    @Query("SELECT DISTINCT monthKey FROM budgets WHERE monthKey < :monthKey ORDER BY monthKey DESC LIMIT 1")
    suspend fun latestMonthBefore(monthKey: String): String?
    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND monthKey = :monthKey LIMIT 1")
    suspend fun find(categoryId: Long, monthKey: String): BudgetEntity?
    @Insert suspend fun insert(entity: BudgetEntity): Long
    @Insert suspend fun insertAll(entities: List<BudgetEntity>)
    @Update suspend fun update(entity: BudgetEntity)
    @Delete suspend fun delete(entity: BudgetEntity)
    @Query("DELETE FROM budgets") suspend fun clear()
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans ORDER BY nextDueEpochDay ASC")
    fun observeAll(): Flow<List<LoanEntity>>
    @Insert suspend fun insert(entity: LoanEntity): Long
    @Insert suspend fun insertAll(entities: List<LoanEntity>)
    @Update suspend fun update(entity: LoanEntity)
    @Delete suspend fun delete(entity: LoanEntity)
    @Query("DELETE FROM loans") suspend fun clear()
}

@Dao
interface OwedDao {
    @Query("SELECT * FROM owed ORDER BY personName ASC")
    fun observeAll(): Flow<List<OwedEntity>>
    @Insert suspend fun insert(entity: OwedEntity): Long
    @Insert suspend fun insertAll(entities: List<OwedEntity>)
    @Update suspend fun update(entity: OwedEntity)
    @Delete suspend fun delete(entity: OwedEntity)
    @Query("DELETE FROM owed") suspend fun clear()
    @Query("DELETE FROM owed WHERE sourceTransactionId = :transactionId") suspend fun deleteForTransaction(transactionId: Long)
}

@Dao
interface CardPaymentDao {
    @Query("SELECT * FROM card_payments ORDER BY dateEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<CardPaymentEntity>>
    @Insert suspend fun insert(entity: CardPaymentEntity): Long
    @Insert suspend fun insertAll(entities: List<CardPaymentEntity>)
    @Query("DELETE FROM card_payments") suspend fun clear()
}

@Dao
interface FixedDepositDao {
    @Query("SELECT * FROM fixed_deposits ORDER BY status ASC, maturityDateEpochDay ASC")
    fun observeAll(): Flow<List<FixedDepositEntity>>
    @Query("SELECT * FROM fixed_deposits WHERE id = :id") suspend fun getById(id: Long): FixedDepositEntity?
    @Query("SELECT * FROM fixed_deposits WHERE status = 'ACTIVE' AND maturityDateEpochDay <= :todayEpochDay")
    suspend fun getDueActiveDeposits(todayEpochDay: Long): List<FixedDepositEntity>
    @Query("SELECT COUNT(*) FROM fixed_deposits WHERE linkedAccountId = :accountId AND status = 'ACTIVE'")
    suspend fun activeCountForAccount(accountId: Long): Int
    @Insert suspend fun insert(entity: FixedDepositEntity): Long
    @Insert suspend fun insertAll(entities: List<FixedDepositEntity>)
    @Update suspend fun update(entity: FixedDepositEntity)
    @Delete suspend fun delete(entity: FixedDepositEntity)
    @Query("DELETE FROM fixed_deposits") suspend fun clear()
}

@Dao
interface RecurringTransactionDao {
    @Query("SELECT * FROM recurring_transactions ORDER BY isEnabled DESC, nextOccurrenceEpochDay ASC, id DESC")
    fun observeAll(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id")
    suspend fun getById(id: Long): RecurringTransactionEntity?

    @Query("SELECT * FROM recurring_transactions WHERE isEnabled = 1 AND nextOccurrenceEpochDay <= :todayEpochDay ORDER BY nextOccurrenceEpochDay ASC, id ASC")
    suspend fun getDueSchedules(todayEpochDay: Long): List<RecurringTransactionEntity>

    @Query("SELECT COUNT(*) FROM recurring_transactions WHERE isEnabled = 1")
    suspend fun activeCount(): Int

    @Insert suspend fun insert(entity: RecurringTransactionEntity): Long
    @Insert suspend fun insertAll(entities: List<RecurringTransactionEntity>)
    @Update suspend fun update(entity: RecurringTransactionEntity)
    @Delete suspend fun delete(entity: RecurringTransactionEntity)
    @Query("DELETE FROM recurring_transactions") suspend fun clear()
}

@Dao
interface RecurringOccurrenceDao {
    @Query("SELECT COUNT(*) > 0 FROM recurring_occurrences WHERE scheduleId = :scheduleId AND occurrenceEpochDay = :occurrenceEpochDay")
    suspend fun hasOccurrence(scheduleId: Long, occurrenceEpochDay: Long): Boolean

    @Insert suspend fun insert(entity: RecurringOccurrenceEntity): Long
    @Insert suspend fun insertAll(entities: List<RecurringOccurrenceEntity>)
    @Query("SELECT * FROM recurring_occurrences WHERE scheduleId = :scheduleId ORDER BY occurrenceEpochDay ASC")
    suspend fun forSchedule(scheduleId: Long): List<RecurringOccurrenceEntity>
    @Query("SELECT * FROM recurring_occurrences")
    suspend fun getAll(): List<RecurringOccurrenceEntity>
    @Query("DELETE FROM recurring_occurrences WHERE scheduleId = :scheduleId")
    suspend fun deleteForSchedule(scheduleId: Long)
    @Query("DELETE FROM recurring_occurrences")
    suspend fun clear()
}
