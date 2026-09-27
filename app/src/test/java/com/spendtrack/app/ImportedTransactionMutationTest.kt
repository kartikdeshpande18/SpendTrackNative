package com.spendtrack.app

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.spendtrack.app.data.local.*
import com.spendtrack.app.data.repository.FinanceRepository
import com.spendtrack.app.importer.model.*
import com.spendtrack.app.importer.repository.ImportRepository
import com.spendtrack.app.ui.dashboard.TopBudgetsCalculator
import com.spendtrack.app.ui.spending.DateFilterType
import com.spendtrack.app.ui.spending.TransactionDateFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.Executor

class ImportedTransactionMutationTest {

    private lateinit var fakeDb: MutationTestSpendTrackDatabase
    private lateinit var financeRepo: FinanceRepository
    private lateinit var importRepo: ImportRepository

    @Before
    fun setUp() {
        fakeDb = MutationTestSpendTrackDatabase()
        financeRepo = FinanceRepository(fakeDb)
        importRepo = ImportRepository(fakeDb)

        // Seed accounts
        fakeDb.accountsList.add(
            AccountEntity(id = 1L, name = "ICICI Savings", bank = "ICICI", balancePaise = 100000L, isCreditCard = false)
        )
        fakeDb.accountsList.add(
            AccountEntity(id = 2L, name = "HDFC Savings", bank = "HDFC", balancePaise = 200000L, isCreditCard = false)
        )
        fakeDb.accountsList.add(
            AccountEntity(id = 3L, name = "ICICI Coral Card", bank = "ICICI", balancePaise = 50000L, isCreditCard = true)
        )

        // Seed categories
        fakeDb.categoriesList.add(CategoryEntity(id = 1L, name = "Food", isIncomeCategory = false))
        fakeDb.categoriesList.add(CategoryEntity(id = 2L, name = "Transport", isIncomeCategory = false))
        fakeDb.categoriesList.add(CategoryEntity(id = 3L, name = "Shopping", isIncomeCategory = false))
        fakeDb.categoriesList.add(CategoryEntity(id = 4L, name = "Other", isIncomeCategory = false, isSystemFallback = true))
        fakeDb.categoriesList.add(CategoryEntity(id = 5L, name = "Salary", isIncomeCategory = true))
        fakeDb.categoriesList.add(CategoryEntity(id = 6L, name = "Other income", isIncomeCategory = true))
    }

    private suspend fun importSampleTransaction(
        dateEpochDay: Long = LocalDate.of(2026, 5, 15).toEpochDay(),
        merchant: String = "SWIGGY IN",
        amountPaise: Long = 16600L,
        direction: Direction = Direction.DEBIT,
        type: ImporterTransactionType = ImporterTransactionType.EXPENSE,
        sourceAccountId: Long = 1L,
        categoryId: Long? = 1L,
        destinationAccountId: Long? = null
    ): Long {
        val tx = NormalizedTransaction(
            dateEpochDay = dateEpochDay,
            amountPaise = amountPaise,
            direction = direction,
            rawNarration = merchant,
            normalizedMerchant = merchant,
            referenceNumber = null,
            transactionType = type,
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            sourceFilename = "statement.pdf",
            parserIdentifier = "test_parser",
            duplicateFingerprint = "fingerprint_${System.nanoTime()}",
            categoryId = categoryId,
            reviewStatus = ReviewStatus.ACCEPTED
        )
        val fileEntry = StatementFileEntry(
            uri = null,
            filename = "statement.pdf",
            format = com.spendtrack.app.importer.detector.StatementFormat.ICICI_SAVINGS,
            fileHash = "hash1"
        )
        val batch = ImportBatch(
            files = listOf(fileEntry),
            accountMappings = mapOf("statement.pdf" to sourceAccountId),
            transactions = listOf(tx)
        )
        val result = importRepo.commitAcceptedBatch(batch, listOf(tx))
        assertTrue("Import must succeed", result.isSuccess)
        return fakeDb.transactionsList.last().id
    }

    // -------------------------------------------------------------------------
    // TEST A: Import a transaction -> edit merchant -> save -> verify persisted
    // -------------------------------------------------------------------------
    @Test
    fun testA_importTransaction_editMerchant_persisted() = runBlocking {
        val txId = importSampleTransaction(merchant = "SWIGGY IN")
        val original = fakeDb.transactions().getById(txId)
        assertNotNull(original)
        assertEquals("SWIGGY IN", original!!.description)

        val updated = original.copy(description = "ZOMATO RESTAURANT")
        financeRepo.updateTransaction(updated)

        val persisted = fakeDb.transactions().getById(txId)
        assertNotNull(persisted)
        assertEquals("ZOMATO RESTAURANT", persisted!!.description)
        assertEquals(original.amountPaise, persisted.amountPaise)
    }

    // -------------------------------------------------------------------------
    // TEST B: Import a transaction -> edit amount -> save -> verify persisted & balance updated
    // -------------------------------------------------------------------------
    @Test
    fun testB_importTransaction_editAmount_persisted() = runBlocking {
        // Initial Account 1 balance = 100,000. After importing 16,600 expense: 100,000 - 16,600 = 83,400.
        val txId = importSampleTransaction(amountPaise = 16600L)
        val accAfterImport = fakeDb.accounts().getById(1L)!!
        assertEquals(83400L, accAfterImport.balancePaise)

        // Edit amount to 25,000
        val original = fakeDb.transactions().getById(txId)!!
        financeRepo.updateTransaction(original.copy(amountPaise = 25000L))

        val persisted = fakeDb.transactions().getById(txId)!!
        assertEquals(25000L, persisted.amountPaise)

        // Account balance should now reflect 100,000 - 25,000 = 75,000
        val accAfterEdit = fakeDb.accounts().getById(1L)!!
        assertEquals(75000L, accAfterEdit.balancePaise)
    }

    // -------------------------------------------------------------------------
    // TEST C: Import a transaction -> edit category -> save -> verify persisted
    // -------------------------------------------------------------------------
    @Test
    fun testC_importTransaction_editCategory_persisted() = runBlocking {
        val txId = importSampleTransaction(categoryId = 1L) // Food
        val original = fakeDb.transactions().getById(txId)!!
        assertEquals(1L, original.categoryId)

        financeRepo.updateTransaction(original.copy(categoryId = 3L)) // Shopping

        val persisted = fakeDb.transactions().getById(txId)!!
        assertEquals(3L, persisted.categoryId)
    }

    // -------------------------------------------------------------------------
    // TEST D: Import a transaction -> edit account -> save -> verify persisted & balances updated
    // -------------------------------------------------------------------------
    @Test
    fun testD_importTransaction_editAccount_persisted() = runBlocking {
        // Imported on Account 1 (ICICI Savings, initial 100,000)
        val txId = importSampleTransaction(amountPaise = 20000L, sourceAccountId = 1L)
        assertEquals(80000L, fakeDb.accounts().getById(1L)!!.balancePaise)
        assertEquals(200000L, fakeDb.accounts().getById(2L)!!.balancePaise)

        // Move to Account 2 (HDFC Savings, initial 200,000)
        val original = fakeDb.transactions().getById(txId)!!
        financeRepo.updateTransaction(original.copy(accountId = 2L))

        val persisted = fakeDb.transactions().getById(txId)!!
        assertEquals(2L, persisted.accountId)

        // Account 1 restored to 100,000, Account 2 debited to 180,000
        assertEquals(100000L, fakeDb.accounts().getById(1L)!!.balancePaise)
        assertEquals(180000L, fakeDb.accounts().getById(2L)!!.balancePaise)
    }

    // -------------------------------------------------------------------------
    // TEST E: Import a transaction -> edit date -> save -> verify persisted
    // -------------------------------------------------------------------------
    @Test
    fun testE_importTransaction_editDate_persisted() = runBlocking {
        val initialDate = LocalDate.of(2026, 5, 15).toEpochDay()
        val newDate = LocalDate.of(2026, 5, 20).toEpochDay()
        val txId = importSampleTransaction(dateEpochDay = initialDate)

        val original = fakeDb.transactions().getById(txId)!!
        financeRepo.updateTransaction(original.copy(dateEpochDay = newDate))

        val persisted = fakeDb.transactions().getById(txId)!!
        assertEquals(newDate, persisted.dateEpochDay)
    }

    // -------------------------------------------------------------------------
    // TEST F: Import a transaction -> delete -> verify DAO contains zero matching transaction
    // -------------------------------------------------------------------------
    @Test
    fun testF_importTransaction_delete_persisted() = runBlocking {
        // Initial Account 1 balance = 100,000
        val txId = importSampleTransaction(amountPaise = 16600L, sourceAccountId = 1L)
        assertEquals(83400L, fakeDb.accounts().getById(1L)!!.balancePaise)
        assertEquals(1, fakeDb.transactionsList.size)

        financeRepo.deleteTransaction(txId)

        assertEquals("Transaction must be deleted from Room", 0, fakeDb.transactionsList.size)
        assertNull("getById must return null", fakeDb.transactions().getById(txId))
        // Account balance must be restored
        assertEquals(100000L, fakeDb.accounts().getById(1L)!!.balancePaise)
    }

    // -------------------------------------------------------------------------
    // TEST G: Import multiple transactions -> edit one -> delete another -> verify
    // -------------------------------------------------------------------------
    @Test
    fun testG_importMultiple_editOne_deleteAnother() = runBlocking {
        val tx1Id = importSampleTransaction(merchant = "Merchant 1", amountPaise = 10000L)
        val tx2Id = importSampleTransaction(merchant = "Merchant 2", amountPaise = 20000L)
        val tx3Id = importSampleTransaction(merchant = "Merchant 3", amountPaise = 30000L)
        assertEquals(3, fakeDb.transactionsList.size)

        // Edit tx1
        val tx1 = fakeDb.transactions().getById(tx1Id)!!
        financeRepo.updateTransaction(tx1.copy(description = "Merchant 1 Edited"))

        // Delete tx2
        financeRepo.deleteTransaction(tx2Id)

        // Verify tx1 remains and is edited
        val persisted1 = fakeDb.transactions().getById(tx1Id)
        assertNotNull(persisted1)
        assertEquals("Merchant 1 Edited", persisted1!!.description)

        // Verify tx2 is absent
        val persisted2 = fakeDb.transactions().getById(tx2Id)
        assertNull(persisted2)

        // Verify tx3 remains unchanged
        val persisted3 = fakeDb.transactions().getById(tx3Id)
        assertNotNull(persisted3)
        assertEquals("Merchant 3", persisted3!!.description)
        assertEquals(30000L, persisted3.amountPaise)

        assertEquals(2, fakeDb.transactionsList.size)
    }

    // -------------------------------------------------------------------------
    // TEST H: Perform edit -> delete -> edit on different imported transactions sequentially
    // -------------------------------------------------------------------------
    @Test
    fun testH_sequentialEditDeleteEdit() = runBlocking {
        val tx1Id = importSampleTransaction(merchant = "Seq 1", amountPaise = 5000L)
        val tx2Id = importSampleTransaction(merchant = "Seq 2", amountPaise = 10000L)
        val tx3Id = importSampleTransaction(merchant = "Seq 3", amountPaise = 15000L)

        // 1. Edit tx1
        val tx1 = fakeDb.transactions().getById(tx1Id)!!
        financeRepo.updateTransaction(tx1.copy(amountPaise = 7500L))

        // 2. Delete tx2
        financeRepo.deleteTransaction(tx2Id)

        // 3. Edit tx3
        val tx3 = fakeDb.transactions().getById(tx3Id)!!
        financeRepo.updateTransaction(tx3.copy(description = "Seq 3 Updated"))

        assertEquals(2, fakeDb.transactionsList.size)
        assertEquals(7500L, fakeDb.transactions().getById(tx1Id)!!.amountPaise)
        assertNull(fakeDb.transactions().getById(tx2Id))
        assertEquals("Seq 3 Updated", fakeDb.transactions().getById(tx3Id)!!.description)
    }

    // -------------------------------------------------------------------------
    // TEST I: Verify dashboard/transaction lists refresh correctly after update & delete
    // -------------------------------------------------------------------------
    @Test
    fun testI_dashboardAndListsRefreshCorrectly() = runBlocking {
        val mayMonth = YearMonth.of(2026, 5)
        val may15 = LocalDate.of(2026, 5, 15).toEpochDay()
        val tx1Id = importSampleTransaction(dateEpochDay = may15, merchant = "Food item", amountPaise = 10000L, categoryId = 1L)
        val tx2Id = importSampleTransaction(dateEpochDay = may15, merchant = "Transport item", amountPaise = 5000L, categoryId = 2L)

        // Initial filter
        val filter = TransactionDateFilter(type = DateFilterType.ALL_DATES)
        val list1 = TransactionDateFilter.filterTransactions(
            transactions = fakeDb.transactionsList,
            categoryFilter = null,
            dateFilter = filter,
            categories = fakeDb.categoriesList,
            accounts = fakeDb.accountsList
        )
        assertEquals(2, list1.size)

        // Top budgets calculation
        val spentCat1 = TopBudgetsCalculator.calculateCategorySpendingPaise(fakeDb.transactionsList, 1L, mayMonth.toString())
        assertEquals(10000L, spentCat1)

        // Delete tx1
        financeRepo.deleteTransaction(tx1Id)

        val listAfterDelete = TransactionDateFilter.filterTransactions(
            transactions = fakeDb.transactionsList,
            categoryFilter = null,
            dateFilter = filter,
            categories = fakeDb.categoriesList,
            accounts = fakeDb.accountsList
        )
        assertEquals(1, listAfterDelete.size)
        assertEquals(tx2Id, listAfterDelete[0].id)

        // Recalculate spending after delete
        val spentAfterDelete = TopBudgetsCalculator.calculateCategorySpendingPaise(fakeDb.transactionsList, 1L, mayMonth.toString())
        assertEquals(0L, spentAfterDelete)

        // Update tx2 amount
        val tx2 = fakeDb.transactions().getById(tx2Id)!!
        financeRepo.updateTransaction(tx2.copy(amountPaise = 8000L))

        val spentCat2 = TopBudgetsCalculator.calculateCategorySpendingPaise(fakeDb.transactionsList, 2L, mayMonth.toString())
        assertEquals(8000L, spentCat2)
    }

    // -------------------------------------------------------------------------
    // TEST J: Verify no exception occurs when an imported transaction has nullable/missing category or account metadata
    // -------------------------------------------------------------------------
    @Test
    fun testJ_noExceptionOnNullableOrMissingMetadata() = runBlocking {
        // Case 1: Imported transfer with NULL destinationAccountId (single statement self transfer)
        val transferId = importSampleTransaction(
            merchant = "Self Transfer",
            amountPaise = 15000L,
            direction = Direction.DEBIT,
            type = ImporterTransactionType.TRANSFER,
            sourceAccountId = 1L,
            categoryId = 0L,
            destinationAccountId = null
        )
        val importedTransfer = fakeDb.transactions().getById(transferId)!!
        assertEquals(TransactionType.TRANSFER, importedTransfer.type)
        assertNull("destinationAccountId must be null", importedTransfer.destinationAccountId)

        // Edit the transfer without destinationAccountId - MUST NOT CRASH
        financeRepo.updateTransaction(importedTransfer.copy(description = "Self Transfer Edited"))
        assertEquals("Self Transfer Edited", fakeDb.transactions().getById(transferId)!!.description)

        // Delete the transfer without destinationAccountId - MUST NOT CRASH
        financeRepo.deleteTransaction(transferId)
        assertNull(fakeDb.transactions().getById(transferId))

        // Case 2: Imported transaction with categoryId = 0L (Uncategorized)
        val uncategorizedId = importSampleTransaction(
            merchant = "Unknown Store",
            amountPaise = 5000L,
            categoryId = 0L,
            sourceAccountId = 1L
        )
        val uncatTx = fakeDb.transactions().getById(uncategorizedId)!!
        assertEquals(0L, uncatTx.categoryId)

        // Edit merchant & amount - MUST NOT CRASH
        financeRepo.updateTransaction(uncatTx.copy(description = "Known Store", amountPaise = 6000L))
        val updatedUncat = fakeDb.transactions().getById(uncategorizedId)!!
        assertEquals("Known Store", updatedUncat.description)

        // Delete - MUST NOT CRASH
        financeRepo.deleteTransaction(uncategorizedId)
        assertNull(fakeDb.transactions().getById(uncategorizedId))

        // Case 3: Transaction referencing nonexistent account (e.g. account 999L deleted)
        val orphanTx = TransactionEntity(
            id = 9999L,
            dateEpochDay = LocalDate.now().toEpochDay(),
            description = "Orphan",
            amountPaise = 1000L,
            categoryId = 0L,
            accountId = 999L,
            type = TransactionType.EXPENSE
        )
        fakeDb.transactionsList.add(orphanTx)

        // Update orphan - MUST NOT CRASH
        financeRepo.updateTransaction(orphanTx.copy(description = "Orphan Updated"))
        assertEquals("Orphan Updated", fakeDb.transactions().getById(9999L)!!.description)

        // Delete orphan - MUST NOT CRASH
        financeRepo.deleteTransaction(9999L)
        assertNull(fakeDb.transactions().getById(9999L))
    }

    // -------------------------------------------------------------------------
    // In-Memory Test Database
    // -------------------------------------------------------------------------
    class MutationTestSpendTrackDatabase : SpendTrackDatabase() {
        val accountsList = mutableListOf<AccountEntity>()
        val categoriesList = mutableListOf<CategoryEntity>()
        val transactionsList = mutableListOf<TransactionEntity>()
        val cardPaymentsList = mutableListOf<CardPaymentEntity>()
        val importHistoryList = mutableListOf<ImportHistoryEntity>()
        val owedList = mutableListOf<OwedEntity>()
        val budgetsList = mutableListOf<BudgetEntity>()

        private val dummyDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "inTransaction" -> true
                "isOpen" -> true
                "isWriteAheadLoggingEnabled" -> false
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        } as SupportSQLiteDatabase

        private val dummyHelper = Proxy.newProxyInstance(
            SupportSQLiteOpenHelper::class.java.classLoader,
            arrayOf(SupportSQLiteOpenHelper::class.java)
        ) { _, method, _ ->
            if (method.name == "getWritableDatabase" || method.name == "getReadableDatabase") dummyDb
            else null
        } as SupportSQLiteOpenHelper

        override val openHelper: SupportSQLiteOpenHelper = dummyHelper

        init {
            val directExecutor = Executor { it.run() }
            RoomDatabase::class.java.declaredFields.forEach { f ->
                f.isAccessible = true
                when (f.name) {
                    "mAllowMainThreadQueries", "allowMainThreadQueries" -> runCatching { f.set(this, true) }
                    "mOpenHelper", "openHelper" -> runCatching { f.set(this, dummyHelper) }
                    "internalTransactionExecutor", "mTransactionExecutor", "transactionExecutor" -> runCatching { f.set(this, directExecutor) }
                    "internalQueryExecutor", "mQueryExecutor", "queryExecutor" -> runCatching { f.set(this, directExecutor) }
                }
            }
        }

        private val _accountsDao = object : AccountDao {
            override fun observeAll(): Flow<List<AccountEntity>> = flowOf(accountsList.toList())
            override suspend fun insert(entity: AccountEntity): Long {
                val id = if (entity.id == 0L) (accountsList.size + 1).toLong() else entity.id
                accountsList.add(entity.copy(id = id))
                return id
            }
            override suspend fun insertAll(entities: List<AccountEntity>) { accountsList.addAll(entities) }
            override suspend fun getById(id: Long): AccountEntity? = accountsList.firstOrNull { it.id == id }
            override suspend fun update(entity: AccountEntity) {
                val idx = accountsList.indexOfFirst { it.id == entity.id }
                if (idx >= 0) accountsList[idx] = entity
            }
            override suspend fun delete(entity: AccountEntity) { accountsList.removeAll { it.id == entity.id } }
            override suspend fun clear() { accountsList.clear() }
            override suspend fun transactionCount(accountId: Long): Int = 0
            override suspend fun recurringCount(accountId: Long): Int = 0
        }

        private val _transactionsDao = object : TransactionDao {
            override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(transactionsList.toList())
            override suspend fun insert(entity: TransactionEntity): Long {
                val id = if (entity.id == 0L) (transactionsList.size + 1).toLong() else entity.id
                transactionsList.add(entity.copy(id = id))
                return id
            }
            override suspend fun insertAll(entities: List<TransactionEntity>) { transactionsList.addAll(entities) }
            override suspend fun update(entity: TransactionEntity) {
                val idx = transactionsList.indexOfFirst { it.id == entity.id }
                if (idx >= 0) transactionsList[idx] = entity
            }
            override suspend fun delete(entity: TransactionEntity) { transactionsList.removeAll { it.id == entity.id } }
            override suspend fun clear() { transactionsList.clear() }
            override suspend fun getById(id: Long): TransactionEntity? = transactionsList.firstOrNull { it.id == id }
        }

        private val _cardPaymentsDao = object : CardPaymentDao {
            override fun observeAll(): Flow<List<CardPaymentEntity>> = flowOf(cardPaymentsList.toList())
            override suspend fun insert(entity: CardPaymentEntity): Long {
                val id = if (entity.id == 0L) (cardPaymentsList.size + 1).toLong() else entity.id
                cardPaymentsList.add(entity.copy(id = id))
                return id
            }
            override suspend fun insertAll(entities: List<CardPaymentEntity>) { cardPaymentsList.addAll(entities) }
            override suspend fun clear() { cardPaymentsList.clear() }
        }

        private val _importHistoryDao = object : ImportHistoryDao {
            override suspend fun insert(history: ImportHistoryEntity): Long {
                val id = if (history.id == 0L) (importHistoryList.size + 1).toLong() else history.id
                importHistoryList.add(history.copy(id = id))
                return id
            }
            override suspend fun getByFileHash(hash: String): ImportHistoryEntity? =
                importHistoryList.firstOrNull { it.fileHash == hash }
        }

        private val _categoriesDao = object : CategoryDao {
            override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(categoriesList.toList())
            override suspend fun count(): Int = categoriesList.size
            override suspend fun insert(entity: CategoryEntity): Long {
                val id = if (entity.id == 0L) (categoriesList.size + 1).toLong() else entity.id
                categoriesList.add(entity.copy(id = id))
                return id
            }
            override suspend fun insertAll(entities: List<CategoryEntity>) { categoriesList.addAll(entities) }
            override suspend fun update(entity: CategoryEntity) {}
            override suspend fun delete(entity: CategoryEntity) {}
            override suspend fun clear() { categoriesList.clear() }
            override suspend fun transactionCount(categoryId: Long): Int = 0
            override suspend fun budgetCount(categoryId: Long): Int = 0
            override suspend fun recurringCount(categoryId: Long): Int = 0
        }

        private val _owedDao = object : OwedDao {
            override fun observeAll(): Flow<List<OwedEntity>> = flowOf(owedList.toList())
            override suspend fun insert(entity: OwedEntity): Long {
                val id = if (entity.id == 0L) (owedList.size + 1).toLong() else entity.id
                owedList.add(entity.copy(id = id))
                return id
            }
            override suspend fun insertAll(entities: List<OwedEntity>) { owedList.addAll(entities) }
            override suspend fun update(entity: OwedEntity) {
                val idx = owedList.indexOfFirst { it.id == entity.id }
                if (idx >= 0) owedList[idx] = entity
            }
            override suspend fun delete(entity: OwedEntity) { owedList.removeAll { it.id == entity.id } }
            override suspend fun clear() { owedList.clear() }
            override suspend fun deleteForTransaction(transactionId: Long) {
                owedList.removeAll { it.sourceTransactionId == transactionId }
            }
        }

        private val _budgetDao = object : BudgetDao {
            override fun observeAll(): Flow<List<BudgetEntity>> = flowOf(budgetsList.toList())
            override suspend fun forMonth(monthKey: String): List<BudgetEntity> = budgetsList.filter { it.monthKey == monthKey }
            override suspend fun latestMonthBefore(monthKey: String): String? = null
            override suspend fun find(categoryId: Long, monthKey: String): BudgetEntity? = budgetsList.find { it.categoryId == categoryId && it.monthKey == monthKey }
            override suspend fun insert(entity: BudgetEntity): Long {
                val id = if (entity.id == 0L) (budgetsList.size + 1).toLong() else entity.id
                budgetsList.add(entity.copy(id = id))
                return id
            }
            override suspend fun insertAll(entities: List<BudgetEntity>) { budgetsList.addAll(entities) }
            override suspend fun update(entity: BudgetEntity) {}
            override suspend fun delete(entity: BudgetEntity) {}
            override suspend fun clear() { budgetsList.clear() }
        }

        override fun accounts(): AccountDao = _accountsDao
        override fun transactions(): TransactionDao = _transactionsDao
        override fun cardPayments(): CardPaymentDao = _cardPaymentsDao
        override fun importHistory(): ImportHistoryDao = _importHistoryDao
        override fun categories(): CategoryDao = _categoriesDao
        override fun owed(): OwedDao = _owedDao
        override fun budgets(): BudgetDao = _budgetDao

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified DAO : Any> createEmptyDao(): DAO {
            return Proxy.newProxyInstance(
                DAO::class.java.classLoader,
                arrayOf(DAO::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "observeAll" -> flowOf(emptyList<Any>())
                    else -> when (method.returnType) {
                        java.lang.Boolean.TYPE -> false
                        java.lang.Integer.TYPE -> 0
                        java.lang.Long.TYPE -> 0L
                        else -> null
                    }
                }
            } as DAO
        }

        private val _loansDao by lazy { createEmptyDao<LoanDao>() }
        private val _fixedDepositsDao by lazy { createEmptyDao<FixedDepositDao>() }
        private val _recurringTransactionsDao by lazy { createEmptyDao<RecurringTransactionDao>() }
        private val _recurringOccurrencesDao by lazy { createEmptyDao<RecurringOccurrenceDao>() }

        override fun loans(): LoanDao = _loansDao
        override fun fixedDeposits(): FixedDepositDao = _fixedDepositsDao
        override fun recurringTransactions(): RecurringTransactionDao = _recurringTransactionsDao
        override fun recurringOccurrences(): RecurringOccurrenceDao = _recurringOccurrencesDao

        override fun inTransaction(): Boolean = true
        override val isOpen: Boolean = true

        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
            throw UnsupportedOperationException()
        }

        override fun createInvalidationTracker(): InvalidationTracker {
            return InvalidationTracker(this, emptyMap(), emptyMap(), "accounts")
        }

        override fun clearAllTables() {}
    }
}
