package com.spendtrack.app.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendtrack.app.data.local.*
import com.spendtrack.app.ui.parsePaise
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics
import com.spendtrack.app.ui.recurring.RecurrenceFrequencySelector
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun AddTransactionScreen(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onSaved: (TransactionEntity) -> Unit,
    onCancel: () -> Unit,
    initial: TransactionEntity? = null,
    onSaveRecurring: ((RecurringTransactionEntity) -> Unit)? = null
) {
    val haptics = rememberSpendTrackHaptics()
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }; var amount by remember(initial?.id) { mutableStateOf(initial?.let { "%.2f".format(java.util.Locale.US, it.amountPaise / 100.0) }.orEmpty()) }
    var transactionType by remember(initial?.id) { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
    var shared by remember(initial?.id) { mutableStateOf(initial?.isShared == true) }; var share by remember(initial?.id) { mutableStateOf(initial?.mySharePaise?.let { "%.2f".format(java.util.Locale.US, it / 100.0) }.orEmpty()) }; var sharedWith by remember(initial?.id) { mutableStateOf(initial?.sharedWith.orEmpty()) }
    var dateEpochDay by remember(initial?.id) { mutableLongStateOf(initial?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Recurring options
    var isRecurring by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf(RecurrenceFrequency.MONTHLY) }
    var hasEndDate by remember { mutableStateOf(false) }
    var endDateEpochDay by remember { mutableLongStateOf(LocalDate.now().plusMonths(6).toEpochDay()) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    // Account selection for Income/Expense
    var accountIndex by remember(initial?.id, accounts) { mutableIntStateOf(accounts.indexOfFirst { it.id == initial?.accountId }.coerceAtLeast(0)) }
    // Category selection (only for Income/Expense)
    var categoryIndex by remember(initial?.id, categories, transactionType) { mutableIntStateOf(
        if (transactionType == TransactionType.TRANSFER) 0
        else categories.filter { it.isIncomeCategory == (transactionType == TransactionType.INCOME) }.indexOfFirst { it.id == initial?.categoryId }.coerceAtLeast(0)
    ) }
    var accountMenuExpanded by remember { mutableStateOf(false) }; var categoryMenuExpanded by remember { mutableStateOf(false) }
    // Transfer-specific: source and destination accounts
    var fromAccountIndex by remember(initial?.id, accounts) { mutableIntStateOf(accounts.indexOfFirst { it.id == initial?.accountId }.coerceAtLeast(0)) }
    var toAccountIndex by remember(initial?.id, accounts) { mutableIntStateOf(
        if (initial?.destinationAccountId != null) {
            accounts.indexOfFirst { it.id == initial.destinationAccountId }.coerceAtLeast(0)
        } else if (accounts.size > 1) {
            val srcIdx = accounts.indexOfFirst { it.id == initial?.accountId }.coerceAtLeast(0)
            if (srcIdx == 0) 1 else 0
        } else 0
    ) }
    var fromMenuExpanded by remember { mutableStateOf(false) }; var toMenuExpanded by remember { mutableStateOf(false) }

    val isTransfer = transactionType == TransactionType.TRANSFER
    val isIncome = transactionType == TransactionType.INCOME
    val filtered = remember(categories, isIncome) {
        categories.filter { it.isIncomeCategory == isIncome }
    }
    val accountNames = remember(accounts) { accounts.map { it.name } }
    val categoryNames = remember(filtered) { filtered.map { it.name } }
    val account = accounts.getOrNull(accountIndex); val category = filtered.getOrNull(categoryIndex)
    val fromAccount = accounts.getOrNull(fromAccountIndex); val toAccount = accounts.getOrNull(toAccountIndex)
    val paise = parsePaise(amount)

    val canSave = if (isTransfer) {
        paise > 0 && fromAccount != null && toAccount != null && fromAccount.id != toAccount.id && (!isRecurring || !hasEndDate || endDateEpochDay >= dateEpochDay)
    } else {
        paise > 0 && account != null && category != null && (!shared || sharedWith.isNotBlank()) && (!isRecurring || !hasEndDate || endDateEpochDay >= dateEpochDay)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ haptics.click(); onCancel() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }; Text(if (initial == null) "Add Transaction" else "Edit Transaction", style = MaterialTheme.typography.displaySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(transactionType == TransactionType.EXPENSE, { haptics.selection(); transactionType = TransactionType.EXPENSE; categoryIndex = 0 }, { Text("Expense") })
            FilterChip(transactionType == TransactionType.INCOME, { haptics.selection(); transactionType = TransactionType.INCOME; categoryIndex = 0 }, { Text("Income") })
            FilterChip(transactionType == TransactionType.TRANSFER, { haptics.selection(); transactionType = TransactionType.TRANSFER }, { Text("Transfer") })
        }
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount (₹)") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedButton({ haptics.click(); showDatePicker = true }, Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(if (isRecurring) "Start Date" else "Date"); Text(LocalDate.ofEpochDay(dateEpochDay).toString()) } }
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, singleLine = true)

        if (isTransfer) {
            // From/To account pickers for transfers
            SelectionMenu("From account", fromAccount?.name ?: "No account", accountNames, accountNames.isNotEmpty(), fromMenuExpanded, { fromMenuExpanded = it }) { fromAccountIndex = it }
            SelectionMenu("To account", toAccount?.name ?: "No account", accountNames, accountNames.isNotEmpty(), toMenuExpanded, { toMenuExpanded = it }) { toAccountIndex = it }
            if (fromAccount != null && toAccount != null && fromAccount.id == toAccount.id) {
                Text("Source and destination must be different accounts.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        } else {
            // Standard Income/Expense fields
            SelectionMenu("Account", account?.name ?: "No account", accountNames, accountNames.isNotEmpty(), accountMenuExpanded, { accountMenuExpanded = it }) { accountIndex = it }
            SelectionMenu("Category", category?.name ?: "No matching category", categoryNames, categoryNames.isNotEmpty(), categoryMenuExpanded, { categoryMenuExpanded = it }) { categoryIndex = it }
            if (!isIncome) {
                Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(shared, { haptics.selection(); shared = it }); Text("Shared expense") }
                    AnimatedVisibility(visible = shared) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            OutlinedTextField(share, { share = it }, Modifier.fillMaxWidth(), label = { Text("My share (₹)") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                            OutlinedTextField(sharedWith, { sharedWith = it }, Modifier.fillMaxWidth(), label = { Text("Who owes the remainder?") }, singleLine = true)
                        }
                    }
                }
            }
        }

        if (initial == null && onSaveRecurring != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().animateContentSize()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text("Make Recurring", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text("Automatically creates transactions on schedule", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = isRecurring, onCheckedChange = { haptics.selection(); isRecurring = it })
                    }
                    AnimatedVisibility(visible = isRecurring) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Frequency", style = MaterialTheme.typography.labelMedium)
                            RecurrenceFrequencySelector(
                                selectedFrequency = frequency,
                                onFrequencySelected = { frequency = it }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Set End Date", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = hasEndDate, onCheckedChange = { haptics.selection(); hasEndDate = it })
                            }
                            AnimatedVisibility(visible = hasEndDate) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { haptics.click(); showEndDatePicker = true },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("End Date")
                                            Text(LocalDate.ofEpochDay(endDateEpochDay).toString())
                                        }
                                    }
                                    if (endDateEpochDay < dateEpochDay) {
                                        Text("End date cannot be before start date.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            val summary = RecurringTransactionScheduler.formatRecurrenceSummary(
                                frequency = frequency,
                                startDate = LocalDate.ofEpochDay(dateEpochDay),
                                endDate = if (hasEndDate) LocalDate.ofEpochDay(endDateEpochDay) else null
                            )
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button({
            haptics.confirm()
            if (isRecurring && onSaveRecurring != null) {
                val recurring = RecurringTransactionEntity(
                    description = description.trim(),
                    amountPaise = paise,
                    categoryId = if (isTransfer) 0 else category!!.id,
                    accountId = if (isTransfer) fromAccount!!.id else account!!.id,
                    type = transactionType,
                    frequency = frequency,
                    startDateEpochDay = dateEpochDay,
                    endDateEpochDay = if (hasEndDate) endDateEpochDay else null,
                    nextOccurrenceEpochDay = dateEpochDay,
                    isEnabled = true,
                    isShared = shared && !isIncome && !isTransfer,
                    mySharePaise = if (shared && !isIncome && !isTransfer) parsePaise(share) else null,
                    sharedWith = if (shared && !isIncome && !isTransfer) sharedWith.trim() else "",
                    destinationAccountId = if (isTransfer) toAccount!!.id else null,
                )
                onSaveRecurring(recurring)
            } else if (isTransfer) {
                val fromId = fromAccount?.id ?: initial?.accountId ?: 0L
                val toId = toAccount?.id ?: initial?.destinationAccountId
                onSaved(TransactionEntity(id = initial?.id ?: 0, dateEpochDay = dateEpochDay, description = description.trim(), amountPaise = paise, categoryId = 0, accountId = fromId, type = TransactionType.TRANSFER, destinationAccountId = toId))
            } else {
                val accId = account?.id ?: initial?.accountId ?: 0L
                val catId = category?.id ?: initial?.categoryId ?: 0L
                onSaved(TransactionEntity(id = initial?.id ?: 0, dateEpochDay = dateEpochDay, description = description.trim(), amountPaise = paise, categoryId = catId, accountId = accId, type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE, isShared = shared && !isIncome, mySharePaise = if (shared && !isIncome) parsePaise(share) else null, sharedWith = if (shared && !isIncome) sharedWith.trim() else ""))
            }
        }, Modifier.fillMaxWidth(), enabled = canSave) {
            Text(
                if (isRecurring) "Save Recurring Schedule"
                else if (initial == null) "Save Transaction"
                else "Save Changes"
            )
        }
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(dateEpochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton({ haptics.confirm(); pickerState.selectedDateMillis?.let { dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }; showDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton({ showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = pickerState) }
    }
    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(endDateEpochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { showEndDatePicker = false }, confirmButton = { TextButton({ haptics.confirm(); pickerState.selectedDateMillis?.let { endDateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }; showEndDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton({ showEndDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = pickerState) }
    }
}

@Composable private fun SelectionMenu(label: String, value: String, options: List<String>, enabled: Boolean, expanded: Boolean, setExpanded: (Boolean) -> Unit, select: (Int) -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton({ haptics.click(); setExpanded(true) }, Modifier.fillMaxWidth(), enabled = enabled) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) } }
        DropdownMenu(expanded, { setExpanded(false) }) { options.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option) }, onClick = { haptics.selection(); select(index); setExpanded(false) }) } }
    }
}
