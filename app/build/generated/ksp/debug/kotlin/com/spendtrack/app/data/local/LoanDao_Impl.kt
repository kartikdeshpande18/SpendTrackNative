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
public class LoanDao_Impl(
  __db: RoomDatabase,
) : LoanDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLoanEntity: EntityInsertAdapter<LoanEntity>

  private val __deleteAdapterOfLoanEntity: EntityDeleteOrUpdateAdapter<LoanEntity>

  private val __updateAdapterOfLoanEntity: EntityDeleteOrUpdateAdapter<LoanEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfLoanEntity = object : EntityInsertAdapter<LoanEntity>() {
      protected override fun createQuery(): String = "INSERT OR ABORT INTO `loans` (`id`,`name`,`originalPrincipalPaise`,`currentOutstandingPaise`,`interestRateBps`,`emiPaise`,`nextDueEpochDay`,`remainingTenureMonths`,`principalPaidPaise`,`interestPaidPaise`,`includeInNetWorth`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LoanEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindLong(3, entity.originalPrincipalPaise)
        statement.bindLong(4, entity.currentOutstandingPaise)
        statement.bindLong(5, entity.interestRateBps.toLong())
        statement.bindLong(6, entity.emiPaise)
        statement.bindLong(7, entity.nextDueEpochDay)
        statement.bindLong(8, entity.remainingTenureMonths.toLong())
        statement.bindLong(9, entity.principalPaidPaise)
        statement.bindLong(10, entity.interestPaidPaise)
        val _tmp: Int = if (entity.includeInNetWorth) 1 else 0
        statement.bindLong(11, _tmp.toLong())
      }
    }
    this.__deleteAdapterOfLoanEntity = object : EntityDeleteOrUpdateAdapter<LoanEntity>() {
      protected override fun createQuery(): String = "DELETE FROM `loans` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: LoanEntity) {
        statement.bindLong(1, entity.id)
      }
    }
    this.__updateAdapterOfLoanEntity = object : EntityDeleteOrUpdateAdapter<LoanEntity>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `loans` SET `id` = ?,`name` = ?,`originalPrincipalPaise` = ?,`currentOutstandingPaise` = ?,`interestRateBps` = ?,`emiPaise` = ?,`nextDueEpochDay` = ?,`remainingTenureMonths` = ?,`principalPaidPaise` = ?,`interestPaidPaise` = ?,`includeInNetWorth` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: LoanEntity) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.name)
        statement.bindLong(3, entity.originalPrincipalPaise)
        statement.bindLong(4, entity.currentOutstandingPaise)
        statement.bindLong(5, entity.interestRateBps.toLong())
        statement.bindLong(6, entity.emiPaise)
        statement.bindLong(7, entity.nextDueEpochDay)
        statement.bindLong(8, entity.remainingTenureMonths.toLong())
        statement.bindLong(9, entity.principalPaidPaise)
        statement.bindLong(10, entity.interestPaidPaise)
        val _tmp: Int = if (entity.includeInNetWorth) 1 else 0
        statement.bindLong(11, _tmp.toLong())
        statement.bindLong(12, entity.id)
      }
    }
  }

  public override suspend fun insert(entity: LoanEntity): Long = performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfLoanEntity.insertAndReturnId(_connection, entity)
    _result
  }

  public override suspend fun insertAll(entities: List<LoanEntity>): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfLoanEntity.insert(_connection, entities)
  }

  public override suspend fun delete(entity: LoanEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfLoanEntity.handle(_connection, entity)
  }

  public override suspend fun update(entity: LoanEntity): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfLoanEntity.handle(_connection, entity)
  }

  public override fun observeAll(): Flow<List<LoanEntity>> {
    val _sql: String = "SELECT * FROM loans ORDER BY nextDueEpochDay ASC"
    return createFlow(__db, false, arrayOf("loans")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfName: Int = getColumnIndexOrThrow(_stmt, "name")
        val _columnIndexOfOriginalPrincipalPaise: Int = getColumnIndexOrThrow(_stmt, "originalPrincipalPaise")
        val _columnIndexOfCurrentOutstandingPaise: Int = getColumnIndexOrThrow(_stmt, "currentOutstandingPaise")
        val _columnIndexOfInterestRateBps: Int = getColumnIndexOrThrow(_stmt, "interestRateBps")
        val _columnIndexOfEmiPaise: Int = getColumnIndexOrThrow(_stmt, "emiPaise")
        val _columnIndexOfNextDueEpochDay: Int = getColumnIndexOrThrow(_stmt, "nextDueEpochDay")
        val _columnIndexOfRemainingTenureMonths: Int = getColumnIndexOrThrow(_stmt, "remainingTenureMonths")
        val _columnIndexOfPrincipalPaidPaise: Int = getColumnIndexOrThrow(_stmt, "principalPaidPaise")
        val _columnIndexOfInterestPaidPaise: Int = getColumnIndexOrThrow(_stmt, "interestPaidPaise")
        val _columnIndexOfIncludeInNetWorth: Int = getColumnIndexOrThrow(_stmt, "includeInNetWorth")
        val _result: MutableList<LoanEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: LoanEntity
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpName: String
          _tmpName = _stmt.getText(_columnIndexOfName)
          val _tmpOriginalPrincipalPaise: Long
          _tmpOriginalPrincipalPaise = _stmt.getLong(_columnIndexOfOriginalPrincipalPaise)
          val _tmpCurrentOutstandingPaise: Long
          _tmpCurrentOutstandingPaise = _stmt.getLong(_columnIndexOfCurrentOutstandingPaise)
          val _tmpInterestRateBps: Int
          _tmpInterestRateBps = _stmt.getLong(_columnIndexOfInterestRateBps).toInt()
          val _tmpEmiPaise: Long
          _tmpEmiPaise = _stmt.getLong(_columnIndexOfEmiPaise)
          val _tmpNextDueEpochDay: Long
          _tmpNextDueEpochDay = _stmt.getLong(_columnIndexOfNextDueEpochDay)
          val _tmpRemainingTenureMonths: Int
          _tmpRemainingTenureMonths = _stmt.getLong(_columnIndexOfRemainingTenureMonths).toInt()
          val _tmpPrincipalPaidPaise: Long
          _tmpPrincipalPaidPaise = _stmt.getLong(_columnIndexOfPrincipalPaidPaise)
          val _tmpInterestPaidPaise: Long
          _tmpInterestPaidPaise = _stmt.getLong(_columnIndexOfInterestPaidPaise)
          val _tmpIncludeInNetWorth: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIncludeInNetWorth).toInt()
          _tmpIncludeInNetWorth = _tmp != 0
          _item = LoanEntity(_tmpId,_tmpName,_tmpOriginalPrincipalPaise,_tmpCurrentOutstandingPaise,_tmpInterestRateBps,_tmpEmiPaise,_tmpNextDueEpochDay,_tmpRemainingTenureMonths,_tmpPrincipalPaidPaise,_tmpInterestPaidPaise,_tmpIncludeInNetWorth)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clear() {
    val _sql: String = "DELETE FROM loans"
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
