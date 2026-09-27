package com.spendtrack.app.importer.model

import android.net.Uri
import com.spendtrack.app.importer.detector.StatementFormat

data class StatementFileEntry(
    val uri: Uri? = null,
    val filename: String,
    val format: StatementFormat,
    val fileHash: String = "",
    val isAlreadyImported: Boolean = false,
    val rawText: String = "",
    val transactionCount: Int = 0,
    val isReconciled: Boolean = false,
)

data class ImportBatch(
    val files: List<StatementFileEntry>,
    val accountMappings: Map<String, Long>, // filename -> accountId
    val transactions: List<NormalizedTransaction>
)
