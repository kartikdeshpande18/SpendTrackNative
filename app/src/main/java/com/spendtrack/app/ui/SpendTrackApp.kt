package com.spendtrack.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.ui.text.style.TextOverflow
import com.spendtrack.app.ui.spending.DateFilterType
import com.spendtrack.app.ui.spending.TransactionDateFilter
import com.spendtrack.app.ui.spending.CustomDateRangeDialog
import com.spendtrack.app.ui.spending.MonthSelectorDialog

import androidx.compose.runtime.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
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
import com.spendtrack.app.ui.fd.FixedDepositsScreen
import com.spendtrack.app.ui.recurring.RecurringTransactionsScreen
import com.spendtrack.app.ui.theme.SpendTrackTheme
import com.spendtrack.app.ui.animation.SpendTrackMotion
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics
import androidx.compose.material.icons.outlined.Settings
import com.spendtrack.app.ui.dashboard.SavingsCalculator
import com.spendtrack.app.ui.dashboard.TopBudgetsCalculator
import com.spendtrack.app.ui.dashboard.TopBudgetCategoryCard
import com.spendtrack.app.ui.dashboard.TopBudgetCategoriesEmptyCard
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private enum class Overlay { NONE, TRANSACTION, EDIT_TRANSACTION, ACCOUNTS, CATEGORIES, BUDGETS, DATA, REMINDERS, ADD_LOAN, ADD_OWED, CARD_PAYMENT, LOAN_OWED_CHOOSER, FIXED_DEPOSITS, RECURRING_TRANSACTIONS, IMPORT_STATEMENT }
private enum class ReminderType { CARD, LOAN }

@Composable
fun SpendTrackApp(viewModel: FinanceViewModel = viewModel()) {
    var route by rememberSaveable { mutableStateOf("home") }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NONE) }
    var editingTransactionId by rememberSaveable { mutableLongStateOf(0L) }
    var dashboardMonthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val spendingListState = rememberLazyListState()
    var spendingQuery by rememberSaveable { mutableStateOf("") }
    var spendingCategoryFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var spendingDateFilterType by rememberSaveable { mutableStateOf(DateFilterType.THIS_MONTH.name) }
    var spendingCustomStartDay by rememberSaveable { mutableStateOf<Long?>(null) }
    var spendingCustomEndDay by rememberSaveable { mutableStateOf<Long?>(null) }

    var floatingControlsHeightPx by remember { mutableIntStateOf(0) }
    val measuredControlsHeight = with(LocalDensity.current) { floatingControlsHeightPx.toDp() }
    val navBarsBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val minControlsHeight = 72.dp + 12.dp + navBarsBottom
    val floatingControlsHeight = maxOf(measuredControlsHeight, minControlsHeight)
    val bottomContentPadding = floatingControlsHeight + 28.dp
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val loans by viewModel.loans.collectAsState()
    val owed by viewModel.owed.collectAsState()
    val budgets by viewModel.budgets.collectAsState()
    val fixedDeposits by viewModel.fixedDeposits.collectAsState()
    val recurringTransactions by viewModel.recurringTransactions.collectAsState()
    BackHandler(enabled = overlay != Overlay.NONE || route != "home") {
        when {
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            route != "home" -> route = "home"
        }
    }
    SpendTrackTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            val effectiveOverlay = if (overlay == Overlay.LOAN_OWED_CHOOSER) Overlay.NONE else overlay
            AnimatedContent(
                targetState = effectiveOverlay,
                transitionSpec = { SpendTrackMotion.overlayTransition() },
                label = "OverlayTransition",
            ) { currentOverlay ->
                when (currentOverlay) {
                    Overlay.TRANSACTION -> AddTransactionScreen(
                        accounts = accounts,
                        categories = categories,
                        onSaved = { viewModel.addTransaction(it); overlay = Overlay.NONE },
                        onCancel = { overlay = Overlay.NONE },
                        onSaveRecurring = { viewModel.addRecurringTransaction(it); overlay = Overlay.NONE }
                    )
                    Overlay.EDIT_TRANSACTION -> transactions.find { it.id == editingTransactionId }?.let { transaction ->
                        AddTransactionScreen(accounts, categories, onSaved = { viewModel.updateTransaction(it); overlay = Overlay.NONE }, onCancel = { overlay = Overlay.NONE }, initial = transaction)
                    }
                    Overlay.ACCOUNTS -> AccountsScreen(accounts, onBack = { overlay = Overlay.NONE }, onAdd = { name, card, opening, lastFour, limit, cycle, due, minBal -> viewModel.addAccount(name, card, opening, lastFour, limit, cycle, due, minBal) }, onUpdate = viewModel::updateAccount, onDelete = viewModel::deleteAccount, onPayCard = { overlay = Overlay.CARD_PAYMENT })
                    Overlay.CATEGORIES -> CategoriesScreen(categories, onBack = { overlay = Overlay.NONE }, onAdd = viewModel::addCategory, onUpdate = viewModel::updateCategory, onDelete = viewModel::deleteCategory)
                    Overlay.BUDGETS -> BudgetsScreen(budgets, categories, transactions, onBack = { overlay = Overlay.NONE }, onSave = viewModel::saveBudget, onDelete = viewModel::deleteBudget, ensureMonth = viewModel::ensureBudgetMonth)
                    Overlay.DATA -> DataScreen(viewModel, onBack = { overlay = Overlay.NONE }, onImportStatement = { overlay = Overlay.IMPORT_STATEMENT })
                    Overlay.REMINDERS -> RemindersScreen(onBack = { overlay = Overlay.NONE })
                    Overlay.ADD_LOAN -> AddLoanScreen(onBack = { overlay = Overlay.NONE }, onSave = { viewModel.addLoan(it); overlay = Overlay.NONE })
                    Overlay.ADD_OWED -> AddOwedScreen(onBack = { overlay = Overlay.NONE }, onSave = { viewModel.addOwed(it); overlay = Overlay.NONE })
                    Overlay.CARD_PAYMENT -> CardPaymentScreen(accounts, onBack = { overlay = Overlay.NONE }, onPay = { cardId, sourceId, amount -> viewModel.payCard(cardId, sourceId, amount, LocalDate.now().toEpochDay()); overlay = Overlay.NONE })
                    Overlay.FIXED_DEPOSITS -> FixedDepositsScreen(
                        fixedDeposits = fixedDeposits,
                        accounts = accounts,
                        onBack = { overlay = Overlay.NONE },
                        onAdd = { name, accountId, principal, rate, start, maturity, maturityAmount, desc, freq, isExisting ->
                            viewModel.addFixedDeposit(name, accountId, principal, rate, start, maturity, maturityAmount, desc, freq, isExisting)
                        },
                        onUpdate = viewModel::updateFixedDeposit,
                        onDelete = viewModel::deleteFixedDeposit,
                        onMature = viewModel::matureFixedDeposit
                    )
                    Overlay.RECURRING_TRANSACTIONS -> RecurringTransactionsScreen(
                        recurringTransactions = recurringTransactions,
                        accounts = accounts,
                        categories = categories,
                        onBack = { overlay = Overlay.NONE },
                        onAddNew = { overlay = Overlay.TRANSACTION },
                        onToggleEnable = viewModel::toggleRecurringTransaction,
                        onUpdate = viewModel::updateRecurringTransaction,
                        onDelete = viewModel::deleteRecurringTransaction
                    )
                    Overlay.IMPORT_STATEMENT -> {
                        val ctx = LocalContext.current
                        val importRepo = remember { com.spendtrack.app.importer.repository.ImportRepository(DatabaseProvider.get(ctx)) }
                        val parsers = remember {
                            mapOf(
                                com.spendtrack.app.importer.detector.StatementFormat.HDFC_SAVINGS to com.spendtrack.app.importer.parser.HdfcSavingsParser(),
                                com.spendtrack.app.importer.detector.StatementFormat.ICICI_SAVINGS to com.spendtrack.app.importer.parser.IciciSavingsParser(),
                                com.spendtrack.app.importer.detector.StatementFormat.ICICI_CREDIT_CARD to com.spendtrack.app.importer.parser.IciciCreditCardParser(),
                                com.spendtrack.app.importer.detector.StatementFormat.SLICE_SAVINGS to com.spendtrack.app.importer.parser.SliceSavingsParser(),
                                com.spendtrack.app.importer.detector.StatementFormat.SLICE_CREDIT_CARD to com.spendtrack.app.importer.parser.SliceCreditCardParser(),
                                com.spendtrack.app.importer.detector.StatementFormat.SLICE_ATOM to com.spendtrack.app.importer.parser.SliceAtomParser(),
                                com.spendtrack.app.importer.detector.StatementFormat.SBI_SAVINGS to com.spendtrack.app.importer.parser.SbiSavingsParser(),
                                com.spendtrack.app.importer.detector.StatementFormat.KOTAK_SAVINGS to com.spendtrack.app.importer.parser.KotakSavingsParser()
                            )
                        }
                        val importViewModel: com.spendtrack.app.importer.ui.ImportViewModel = viewModel(
                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                    @Suppress("UNCHECKED_CAST")
                                    return com.spendtrack.app.importer.ui.ImportViewModel(importRepo, parsers) as T
                                }
                            }
                        )
                        com.spendtrack.app.importer.ui.ImportScreen(
                            viewModel = importViewModel,
                            accounts = accounts,
                            categories = categories,
                            existingTransactions = transactions,
                            onBack = { overlay = Overlay.NONE }
                        )
                    }
                    else -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        AnimatedContent(
                            targetState = route,
                            transitionSpec = { SpendTrackMotion.routeTransition(initialState, targetState) },
                            label = "RouteTransition",
                        ) { currentRoute ->
                            when (currentRoute) {
                                "home" -> {
                                    val activeFdPrincipal = fixedDeposits.filter { it.status == FixedDepositStatus.ACTIVE }.sumOf { it.principalPaise }
                                    val currentDashboardMonth = runCatching { YearMonth.parse(dashboardMonthStr) }.getOrDefault(YearMonth.now())
                                    Dashboard(
                                        transactions = transactions,
                                        accounts = accounts,
                                        categories = categories,
                                        budgets = budgets,
                                        loans = loans.filter { it.includeInNetWorth }.sumOf { it.currentOutstandingPaise },
                                        owed = owed.sumOf { if (it.direction == OwedDirection.OWED_TO_ME) it.amountPaise else -it.amountPaise },
                                        activeFdPrincipal = activeFdPrincipal,
                                        bottomContentPadding = bottomContentPadding,
                                        selectedMonth = currentDashboardMonth,
                                        onMonthSelected = { dashboardMonthStr = it.toString() },
                                        onSettings = { route = "settings" }
                                    )
                                }
                                "spending" -> {
                                    val currentFilterType = runCatching { DateFilterType.valueOf(spendingDateFilterType) }.getOrDefault(DateFilterType.THIS_MONTH)
                                    val currentDateFilter = TransactionDateFilter(
                                        type = currentFilterType,
                                        customStartDateEpochDay = spendingCustomStartDay,
                                        customEndDateEpochDay = spendingCustomEndDay
                                    )
                                    Spending(
                                        transactions = transactions,
                                        categories = categories,
                                        accounts = accounts,
                                        remove = viewModel::deleteTransaction,
                                        bottomContentPadding = bottomContentPadding,
                                        listState = spendingListState,
                                        query = spendingQuery,
                                        onQueryChange = { spendingQuery = it },
                                        categoryFilter = spendingCategoryFilter,
                                        onCategoryFilterChange = { spendingCategoryFilter = it },
                                        dateFilter = currentDateFilter,
                                        onDateFilterChange = { newFilter ->
                                            spendingDateFilterType = newFilter.type.name
                                            spendingCustomStartDay = newFilter.customStartDateEpochDay
                                            spendingCustomEndDay = newFilter.customEndDateEpochDay
                                        },
                                        onEdit = { editingTransactionId = it.id; overlay = Overlay.EDIT_TRANSACTION },
                                        onSettings = { route = "settings" }
                                    )
                                }
                                "accounts" -> AccountsScreen(
                                    accounts = accounts,
                                    bottomContentPadding = bottomContentPadding,
                                    onBack = { route = "home" },
                                    onAdd = { name, card, opening, lastFour, limit, cycle, due, minBal ->
                                        viewModel.addAccount(name, card, opening, lastFour, limit, cycle, due, minBal)
                                    },
                                    onUpdate = viewModel::updateAccount,
                                    onDelete = viewModel::deleteAccount,
                                    onPayCard = { overlay = Overlay.CARD_PAYMENT },
                                    onSettings = { route = "settings" },
                                )
                                "loans_owed" -> LoansOwed(loans, owed, bottomContentPadding, onUpdateLoan = viewModel::updateLoan, onDeleteLoan = viewModel::deleteLoan, onUpdateOwed = viewModel::updateOwed, onDeleteOwed = viewModel::deleteOwed, onSettleOwed = viewModel::settleOwed, onSettings = { route = "settings" })
                                else -> Settings(
                                    accounts.size,
                                    categories.size,
                                    fixedDeposits.count { it.status == FixedDepositStatus.ACTIVE },
                                    recurringTransactions.count { it.isEnabled },
                                    bottomContentPadding,
                                    onBack = { route = "home" },
                                    onAccounts = { overlay = Overlay.ACCOUNTS },
                                    onCategories = { overlay = Overlay.CATEGORIES },
                                    onFixedDeposits = { overlay = Overlay.FIXED_DEPOSITS },
                                    onRecurring = { overlay = Overlay.RECURRING_TRANSACTIONS },
                                    onBudgets = { overlay = Overlay.BUDGETS },
                                    onData = { overlay = Overlay.DATA },
                                    onReminders = { overlay = Overlay.REMINDERS },
                                    onImportStatement = { overlay = Overlay.IMPORT_STATEMENT }
                                )
                            }
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
}

@Composable private fun FloatingControls(route: String, modifier: Modifier = Modifier, changeRoute: (String) -> Unit, add: () -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    Layout(
        content = {
            // Measurable 0: Navigation Pill (contains ONLY Home, Spending, Loans & Owed)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    topLevelDestinations.forEach { destination ->
                        val isSelected = route == destination.route
                        val containerColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            animationSpec = tween(SpendTrackMotion.DurationFast),
                            label = "TabContainerColor",
                        )
                        val contentColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = tween(SpendTrackMotion.DurationFast),
                            label = "TabContentColor",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.05f else 1.0f,
                            animationSpec = tween(SpendTrackMotion.DurationFast),
                            label = "TabIconScale",
                        )
                        FilledIconButton(
                            onClick = {
                                if (route != destination.route) {
                                    haptics.selection()
                                    changeRoute(destination.route)
                                }
                            },
                            modifier = Modifier.size(44.dp),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = containerColor,
                                contentColor = contentColor,
                            ),
                        ) {
                            Icon(
                                destination.icon,
                                contentDescription = destination.label,
                                modifier = Modifier.size(24.dp).scale(iconScale),
                            )
                        }
                    }
                }
            }

            // Measurable 1: Independent Large FAB positioned immediately to the right of the centered pill
            val fabInteractionSource = remember { MutableInteractionSource() }
            val isFabPressed by fabInteractionSource.collectIsPressedAsState()
            val fabScale by animateFloatAsState(
                targetValue = if (isFabPressed) 0.92f else 1.0f,
                animationSpec = tween(SpendTrackMotion.DurationFast),
                label = "FabScale",
            )
            FloatingActionButton(
                onClick = {
                    haptics.click()
                    add()
                },
                interactionSource = fabInteractionSource,
                modifier = Modifier
                    .size(72.dp)
                    .scale(fabScale),
                shape = RoundedCornerShape(24.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 8.dp,
                ),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add transaction",
                    modifier = Modifier.size(32.dp),
                )
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
    ) { measurables, constraints ->
        val pillPlaceable = measurables[0].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val fabPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))

        val layoutWidth = constraints.maxWidth
        val layoutHeight = maxOf(pillPlaceable.height, fabPlaceable.height)

        val preferredGapPx = 12.dp.roundToPx()
        val minGapPx = 8.dp.roundToPx()
        val minMarginPx = 12.dp.roundToPx()

        // Responsive gap calculation to ensure entire group fits within screen margins
        var gapPx = preferredGapPx
        var totalWidth = pillPlaceable.width + gapPx + fabPlaceable.width

        if (totalWidth > layoutWidth - 2 * minMarginPx) {
            gapPx = maxOf(minGapPx, layoutWidth - 2 * minMarginPx - pillPlaceable.width - fabPlaceable.width)
            totalWidth = pillPlaceable.width + gapPx + fabPlaceable.width
        }

        // Center the COMBINED composition (Navigation Pill + Gap + FAB)
        val groupStartX = ((layoutWidth - totalWidth) / 2).coerceAtLeast(minMarginPx)
        val pillX = groupStartX
        val fabX = groupStartX + pillPlaceable.width + gapPx

        // Vertically align visual centers
        val pillY = (layoutHeight - pillPlaceable.height) / 2
        val fabY = (layoutHeight - fabPlaceable.height) / 2

        layout(layoutWidth, layoutHeight) {
            pillPlaceable.placeRelative(pillX, pillY)
            fabPlaceable.placeRelative(fabX, fabY)
        }
    }
}

@Composable private fun Dashboard(
    transactions: List<TransactionEntity>,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    budgets: List<BudgetEntity>,
    loans: Long,
    owed: Long,
    activeFdPrincipal: Long,
    bottomContentPadding: Dp,
    selectedMonth: YearMonth,
    onMonthSelected: (YearMonth) -> Unit,
    onSettings: () -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    var showMonthPicker by remember { mutableStateOf(false) }
    val isCurrentMonth = selectedMonth == YearMonth.now()
    val inMonth = transactions.filter { YearMonth.from(LocalDate.ofEpochDay(it.dateEpochDay)) == selectedMonth }
    val spending = inMonth.filter { it.type == TransactionType.EXPENSE }.sumOf { it.mySharePaise ?: it.amountPaise }
    val income = inMonth.filter { it.type == TransactionType.INCOME }.sumOf { it.amountPaise }
    val cash = accounts.filterNot { it.isCreditCard }.sumOf { it.balancePaise }
    val availableSavings = SavingsCalculator.calculateAvailableSavingsPaise(accounts)
    val cards = accounts.filter { it.isCreditCard }.sumOf { it.balancePaise }
    val topBudgets = TopBudgetsCalculator.calculateTopBudgetCategories(budgets, categories, transactions, selectedMonth.toString())

    if (showMonthPicker) {
        MonthSelectorDialog(
            selectedMonth = selectedMonth,
            onDismiss = { showMonthPicker = false },
            onSelectMonth = { newMonth ->
                onMonthSelected(newMonth)
                showMonthPicker = false
            }
        )
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("SpendTrack", style = MaterialTheme.typography.displaySmall)
                    Surface(
                        onClick = {
                            haptics.selection()
                            showMonthPicker = true
                        },
                        shape = MaterialTheme.shapes.small,
                        color = Color.Transparent
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = displayMonth(selectedMonth),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Month",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                IconButton(onClick = { haptics.selection(); onSettings() }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SummaryCard("Net Worth", money(cash + activeFdPrincipal + owed - cards - loans), "") }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { SmallMetric(if (isCurrentMonth) "This Month" else "Spending", money(spending), Modifier.weight(1f)); SmallMetric("Income", money(income), Modifier.weight(1f)) } }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { SmallMetric("Savings", money(availableSavings), Modifier.weight(1f)); SmallMetric("Card Dues", money(cards), Modifier.weight(1f)) } }
        item { CategorySpendingDonut(inMonth, categories) }
        item { DailyTransactionBarChart(transactions, selectedMonth = selectedMonth) }
        item { Text("Top 3 Budget Categories", style = MaterialTheme.typography.titleLarge) }
        if (topBudgets.isEmpty()) {
            item { TopBudgetCategoriesEmptyCard() }
        } else {
            items(topBudgets, key = { "top_budget_${it.categoryId}" }) { item ->
                TopBudgetCategoryCard(item)
            }
        }
        item { Text("Recent Transactions", style = MaterialTheme.typography.titleLarge) }
        if (transactions.isEmpty()) {
            item { SummaryCard("No Transactions Yet", "", "Tap + to record your first transaction") }
        } else {
            items(transactions.take(5), key = { "recent_tx_${it.id}" }) { t ->
                TransactionCard(
                    transaction = t,
                    accounts = accounts,
                    categories = categories
                )
            }
        }
    }
}

@Composable private fun Spending(
    transactions: List<TransactionEntity>,
    categories: List<CategoryEntity>,
    accounts: List<AccountEntity>,
    remove: (Long) -> Unit,
    bottomContentPadding: Dp,
    listState: LazyListState,
    query: String,
    onQueryChange: (String) -> Unit,
    categoryFilter: Long?,
    onCategoryFilterChange: (Long?) -> Unit,
    dateFilter: TransactionDateFilter,
    onDateFilterChange: (TransactionDateFilter) -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onSettings: () -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    var categoryExpanded by remember { mutableStateOf(false) }
    var dateExpanded by remember { mutableStateOf(false) }
    var showCustomDateRangeDialog by remember { mutableStateOf(false) }

    val visible = remember(transactions, query, categoryFilter, dateFilter, categories, accounts) {
        TransactionDateFilter.filterTransactions(
            transactions = transactions,
            categoryFilter = categoryFilter,
            dateFilter = dateFilter,
            query = query,
            categories = categories,
            accounts = accounts
        )
    }
    val classification = remember(transactions, categoryFilter, dateFilter) {
        TransactionDateFilter.filterForChart(
            transactions = transactions,
            categoryFilter = categoryFilter,
            dateFilter = dateFilter
        )
    }

    if (showCustomDateRangeDialog) {
        CustomDateRangeDialog(
            initialStartEpochDay = dateFilter.customStartDateEpochDay,
            initialEndEpochDay = dateFilter.customEndDateEpochDay,
            onDismiss = { showCustomDateRangeDialog = false },
            onApply = { startEpochDay, endEpochDay ->
                onDateFilterChange(
                    TransactionDateFilter(
                        type = DateFilterType.CUSTOM_RANGE,
                        customStartDateEpochDay = startEpochDay,
                        customEndDateEpochDay = endEpochDay
                    )
                )
                showCustomDateRangeDialog = false
            }
        )
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Spending", style = MaterialTheme.typography.displaySmall)
                IconButton(onClick = { haptics.selection(); onSettings() }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search Transactions") },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
        }
        item { IncomeVsExpenseChart(classification) }
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    FilterChip(
                        selected = categoryFilter != null,
                        onClick = { haptics.selection(); categoryExpanded = true },
                        label = {
                            Text(
                                categories.find { it.id == categoryFilter }?.name ?: "All Categories",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Categories") },
                            onClick = {
                                haptics.selection()
                                onCategoryFilterChange(null)
                                categoryExpanded = false
                            }
                        )
                        categories.filterNot { it.isIncomeCategory }.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    haptics.selection()
                                    onCategoryFilterChange(category.id)
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) {
                    FilterChip(
                        selected = dateFilter.type != DateFilterType.ALL_DATES,
                        onClick = { haptics.selection(); dateExpanded = true },
                        label = {
                            Text(
                                dateFilter.label(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = {
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(
                        expanded = dateExpanded,
                        onDismissRequest = { dateExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Dates") },
                            onClick = {
                                haptics.selection()
                                onDateFilterChange(TransactionDateFilter(DateFilterType.ALL_DATES))
                                dateExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("This Month") },
                            onClick = {
                                haptics.selection()
                                onDateFilterChange(TransactionDateFilter(DateFilterType.THIS_MONTH))
                                dateExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Last 7 Days") },
                            onClick = {
                                haptics.selection()
                                onDateFilterChange(TransactionDateFilter(DateFilterType.LAST_7_DAYS))
                                dateExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Last 30 Days") },
                            onClick = {
                                haptics.selection()
                                onDateFilterChange(TransactionDateFilter(DateFilterType.LAST_30_DAYS))
                                dateExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Custom Range…") },
                            onClick = {
                                haptics.selection()
                                dateExpanded = false
                                showCustomDateRangeDialog = true
                            }
                        )
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            item {
                SummaryCard(
                    if (transactions.isEmpty()) "No Transactions Yet" else "No Matching Transactions",
                    "",
                    if (transactions.isEmpty()) "Tap + to record your first transaction" else "Try adjusting your search or filters"
                )
            }
        }
        items(visible, key = { it.id }) { t ->
            TransactionCard(
                transaction = t,
                accounts = accounts,
                categories = categories,
                onEdit = onEdit,
                onDelete = remove
            )
        }
    }
}

@Composable
fun TransactionCard(
    transaction: TransactionEntity,
    accounts: List<AccountEntity>,
    categories: List<CategoryEntity>,
    modifier: Modifier = Modifier,
    onEdit: ((TransactionEntity) -> Unit)? = null,
    onDelete: ((Long) -> Unit)? = null,
) {
    val haptics = rememberSpendTrackHaptics()
    val isTransfer = transaction.type == TransactionType.TRANSFER
    val isIncome = transaction.type == TransactionType.INCOME
    val dateText = formatDate(transaction.dateEpochDay)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 18.dp,
                    top = 16.dp,
                    end = if (onEdit != null || onDelete != null) 10.dp else 18.dp,
                    bottom = 16.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = if (onEdit != null || onDelete != null) 8.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Primary line: description/type yields space first, amount gets guaranteed width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val title = if (isTransfer) "Transfer" else transaction.description.ifBlank { "Transaction" }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .padding(end = 12.dp)
                    )
                    val absPaise = Math.abs(transaction.amountPaise)
                    val amountText = when {
                        isTransfer -> money(absPaise)
                        isIncome -> "+${money(absPaise)}"
                        else -> "−${money(absPaise)}"
                    }
                    val amountColor = when {
                        isTransfer -> MaterialTheme.colorScheme.secondary
                        isIncome -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(
                        text = amountText,
                        style = MaterialTheme.typography.titleLarge,
                        color = amountColor,
                        maxLines = 1,
                        softWrap = false
                    )
                }

                // Secondary line: transfer route or category/account/date
                if (isTransfer) {
                    val src = accounts.find { it.id == transaction.accountId }?.name ?: "Account"
                    val dst = accounts.find { it.id == transaction.destinationAccountId }?.name ?: "Account"
                    Text(
                        text = "$src → $dst",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metadata = if (transaction.description.isNotBlank()) "$dateText · ${transaction.description}" else dateText
                    Text(
                        text = metadata,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    val cat = categories.find { it.id == transaction.categoryId }?.name ?: "Other"
                    val acc = accounts.find { it.id == transaction.accountId }?.name ?: "Account"
                    Text(
                        text = "$cat · $acc · $dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (onEdit != null || onDelete != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (onEdit != null) {
                        IconButton(
                            onClick = { haptics.click(); onEdit(transaction) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit transaction",
                                tint = LocalContentColor.current,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = { haptics.reject(); onDelete(transaction.id) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete transaction",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun LoansOwed(
    loans: List<LoanEntity>,
    owed: List<OwedEntity>,
    bottomContentPadding: Dp,
    onUpdateLoan: (LoanEntity) -> Unit,
    onDeleteLoan: (LoanEntity) -> Unit,
    onUpdateOwed: (OwedEntity) -> Unit,
    onDeleteOwed: (OwedEntity) -> Unit,
    onSettleOwed: (OwedEntity, Long) -> Unit,
    onSettings: () -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    val owedToMe = owed.filter { it.direction == OwedDirection.OWED_TO_ME }.sumOf { it.amountPaise }
    val iOwe = owed.filter { it.direction == OwedDirection.I_OWE }.sumOf { it.amountPaise }
    var managingLoan by remember { mutableStateOf<LoanEntity?>(null) }
    var managingOwed by remember { mutableStateOf<OwedEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = bottomContentPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Loans & Owed", style = MaterialTheme.typography.displaySmall)
                IconButton(onClick = { haptics.selection(); onSettings() }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { SmallMetric("Owed to you", money(owedToMe), Modifier.weight(1f)); SmallMetric("You owe", money(iOwe), Modifier.weight(1f)) } }
        item { Text("Loans", style = MaterialTheme.typography.titleLarge) }
        if (loans.isEmpty()) item { SummaryCard("No loans", "", "") }
        items(loans, key = { "loan_${it.id}" }) { loan -> LoanCard(loan) { haptics.click(); managingLoan = loan } }
        item { Text("People", style = MaterialTheme.typography.titleLarge) }
        if (owed.isEmpty()) item { SummaryCard("No owed entries", "", "") }
        items(owed, key = { "owed_${it.id}" }) { entry -> ManageableCard(entry.personName, money(entry.amountPaise), if (entry.direction == OwedDirection.OWED_TO_ME) "Owes you" else "You owe") { haptics.click(); managingOwed = entry } }
    }
    managingLoan?.let { loan -> LoanManageDialog(loan, onDismiss = { managingLoan = null }, onSave = { haptics.confirm(); onUpdateLoan(it); managingLoan = null }, onDelete = { haptics.reject(); onDeleteLoan(loan); managingLoan = null }) }
    managingOwed?.let { entry -> OwedManageDialog(entry, onDismiss = { managingOwed = null }, onSave = { haptics.confirm(); onUpdateOwed(it); managingOwed = null }, onDelete = { haptics.reject(); onDeleteOwed(entry); managingOwed = null }, onSettle = { amount -> haptics.confirm(); onSettleOwed(entry, amount); managingOwed = null }) }
}

@Composable private fun Settings(
    accounts: Int,
    categories: Int,
    activeFdCount: Int,
    activeRecurringCount: Int,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onAccounts: () -> Unit,
    onCategories: () -> Unit,
    onFixedDeposits: () -> Unit,
    onRecurring: () -> Unit,
    onBudgets: () -> Unit,
    onData: () -> Unit,
    onReminders: () -> Unit,
    onImportStatement: () -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    LazyColumn(Modifier.fillMaxSize().padding(20.dp), contentPadding = PaddingValues(bottom = bottomContentPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionIcon(Icons.Default.ArrowBack, "Back", onClick = { haptics.click(); onBack() })
                Text("Settings", style = MaterialTheme.typography.displaySmall)
            }
        }
        item { SettingsCard("Accounts", "$accounts accounts and cards") { haptics.click(); onAccounts() } }
        item { SettingsCard("Categories", "$categories categories") { haptics.click(); onCategories() } }
        item { SettingsCard("Fixed Deposits", "$activeFdCount active deposits") { haptics.click(); onFixedDeposits() } }
        item { SettingsCard("Recurring Transactions", "$activeRecurringCount active schedules") { haptics.click(); onRecurring() } }
        item { SettingsCard("Budget", "Category budgets") { haptics.click(); onBudgets() } }
        item { SettingsCard("Data", "Backup and restore") { haptics.click(); onData() } }
        item { SettingsCard("Alerts & Reminders", "Card and loan notifications") { haptics.click(); onReminders() } }
        item { SettingsCard("Import Statements", "Bulk import from PDF") { haptics.click(); onImportStatement() } }
    }
}
@Composable private fun SettingsCard(title: String, sub: String, onClick: () -> Unit = {}) { Card(Modifier.fillMaxWidth().clickable(onClick = onClick), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) } } }

@Composable private fun RemindersScreen(onBack: () -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    val context = LocalContext.current
    var cardTimes by remember { mutableStateOf(ReminderPreferences.cardLeadTimes(context)) }
    var loanTimes by remember { mutableStateOf(ReminderPreferences.loanLeadTimes(context)) }
    var editing by remember { mutableStateOf<ReminderType?>(null) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Alerts & Reminders", style = MaterialTheme.typography.displaySmall) }
        item { ReminderSettingsCard("Card Reminders", cardTimes.summary()) { haptics.click(); editing = ReminderType.CARD } }
        item { ReminderSettingsCard("Loan Reminders", loanTimes.summary()) { haptics.click(); editing = ReminderType.LOAN } }
    }
    when (editing) {
        ReminderType.CARD -> ReminderConfigDialog("Card Reminders", cardTimes, onDismiss = { editing = null }) { value -> haptics.confirm(); ReminderPreferences.saveCardLeadTimes(context, value); cardTimes = value; editing = null }
        ReminderType.LOAN -> ReminderConfigDialog("Loan Reminders", loanTimes, onDismiss = { editing = null }) { value -> haptics.confirm(); ReminderPreferences.saveLoanLeadTimes(context, value); loanTimes = value; editing = null }
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
@Composable private fun DataScreen(viewModel: FinanceViewModel, onBack: () -> Unit, onImportStatement: (() -> Unit)? = null) {
    val context = LocalContext.current
    var backupText by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> backupText?.let { text -> if (uri != null) { runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) } }.onSuccess { status = "Backup saved" }.onFailure { status = "Backup failed" } }; backupText = null } }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) pendingRestore = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to read file") }.getOrElse { status = "Restore failed"; null } }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Data", style = MaterialTheme.typography.displaySmall) }
        if (onImportStatement != null) {
            item { Button({ onImportStatement() }, Modifier.fillMaxWidth()) { Text("Import Statement (PDF)") } }
        }
        item { OutlinedButton({ viewModel.exportBackup { result -> result.onSuccess { backupText = it; createDocument.launch("spendtrack-backup.json") }.onFailure { status = "Backup failed" } } }, Modifier.fillMaxWidth()) { Text("Create Backup") } }
        item { OutlinedButton({ openDocument.launch(arrayOf("application/json", "text/plain")) }, Modifier.fillMaxWidth()) { Text("Restore Backup") } }
        if (status.isNotBlank()) item { Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    pendingRestore?.let { text -> AlertDialog(onDismissRequest = { pendingRestore = null }, title = { Text("Restore Backup?") }, text = { Text("This replaces the current local data.") }, confirmButton = { TextButton({ viewModel.restoreBackup(text) { result -> status = if (result.isSuccess) "Backup restored" else "Restore failed"; pendingRestore = null } }) { Text("Restore") } }, dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } }) }
}
@Composable private fun SummaryCard(title: String, value: String, sub: String) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraExtraLarge, colors = CardDefaults.cardColors(containerColor = if (title == "Net Worth") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(22.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            AnimatedContent(
                targetState = value,
                transitionSpec = { SpendTrackMotion.numberTransition() },
                label = "SummaryCardValue",
            ) { targetVal ->
                Text(targetVal, style = MaterialTheme.typography.headlineMedium)
            }
            if (sub.isNotBlank()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable private fun SmallMetric(title: String, value: String, modifier: Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.largeIncreased, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSecondaryContainer)
            AnimatedContent(
                targetState = value,
                transitionSpec = { SpendTrackMotion.numberTransition() },
                label = "SmallMetricValue",
            ) { targetVal ->
                Text(targetVal, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}
@Composable private fun ActionDialog(close: () -> Unit) { AlertDialog(onDismissRequest = close, title = { Text("Add to Loans & Owed") }, text = { Text("Loan and owed-entry forms are still to be completed.") }, confirmButton = { TextButton(close) { Text("OK") } }) }

@Composable private fun AccountsScreen(
    accounts: List<AccountEntity>,
    bottomContentPadding: Dp = 0.dp,
    onBack: () -> Unit,
    onAdd: (String, Boolean, Long, String, Long?, Int?, Int?, Long) -> Unit,
    onUpdate: (AccountEntity) -> Unit,
    onDelete: (AccountEntity) -> Unit,
    onPayCard: () -> Unit,
    onSettings: (() -> Unit)? = null,
) {
    val haptics = rememberSpendTrackHaptics()
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var minBalance by remember { mutableStateOf("") }
    var card by remember { mutableStateOf(false) }
    var lastFour by remember { mutableStateOf("") }; var limit by remember { mutableStateOf("") }; var cycle by remember { mutableStateOf("") }; var due by remember { mutableStateOf("") }
    var managing by remember { mutableStateOf<AccountEntity?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().then(if (onSettings == null) Modifier.statusBarsPadding() else Modifier).padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onSettings == null) {
                        ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Accounts", style = MaterialTheme.typography.displaySmall)
                }
                if (onSettings != null) {
                    IconButton(onClick = { haptics.selection(); onSettings() }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (adding) item {
            Card(Modifier.fillMaxWidth().animateContentSize()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add Account", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(if (card) "Card name" else "Account name") }, singleLine = true)
                OutlinedTextField(opening, { opening = it }, Modifier.fillMaxWidth(), label = { Text(if (card) "Current outstanding (₹)" else "Opening balance (₹)") }, singleLine = true)
                if (!card) {
                    OutlinedTextField(minBalance, { minBalance = it }, Modifier.fillMaxWidth(), label = { Text("Minimum Balance (₹)") }, singleLine = true)
                }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(card, { haptics.selection(); card = it }); Text("Credit card") }
                if (card) {
                    OutlinedTextField(lastFour, { lastFour = it.take(4) }, Modifier.fillMaxWidth(), label = { Text("Last four digits") }, singleLine = true)
                    OutlinedTextField(limit, { limit = it }, Modifier.fillMaxWidth(), label = { Text("Credit limit (₹)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(cycle, { cycle = it }, Modifier.weight(1f), label = { Text("Statement day") }, singleLine = true); OutlinedTextField(due, { due = it }, Modifier.weight(1f), label = { Text("Due day") }, singleLine = true) }
                }
                Button(onClick = {
                    haptics.confirm()
                    val parsedMin = if (!card) parsePaise(minBalance).coerceAtLeast(0L) else 0L
                    onAdd(name.trim(), card, parsePaise(opening), lastFour, parsePaise(limit).takeIf { card && it > 0 }, cycle.toIntOrNull()?.takeIf { card && it in 1..31 }, due.toIntOrNull()?.takeIf { card && it in 1..31 }, parsedMin)
                    name = ""; opening = ""; minBalance = ""; card = false; lastFour = ""; limit = ""; cycle = ""; due = ""; adding = false
                }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save") }
            } }
        } else item { Button({ haptics.click(); adding = true }, Modifier.fillMaxWidth()) { Text("Add Account Or Card") } }
        items(accounts, key = { it.id }) { account -> ManageableCard(account.name, money(account.balancePaise), if (account.isCreditCard) "Credit card" else "Savings account") { haptics.click(); managing = account } }
    }
    managing?.let { AccountManageDialog(it, onDismiss = { managing = null }, onSave = { onUpdate(it); managing = null }, onDelete = { onDelete(it); managing = null }, onPayCard = { managing = null; onPayCard() }) }
}

@Composable private fun CategoriesScreen(categories: List<CategoryEntity>, onBack: () -> Unit, onAdd: (String, Boolean) -> Unit, onUpdate: (CategoryEntity) -> Unit, onDelete: (CategoryEntity) -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var income by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf<CategoryEntity?>(null) }
    val incomeCategories = categories.filter { it.isIncomeCategory }
    val expenseCategories = categories.filterNot { it.isIncomeCategory }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Categories", style = MaterialTheme.typography.displaySmall) }
        if (adding) item {
            Card(Modifier.fillMaxWidth().animateContentSize()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Category name") }, singleLine = true)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(income, { haptics.selection(); income = it }); Text("Income category") }
                Button(onClick = { haptics.confirm(); onAdd(name.trim(), income); name = ""; income = false; adding = false }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save Category") }
            } }
        } else item { Button({ haptics.click(); adding = true }, Modifier.fillMaxWidth()) { Text("Add Category") } }
        item { Text("Income Categories", style = MaterialTheme.typography.titleLarge) }
        if (incomeCategories.isEmpty()) item { Text("No Income Categories", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(incomeCategories, key = { it.id }) { category -> ManageableCard(category.name, "Income", "") { haptics.click(); managing = category } }
        item { Text("Expense Categories", style = MaterialTheme.typography.titleLarge) }
        if (expenseCategories.isEmpty()) item { Text("No Expense Categories", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(expenseCategories, key = { it.id }) { category -> ManageableCard(category.name, "Expense", "") { haptics.click(); managing = category } }
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
    val haptics = rememberSpendTrackHaptics()
    var name by remember(account.id) { mutableStateOf(account.name) }
    var balance by remember(account.id) { mutableStateOf("%.2f".format(Locale.US, account.balancePaise / 100.0)) }
    var minBalance by remember(account.id) {
        mutableStateOf(
            if (account.minimumBalancePaise > 0) {
                if (account.minimumBalancePaise % 100L == 0L) (account.minimumBalancePaise / 100).toString()
                else "%.2f".format(Locale.US, account.minimumBalancePaise / 100.0)
            } else ""
        )
    }
    var lastFour by remember(account.id) { mutableStateOf(account.maskedNumber) }; var limit by remember(account.id) { mutableStateOf(account.creditLimitPaise?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()) }; var cycle by remember(account.id) { mutableStateOf(account.statementCycleDay?.toString().orEmpty()) }; var due by remember(account.id) { mutableStateOf(account.dueDay?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Account") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(balance, { balance = it }, Modifier.fillMaxWidth(), label = { Text(if (account.isCreditCard) "Current outstanding (₹)" else "Current balance (₹)") }, singleLine = true)
            if (!account.isCreditCard) {
                OutlinedTextField(minBalance, { minBalance = it }, Modifier.fillMaxWidth(), label = { Text("Minimum Balance (₹)") }, singleLine = true)
            }
            if (account.isCreditCard) { OutlinedTextField(lastFour, { lastFour = it.take(4) }, Modifier.fillMaxWidth(), label = { Text("Last four digits") }, singleLine = true); OutlinedTextField(limit, { limit = it }, Modifier.fillMaxWidth(), label = { Text("Credit limit (₹)") }, singleLine = true); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { OutlinedTextField(cycle, { cycle = it }, Modifier.weight(1f), label = { Text("Statement day") }, singleLine = true); OutlinedTextField(due, { due = it }, Modifier.weight(1f), label = { Text("Due day") }, singleLine = true) } }
            Text("Accounts with transactions cannot be deleted.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (account.isCreditCard) {
                    Button(
                        onClick = { haptics.click(); onPayCard() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Text("Pay Bill")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { haptics.reject(); onDelete() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { haptics.click(); onDismiss() }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                haptics.confirm()
                                val parsedMin = if (!account.isCreditCard) parsePaise(minBalance).coerceAtLeast(0L) else 0L
                                onSave(
                                    account.copy(
                                        name = name.trim(),
                                        balancePaise = parsePaise(balance),
                                        maskedNumber = lastFour,
                                        creditLimitPaise = parsePaise(limit).takeIf { account.isCreditCard && it > 0 },
                                        statementCycleDay = cycle.toIntOrNull()?.takeIf { account.isCreditCard && it in 1..31 },
                                        dueDay = due.toIntOrNull()?.takeIf { account.isCreditCard && it in 1..31 },
                                        minimumBalancePaise = parsedMin
                                    )
                                )
                            },
                            enabled = name.isNotBlank()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        },
        dismissButton = null,
    )
}

@Composable private fun CategoryManageDialog(category: CategoryEntity, onDismiss: () -> Unit, onSave: (CategoryEntity) -> Unit, onDelete: () -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    var name by remember(category.id) { mutableStateOf(category.name) }
    var income by remember(category.id) { mutableStateOf(category.isIncomeCategory) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Category") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Category name") }, singleLine = true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(income, { haptics.selection(); income = it }); Text("Income category") }
            Text("Default categories and categories used by transactions or budgets cannot be deleted.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ haptics.confirm(); onSave(category.copy(name = name.trim(), isIncomeCategory = income)) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!category.isSystemFallback) {
                    TextButton(
                        onClick = { haptics.reject(); onDelete() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                }
                TextButton(onClick = { haptics.click(); onDismiss() }) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable private fun BudgetsScreen(budgets: List<BudgetEntity>, categories: List<CategoryEntity>, transactions: List<TransactionEntity>, onBack: () -> Unit, onSave: (Long, String, Long) -> Unit, onDelete: (BudgetEntity) -> Unit, ensureMonth: (String) -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    val month = YearMonth.now().toString()
    LaunchedEffect(month) { ensureMonth(month) }
    val expenseCategories = categories.filterNot { it.isIncomeCategory }
    var adding by remember { mutableStateOf(false) }
    var categoryIndex by remember { mutableIntStateOf(0) }
    var categoryMenu by remember { mutableStateOf(false) }
    var limit by remember { mutableStateOf("") }
    val current = budgets.filter { it.monthKey == month }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Budget", style = MaterialTheme.typography.displaySmall); Text(displayMonth(month), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (adding) item {
            Card(Modifier.fillMaxWidth().animateContentSize()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton({ categoryMenu = true }, Modifier.fillMaxWidth(), enabled = expenseCategories.isNotEmpty()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Category"); Text(expenseCategories.getOrNull(categoryIndex)?.name ?: "Choose") } }
                    DropdownMenu(categoryMenu, { categoryMenu = false }) { expenseCategories.forEachIndexed { index, category -> DropdownMenuItem({ Text(category.name) }, { haptics.selection(); categoryIndex = index; categoryMenu = false }) } }
                }
                OutlinedTextField(limit, { limit = it }, Modifier.fillMaxWidth(), label = { Text("Monthly limit (₹)") }, singleLine = true)
                Button(onClick = { haptics.confirm(); expenseCategories.getOrNull(categoryIndex)?.let { onSave(it.id, month, parsePaise(limit)); limit = ""; adding = false } }, enabled = parsePaise(limit) > 0 && expenseCategories.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Save Budget") }
            } }
        } else item { Button({ haptics.click(); adding = true }, Modifier.fillMaxWidth()) { Text("Add Budget") } }
        if (current.isEmpty()) item { SummaryCard("No Budgets", "", "") }
        items(current, key = { it.id }) { budget ->
            val category = categories.find { it.id == budget.categoryId }?.name ?: "Category"
            val used = TopBudgetsCalculator.calculateCategorySpendingPaise(transactions, budget.categoryId, month)
            val progress = if (budget.limitPaise > 0) (used.toFloat() / budget.limitPaise.toFloat()).coerceIn(0f, 1f) else 0f
            Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(category, style = MaterialTheme.typography.titleMedium); Text("${money(used)} / ${money(budget.limitPaise)}", style = MaterialTheme.typography.titleLarge) }; ActionIcon(Icons.Default.Delete, "Delete budget", MaterialTheme.colorScheme.error) { haptics.reject(); onDelete(budget) } }
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            } }
        }
    }
}

@Composable private fun LoanCard(loan: LoanEntity, onManage: () -> Unit) {
    val repaid = if (loan.originalPrincipalPaise > 0) ((loan.originalPrincipalPaise - loan.currentOutstandingPaise).toFloat() / loan.originalPrincipalPaise).coerceIn(0f, 1f) else 0f
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(loan.name, style = MaterialTheme.typography.titleMedium); Text("${(repaid * 100).toInt()}%") }
        LinearProgressIndicator(progress = { repaid }, modifier = Modifier.fillMaxWidth())
        Text(money(loan.currentOutstandingPaise), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("EMI ${money(loan.emiPaise)} · Due ${LocalDate.ofEpochDay(loan.nextDueEpochDay)}", color = MaterialTheme.colorScheme.onSurfaceVariant); ActionIcon(Icons.Default.Edit, "Manage Loan", onClick = onManage) }
    } }
}

@Composable private fun LoanManageDialog(loan: LoanEntity, onDismiss: () -> Unit, onSave: (LoanEntity) -> Unit, onDelete: () -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    var name by remember(loan.id) { mutableStateOf(loan.name) }
    var repayment by remember(loan.id) { mutableStateOf("") }
    val repaymentPaise = parsePaise(repayment).coerceAtMost(loan.currentOutstandingPaise)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Loan") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Loan name") }, singleLine = true)
            OutlinedTextField(repayment, { repayment = it }, Modifier.fillMaxWidth(), label = { Text("Principal repayment (₹)") }, singleLine = true)
            Text("Outstanding ${money(loan.currentOutstandingPaise)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.confirm()
                    onSave(loan.copy(name = name.trim(), currentOutstandingPaise = loan.currentOutstandingPaise - repaymentPaise, principalPaidPaise = loan.principalPaidPaise + repaymentPaise))
                },
                enabled = name.isNotBlank()
            ) {
                Text(if (repaymentPaise > 0) "Pay EMI" else "Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = { haptics.reject(); onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
                TextButton(onClick = { haptics.click(); onDismiss() }) {
                    Text("Cancel")
                }
            }
        },
    )
}

@Composable private fun OwedManageDialog(owed: OwedEntity, onDismiss: () -> Unit, onSave: (OwedEntity) -> Unit, onDelete: () -> Unit, onSettle: (Long) -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    var person by remember(owed.id) { mutableStateOf(owed.personName) }
    var note by remember(owed.id) { mutableStateOf(owed.note) }
    var settlement by remember(owed.id) { mutableStateOf("") }
    val settlementPaise = parsePaise(settlement).coerceAtMost(owed.amountPaise)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Owed Entry") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(person, { person = it }, Modifier.fillMaxWidth(), label = { Text("Person") }, singleLine = true)
            OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") }, singleLine = true)
            OutlinedTextField(settlement, { settlement = it }, Modifier.fillMaxWidth(), label = { Text("Settlement amount (₹)") }, singleLine = true)
            Text("Outstanding ${money(owed.amountPaise)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = {
            TextButton(
                onClick = {
                    haptics.confirm()
                    onSave(owed.copy(personName = person.trim(), note = note.trim()))
                },
                enabled = person.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (settlementPaise > 0) {
                    TextButton(onClick = { haptics.confirm(); onSettle(settlementPaise) }) {
                        Text("Settle")
                    }
                }
                TextButton(
                    onClick = { haptics.reject(); onDelete() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
                TextButton(onClick = { haptics.click(); onDismiss() }) {
                    Text("Cancel")
                }
            }
        },
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
    val haptics = rememberSpendTrackHaptics()
    var name by remember { mutableStateOf("") }; var principal by remember { mutableStateOf("") }; var outstanding by remember { mutableStateOf("") }
    var interest by remember { mutableStateOf("") }; var emi by remember { mutableStateOf("") }; var tenure by remember { mutableStateOf("") }; var includeNetWorth by remember { mutableStateOf(true) }
    var dueEpochDay by remember { mutableLongStateOf(LocalDate.now().plusMonths(1).toEpochDay()) }
    var showDatePicker by remember { mutableStateOf(false) }
    val toPaise = { value: String -> parsePaise(value) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Add Loan", style = MaterialTheme.typography.displaySmall)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Loan name") }, singleLine = true)
        OutlinedTextField(principal, { principal = it }, Modifier.fillMaxWidth(), label = { Text("Original principal (₹)") }, singleLine = true)
        OutlinedTextField(outstanding, { outstanding = it }, Modifier.fillMaxWidth(), label = { Text("Current outstanding (₹)") }, singleLine = true)
        OutlinedTextField(interest, { interest = it }, Modifier.fillMaxWidth(), label = { Text("Interest rate (%)") }, singleLine = true)
        OutlinedTextField(emi, { emi = it }, Modifier.fillMaxWidth(), label = { Text("EMI (₹)") }, singleLine = true)
        OutlinedTextField(tenure, { tenure = it }, Modifier.fillMaxWidth(), label = { Text("Remaining tenure (months)") }, singleLine = true)
        OutlinedButton({ showDatePicker = true }, Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Next due date"); Text(LocalDate.ofEpochDay(dueEpochDay).toString()) } }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(includeNetWorth, { haptics.selection(); includeNetWorth = it }); Text("Include in net worth") }
        Button(onClick = { haptics.confirm(); onSave(LoanEntity(name = name.trim(), originalPrincipalPaise = toPaise(principal), currentOutstandingPaise = toPaise(outstanding), interestRateBps = ((interest.toDoubleOrNull() ?: 0.0) * 100).toInt(), emiPaise = toPaise(emi), nextDueEpochDay = dueEpochDay, remainingTenureMonths = tenure.toIntOrNull() ?: 0, principalPaidPaise = (toPaise(principal) - toPaise(outstanding)).coerceAtLeast(0), interestPaidPaise = 0, includeInNetWorth = includeNetWorth)) }, enabled = name.isNotBlank() && toPaise(principal) > 0 && toPaise(outstanding) >= 0, modifier = Modifier.fillMaxWidth()) { Text("Save Loan") }
    }
    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(dueEpochDay).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDatePicker = false }, confirmButton = { TextButton({ haptics.confirm(); pickerState.selectedDateMillis?.let { dueEpochDay = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toEpochDay() }; showDatePicker = false }) { Text("OK") } }, dismissButton = { TextButton({ showDatePicker = false }) { Text("Cancel") } }) { DatePicker(state = pickerState) }
    }
}

@Composable private fun AddOwedScreen(onBack: () -> Unit, onSave: (OwedEntity) -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    var person by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var owesMe by remember { mutableStateOf(true) }
    val paise = parsePaise(amount)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Add Owed Entry", style = MaterialTheme.typography.displaySmall)
        OutlinedTextField(person, { person = it }, Modifier.fillMaxWidth(), label = { Text("Person") }, singleLine = true)
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount (₹)") }, singleLine = true)
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(owesMe, { haptics.selection(); owesMe = true }, { Text("Owes you") }); FilterChip(!owesMe, { haptics.selection(); owesMe = false }, { Text("You owe") }) }
        Spacer(Modifier.weight(1f)); Button(onClick = { haptics.confirm(); onSave(OwedEntity(personName = person.trim(), amountPaise = paise, direction = if (owesMe) OwedDirection.OWED_TO_ME else OwedDirection.I_OWE, note = note.trim())) }, enabled = person.isNotBlank() && paise > 0, modifier = Modifier.fillMaxWidth()) { Text("Save Entry") }
    }
}

@Composable private fun CardPaymentScreen(accounts: List<AccountEntity>, onBack: () -> Unit, onPay: (Long, Long, Long) -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    val cards = accounts.filter { it.isCreditCard && it.balancePaise > 0 }
    val sources = accounts.filterNot { it.isCreditCard }
    var cardIndex by remember { mutableIntStateOf(0) }; var sourceIndex by remember { mutableIntStateOf(0) }
    var cardMenu by remember { mutableStateOf(false) }; var sourceMenu by remember { mutableStateOf(false) }
    var fullPayment by remember { mutableStateOf(true) }; var amount by remember { mutableStateOf("") }
    val card = cards.getOrNull(cardIndex); val source = sources.getOrNull(sourceIndex)
    val amountPaise = if (fullPayment) card?.balancePaise ?: 0 else parsePaise(amount)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ActionIcon(Icons.Default.ArrowBack, "Back", onClick = onBack); Text("Pay Card Bill", style = MaterialTheme.typography.displaySmall)
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton({ cardMenu = true }, Modifier.fillMaxWidth(), enabled = cards.isNotEmpty()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Credit card"); Text(card?.name ?: "No card due") } }
            DropdownMenu(cardMenu, { cardMenu = false }) { cards.forEachIndexed { index, item -> DropdownMenuItem({ Text("${item.name} · ${money(item.balancePaise)}") }, { haptics.selection(); cardIndex = index; cardMenu = false }) } }
        }
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton({ sourceMenu = true }, Modifier.fillMaxWidth(), enabled = sources.isNotEmpty()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Pay from"); Text(source?.name ?: "No account") } }
            DropdownMenu(sourceMenu, { sourceMenu = false }) { sources.forEachIndexed { index, item -> DropdownMenuItem({ Text(item.name) }, { haptics.selection(); sourceIndex = index; sourceMenu = false }) } }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(fullPayment, { haptics.selection(); fullPayment = it }); Text("Pay full outstanding") }
        Column(Modifier.fillMaxWidth().animateContentSize()) {
            if (!fullPayment) OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Payment amount (₹)") }, singleLine = true)
            if (fullPayment && card != null) Text(money(card.balancePaise), style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = { haptics.confirm(); onPay(card!!.id, source!!.id, amountPaise) }, enabled = card != null && source != null && amountPaise in 1..(card?.balancePaise ?: 0), modifier = Modifier.fillMaxWidth()) { Text("Pay Bill") }
    }
}

fun formatIndianMoney(paise: Long): String {
    val absPaise = Math.abs(paise)
    val rupees = absPaise / 100
    val remPaise = absPaise % 100
    val paiseStr = String.format(Locale.US, "%02d", remPaise)
    val rupeeStr = rupees.toString()
    val formattedRupees = if (rupeeStr.length <= 3) {
        rupeeStr
    } else {
        val lastThree = rupeeStr.takeLast(3)
        val rest = rupeeStr.dropLast(3)
        val sb = StringBuilder()
        for (i in rest.indices) {
            if (i > 0 && (rest.length - i) % 2 == 0) {
                sb.append(',')
            }
            sb.append(rest[i])
        }
        sb.append(',').append(lastThree).toString()
    }
    return (if (paise < 0) "−" else "") + "₹" + formattedRupees + "." + paiseStr
}

fun money(paise: Long): String = formatIndianMoney(paise)
private fun displayMonth(ym: YearMonth): String = "${ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())} ${ym.year}"
private fun displayMonth(ymString: String): String = runCatching { displayMonth(YearMonth.parse(ymString)) }.getOrDefault(ymString)
private fun formatDate(epochDay: Long): String = formatEpochDate(epochDay)
