package com.spendtrack.app.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AccountDao_Impl(
  __db: RoomDatabase,
) : AccountDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfAccountEntity: EntityInsertAdapter<AccountEntity>

  private val __deleteAdapterOfAccountEntity: EntityDeleteOrUpdateAdapter<AccountEntity>

  private val __updateAdapterOfAccountEntity: EntityDeleteOrUpdateAdapter<AccountEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfAccountEntity = object : EntityInsertAdapter<AccountEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `accounts` (`id`,`name`,`bank`,`maskedNumber`,`balancePaise`,`isCreditCard`,`creditLimitPaise`,`statementCycleDay`,`dueDay`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: AccountEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.bank)
        statement.bindText(4, entity.maskedNumber)
        statement.bindLong(5, entity.balancePaise)
        val _tmp: Int = if (entity.isCreditCard) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        val _tmpCreditLimitPaise: Long? = entity.creditLimitPaise
        if (_tmpCreditLimitPaise == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpCreditLimitPaise)
        }
        val _tmpStatementCycleDay: Int? = entity.statementCycleDay
        if (_tmpStatementCycleDay == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpStatementCycleDay.toLong())
        }
        val _tmpDueDay: Int? = entity.dueDay
        if (_tmpDueDay == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpDueDay.toLong())
        }
      }
    }
    this.__deleteAdapterOfAccountEntity = object : EntityDeleteOrUpdateAdapter<AccountEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `accounts` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: AccountEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfAccountEntity = object : EntityDeleteOrUpdateAdapter<AccountEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `accounts` SET `id` = ?,`name` = ?,`bank` = ?,`maskedNumber` = ?,`balancePaise` = ?,`isCreditCard` = ?,`creditLimitPaise` = ?,`statementCycleDay` = ?,`dueDay` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: AccountEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindText(3, entity.bank)
        statement.bindText(4, entity.maskedNumber)
        statement.bindLong(5, entity.balancePaise)
        val _tmp: Int = if (entity.isCreditCard) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        val _tmpCreditLimitPaise: Long? = entity.creditLimitPaise
        if (_tmpCreditLimitPaise == null) {
          statement.bindNull(7)
        } else {
          statement.bindLong(7, _tmpCreditLimitPaise)
        }
        val _tmpStatementCycleDay: Int? = entity.statementCycleDay
        if (_tmpStatementCycleDay == null) {
          statement.bindNull(8)
        } else {
          statement.bindLong(8, _tmpStatementCycleDay.toLong())
        }
        val _tmpDueDay: Int? = entity.dueDay
        if (_tmpDueDay == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpDueDay.toLong())
        }
        statement.bindLong(10, entity.id)
      }
    }
  }

  public override suspend fun insert(entity: AccountEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfAccountEntity.insertAndReturnId(_connection, entity)
    _result
  }

  public override suspend fun insertAll(entities: List<AccountEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfAccountEntity.insert(_connection, entities)
  }

  public override suspend fun delete(entity: AccountEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfAccountEntity.handle(_connection, entity)
  }

  public override suspend fun update(entity: AccountEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfAccountEntity.handle(_connection, entity)
  }

  public override fun observeAll(): Flow<List<AccountEntity>> {
    val _sql: String = "SELECT * FROM accounts ORDER BY isCreditCard ASC, name ASC"
    return createFlow(__db, false, arrayOf("accounts")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBank: Int = getColumnIndexOrThrow(_stmt, "bank")
        val _columnIndexOfMaskedNumber: Int = getColumnIndexOrThrow(_stmt, "maskedNumber")
        val _columnIndexOfBalancePaise: Int = getColumnIndexOrThrow(_stmt, "balancePaise")
        val _columnIndexOfIsCreditCard: Int = getColumnIndexOrThrow(_stmt, "isCreditCard")
        val _columnIndexOfCreditLimitPaise: Int = getColumnIndexOrThrow(_stmt, "creditLimitPaise")
        val _columnIndexOfStatementCycleDay: Int = getColumnIndexOrThrow(_stmt, "statementCycleDay")
        val _columnIndexOfDueDay: Int = getColumnIndexOrThrow(_stmt, "dueDay")
        val _result: MutableList<AccountEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: AccountEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBank: String
          _tmpBank = _stmt.getText(_columnIndexOfBank)
          val _tmpMaskedNumber: String
          _tmpMaskedNumber = _stmt.getText(_columnIndexOfMaskedNumber)
          val _tmpBalancePaise: Long
          _tmpBalancePaise = _stmt.getLong(_columnIndexOfBalancePaise)
          val _tmpIsCreditCard: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCreditCard).toInt()
          _tmpIsCreditCard = _tmp != 0
          val _tmpCreditLimitPaise: Long?
          if (_stmt.isNull(_columnIndexOfCreditLimitPaise)) {
            _tmpCreditLimitPaise = null
          } else {
            _tmpCreditLimitPaise = _stmt.getLong(_columnIndexOfCreditLimitPaise)
          }
          val _tmpStatementCycleDay: Int?
          if (_stmt.isNull(_columnIndexOfStatementCycleDay)) {
            _tmpStatementCycleDay = null
          } else {
            _tmpStatementCycleDay = _stmt.getLong(_columnIndexOfStatementCycleDay).toInt()
          }
          val _tmpDueDay: Int?
          if (_stmt.isNull(_columnIndexOfDueDay)) {
            _tmpDueDay = null
          } else {
            _tmpDueDay = _stmt.getLong(_columnIndexOfDueDay).toInt()
          }
          _item = AccountEntity(_tmpId,_tmpName,_tmpBank,_tmpMaskedNumber,_tmpBalancePaise,_tmpIsCreditCard,_tmpCreditLimitPaise,_tmpStatementCycleDay,_tmpDueDay)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): AccountEntity? {
    val _sql: String = "SELECT * FROM accounts WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfBank: Int = getColumnIndexOrThrow(_stmt, "bank")
        val _columnIndexOfMaskedNumber: Int = getColumnIndexOrThrow(_stmt, "maskedNumber")
        val _columnIndexOfBalancePaise: Int = getColumnIndexOrThrow(_stmt, "balancePaise")
        val _columnIndexOfIsCreditCard: Int = getColumnIndexOrThrow(_stmt, "isCreditCard")
        val _columnIndexOfCreditLimitPaise: Int = getColumnIndexOrThrow(_stmt, "creditLimitPaise")
        val _columnIndexOfStatementCycleDay: Int = getColumnIndexOrThrow(_stmt, "statementCycleDay")
        val _columnIndexOfDueDay: Int = getColumnIndexOrThrow(_stmt, "dueDay")
        val _result: AccountEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpBank: String
          _tmpBank = _stmt.getText(_columnIndexOfBank)
          val _tmpMaskedNumber: String
          _tmpMaskedNumber = _stmt.getText(_columnIndexOfMaskedNumber)
          val _tmpBalancePaise: Long
          _tmpBalancePaise = _stmt.getLong(_columnIndexOfBalancePaise)
          val _tmpIsCreditCard: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsCreditCard).toInt()
          _tmpIsCreditCard = _tmp != 0
          val _tmpCreditLimitPaise: Long?
          if (_stmt.isNull(_columnIndexOfCreditLimitPaise)) {
            _tmpCreditLimitPaise = null
          } else {
            _tmpCreditLimitPaise = _stmt.getLong(_columnIndexOfCreditLimitPaise)
          }
          val _tmpStatementCycleDay: Int?
          if (_stmt.isNull(_columnIndexOfStatementCycleDay)) {
            _tmpStatementCycleDay = null
          } else {
            _tmpStatementCycleDay = _stmt.getLong(_columnIndexOfStatementCycleDay).toInt()
          }
          val _tmpDueDay: Int?
          if (_stmt.isNull(_columnIndexOfDueDay)) {
            _tmpDueDay = null
          } else {
            _tmpDueDay = _stmt.getLong(_columnIndexOfDueDay).toInt()
          }
          _result = AccountEntity(_tmpId,_tmpName,_tmpBank,_tmpMaskedNumber,_tmpBalancePaise,_tmpIsCreditCard,_tmpCreditLimitPaise,_tmpStatementCycleDay,_tmpDueDay)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun transactionCount(accountId: Long): Int {
    val _sql: String = "SELECT COUNT(*) FROM transactions WHERE accountId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, accountId)
        val _result: Int
        if (_stmt.step()) {
          val _tmp: Int
          _tmp = _stmt.getLong(0).toInt()
          _result = _tmp
        } else {
          _result = 0
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM accounts"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
