package com.spendtrack.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.spendtrack.app.data.local.*
import com.spendtrack.app.reminders.ReminderLeadTimes
import com.spendtrack.app.reminders.ReminderPreferences
import com.spendtrack.app.ui.transactions.AddTransactionScreen
import com.spendtrack.app.ui.theme.SpendTrackExpressiveTypography
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private enum class Overlay { NONE, TRANSACTION, EDIT_TRANSACTION, ACCOUNTS, CATEGORIES, BUDGETS, DATA, REMINDERS, ADD_LOAN, ADD_OWED, CARD_PAYMENT, LOAN_OWED_CHOOSER }
private enum class ReminderType { CARD, LOAN }

@Composable
fun SpendTrackApp(viewModel: FinanceViewModel = viewModel()) {
    var route by rememberSaveable { mutableStateOf("home") }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var editingTransactionId by rememberSaveable { mutableLongStateOf(0L) }
    var floatingControlsHeightPx by remember { mutableIntStateOf(0) }
    val floatingControlsHeight = with(LocalDensity.current) { floatingControlsHeightPx.toDp() }
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val owed by viewModel.owed.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    BackHandler(enabled = overlay != Overlay.NONE || route != "home") {
        when {
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            route != "home" -> route = "home"
        }
    }
    // Expressive theme supplies the component shapes/motion; use its light palette and the M3 dark palette for system night mode.
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else expressiveLightColorScheme()
    MaterialExpressiveTheme(colorScheme = colorScheme, typography = SpendTrackExpressiveTypography) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when (overlay) {
                Overlay.TRANSACTION -> AddTransactionScreen(accounts, categories, { viewModel.addTransaction(it); overlay = Overlay.NONE }, { overlay = Overlay.NONE })
                Overlay.EDIT_TRANSACTION -> transactions.find { it.id == editingTransactionId }?.let { transaction ->
                    AddTransactionScreen(accounts, categories, onSaved = { viewModel.updateTransaction(it); overlay = Overlay.NONE }, onCancel = { overlay = Overlay.NONE }, initial = transaction)
                }
                Overlay.ACCOUNTS -> AccountsScreen(accounts, onBack = { overlay = Overlay.NONE }, onAdd = viewModel::addAccount, onUpdate = viewModel::updateAccount, onDelete = viewModel::deleteAccount, onPayCard = { overlay = Overlay.CARD_PAYMENT })
                Overlay.CATEGORIES -> CategoriesScreen(categories, onBack = { overlay = Overlay.NONE }, onAdd = viewModel::addCategory, onUpdate = viewModel::updateCategory, onDelete = viewModel::deleteCategory)
                Overlay.BUDGETS -> BudgetsScreen(budgets, categories, transactions, onBack = { overlay = Overlay.NONE }, onSave = viewModel::saveBudget, onDelete = viewModel::deleteBudget, ensureMonth = viewModel::ensureBudgetMonth)
                Overlay.DATA -> DataScreen(viewModel, onBack = { overlay = Overlay.NONE })
                Overlay.REMINDERS -> RemindersScreen(onBack = { overlay = Overlay.NONE })
                Overlay.ADD_LOAN -> AddLoanScreen(onBack = { overlay = Overlay.NONE }, onSave = { viewModel.addLoan(it); overlay = Overlay.NONE })
                Overlay.ADD_OWED -> AddOwedScreen(onBack = { overlay = Overlay.NONE }, onSave = { viewModel.addOwed(it); overlay = Overlay.NONE })
                Overlay.CARD_PAYMENT -> CardPaymentScreen(accounts, onBack = { overlay = Overlay.NONE }, onPay = { cardId, sourceId, amount -> viewModel.payCard(cardId, sourceId, amount, LocalDate.now().toEpochDay()); overlay = Overlay.NONE })
                else -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                when (route) {
                    "home" -> Dashboard(transactions, accounts, loans.filter { it.includeInNetWorth }.sumOf { it.currentOutstandingPaise }, owed.sumOf { if (it.direction == OwedDirection.OWED_TO_ME) it.amountPaise else -it.amountPaise }, floatingControlsHeight)
                    "spending" -> Spending(transactions, categories, accounts, viewModel::deleteTransaction, floatingControlsHeight, onEdit = { editingTransactionId = it.id; overlay = Overlay.EDIT_TRANSACTION })
                    "loans_owed" -> LoansOwed(loans, owed, floatingControlsHeight, onUpdateLoan = viewModel::updateLoan, onDeleteLoan = viewModel::deleteLoan, onUpdateOwed = viewModel::updateOwed, onDeleteOwed = viewModel::deleteOwed, onSettleOwed = viewModel::settleOwed)
                    else -> Settings(accounts.size, categories.size, floatingControlsHeight, onAccounts = { overlay = Overlay.ACCOUNTS }, onCategories = { overlay = Overlay.CATEGORIES }, onBudgets = { overlay = Overlay.BUDGETS }, onData = { overlay = Overlay.DATA }, onReminders = { overlay = Overlay.REMINDERS })
                }
                if (overlay == Overlay.LOAN_OWED_CHOOSER) LoanOwedActionDialog(onLoan = { overlay = Overlay.ADD_LOAN }, onOwed = { overlay = Overlay.ADD_OWED }, onDismiss = { overlay = Overlay.NONE })
                FloatingControls(
                    route = route,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).onSizeChanged { floatingControlsHeightPx = it.height },
                    changeRoute = { route = it },
                ) { overlay = if (route == "loans_owed") Overlay.LOAN_OWED_CHOOSER else Overlay.TRANSACTION }
            }
            }
        }
    }
}

@Composable private fun FloatingControls(route: String, modifier: Modifier = Modifier, changeRoute: (String) -> Unit, add: () -> Unit) {
    Row(modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Surface(Modifier.weight(1f), shape = MaterialTheme.shapes.extraExtraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 6.dp, shadowElevation = 4.dp) {
            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                topLevelDestinations.forEach { destination -> FilledIconButton(onClick = { changeRoute(destination.route) }, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = if (route == destination.route) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)) { Icon(destination.icon, destination.label) } }
            }
        }
        LargeFloatingActionButton(onClick = add) { Icon(Icons.Default.Add, "Add transaction") }
    }
}

@Composable private fun Dashboard(transactions: List<TransactionEntity>, accounts: List<AccountEntity>, loans: Long, owed: Long, bottomContentPadding: Dp) {
    val month = YearMonth.now()
    val inMonth = transactions.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == month }
    val spending = inMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.mySharePaise ?: it.amountPaise }
    val income = inMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
    val cash = accounts.filterNot { it.isCreditCard }.sumOf { it.balancePaise }; val cards = accounts.filter { it.isCreditCard }.sumOf { it.balancePaise }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(bottom = bottomContentPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("SpendTrack", style = MaterialTheme.typography.displaySmall); Text(month.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SummaryCard("Net Worth", money(cash + owed - cards - loans), "") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { SmallMetric("This Month", money(spending), Modifier.weight(1f)); SmallMetric("Income", money(income), Modifier.weight(1f)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { SmallMetric("Savings", money(cash), Modifier.weight(1f)); SmallMetric("Card dues", money(cards), Modifier.weight(1f)) } }
        item { Text("Recent transactions", style = MaterialTheme.typography.titleLarge) }
        if (transactions.isEmpty()) item { SummaryCard("No transactions yet", "", "Tap + to record one") }
        items(transactions.take(5)) { SummaryCard(it.description.ifBlank { "Transaction" }, (if (it.type == TransactionType.INCOME) "+" else "−") + money(it.amountPaise), LocalDate.ofEpochDay(it.dateEpochDay).toString()) }
        item { DailyTransactionBarChart(transactions) }
    }
}

@Composable private fun Spending(transactions: List<TransactionEntity>, categories: List<CategoryEntity>, accounts: List<AccountEntity>, remove: (Long) -> Unit, bottomContentPadding: Dp, onEdit: (TransactionEntity) -> Unit) {
    var query by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<Long?>(null) }
    var filterExpanded by remember { mutableStateOf(false) }
    var monthFilter by remember { mutableStateOf<String?>(null) }
    var monthExpanded by remember { mutableStateOf(false) }
    val months = transactions.map { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)).toString() }.distinct().sortedDescending()
    val visible = transactions.filter { transaction ->
        (categoryFilter == null || transaction.categoryId == categoryFilter) &&
            (monthFilter == null || YearMonth.from(LocalDate.ofEpochDay(transaction.dateEpochDay)).toString() == monthFilter) &&
            (query.isBlank() || transaction.description.contains(query, ignoreCase = true) || categories.find { it.id == transaction.categoryId }?.name?.contains(query, ignoreCase = true) == true)
    }
    val classification = transactions.filter { transaction ->
        (categoryFilter == null || transaction.categoryId == categoryFilter) &&
            (monthFilter == null || YearMonth.from(LocalDate.ofEpochDay(transaction.dateEpochDay)).toString() == monthFilter)
    }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = bottomContentPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Spending", style = MaterialTheme.typography.displaySmall) }
        item { OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search transactions") }, singleLine = true, shape = MaterialTheme.shapes.large) }
        item { CategorySpendingDonut(classification, categories) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box {
                    FilterChip(selected = categoryFilter != null, onClick = { filterExpanded = true }, label = { Text(categories.find { it.id == categoryFilter }?.name ?: "All categories", maxLines = 1) })
                    DropdownMenu(filterExpanded, { filterExpanded = false }) {
                        DropdownMenuItem({ Text("All categories") }, { categoryFilter = null; filterExpanded = false })
                        categories.filterNot { it.isIncomeCategory }.forEach { category -> DropdownMenuItem({ Text(category.name) }, { categoryFilter = category.id; filterExpanded = false }) }
                    }
                }
                Box {
                    FilterChip(selected = monthFilter != null, onClick = { monthExpanded = true }, label = { Text(monthFilter ?: "All months", maxLines = 1) })
                    DropdownMenu(monthExpanded, { monthExpanded = false }) {
                        DropdownMenuItem({ Text("All months") }, { monthFilter = null; monthExpanded = false })
                        months.forEach { month -> DropdownMenuItem({ Text(month) }, { monthFilter = month; monthExpanded = false }) }
                    }
                }
            }
        }
        if (visible.isEmpty()) item { SummaryCard(if (transactions.isEmpty()) "No transactions yet" else "No matches", "", "") }
        items(visible) { t -> Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(t.description.ifBlank { "Transaction" }, style = MaterialTheme.typography.titleMedium); Text(money(t.amountPaise), style = MaterialTheme.typography.titleLarge) }; Text("${categories.find { it.id == t.categoryId }?.name ?: "Other"} · ${accounts.find { it.id == t.accountId }?.name ?: "Account"}", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Column { ActionIcon(Icons.Default.Edit, "Edit transaction") { onEdit(t) }; ActionIcon(Icons.Default.Delete, "Delete transaction", MaterialTheme.colorScheme.error) { remove(t.id) } } } } }
    }
}

@Composable private fun LoansOwed(loans: List<LoanEntity>, owed: List<OwedEntity>, bottomContentPadding: Dp, onUpdateLoan: (LoanEntity) -> Unit, onDeleteLoan: (LoanEntity) -> Unit, onUpdateOwed: (OwedEntity) -> Unit, onDeleteOwed: (OwedEntity) -> Unit, onSettleOwed: (OwedEntity, Long) -> Unit) {
    val owedToMe = owed.filter { it.direction == OwedDirection.OWED_TO_ME }.sumOf { it.amountPaise }
    val iOwe = owed.filter { it.direction == OwedDirection.I_OWE }.sumOf { it.amountPaise }
    var managingLoan by remember { mutableStateOf<LoanEntity?>(null) }
    var managingOwed by remember { mutableStateOf<OwedEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = bottomContentPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Loans & Owed", style = MaterialTheme.typography.displaySmall) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { SmallMetric("Owed to you", money(owedToMe), Modifier.weight(1f)); SmallMetric("You owe", money(iOwe), Modifier.weight(1f)) } }
        item { Text("Loans", style = MaterialTheme.typography.titleLarge) }
        if (loans.isEmpty()) item { SummaryCard("No loans", "", "") }
        items(loans) { loan -> LoanCard(loan) { managingLoan = loan } }
        item { Text("People", style = MaterialTheme.typography.titleLarge) }
        if (owed.isEmpty()) item { SummaryCard("No owed entries", "", "") }
        items(owed) { entry -> ManageableCard(entry.personName, money(entry.amountPaise), if (entry.direction == OwedDirection.OWED_TO_ME) "Owes you" else "You owe") { managingOwed = entry } }
    }
    managingLoan?.let { loan -> LoanManageDialog(loan, onDismiss = { managingLoan = null }, onSave = { onUpdateLoan(it); managingLoan = null }, onDelete = { onDeleteLoan(loan); managingLoan = null }) }
    managingOwed?.let { entry -> OwedManageDialog(entry, onDismiss = { managingOwed = null }, onSave = { onUpdateOwed(it); managingOwed = null }, onDelete = { onDeleteOwed(entry); managingOwed = null }, onSettle = { amount -> onSettleOwed(entry, amount); managingOwed = null }) }
}
@Composable private fun Settings(accounts: Int, categories: Int, bottomContentPadding: Dp, onAccounts: () -> Unit, onCategories: () -> Unit, onBudgets: () -> Unit, onData: () -> Unit, onReminders: () -> Unit) { LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = bottomContentPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Text("Settings", style = MaterialTheme.typography.displaySmall) }; item { SettingsCard("Accounts", "$accounts accounts and cards", onAccounts) }; item { SettingsCard("Categories", "$categories categories", onCategories) }; item { SettingsCard("Budget", "Category budgets", onBudgets) }; item { SettingsCard("Data", "Backup and restore", onData) }; item { SettingsCard("Alerts & reminders", "Card and loan notifications", onReminders) } } }
@Composable private fun SettingsCard(title: String, sub: String, onClick: () -> Unit = {}) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun RemindersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var cardTimes by remember { mutableStateOf(ReminderPreferences.cardLeadTimes(context)) }
    var loanTimes by remember { mutableStateOf(ReminderPreferences.loanLeadTimes(context)) }
    var editing by remember { mutableStateOf<ReminderType?>(null) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Alerts & reminders", style = MaterialTheme.typography.displaySmall) }
        item { ReminderSettingsCard("Card reminders", cardTimes.summary()) { editing = ReminderType.CARD } }
        item { ReminderSettingsCard("Loan reminders", loanTimes.summary()) { editing = ReminderType.LOAN } }
    }
    when (editing) {
        ReminderType.CARD -> ReminderConfigDialog("Card reminders", cardTimes, onDismiss = { editing = null }) { value -> ReminderPreferences.saveCardLeadTimes(context, value); cardTimes = value; editing = null }
        ReminderType.LOAN -> ReminderConfigDialog("Loan reminders", loanTimes, onDismiss = { editing = null }) { value -> ReminderPreferences.saveLoanLeadTimes(context, value); loanTimes = value; editing = null }
        null -> Unit
    }
}

@Composable private fun ReminderSettingsCard(title: String, summary: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.extraExtraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(summary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.Edit, contentDescription = "Configure $title", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable private fun ReminderConfigDialog(title: String, initial: ReminderLeadTimes, onDismiss: () -> Unit, onSave: (ReminderLeadTimes) -> Unit) {
    var first by remember(initial) { mutableStateOf(initial.firstDays.toString()) }
    var second by remember(initial) { mutableStateOf(initial.secondDays.toString()) }
    val firstDays = first.toIntOrNull()
    val secondDays = second.toIntOrNull()
    val isValid = firstDays != null && secondDays != null && firstDays > 0 && secondDays > 0 && firstDays != secondDays
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose two different days before the due date.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(first, { first = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("First reminder (days before)") }, singleLine = true)
            OutlinedTextField(second, { second = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Second reminder (days before)") }, singleLine = true)
            if (!isValid) Text("Use two different positive day values.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ onSave(ReminderLeadTimes(firstDays!!, secondDays!!)) }, enabled = isValid) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
@Composable private fun DataScreen(viewModel: FinanceViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var backupText by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> backupText?.let { text -> if (uri != null) { runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } }.onSuccess { status = "Backup saved" }.onFailure { status = "Backup failed" } }; backupText = null } }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to read file") }.getOrElse { status = "Restore failed"; null } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Data", style = MaterialTheme.typography.displaySmall) }
        item { Button({ viewModel.exportBackup { result -> result.onSuccess { backupText = it; createDocument.launch("spendtrack-backup.json") }.onFailure { status = "Backup failed" } } }, Modifier.fillMaxWidth()) { Text("Create backup") } }
        item { OutlinedButton({ openDocument.launch(arrayOf("application/json", "text/plain")) }, Modifier.fillMaxWidth()) { Text("Restore backup") } }
        if (status.isNotBlank()) item { Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    pendingRestore?.let { text -> AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("Restore backup?") }, text = { Text("This replaces the current local data.") }, confirmButton = { TextButton({ viewModel.restoreBackup(text) { result -> status = if (result.isSuccess) "Backup restored" else "Restore failed"; pendingRestore = null } }) { Text("Restore") } }, dismissButton = { ActionIcon(Icons.Default.Close, "Cancel", onClick = { pendingRestore = null }) }) }
}
@Composable private fun SummaryCard(title: String, value: String, sub: String) { Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraExtraLarge, colors = CardDefaults.cardColors(containerColor = if (title == "Net Worth") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) { Column(Modifier.padding(22.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(value, style = MaterialTheme.typography.headlineMedium); if (sub.isNotBlank()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
@Composable private fun SmallMetric(title: String, value: String, modifier: Modifier) { Card(modifier, shape = MaterialTheme.shapes.largeIncreased, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) { Column(Modifier.padding(18.dp)) { Text(title, color = MaterialTheme.colorScheme.onSecondaryContainer); Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer) } } }
@Composable private fun ActionDialog(close: () -> Unit) { AlertDialog(onDismissRequest = close, title = { Text("Add to Loans & Owed") }, text = { Text("Loan and owed-entry forms are still to be completed.") }, confirmButton = { TextButton(close) { Text("OK") } }) }

@Composable private fun AccountsScreen(accounts: List<AccountEntity>, onBack: () -> Unit, onAdd: (String, Boolean, Long, String, Long?, Int?, Int?) -> Unit, onUpdate: (AccountEntity) -> Unit, onDelete: (AccountEntity) -> Unit, onPayCard: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var card by remember { mutableStateOf(false) }
    var lastFour by remember { mutableStateOf("") }; var limit by remember { mutableStateOf("") }; var cycle by remember { mutableStateOf("") }; var due by remember { mutableStateOf("") }
    var managing by remember { mutableStateOf<AccountEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Accounts", style = MaterialTheme.typography.displaySmall) }
        if (adding) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add account", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(if (card) "Card name" else "Account name") }, singleLine = true)
                OutlinedTextField(opening, { opening = it }, Modifier.fillMaxWidth(), label = { Text(if (card) "Current outstanding (₹)" else "Opening balance (₹)") }, singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(card, { card = it }); Text("Credit card") }
                if (card) {
                    OutlinedTextField(lastFour, { lastFour = it.take(4) }, Modifier.fillMaxWidth(), label = { Text("Last four digits") }, singleLine = true)
                    OutlinedTextField(limit, { limit = it }, Modifier.fillMaxWidth(), label = { Text("Credit limit (₹)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(cycle, { cycle = it }, Modifier.weight(1f), label = { Text("Statement day") }, singleLine = true); OutlinedTextField(due, { due = it }, Modifier.weight(1f), label = { Text("Due day") }, singleLine = true) }
                }
                Button(onClick = { onAdd(name.trim(), card, ((opening.toDoubleOrNull() ?: 0.0) * 100).toLong(), lastFour, ((limit.toDoubleOrNull() ?: 0.0) * 100).toLong().takeIf { card && it > 0 }, cycle.toIntOrNull()?.takeIf { card && it in 1..31 }, due.toIntOrNull()?.takeIf { card && it in 1..31 }); name = ""; opening = ""; card = false; lastFour = ""; limit = ""; cycle = ""; due = ""; adding = false }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save") }
            } }
        } else item { Button({ adding = true }, Modifier.fillMaxWidth()) { Text("Add account or card") } }
        items(accounts) { account -> ManageableCard(account.name, money(account.balancePaise), if (account.isCreditCard) "Credit card" else "Savings account") { managing = account } }
    }
    managing?.let { AccountManageDialog(it, onDismiss = { managing = null }, onSave = { onUpdate(it); managing = null }, onDelete = { onDelete(it); managing = null }, onPayCard = { managing = null; onPayCard() }) }
}

@Composable private fun CategoriesScreen(categories: List<CategoryEntity>, onBack: () -> Unit, onAdd: (String, Boolean) -> Unit, onUpdate: (CategoryEntity) -> Unit, onDelete: (CategoryEntity) -> Unit) {
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<CategoryEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Categories", style = MaterialTheme.typography.displaySmall) }
        if (adding) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Category name") }, singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(income, { income = it }); Text("Income category") }
                Button(onClick = { onAdd(name.trim(), income); name = ""; income = false; adding = false }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save category") }
            } }
        } else item { Button({ adding = true }, Modifier.fillMaxWidth()) { Text("Add category") } }
        items(categories) { category -> ManageableCard(category.name, if (category.isIncomeCategory) "Income" else "Expense", "") { managing = category } }
    }
    managing?.let { CategoryManageDialog(it, onDismiss = { managing = null }, onSave = { onUpdate(it); managing = null }, onDelete = { onDelete(it); managing = null }) }
}

@Composable private fun ManageableCard(title: String, value: String, subtitle: String, onManage: () -> Unit) {
    Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(value, style = MaterialTheme.typography.titleLarge); if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        ActionIcon(Icons.Default.Edit, "Manage", onClick = onManage)
    } }
}

@Composable private fun ActionIcon(icon: ImageVector, description: String, tint: Color? = null, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = tint ?: LocalContentColor.current) }
}

@Composable private fun AccountManageDialog(account: AccountEntity, onDismiss: () -> Unit, onSave: (AccountEntity) -> Unit, onDelete: () -> Unit, onPayCard: () -> Unit) {
    var name by remember(account.id) { mutableStateOf(account.name) }
    var lastFour by remember(account.id) { mutableStateOf(account.maskedNumber) }; var limit by remember(account.id) { mutableStateOf(account.creditLimitPaise?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()) }; var cycle by remember(account.id) { mutableStateOf(account.statementCycleDay?.toString().orEmpty()) }; var due by remember(account.id) { mutableStateOf(account.dueDay?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage account") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            if (account.isCreditCard) { OutlinedTextField(lastFour, { lastFour = it.take(4) }, Modifier.fillMaxWidth(), label = { Text("Last four digits") }, singleLine = true); OutlinedTextField(limit, { limit = it }, Modifier.fillMaxWidth(), label = { Text("Credit limit (₹)") }, singleLine = true); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(cycle, { cycle = it }, Modifier.weight(1f), label = { Text("Statement day") }, singleLine = true); OutlinedTextField(due, { due = it }, Modifier.weight(1f), label = { Text("Due day") }, singleLine = true) } } else Text("Savings account", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Accounts with transactions cannot be deleted.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ onSave(account.copy(name = name.trim(), maskedNumber = lastFour, creditLimitPaise = ((limit.toDoubleOrNull() ?: 0.0) * 100).toLong().takeIf { account.isCreditCard && it > 0 }, statementCycleDay = cycle.toIntOrNull()?.takeIf { account.isCreditCard && it in 1..31 }, dueDay = due.toIntOrNull()?.takeIf { account.isCreditCard && it in 1..31 })) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { Row { if (account.isCreditCard) ActionIcon(Icons.Default.Payments, "Pay card bill", onClick = onPayCard); ActionIcon(Icons.Default.Delete, "Delete account", MaterialTheme.colorScheme.error, onDelete); ActionIcon(Icons.Default.Close, "Close", onClick = onDismiss) } },
    )
}

@Composable private fun CategoryManageDialog(category: CategoryEntity, onDismiss: () -> Unit, onSave: (CategoryEntity) -> Unit, onDelete: () -> Unit) {
    var name by remember(category.id) { mutableStateOf(category.name) }
    var income by remember(category.id) { mutableStateOf(category.isIncomeCategory) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage category") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Category name") }, singleLine = true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(income, { income = it }); Text("Income category") }
            Text("Default categories and categories used by transactions or budgets cannot be deleted.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ onSave(category.copy(name = name.trim(), isIncomeCategory = income)) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { Row { if (!category.isSystemFallback) ActionIcon(Icons.Default.Delete, "Delete category", MaterialTheme.colorScheme.error, onDelete); ActionIcon(Icons.Default.Close, "Close", onClick = onDismiss) } },
    )
}

@Composable private fun BudgetsScreen(budgets: List<BudgetEntity>, categories: List<CategoryEntity>, transactions: List<TransactionEntity>, onBack: () -> Unit, onSave: (Long, String, Long) -> Unit, onDelete: (BudgetEntity) -> Unit, ensureMonth: (String) -> Unit) {
    val month = YearMonth.now().toString()
    LaunchedEffect(month) { ensureMonth(month) }
    val expenseCategories = categories.filterNot { it.isIncomeCategory }
    var adding by remember { mutableStateOf(false) }
    var categoryIndex by remember { mutableIntStateOf(0) }
    var categoryMenu by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf("") }
    val current = budgets.filter { it.monthKey == month }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Budget", style = MaterialTheme.typography.displaySmall); Text(month, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (adding) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton({ categoryMenu = true }, Modifier.fillMaxWidth(), enabled = expenseCategories.isNotEmpty()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Category"); Text(expenseCategories.getOrNull(categoryIndex)?.name ?: "Choose") } }
                    DropdownMenu(categoryMenu, { categoryMenu = false }) { expenseCategories.forEachIndexed { index, category -> DropdownMenuItem({ Text(category.name) }, { categoryIndex = index; categoryMenu = false }) } }
                }
                OutlinedTextField(limit, { limit = it }, Modifier.fillMaxWidth(), label = { Text("Monthly limit (₹)") }, singleLine = true)
                Button(onClick = { expenseCategories.getOrNull(categoryIndex)?.let { onSave(it.id, month, ((limit.toDoubleOrNull() ?: 0.0) * 100).toLong()); limit = ""; adding = false } }, enabled = limit.toDoubleOrNull()?.let { it > 0 } == true && expenseCategories.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Save budget") }
            } }
        } else item { Button({ adding = true }, Modifier.fillMaxWidth()) { Text("Add budget") } }
        if (current.isEmpty()) item { SummaryCard("No budgets", "", "") }
        items(current) { budget ->
            val category = categories.find { it.id == budget.categoryId }?.name ?: "Category"
            val used = transactions.filter { it.type == TransactionType.EXPENSE && it.categoryId == budget.categoryId && YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)).toString() == month }.sumOf { it.mySharePaise ?: it.amountPaise }
            Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(category, style = MaterialTheme.typography.titleMedium); Text("${money(used)} / ${money(budget.limitPaise)}", style = MaterialTheme.typography.titleLarge) }; ActionIcon(Icons.Default.Delete, "Delete budget", MaterialTheme.colorScheme.error) { onDelete(budget) } } }
        }
    }
}

@Composable private fun LoanCard(loan: LoanEntity, onManage: () -> Unit) {
    val repaid = if (loan.originalPrincipalPaise > 0) ((loan.originalPrincipalPaise - loan.currentOutstandingPaise).toFloat() / loan.originalPrincipalPaise).coerceIn(0f, 1f) else 0f
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(loan.name, style = MaterialTheme.typography.titleMedium); Text("${(repaid * 100).toInt()}%") }
        LinearProgressIndicator(progress = { repaid }, modifier = Modifier.fillMaxWidth())
        Text(money(loan.currentOutstandingPaise), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("EMI ${money(loan.emiPaise)} · Due ${LocalDate.ofEpochDay(loan.nextDueEpochDay)}", color = MaterialTheme.colorScheme.onSurfaceVariant); ActionIcon(Icons.Default.Edit, "Manage loan", onClick = onManage) }
    } }
}

@Composable private fun LoanManageDialog(loan: LoanEntity, onDismiss: () -> Unit, onSave: (LoanEntity) -> Unit, onDelete: () -> Unit) {
    var name by remember(loan.id) { mutableStateOf(loan.name) }
    var repayment by remember(loan.id) { mutableStateOf("") }
    val repaymentPaise = ((repayment.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtMost(loan.currentOutstandingPaise)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage loan") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Loan name") }, singleLine = true)
            OutlinedTextField(repayment, { repayment = it }, Modifier.fillMaxWidth(), label = { Text("Principal repayment (₹)") }, singleLine = true)
            Text("Outstanding ${money(loan.currentOutstandingPaise)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton({ onSave(loan.copy(name = name.trim(), currentOutstandingPaise = loan.currentOutstandingPaise - repaymentPaise, principalPaidPaise = loan.principalPaidPaise + repaymentPaise)) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { Row { ActionIcon(Icons.Default.Delete, "Delete loan", MaterialTheme.colorScheme.error, onDelete); ActionIcon(Icons.Default.Close, "Close", onClick = onDismiss) } },
    )
}

@Composable private fun OwedManageDialog(owed: OwedEntity, onDismiss: () -> Unit, onSave: (OwedEntity) -> Unit, onDelete: () -> Unit, onSettle: (Long) -> Unit) {
    var person by remember(owed.id) { mutableStateOf(owed.personName) }
    var note by remember(owed.id) { mutableStateOf(owed.note) }
    var settlement by remember(owed.id) { mutableStateOf("") }
    val settlementPaise = ((settlement.toDoubleOrNull() ?: 0.0) * 100).toLong().coerceAtMost(owed.amountPaise)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage owed entry") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(person, { person = it }, Modifier.fillMaxWidth(), label = { Text("Person") }, singleLine = true)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") }, singleLine = true)
            OutlinedTextField(settlement, { settlement = it }, Modifier.fillMaxWidth(), label = { Text("Settlement amount (₹)") }, singleLine = true)
            Text("Outstanding ${money(owed.amountPaise)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton({ onSave(owed.copy(personName = person.trim(), note = note.trim())) }, enabled = person.isNotBlank()) { Text("Save") } },
        dismissButton = { Row { if (settlementPaise > 0) ActionIcon(Icons.Default.Payments, "Settle", onClick = { onSettle(settlementPaise) }); ActionIcon(Icons.Default.Delete, "Delete owed entry", MaterialTheme.colorScheme.error, onDelete); ActionIcon(Icons.Default.Close, "Close", onClick = onDismiss) } },
    )
}

@Composable private fun LoanOwedActionDialog(onLoan: () -> Unit, onOwed: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraExtraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Add", style = MaterialTheme.typography.headlineSmall)
                    ActionIcon(Icons.Default.Close, "Close", onClick = onDismiss)
                }
                ExpressiveActionCard("Loan", "Track a loan balance", onLoan)
                ExpressiveActionCard("Owed", "Track money between people", onOwed)
            }
        }
    }
}

@Composable private fun ExpressiveActionCard(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable private fun AddLoanScreen(onBack: () -> Unit, onSave: (LoanEntity) -> Unit) {
    var name by remember { mutableStateOf("") }; var principal by remember { mutableStateOf("") }; var outstanding by remember { mutableStateOf("") }
    var interest by remember { mutableStateOf("") }; var emi by remember { mutableStateOf("") }; var tenure by remember { mutableStateOf("") }; var includeNetWorth by remember { mutableStateOf(true) }
    var dueEpochDay by remember { mutableLongStateOf(LocalDate.now().plusMonths(1).toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val toPaise = { value: String -> ((value.toDoubleOrNull() ?: 0.0) * 100).toLong() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Add loan", style = MaterialTheme.typography.displaySmall) }
        item { OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Loan name") }, singleLine = true) }
        item { OutlinedTextField(principal, { principal = it }, Modifier.fillMaxWidth(), label = { Text("Original principal (₹)") }, singleLine = true) }
        item { OutlinedTextField(outstanding, { outstanding = it }, Modifier.fillMaxWidth(), label = { Text("Current outstanding (₹)") }, singleLine = true) }
        item { OutlinedTextField(interest, { interest = it }, Modifier.fillMaxWidth(), label = { Text("Interest rate (%)") }, singleLine = true) }
        item { OutlinedTextField(emi, { emi = it }, Modifier.fillMaxWidth(), label = { Text("EMI (₹)") }, singleLine = true) }
        item { OutlinedTextField(tenure, { tenure = it }, Modifier.fillMaxWidth(), label = { Text("Remaining tenure (months)") }, singleLine = true) }
        item { OutlinedButton({ showDatePicker = true }, Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Next due date"); Text(LocalDate.ofEpochDay(dueEpochDay).toString()) } } }
        item { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(includeNetWorth, { includeNetWorth = it }); Text("Include in net worth") } }
        item { Button(onClick = { onSave(LoanEntity(name = name.trim(), originalPrincipalPaise = toPaise(principal), currentOutstandingPaise = toPaise(outstanding), interestRateBps = ((interest.toDoubleOrNull() ?: 0.0) * 100).toInt(), emiPaise = toPaise(emi), nextDueEpochDay = dueEpochDay, remainingTenureMonths = tenure.toIntOrNull() ?: 0, principalPaidPaise = (toPaise(principal) - toPaise(outstanding)).coerceAtLeast(0), interestPaidPaise = 0, includeInNetWorth = includeNetWorth)) }, enabled = name.isNotBlank() && toPaise(principal) > 0 && toPaise(outstanding) >= 0, modifier = Modifier.fillMaxWidth()) { Text("Save loan") } }
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(dueEpochDay).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton({ pickerState.selectedDateMillis?.let { dueEpochDay = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay() }; showDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton({ showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = pickerState) }
    }
}

@Composable private fun AddOwedScreen(onBack: () -> Unit, onSave: (OwedEntity) -> Unit) {
    var person by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var owesMe by remember { mutableStateOf(true) }
    val paise = ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Add owed entry", style = MaterialTheme.typography.displaySmall)
        OutlinedTextField(person, { person = it }, Modifier.fillMaxWidth(), label = { Text("Person") }, singleLine = true)
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount (₹)") }, singleLine = true)
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(owesMe, { owesMe = true }, { Text("Owes you") }); FilterChip(!owesMe, { owesMe = false }, { Text("You owe") }) }
        Spacer(Modifier.weight(1f)); Button(onClick = { onSave(OwedEntity(personName = person.trim(), amountPaise = paise, direction = if (owesMe) OwedDirection.OWED_TO_ME else OwedDirection.I_OWE, note = note.trim())) }, enabled = person.isNotBlank() && paise > 0, modifier = Modifier.fillMaxWidth()) { Text("Save entry") }
    }
}

@Composable private fun CardPaymentScreen(accounts: List<AccountEntity>, onBack: () -> Unit, onPay: (Long, Long, Long) -> Unit) {
    val cards = accounts.filter { it.isCreditCard && it.balancePaise > 0 }
    val sources = accounts.filterNot { it.isCreditCard }
    var cardIndex by remember { mutableIntStateOf(0) }; var sourceIndex by remember { mutableIntStateOf(0) }
    var cardMenu by remember { mutableStateOf(false) }; var sourceMenu by remember { mutableStateOf(false) }
    var fullPayment by remember { mutableStateOf(true) }; var amount by remember { mutableStateOf("") }
    val card = cards.getOrNull(cardIndex); val source = sources.getOrNull(sourceIndex)
    val amountPaise = if (fullPayment) card?.balancePaise ?: 0 else ((amount.toDoubleOrNull() ?: 0.0) * 100).toLong()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Pay card bill", style = MaterialTheme.typography.displaySmall)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton({ cardMenu = true }, Modifier.fillMaxWidth(), enabled = cards.isNotEmpty()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Credit card"); Text(card?.name ?: "No card due") } }
            DropdownMenu(cardMenu, { cardMenu = false }) { cards.forEachIndexed { index, item -> DropdownMenuItem({ Text("${item.name} · ${money(item.balancePaise)}") }, { cardIndex = index; cardMenu = false }) } }
        }
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton({ sourceMenu = true }, Modifier.fillMaxWidth(), enabled = sources.isNotEmpty()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Pay from"); Text(source?.name ?: "No account") } }
            DropdownMenu(sourceMenu, { sourceMenu = false }) { sources.forEachIndexed { index, item -> DropdownMenuItem({ Text(item.name) }, { sourceIndex = index; sourceMenu = false }) } }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(fullPayment, { fullPayment = it }); Text("Pay full outstanding") }
        if (!fullPayment) OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Payment amount (₹)") }, singleLine = true)
        if (fullPayment && card != null) Text(money(card.balancePaise), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.weight(1f))
        Button(onClick = { onPay(card!!.id, source!!.id, amountPaise) }, enabled = card != null && source != null && amountPaise in 1..(card?.balancePaise ?: 0), modifier = Modifier.fillMaxWidth()) { Text("Pay bill") }
    }
}
private fun money(paise: Long) = "₹" + String.format(Locale.US, "%,.2f", paise / 100.0)
