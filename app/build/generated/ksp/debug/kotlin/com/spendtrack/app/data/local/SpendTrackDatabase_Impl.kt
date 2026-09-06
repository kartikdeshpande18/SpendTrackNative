package com.spendtrack.app.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SpendTrackDatabase_Impl : SpendTrackDatabase() {
  private val _accountDao: Lazy<AccountDao> = lazy {
    AccountDao_Impl(this)
  }

  private val _categoryDao: Lazy<CategoryDao> = lazy {
    CategoryDao_Impl(this)
  }

  private val _transactionDao: Lazy<TransactionDao> = lazy {
    TransactionDao_Impl(this)
  }

  private val _budgetDao: Lazy<BudgetDao> = lazy {
    BudgetDao_Impl(this)
  }

  private val _loanDao: Lazy<LoanDao> = lazy {
    LoanDao_Impl(this)
  }

  private val _owedDao: Lazy<OwedDao> = lazy {
    OwedDao_Impl(this)
  }

  private val _cardPaymentDao: Lazy<CardPaymentDao> = lazy {
    CardPaymentDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(3, "531871258fee1b8923aa62353e1225d8", "222d4854985161757c6ccd3108e652d5") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `bank` TEXT NOT NULL, `maskedNumber` TEXT NOT NULL, `balancePaise` INTEGER NOT NULL, `isCreditCard` INTEGER NOT NULL, `creditLimitPaise` INTEGER, `statementCycleDay` INTEGER, `dueDay` INTEGER)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `isIncomeCategory` INTEGER NOT NULL, `isSystemFallback` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateEpochDay` INTEGER NOT NULL, `description` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `categoryId` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `type` TEXT NOT NULL, `isShared` INTEGER NOT NULL, `mySharePaise` INTEGER, `sharedWith` TEXT NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `categoryId` INTEGER NOT NULL, `monthKey` TEXT NOT NULL, `limitPaise` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `loans` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `originalPrincipalPaise` INTEGER NOT NULL, `currentOutstandingPaise` INTEGER NOT NULL, `interestRateBps` INTEGER NOT NULL, `emiPaise` INTEGER NOT NULL, `nextDueEpochDay` INTEGER NOT NULL, `remainingTenureMonths` INTEGER NOT NULL, `principalPaidPaise` INTEGER NOT NULL, `interestPaidPaise` INTEGER NOT NULL, `includeInNetWorth` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `owed` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `personName` TEXT NOT NULL, `amountPaise` INTEGER NOT NULL, `direction` TEXT NOT NULL, `note` TEXT NOT NULL, `sourceTransactionId` INTEGER)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `card_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `cardAccountId` INTEGER NOT NULL, `sourceAccountId` INTEGER NOT NULL, `amountPaise` INTEGER NOT NULL, `dateEpochDay` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '531871258fee1b8923aa62353e1225d8')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `accounts`")
        connection.execSQL("DROP TABLE IF EXISTS `categories`")
        connection.execSQL("DROP TABLE IF EXISTS `transactions`")
        connection.execSQL("DROP TABLE IF EXISTS `budgets`")
        connection.execSQL("DROP TABLE IF EXISTS `loans`")
        connection.execSQL("DROP TABLE IF EXISTS `owed`")
        connection.execSQL("DROP TABLE IF EXISTS `card_payments`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsAccounts: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsAccounts.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccounts.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccounts.put("bank", TableInfo.Column("bank", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccounts.put("maskedNumber", TableInfo.Column("maskedNumber", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccounts.put("balancePaise", TableInfo.Column("balancePaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccounts.put("isCreditCard", TableInfo.Column("isCreditCard", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccounts.put("creditLimitPaise", TableInfo.Column("creditLimitPaise", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccounts.put("statementCycleDay", TableInfo.Column("statementCycleDay", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsAccounts.put("dueDay", TableInfo.Column("dueDay", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysAccounts: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesAccounts: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoAccounts: TableInfo = TableInfo("accounts", _columnsAccounts, _foreignKeysAccounts, _indicesAccounts)
        val _existingAccounts: TableInfo = read(connection, "accounts")
        if (!_infoAccounts.equals(_existingAccounts)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |accounts(com.spendtrack.app.data.local.AccountEntity).
              | Expected:
              |""".trimMargin() + _infoAccounts + """
              |
              | Found:
              |""".trimMargin() + _existingAccounts)
        }
        val _columnsCategories: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCategories.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCategories.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCategories.put("isIncomeCategory", TableInfo.Column("isIncomeCategory", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCategories.put("isSystemFallback", TableInfo.Column("isSystemFallback", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCategories: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCategories: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCategories: TableInfo = TableInfo("categories", _columnsCategories, _foreignKeysCategories, _indicesCategories)
        val _existingCategories: TableInfo = read(connection, "categories")
        if (!_infoCategories.equals(_existingCategories)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |categories(com.spendtrack.app.data.local.CategoryEntity).
              | Expected:
              |""".trimMargin() + _infoCategories + """
              |
              | Found:
              |""".trimMargin() + _existingCategories)
        }
        val _columnsTransactions: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsTransactions.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTransactions.put("dateEpochDay", TableInfo.Column("dateEpochDay", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTransactions.put("description", TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTransactions.put("amountPaise", TableInfo.Column("amountPaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTransactions.put("categoryId", TableInfo.Column("categoryId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTransactions.put("accountId", TableInfo.Column("accountId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTransactions.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTransactions.put("isShared", TableInfo.Column("isShared", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTransactions.put("mySharePaise", TableInfo.Column("mySharePaise", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsTransactions.put("sharedWith", TableInfo.Column("sharedWith", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysTransactions: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesTransactions: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoTransactions: TableInfo = TableInfo("transactions", _columnsTransactions, _foreignKeysTransactions, _indicesTransactions)
        val _existingTransactions: TableInfo = read(connection, "transactions")
        if (!_infoTransactions.equals(_existingTransactions)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |transactions(com.spendtrack.app.data.local.TransactionEntity).
              | Expected:
              |""".trimMargin() + _infoTransactions + """
              |
              | Found:
              |""".trimMargin() + _existingTransactions)
        }
        val _columnsBudgets: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsBudgets.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBudgets.put("categoryId", TableInfo.Column("categoryId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBudgets.put("monthKey", TableInfo.Column("monthKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsBudgets.put("limitPaise", TableInfo.Column("limitPaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysBudgets: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesBudgets: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoBudgets: TableInfo = TableInfo("budgets", _columnsBudgets, _foreignKeysBudgets, _indicesBudgets)
        val _existingBudgets: TableInfo = read(connection, "budgets")
        if (!_infoBudgets.equals(_existingBudgets)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |budgets(com.spendtrack.app.data.local.BudgetEntity).
              | Expected:
              |""".trimMargin() + _infoBudgets + """
              |
              | Found:
              |""".trimMargin() + _existingBudgets)
        }
        val _columnsLoans: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsLoans.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("name", TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("originalPrincipalPaise", TableInfo.Column("originalPrincipalPaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("currentOutstandingPaise", TableInfo.Column("currentOutstandingPaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("interestRateBps", TableInfo.Column("interestRateBps", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("emiPaise", TableInfo.Column("emiPaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("nextDueEpochDay", TableInfo.Column("nextDueEpochDay", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("remainingTenureMonths", TableInfo.Column("remainingTenureMonths", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("principalPaidPaise", TableInfo.Column("principalPaidPaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("interestPaidPaise", TableInfo.Column("interestPaidPaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsLoans.put("includeInNetWorth", TableInfo.Column("includeInNetWorth", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysLoans: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesLoans: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoLoans: TableInfo = TableInfo("loans", _columnsLoans, _foreignKeysLoans, _indicesLoans)
        val _existingLoans: TableInfo = read(connection, "loans")
        if (!_infoLoans.equals(_existingLoans)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |loans(com.spendtrack.app.data.local.LoanEntity).
              | Expected:
              |""".trimMargin() + _infoLoans + """
              |
              | Found:
              |""".trimMargin() + _existingLoans)
        }
        val _columnsOwed: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsOwed.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOwed.put("personName", TableInfo.Column("personName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOwed.put("amountPaise", TableInfo.Column("amountPaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOwed.put("direction", TableInfo.Column("direction", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOwed.put("note", TableInfo.Column("note", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsOwed.put("sourceTransactionId", TableInfo.Column("sourceTransactionId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysOwed: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesOwed: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoOwed: TableInfo = TableInfo("owed", _columnsOwed, _foreignKeysOwed, _indicesOwed)
        val _existingOwed: TableInfo = read(connection, "owed")
        if (!_infoOwed.equals(_existingOwed)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |owed(com.spendtrack.app.data.local.OwedEntity).
              | Expected:
              |""".trimMargin() + _infoOwed + """
              |
              | Found:
              |""".trimMargin() + _existingOwed)
        }
        val _columnsCardPayments: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsCardPayments.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardPayments.put("cardAccountId", TableInfo.Column("cardAccountId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardPayments.put("sourceAccountId", TableInfo.Column("sourceAccountId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardPayments.put("amountPaise", TableInfo.Column("amountPaise", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsCardPayments.put("dateEpochDay", TableInfo.Column("dateEpochDay", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysCardPayments: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesCardPayments: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoCardPayments: TableInfo = TableInfo("card_payments", _columnsCardPayments, _foreignKeysCardPayments, _indicesCardPayments)
        val _existingCardPayments: TableInfo = read(connection, "card_payments")
        if (!_infoCardPayments.equals(_existingCardPayments)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |card_payments(com.spendtrack.app.data.local.CardPaymentEntity).
              | Expected:
              |""".trimMargin() + _infoCardPayments + """
              |
              | Found:
              |""".trimMargin() + _existingCardPayments)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "accounts", "categories", "transactions", "budgets", "loans", "owed", "card_payments")
  }

  public override fun clearAllTables() {
    super.performClear(false, "accounts", "categories", "transactions", "budgets", "loans", "owed", "card_payments")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(AccountDao::class, AccountDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CategoryDao::class, CategoryDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(TransactionDao::class, TransactionDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(BudgetDao::class, BudgetDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(LoanDao::class, LoanDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(OwedDao::class, OwedDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(CardPaymentDao::class, CardPaymentDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun accounts(): AccountDao = _accountDao.value

  public override fun categories(): CategoryDao = _categoryDao.value

  public override fun transactions(): TransactionDao = _transactionDao.value

  public override fun budgets(): BudgetDao = _budgetDao.value

  public override fun loans(): LoanDao = _loanDao.value

  public override fun owed(): OwedDao = _owedDao.value

  public override fun cardPayments(): CardPaymentDao = _cardPaymentDao.value
}
