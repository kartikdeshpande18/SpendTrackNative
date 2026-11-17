package com.spendtrack.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import com.spendtrack.app.ui.spending.DateFilterType
import com.spendtrack.app.ui.spending.TransactionDateFilter
import com.spendtrack.app.ui.spending.TransactionKindFilter
import com.spendtrack.app.ui.spending.TransactionAmountRange
import com.spendtrack.app.ui.spending.CustomDateRangeDialog
import com.spendtrack.app.ui.spending.MonthSelectorDialog

import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context.CLIPBOARD_SERVICE
import android.widget.Toast
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import com.spendtrack.app.data.local.*
import com.spendtrack.app.reminders.ReminderLeadTimes
import com.spendtrack.app.reminders.ReminderPreferences
import com.spendtrack.app.ui.transactions.AddTransactionScreen
import com.spendtrack.app.ui.fd.FixedDepositsScreen
import com.spendtrack.app.ui.recurring.RecurringTransactionsScreen
import com.spendtrack.app.ui.theme.SpendTrackTheme
import com.spendtrack.app.ui.theme.AppThemeMode
import com.spendtrack.app.ui.theme.ThemePreferences
import com.spendtrack.app.ui.animation.SpendTrackMotion
import com.spendtrack.app.ui.haptics.rememberSpendTrackHaptics
import androidx.compose.material.icons.outlined.Settings
import com.spendtrack.app.ui.dashboard.SavingsCalculator
import com.spendtrack.app.ui.dashboard.TopBudgetsCalculator
import com.spendtrack.app.ui.dashboard.TopBudgetCategoryCard
import com.spendtrack.app.ui.dashboard.TopBudgetCategoriesEmptyCard
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.LaunchedEffect

private enum class Overlay { NONE, TRANSACTION, EDIT_TRANSACTION, ACCOUNTS, CATEGORIES, BUDGETS, DATA, REMINDERS, APPEARANCE, ABOUT, ADD_LOAN, ADD_OWED, CARD_PAYMENT, LOAN_OWED_CHOOSER, FIXED_DEPOSITS, RECURRING_TRANSACTIONS, IMPORT_STATEMENT, SHARED_EXPENSES }
private enum class ReminderType { CARD, LOAN }

@Composable
fun SpendTrackApp(
    viewModel: FinanceViewModel = viewModel(),
    isAppLocked: Boolean = false,
    onUnlockRequest: () -> Unit = {},
    biometricEnabled: Boolean = false,
    onBiometricPreferenceChange: (Boolean) -> Unit = {},
    restoreBackupUri: Uri? = null,
    onRestoreBackupUriConsumed: () -> Unit = {},
    onPickRestoreBackup: () -> Unit = {},
    onCreateBackup: (String) -> Unit = {},
    selectedStatementUris: List<Uri> = emptyList(),
    onStatementUrisConsumed: () -> Unit = {},
    onPickStatementFiles: () -> Unit = {},
    widgetAction: String? = null,
    onWidgetActionConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var themeMode by rememberSaveable { mutableStateOf(ThemePreferences.mode(context)) }
    var trueBlack by rememberSaveable { mutableStateOf(ThemePreferences.isTrueBlackEnabled(context)) }
    val darkTheme = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }
    var route by rememberSaveable { mutableStateOf("home") }
    var previousRoute by rememberSaveable { mutableStateOf("home") }
    var overlay by rememberSaveable(stateSaver = run {
        androidx.compose.runtime.saveable.Saver(
            save = { it.name },
            restore = { name -> runCatching { Overlay.valueOf(name) }.getOrDefault(Overlay.NONE) }
        )
    }) { mutableStateOf(Overlay.NONE) }
    var transactionInitialType by rememberSaveable { mutableStateOf(TransactionType.EXPENSE) }
    var selectedCardPaymentCardId by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(widgetAction) {
        when (widgetAction) {
            "add_expense" -> {
                transactionInitialType = TransactionType.EXPENSE
                overlay = Overlay.TRANSACTION
                onWidgetActionConsumed()
            }
            "add_income" -> {
                transactionInitialType = TransactionType.INCOME
                overlay = Overlay.TRANSACTION
                onWidgetActionConsumed()
            }
            "add_transfer" -> {
                transactionInitialType = TransactionType.TRANSFER
                overlay = Overlay.TRANSACTION
                onWidgetActionConsumed()
            }
            "add_transaction" -> {
                transactionInitialType = TransactionType.EXPENSE
                overlay = Overlay.TRANSACTION
                onWidgetActionConsumed()
            }
            "budgets" -> {
                overlay = Overlay.BUDGETS
                onWidgetActionConsumed()
            }
            "spending" -> {
                route = "spending"
                onWidgetActionConsumed()
            }
        }
    }
    var editingTransactionId by rememberSaveable { mutableLongStateOf(0L) }
    var dashboardMonthStr by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val spendingListState = rememberLazyListState()
    var spendingQuery by rememberSaveable { mutableStateOf("") }
    var spendingCategoryFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var spendingAccountFilter by rememberSaveable { mutableStateOf<Long?>(null) }
    var spendingKindFilter by rememberSaveable { mutableStateOf(TransactionKindFilter.ALL.name) }
    var spendingMinimumPaise by rememberSaveable { mutableStateOf<Long?>(null) }
    var spendingMaximumPaise by rememberSaveable { mutableStateOf<Long?>(null) }
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
    val people by viewModel.people.collectAsState()
    val activePeople by viewModel.activePeople.collectAsState()
    BackHandler(enabled = overlay != Overlay.NONE || route != "home") {
        when {
            overlay != Overlay.NONE -> overlay = Overlay.NONE
            route == "settings" -> route = if (previousRoute == "settings" || previousRoute.isBlank()) "home" else previousRoute
            route != "home" -> route = "home"
        }
    }
    SpendTrackTheme(darkTheme = darkTheme, trueBlack = trueBlack) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
            val activeOwedParticipants = remember(activePeople) { activePeople.map { it.name } }
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
                        initialType = transactionInitialType,
                        onSaved = {
                            viewModel.addTransaction(it)
                            transactionInitialType = TransactionType.EXPENSE
                            overlay = Overlay.NONE
                        },
                        onCancel = {
                            transactionInitialType = TransactionType.EXPENSE
                            overlay = Overlay.NONE
                        },
                        onSaveRecurring = {
                            viewModel.addRecurringTransaction(it)
                            transactionInitialType = TransactionType.EXPENSE
                            overlay = Overlay.NONE
                        },
                        owedParticipants = activeOwedParticipants,
                        onCreatePerson = viewModel::createPerson
                    )
                    Overlay.EDIT_TRANSACTION -> transactions.find { it.id == editingTransactionId }?.let { transaction ->
                        AddTransactionScreen(
                            accounts = accounts,
                            categories = categories,
                            onSaved = { viewModel.updateTransaction(it); overlay = Overlay.NONE },
                            onCancel = { overlay = Overlay.NONE },
                            initial = transaction,
                            onSaveRecurring = { viewModel.addRecurringTransaction(it); overlay = Overlay.NONE },
                            owedParticipants = activeOwedParticipants,
                            onCreatePerson = viewModel::createPerson
                        )
                    }
                    Overlay.ACCOUNTS -> AccountsScreen(accounts, onBack = { overlay = Overlay.NONE }, onAdd = { name, card, opening, lastFour, limit, cycle, due, minBal -> viewModel.addAccount(name, card, opening, lastFour, limit, cycle, due, minBal) }, onUpdate = viewModel::updateAccount, onDelete = viewModel::deleteAccount, onPayCard = { overlay = Overlay.CARD_PAYMENT })
                    Overlay.CATEGORIES -> CategoriesScreen(categories, onBack = { overlay = Overlay.NONE }, onAdd = { name, inc -> viewModel.addCategory(name, inc) }, onUpdate = viewModel::updateCategory, onDelete = viewModel::deleteCategory)
                    Overlay.BUDGETS -> BudgetsScreen(budgets, categories, transactions, onBack = { overlay = Overlay.NONE }, onSave = viewModel::saveBudget, onDelete = viewModel::deleteBudget, ensureMonth = viewModel::ensureBudgetMonth)
                    Overlay.DATA -> DataScreen(viewModel, restoreBackupUri, onRestoreBackupUriConsumed, onPickRestoreBackup, onCreateBackup, onBack = { overlay = Overlay.NONE }, onImportStatement = { overlay = Overlay.IMPORT_STATEMENT })
                    Overlay.REMINDERS -> RemindersScreen(onBack = { overlay = Overlay.NONE })
                    Overlay.APPEARANCE -> AppearanceScreen(
                        themeMode = themeMode,
                        trueBlack = trueBlack,
                        onThemeModeChange = { themeMode = it; ThemePreferences.setMode(context, it) },
                        onTrueBlackChange = { trueBlack = it; ThemePreferences.setTrueBlackEnabled(context, it) },
                        onBack = { overlay = Overlay.NONE }
                    )
                    Overlay.ABOUT -> AboutScreen(onBack = { overlay = Overlay.NONE })
                    Overlay.ADD_LOAN -> AddLoanScreen(onBack = { overlay = Overlay.NONE }, onSave = { viewModel.addLoan(it); overlay = Overlay.NONE })
                    Overlay.ADD_OWED -> AddOwedScreen(people = activePeople, onBack = { overlay = Overlay.NONE }, onSave = { viewModel.addOwed(it); overlay = Overlay.NONE })
                    Overlay.CARD_PAYMENT -> CardPaymentScreen(accounts, initialCardId = selectedCardPaymentCardId, onBack = { selectedCardPaymentCardId = null; overlay = Overlay.NONE }, onPay = { cardId, sourceId, amount -> viewModel.payCard(cardId, sourceId, amount, LocalDate.now().toEpochDay()); selectedCardPaymentCardId = null; overlay = Overlay.NONE })
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
                        onAddNew = { transactionInitialType = TransactionType.EXPENSE; overlay = Overlay.TRANSACTION },
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
                            selectedUris = selectedStatementUris,
                            onSelectedUrisConsumed = onStatementUrisConsumed,
                            onPickFiles = onPickStatementFiles,
                            onBack = { overlay = Overlay.NONE }
                        )
                    }
                    Overlay.SHARED_EXPENSES -> SharedExpensesPeopleScreen(
                        people = people,
                        transactions = transactions,
                        recurringTransactions = recurringTransactions,
                        owed = owed,
                        bottomContentPadding = 0.dp,
                        onBack = { overlay = Overlay.NONE },
                        onCreate = viewModel::createPerson,
                        onRename = viewModel::renamePerson,
                        onSetActive = viewModel::setPersonActive,
                        onDelete = viewModel::deletePerson
                    )
                    else -> Box(Modifier.fillMaxSize().statusBarsPadding()) {
                        AnimatedContent(
                            targetState = route,
                            transitionSpec = { SpendTrackMotion.routeTransition(initialState, targetState) },
                            label = "RouteTransition",
                        ) { currentRoute ->
                            when (currentRoute) {
                                "home" -> {
                                    val activeFdPrincipal = remember(fixedDeposits) { fixedDeposits.filter { it.status == FixedDepositStatus.ACTIVE }.sumOf { it.principalPaise } }
                                    val currentDashboardMonth = remember(dashboardMonthStr) { runCatching { YearMonth.parse(dashboardMonthStr) }.getOrDefault(YearMonth.now()) }
                                    val loansTotal = remember(loans) { loans.filter { it.includeInNetWorth }.sumOf { it.currentOutstandingPaise } }
                                    val owedTotal = remember(owed) { owed.sumOf { if (it.direction == OwedDirection.OWED_TO_ME) it.amountPaise else -it.amountPaise } }
                                    Dashboard(
                                        transactions = transactions,
                                        accounts = accounts,
                                        categories = categories,
                                        budgets = budgets,
                                        loans = loansTotal,
                                        owed = owedTotal,
                                        activeFdPrincipal = activeFdPrincipal,
                                        bottomContentPadding = bottomContentPadding,
                                        selectedMonth = currentDashboardMonth,
                                        onMonthSelected = { dashboardMonthStr = it.toString() },
                                        onPayCard = { card ->
                                            selectedCardPaymentCardId = card.id
                                            overlay = Overlay.CARD_PAYMENT
                                        },
                                        onSettings = {
                                            previousRoute = route
                                            overlay = Overlay.NONE
                                            route = "settings"
                                        }
                                    )
                                }
                                "spending" -> {
                                    val currentFilterType = runCatching { DateFilterType.valueOf(spendingDateFilterType) }.getOrDefault(DateFilterType.THIS_MONTH)
                                    val currentDateFilter = TransactionDateFilter(
                                        type = currentFilterType,
                                        customStartDateEpochDay = spendingCustomStartDay,
                                        customEndDateEpochDay = spendingCustomEndDay
                                    )
                                    val currentKindFilter = runCatching { TransactionKindFilter.valueOf(spendingKindFilter) }
                                        .getOrDefault(TransactionKindFilter.ALL)
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
                                        accountFilter = spendingAccountFilter,
                                        onAccountFilterChange = { spendingAccountFilter = it },
                                        kindFilter = currentKindFilter,
                                        onKindFilterChange = { spendingKindFilter = it.name },
                                        amountRange = TransactionAmountRange(spendingMinimumPaise, spendingMaximumPaise),
                                        onAmountRangeChange = { spendingMinimumPaise = it.minimumPaise; spendingMaximumPaise = it.maximumPaise },
                                        dateFilter = currentDateFilter,
                                        onDateFilterChange = { newFilter ->
                                            spendingDateFilterType = newFilter.type.name
                                            spendingCustomStartDay = newFilter.customStartDateEpochDay
                                            spendingCustomEndDay = newFilter.customEndDateEpochDay
                                        },
                                        onEdit = { editingTransactionId = it.id; overlay = Overlay.EDIT_TRANSACTION },
                                        onSettings = {
                                            previousRoute = route
                                            overlay = Overlay.NONE
                                            route = "settings"
                                        }
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
                                    onSettings = {
                                        previousRoute = route
                                        overlay = Overlay.NONE
                                        route = "settings"
                                    }
                                )
                                "loans_owed" -> LoansOwed(
                                    loans = loans,
                                    owed = owed,
                                    accounts = accounts,
                                    bottomContentPadding = bottomContentPadding,
                                    onUpdateLoan = viewModel::updateLoan,
                                    onDeleteLoan = viewModel::deleteLoan,
                                    onRepayLoan = { loanId, accId, amount -> viewModel.repayLoan(loanId, accId, amount) },
                                    onUpdateOwed = viewModel::updateOwed,
                                    onDeleteOwed = viewModel::deleteOwed,
                                    onSettleOwed = { owedId, amount, accId -> viewModel.settleOwed(owedId, amount, accId) },
                                    onSettings = {
                                        previousRoute = route
                                        overlay = Overlay.NONE
                                        route = "settings"
                                    }
                                )
                                else -> {
                                    val activeFdCount = remember(fixedDeposits) { fixedDeposits.count { it.status == FixedDepositStatus.ACTIVE } }
                                    val activeRecurringCount = remember(recurringTransactions) { recurringTransactions.count { it.isEnabled } }
                                    Settings(
                                        accounts.size,
                                        categories.size,
                                        activeFdCount,
                                        activeRecurringCount,
                                        bottomContentPadding,
                                        onBack = {
                                            val target = if (previousRoute == "settings" || previousRoute.isBlank()) "home" else previousRoute
                                            overlay = Overlay.NONE
                                            route = target
                                        },
                                        onAccounts = { overlay = Overlay.ACCOUNTS },
                                        onCategories = { overlay = Overlay.CATEGORIES },
                                        onFixedDeposits = { overlay = Overlay.FIXED_DEPOSITS },
                                        onRecurring = { overlay = Overlay.RECURRING_TRANSACTIONS },
                                        onBudgets = { overlay = Overlay.BUDGETS },
                                        onData = { overlay = Overlay.DATA },
                                        onReminders = { overlay = Overlay.REMINDERS },
                                        onAppearance = { overlay = Overlay.APPEARANCE },
                                        onAbout = { overlay = Overlay.ABOUT },
                                        onSharedExpenses = { overlay = Overlay.SHARED_EXPENSES },
                                        biometricEnabled = biometricEnabled,
                                        onBiometricPreferenceChange = onBiometricPreferenceChange
                                    )
                                }
                            }
                        }
                        if (overlay == Overlay.LOAN_OWED_CHOOSER) LoanOwedActionDialog(onLoan = { overlay = Overlay.ADD_LOAN }, onOwed = { overlay = Overlay.ADD_OWED }, onDismiss = { overlay = Overlay.NONE })
                        if (route in listOf("home", "spending", "loans_owed") && overlay == Overlay.NONE) {
                            FloatingControls(
                                route = route,
                                modifier = Modifier.align(androidx.compose.ui.Alignment.BottomCenter).onSizeChanged { floatingControlsHeightPx = it.height },
                                changeRoute = { route = it },
                            ) {
                                transactionInitialType = TransactionType.EXPENSE
                                overlay = if (route == "loans_owed") Overlay.LOAN_OWED_CHOOSER else Overlay.TRANSACTION
                            }
                        }
                    }
                }
                if (isAppLocked) BiometricLockScreen(onUnlockRequest)
            }
        }
    }
}
}

@Composable private fun BiometricLockScreen(onUnlock: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).clickable(onClick = {}).statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SpendTrack is locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onUnlock) { Text("Unlock with biometrics") }
    }
}

@Composable private fun FloatingControls(route: String, modifier: Modifier = Modifier, changeRoute: (String) -> Unit, add: () -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    Layout(
        content = {
            // Measurable 0: Navigation Pill (contains ONLY Home, Spending, Ledger)
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
                    contentDescription = "Add Transaction",
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
        var gapPx = preferredGapPx
        var totalWidth = pillPlaceable.width + gapPx + fabPlaceable.width
        if (totalWidth > layoutWidth - 2 * minMarginPx) {
            gapPx = maxOf(minGapPx, layoutWidth - 2 * minMarginPx - pillPlaceable.width - fabPlaceable.width)
            totalWidth = pillPlaceable.width + gapPx + fabPlaceable.width
        }
        val groupStartX = ((layoutWidth - totalWidth) / 2).coerceAtLeast(minMarginPx)
        val pillX = groupStartX
        val fabX = groupStartX + pillPlaceable.width + gapPx
        val pillY = (layoutHeight - pillPlaceable.height) / 2
        val fabY = (layoutHeight - fabPlaceable.height) / 2

        layout(layoutWidth, layoutHeight) {
            pillPlaceable.placeRelative(pillX, pillY)
            fabPlaceable.placeRelative(fabX, fabY)
        }
    }
}

@Composable
private fun CreditCardBillAlertSection(
    pendingBills: List<AccountEntity>,
    onPayCard: (AccountEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberSpendTrackHaptics()
    val pagerState = rememberPagerState(pageCount = { pendingBills.size })

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            pageSpacing = 12.dp
        ) { page ->
            val card = pendingBills[page]
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = card.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                val masked = card.maskedNumber?.takeIf { it.isNotBlank() }?.let { "•••• $it" }
                                val cycleInfo = card.statementCycleDay?.let { "Statement generated on ${it}th" } ?: "Bill pending"
                                Text(
                                    text = listOfNotNull(masked, cycleInfo).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (pendingBills.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${page + 1}/${pendingBills.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Next bill",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "Pending Bill Amount",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = money(card.balancePaise),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Button(
                            onClick = {
                                haptics.click()
                                onPayCard(card)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "Pay Now",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        if (pendingBills.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pendingBills.size) { index ->
                    val isCurrent = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (isCurrent) 18.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                    )
                }
            }
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
    onPayCard: (AccountEntity) -> Unit,
    onSettings: () -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var showMonthPicker by remember { mutableStateOf(false) }
    val isCurrentMonth = selectedMonth == YearMonth.now()
    val startEpoch = remember(selectedMonth) { selectedMonth.atDay(1).toEpochDay() }
    val endEpoch = remember(selectedMonth) { selectedMonth.atEndOfMonth().toEpochDay() }
    val inMonth = remember(transactions, startEpoch, endEpoch) {
        transactions.filter { it.dateEpochDay in startEpoch..endEpoch }
    }
    val spending = remember(inMonth) {
        inMonth.filter { it.type == TransactionType.EXPENSE && !it.isExcludedFromCalculations }.sumOf { it.mySharePaise ?: it.amountPaise }
    }
    val income = remember(inMonth) {
        inMonth.filter { it.type == TransactionType.INCOME && !it.isExcludedFromCalculations }.sumOf { it.amountPaise }
    }
    val cash = remember(accounts) {
        accounts.filterNot { it.isCreditCard }.sumOf { it.balancePaise }
    }
    val availableSavings = remember(accounts) {
        SavingsCalculator.calculateAvailableSavingsPaise(accounts)
    }
    val cards = remember(accounts) {
        accounts.filter { it.isCreditCard }.sumOf { it.balancePaise.coerceAtLeast(0L) }
    }
    val todayDate = remember { LocalDate.now() }
    val pendingBills = remember(accounts, todayDate) {
        accounts.filter { account ->
            if (!account.isCreditCard || account.balancePaise <= 0L) {
                false
            } else {
                val cycleDay = account.statementCycleDay ?: 1
                val dueDay = account.dueDay
                if (dueDay != null && dueDay < cycleDay) {
                    todayDate.dayOfMonth >= cycleDay || todayDate.dayOfMonth <= dueDay
                } else {
                    todayDate.dayOfMonth >= cycleDay
                }
            }
        }
    }
    val topBudgets = remember(budgets, categories, transactions, selectedMonth) {
        TopBudgetsCalculator.calculateTopBudgetCategories(budgets, categories, transactions, selectedMonth.toString())
    }
    val recentAccountMap = remember(accounts) { accounts.associateBy { it.id } }
    val recentCategoryMap = remember(categories) { categories.associateBy { it.id } }

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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Dashboard",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                    val today = remember { LocalDate.now() }
                    val isCurrentMonth = selectedMonth == YearMonth.from(today)
                    Surface(
                        onClick = {
                            haptics.selection()
                            showMonthPicker = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            val dayOfWeek = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
                            val dateText = if (isCurrentMonth) {
                                "$dayOfWeek, ${today.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))}"
                            } else {
                                val shortDay = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                                "${displayMonth(selectedMonth)} ($shortDay, ${today.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))})"
                            }
                            Text(
                                text = dateText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Month",
                                modifier = Modifier.size(18.dp),
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
        if (pendingBills.isNotEmpty()) {
            item {
                CreditCardBillAlertSection(
                    pendingBills = pendingBills,
                    onPayCard = onPayCard
                )
            }
        }
        item { DailyTransactionBarChart(transactions, selectedMonth = selectedMonth) }
        item { Text("Top Budgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
        if (topBudgets.isEmpty()) {
            item { TopBudgetCategoriesEmptyCard() }
        } else {
            items(topBudgets, key = { "top_budget_${it.categoryId}" }) { item ->
                TopBudgetCategoryCard(item)
            }
        }
        item { Text("Recent Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
        if (transactions.isEmpty()) {
            item { SummaryCard("No Transactions Yet", "", "Tap + to record your first transaction") }
        } else {
            items(transactions.take(5), key = { "recent_tx_${it.id}" }) { t ->
                TransactionCard(
                    transaction = t,
                    accounts = accounts,
                    categories = categories,
                    accountMap = recentAccountMap,
                    categoryMap = recentCategoryMap
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
    accountFilter: Long?,
    onAccountFilterChange: (Long?) -> Unit,
    kindFilter: TransactionKindFilter,
    onKindFilterChange: (TransactionKindFilter) -> Unit,
    amountRange: TransactionAmountRange,
    onAmountRangeChange: (TransactionAmountRange) -> Unit,
    dateFilter: TransactionDateFilter,
    onDateFilterChange: (TransactionDateFilter) -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onSettings: () -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    val coroutineScope = rememberCoroutineScope()
    var categoryExpanded by remember { mutableStateOf(false) }
    var dateExpanded by remember { mutableStateOf(false) }
    var showCustomDateRangeDialog by remember { mutableStateOf(false) }
    var showAmountRangeDialog by remember { mutableStateOf(false) }
    var minimumAmountText by rememberSaveable(amountRange.minimumPaise) { mutableStateOf(amountRange.minimumPaise?.let(::formatPaiseInput).orEmpty()) }
    var maximumAmountText by rememberSaveable(amountRange.maximumPaise) { mutableStateOf(amountRange.maximumPaise?.let(::formatPaiseInput).orEmpty()) }
    val accountMap = remember(accounts) { accounts.associateBy { it.id } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    val visible = remember(transactions, query, categoryFilter, accountFilter, kindFilter, amountRange, dateFilter, categories, accounts) {
        TransactionDateFilter.filterTransactions(
            transactions = transactions,
            categoryFilter = categoryFilter,
            dateFilter = dateFilter,
            query = query,
            categories = categories,
            accounts = accounts,
            accountFilter = accountFilter,
            kindFilter = kindFilter,
            amountRange = amountRange
        )
    }
    val today = remember { LocalDate.now() }
    val currentWeekMondayEpoch = remember(today) {
        today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).toEpochDay()
    }
    val isAllDatesFilter = dateFilter.type == DateFilterType.ALL_DATES
    var weeksBack by rememberSaveable { mutableIntStateOf(0) }
    val minEpochToShow = remember(currentWeekMondayEpoch, weeksBack) {
        LocalDate.ofEpochDay(currentWeekMondayEpoch).minusWeeks(weeksBack.toLong()).toEpochDay()
    }
    val displayedItems = remember(visible, minEpochToShow, isAllDatesFilter) {
        if (isAllDatesFilter) visible.filter { it.dateEpochDay >= minEpochToShow } else visible
    }
    val hasOlderTransactions = remember(visible, minEpochToShow, isAllDatesFilter) {
        isAllDatesFilter && visible.any { it.dateEpochDay < minEpochToShow }
    }
    val classification = remember(transactions, categoryFilter, accountFilter, kindFilter, amountRange, dateFilter) {
        TransactionDateFilter.filterForChart(
            transactions = transactions,
            categoryFilter = categoryFilter,
            dateFilter = dateFilter,
            accountFilter = accountFilter,
            kindFilter = kindFilter,
            amountRange = amountRange
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
    if (showAmountRangeDialog) {
        val minimum = minimumAmountText.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
        val maximum = maximumAmountText.trim().takeIf { it.isNotEmpty() }?.toBigDecimalOrNull()
        val minPaise = minimum?.let { parsePaise(minimumAmountText) }
        val maxPaise = maximum?.let { parsePaise(maximumAmountText) }
        val validMinimum = minimumAmountText.isBlank() || (minimum != null && minimum.signum() >= 0)
        val validMaximum = maximumAmountText.isBlank() || (maximum != null && maximum.signum() >= 0)
        val validRange = validMinimum && validMaximum && (minPaise == null || maxPaise == null || minPaise <= maxPaise)
        AlertDialog(
            onDismissRequest = { showAmountRangeDialog = false },
            title = { Text("Amount Range") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = minimumAmountText,
                        onValueChange = { minimumAmountText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Minimum amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = !validMinimum
                    )
                    OutlinedTextField(
                        value = maximumAmountText,
                        onValueChange = { maximumAmountText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Maximum amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = !validMaximum
                    )
                    if (validMinimum && validMaximum && !validRange) {
                        Text("Minimum must not exceed maximum.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        minimumAmountText = ""
                        maximumAmountText = ""
                        onAmountRangeChange(TransactionAmountRange())
                        showAmountRangeDialog = false
                    }) { Text("Clear") }
                    TextButton(onClick = { showAmountRangeDialog = false }) { Text("Cancel") }
                    TextButton(
                        onClick = {
                            onAmountRangeChange(TransactionAmountRange(minPaise, maxPaise))
                            showAmountRangeDialog = false
                        },
                        enabled = validRange
                    ) { Text("Apply") }
                }
            },
            dismissButton = {}
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
                Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Spending",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
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
                label = { Text("Search transactions") },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )
        }
        item { SpendingTrendChart(transactions = transactions) }
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
                            text = { Text("All categories") },
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
                            text = { Text("All dates") },
                            onClick = {
                                haptics.selection()
                                onDateFilterChange(TransactionDateFilter(DateFilterType.ALL_DATES))
                                dateExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("This month") },
                            onClick = {
                                haptics.selection()
                                onDateFilterChange(TransactionDateFilter(DateFilterType.THIS_MONTH))
                                dateExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Last 7 days") },
                            onClick = {
                                haptics.selection()
                                onDateFilterChange(TransactionDateFilter(DateFilterType.LAST_7_DAYS))
                                dateExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Last 30 days") },
                            onClick = {
                                haptics.selection()
                                onDateFilterChange(TransactionDateFilter(DateFilterType.LAST_30_DAYS))
                                dateExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Custom range…") },
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
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var accountExpanded by remember { mutableStateOf(false) }
                Box(Modifier.weight(1f)) {
                    FilterChip(
                        selected = accountFilter != null,
                        onClick = { haptics.selection(); accountExpanded = true },
                        label = {
                            Text(
                                accountFilter?.let { id -> accounts.find { it.id == id }?.name }?.let { "Payment: $it" } ?: "Payment Method",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    DropdownMenu(expanded = accountExpanded, onDismissRequest = { accountExpanded = false }) {
                        DropdownMenuItem(
                        text = { Text("All Payment Methods") },
                            onClick = { haptics.selection(); onAccountFilterChange(null); accountExpanded = false }
                        )
                        accounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = { haptics.selection(); onAccountFilterChange(account.id); accountExpanded = false }
                            )
                        }
                    }
                }
                FilterChip(
                    selected = amountRange.isActive,
                    onClick = { haptics.selection(); showAmountRangeDialog = true },
                    label = {
                        Text(
                            amountRange.toLabel(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionKindFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = kindFilter == filter,
                        onClick = { haptics.selection(); onKindFilterChange(filter) },
                        label = {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(filter.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
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
        } else if (displayedItems.isEmpty()) {
            item {
                SummaryCard(
                    "No Transactions This Week",
                    "",
                    "Tap View More below to load previous weeks"
                )
            }
        }
        items(displayedItems, key = { it.id }) { t ->
            TransactionCard(
                transaction = t,
                accounts = accounts,
                categories = categories,
                accountMap = accountMap,
                categoryMap = categoryMap,
                onEdit = onEdit,
                onDelete = remove
            )
        }
        if (hasOlderTransactions) {
            item {
                OutlinedButton(
                    onClick = {
                        haptics.selection()
                        weeksBack++
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("View More")
                }
            }
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
    accountMap: Map<Long, AccountEntity>? = null,
    categoryMap: Map<Long, CategoryEntity>? = null,
) {
    val haptics = rememberSpendTrackHaptics()
    val isTransfer = transaction.type == TransactionType.TRANSFER
    val isIncome = transaction.type == TransactionType.INCOME
    val dateText = formatDate(transaction.dateEpochDay)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = if (onEdit != null || onDelete != null) 8.dp else 16.dp,
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
                    val title = if (isTransfer && transaction.description.isNotBlank()) {
                        transaction.description
                    } else if (isTransfer) {
                        "Transfer"
                    } else {
                        transaction.description.ifBlank { "Transaction" }
                    }
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
                    val src = accountMap?.get(transaction.accountId)?.name
                        ?: accounts.find { it.id == transaction.accountId }?.name ?: "Account"
                    val dst = (transaction.destinationAccountId?.let { accountMap?.get(it)?.name })
                        ?: accounts.find { it.id == transaction.destinationAccountId }?.name ?: "Account"
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
                    val cat = categoryMap?.get(transaction.categoryId)?.name
                        ?: categories.find { it.id == transaction.categoryId }?.name ?: "Other"
                    val acc = accountMap?.get(transaction.accountId)?.name
                        ?: accounts.find { it.id == transaction.accountId }?.name ?: "Account"
                    Text(
                        text = "$cat · $acc · $dateText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (transaction.isExcludedFromCalculations) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.extraSmall,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "Excluded from calculations",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (!transaction.sharedWith.isNullOrBlank()) {
                    Text(
                        text = "Shared with: ${transaction.sharedWith}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
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
                                contentDescription = "Edit Transaction",
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
                                contentDescription = "Delete Transaction",
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
    accounts: List<AccountEntity> = emptyList(),
    bottomContentPadding: Dp,
    onUpdateLoan: (LoanEntity) -> Unit,
    onDeleteLoan: (LoanEntity) -> Unit,
    onRepayLoan: (Long, Long, Long) -> Unit,
    onUpdateOwed: (OwedEntity) -> Unit,
    onDeleteOwed: (OwedEntity) -> Unit,
    onSettleOwed: (Long, Long, Long) -> Unit,
    onSettings: () -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val owedToMe = remember(owed) { owed.filter { it.direction == OwedDirection.OWED_TO_ME }.sumOf { it.amountPaise } }
    val iOwe = remember(owed) { owed.filter { it.direction == OwedDirection.I_OWE }.sumOf { it.amountPaise } }
    var managingLoan by remember { mutableStateOf<LoanEntity?>(null) }
    var managingOwed by remember { mutableStateOf<OwedEntity?>(null) }
    var settlingOwed by remember { mutableStateOf<OwedEntity?>(null) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    coroutineScope.launch { listState.animateScrollToItem(0) }
                },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Ledger",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                IconButton(onClick = { haptics.selection(); onSettings() }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { SmallMetric("Owed to you", money(owedToMe), Modifier.weight(1f)); SmallMetric("You owe", money(iOwe), Modifier.weight(1f)) } }
        item { Text("Loans", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
        if (loans.isEmpty()) item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("No loans tracked yet", style = MaterialTheme.typography.titleMedium)
                    Text("Tap + to add a loan.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(loans, key = { "loan_${it.id}" }) { loan -> LoanCard(loan) { haptics.click(); managingLoan = loan } }
        item { Text("Personal Ledger", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
        if (owed.isEmpty()) item { SummaryCard("No Personal Ledger Entries", "", "") }
        items(owed, key = { "owed_${it.id}" }) { entry ->
            ManageableCard(
                entry.personName,
                money(entry.amountPaise),
                if (entry.amountPaise <= 0L) "Settled" else if (entry.direction == OwedDirection.OWED_TO_ME) "Owes you" else "You owe",
            ) { haptics.click(); managingOwed = entry }
        }
    }
    managingLoan?.let { loan ->
        LoanManageDialog(
            loan = loan,
            accounts = accounts,
            onDismiss = { managingLoan = null },
            onSave = { haptics.confirm(); onUpdateLoan(it); managingLoan = null },
            onDelete = { haptics.reject(); onDeleteLoan(loan); managingLoan = null },
            onRepay = { accountId, amount ->
                haptics.confirm()
                onRepayLoan(loan.id, accountId, amount)
                managingLoan = null
            }
        )
    }
    managingOwed?.let { entry ->
        OwedManageDialog(
            owed = entry,
            onDismiss = { managingOwed = null },
            onSave = { haptics.confirm(); onUpdateOwed(it); managingOwed = null },
            onDelete = { haptics.reject(); onDeleteOwed(entry); managingOwed = null },
            onOpenSettle = {
                val target = entry
                managingOwed = null
                settlingOwed = target
            }
        )
    }
    settlingOwed?.let { entry ->
        SettleOwedDialog(
            owed = entry,
            accounts = accounts,
            onDismiss = { settlingOwed = null },
            onSettle = { amount, accountId ->
                haptics.confirm()
                onSettleOwed(entry.id, amount, accountId)
                settlingOwed = null
            }
        )
    }
}

@Composable private fun SharedExpensesPeopleScreen(
    people: List<PersonEntity>,
    transactions: List<TransactionEntity>,
    recurringTransactions: List<RecurringTransactionEntity>,
    owed: List<OwedEntity>,
    bottomContentPadding: Dp,
    onBack: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onSetActive: (Long, Boolean) -> Unit,
    onDelete: (Long) -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    var name by remember { mutableStateOf("") }
    var managing by remember { mutableStateOf<PersonEntity?>(null) }
    var editing by remember { mutableStateOf<PersonEntity?>(null) }
    var deleting by remember { mutableStateOf<PersonEntity?>(null) }
    val dialogPerson = editing ?: deleting
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Text("Shared Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("New person") },
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = { if (name.isNotBlank()) { haptics.confirm(); onCreate(name.trim()); name = "" } }, enabled = name.isNotBlank()) {
                        Icon(Icons.Default.Add, contentDescription = "Add person")
                    }
                }
            )
        }
        if (people.isEmpty()) item { Text("People you add here will be available in shared expense participants.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(people, key = { it.id }) { person ->
            val personOwed = owed.filter { it.personName.equals(person.name, ignoreCase = true) }
            val relatedTransactions = transactions.filter { tx ->
                val split = SharedExpenseHelper.deserialize(tx.splitDetails)
                split?.participants.orEmpty().any { it.equals(person.name, true) } ||
                    tx.sharedWith.split(",").any { it.trim().equals(person.name, true) }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(person.name, style = MaterialTheme.typography.titleMedium)
                            Text("${relatedTransactions.size} shared expenses${if (!person.isActive) " · Inactive" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { managing = person }) { Text("Edit") }
                    }
                    val balance = personOwed.sumOf { if (it.direction == OwedDirection.OWED_TO_ME) it.amountPaise else -it.amountPaise }
                    if (balance != 0L) Text(
                        "${if (balance > 0) "Owed to you" else "You owe"}: ${money(kotlin.math.abs(balance))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
    managing?.let { person ->
        val hasHistory = transactions.any { tx ->
            val split = SharedExpenseHelper.deserialize(tx.splitDetails)
            split?.participants.orEmpty().any { it.equals(person.name, true) } ||
                split?.shares?.keys.orEmpty().any { it.equals(person.name, true) } ||
                tx.sharedWith.split(",").any { it.trim().equals(person.name, true) }
        } || owed.any { it.personName.equals(person.name, true) } || recurringTransactions.any {
            it.sharedWith.split(",").any { name -> name.trim().equals(person.name, true) }
        }
        AlertDialog(
            onDismissRequest = { managing = null },
            title = { Text(person.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { name = person.name; editing = person; managing = null }) { Text("Rename") }
                    TextButton(onClick = { haptics.selection(); onSetActive(person.id, !person.isActive); managing = null }) {
                        Text(if (person.isActive) "Disable" else "Enable")
                    }
                    if (hasHistory) Text("Delete unavailable because this person has financial history.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    else TextButton(onClick = { deleting = person; managing = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = { managing = null }) { Text("Cancel") } }
        )
    }
    if (dialogPerson != null) {
        val isRename = editing != null
        AlertDialog(
            onDismissRequest = { editing = null; deleting = null; name = "" },
            title = { Text(if (isRename) "Rename person" else "Delete person?") },
            text = {
                if (isRename) {
                    val duplicate = people.any { it.id != dialogPerson.id && it.name.trim().equals(name.trim(), ignoreCase = true) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                        if (duplicate) Text("A person with this name already exists.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
                else Text("This person has no historical shared expenses or Ledger records and will be removed.")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (isRename && name.isNotBlank()) onRename(dialogPerson.id, name.trim())
                    else if (!isRename) onDelete(dialogPerson.id)
                    editing = null; deleting = null; name = ""
                }, enabled = !isRename || (name.isNotBlank() && people.none { it.id != dialogPerson.id && it.name.trim().equals(name.trim(), ignoreCase = true) })) { Text(if (isRename) "Save" else "Delete") }
            },
            dismissButton = { TextButton(onClick = { editing = null; deleting = null; name = "" }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    title: String,
    sub: String = "",
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    },
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (sub.isNotBlank()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        modifier = Modifier.padding(start = 56.dp)
    )
}

@Composable
private fun SettingsCard(title: String, sub: String, onClick: () -> Unit = {}) {
    SettingsRow(title = title, sub = sub, onClick = onClick)
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
    onAppearance: () -> Unit,
    onAbout: () -> Unit,
    onSharedExpenses: () -> Unit,
    biometricEnabled: Boolean,
    onBiometricPreferenceChange: (Boolean) -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = bottomContentPadding + 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = { haptics.click(); onBack() })
                Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }

        // Section 1: Accounts & Balances
        item { SettingsSectionHeader("Accounts & finances") }
        item { SettingsRow("Accounts", "$accounts accounts and cards", Icons.Outlined.AccountBalance, onClick = { haptics.click(); onAccounts() }) }
        item { SettingsDivider() }
        item { SettingsRow("Categories", "$categories categories", Icons.Outlined.Category, onClick = { haptics.click(); onCategories() }) }
        item { SettingsDivider() }
        item { SettingsRow("Shared expenses", "Manage people for group expenses", Icons.Outlined.People, onClick = { haptics.click(); onSharedExpenses() }) }
        item { SettingsDivider() }
        item { SettingsRow("Fixed deposits", "$activeFdCount active deposits", Icons.Outlined.Savings, onClick = { haptics.click(); onFixedDeposits() }) }

        // Section 2: Budgets & Automation
        item { SettingsSectionHeader("Budgets & automation") }
        item { SettingsRow("Budget", "Category budgets", Icons.Outlined.PieChart, onClick = { haptics.click(); onBudgets() }) }
        item { SettingsDivider() }
        item { SettingsRow("Recurring transactions", "$activeRecurringCount active schedules", Icons.Outlined.Repeat, onClick = { haptics.click(); onRecurring() }) }

        // Section 3: Preferences
        item { SettingsSectionHeader("Preferences") }
        item { SettingsRow("Appearance", "Theme and display options", Icons.Outlined.Palette, onClick = { haptics.click(); onAppearance() }) }
        item { SettingsDivider() }
        item { SettingsRow("Alerts & reminders", "Card and loan notifications", Icons.Outlined.Notifications, onClick = { haptics.click(); onReminders() }) }
        item { SettingsDivider() }
        item {
            SettingsRow(
                title = "Biometric authentication",
                sub = "Require biometrics to open SpendTrack",
                icon = Icons.Outlined.Fingerprint,
                onClick = { haptics.selection(); onBiometricPreferenceChange(!biometricEnabled) },
                trailing = {
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { haptics.selection(); onBiometricPreferenceChange(it) }
                    )
                }
            )
        }

        // Section 4: Data & App
        item { SettingsSectionHeader("Data & app") }
        item { SettingsRow("Data", "Backup and restore", Icons.Outlined.Storage, onClick = { haptics.click(); onData() }) }
        item { SettingsDivider() }
        item { SettingsRow("About", "App information and links", Icons.Outlined.Info, onClick = { haptics.click(); onAbout() }) }
    }
}

@Composable private fun AppearanceScreen(
    themeMode: AppThemeMode,
    trueBlack: Boolean,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onTrueBlackChange: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val haptics = rememberSpendTrackHaptics()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        item { SettingsSectionHeader("Theme") }
        items(AppThemeMode.entries) { mode ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { haptics.selection(); onThemeModeChange(mode) }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(mode.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(mode.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RadioButton(
                    selected = themeMode == mode,
                    onClick = { haptics.selection(); onThemeModeChange(mode) }
                )
            }
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        item { SettingsSectionHeader("Additional options") }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("True black", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text("Use pure black surfaces when dark theme is active.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = trueBlack, onCheckedChange = { haptics.selection(); onTrueBlackChange(it) })
            }
        }
    }
}

@Composable private fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    fun copy(label: String, value: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
    fun open(uri: String) { runCatching { uriHandler.openUri(uri) } }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Text("About", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            }
        }
        item { Text("SpendTrack", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item { SettingsRow("Read the README", "Project guide and feature details", Icons.Outlined.Info, onClick = { open("https://github.com/kartikdeshpande18/SpendTrackNative#readme") }) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.padding(start = 56.dp)) }
        item { SettingsRow("Latest release", "View the newest app release", Icons.Outlined.Info, onClick = { open("https://github.com/kartikdeshpande18/SpendTrackNative/releases/latest") }) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.padding(start = 56.dp)) }
        item { SettingsRow("Report an issue", "Open a GitHub issue", Icons.Outlined.Info, onClick = { open("https://github.com/kartikdeshpande18/SpendTrackNative/issues/new") }) }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.padding(start = 56.dp)) }
        item {
            SettingsRow(
                title = "Sponsor",
                sub = "UPI · kartik.deshpande@slc",
                icon = Icons.Outlined.Savings,
                onClick = { open("upi://pay?pa=kartik.deshpande%40slc&pn=Kartik%20Deshpande&cu=INR") },
                trailing = {
                    TextButton(onClick = { copy("UPI ID", "kartik.deshpande@slc") }) { Text("Copy") }
                }
            )
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.padding(start = 56.dp)) }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { copy("Version", version) }
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Version", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(version, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable private fun RemindersScreen(onBack: () -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(ReminderPreferences.isNotificationsEnabled(context)) }
    var cardTimes by remember { mutableStateOf(ReminderPreferences.cardLeadTimes(context)) }
    var loanTimes by remember { mutableStateOf(ReminderPreferences.loanLeadTimes(context)) }
    var editing by remember { mutableStateOf<ReminderType?>(null) }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Text("Alerts & reminders", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(horizontal = 4.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Bill reminders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(if (isEnabled) "Notifications enabled" else "Notifications disabled", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = isEnabled, onCheckedChange = {
                    haptics.selection()
                    isEnabled = it
                    ReminderPreferences.setNotificationsEnabled(context, it)
                })
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.padding(start = 4.dp)) }
        item {
            SettingsRow(
                title = "Card reminders",
                sub = cardTimes.summary(),
                icon = Icons.Outlined.Notifications,
                onClick = { haptics.click(); editing = ReminderType.CARD }
            )
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), modifier = Modifier.padding(start = 56.dp)) }
        item {
            SettingsRow(
                title = "Loan reminders",
                sub = loanTimes.summary(),
                icon = Icons.Outlined.Notifications,
                onClick = { haptics.click(); editing = ReminderType.LOAN }
            )
        }
    }
    when (editing) {
        ReminderType.CARD -> ReminderConfigDialog("Card reminders", cardTimes, onDismiss = { editing = null }) { value -> haptics.confirm(); ReminderPreferences.saveCardLeadTimes(context, value); cardTimes = value; editing = null }
        ReminderType.LOAN -> ReminderConfigDialog("Loan reminders", loanTimes, onDismiss = { editing = null }) { value -> haptics.confirm(); ReminderPreferences.saveLoanLeadTimes(context, value); loanTimes = value; editing = null }
        null -> Unit
    }
}

@Composable private fun ReminderSettingsCard(title: String, summary: String, onClick: () -> Unit) {
    SettingsRow(title = title, sub = summary, icon = Icons.Outlined.Notifications, onClick = onClick)
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
@Composable private fun DataScreen(
    viewModel: FinanceViewModel,
    restoreBackupUri: Uri?,
    onRestoreBackupUriConsumed: () -> Unit,
    onPickRestoreBackup: () -> Unit,
    onCreateBackup: (String) -> Unit,
    onBack: () -> Unit,
    onImportStatement: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var pendingRestore by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var isRestoring by remember { mutableStateOf(false) }
    LaunchedEffect(restoreBackupUri) {
        val uri = restoreBackupUri ?: return@LaunchedEffect
        val result = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("Unable to read file") }
        }
        result.fold({ pendingRestore = it }, { status = "Restore failed: ${it.localizedMessage ?: "Unable to read file"}" })
        onRestoreBackupUriConsumed()
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Text("Data", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            }
        }
        if (onImportStatement != null) {
            item { Button({ onImportStatement() }, Modifier.fillMaxWidth()) { Text("Import statement (PDF)") } }
        }
        item { OutlinedButton({ viewModel.exportBackup { result -> result.onSuccess(onCreateBackup).onFailure { status = "Backup failed: ${it.localizedMessage ?: "Unable to create backup"}" } } }, Modifier.fillMaxWidth()) { Text("Create backup") } }
        item {
            OutlinedButton({
                runCatching { onPickRestoreBackup() }
                    .onFailure { status = "Unable to open file picker: ${it.localizedMessage ?: "Try again"}" }
            }, Modifier.fillMaxWidth()) { Text("Restore backup") }
        }
        if (status.isNotBlank()) item { Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    pendingRestore?.let { text -> AlertDialog(onDismissRequest = { if (!isRestoring) pendingRestore = null }, title = { Text("Restore backup?") }, text = { Text("This replaces the current local data.") }, confirmButton = { TextButton({ isRestoring = true; viewModel.restoreBackup(text) { result -> isRestoring = false; status = result.fold({ "Backup restored" }, { "Restore failed: ${it.localizedMessage ?: "Invalid backup"}" }); pendingRestore = null } }, enabled = !isRestoring) { Text(if (isRestoring) "Restoring…" else "Restore") } }, dismissButton = { TextButton(onClick = { if (!isRestoring) pendingRestore = null }, enabled = !isRestoring) { Text("Cancel") } }) }
}
@Composable private fun SummaryCard(title: String, value: String, sub: String) {
    val isNetWorth = title == "Net Worth"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = if (isNetWorth) RoundedCornerShape(32.dp) else RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNetWorth) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            Modifier.padding(
                horizontal = if (isNetWorth) 24.dp else 16.dp,
                vertical = if (isNetWorth) 20.dp else 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (isNetWorth) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedContent(
                targetState = value,
                transitionSpec = { SpendTrackMotion.numberTransition() },
                label = "SummaryCardValue",
            ) { targetVal ->
                Text(
                    targetVal,
                    style = if (isNetWorth) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isNetWorth) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isNetWorth) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable private fun SmallMetric(title: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
            AnimatedContent(
                targetState = value,
                transitionSpec = { SpendTrackMotion.numberTransition() },
                label = "SmallMetricValue",
            ) { targetVal ->
                Text(
                    targetVal,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
@Composable private fun ActionDialog(close: () -> Unit) { AlertDialog(onDismissRequest = close, title = { Text("Add a loan or personal ledger entry") }, text = { Text("Loan and owed-entry forms are still to be completed.") }, confirmButton = { TextButton(close) { Text("OK") } }) }

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
                        ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                }
                if (onSettings != null) {
                    IconButton(onClick = { haptics.selection(); onSettings() }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (adding) item {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(if (card) "Card name" else "Account name") }, singleLine = true)
                OutlinedTextField(opening, { opening = it }, Modifier.fillMaxWidth(), label = { Text(if (card) "Current outstanding (₹)" else "Opening balance (₹)") }, singleLine = true)
                if (!card) {
                    OutlinedTextField(minBalance, { minBalance = it }, Modifier.fillMaxWidth(), label = { Text("Minimum balance (₹)") }, singleLine = true)
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
        } else item { Button({ haptics.click(); adding = true }, Modifier.fillMaxWidth()) { Text("Add account or card") } }
        items(accounts, key = { it.id }) { account ->
            val subtitle = if (account.isCreditCard) {
                val lim = account.creditLimitPaise
                if (lim != null && lim > 0) {
                    val util = ((account.balancePaise.toDouble() / lim) * 100).toInt()
                    "Limit: ${money(lim)} ($util% used)"
                } else {
                    "Credit card"
                }
            } else {
                if (account.interestRateBps > 0) {
                    "Savings · ${"%.2f".format(Locale.US, account.interestRateBps / 100.0)}% p.a."
                } else {
                    "Savings account"
                }
            }
            ManageableCard(account.name, money(account.balancePaise), subtitle) { haptics.click(); managing = account }
        }
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
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                Text("Categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            }
        }
        if (adding) item {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Category name") }, singleLine = true)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(income, { haptics.selection(); income = it }); Text("Income category") }
                    Button(onClick = { haptics.confirm(); onAdd(name.trim(), income); name = ""; income = false; adding = false }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save category") }
                }
            }
        } else item { Button({ haptics.click(); adding = true }, Modifier.fillMaxWidth()) { Text("Add category") } }
        item { Text("Income categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
        if (incomeCategories.isEmpty()) item { Text("No income categories", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(incomeCategories, key = { it.id }) { category ->
            val subtitle = if (category.isDisabled) "Income · Disabled" else "Income"
            ManageableCard(category.name, subtitle, "") { haptics.click(); managing = category }
        }
        item { Text("Expense categories", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
        if (expenseCategories.isEmpty()) item { Text("No expense categories", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(expenseCategories, key = { it.id }) { category ->
            val subtitle = if (category.isDisabled) "Expense · Disabled" else "Expense"
            ManageableCard(category.name, subtitle, "") { haptics.click(); managing = category }
        }
    }
    managing?.let { CategoryManageDialog(it, onDismiss = { managing = null }, onSave = { onUpdate(it); managing = null }, onDelete = { onDelete(it); managing = null }) }
}

@Composable private fun ManageableCard(title: String, value: String, subtitle: String, onManage: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ActionIcon(Icons.Default.Edit, "Manage", onClick = onManage)
        }
    }
}

@Composable private fun ActionIcon(icon: ImageVector, description: String, tint: Color? = null, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(durationMillis = SpendTrackMotion.DurationFast, easing = SpendTrackMotion.DecelEasing),
        label = "ActionIconScale"
    )
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.size(40.dp).scale(scale)
    ) {
        Icon(icon, contentDescription = description, tint = tint ?: LocalContentColor.current, modifier = Modifier.size(20.dp))
    }
}

@Composable fun SpendTrackBackButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = tween(durationMillis = SpendTrackMotion.DurationFast, easing = SpendTrackMotion.DecelEasing),
        label = "BackButtonScale"
    )
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier.size(40.dp).scale(scale)
    ) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(20.dp))
    }
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
    var interestRate by remember(account.id) {
        mutableStateOf(if (account.interestRateBps > 0) "%.2f".format(Locale.US, account.interestRateBps / 100.0) else "")
    }
    var interestFreq by remember(account.id) {
        mutableStateOf(runCatching { InterestCreditFrequency.valueOf(account.interestCreditFrequency) }.getOrDefault(InterestCreditFrequency.QUARTERLY))
    }
    var lastFour by remember(account.id) { mutableStateOf(account.maskedNumber) }; var limit by remember(account.id) { mutableStateOf(account.creditLimitPaise?.let { "%.2f".format(Locale.US, it / 100.0) }.orEmpty()) }; var cycle by remember(account.id) { mutableStateOf(account.statementCycleDay?.toString().orEmpty()) }; var due by remember(account.id) { mutableStateOf(account.dueDay?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage account") },
        text = { Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
            OutlinedTextField(balance, { balance = it }, Modifier.fillMaxWidth(), label = { Text(if (account.isCreditCard) "Current outstanding (₹)" else "Current balance (₹)") }, singleLine = true)
            if (!account.isCreditCard) {
                OutlinedTextField(minBalance, { minBalance = it }, Modifier.fillMaxWidth(), label = { Text("Minimum balance (₹)") }, singleLine = true)
                OutlinedTextField(interestRate, { interestRate = it }, Modifier.fillMaxWidth(), label = { Text("Interest rate (% p.a.)") }, placeholder = { Text("e.g. 3.5") }, singleLine = true)
                Text("Interest posting frequency", style = MaterialTheme.typography.labelMedium)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    InterestCreditFrequency.entries.forEachIndexed { index, frequency ->
                        SegmentedButton(
                            selected = interestFreq == frequency,
                            onClick = { haptics.selection(); interestFreq = frequency },
                            shape = SegmentedButtonDefaults.itemShape(index, InterestCreditFrequency.entries.size),
                            icon = {},
                            label = { Text(frequency.displayName, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) },
                        )
                    }
                }
                Text("Calculated from daily closing balances and credited ${interestFreq.displayName.lowercase()}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Pay bill")
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
                                val parsedRateBps = ((interestRate.toDoubleOrNull() ?: 0.0) * 100).toInt().coerceAtLeast(0)
                                onSave(
                                    account.copy(
                                        name = name.trim(),
                                        balancePaise = parsePaise(balance),
                                        maskedNumber = lastFour,
                                        creditLimitPaise = parsePaise(limit).takeIf { account.isCreditCard && it > 0 },
                                        statementCycleDay = cycle.toIntOrNull()?.takeIf { account.isCreditCard && it in 1..31 },
                                        dueDay = due.toIntOrNull()?.takeIf { account.isCreditCard && it in 1..31 },
                                        minimumBalancePaise = parsedMin,
                                        interestRateBps = parsedRateBps,
                                        interestCalculationMethod = InterestCalculationMethod.DAILY.name,
                                        interestCreditFrequency = interestFreq.name
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
    var disabled by remember(category.id) { mutableStateOf(category.isDisabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage category") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Category name") }, singleLine = true)
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(income, { haptics.selection(); income = it }); Text("Income category") }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(disabled, { haptics.selection(); disabled = it }); Text("Disable category (hide from new transactions)") }
            Text("Default categories and categories used by transactions or budgets cannot be deleted.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { TextButton({ haptics.confirm(); onSave(category.copy(name = name.trim(), isIncomeCategory = income, isDisabled = disabled)) }, enabled = name.isNotBlank()) { Text("Save") } },
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
    val current = remember(budgets, month) { budgets.filter { it.monthKey == month } }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val monthlyCategorySpending = remember(transactions, month) {
        val parsedYm = runCatching { YearMonth.parse(month) }.getOrElse { YearMonth.now() }
        val startEpoch = parsedYm.atDay(1).toEpochDay()
        val endEpoch = parsedYm.atEndOfMonth().toEpochDay()
        val map = HashMap<Long, Long>()
        for (t in transactions) {
            if (t.type == TransactionType.EXPENSE && t.dateEpochDay in startEpoch..endEpoch) {
                val catId = t.categoryId
                if (catId != null) {
                    map[catId] = (map[catId] ?: 0L) + (t.mySharePaise ?: t.amountPaise)
                }
            }
        }
        map
    }
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                    Text("Budget", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                }
                Text(displayMonth(month), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (adding) item {
            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth()) {
                    OutlinedButton({ categoryMenu = true }, Modifier.fillMaxWidth(), enabled = expenseCategories.isNotEmpty()) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Category"); Text(expenseCategories.getOrNull(categoryIndex)?.name ?: "Choose") } }
                    DropdownMenu(categoryMenu, { categoryMenu = false }) { expenseCategories.forEachIndexed { index, category -> DropdownMenuItem({ Text(category.name) }, { haptics.selection(); categoryIndex = index; categoryMenu = false }) } }
                }
                OutlinedTextField(limit, { limit = it }, Modifier.fillMaxWidth(), label = { Text("Monthly limit (₹)") }, singleLine = true)
                Button(onClick = { haptics.confirm(); expenseCategories.getOrNull(categoryIndex)?.let { onSave(it.id, month, parsePaise(limit)); limit = ""; adding = false } }, enabled = parsePaise(limit) > 0 && expenseCategories.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Save budget") }
            } }
        } else item { Button({ haptics.click(); adding = true }, Modifier.fillMaxWidth()) { Text("Add budget") } }
        if (current.isEmpty()) item { SummaryCard("No Budgets", "", "") }
        items(current, key = { it.id }) { budget ->
            val category = categoryMap[budget.categoryId]?.name ?: "Category"
            val used = monthlyCategorySpending[budget.categoryId] ?: 0L
            val progress = if (budget.limitPaise > 0) (used.toFloat() / budget.limitPaise.toFloat()).coerceIn(0f, 1f) else 0f
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(category, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text("${money(used)} / ${money(budget.limitPaise)}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                        ActionIcon(Icons.Default.Delete, "Delete budget", MaterialTheme.colorScheme.error) { haptics.reject(); onDelete(budget) }
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable private fun LoanCard(loan: LoanEntity, onManage: () -> Unit) {
    val repaid = if (loan.originalPrincipalPaise > 0) ((loan.originalPrincipalPaise - loan.currentOutstandingPaise).toFloat() / loan.originalPrincipalPaise).coerceIn(0f, 1f) else 0f
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(loan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text("${(repaid * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            LinearProgressIndicator(
                progress = { repaid },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
            )
            Text(money(loan.currentOutstandingPaise), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("EMI ${money(loan.emiPaise)} · Due ${LocalDate.ofEpochDay(loan.nextDueEpochDay)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionIcon(Icons.Default.Edit, "Manage Loan", onClick = onManage)
            }
        }
    }
}

@Composable private fun LoanManageDialog(
    loan: LoanEntity,
    accounts: List<AccountEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (LoanEntity) -> Unit,
    onDelete: () -> Unit,
    onRepay: (Long, Long) -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    var name by remember(loan.id) { mutableStateOf(loan.name) }
    var repayment by remember(loan.id) { mutableStateOf("") }
    val paymentAccounts = remember(accounts) { accounts.filterNot { it.isCreditCard } }
    var selectedAccountIndex by remember(paymentAccounts) { mutableIntStateOf(0) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val selectedAccount = paymentAccounts.getOrNull(selectedAccountIndex)
    val repaymentPaise = parsePaise(repayment).coerceAtMost(loan.currentOutstandingPaise)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Loan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Loan name") }, singleLine = true)
                OutlinedTextField(
                    repayment,
                    { repayment = it },
                    Modifier.fillMaxWidth(),
                    label = { Text("Principal repayment / EMI (₹)") },
                    placeholder = { if (loan.emiPaise > 0) Text("EMI: ${money(loan.emiPaise)}") else null },
                    singleLine = true
                )
                if (paymentAccounts.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { accountMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Pay from:")
                                Text(selectedAccount?.name ?: "Select account")
                            }
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            paymentAccounts.forEachIndexed { index, acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${money(acc.balancePaise)})") },
                                    onClick = {
                                        haptics.selection()
                                        selectedAccountIndex = index
                                        accountMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Text("Outstanding: ${money(loan.currentOutstandingPaise)} · Paid: ${money(loan.principalPaidPaise)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (repaymentPaise > 0 && selectedAccount != null) {
                    Button(
                        onClick = {
                            haptics.confirm()
                            onRepay(selectedAccount.id, repaymentPaise)
                        }
                    ) {
                        Text("Pay ${money(repaymentPaise)}")
                    }
                }
                TextButton(
                    onClick = {
                        haptics.confirm()
                        onSave(loan.copy(name = name.trim()))
                    },
                    enabled = name.isNotBlank()
                ) {
                    Text("Save")
                }
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
        }
    )
}

@Composable private fun OwedManageDialog(
    owed: OwedEntity,
    onDismiss: () -> Unit,
    onSave: (OwedEntity) -> Unit,
    onDelete: () -> Unit,
    onOpenSettle: (() -> Unit)? = null
) {
    val haptics = rememberSpendTrackHaptics()
    var person by remember(owed.id) { mutableStateOf(owed.personName) }
    var note by remember(owed.id) { mutableStateOf(owed.note) }
    val isIncoming = owed.direction == OwedDirection.OWED_TO_ME

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Owed Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isIncoming) "Owed to you" else "You owe",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = money(owed.amountPaise),
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (owed.repaidAmountPaise > 0) {
                            Text(
                                text = "Already repaid: ${money(owed.repaidAmountPaise)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = person,
                    onValueChange = { person = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Person") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    singleLine = true
                )

                if (owed.amountPaise > 0 && onOpenSettle != null) {
                    Button(
                        onClick = {
                            haptics.click()
                            onOpenSettle()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Record Settlement")
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { haptics.click(); onDismiss() }) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        haptics.confirm()
                        onSave(owed.copy(personName = person.trim(), note = note.trim()))
                    },
                    enabled = person.isNotBlank()
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { haptics.reject(); onDelete() },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        }
    )
}

@Composable private fun SettleOwedDialog(
    owed: OwedEntity,
    accounts: List<AccountEntity> = emptyList(),
    onDismiss: () -> Unit,
    onSettle: (Long, Long) -> Unit
) {
    val haptics = rememberSpendTrackHaptics()
    val isIncoming = owed.direction == OwedDirection.OWED_TO_ME
    val eligibleAccounts = remember(accounts, isIncoming) {
        if (isIncoming) accounts else accounts.filterNot { it.isCreditCard }
    }
    var selectedAccountIndex by remember(eligibleAccounts) { mutableIntStateOf(0) }
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val selectedAccount = eligibleAccounts.getOrNull(selectedAccountIndex)

    var isFullSettlement by remember(owed.id) { mutableStateOf(true) }
    var customAmountText by remember(owed.id) { mutableStateOf("") }

    val effectivePaise = if (isFullSettlement) {
        owed.amountPaise
    } else {
        parsePaise(customAmountText).coerceAtMost(owed.amountPaise)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record settlement") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = if (isIncoming) {
                        "Receive settlement from ${owed.personName}"
                    } else {
                        "Pay settlement to ${owed.personName}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isFullSettlement,
                        onClick = {
                            haptics.selection()
                            isFullSettlement = true
                            customAmountText = ""
                        },
                        label = { Text("Full (${money(owed.amountPaise)})") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !isFullSettlement,
                        onClick = {
                            haptics.selection()
                            isFullSettlement = false
                        },
                        label = { Text("Custom amount") },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!isFullSettlement) {
                    OutlinedTextField(
                        value = customAmountText,
                        onValueChange = { customAmountText = it },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Start),
                        label = { Text("Custom amount (₹)") },
                        placeholder = { Text("Max: ${money(owed.amountPaise)}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true
                    )
                }

                if (eligibleAccounts.isNotEmpty()) {
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { accountMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (isIncoming) "Deposit to:" else "Pay from:")
                                Text(selectedAccount?.name ?: "Select account")
                            }
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false }
                        ) {
                            eligibleAccounts.forEachIndexed { index, acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.name} (${money(acc.balancePaise)})") },
                                    onClick = {
                                        haptics.selection()
                                        selectedAccountIndex = index
                                        accountMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (effectivePaise > 0 && selectedAccount != null) {
                        haptics.confirm()
                        onSettle(effectivePaise, selectedAccount.id)
                    }
                },
                enabled = effectivePaise > 0 && selectedAccount != null
            ) {
                Text("Confirm Settlement (${money(effectivePaise)})")
            }
        },
        dismissButton = {
            TextButton(onClick = { haptics.click(); onDismiss() }) {
                Text("Cancel")
            }
        }
    )
}

@Composable private fun LoanOwedActionDialog(onLoan: () -> Unit, onOwed: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp,
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("Add", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
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
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSecondaryContainer)
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
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack); Text("Add loan", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium) }
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

@Composable private fun AddOwedScreen(people: List<PersonEntity>, onBack: () -> Unit, onSave: (OwedEntity) -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    var person by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }; var owesMe by remember { mutableStateOf(true) }
    var personMenu by remember { mutableStateOf(false) }
    val paise = parsePaise(amount)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack); Text("Add owed entry", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium) }
        ExposedDropdownMenuBox(expanded = personMenu, onExpandedChange = { personMenu = it }) {
            OutlinedTextField(
                value = person,
                onValueChange = {},
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                label = { Text("Person") },
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = personMenu) },
                singleLine = true
            )
            ExposedDropdownMenu(expanded = personMenu, onDismissRequest = { personMenu = false }) {
                people.forEach { entry ->
                    DropdownMenuItem(text = { Text(entry.name) }, onClick = { person = entry.name; personMenu = false })
                }
            }
        }
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("Amount (₹)") }, singleLine = true)
        OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(owesMe, { haptics.selection(); owesMe = true }, { Text("Owes you") }); FilterChip(!owesMe, { haptics.selection(); owesMe = false }, { Text("You owe") }) }
        if (people.isEmpty()) Text("Add a person in Settings → Shared Expenses first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f)); Button(onClick = { haptics.confirm(); onSave(OwedEntity(personName = person, amountPaise = paise, direction = if (owesMe) OwedDirection.OWED_TO_ME else OwedDirection.I_OWE, note = note.trim())) }, enabled = people.any { it.name == person } && paise > 0, modifier = Modifier.fillMaxWidth()) { Text("Save Entry") }
    }
}

@Composable private fun CardPaymentScreen(accounts: List<AccountEntity>, initialCardId: Long? = null, onBack: () -> Unit, onPay: (Long, Long, Long) -> Unit) {
    val haptics = rememberSpendTrackHaptics()
    val cards = accounts.filter { it.isCreditCard && it.balancePaise > 0 }
    val sources = accounts.filterNot { it.isCreditCard }
    val initialCardIndex = remember(cards, initialCardId) {
        val found = cards.indexOfFirst { it.id == initialCardId }
        if (found >= 0) found else 0
    }
    var cardIndex by remember(initialCardIndex) { mutableIntStateOf(initialCardIndex) }; var sourceIndex by remember { mutableIntStateOf(0) }
    var cardMenu by remember { mutableStateOf(false) }; var sourceMenu by remember { mutableStateOf(false) }
    var fullPayment by remember { mutableStateOf(true) }; var amount by remember { mutableStateOf("") }
    val card = cards.getOrNull(cardIndex); val source = sources.getOrNull(sourceIndex)
    val amountPaise = if (fullPayment) card?.balancePaise ?: 0 else parsePaise(amount)
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { ActionIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack); Text("Pay Card Bill", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium) }
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
    val remPaise = (absPaise % 100).toInt()
    val paiseStr = if (remPaise < 10) "0$remPaise" else "$remPaise"
    val rupeeStr = rupees.toString()
    val formattedRupees = if (rupeeStr.length <= 3) {
        rupeeStr
    } else {
        val lastThree = rupeeStr.takeLast(3)
        val rest = rupeeStr.dropLast(3)
        val sb = java.lang.StringBuilder(rupeeStr.length + (rest.length / 2) + 1)
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

private fun formatPaiseInput(paise: Long): String = "${paise / 100}.${(paise % 100).toString().padStart(2, '0')}"

private fun TransactionAmountRange.toLabel(): String = when {
    minimumPaise != null && maximumPaise != null -> "${money(minimumPaise)} – ${money(maximumPaise)}"
    minimumPaise != null -> "From ${money(minimumPaise)}"
    maximumPaise != null -> "Up to ${money(maximumPaise)}"
    else -> "Amount Range"
}

private fun displayMonth(ym: YearMonth): String = "${ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())} ${ym.year}"
private fun displayMonth(ymString: String): String = runCatching { displayMonth(YearMonth.parse(ymString)) }.getOrDefault(ymString)
private fun formatDate(epochDay: Long): String = formatEpochDate(epochDay)
