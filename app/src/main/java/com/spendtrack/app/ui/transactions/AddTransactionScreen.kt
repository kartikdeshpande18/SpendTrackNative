package com.spendtrack.app.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendtrack.app.data.local.*
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset

@Composable
fun AddTransactionScreen(accounts: List<AccountEntity>, categories: List<CategoryEntity>, onSaved: (TransactionEntity) -> Unit, onCancel: () -> Unit, initial: TransactionEntity? = null) {
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }; var amount by remember(initial?.id) { mutableStateOf(initial?.let { "%.2f".format(java.util.Locale.US, it.amountPaise / 100.0) }.orEmpty()) }
    var income by remember(initial?.id) { mutableStateOf(initial?.type == TransactionType.INCOME) }; var shared by remember(initial?.id) { mutableStateOf(initial?.isShared == true) }; var share by remember(initial?.id) { mutableStateOf(initial?.mySharePaise?.let { "%.2f".format(java.util.Locale.US, it / 100.0) }.orEmpty()) }; var sharedWith by remember(initial?.id) { mutableStateOf(initial?.sharedWith.orEmpty()) }
    var dateEpochDay by remember(initial?.id) { mutableLongStateOf(initial?.dateEpochDay ?: LocalDate.now().toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var accountIndex by remember(initial?.id, accounts) { mutableIntStateOf(accounts.indexOfFirst { it.id == initial?.accountId }.coerceAtLeast(0)) }; var categoryIndex by remember(initial?.id, categories) { mutableIntStateOf(categories.filter { it.isIncomeCategory == (initial?.type == TransactionType.INCOME) }.indexOfFirst { it.id == initial?.categoryId }.coerceAtLeast(0)) }
    var accountMenuExpanded by remember { mutableStateOf(false) }; var categoryMenuExpanded by remember { mutableStateOf(false) }
    val filtered = categories.filter { it.isIncomeCategory == income }; val account = accounts.getOrNull(accountIndex); val category = filtered.getOrNull(categoryIndex)
    val paise = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onCancel) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }; Text(if (initial == null) "Add transaction" else "Edit transaction", style = MaterialTheme.typography.displaySmall) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(!income, { income = false; categoryIndex = 0 }, { Text("Expense") }); FilterChip(income, { income = true; categoryIndex = 0 }, { Text("Income") }) }
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount (₹)") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
        OutlinedButton({ showDatePicker = true }, Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Date"); Text(LocalDate.ofEpochDay(dateEpochDay).toString()) } }
        OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text("Description") }, singleLine = true)
        SelectionMenu("Account", account?.name ?: "No account", accounts.map { it.name }, accounts.isNotEmpty(), accountMenuExpanded, { accountMenuExpanded = it }) { accountIndex = it }
        SelectionMenu("Category", category?.name ?: "No matching category", filtered.map { it.name }, filtered.isNotEmpty(), categoryMenuExpanded, { categoryMenuExpanded = it }) { categoryIndex = it }
        if (!income) { Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(shared, { shared = it }); Text("Shared expense") }; if (shared) { OutlinedTextField(share, { share = it }, Modifier.fillMaxWidth(), label = { Text("My share (₹)") }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true); OutlinedTextField(sharedWith, { sharedWith = it }, Modifier.fillMaxWidth(), label = { Text("Who owes the remainder?") }, singleLine = true) } }
        Spacer(Modifier.weight(1f))
        Button({ onSaved(TransactionEntity(id = initial?.id ?: 0, dateEpochDay = dateEpochDay, description = description.trim(), amountPaise = paise, categoryId = category!!.id, accountId = account!!.id, type = if (income) TransactionType.INCOME else TransactionType.EXPENSE, isShared = shared, mySharePaise = if (shared) ((share.toDoubleOrNull() ?: 0.0) * 100).toLong() else null, sharedWith = if (shared) sharedWith.trim() else "")) }, Modifier.fillMaxWidth(), enabled = paise > 0 && account != null && category != null && (!shared || sharedWith.isNotBlank())) { Text(if (initial == null) "Save transaction" else "Save changes") }
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(dateEpochDay).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton({ pickerState.selectedDateMillis?.let { dateEpochDay = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }; showDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton({ showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = pickerState) }
    }
}

@Composable private fun SelectionMenu(label: String, value: String, options: List<String>, enabled: Boolean, expanded: Boolean, setExpanded: (Boolean) -> Unit, select: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton({ setExpanded(true) }, Modifier.fillMaxWidth(), enabled = enabled) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Text(value) } }
        DropdownMenu(expanded, { setExpanded(false) }) { options.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option) }, onClick = { select(index); setExpanded(false) }) } }
    }
}
