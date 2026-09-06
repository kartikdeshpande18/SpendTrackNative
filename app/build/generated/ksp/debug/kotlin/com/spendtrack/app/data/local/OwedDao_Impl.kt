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
public class OwedDao_Impl(
  __db: RoomDatabase,
) : OwedDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfOwedEntity: EntityInsertAdapter<OwedEntity>

  private val __spendTrackConverters: SpendTrackConverters = SpendTrackConverters()

  private val __deleteAdapterOfOwedEntity: EntityDeleteOrUpdateAdapter<OwedEntity>

  private val __updateAdapterOfOwedEntity: EntityDeleteOrUpdateAdapter<OwedEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfOwedEntity = object : EntityInsertAdapter<OwedEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `owed` (`id`,`personName`,`amountPaise`,`direction`,`note`,`sourceTransactionId`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: OwedEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.personName)
        statement.bindLong(3, entity.amountPaise)
        val _tmp: String = __spendTrackConverters.owedDirectionToString(entity.direction)
        statement.bindText(4, _tmp)
        statement.bindText(5, entity.note)
        val _tmpSourceTransactionId: Long? = entity.sourceTransactionId
        if (_tmpSourceTransactionId == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpSourceTransactionId)
        }
      }
    }
    this.__deleteAdapterOfOwedEntity = object : EntityDeleteOrUpdateAdapter<OwedEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `owed` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: OwedEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfOwedEntity = object : EntityDeleteOrUpdateAdapter<OwedEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `owed` SET `id` = ?,`personName` = ?,`amountPaise` = ?,`direction` = ?,`note` = ?,`sourceTransactionId` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: OwedEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.personName)
        statement.bindLong(3, entity.amountPaise)
        val _tmp: String = __spendTrackConverters.owedDirectionToString(entity.direction)
        statement.bindText(4, _tmp)
        statement.bindText(5, entity.note)
        val _tmpSourceTransactionId: Long? = entity.sourceTransactionId
        if (_tmpSourceTransactionId == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpSourceTransactionId)
        }
        statement.bindLong(7, entity.id)
      }
    }
  }

  public override suspend fun insert(entity: OwedEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfOwedEntity.insertAndReturnId(_connection, entity)
    _result
  }

  public override suspend fun insertAll(entities: List<OwedEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfOwedEntity.insert(_connection, entities)
  }

  public override suspend fun delete(entity: OwedEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfOwedEntity.handle(_connection, entity)
  }

  public override suspend fun update(entity: OwedEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfOwedEntity.handle(_connection, entity)
  }

  public override fun observeAll(): Flow<List<OwedEntity>> {
    val _sql: String = "SELECT * FROM owed ORDER BY personName ASC"
    return createFlow(__db, false, arrayOf("owed")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfPersonName: Int = getColumnIndexOrThrow(_stmt, "personName")
        val _columnIndexOfAmountPaise: Int = getColumnIndexOrThrow(_stmt, "amountPaise")
        val _columnIndexOfDirection: Int = getColumnIndexOrThrow(_stmt, "direction")
        val _columnIndexOfNote: Int = getColumnIndexOrThrow(_stmt, "note")
        val _columnIndexOfSourceTransactionId: Int = getColumnIndexOrThrow(_stmt, "sourceTransactionId")
        val _result: MutableList<OwedEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: OwedEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpPersonName: String
          _tmpPersonName = _stmt.getText(_columnIndexOfPersonName)
          val _tmpAmountPaise: Long
          _tmpAmountPaise = _stmt.getLong(_columnIndexOfAmountPaise)
          val _tmpDirection: OwedDirection
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfDirection)
          _tmpDirection = __spendTrackConverters.stringToOwedDirection(_tmp)
          val _tmpNote: String
          _tmpNote = _stmt.getText(_columnIndexOfNote)
          val _tmpSourceTransactionId: Long?
          if (_stmt.isNull(_columnIndexOfSourceTransactionId)) {
            _tmpSourceTransactionId = null
          } else {
            _tmpSourceTransactionId = _stmt.getLong(_columnIndexOfSourceTransactionId)
          }
          _item = OwedEntity(_tmpId,_tmpPersonName,_tmpAmountPaise,_tmpDirection,_tmpNote,_tmpSourceTransactionId)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM owed"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteForTransaction(transactionId: Long) {
    val _sql: String = "DELETE FROM owed WHERE sourceTransactionId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, transactionId)
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
