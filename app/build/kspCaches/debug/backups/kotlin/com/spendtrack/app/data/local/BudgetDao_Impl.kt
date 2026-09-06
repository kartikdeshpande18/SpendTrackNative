package com.spendtrack.app.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
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
public class BudgetDao_Impl(
  __db: RoomDatabase,
) : BudgetDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfBudgetEntity: EntityInsertAdapter<BudgetEntity>

  private val __deleteAdapterOfBudgetEntity: EntityDeleteOrUpdateAdapter<BudgetEntity>

  private val __updateAdapterOfBudgetEntity: EntityDeleteOrUpdateAdapter<BudgetEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfBudgetEntity = object : EntityInsertAdapter<BudgetEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `budgets` (`id`,`categoryId`,`monthKey`,`limitPaise`) VALUES (nullif(?, 0),?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: BudgetEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.categoryId)
        statement.bindText(3, entity.monthKey)
        statement.bindLong(4, entity.limitPaise)
      }
    }
    this.__deleteAdapterOfBudgetEntity = object : EntityDeleteOrUpdateAdapter<BudgetEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `budgets` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: BudgetEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfBudgetEntity = object : EntityDeleteOrUpdateAdapter<BudgetEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `budgets` SET `id` = ?,`categoryId` = ?,`monthKey` = ?,`limitPaise` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: BudgetEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.categoryId)
        statement.bindText(3, entity.monthKey)
        statement.bindLong(4, entity.limitPaise)
        statement.bindLong(5, entity.id)
      }
    }
  }

  public override suspend fun insert(entity: BudgetEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfBudgetEntity.insertAndReturnId(_connection, entity)
    _result
  }

  public override suspend fun insertAll(entities: List<BudgetEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfBudgetEntity.insert(_connection, entities)
  }

  public override suspend fun delete(entity: BudgetEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfBudgetEntity.handle(_connection, entity)
  }

  public override suspend fun update(entity: BudgetEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfBudgetEntity.handle(_connection, entity)
  }

  public override fun observeAll(): Flow<List<BudgetEntity>> {
    val _sql: String = "SELECT * FROM budgets ORDER BY monthKey DESC"
    return createFlow(__db, false, arrayOf("budgets")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCategoryId: Int = getColumnIndexOrThrow(_stmt, "categoryId")
        val _columnIndexOfMonthKey: Int = getColumnIndexOrThrow(_stmt, "monthKey")
        val _columnIndexOfLimitPaise: Int = getColumnIndexOrThrow(_stmt, "limitPaise")
        val _result: MutableList<BudgetEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BudgetEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCategoryId: Long
          _tmpCategoryId = _stmt.getLong(_columnIndexOfCategoryId)
          val _tmpMonthKey: String
          _tmpMonthKey = _stmt.getText(_columnIndexOfMonthKey)
          val _tmpLimitPaise: Long
          _tmpLimitPaise = _stmt.getLong(_columnIndexOfLimitPaise)
          _item = BudgetEntity(_tmpId,_tmpCategoryId,_tmpMonthKey,_tmpLimitPaise)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun forMonth(monthKey: String): List<BudgetEntity> {
    val _sql: String = "SELECT * FROM budgets WHERE monthKey = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, monthKey)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCategoryId: Int = getColumnIndexOrThrow(_stmt, "categoryId")
        val _columnIndexOfMonthKey: Int = getColumnIndexOrThrow(_stmt, "monthKey")
        val _columnIndexOfLimitPaise: Int = getColumnIndexOrThrow(_stmt, "limitPaise")
        val _result: MutableList<BudgetEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: BudgetEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCategoryId: Long
          _tmpCategoryId = _stmt.getLong(_columnIndexOfCategoryId)
          val _tmpMonthKey: String
          _tmpMonthKey = _stmt.getText(_columnIndexOfMonthKey)
          val _tmpLimitPaise: Long
          _tmpLimitPaise = _stmt.getLong(_columnIndexOfLimitPaise)
          _item = BudgetEntity(_tmpId,_tmpCategoryId,_tmpMonthKey,_tmpLimitPaise)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun latestMonthBefore(monthKey: String): String? {
    val _sql: String = "SELECT DISTINCT monthKey FROM budgets WHERE monthKey < ? ORDER BY monthKey DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, monthKey)
        val _result: String?
        if (_stmt.step()) {
          if (_stmt.isNull(0)) {
            _result = null
          } else {
            _result = _stmt.getText(0)
          }
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun find(categoryId: Long, monthKey: String): BudgetEntity? {
    val _sql: String = "SELECT * FROM budgets WHERE categoryId = ? AND monthKey = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, categoryId)
        _argIndex = 2
        _stmt.bindText(_argIndex, monthKey)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCategoryId: Int = getColumnIndexOrThrow(_stmt, "categoryId")
        val _columnIndexOfMonthKey: Int = getColumnIndexOrThrow(_stmt, "monthKey")
        val _columnIndexOfLimitPaise: Int = getColumnIndexOrThrow(_stmt, "limitPaise")
        val _result: BudgetEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCategoryId: Long
          _tmpCategoryId = _stmt.getLong(_columnIndexOfCategoryId)
          val _tmpMonthKey: String
          _tmpMonthKey = _stmt.getText(_columnIndexOfMonthKey)
          val _tmpLimitPaise: Long
          _tmpLimitPaise = _stmt.getLong(_columnIndexOfLimitPaise)
          _result = BudgetEntity(_tmpId,_tmpCategoryId,_tmpMonthKey,_tmpLimitPaise)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM budgets"
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
