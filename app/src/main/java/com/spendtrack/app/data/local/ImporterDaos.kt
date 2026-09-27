package com.spendtrack.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ImportHistoryDao {
    @Insert
    suspend fun insert(history: ImportHistoryEntity): Long
    
    @Query("SELECT * FROM import_history WHERE fileHash = :hash LIMIT 1")
    suspend fun getByFileHash(hash: String): ImportHistoryEntity?
}
