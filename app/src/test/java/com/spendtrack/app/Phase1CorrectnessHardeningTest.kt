package com.spendtrack.app

import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.spendtrack.app.data.local.*
import com.spendtrack.app.data.repository.BackupRepository
import com.spendtrack.app.ui.formatEpochDate
import com.spendtrack.app.ui.parsePaise
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.util.concurrent.Executor

class Phase1CorrectnessHardeningTest {

    @Test
    fun testParsePaise_wholeRupees() {
        assertEquals(0L, parsePaise("0"))
        assertEquals(100L, parsePaise("1"))
        assertEquals(1000L, parsePaise("10"))
        assertEquals(10000L, parsePaise("100"))
        assertEquals(150000L, parsePaise("1500"))
    }

    @Test
    fun testParsePaise_decimalRupees() {
        assertEquals(110L, parsePaise("1.1"))
        assertEquals(115L, parsePaise("1.15"))
        assertEquals(1000L, parsePaise("10.00"))
        assertEquals(150050L, parsePaise("1500.50"))
        assertEquals(71L, parsePaise("0.71"))
        assertEquals(15499999L, parsePaise("154999.99"))
    }

    @Test
    fun testParsePaise_ieee754PrecisionSafety() {
        // Values like 1.15, 2.15, 4.15, 8.15 frequently suffer from IEEE-754 precision issues
        // e.g. 1.15 * 100 in double is 114.99999999999999
        assertEquals(115L, parsePaise("1.15"))
        assertEquals(215L, parsePaise("2.15"))
        assertEquals(415L, parsePaise("4.15"))
        assertEquals(815L, parsePaise("8.15"))
        assertEquals(1415L, parsePaise("14.15"))
        assertEquals(2815L, parsePaise("28.15"))
        assertEquals(5615L, parsePaise("56.15"))
        assertEquals(11215L, parsePaise("112.15"))
    }

    @Test
    fun testParsePaise_moreThanTwoDecimalPlacesRounding() {
        // Inputs with >2 decimal places round half-up at the paise level deterministically
        assertEquals(200L, parsePaise("1.999"))
        assertEquals(200L, parsePaise("1.995"))
        assertEquals(199L, parsePaise("1.994"))
        assertEquals(1L, parsePaise("0.005"))
        assertEquals(0L, parsePaise("0.004"))
        assertEquals(100050L, parsePaise("1000.499"))
    }

    @Test
    fun testParsePaise_overflowSafety() {
        // Enormous or out-of-range values that cannot fit in a Long throw ArithmeticException in longValueExact,
        // which parsePaise catches and safely returns 0L rather than wrapping around to negative/corrupted numbers.
        assertEquals(0L, parsePaise("99999999999999999999999999999999999999999"))
        assertEquals(0L, parsePaise("-99999999999999999999999999999999999999999"))
        assertEquals(0L, parsePaise("1e30"))
        assertEquals(0L, parsePaise("100000000000000000000000000000.00"))
    }

    @Test
    fun testParsePaise_currencySymbolsAndCommas() {
        assertEquals(10050L, parsePaise("₹100.50"))
        assertEquals(5000L, parsePaise(" ₹ 50 "))
        assertEquals(150050L, parsePaise("1,500.50"))
        assertEquals(15499999L, parsePaise("₹1,54,999.99"))
    }

    @Test
    fun testParsePaise_emptyAndMalformedInput() {
        assertEquals(0L, parsePaise(""))
        assertEquals(0L, parsePaise("   "))
        assertEquals(0L, parsePaise("abc"))
        assertEquals(0L, parsePaise("₹"))
        assertEquals(0L, parsePaise("12.34.56"))
        assertEquals(0L, parsePaise(".."))
    }

    @Test
    fun testParsePaise_negativeInput() {
        assertEquals(-5025L, parsePaise("-50.25"))
        assertEquals(-10000L, parsePaise("-100"))
        assertEquals(-71L, parsePaise("-0.71"))
    }

    @Test
    fun testFormatEpochDate_validDates() {
        val date = LocalDate.of(2026, 9, 15)
        val expectedShortMonth = date.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        val formatted = formatEpochDate(date.toEpochDay())
        assertEquals("15 $expectedShortMonth 2026", formatted)

        val epochZero = formatEpochDate(0L)
        val expectedJan = LocalDate.of(1970, 1, 1).month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        assertEquals("1 $expectedJan 1970", epochZero)
    }

    @Test
    fun testFormatEpochDate_invalidAndOutOfBoundsInput() {
        // Out of bounds epoch-days that would throw DateTimeException in LocalDate.ofEpochDay
        assertEquals("—", formatEpochDate(Long.MAX_VALUE))
        assertEquals("—", formatEpochDate(Long.MIN_VALUE))
        assertEquals("—", formatEpochDate(999999999999L))
        assertEquals("—", formatEpochDate(-999999999999L))
    }

    @Test
    fun testSpendTrackConverters_safeEnumDeserialization() {
        val converters = SpendTrackConverters()

        // Valid values
        assertEquals(TransactionType.INCOME, converters.stringToTransactionType("INCOME"))
        assertEquals(TransactionType.EXPENSE, converters.stringToTransactionType("EXPENSE"))
        assertEquals(TransactionType.TRANSFER, converters.stringToTransactionType("TRANSFER"))
        assertEquals(OwedDirection.OWED_TO_ME, converters.stringToOwedDirection("OWED_TO_ME"))
        assertEquals(OwedDirection.I_OWE, converters.stringToOwedDirection("I_OWE"))
        assertEquals(FixedDepositStatus.ACTIVE, converters.stringToFixedDepositStatus("ACTIVE"))
        assertEquals(FixedDepositStatus.MATURED, converters.stringToFixedDepositStatus("MATURED"))
        assertEquals(CompoundingFrequency.QUARTERLY, converters.stringToCompoundingFrequency("QUARTERLY"))
        assertEquals(RecurrenceFrequency.MONTHLY, converters.stringToRecurrenceFrequency("MONTHLY"))

        // Corrupted / unknown enum strings safely fallback without crashing
        assertEquals(TransactionType.EXPENSE, converters.stringToTransactionType("UNKNOWN_TYPE"))
        assertEquals(TransactionType.EXPENSE, converters.stringToTransactionType(""))
        assertEquals(OwedDirection.OWED_TO_ME, converters.stringToOwedDirection("INVALID_DIRECTION"))
        assertEquals(FixedDepositStatus.ACTIVE, converters.stringToFixedDepositStatus("NON_EXISTENT_STATUS"))
        assertEquals(CompoundingFrequency.QUARTERLY, converters.stringToCompoundingFrequency("HOURLY"))
        assertEquals(RecurrenceFrequency.MONTHLY, converters.stringToRecurrenceFrequency("BI_WEEKLY"))
    }

    // --- BackupRepository Regression Tests ---

    @Test
    fun testBackupRepository_restoreWithInvalidEnumsUsesSafeFallbacks() = runBlocking {
        val fakeDb = TestSpendTrackDatabase()
        val repo = BackupRepository(fakeDb)

        val corruptedBackupJson = """
        {
          "format": "spendtrack-backup",
          "version": 1,
          "accounts": [
            { "id": 1, "name": "Savings", "bank": "SBI", "masked": "1234", "balance": 500000, "card": false, "minimumBalancePaise": 0 }
          ],
          "categories": [
            { "id": 1, "name": "General", "income": false, "fallback": true }
          ],
          "transactions": [
            {
              "id": 10,
              "date": 19600,
              "description": "Corrupted Tx Type",
              "amount": 25000,
              "category": 1,
              "account": 1,
              "type": "UNKNOWN_TRANSACTION_TYPE",
              "shared": false
            }
          ],
          "budgets": [],
          "loans": [],
          "owed": [
            {
              "id": 20,
              "person": "Alice",
              "amount": 10000,
              "direction": "CORRUPTED_DIRECTION"
            }
          ],
          "cardPayments": [],
          "fixedDeposits": [
            {
              "id": 30,
              "name": "Term Deposit",
              "accountId": 1,
              "principal": 100000,
              "rate": 700,
              "startDate": 19500,
              "maturityDate": 19865,
              "maturityAmount": 107000,
              "status": "CORRUPTED_STATUS",
              "compoundingFrequency": "INVALID_FREQ"
            }
          ],
          "recurringTransactions": [
            {
              "id": 40,
              "description": "Streaming",
              "amount": 49900,
              "category": 1,
              "account": 1,
              "type": "INVALID_TYPE",
              "frequency": "CORRUPTED_FREQUENCY",
              "startDate": 19500,
              "nextOccurrence": 19530
            }
          ],
          "recurringOccurrences": []
        }
        """.trimIndent()

        // Must restore without throwing IllegalArgumentException
        repo.restore(corruptedBackupJson)

        // Verify that safe fallbacks were applied
        assertEquals(1, fakeDb.transactionsList.size)
        assertEquals(TransactionType.EXPENSE, fakeDb.transactionsList[0].type)

        assertEquals(1, fakeDb.owedList.size)
        assertEquals(OwedDirection.OWED_TO_ME, fakeDb.owedList[0].direction)

        assertEquals(1, fakeDb.fixedDepositsList.size)
        assertEquals(FixedDepositStatus.ACTIVE, fakeDb.fixedDepositsList[0].status)
        assertEquals(CompoundingFrequency.QUARTERLY, fakeDb.fixedDepositsList[0].compoundingFrequency)

        assertEquals(1, fakeDb.recurringTransactionsList.size)
        assertEquals(TransactionType.EXPENSE, fakeDb.recurringTransactionsList[0].type)
        assertEquals(RecurrenceFrequency.MONTHLY, fakeDb.recurringTransactionsList[0].frequency)
    }

    @Test
    fun testBackupRepository_exportProducesValidJsonFormat() = runBlocking {
        val fakeDb = TestSpendTrackDatabase()
        fakeDb.accountsList.add(AccountEntity(id = 1L, name = "HDFC", bank = "HDFC Bank", balancePaise = 250000L, isCreditCard = false, minimumBalancePaise = 50000L))
        fakeDb.categoriesList.add(CategoryEntity(id = 1L, name = "Food", isIncomeCategory = false))

        val repo = BackupRepository(fakeDb)
        val exportedJson = repo.export()

        val json = JSONObject(exportedJson)
        assertEquals("spendtrack-backup", json.getString("format"))
        assertEquals(1, json.getInt("version"))

        val accountsArray = json.getJSONArray("accounts")
        assertEquals(1, accountsArray.length())
        val acctObj = accountsArray.getJSONObject(0)
        assertEquals(1L, acctObj.getLong("id"))
        assertEquals("HDFC", acctObj.getString("name"))
        assertEquals(250000L, acctObj.getLong("balance"))
        assertEquals(50000L, acctObj.getLong("minimumBalancePaise"))

        val categoriesArray = json.getJSONArray("categories")
        assertEquals(1, categoriesArray.length())
        assertEquals("Food", categoriesArray.getJSONObject(0).getString("name"))

        assertTrue(json.has("transactions"))
        assertTrue(json.has("budgets"))
        assertTrue(json.has("loans"))
        assertTrue(json.has("owed"))
        assertTrue(json.has("people"))
        assertTrue(json.has("cardPayments"))
        assertTrue(json.has("fixedDeposits"))
        assertTrue(json.has("recurringTransactions"))
        assertTrue(json.has("recurringOccurrences"))
    }

    @Test
    fun testBackupRepository_roundTripExportAndRestore() = runBlocking {
        val sourceDb = TestSpendTrackDatabase()
        val account = AccountEntity(id = 10L, name = "Salary Account", bank = "Axis", balancePaise = 85000_00L, isCreditCard = false, minimumBalancePaise = 10000_00L)
        val category = CategoryEntity(id = 20L, name = "Groceries", isIncomeCategory = false)
        val transaction = TransactionEntity(id = 30L, dateEpochDay = 19600L, description = "Supermarket", amountPaise = 3450_50L, categoryId = 20L, accountId = 10L, type = TransactionType.EXPENSE)
        val fd = FixedDepositEntity(id = 40L, name = "Emergency FD", linkedAccountId = 10L, principalPaise = 50000_00L, interestRateBps = 750, startDateEpochDay = 19500L, maturityDateEpochDay = 19865L, maturityAmountPaise = 53750_00L, status = FixedDepositStatus.ACTIVE, compoundingFrequency = CompoundingFrequency.QUARTERLY)
        val owed = OwedEntity(id = 50L, personName = "Bob", amountPaise = 1500_00L, direction = OwedDirection.OWED_TO_ME)

        sourceDb.accountsList.add(account)
        sourceDb.categoriesList.add(category)
        sourceDb.transactionsList.add(transaction)
        sourceDb.fixedDepositsList.add(fd)
        sourceDb.owedList.add(owed)
        sourceDb.people().insert(PersonEntity(id = 60L, name = "Archived friend", normalizedName = "archived friend", isActive = false))

        val sourceRepo = BackupRepository(sourceDb)
        val exportedJson = sourceRepo.export()

        // Restore into a clean destination database
        val destDb = TestSpendTrackDatabase()
        val destRepo = BackupRepository(destDb)
        destRepo.restore(exportedJson)

        assertEquals(1, destDb.accountsList.size)
        assertEquals(account.id, destDb.accountsList[0].id)
        assertEquals(account.name, destDb.accountsList[0].name)
        assertEquals(account.balancePaise, destDb.accountsList[0].balancePaise)
        assertEquals(account.minimumBalancePaise, destDb.accountsList[0].minimumBalancePaise)

        assertEquals(1, destDb.categoriesList.size)
        assertEquals(category.id, destDb.categoriesList[0].id)
        assertEquals(category.name, destDb.categoriesList[0].name)

        assertEquals(1, destDb.transactionsList.size)
        assertEquals(transaction.id, destDb.transactionsList[0].id)
        assertEquals(transaction.amountPaise, destDb.transactionsList[0].amountPaise)
        assertEquals(TransactionType.EXPENSE, destDb.transactionsList[0].type)

        assertEquals(1, destDb.fixedDepositsList.size)
        assertEquals(fd.id, destDb.fixedDepositsList[0].id)
        assertEquals(fd.principalPaise, destDb.fixedDepositsList[0].principalPaise)
        assertEquals(FixedDepositStatus.ACTIVE, destDb.fixedDepositsList[0].status)
        assertEquals(CompoundingFrequency.QUARTERLY, destDb.fixedDepositsList[0].compoundingFrequency)

        assertEquals(1, destDb.owedList.size)
        assertEquals(owed.id, destDb.owedList[0].id)
        assertEquals(owed.amountPaise, destDb.owedList[0].amountPaise)
        assertEquals(OwedDirection.OWED_TO_ME, destDb.owedList[0].direction)
        assertEquals(PersonEntity(60L, "Archived friend", "archived friend", false), destDb.people().getById(60L))
    }

    /**
     * Test in-memory database implementation that satisfies RoomDatabase's reflection requirements
     * without needing physical SQLite Android drivers during JVM unit tests.
     */
    private class TestSpendTrackDatabase : SpendTrackDatabase() {
        private val _peopleDao = InMemoryPersonDao()
        val accountsList = mutableListOf<AccountEntity>()
        val categoriesList = mutableListOf<CategoryEntity>()
        val transactionsList = mutableListOf<TransactionEntity>()
        val budgetsList = mutableListOf<BudgetEntity>()
        val loansList = mutableListOf<LoanEntity>()
        val owedList = mutableListOf<OwedEntity>()
        val cardPaymentsList = mutableListOf<CardPaymentEntity>()
        val fixedDepositsList = mutableListOf<FixedDepositEntity>()
        val recurringTransactionsList = mutableListOf<RecurringTransactionEntity>()
        val recurringOccurrencesList = mutableListOf<RecurringOccurrenceEntity>()
        val importHistoryList = mutableListOf<ImportHistoryEntity>()

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

        @Suppress("UNCHECKED_CAST")
        private inline fun <reified DAO : Any, E : Any> createDaoProxy(list: MutableList<E>): DAO {
            return Proxy.newProxyInstance(
                DAO::class.java.classLoader,
                arrayOf(DAO::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "observeAll" -> flowOf(list.toList())
                    "getAll" -> list.toList()
                    "clear" -> { list.clear(); null }
                    "insertAll" -> {
                        if (args != null && args.isNotEmpty() && args[0] is List<*>) {
                            list.addAll(args[0] as List<E>)
                        }
                        null
                    }
                    else -> null
                }
            } as DAO
        }

        private val _accountsDao by lazy { createDaoProxy<AccountDao, AccountEntity>(accountsList) }
        private val _categoriesDao by lazy { createDaoProxy<CategoryDao, CategoryEntity>(categoriesList) }
        private val _transactionsDao by lazy { createDaoProxy<TransactionDao, TransactionEntity>(transactionsList) }
        private val _budgetsDao by lazy { createDaoProxy<BudgetDao, BudgetEntity>(budgetsList) }
        private val _loansDao by lazy { createDaoProxy<LoanDao, LoanEntity>(loansList) }
        private val _owedDao by lazy { createDaoProxy<OwedDao, OwedEntity>(owedList) }
        private val _cardPaymentsDao by lazy { createDaoProxy<CardPaymentDao, CardPaymentEntity>(cardPaymentsList) }
        private val _fixedDepositsDao by lazy { createDaoProxy<FixedDepositDao, FixedDepositEntity>(fixedDepositsList) }
        private val _recurringTransactionsDao by lazy { createDaoProxy<RecurringTransactionDao, RecurringTransactionEntity>(recurringTransactionsList) }
        private val _recurringOccurrencesDao by lazy { createDaoProxy<RecurringOccurrenceDao, RecurringOccurrenceEntity>(recurringOccurrencesList) }
        private val _importHistoryDao by lazy { createDaoProxy<ImportHistoryDao, ImportHistoryEntity>(importHistoryList) }

        override fun accounts(): AccountDao = _accountsDao
        override fun categories(): CategoryDao = _categoriesDao
        override fun transactions(): TransactionDao = _transactionsDao
        override fun budgets(): BudgetDao = _budgetsDao
        override fun loans(): LoanDao = _loansDao
        override fun owed(): OwedDao = _owedDao
        override fun cardPayments(): CardPaymentDao = _cardPaymentsDao
        override fun fixedDeposits(): FixedDepositDao = _fixedDepositsDao
        override fun recurringTransactions(): RecurringTransactionDao = _recurringTransactionsDao
        override fun recurringOccurrences(): RecurringOccurrenceDao = _recurringOccurrencesDao
        override fun importHistory(): ImportHistoryDao = _importHistoryDao
        override fun people(): PersonDao = _peopleDao

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
