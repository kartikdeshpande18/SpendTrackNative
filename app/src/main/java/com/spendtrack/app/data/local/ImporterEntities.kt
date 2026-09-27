package com.spendtrack.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_history")
data class ImportHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filename: String,
    val fileHash: String,
    val format: String,
    val accountId: Long,
    val importTimestampEpochMs: Long,
    val transactionsImported: Int,
    val duplicatesSkipped: Int,
)
