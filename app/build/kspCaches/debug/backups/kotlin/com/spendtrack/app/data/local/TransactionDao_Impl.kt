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
public class TransactionDao_Impl(
  __db: RoomDatabase,
) : TransactionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfTransactionEntity: EntityInsertAdapter<TransactionEntity>

  private val __spendTrackConverters: SpendTrackConverters = SpendTrackConverters()

  private val __deleteAdapterOfTransactionEntity: EntityDeleteOrUpdateAdapter<TransactionEntity>

  private val __updateAdapterOfTransactionEntity: EntityDeleteOrUpdateAdapter<TransactionEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfTransactionEntity = object : EntityInsertAdapter<TransactionEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `transactions` (`id`,`dateEpochDay`,`description`,`amountPaise`,`categoryId`,`accountId`,`type`,`isShared`,`mySharePaise`,`sharedWith`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: TransactionEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.dateEpochDay)
        statement.bindText(3, entity.description)
        statement.bindLong(4, entity.amountPaise)
        statement.bindLong(5, entity.categoryId)
        statement.bindLong(6, entity.accountId)
        val _tmp: String = __spendTrackConverters.transactionTypeToString(entity.type)
        statement.bindText(7, _tmp)
        val _tmp_1: Int = if (entity.isShared) 1 else 0
        statement.bindLong(8, _tmp_1.toLong())
        val _tmpMySharePaise: Long? = entity.mySharePaise
        if (_tmpMySharePaise == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpMySharePaise)
        }
        statement.bindText(10, entity.sharedWith)
      }
    }
    this.__deleteAdapterOfTransactionEntity = object : EntityDeleteOrUpdateAdapter<TransactionEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `transactions` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: TransactionEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfTransactionEntity = object : EntityDeleteOrUpdateAdapter<TransactionEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `transactions` SET `id` = ?,`dateEpochDay` = ?,`description` = ?,`amountPaise` = ?,`categoryId` = ?,`accountId` = ?,`type` = ?,`isShared` = ?,`mySharePaise` = ?,`sharedWith` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: TransactionEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.dateEpochDay)
        statement.bindText(3, entity.description)
        statement.bindLong(4, entity.amountPaise)
        statement.bindLong(5, entity.categoryId)
        statement.bindLong(6, entity.accountId)
        val _tmp: String = __spendTrackConverters.transactionTypeToString(entity.type)
        statement.bindText(7, _tmp)
        val _tmp_1: Int = if (entity.isShared) 1 else 0
        statement.bindLong(8, _tmp_1.toLong())
        val _tmpMySharePaise: Long? = entity.mySharePaise
        if (_tmpMySharePaise == null) {
          statement.bindNull(9)
        } else {
          statement.bindLong(9, _tmpMySharePaise)
        }
        statement.bindText(10, entity.sharedWith)
        statement.bindLong(11, entity.id)
      }
    }
  }

  public override suspend fun insert(entity: TransactionEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfTransactionEntity.insertAndReturnId(_connection, entity)
    _result
  }

  public override suspend fun insertAll(entities: List<TransactionEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfTransactionEntity.insert(_connection, entities)
  }

  public override suspend fun delete(entity: TransactionEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfTransactionEntity.handle(_connection, entity)
  }

  public override suspend fun update(entity: TransactionEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfTransactionEntity.handle(_connection, entity)
  }

  public override fun observeAll(): Flow<List<TransactionEntity>> {
    val _sql: String = "SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC"
    return createFlow(__db, false, arrayOf("transactions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDateEpochDay: Int = getColumnIndexOrThrow(_stmt, "dateEpochDay")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfAmountPaise: Int = getColumnIndexOrThrow(_stmt, "amountPaise")
        val _columnIndexOfCategoryId: Int = getColumnIndexOrThrow(_stmt, "categoryId")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "accountId")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfIsShared: Int = getColumnIndexOrThrow(_stmt, "isShared")
        val _columnIndexOfMySharePaise: Int = getColumnIndexOrThrow(_stmt, "mySharePaise")
        val _columnIndexOfSharedWith: Int = getColumnIndexOrThrow(_stmt, "sharedWith")
        val _result: MutableList<TransactionEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: TransactionEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDateEpochDay: Long
          _tmpDateEpochDay = _stmt.getLong(_columnIndexOfDateEpochDay)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpAmountPaise: Long
          _tmpAmountPaise = _stmt.getLong(_columnIndexOfAmountPaise)
          val _tmpCategoryId: Long
          _tmpCategoryId = _stmt.getLong(_columnIndexOfCategoryId)
          val _tmpAccountId: Long
          _tmpAccountId = _stmt.getLong(_columnIndexOfAccountId)
          val _tmpType: TransactionType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfType)
          _tmpType = __spendTrackConverters.stringToTransactionType(_tmp)
          val _tmpIsShared: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsShared).toInt()
          _tmpIsShared = _tmp_1 != 0
          val _tmpMySharePaise: Long?
          if (_stmt.isNull(_columnIndexOfMySharePaise)) {
            _tmpMySharePaise = null
          } else {
            _tmpMySharePaise = _stmt.getLong(_columnIndexOfMySharePaise)
          }
          val _tmpSharedWith: String
          _tmpSharedWith = _stmt.getText(_columnIndexOfSharedWith)
          _item = TransactionEntity(_tmpId,_tmpDateEpochDay,_tmpDescription,_tmpAmountPaise,_tmpCategoryId,_tmpAccountId,_tmpType,_tmpIsShared,_tmpMySharePaise,_tmpSharedWith)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getById(id: Long): TransactionEntity? {
    val _sql: String = "SELECT * FROM transactions WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfDateEpochDay: Int = getColumnIndexOrThrow(_stmt, "dateEpochDay")
        val _columnIndexOfDescription: Int = getColumnIndexOrThrow(_stmt, "description")
        val _columnIndexOfAmountPaise: Int = getColumnIndexOrThrow(_stmt, "amountPaise")
        val _columnIndexOfCategoryId: Int = getColumnIndexOrThrow(_stmt, "categoryId")
        val _columnIndexOfAccountId: Int = getColumnIndexOrThrow(_stmt, "accountId")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfIsShared: Int = getColumnIndexOrThrow(_stmt, "isShared")
        val _columnIndexOfMySharePaise: Int = getColumnIndexOrThrow(_stmt, "mySharePaise")
        val _columnIndexOfSharedWith: Int = getColumnIndexOrThrow(_stmt, "sharedWith")
        val _result: TransactionEntity?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpDateEpochDay: Long
          _tmpDateEpochDay = _stmt.getLong(_columnIndexOfDateEpochDay)
          val _tmpDescription: String
          _tmpDescription = _stmt.getText(_columnIndexOfDescription)
          val _tmpAmountPaise: Long
          _tmpAmountPaise = _stmt.getLong(_columnIndexOfAmountPaise)
          val _tmpCategoryId: Long
          _tmpCategoryId = _stmt.getLong(_columnIndexOfCategoryId)
          val _tmpAccountId: Long
          _tmpAccountId = _stmt.getLong(_columnIndexOfAccountId)
          val _tmpType: TransactionType
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfType)
          _tmpType = __spendTrackConverters.stringToTransactionType(_tmp)
          val _tmpIsShared: Boolean
          val _tmp_1: Int
          _tmp_1 = _stmt.getLong(_columnIndexOfIsShared).toInt()
          _tmpIsShared = _tmp_1 != 0
          val _tmpMySharePaise: Long?
          if (_stmt.isNull(_columnIndexOfMySharePaise)) {
            _tmpMySharePaise = null
          } else {
            _tmpMySharePaise = _stmt.getLong(_columnIndexOfMySharePaise)
          }
          val _tmpSharedWith: String
          _tmpSharedWith = _stmt.getText(_columnIndexOfSharedWith)
          _result = TransactionEntity(_tmpId,_tmpDateEpochDay,_tmpDescription,_tmpAmountPaise,_tmpCategoryId,_tmpAccountId,_tmpType,_tmpIsShared,_tmpMySharePaise,_tmpSharedWith)
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
    val _sql: String = "DELETE FROM transactions"
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
