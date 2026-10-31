package com.spendtrack.app.ui.transactions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendtrack.app.data.local.*
import com.spendtrack.app.ui.parsePaise
import com.spendtrack.app.ui.money
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics
import com.spendtrack.app.ui.recurring.RecurrenceFrequencySelector
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import java.util.Locale

@Composable
fun AddTransactionScreen(
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onSaved: (TransactionEntity) -> Unit,
    onCancel: () -> Unit,
    initial: TransactionEntity? = null,
    onSaveRecurring: ((RecurringTransactionEntity) -> Unit)? = null,
    owedParticipants: List<String> = emptyList()
) {
    val haptics = rememberSpendTrackHaptics()
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }; var amount by remember(initial?.id) { mutableStateOf(initial?.let { "%.2f".format(java.util.Locale.US, it.amountPaise / 100.0) }.orEmpty()) }
    var transactionType by remember(initial?.id) { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
    var isExcludedFromCalculations by remember(initial?.id) { mutableStateOf(initial?.isExcludedFromCalculations ?: false) }
    var shared by remember(initial?.id) { mutableStateOf(initial?.isShared == true) }; var share by remember(initial?.id) { mutableStateOf(initial?.mySharePaise?.let { "%.2f".format(java.util.Locale.US, it / 100.0) }.orEmpty()) }; var sharedWith by remember(initial?.id) { mutableStateOf(initial?.sharedWith.orEmpty()) }

    // Multi-participant split state (dynamically derived from owed section, without hardcoding)
    val initialSplit = remember(initial?.id) { initial?.splitDetails?.let { SharedExpenseHelper.deserialize(it) } }
    val availableMembers = remember(initial?.id, owedParticipants) {
        val list = mutableStateListOf<String>()
        list.add("You")
        owedParticipants.forEach { name ->
            val trimmed = name.trim()
            if (trimmed.isNotBlank() && !list.any { it.equals(trimmed, ignoreCase = true) }) {
                list.add(trimmed)
            }
        }
        if (initialSplit != null && initialSplit.participants.isNotEmpty()) {
            initialSplit.participants.forEach { name ->
                val trimmed = name.trim()
                if (trimmed.isNotBlank() && !list.any { it.equals(trimmed, ignoreCase = true) }) {
                    list.add(trimmed)
                }
            }
        } else if (initial?.sharedWith?.isNotBlank() == true) {
            initial.sharedWith.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { name ->
                if (!list.any { it.equals(name, ignoreCase = true) }) {
                    list.add(name)
                }
            }
        }
        list
    }
    var selectedParticipants by remember(initial?.id, availableMembers) {
        val set = mutableSetOf<String>()
        if (initialSplit != null && initialSplit.participants.isNotEmpty()) {
            set.addAll(initialSplit.participants)
        } else if (initial?.sharedWith?.isNotBlank() == true) {
            set.add("You")
            set.addAll(initial.sharedWith.split(",").map { it.trim() }.filter { it.isNotBlank() })
        } else {
            set.add("You")
            val firstOther = availableMembers.firstOrNull { !it.equals("You", ignoreCase = true) && !it.equals("Kartik", ignoreCase = true) && !it.equals("Me", ignoreCase = true) }
            if (firstOther != null) {
                set.add(firstOther)
            }
        }
        mutableStateOf(set.toSet())
    }
    var splitMethod by remember(initial?.id) { mutableStateOf(initialSplit?.splitMethod ?: SplitMethod.EQUAL) }
    val customShares = remember(initial?.id) {
        val map = mutableStateMapOf<String, String>()
        if (initialSplit != null && initialSplit.shares.isNotEmpty()) {
            initialSplit.shares.forEach { (name, pa) ->
                map[name] = "%.2f".format(Locale.US, pa / 100.0)
            }
        }
        map
    }
    var showCreateParticipantDialog by remember { mutableStateOf(false) }
    var newParticipantName by remember { mutableStateOf("") }
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
    val filtered = remember(categories, isIncome, initial) {
        categories.filter { it.isIncomeCategory == isIncome && (!it.isDisabled || it.id == initial?.categoryId) }
    }
    val accountNames = remember(accounts) { accounts.map { it.name } }
    val categoryNames = remember(filtered) { filtered.map { it.name } }
    val account = accounts.getOrNull(accountIndex); val category = filtered.getOrNull(categoryIndex)
    val fromAccount = accounts.getOrNull(fromAccountIndex); val toAccount = accounts.getOrNull(toAccountIndex)
    val paise = parsePaise(amount)
    val equalShares = remember(paise, selectedParticipants) {
        SharedExpenseHelper.calculateEqualSplit(paise, selectedParticipants.toList())
    }
    val customSum = remember(customShares.values.toList(), selectedParticipants) {
        selectedParticipants.sumOf { parsePaise(customShares[it].orEmpty()) }
    }
    val isSplitValid = !shared || (
        selectedParticipants.size >= 2 && (
            splitMethod == SplitMethod.EQUAL || (splitMethod == SplitMethod.CUSTOM && customSum == paise)
        )
    )

    val canSave = if (isTransfer) {
        paise > 0 && fromAccount != null && toAccount != null && fromAccount.id != toAccount.id && (!isRecurring || !hasEndDate || endDateEpochDay >= dateEpochDay)
    } else {
        paise > 0 && account != null && category != null && isSplitValid && (!isRecurring || !hasEndDate || endDateEpochDay >= dateEpochDay)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { haptics.click(); onCancel() }, modifier = Modifier.size(40.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp)) }; Text(if (initial == null) "Add transaction" else "Edit transaction", style = MaterialTheme.typography.titleLarge) }
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
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Participants", style = MaterialTheme.typography.titleSmall)
                            // Chips for group members with + Create New as last option
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableMembers.forEach { member ->
                                    val isSelected = selectedParticipants.contains(member)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptics.selection()
                                            selectedParticipants = if (isSelected) {
                                                if (selectedParticipants.size > 1) selectedParticipants - member else selectedParticipants
                                            } else {
                                                selectedParticipants + member
                                            }
                                        },
                                        label = { Text(member) },
                                        leadingIcon = if (isSelected) {
                                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                        } else null
                                    )
                                }
                                AssistChip(
                                    onClick = {
                                        haptics.click()
                                        showCreateParticipantDialog = true
                                    },
                                    label = { Text("+ Create New") },
                                    colors = AssistChipDefaults.assistChipColors(
                                        labelColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            if (selectedParticipants.size < 2) {
                                Text(
                                    "Select or add at least one other participant to split with.",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            // Split Method selector
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = splitMethod == SplitMethod.EQUAL,
                                    onClick = { haptics.selection(); splitMethod = SplitMethod.EQUAL },
                                    label = { Text("Equal Split") }
                                )
                                FilterChip(
                                    selected = splitMethod == SplitMethod.CUSTOM,
                                    onClick = { haptics.selection(); splitMethod = SplitMethod.CUSTOM },
                                    label = { Text("Custom Split") }
                                )
                            }

                            if (splitMethod == SplitMethod.EQUAL) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("Calculated Shares (${selectedParticipants.size} people):", style = MaterialTheme.typography.labelMedium)
                                        selectedParticipants.forEach { person ->
                                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text(person, style = MaterialTheme.typography.bodyMedium)
                                                Text(money(equalShares[person] ?: 0L), style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            } else {
                                // Custom Split fields
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Enter each person's share:", style = MaterialTheme.typography.labelMedium)
                                    selectedParticipants.forEach { person ->
                                        OutlinedTextField(
                                            value = customShares[person] ?: "",
                                            onValueChange = { customShares[person] = it },
                                            modifier = Modifier.fillMaxWidth(),
                                            label = { Text("$person's share (₹)") },
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                            singleLine = true
                                        )
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Total allocated:", style = MaterialTheme.typography.bodySmall)
                                        Text("${money(customSum)} of ${money(paise)}", style = MaterialTheme.typography.bodySmall, color = if (customSum == paise) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                    }
                                    if (customSum != paise) {
                                        Text("Shares must equal the transaction amount.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Exclude from calculations switch
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("Exclude from calculations", style = MaterialTheme.typography.bodyMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text("Does not affect account balance, spending, income, or budgets", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isExcludedFromCalculations, onCheckedChange = { haptics.selection(); isExcludedFromCalculations = it })
            }
        }

        if (onSaveRecurring != null) {
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

            val finalSplitDetails = if (shared && !isIncome && !isTransfer) {
                val sharesPaise = if (splitMethod == SplitMethod.EQUAL) {
                    equalShares
                } else {
                    selectedParticipants.associateWith { parsePaise(customShares[it].orEmpty()) }
                }
                SharedExpenseHelper.serialize(
                    SplitDetails(
                        payer = "You",
                        splitMethod = splitMethod,
                        participants = selectedParticipants.toList(),
                        shares = sharesPaise
                    )
                )
            } else ""

            val finalSharedWith = if (shared && !isIncome && !isTransfer) {
                selectedParticipants.filter {
                    !it.equals("You", ignoreCase = true) &&
                    !it.equals("Kartik", ignoreCase = true) &&
                    !it.equals("Me", ignoreCase = true)
                }.joinToString(", ")
            } else ""

            val myShareFinal = if (shared && !isIncome && !isTransfer) {
                if (splitMethod == SplitMethod.EQUAL) {
                    equalShares["You"] ?: equalShares["Kartik"] ?: equalShares["Me"]
                        ?: if (selectedParticipants.isNotEmpty()) paise / selectedParticipants.size else paise
                } else {
                    parsePaise(customShares["You"] ?: customShares["Kartik"] ?: customShares["Me"].orEmpty())
                }
            } else null

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
                    mySharePaise = myShareFinal,
                    sharedWith = finalSharedWith,
                    destinationAccountId = if (isTransfer) toAccount!!.id else null,
                )
                onSaveRecurring(recurring)
                if (initial != null) {
                    if (isTransfer) {
                        val fromId = fromAccount?.id ?: initial.accountId
                        val toId = toAccount?.id ?: initial.destinationAccountId
                        onSaved(
                            TransactionEntity(
                                id = initial.id,
                                dateEpochDay = dateEpochDay,
                                description = description.trim(),
                                amountPaise = paise,
                                categoryId = 0,
                                accountId = fromId,
                                type = TransactionType.TRANSFER,
                                destinationAccountId = toId,
                                isExcludedFromCalculations = isExcludedFromCalculations
                            )
                        )
                    } else {
                        val accId = account?.id ?: initial.accountId
                        val catId = category?.id ?: initial.categoryId
                        onSaved(
                            TransactionEntity(
                                id = initial.id,
                                dateEpochDay = dateEpochDay,
                                description = description.trim(),
                                amountPaise = paise,
                                categoryId = catId,
                                accountId = accId,
                                type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                                isShared = shared && !isIncome,
                                mySharePaise = myShareFinal,
                                sharedWith = finalSharedWith,
                                isExcludedFromCalculations = isExcludedFromCalculations,
                                splitDetails = finalSplitDetails
                            )
                        )
                    }
                }
            } else if (isTransfer) {
                val fromId = fromAccount?.id ?: initial?.accountId ?: 0L
                val toId = toAccount?.id ?: initial?.destinationAccountId
                onSaved(
                    TransactionEntity(
                        id = initial?.id ?: 0,
                        dateEpochDay = dateEpochDay,
                        description = description.trim(),
                        amountPaise = paise,
                        categoryId = 0,
                        accountId = fromId,
                        type = TransactionType.TRANSFER,
                        destinationAccountId = toId,
                        isExcludedFromCalculations = isExcludedFromCalculations
                    )
                )
            } else {
                val accId = account?.id ?: initial?.accountId ?: 0L
                val catId = category?.id ?: initial?.categoryId ?: 0L
                onSaved(
                    TransactionEntity(
                        id = initial?.id ?: 0,
                        dateEpochDay = dateEpochDay,
                        description = description.trim(),
                        amountPaise = paise,
                        categoryId = catId,
                        accountId = accId,
                        type = if (isIncome) TransactionType.INCOME else TransactionType.EXPENSE,
                        isShared = shared && !isIncome,
                        mySharePaise = myShareFinal,
                        sharedWith = finalSharedWith,
                        isExcludedFromCalculations = isExcludedFromCalculations,
                        splitDetails = finalSplitDetails
                    )
                )
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
    if (showCreateParticipantDialog) {
        AlertDialog(
            onDismissRequest = { showCreateParticipantDialog = false; newParticipantName = "" },
            title = { Text("New Participant") },
            text = {
                OutlinedTextField(
                    value = newParticipantName,
                    onValueChange = { newParticipantName = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newParticipantName.trim()
                        if (name.isNotBlank()) {
                            haptics.confirm()
                            if (!availableMembers.contains(name)) {
                                availableMembers.add(name)
                            }
                            selectedParticipants = selectedParticipants + name
                            showCreateParticipantDialog = false
                            newParticipantName = ""
                        }
                    },
                    enabled = newParticipantName.trim().isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateParticipantDialog = false; newParticipantName = "" }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable private fun SelectionMenu(label: String, value: String, options: List<String>, enabled: Boolean, expanded: Boolean, setExpanded: (Boolean) -> Unit, select: (Int) -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton({ haptics.click(); setExpanded(true) }, Modifier.fillMaxWidth(), enabled = enabled) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) } }
        DropdownMenu(expanded, { setExpanded(false) }) { options.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option) }, onClick = { haptics.selection(); select(index); setExpanded(false) }) } }
    }
}
