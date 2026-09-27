package com.spendtrack.app.importer.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spendtrack.app.ui.parsePaise
import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.importer.detector.StatementFormat
import com.spendtrack.app.importer.model.ImportBatch
import com.spendtrack.app.importer.model.ImportStatus
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import com.spendtrack.app.importer.model.ReviewStatus
import com.spendtrack.app.importer.model.StatementFileEntry
import com.spendtrack.app.ui.formatIndianMoney
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    existingTransactions: List<TransactionEntity>,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // DEBUG: Log the state observed by Compose to distinguish ViewModel vs UI issues
    LaunchedEffect(state) {
        val stateName = when (state) {
            is ImportState.Idle -> "Idle"
            is ImportState.Analyzing -> "Analyzing"
            is ImportState.AccountMapping -> "AccountMapping"
            is ImportState.Review -> "Review"
            is ImportState.Importing -> "Importing"
            is ImportState.Success -> "Success"
            is ImportState.Error -> "Error"
        }
        android.util.Log.d("SpendTrackImport", "COMPOSE_OBSERVED_STATE=$stateName")
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.selectFiles(context, uris, accounts)
        }
    }

    var transactionToEdit by remember { mutableStateOf<NormalizedTransaction?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (state) {
                            is ImportState.Review -> "Review Statement Import"
                            is ImportState.AccountMapping -> "Map Statement Accounts"
                            is ImportState.Success -> "Import Summary"
                            else -> "Import Statement"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state is ImportState.Review || state is ImportState.AccountMapping) {
                            viewModel.cancelImport()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                is ImportState.Idle -> {
                    IdleStage(
                        onSelectFiles = { launcher.launch(arrayOf("application/pdf")) }
                    )
                }

                is ImportState.Analyzing -> {
                    LoadingStage(message = s.message)
                }

                is ImportState.AccountMapping -> {
                    AccountMappingStage(
                        files = s.files,
                        mappings = s.mappings,
                        accounts = accounts,
                        onUpdateMapping = { filename, accountId ->
                            viewModel.updateAccountMapping(filename, accountId)
                        },
                        onProceed = {
                            viewModel.confirmAccountMapping(context, categories, existingTransactions)
                        },
                        onCancel = { viewModel.cancelImport() }
                    )
                }

                is ImportState.Review -> {
                    ReviewStage(
                        batch = s.batch,
                        currentFilter = s.currentFilter,
                        accounts = accounts,
                        categories = categories,
                        onFilterSelected = viewModel::setReviewFilter,
                        onAccept = viewModel::acceptTransaction,
                        onRemove = viewModel::removeTransaction,
                        onUndo = viewModel::undoStatus,
                        onEdit = { tx -> transactionToEdit = tx },
                        onAcceptAllHighConfidence = viewModel::acceptAllHighConfidence,
                        onRemoveAllDuplicates = viewModel::removeAllDuplicates,
                        onDiscard = { viewModel.cancelImport() },
                        onConfirmImport = { viewModel.confirmImport() }
                    )
                }

                is ImportState.Importing -> {
                    LoadingStage(message = "Committing accepted transactions to database...")
                }

                is ImportState.Success -> {
                    SuccessStage(
                        successState = s,
                        onDone = {
                            viewModel.reset()
                            onBack()
                        }
                    )
                }

                is ImportState.Error -> {
                    ErrorStage(
                        message = s.message,
                        onDismiss = { viewModel.reset() }
                    )
                }
            }

            // Edit Transaction Dialog
            transactionToEdit?.let { tx ->
                EditTransactionDialog(
                    transaction = tx,
                    accounts = accounts,
                    categories = categories,
                    onDismiss = { transactionToEdit = null },
                    onSave = { merchant, amountPaise, dateEpochDay, type, catId, srcAccId, dstAccId ->
                        viewModel.editTransaction(
                            txId = tx.id,
                            merchant = merchant,
                            amountPaise = amountPaise,
                            dateEpochDay = dateEpochDay,
                            type = type,
                            categoryId = catId,
                            sourceAccountId = srcAccId,
                            destinationAccountId = dstAccId
                        )
                        transactionToEdit = null
                    }
                )
            }
        }
    }
}

@Composable
private fun IdleStage(onSelectFiles: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Import Statement",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select one or more PDF bank or credit card statements. All parsing is done 100% locally on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onSelectFiles,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FileOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Select PDF Statements")
        }
    }
}

@Composable
private fun LoadingStage(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AccountMappingStage(
    files: List<StatementFileEntry>,
    mappings: Map<String, Long>,
    accounts: List<AccountEntity>,
    onUpdateMapping: (String, Long) -> Unit,
    onProceed: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Select Target Account",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Choose which SpendTrack account to import transactions into for each statement file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(files) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = file.filename,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Detected: ${file.format.name.replace('_', ' ')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val selectedAccountId = mappings[file.filename] ?: 0L
                            var expanded by remember { mutableStateOf(false) }
                            val currentAccount = accounts.firstOrNull { it.id == selectedAccountId }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { expanded = true },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = currentAccount?.name ?: "Select an account...",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                    }
                                }
                                DropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    accounts.forEach { acc ->
                                        DropdownMenuItem(
                                            text = { Text("${acc.name}${if (acc.isCreditCard) " (Credit Card)" else ""}") },
                                            onClick = {
                                                onUpdateMapping(file.filename, acc.id)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Cancel",
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
                Button(
                    onClick = onProceed,
                    modifier = Modifier.weight(1.8f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Continue to Review",
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewStage(
    batch: ImportBatch,
    currentFilter: ReviewFilter,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onFilterSelected: (ReviewFilter) -> Unit,
    onAccept: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUndo: (String) -> Unit,
    onEdit: (NormalizedTransaction) -> Unit,
    onAcceptAllHighConfidence: () -> Unit,
    onRemoveAllDuplicates: () -> Unit,
    onDiscard: () -> Unit,
    onConfirmImport: () -> Unit
) {
    val totalCount = batch.transactions.size
    val pendingCount = batch.transactions.count { it.reviewStatus == ReviewStatus.PENDING }
    val acceptedCount = batch.transactions.count { it.isAccepted }
    val removedCount = batch.transactions.count { it.reviewStatus == ReviewStatus.REMOVED }

    val filteredTransactions = when (currentFilter) {
        ReviewFilter.ALL -> batch.transactions
        ReviewFilter.PENDING -> batch.transactions.filter { it.reviewStatus == ReviewStatus.PENDING }
        ReviewFilter.ACCEPTED -> batch.transactions.filter { it.isAccepted }
        ReviewFilter.REMOVED -> batch.transactions.filter { it.reviewStatus == ReviewStatus.REMOVED }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Files Summary Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Parsed Statements (${batch.files.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                batch.files.forEach { file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = file.filename,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${file.transactionCount} txns · ${if (file.isReconciled) "Reconciled" else "Checked"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (file.isReconciled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Quick Bulk Actions Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onAcceptAllHighConfidence,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Accept New (${batch.transactions.count { it.status == ImportStatus.NEW && it.reviewStatus == ReviewStatus.PENDING }})", style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(
                onClick = onRemoveAllDuplicates,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("Remove Duplicates", style = MaterialTheme.typography.labelSmall)
            }
        }

        // Filter Chips Bar (horizontally scrollable to prevent wrapping on narrow screens)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = currentFilter == ReviewFilter.ALL,
                onClick = { onFilterSelected(ReviewFilter.ALL) },
                label = { Text("All ($totalCount)", maxLines = 1) }
            )
            FilterChip(
                selected = currentFilter == ReviewFilter.PENDING,
                onClick = { onFilterSelected(ReviewFilter.PENDING) },
                label = { Text("Pending ($pendingCount)", maxLines = 1) }
            )
            FilterChip(
                selected = currentFilter == ReviewFilter.ACCEPTED,
                onClick = { onFilterSelected(ReviewFilter.ACCEPTED) },
                label = { Text("Accepted ($acceptedCount)", maxLines = 1) }
            )
            FilterChip(
                selected = currentFilter == ReviewFilter.REMOVED,
                onClick = { onFilterSelected(ReviewFilter.REMOVED) },
                label = { Text("Removed ($removedCount)", maxLines = 1) }
            )
        }

        // Transactions List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (filteredTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No transactions in this view.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredTransactions, key = { it.id }) { tx ->
                    val accountName = accounts.firstOrNull { it.id == tx.sourceAccountId }?.name ?: "Account"
                    val categoryName = categories.firstOrNull { it.id == tx.categoryId }?.name ?: "Uncategorized"

                    TransactionReviewCard(
                        transaction = tx,
                        accountName = accountName,
                        categoryName = categoryName,
                        onAccept = { onAccept(tx.id) },
                        onRemove = { onRemove(tx.id) },
                        onUndo = { onUndo(tx.id) },
                        onEdit = { onEdit(tx) }
                    )
                }
            }
        }

        // Bottom Action Bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDiscard) {
                    Text("Discard")
                }

                Button(
                    onClick = onConfirmImport,
                    enabled = acceptedCount > 0
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import $acceptedCount ${if (acceptedCount == 1) "Txn" else "Txns"}")
                }
            }
        }
    }
}

@Composable
private fun TransactionReviewCard(
    transaction: NormalizedTransaction,
    accountName: String,
    categoryName: String,
    onAccept: () -> Unit,
    onRemove: () -> Unit,
    onUndo: () -> Unit,
    onEdit: () -> Unit
) {
    val isRemoved = transaction.reviewStatus == ReviewStatus.REMOVED
    val isAccepted = transaction.isAccepted

    val cardColor = when (transaction.reviewStatus) {
        ReviewStatus.REMOVED -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ReviewStatus.ACCEPTED, ReviewStatus.EDITED -> MaterialTheme.colorScheme.surface
        ReviewStatus.PENDING -> MaterialTheme.colorScheme.surface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isRemoved) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header: Date and Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateStr = runCatching {
                    LocalDate.ofEpochDay(transaction.dateEpochDay).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                }.getOrDefault("Day ${transaction.dateEpochDay}")

                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Format paise correctly using formatIndianMoney
                val formattedAmount = formatIndianMoney(transaction.amountPaise)
                val isPositive = transaction.direction == com.spendtrack.app.importer.model.ImporterDirection.CREDIT ||
                        transaction.transactionType == ImporterTransactionType.INCOME ||
                        transaction.transactionType == ImporterTransactionType.REFUND

                Text(
                    text = "${if (isPositive) "+" else "-"}$formattedAmount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPositive) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Merchant / Narration
            Text(
                text = transaction.normalizedMerchant.ifEmpty { transaction.rawNarration },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (transaction.rawNarration != transaction.normalizedMerchant) {
                Text(
                    text = transaction.rawNarration,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Account & Category & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(accountName, style = MaterialTheme.typography.labelSmall) }
                )
                AssistChip(
                    onClick = {},
                    label = { Text(categoryName, style = MaterialTheme.typography.labelSmall) }
                )

                // Status Pill
                val statusText = when (transaction.reviewStatus) {
                    ReviewStatus.ACCEPTED -> "Accepted"
                    ReviewStatus.EDITED -> "Edited"
                    ReviewStatus.REMOVED -> "Excluded"
                    ReviewStatus.PENDING -> when {
                        transaction.status == ImportStatus.DUPLICATE -> "Duplicate"
                        transaction.status == ImportStatus.POSSIBLE_DUPLICATE -> "Possible Dup"
                        transaction.transactionType == ImporterTransactionType.TRANSFER -> "Transfer"
                        transaction.transactionType == ImporterTransactionType.CARD_PAYMENT -> "Card Pay"
                        transaction.status == ImportStatus.NEEDS_REVIEW -> "Review"
                        else -> "New"
                    }
                }
                val statusColor = when (transaction.reviewStatus) {
                    ReviewStatus.ACCEPTED -> Color(0xFF2E7D32)
                    ReviewStatus.EDITED -> MaterialTheme.colorScheme.primary
                    ReviewStatus.REMOVED -> MaterialTheme.colorScheme.error
                    ReviewStatus.PENDING -> if (transaction.status == ImportStatus.DUPLICATE || transaction.status == ImportStatus.POSSIBLE_DUPLICATE) {
                        Color(0xFFE65100)
                    } else MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = "• $statusText",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (transaction.reviewStatus == ReviewStatus.PENDING) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onEdit) {
                        Text("Edit")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = onAccept,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Accept")
                    }
                } else {
                    TextButton(onClick = onUndo) {
                        Text("Undo")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = onEdit) {
                        Text("Edit")
                    }
                }
            }
        }
    }
}

@Composable
private fun EditTransactionDialog(
    transaction: NormalizedTransaction,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (
        merchant: String,
        amountPaise: Long,
        dateEpochDay: Long,
        type: ImporterTransactionType,
        categoryId: Long?,
        sourceAccountId: Long,
        destinationAccountId: Long?
    ) -> Unit
) {
    var merchant by remember { mutableStateOf(transaction.normalizedMerchant) }
    // Convert paise to rupee decimal string for editing: e.g. 16600L -> "166.00"
    val initialRupees = remember(transaction.amountPaise) {
        val rupees = transaction.amountPaise / 100
        val paise = Math.abs(transaction.amountPaise % 100)
        String.format(java.util.Locale.US, "%d.%02d", rupees, paise)
    }
    var amountInput by remember { mutableStateOf(initialRupees) }
    var selectedType by remember { mutableStateOf(transaction.transactionType) }
    var selectedCategoryId by remember { mutableStateOf(transaction.categoryId) }
    var selectedSourceAccountId by remember { mutableStateOf(transaction.sourceAccountId) }
    var selectedDestAccountId by remember { mutableStateOf(transaction.destinationAccountId) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with strong visual hierarchy and date subtitle
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Edit Transaction",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    val dateFormatted = runCatching {
                        LocalDate.ofEpochDay(transaction.dateEpochDay).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                    }.getOrDefault("")
                    if (dateFormatted.isNotEmpty()) {
                        Text(
                            text = dateFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Merchant / Narration text field
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Merchant / Narration") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                // Amount text field with currency symbol prefix
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text("Amount (₹)") },
                    prefix = {
                        Text(
                            text = "₹ ",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                // Category selector
                var catExpanded by remember { mutableStateOf(false) }
                val currentCategory = categories.firstOrNull { it.id == selectedCategoryId }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { catExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Category",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentCategory?.name ?: "None / Uncategorized",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (currentCategory != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.75f)
                    ) {
                        DropdownMenuItem(
                            text = { Text("None / Uncategorized") },
                            onClick = {
                                selectedCategoryId = null
                                catExpanded = false
                            }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategoryId = cat.id
                                    catExpanded = false
                                }
                            )
                        }
                    }
                }

                // Account selector
                var accExpanded by remember { mutableStateOf(false) }
                val currentAccount = accounts.firstOrNull { it.id == selectedSourceAccountId }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedCard(
                        onClick = { accExpanded = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Account",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = currentAccount?.let { "${it.name}${if (it.isCreditCard) " (Credit Card)" else ""}" } ?: "Select Account",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (currentAccount != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = accExpanded,
                        onDismissRequest = { accExpanded = false },
                        modifier = Modifier.fillMaxWidth(0.75f)
                    ) {
                        accounts.forEach { acc ->
                            DropdownMenuItem(
                                text = { Text("${acc.name}${if (acc.isCreditCard) " (Credit Card)" else ""}") },
                                onClick = {
                                    selectedSourceAccountId = acc.id
                                    accExpanded = false
                                }
                            )
                        }
                    }
                }

                // Action buttons: Cancel and Save (visually balanced Material 3 actions)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Cancel", textAlign = TextAlign.Center, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            val parsedPaise = parsePaiseFromInput(amountInput)
                            onSave(
                                merchant.trim(),
                                parsedPaise,
                                transaction.dateEpochDay,
                                selectedType,
                                selectedCategoryId,
                                selectedSourceAccountId,
                                selectedDestAccountId
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Text("Save", textAlign = TextAlign.Center, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun parsePaiseFromInput(input: String): Long {
    return parsePaise(input)
}

@Composable
private fun SuccessStage(
    successState: ImportState.Success,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Import Successful!",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("Imported Transactions", "${successState.importedCount}")
                if (successState.excludedCount > 0) {
                    StatRow("Excluded Transactions", "${successState.excludedCount}")
                }
                if (successState.duplicatesSkippedCount > 0) {
                    StatRow("Duplicates Skipped", "${successState.duplicatesSkippedCount}")
                }
                if (successState.transfersCount > 0) {
                    StatRow("Transfers Created", "${successState.transfersCount}")
                }
                if (successState.cardPaymentsCount > 0) {
                    StatRow("Card Payments Created", "${successState.cardPaymentsCount}")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ErrorStage(
    message: String,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Import Failed",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dismiss")
        }
    }
}
