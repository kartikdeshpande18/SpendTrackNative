package com.spendtrack.app.importer.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.importer.classification.Categorizer
import com.spendtrack.app.importer.detector.PdfTextExtractor
import com.spendtrack.app.importer.detector.StatementDetector
import com.spendtrack.app.importer.detector.StatementFormat
import com.spendtrack.app.importer.duplicate.DuplicateDetector
import com.spendtrack.app.importer.matching.TransferMatcher
import com.spendtrack.app.importer.model.ImportBatch
import com.spendtrack.app.importer.model.ImportStatus
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import com.spendtrack.app.importer.model.ReviewStatus
import com.spendtrack.app.importer.model.StatementFileEntry
import com.spendtrack.app.importer.normalization.MerchantNormalizer
import com.spendtrack.app.importer.parser.StatementParser
import com.spendtrack.app.importer.reconciliation.Reconciler
import com.spendtrack.app.importer.repository.ImportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

sealed class ImportState {
    object Idle : ImportState()
    data class Analyzing(val message: String) : ImportState()
    data class AccountMapping(
        val files: List<StatementFileEntry>,
        val mappings: Map<String, Long> // filename -> accountId
    ) : ImportState()
    data class Review(
        val batch: ImportBatch,
        val currentFilter: ReviewFilter = ReviewFilter.ALL
    ) : ImportState()
    object Importing : ImportState()
    data class Success(
        val importedCount: Int,
        val excludedCount: Int,
        val duplicatesSkippedCount: Int,
        val transfersCount: Int,
        val cardPaymentsCount: Int
    ) : ImportState()
    data class Error(val message: String) : ImportState()
}

enum class ReviewFilter {
    ALL,
    PENDING,
    ACCEPTED,
    REMOVED
}

class ImportViewModel(
    private val importRepository: ImportRepository,
    private val parsers: Map<StatementFormat, StatementParser>,
    private val customScope: kotlinx.coroutines.CoroutineScope? = null
) : ViewModel() {

    private val scope: kotlinx.coroutines.CoroutineScope
        get() = customScope ?: viewModelScope

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state

    private fun updateState(newState: ImportState) {
        val tag = "SpendTrackImport"
        val stateName = when (newState) {
            is ImportState.Idle -> "Idle"
            is ImportState.Analyzing -> "Analyzing"
            is ImportState.AccountMapping -> "AccountMapping"
            is ImportState.Review -> "Review"
            is ImportState.Importing -> "Importing"
            is ImportState.Success -> "Success"
            is ImportState.Error -> "Error"
        }
        runCatching {
            android.util.Log.d(tag, "IMPORT_UI_STATE=$stateName")
        }
        _state.value = newState
    }

    private fun logAction(action: String) {
        runCatching {
            android.util.Log.d("SpendTrackImport", "IMPORT_ACTION=$action")
        }
    }

    private var currentFiles = mutableListOf<StatementFileEntry>()
    private var currentMappings = mutableMapOf<String, Long>()

    /**
     * Step 1 & 2: Select PDF(s) and detect format locally.
     * Absolutely ZERO database writes occur here.
     * Transitions directly to AccountMapping (or Error if unsupported/already imported).
     */
    fun selectFiles(
        context: Context,
        uris: List<Uri>,
        accounts: List<AccountEntity>
    ) {
        if (uris.isEmpty()) return
        logAction("selectFiles_started")
        updateState(ImportState.Analyzing("Analyzing statement files..."))

        scope.launch(Dispatchers.IO) {
            try {
                val fileEntries = mutableListOf<StatementFileEntry>()
                val prefs = context.getSharedPreferences("statement_account_mappings", Context.MODE_PRIVATE)

                for (uri in uris) {
                    val filename = uri.lastPathSegment ?: "statement.pdf"
                    val text = PdfTextExtractor.extractText(context, uri)
                    if (text == null) {
                        fileEntries.add(
                            StatementFileEntry(
                                uri = uri,
                                filename = filename,
                                format = StatementFormat.UNSUPPORTED,
                                fileHash = "",
                                isAlreadyImported = false,
                                rawText = ""
                            )
                        )
                        continue
                    }

                    val hash = computeSha256(text)
                    val isAlreadyImported = importRepository.checkIsAlreadyImported(hash)
                    val format = StatementDetector.detectFormat(text)

                    fileEntries.add(
                        StatementFileEntry(
                            uri = uri,
                            filename = filename,
                            format = format,
                            fileHash = hash,
                            isAlreadyImported = isAlreadyImported,
                            rawText = text
                        )
                    )
                }

                currentFiles = fileEntries

                val validFiles = fileEntries.filter { it.format != StatementFormat.UNSUPPORTED && !it.isAlreadyImported }
                if (validFiles.isEmpty()) {
                    val alreadyImportedCount = fileEntries.count { it.isAlreadyImported }
                    val unsupportedCount = fileEntries.count { it.format == StatementFormat.UNSUPPORTED }
                    val msg = buildString {
                        append("Cannot import selected files: ")
                        if (alreadyImportedCount > 0) append("$alreadyImportedCount file(s) already imported. ")
                        if (unsupportedCount > 0) append("$unsupportedCount file(s) unsupported format.")
                    }
                    updateState(ImportState.Error(msg))
                    return@launch
                }

                // Heuristic / saved account mapping
                val initialMappings = mutableMapOf<String, Long>()
                for (file in validFiles) {
                    val savedAccountId = prefs.getLong("mapping_${file.format.name}", -1L)
                    val matchedAccount = if (savedAccountId > 0 && accounts.any { it.id == savedAccountId }) {
                        accounts.first { it.id == savedAccountId }
                    } else {
                        when (file.format) {
                            StatementFormat.HDFC_SAVINGS -> accounts.firstOrNull { it.name.contains("hdfc", ignoreCase = true) && !it.isCreditCard }
                            StatementFormat.ICICI_SAVINGS -> accounts.firstOrNull { it.name.contains("icici", ignoreCase = true) && !it.isCreditCard }
                            StatementFormat.ICICI_CREDIT_CARD -> accounts.firstOrNull { it.name.contains("icici", ignoreCase = true) && it.isCreditCard }
                                ?: accounts.firstOrNull { it.name.contains("coral", ignoreCase = true) || it.name.contains("amazon", ignoreCase = true) }
                            StatementFormat.SLICE_SAVINGS -> accounts.firstOrNull { it.name.contains("slice", ignoreCase = true) && !it.isCreditCard }
                            StatementFormat.SLICE_CREDIT_CARD -> accounts.firstOrNull { it.name.contains("slice", ignoreCase = true) && it.isCreditCard }
                            StatementFormat.SLICE_ATOM -> accounts.firstOrNull { it.name.contains("atom", ignoreCase = true) || it.name.contains("emergency", ignoreCase = true) }
                            StatementFormat.SBI_SAVINGS -> accounts.firstOrNull { (it.name.contains("sbi", ignoreCase = true) || it.name.contains("state bank", ignoreCase = true)) && !it.isCreditCard }
                            StatementFormat.KOTAK_SAVINGS -> accounts.firstOrNull { (it.name.contains("kotak", ignoreCase = true) || it.name.contains("811", ignoreCase = true)) && !it.isCreditCard }
                            else -> null
                        } ?: accounts.firstOrNull()
                    }

                    if (matchedAccount != null) {
                        initialMappings[file.filename] = matchedAccount.id
                    }
                }

                currentMappings = initialMappings
                updateState(ImportState.AccountMapping(
                    files = validFiles,
                    mappings = initialMappings
                ))
            } catch (e: Exception) {
                updateState(ImportState.Error("Error inspecting statement: ${e.message}"))
            }
        }
    }

    /**
     * Updates mapped SpendTrack account for a specific file.
     */
    fun updateAccountMapping(filename: String, accountId: Long) {
        val currentState = _state.value
        if (currentState is ImportState.AccountMapping) {
            val newMappings = currentState.mappings.toMutableMap()
            newMappings[filename] = accountId
            currentMappings = newMappings
            updateState(currentState.copy(mappings = newMappings))
        }
    }

    /**
     * Step 3 -> 4: Confirms account mappings, parses files locally, and generates temporary review batch.
     * Transitions directly to the MANDATORY Review Screen.
     * Absolutely ZERO database writes occur here.
     */
    fun confirmAccountMapping(
        context: Context? = null,
        categories: List<CategoryEntity>,
        existingTransactions: List<TransactionEntity>
    ) {
        val currentState = _state.value as? ImportState.AccountMapping ?: return
        logAction("accountMappingConfirmed")
        val validFiles = currentState.files
        val mappings = currentState.mappings

        for (f in validFiles) {
            if ((mappings[f.filename] ?: 0L) <= 0L) {
                updateState(ImportState.Error("Please select a SpendTrack account for ${f.filename}"))
                return
            }
        }

        updateState(ImportState.Analyzing("Parsing statement transactions..."))

        scope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    context?.getSharedPreferences("statement_account_mappings", Context.MODE_PRIVATE)?.let { prefs ->
                        val editor = prefs.edit()
                        for (file in validFiles) {
                            mappings[file.filename]?.let { acctId ->
                                editor.putLong("mapping_${file.format.name}", acctId)
                            }
                        }
                        editor.apply()
                    }
                }

                val allTransactions = mutableListOf<NormalizedTransaction>()
                val parsedFiles = mutableListOf<StatementFileEntry>()

                for (file in validFiles) {
                    val parser = parsers[file.format] ?: continue
                    val accountId = mappings[file.filename] ?: 0L
                    val parsed = parser.parse(file.rawText, file.filename)

                    val normalized = parsed.map { tx ->
                        val normMerchant = MerchantNormalizer.normalize(tx.rawNarration)
                        val cat = Categorizer.categorize(tx.copy(normalizedMerchant = normMerchant), categories)
                        tx.copy(
                            sourceAccountId = accountId,
                            normalizedMerchant = normMerchant,
                            categoryId = cat?.id,
                            sourceFilename = file.filename,
                            duplicateFingerprint = "${file.fileHash}_${tx.dateEpochDay}_${tx.amountPaise}_${tx.direction.name}",
                            reviewStatus = ReviewStatus.PENDING
                        )
                    }

                    val isReconciled = Reconciler.reconcile(normalized).isSuccessful
                    parsedFiles.add(
                        file.copy(
                            transactionCount = normalized.size,
                            isReconciled = isReconciled
                        )
                    )
                    allTransactions.addAll(normalized)
                }

                // In-memory duplicate & transfer detection
                val afterDuplicates = DuplicateDetector.flagDuplicates(allTransactions, existingTransactions)
                val afterTransfers = TransferMatcher.matchTransfers(afterDuplicates)

                val batch = ImportBatch(
                    files = parsedFiles,
                    accountMappings = mappings,
                    transactions = afterTransfers
                )

                // MANDATORY transition to REVIEW SCREEN
                logAction("parseCompleted_transitioningToReview")
                updateState(ImportState.Review(
                    batch = batch,
                    currentFilter = ReviewFilter.ALL
                ))
            } catch (e: Exception) {
                e.printStackTrace()
                updateState(ImportState.Error("Parsing failed: ${e.message}"))
            }
        }
    }

    /**
     * Parse text directly for a single format and go directly to Review state (used in headless testing and single-statement flows).
     */
    fun parseStatementText(
        format: StatementFormat,
        text: String,
        filename: String,
        accountId: Long,
        categories: List<CategoryEntity> = emptyList(),
        existingTransactions: List<TransactionEntity> = emptyList()
    ) {
        val parser = parsers[format] ?: run {
            updateState(ImportState.Error("Unsupported format"))
            return
        }
        val fileHash = computeSha256(text)
        val parsed = parser.parse(text, filename)
        val normalized = parsed.map { tx ->
            val normMerchant = MerchantNormalizer.normalize(tx.rawNarration)
            val cat = Categorizer.categorize(tx.copy(normalizedMerchant = normMerchant), categories)
            tx.copy(
                sourceAccountId = accountId,
                normalizedMerchant = normMerchant,
                categoryId = cat?.id,
                sourceFilename = filename,
                duplicateFingerprint = "${fileHash}_${tx.dateEpochDay}_${tx.amountPaise}_${tx.direction.name}",
                reviewStatus = ReviewStatus.PENDING
            )
        }
        val afterDuplicates = DuplicateDetector.flagDuplicates(normalized, existingTransactions)
        val afterTransfers = TransferMatcher.matchTransfers(afterDuplicates)
        val fileEntry = StatementFileEntry(
            filename = filename,
            format = format,
            fileHash = fileHash,
            transactionCount = normalized.size,
            isReconciled = Reconciler.reconcile(normalized).isSuccessful,
            rawText = text
        )
        val batch = ImportBatch(
            files = listOf(fileEntry),
            accountMappings = mapOf(filename to accountId),
            transactions = afterTransfers
        )
        updateState(ImportState.Review(batch = batch, currentFilter = ReviewFilter.ALL))
    }

    fun setReviewFilter(filter: ReviewFilter) {
        val currentState = _state.value
        if (currentState is ImportState.Review) {
            updateState(currentState.copy(currentFilter = filter))
        }
    }

    fun acceptTransaction(txId: String) {
        updateTransactionInReview(txId) { it.copy(reviewStatus = ReviewStatus.ACCEPTED) }
    }

    fun removeTransaction(txId: String) {
        updateTransactionInReview(txId) { it.copy(reviewStatus = ReviewStatus.REMOVED) }
    }

    fun undoStatus(txId: String) {
        updateTransactionInReview(txId) { it.copy(reviewStatus = ReviewStatus.PENDING) }
    }

    fun editTransaction(
        txId: String,
        merchant: String,
        amountPaise: Long,
        dateEpochDay: Long,
        type: ImporterTransactionType,
        categoryId: Long?,
        sourceAccountId: Long,
        destinationAccountId: Long?
    ) {
        updateTransactionInReview(txId) { tx ->
            tx.copy(
                normalizedMerchant = merchant,
                amountPaise = amountPaise,
                dateEpochDay = dateEpochDay,
                transactionType = type,
                categoryId = categoryId,
                sourceAccountId = sourceAccountId,
                destinationAccountId = destinationAccountId,
                reviewStatus = ReviewStatus.EDITED,
                isEdited = true
            )
        }
    }

    private fun updateTransactionInReview(txId: String, transform: (NormalizedTransaction) -> NormalizedTransaction) {
        val currentState = _state.value
        if (currentState is ImportState.Review) {
            val updatedTransactions = currentState.batch.transactions.map { tx ->
                if (tx.id == txId) transform(tx) else tx
            }
            updateState(currentState.copy(
                batch = currentState.batch.copy(transactions = updatedTransactions)
            ))
        }
    }

    fun acceptAllHighConfidence() {
        val currentState = _state.value as? ImportState.Review ?: return
        val updated = currentState.batch.transactions.map { tx ->
            if (tx.status == ImportStatus.NEW && tx.reviewStatus == ReviewStatus.PENDING) {
                tx.copy(reviewStatus = ReviewStatus.ACCEPTED)
            } else tx
        }
        updateState(currentState.copy(batch = currentState.batch.copy(transactions = updated)))
    }

    fun removeAllDuplicates() {
        val currentState = _state.value as? ImportState.Review ?: return
        val updated = currentState.batch.transactions.map { tx ->
            if ((tx.status == ImportStatus.DUPLICATE || tx.status == ImportStatus.POSSIBLE_DUPLICATE) &&
                tx.reviewStatus == ReviewStatus.PENDING) {
                tx.copy(reviewStatus = ReviewStatus.REMOVED)
            } else tx
        }
        updateState(currentState.copy(batch = currentState.batch.copy(transactions = updated)))
    }

    fun acceptAllFromAccount(accountId: Long) {
        val currentState = _state.value as? ImportState.Review ?: return
        val updated = currentState.batch.transactions.map { tx ->
            if (tx.sourceAccountId == accountId && tx.reviewStatus == ReviewStatus.PENDING) {
                tx.copy(reviewStatus = ReviewStatus.ACCEPTED)
            } else tx
        }
        updateState(currentState.copy(batch = currentState.batch.copy(transactions = updated)))
    }

    fun acceptAllOfType(type: ImporterTransactionType) {
        val currentState = _state.value as? ImportState.Review ?: return
        val updated = currentState.batch.transactions.map { tx ->
            if (tx.transactionType == type && tx.reviewStatus == ReviewStatus.PENDING) {
                tx.copy(reviewStatus = ReviewStatus.ACCEPTED)
            } else tx
        }
        updateState(currentState.copy(batch = currentState.batch.copy(transactions = updated)))
    }

    fun cancelImport() {
        currentFiles.clear()
        currentMappings.clear()
        updateState(ImportState.Idle)
    }

    /**
     * Final user confirmation.
     * ONLY HERE are accepted transactions committed to SpendTrack Room database.
     */
    fun confirmImport() {
        val currentState = _state.value as? ImportState.Review ?: return
        logAction("confirmImport")
        val batch = currentState.batch
        val acceptedTransactions = batch.transactions.filter { it.isAccepted }

        if (acceptedTransactions.isEmpty()) {
            updateState(ImportState.Error("No transactions have been accepted for import."))
            return
        }

        updateState(ImportState.Importing)

        scope.launch(Dispatchers.IO) {
            val result = importRepository.commitAcceptedBatch(batch, acceptedTransactions)
            if (result.isSuccess) {
                val excludedCount = batch.transactions.count { it.reviewStatus == ReviewStatus.REMOVED }
                val duplicatesSkipped = batch.transactions.count {
                    (it.status == ImportStatus.DUPLICATE || it.status == ImportStatus.POSSIBLE_DUPLICATE) && !it.isAccepted
                }
                val transfersCount = acceptedTransactions.count { it.transactionType == ImporterTransactionType.TRANSFER }
                val cardPaymentsCount = acceptedTransactions.count { it.transactionType == ImporterTransactionType.CARD_PAYMENT }

                updateState(ImportState.Success(
                    importedCount = acceptedTransactions.size,
                    excludedCount = excludedCount,
                    duplicatesSkippedCount = duplicatesSkipped,
                    transfersCount = transfersCount,
                    cardPaymentsCount = cardPaymentsCount
                ))
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Unknown database error"
                updateState(ImportState.Error("Failed to import transactions: $msg"))
            }
        }
    }

    fun reset() {
        currentFiles.clear()
        currentMappings.clear()
        updateState(ImportState.Idle)
    }

    private fun computeSha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
