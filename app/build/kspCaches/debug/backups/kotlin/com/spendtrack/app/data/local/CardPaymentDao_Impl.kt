package com.spendtrack.app.`data`.local

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
public class CardPaymentDao_Impl(
  __db: RoomDatabase,
) : CardPaymentDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfCardPaymentEntity: EntityInsertAdapter<CardPaymentEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfCardPaymentEntity = object : EntityInsertAdapter<CardPaymentEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `card_payments` (`id`,`cardAccountId`,`sourceAccountId`,`amountPaise`,`dateEpochDay`) VALUES (nullif(?, 0),?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: CardPaymentEntity) {
        statement.bindLong(1, entity.id)
        statement.bindLong(2, entity.cardAccountId)
        statement.bindLong(3, entity.sourceAccountId)
        statement.bindLong(4, entity.amountPaise)
        statement.bindLong(5, entity.dateEpochDay)
      }
    }
  }

  public override suspend fun insert(entity: CardPaymentEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfCardPaymentEntity.insertAndReturnId(_connection, entity)
    _result
  }

  public override suspend fun insertAll(entities: List<CardPaymentEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfCardPaymentEntity.insert(_connection, entities)
  }

  public override fun observeAll(): Flow<List<CardPaymentEntity>> {
    val _sql: String = "SELECT * FROM card_payments ORDER BY dateEpochDay DESC, id DESC"
    return createFlow(__db, false, arrayOf("card_payments")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfCardAccountId: Int = getColumnIndexOrThrow(_stmt, "cardAccountId")
        val _columnIndexOfSourceAccountId: Int = getColumnIndexOrThrow(_stmt, "sourceAccountId")
        val _columnIndexOfAmountPaise: Int = getColumnIndexOrThrow(_stmt, "amountPaise")
        val _columnIndexOfDateEpochDay: Int = getColumnIndexOrThrow(_stmt, "dateEpochDay")
        val _result: MutableList<CardPaymentEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: CardPaymentEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpCardAccountId: Long
          _tmpCardAccountId = _stmt.getLong(_columnIndexOfCardAccountId)
          val _tmpSourceAccountId: Long
          _tmpSourceAccountId = _stmt.getLong(_columnIndexOfSourceAccountId)
          val _tmpAmountPaise: Long
          _tmpAmountPaise = _stmt.getLong(_columnIndexOfAmountPaise)
          val _tmpDateEpochDay: Long
          _tmpDateEpochDay = _stmt.getLong(_columnIndexOfDateEpochDay)
          _item = CardPaymentEntity(_tmpId,_tmpCardAccountId,_tmpSourceAccountId,_tmpAmountPaise,_tmpDateEpochDay)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM card_payments"
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
