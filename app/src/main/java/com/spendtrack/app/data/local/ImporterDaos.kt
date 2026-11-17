package com.spendtrack.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ImportHistoryDao {
    @Insert
    suspend fun insert(history: ImportHistoryEntity): Long

    @Insert
    suspend fun insertAll(entities: List<ImportHistoryEntity>)

    @Query("SELECT * FROM import_history")
    suspend fun getAll(): List<ImportHistoryEntity>

    @Query("SELECT * FROM import_history WHERE fileHash = :hash LIMIT 1")
    suspend fun getByFileHash(hash: String): ImportHistoryEntity?

    @Query("DELETE FROM import_history")
    suspend fun clear()
}
