package com.spendtrack.app.ui.recurring

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spendtrack.app.data.local.*
import com.spendtrack.app.ui.formatIndianMoney
import com.spendtrack.app.ui.formatEpochDate
import com.spendtrack.app.ui.parsePaise
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@Composable
fun RecurringTransactionsScreen(
    recurringTransactions: List<RecurringTransactionEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    onBack: () -> Unit,
    onAddNew: () -> Unit,
    onToggleEnable: (id: Long, isEnabled: Boolean) -> Unit,
    onUpdate: (RecurringTransactionEntity) -> Unit,
    onDelete: (RecurringTransactionEntity) -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Active, 1: Paused
    var editingSchedule by remember { mutableStateOf<RecurringTransactionEntity?>(null) }
    var scheduleToDelete by remember { mutableStateOf<RecurringTransactionEntity?>(null) }

    val (activeSchedules, pausedSchedules) = remember(recurringTransactions) {
        val active = ArrayList<RecurringTransactionEntity>()
        val paused = ArrayList<RecurringTransactionEntity>()
        for (r in recurringTransactions) {
            if (r.isEnabled) active.add(r) else paused.add(r)
        }
        active to paused
    }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val displayedList = if (selectedTab == 0) activeSchedules else pausedSchedules

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { haptics.click(); onBack() }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
                }
                Text("Recurring transactions", style = MaterialTheme.typography.titleLarge)
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = MaterialTheme.shapes.extraExtraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scheduled commitments", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${activeSchedules.size} Active · ${pausedSchedules.size} Paused",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        "Transactions generate automatically on their scheduled dates.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    haptics.click()
                    onAddNew()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add recurring schedule")
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedTab == 0,
                    onClick = { haptics.selection(); selectedTab = 0 },
                    label = { Text("Active (${activeSchedules.size})") }
                )
                FilterChip(
                    selected = selectedTab == 1,
                    onClick = { haptics.selection(); selectedTab = 1 },
                    label = { Text("Paused (${pausedSchedules.size})") }
                )
            }
        }

        if (displayedList.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraExtraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ) {
                    Column(Modifier.padding(22.dp)) {
                        Text(
                            if (selectedTab == 0) "No active recurring schedules" else "No paused recurring schedules",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Tap 'Add Recurring Schedule' above to automate regular expenses, income, or transfers.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(displayedList, key = { it.id }) { schedule ->
            val account = accountMap[schedule.accountId]
            val destAccount = schedule.destinationAccountId?.let { accountMap[it] }
            val category = categoryMap[schedule.categoryId]

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                schedule.description.ifBlank { "Recurring Transaction" },
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subtitle = when (schedule.type) {
                                TransactionType.TRANSFER -> "Transfer: ${account?.name ?: "Account"} → ${destAccount?.name ?: "Account"}"
                                TransactionType.INCOME -> "Income · ${account?.name ?: "Account"} · ${category?.name ?: "Category"}"
                                TransactionType.EXPENSE -> "Expense · ${account?.name ?: "Account"} · ${category?.name ?: "Category"}"
                            }
                            Text(
                                subtitle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            money(schedule.amountPaise),
                            style = MaterialTheme.typography.titleLarge,
                            color = when (schedule.type) {
                                TransactionType.INCOME -> MaterialTheme.colorScheme.primary
                                TransactionType.EXPENSE -> MaterialTheme.colorScheme.onSurface
                                TransactionType.TRANSFER -> MaterialTheme.colorScheme.secondary
                            },
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    val recurrenceSummary = RecurringTransactionScheduler.formatRecurrenceSummary(
                        frequency = schedule.frequency,
                        startDate = LocalDate.ofEpochDay(schedule.startDateEpochDay),
                        endDate = schedule.endDateEpochDay?.let { LocalDate.ofEpochDay(it) }
                    )

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = recurrenceSummary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Next: ${formatDate(schedule.nextOccurrenceEpochDay)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Switch(
                                checked = schedule.isEnabled,
                                onCheckedChange = { isChecked ->
                                    haptics.selection()
                                    onToggleEnable(schedule.id, isChecked)
                                }
                            )
                            Text(
                                if (schedule.isEnabled) "Active" else "Paused",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (schedule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = {
                                haptics.click()
                                editingSchedule = schedule
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit schedule", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = {
                                haptics.reject()
                                scheduleToDelete = schedule
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete schedule", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    scheduleToDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { scheduleToDelete = null },
            title = { Text("Delete recurring schedule?") },
            text = {
                Text("This will stop future recurring occurrences. Transactions already created by this schedule will NOT be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptics.confirm()
                        onDelete(schedule)
                        scheduleToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { scheduleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Schedule Dialog
    editingSchedule?.let { schedule ->
        EditRecurringScheduleDialog(
            schedule = schedule,
            onDismiss = { editingSchedule = null },
            onSave = { updated ->
                haptics.confirm()
                onUpdate(updated)
                editingSchedule = null
            }
        )
    }
}

@Composable
private fun EditRecurringScheduleDialog(
    schedule: RecurringTransactionEntity,
    onDismiss: () -> Unit,
    onSave: (RecurringTransactionEntity) -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    var description by remember(schedule.id) { mutableStateOf(schedule.description) }
    var amountText by remember(schedule.id) { mutableStateOf("%.2f".format(Locale.US, schedule.amountPaise / 100.0)) }
    var frequency by remember(schedule.id) { mutableStateOf(schedule.frequency) }
    var hasEndDate by remember(schedule.id) { mutableStateOf(schedule.endDateEpochDay != null) }
    var endDateEpochDay by remember(schedule.id) {
        mutableLongStateOf(schedule.endDateEpochDay ?: LocalDate.now().plusMonths(6).toEpochDay())
    }
    var showEndDatePicker by remember { mutableStateOf(false) }

    val amountPaise = parsePaise(amountText)
    val canSave = description.isNotBlank() && amountPaise > 0 && (!hasEndDate || endDateEpochDay >= schedule.startDateEpochDay)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Edit Recurring Schedule",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Changes apply to future occurrences only. Historical transactions already generated will not be modified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Frequency", style = MaterialTheme.typography.bodyMedium)
                    RecurrenceFrequencySelector(
                        selectedFrequency = frequency,
                        onFrequencySelected = { frequency = it }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Set end date", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = hasEndDate, onCheckedChange = { haptics.selection(); hasEndDate = it })
                    }
                    if (hasEndDate) {
                        OutlinedButton(
                            onClick = { haptics.click(); showEndDatePicker = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("End date")
                                Text(LocalDate.ofEpochDay(endDateEpochDay).toString())
                            }
                        }
                    }
                    val summary = RecurringTransactionScheduler.formatRecurrenceSummary(
                        frequency = frequency,
                        startDate = LocalDate.ofEpochDay(schedule.startDateEpochDay),
                        endDate = if (hasEndDate) LocalDate.ofEpochDay(endDateEpochDay) else null
                    )
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                val updated = schedule.copy(
                                    description = description.trim(),
                                    amountPaise = amountPaise,
                                    frequency = frequency,
                                    endDateEpochDay = if (hasEndDate) endDateEpochDay else null
                                )
                                onSave(updated)
                            },
                            enabled = canSave
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    if (showEndDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.ofEpochDay(endDateEpochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton({
                    haptics.confirm()
                    pickerState.selectedDateMillis?.let {
                        endDateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay()
                    }
                    showEndDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton({ showEndDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

private fun money(paise: Long) = formatIndianMoney(paise)

private fun formatDate(epochDay: Long): String = formatEpochDate(epochDay)
