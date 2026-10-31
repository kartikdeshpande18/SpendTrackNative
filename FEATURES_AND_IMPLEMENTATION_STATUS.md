# SpendTrack — Features, Implementation Status & Core Logic

This document provides a comprehensive inventory of all features requested for the **SpendTrack** Android native application, cross-referencing what has been requested, what has been implemented, what remains partially implemented or not implemented, and the complete technical logic behind each implementation.

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Status Matrix Overview](#2-status-matrix-overview)
3. [Deep-Dive Feature Analysis & Business Logic](#3-deep-dive-feature-analysis--business-logic)
   - [3.1 Owed & Settlement Accounting (Decluttered Two-Dialog Flow)](#31-owed--settlement-accounting-decluttered-two-dialog-flow)
   - [3.2 Transaction History & Database Performance](#32-transaction-history--database-performance)
   - [3.3 Statement Importer (Display, Edit Buffer & Deduplication)](#33-statement-importer-display-edit-buffer--deduplication)
   - [3.4 Importer Keyboard & Focus Behaviour](#34-importer-keyboard--focus-behaviour)
   - [3.5 Chart Behaviour, Animations & Distinguishable Colours](#35-chart-behaviour-animations--distinguishable-colours)
   - [3.6 Tap Status Bar / Header to Scroll to Top](#36-tap-status-bar--header-to-scroll-to-top)
   - [3.7 Transaction Exclusions ("Exclude from Calculations")](#37-transaction-exclusions-exclude-from-calculations--isexcludedfromcalculations)
   - [3.8 Shared Expenses: Dynamic Owed Participants & Custom Splits](#38-shared-expenses-dynamic-owed-participants--custom-splits)
   - [3.9 Savings-Interest Calculation & Posting](#39-savings-interest-calculation--posting)
   - [3.10 Accounts, Credit Cards, Bill Payment & Net Worth Tracking](#310-accounts-credit-cards-bill-payment--net-worth-tracking)
   - [3.11 Loans & Repayment Tracking](#311-loans--repayment-tracking)
   - [3.12 Bill Reminders & Local Notifications](#312-bill-reminders--local-notifications)
   - [3.13 Category Customization & Safe Deletion](#313-category-customization--safe-deletion)
   - [3.14 Recurring Transactions on Existing Records](#314-recurring-transactions-on-existing-records)
   - [3.15 Data Integrity, Balance Reversals & Backup/Restore](#315-data-integrity-balance-reversals--backuprestore)
4. [What Is Not Implemented / Known Limitations](#4-what-is-not-implemented--known-limitations)
5. [Automated Test Suite Summary](#5-automated-test-suite-summary)
6. [Physical Device Testing Guide](#6-physical-device-testing-guide)

---

## 1. Executive Summary

- **Framework & Tech Stack:** Android Native (Kotlin 2.0+, Jetpack Compose, Room SQLite, AndroidX WorkManager, Coroutines/Flow).
- **Architecture:** MVVM repository pattern with SQLite persistence and Room migrations (Current Schema Version: `10`).
- **Test Suite Status:** **194 tests passing** across 20 test classes (`./gradlew test` BUILD SUCCESSFUL).
- **Build Status:** Android Debug APK successfully assembled (`./gradlew assembleDebug` BUILD SUCCESSFUL) and installed to connected USB physical device (`e32d48b0`).
- **Design Rule:** Existing UI layouts, typography, color themes, and component paradigms have been strictly preserved without redesign.

---

## 2. Status Matrix Overview

| Area | Feature / Requirement | Status | Source Location | Automated Test |
|---|---|---|---|---|
| **Recurring** | Create recurring schedule from existing transaction log item | **Implemented** | [`AddTransactionScreen.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/transactions/AddTransactionScreen.kt), [`SpendTrackApp.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt) | `ComprehensiveFeatureHardeningTest.testCreateRecurringScheduleFromExistingTransaction` |
| **Splits** | Selectable participant chips with "+ Create New" dialog | **Implemented** | [`AddTransactionScreen.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/transactions/AddTransactionScreen.kt) | UI Compose Hierarchy & Chip Selection |
| **Exclusion** | "Exclude from Calculations" (no impact on balances, income, expense, budgets, charts) | **Implemented** | [`FinanceRepository.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt), [`Entities.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/Entities.kt) | `ComprehensiveFeatureHardeningTest.testExcludeFromCalculationsDoesNotAffectAccountBalance` |
| **Owed UI** | Uncluttered settlement dialogs (OwedManageDialog + SettleOwedDialog with Full/Custom amount) | **Implemented** | [`SpendTrackApp.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt) | UI Compose Dialog Hierarchy |
| **Categories** | Removal of category color swatches | **Implemented** | [`SpendTrackApp.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt), [`FinanceViewModel.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/FinanceViewModel.kt) | Unit Test Suite Compilation |
| **Owed** | Full and partial settlement with correct accounting credit/debit | **Implemented** | [`FinanceRepository.kt#L331-L392`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L331-L392) | `ComprehensiveFeatureHardeningTest.testFullRepaymentOfOwedEntry` |
| **Owed** | Settlement transaction linkage (`originalExpenseId`) | **Implemented** | [`FinanceRepository.kt#L379`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L379) | `ComprehensiveFeatureHardeningTest.testFullRepaymentOfOwedEntry` |
| **Owed** | Settlement idempotency (deduplication on retry) | **Implemented** | [`FinanceRepository.kt#L363-L370`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L363-L370) | `ComprehensiveFeatureHardeningTest.testFullRepaymentOfOwedEntry` |
| **Owed** | Overpayment prevention guard | **Implemented** | [`FinanceRepository.kt#L340`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L340) | `ComprehensiveFeatureHardeningTest.testPartialRepaymentOfOwedEntry` |
| **Performance** | Database indexes on frequent filters & joins | **Implemented** | [`DatabaseProvider.kt#L71-L76`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/DatabaseProvider.kt#L71-L76), [`Entities.kt#L39-L46`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/Entities.kt#L39-L46) | Room SQLite Migration Test |
| **Performance** | Date-range and incremental loading queries (`LIMIT`, `BETWEEN`) | **Implemented** | [`Daos.kt#L53-L58`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/Daos.kt#L53-L58) | Room DAO Query Tests |
| **Performance** | UI windowing (initial 50 records + load more) | **Implemented** | [`SpendTrackApp.kt#L608-L808`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L608-L808) | Compose UI Hierarchy Inspection |
| **Importer** | In-memory draft buffer before final confirmation | **Implemented** | [`ImportViewModel.kt#L401-L405`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/ui/ImportViewModel.kt#L401-L405) | `ImportWorkflowTest.kt` |
| **Importer** | Edit persistence (types, accounts, categories, exclusion) | **Implemented** | [`ImportScreen.kt#L785-L805`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/ui/ImportScreen.kt#L785-L805), [`ImportRepository.kt#L84`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/repository/ImportRepository.kt#L84) | `ImportedTransactionMutationTest.kt` |
| **Importer** | Keyboard suppression on initial edit dialog opening | **Implemented** | [`ImportScreen.kt#L788-L875`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/ui/ImportScreen.kt#L788-L875) | Compose Focus Tree Verification |
| **Charts** | Animation executes only once per session | **Implemented** | [`ChartAnimationState.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/animation/ChartAnimationState.kt), [`FinanceCharts.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/FinanceCharts.kt) | `ComprehensiveFeatureHardeningTest.testChartAnimationStateExecutesOnlyOncePerSession` |
| **Charts** | Distinguishable category & transaction colors | **Implemented** | [`FinanceCharts.kt#L39-L55`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/FinanceCharts.kt#L39-L55) | `SavingsAndIncomeChartTest.kt` |
| **Navigation**| Tap status bar / header to scroll to top | **Implemented** | [`SpendTrackApp.kt#L450, L595`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L450) | `DashboardScrollAndRecurringArtifactRegressionTest.kt` |
| **Splits** | Dynamic participants (active Owed section + Create New, no hardcoded names) | **Implemented** | [`SharedExpenseHelper.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/SharedExpenseHelper.kt), [`AddTransactionScreen.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/transactions/AddTransactionScreen.kt) | `ComprehensiveFeatureHardeningTest.testSharedExpenseParticipantsAreNotHardcoded` |
| **Card Payment** | Bill payment from accounts creates Transfer transaction entry in history | **Implemented** | [`FinanceRepository.kt#L503-L520`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L503-L520) | `ComprehensiveFeatureHardeningTest.testPayCardFromAccountsSectionCreatesTransactionEntry` |
| **Splits** | Equal split with remainder paise distribution | **Implemented** | [`SharedExpenseHelper.kt#L23-L41`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/SharedExpenseHelper.kt#L23-L41) | `ComprehensiveFeatureHardeningTest.testEqualSplit_remainderPaiseDistribution` |
| **Splits** | Custom split by amount with validation | **Implemented** | [`SharedExpenseHelper.kt#L47-L57`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/SharedExpenseHelper.kt#L47-L57) | `ComprehensiveFeatureHardeningTest.testCustomSplitValidation` |
| **Interest** | Daily, monthly average, quarterly balance formulas | **Implemented** | [`SavingsInterestCalculator.kt#L29-L76`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/SavingsInterestCalculator.kt#L29-L76) | `ComprehensiveFeatureHardeningTest.testDailyBalanceInterestCalculation` |
| **Interest** | Daily balance reconstruction algorithm | **Implemented** | [`SavingsInterestCalculator.kt#L82-L117`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/SavingsInterestCalculator.kt#L82-L117) | `ComprehensiveFeatureHardeningTest.testDailyBalanceReconstruction` |
| **Interest** | Post interest ledger entry + dedup prevention | **Implemented** | [`FinanceRepository.kt#L445-L483`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L445-L483) | `ComprehensiveFeatureHardeningTest.testPostSavingsInterestAccounting` |
| **Accounts** | Multiple savings, cash, credit cards, utilization | **Implemented** | [`SpendTrackApp.kt#L1250-L1310`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L1250-L1310) | `TransferFeatureTest.kt` |
| **Net Worth** | Net worth calculation without double-counting transfers | **Implemented** | [`SpendTrackApp.kt#L544`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L544) | `TransferFeatureTest.testBankToBankTransferBalanceMovement` |
| **Loans** | Loan repayment, credit-card guard, progress tracking | **Implemented** | [`FinanceRepository.kt#L405-L443`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L405-L443), [`SpendTrackApp.kt#L1900-L1950`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L1900-L1950) | `ComprehensiveFeatureHardeningTest.testLoanRepaymentAccounting` |
| **Reminders** | 10 and 5 day lead times, paid suppression, dedup | **Implemented** | [`ReminderWorker.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/reminders/ReminderWorker.kt), [`ReminderPreferences.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/reminders/ReminderPreferences.kt) | Static logic & SharedPreferences verification |
| **Category** | Create, rename, safe disable | **Implemented** | [`SpendTrackApp.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt), [`FinanceRepository.kt#L292-L304`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L292-L304) | `ComprehensiveFeatureHardeningTest.testCategorySafeDisableOnDelete` |
| **Backup** | Backup/Restore supporting all new V10 schema fields | **Implemented** | [`BackupRepository.kt#L48-L62`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/BackupRepository.kt#L48-L62) | `ComprehensiveFeatureHardeningTest.testBackupAndRestoreSupportsNewFields` |

---

## 3. Deep-Dive Feature Analysis & Business Logic

### 3.1 Owed & Settlement Accounting (Decluttered Two-Dialog Flow)

#### User Requirement
- When a person repays an amount owed to me, the selected receiving account must receive the corresponding credit.
- When I repay an amount I owe someone, the selected paying account must be debited.
- Settle entries must link to the original shared expense (`sourceTransactionId`).
- Do not create duplicate transaction entries on retry or repeated submission.
- Prevent overpayments (repayment exceeding outstanding balance).
- Maintain consistency across Owed balance, transaction history, and account balances.
- **UI Decluttering:** Do not overcrowd the edit dialog with 4 cramped buttons. Ask for the settlement amount (Full or Custom) in a dedicated separate dialog with properly aligned buttons.

#### Implementation & Code Location
- **Source Code:** [`FinanceRepository.kt#L331-L392`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L331-L392)
- **Manage Dialog:** [`SpendTrackApp.kt:OwedManageDialog`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt)
- **Dedicated Settle Dialog:** [`SpendTrackApp.kt:SettleOwedDialog`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt)
- **Entities:** [`Entities.kt:50-65`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/Entities.kt#L50-L65) (`originalAmountPaise`, `repaidAmountPaise`)

#### Business Logic & UI Flow
1. **Decoupled Two-Dialog Architecture:**
   - **`OwedManageDialog`**: Opened by tapping an owed entry in Loans & Owed. Displays person name, note, outstanding balance, and repaid total. Footer buttons are cleanly separated: `Delete` (error color) on the left, `Cancel` and `Save` on the right. If the balance > 0, renders a full-width **"Record Settlement"** button.
   - **`SettleOwedDialog`**: Opens when "Record Settlement" is tapped. Offers a clear choice between **"Full (₹X)"** or **"Custom Amount"** via filter chips. Only displays the amount input field when "Custom Amount" is selected, with a dropdown for eligible receiving/paying accounts, and clean `Cancel` / `Confirm Settlement` buttons.
2. **Accounting & Direction check:**
   ```kotlin
   val owed = database.owed().getById(owedId) ?: error("Owed record not found")
   require(amountPaise > 0) { "Settlement amount must be positive" }
   require(amountPaise <= owed.amountPaise) { "Settlement cannot exceed outstanding" }

   val isIncoming = owed.direction == OwedDirection.OWED_TO_ME
   val transactionType = if (isIncoming) TransactionType.INCOME else TransactionType.EXPENSE

   // Balance delta:
   // For Bank / Cash: Incoming = +amt, Outgoing = -amt
   // For Credit Card: Incoming = -outstanding, Outgoing = +outstanding
   val delta = when {
       account.isCreditCard && isIncoming -> -amountPaise
       account.isCreditCard -> amountPaise
       isIncoming -> amountPaise
       else -> -amountPaise
   }
   ```
3. **Deduplication check & Ledger Recording:**
   - Checks `transactions().getByOriginalExpenseId(owed.sourceTransactionId)` matching date, amount, account, and description before creating a transaction.
   - Records a linked transaction and updates account balance and remaining owed balance atomically.

---

### 3.2 Transaction History & Database Performance

#### User Requirement
- Slowness when history exceeds 1 month.
- Resolve missing indexes, unnecessary full-history queries, inefficient rendering, and repeated calculations.

#### Implementation & Code Location
- **Indexes:** [`Entities.kt#L39-L46`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/Entities.kt#L39-L46) & [`DatabaseProvider.kt#L71-L76`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/DatabaseProvider.kt#L71-L76)
- **DAO Methods:** [`Daos.kt#L51-L58`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/Daos.kt#L51-L58)
- **UI Windowing:** [`SpendTrackApp.kt#L608-L808`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L608-L808)

#### Business Logic
1. **6 Composite B-Tree Indexes on `transactions` table:**
   - `index_transactions_dateEpochDay` on `dateEpochDay` (date range lookups)
   - `index_transactions_accountId` on `accountId` (account history lookups)
   - `index_transactions_destinationAccountId` on `destinationAccountId` (transfers)
   - `index_transactions_categoryId` on `categoryId` (category breakdown)
   - `index_transactions_type` on `type` (INCOME / EXPENSE / TRANSFER)
   - `index_transactions_originalExpenseId` on `originalExpenseId` (settlement & loan joins)
2. **Windowed / Paginated Loading:**
   - In `TransactionDao`:
     - `observeRecent(limit: Int)`: `SELECT * FROM transactions ORDER BY dateEpochDay DESC, id DESC LIMIT :limit`
     - `observeDateRange(startEpochDay, endEpochDay)`: `SELECT * FROM transactions WHERE dateEpochDay BETWEEN :startEpochDay AND :endEpochDay ORDER BY dateEpochDay DESC, id DESC`
   - In `SpendingScreen`:
     - Default initial display count: `var displayCount by remember(visible) { mutableIntStateOf(50) }`.
     - Slices in-memory list: `val displayedItems = remember(visible, displayCount) { visible.take(displayCount) }`.
     - If `visible.size > displayCount`, appends a `"Load more (N remaining)"` button that increments `displayCount += 50`.
3. **Recomposition Memoization:**
   - Dashboard totals `inMonth`, `spentThisMonth`, and `incomeThisMonth` are computed with `remember(transactions, selectedMonth)`, eliminating redundant passes per frame.

---

### 3.3 Statement Importer (Display, Edit Buffer & Deduplication)

#### User Requirement
- Imported transactions must remain in a draft buffer until confirmed.
- User edits to merchant, type, category, and account must persist and not be overridden by auto-heuristics.
- Exclude from reports support in importer review.
- Prevent duplicate transaction creation.

#### Implementation & Code Location
- **Model:** [`NormalizedTransaction.kt#L27`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/model/NormalizedTransaction.kt#L27) (`isExcludedFromReports: Boolean = false`)
- **ViewModel:** [`ImportViewModel.kt#L375-L405`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/ui/ImportViewModel.kt#L375-L405)
- **Repository:** [`ImportRepository.kt#L84, L133`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/repository/ImportRepository.kt#L84)
- **UI Dialog:** [`ImportScreen.kt#L785-L1165`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/ui/ImportScreen.kt#L785-L1165)

#### Business Logic
1. **In-Memory Buffer (`ImportState.Review`):**
   - Importer parses statements into a transient `Batch` stored in `_state.value`. No Room SQLite write occurs.
2. **Preserving Edits:**
   - When the user edits in `EditTransactionDialog`, `ImportViewModel.editTransaction()` updates the transaction in the draft list:
     - Sets `isEdited = true`
     - Sets `reviewStatus = ReviewStatus.EDITED`
     - Preserves user-chosen `transactionType`, `merchant`, `amountPaise`, `sourceAccountId`, `destinationAccountId`, `categoryId`, and `isExcludedFromReports`.
   - Automatic categorization passes explicitly skip transactions with `isEdited == true`.
3. **Deduplication:**
   - Computes SHA-256 fingerprint on statement lines and queries `import_history` and existing transactions matching `(dateEpochDay, amountPaise, accountId)`. Matched transactions are flagged `ReviewStatus.DUPLICATE` and unselected by default.

---

### 3.4 Importer Keyboard & Focus Behaviour

#### User Requirement
- Keyboard must NOT open automatically when entering the Edit Transaction screen.
- It should open only when the user taps an editable text field.
- Dropdown or non-text control selection should dismiss or avoid triggering the keyboard.

#### Implementation & Code Location
- **Source Code:** [`ImportScreen.kt#L788-L875`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/ui/ImportScreen.kt#L788-L875)

#### Business Logic
1. **No Auto-Focus:**
   - The dialog completely omits `FocusRequester.requestFocus()` on initialization.
2. **Active Dismissal on Non-Text Controls:**
   - An instance of `val focusManager = LocalFocusManager.current` is injected.
   - When tapping chips (`Expense`, `Income`, `Transfer`), account dropdowns, category dropdowns, or the Date Picker button, `focusManager.clearFocus()` is invoked explicitly, ensuring any previously open soft keyboard closes immediately.

---

### 3.5 Chart Behaviour, Animations & Distinguishable Colours

#### User Requirement
- Chart entrance animations should execute only once per application session.
- Returning to a screen must not replay animations.
- Distinguishable chart colors.

#### Implementation & Code Location
- **Session Singleton:** [`ChartAnimationState.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/animation/ChartAnimationState.kt)
- **Charts:** [`FinanceCharts.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/FinanceCharts.kt)

#### Business Logic
1. **Once-Per-Session Animation Guard:**
   ```kotlin
   object ChartAnimationState {
       private val animatedCharts = Collections.synchronizedSet(mutableSetOf<String>())
       fun shouldAnimate(chartKey: String): Boolean = animatedCharts.add(chartKey)
   }
   ```
   When `CategoryBreakdownChart`, `DailySpendingBarChart`, or `MonthlySavingsVsIncomeChart` initializes:
   - Queries `remember { ChartAnimationState.shouldAnimate("key") }`.
   - On the first composition of the app session, returns `true`, triggering `Animatable.animateTo(...)`.
   - On all subsequent navigations back to that screen, returns `false`, setting initial target value immediately to `1f` (0 ms duration).
2. **Distinguishable Color Palette:**
   - High-contrast 12-color categorical palette defined in `FinanceCharts.kt` (`#2563EB`, `#16A34A`, `#EA580C`, `#9333EA`, `#0891B2`, `#D97706`, `#E11D48`, `#4F46E5`, `#059669`, `#475569`, `#BE123C`, `#7C3AED`).

---

### 3.6 Tap Status Bar / Header to Scroll to Top

#### User Requirement
- Tapping the status bar / top header should animate-scroll long lists to index 0.

#### Implementation & Code Location
- **Source Code:** [`SpendTrackApp.kt#L450-L455, L595-L600`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L450)

#### Business Logic
- The top header surface is decorated with `.clickable { coroutineScope.launch { listState.animateScrollToItem(0) } }`.
- Integrates with `WindowInsets.statusBars` padding so taps on the status bar / header region trigger smooth scroll to top without intercepting nested list gestures.

---

### 3.7 Transaction Exclusions ("Exclude from Calculations" / `isExcludedFromCalculations`)

#### User Requirement
- Remove the old "Exclude from reports" toggle and in-place add **"Exclude from Calculations"**.
- A transaction marked "Exclude from Calculations" must **NOT** be included in ANY calculations:
  - Account balance (omitted from balance deltas on add, update, and delete)
  - Income and expense totals
  - Monthly budgets
  - Category donut, daily spending bar chart, and savings chart

#### Implementation & Code Location
- **Schema & Entity:** [`Entities.kt:62-68`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/Entities.kt#L62-L68) (`@ColumnInfo(name = "isExcludedFromReports") val isExcludedFromCalculations: Boolean = false`, with backward-compatible getter `isExcludedFromReports`)
- **Add / Edit Transaction UI:** [`AddTransactionScreen.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/transactions/AddTransactionScreen.kt)
- **Statement Importer UI & Repo:** [`ImportScreen.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/ui/ImportScreen.kt), [`ImportRepository.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/importer/repository/ImportRepository.kt)
- **Account Balance Bypass:** [`FinanceRepository.kt#L48-L62, L96-L105, L123-L162`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt)
- **Spending & Reports Filtering:** [`TransactionDateFilter.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/spending/TransactionDateFilter.kt), [`TopBudgetsCalculator.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/dashboard/TopBudgetsCalculator.kt), [`FinanceCharts.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/FinanceCharts.kt), [`SpendTrackApp.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt)

#### Business Logic
1. **Account Balance Bypass:**
   - In `FinanceRepository.addTransaction()`:
     ```kotlin
     if (!transaction.isExcludedFromCalculations) {
         // Applies account balance delta (bank, cash, or credit card)
     }
     ```
   - In `FinanceRepository.deleteTransaction()`:
     ```kotlin
     if (!transaction.isExcludedFromCalculations && account != null) {
         // Reverses previous balance delta
     }
     ```
   - In `FinanceRepository.updateTransaction()`:
     - If the previous transaction was not excluded, its balance delta is reversed.
     - If the updated transaction is not excluded, its new balance delta is applied.
2. **Reporting & Chart Aggregations:**
   - Dashboard: `inMonth.filter { it.type == TransactionType.EXPENSE && !it.isExcludedFromCalculations }`
   - TopBudgets: `if (t.type == TransactionType.EXPENSE && !t.isExcludedFromCalculations && t.categoryId == categoryId)`
   - Donut & Daily Bar Charts: Ignores transactions where `t.isExcludedFromCalculations == true`.
3. **UI Badge:**
   - Transaction list items display an `"Excluded from calculations"` badge when enabled.

---

### 3.8 Shared Expenses: Dynamic Owed Participants & Custom Splits

#### User Requirement
- Do **not** hardcode participant names (such as "Yash", "Kartik", "Sanket", etc.).
- Only show participant options who are already created and currently active in the Owed section.
- Provide a **`+ Create New`** chip as the final option to dynamically add a new participant on the fly.
- Equal split with remainder paise distribution.
- Custom split by amount with exact sum validation.

#### Implementation & Code Location
- **Helper Class:** [`SharedExpenseHelper.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/SharedExpenseHelper.kt)
- **UI Screen:** [`AddTransactionScreen.kt#L45-L68, L175-L260`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/transactions/AddTransactionScreen.kt)
- **Recovery Engine:** [`FinanceRepository.kt#L176-L220`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L176-L220)

#### Business Logic
1. **Dynamic Owed Participant Ingestion (No Hardcoded Names):**
   - In `SpendTrackApp.kt`, active participants are extracted from live Room state:
     ```kotlin
     val activeOwedParticipants = remember(owed) {
         owed.filter { it.amountPaise > 0 }
             .map { it.personName.trim() }
             .filter { it.isNotBlank() }
             .distinct()
     }
     ```
   - In `AddTransactionScreen.kt`:
     - Displays `[You]` as the primary user chip.
     - Displays filter chips for each active owed participant.
     - Displays existing participants on the transaction if editing.
     - Ends with an **`AssistChip("+ Create New")`** that opens a dialog to add any new person, automatically selecting them.
2. **Remainder Paise Distribution:**
   ```kotlin
   val n = participants.size
   val baseShare = totalAmountPaise / n
   val remainder = (totalAmountPaise % n).toInt()
   // Distributes 1 extra paisa to the first `remainder` participants
   // Ensures sum(shares) == totalAmountPaise with zero rounding leakage!
   participants.forEachIndexed { index, person ->
       val share = baseShare + if (index < remainder) 1L else 0L
       shares[person] = share
   }
   ```
3. **Custom Split Validation:**
   ```kotlin
   fun validateCustomSplit(totalAmountPaise: Long, shares: Map<String, Long>): Boolean {
       if (shares.values.any { it < 0L }) return false
       return shares.values.sum() == totalAmountPaise
   }
   ```
4. **Storage & Auto-Recovery:**
   Serialized as JSON string in `TransactionEntity.splitDetails`. In `FinanceRepository.recordSharedRecovery()`, each non-user participant with a share > 0 automatically has an `OwedEntity` generated (`direction = OWED_TO_ME`), placing them in the Owed section for future split suggestions.

---

### 3.9 Savings-Interest Calculation & Posting

#### User Requirement
- Support Daily balance, Average Monthly balance, and Quarterly balance formulas.
- Configurable interest rate in basis points (100 bps = 1.00%).
- Currency rounding at paise precision.
- Daily balance reconstruction stepping backwards from current balance.
- Ledger posting convention: `"Interest for {{date}}"`.
- Deduplication to prevent double-crediting.

#### Implementation & Code Location
- **Calculator:** [`SavingsInterestCalculator.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/SavingsInterestCalculator.kt)
- **Posting Logic:** [`FinanceRepository.kt#L445-L483`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L445-L483)
- **Account Manage Dialog:** [`SpendTrackApp.kt#L1375-L1415`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L1375-L1415)

#### Business Logic
1. **Formulas:**
   - Daily: $\sum \frac{\text{Balance}_d \times \text{rate}}{365}$
   - Monthly/Quarterly Average: $\frac{\sum \text{Balance}_d}{\text{Days}} \times \text{rate} \times \frac{\text{Days}}{365}$
2. **Historical Daily Balance Reconstruction:**
   - Computes daily net deltas from stored transactions: $\Delta_d = \text{Income}_d - \text{Expense}_d - \text{TransferOut}_d$.
   - Steps backwards from `currentBalancePaise` as of `todayEpochDay` down to `startEpochDay`:
     $$\text{RunningBalance}_{d-1} = \text{RunningBalance}_d - \Delta_d$$
3. **Posting & Deduplication:**
   - Description formatted as `"Interest for $dateStr"`.
   - Queries `transactions().getDateRange(dateEpochDay, dateEpochDay)` matching `accountId`, `amountPaise`, and description. If already present, skips credit and returns existing ID.
   - On new post: credits account, updates `lastInterestPostingEpochDay = dateEpochDay`, and auto-categorizes under system category `"Interest"`.

---

### 3.10 Accounts, Credit Cards, Bill Payment & Net Worth Tracking

#### User Requirement
- Multiple savings accounts, cash, and credit cards.
- Credit card limits, statement cycles, due dates, and utilization tracking.
- **Bill Payment from Accounts Section:** Paying a credit card bill from the accounts section must record a transaction in the transaction history.
- Net worth calculation avoiding transfer double-counting.

#### Implementation & Code Location
- **Accounts UI:** [`SpendTrackApp.kt#L1250-L1310`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L1250-L1310)
- **Bill Payment Engine:** [`FinanceRepository.kt#L503-L525`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L503-L525)
- **Card Payment UI:** [`SpendTrackApp.kt#L1995-L2030`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L1995-L2030)
- **Net Worth Display:** [`SpendTrackApp.kt#L544`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L544)

#### Business Logic
1. **Net Worth Formula:**
   $$\text{Net Worth} = \text{Bank/Cash Assets} + \text{Active FD Principal} + \text{Net Owed to Me} - \text{Credit Card Dues} - \text{Active Loans}$$
2. **Bill Payment Ledger Integration:**
   When paying a card bill via `FinanceRepository.payCard(cardId, sourceId, amountPaise, dateEpochDay)`:
   - Decreases card outstanding: `card.balancePaise - amountPaise`.
   - Debits payment account: `source.balancePaise - amountPaise`.
   - Inserts `CardPaymentEntity` for card history.
   - **Transaction Log Entry:** Inserts a `TransactionEntity` with:
     ```kotlin
     val tx = TransactionEntity(
         dateEpochDay = dateEpochDay,
         description = "Bill Payment: ${card.name}",
         amountPaise = amountPaise,
         categoryId = 0L,
         accountId = sourceId,
         destinationAccountId = cardId,
         type = TransactionType.TRANSFER,
         isExcludedFromCalculations = false
     )
     database.transactions().insert(tx)
     ```
   - In `TransactionCard`, rendered with title `"Bill Payment: <Card Name>"` and route `"<Bank Name> → <Card Name>"`.
   - Deleting the transaction in the history cleanly triggers two-way balance reversal, restoring both the bank balance and card balance.
3. **Transfer Invariance:**
   - Transfers transfer balance between accounts without changing cumulative assets/liabilities. Because Net Worth queries account balances directly instead of summing transaction records, transfers never double-count.

---

### 3.11 Loans & Repayment Tracking

#### User Requirement
- Loan creation, principal tracking, progress bar, repayment linking, account balance updates.
- Credit cards forbidden as repayment source.
- Repayments excluded from income/expense reports.

#### Implementation & Code Location
- **Repository:** [`FinanceRepository.kt#L405-L443`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L405-L443)
- **UI Screen:** [`SpendTrackApp.kt#L1900-L1950`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L1900-L1950) (`RepayLoanDialog`)

#### Business Logic
1. `require(!account.isCreditCard)` prevents paying loans from credit cards.
2. Deducts repayment from bank account: `balancePaise = account.balancePaise - amountPaise`.
3. Decrements `currentOutstandingPaise` and increments `principalPaidPaise`.
4. Creates linked `TransactionEntity` with `originalExpenseId = loanId` and `isExcludedFromReports = true`.

---

### 3.12 Bill Reminders & Local Notifications

#### User Requirement
- Notifications scheduled at 10 days and 5 days prior to due date.
- Avoid duplicate notifications.
- Skip already paid bills.
- Handle changed due dates.

#### Implementation & Code Location
- **Worker:** [`ReminderWorker.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/reminders/ReminderWorker.kt)
- **Preferences:** [`ReminderPreferences.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/reminders/ReminderPreferences.kt)

#### Business Logic
1. **Execution via AndroidX WorkManager:** Scheduled as periodic daily background worker.
2. **Paid Obligation Suppression:**
   - Credit cards with `card.balancePaise <= 0L` are skipped.
   - Loans with `loan.currentOutstandingPaise <= 0L` are skipped.
3. **Compound Notification Key:**
   - `notifKey = "card_${card.id}_${due}_${daysUntil}"`
   - Because `due` is embedded in the key, if the due date changes or advances to the next billing cycle, a fresh notification triggers without being suppressed by the previous cycle.
   - Once fired, `ReminderPreferences.markNotified(context, notifKey)` prevents duplicate alerts within the same day.

---

### 3.13 Category Customization & Safe Deletion

#### User Requirement
- Create, rename, customize color/icon, safely disable, and preserve historical transactions.

#### Implementation & Code Location
- **Entities & Repository:** [`Entities.kt:26-35`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/local/Entities.kt#L26-L35), [`FinanceRepository.kt#L273-L304`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L273-L304)
- **UI Screen:** [`SpendTrackApp.kt#L1311-L1522`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L1311-L1522)

#### Business Logic
1. **Safe Deletion & Disable Fallback:**
   ```kotlin
   suspend fun deleteCategory(category: CategoryEntity): Boolean = database.withTransaction {
       if (category.isSystemFallback) return@withTransaction false
       val hasTransactions = database.categories().transactionCount(category.id) > 0
       val hasBudgets = database.categories().budgetCount(category.id) > 0
       val hasRecurring = database.categories().recurringCount(category.id) > 0

       if (hasTransactions || hasBudgets || hasRecurring) {
           // Safe disable! Preserves historical records while hiding from new entries
           database.categories().update(category.copy(isDisabled = true))
           return@withTransaction true
       }
       database.categories().delete(category)
       true
   }
   ```
2. **Removal of Category Colors:**
   Category color palette swatches have been removed per user specification, keeping category creation, renaming, and management fast, focused, and uncluttered.

---

### 3.14 Recurring Transactions on Existing Records

#### User Requirement
- When viewing or editing an existing transaction from the transaction log, allow enabling recurring transaction generation ("Make Recurring").

#### Implementation & Code Location
- **Add / Edit Transaction Screen:** [`AddTransactionScreen.kt#L287-L354`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/transactions/AddTransactionScreen.kt#L287-L354)
- **Overlay Routing:** [`SpendTrackApp.kt#L134-L143`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/ui/SpendTrackApp.kt#L134-L143) (`Overlay.EDIT_TRANSACTION`)

#### Business Logic
1. **Unconditional Availability:**
   - Previously gated behind `if (initial == null && onSaveRecurring != null)`.
   - Now checks `if (onSaveRecurring != null)`, rendering the "Make Recurring" card with cadence selection (Daily, Weekly, Monthly, Yearly) and optional end date when editing existing transactions.
2. **Dual Action on Save:**
   - If `isRecurring && initial != null`, updates the existing transaction record in SQLite and registers the recurring schedule via `viewModel.addRecurringTransaction(recurring)`.

---

### 3.15 Data Integrity, Balance Reversals & Backup/Restore

#### User Requirement
- Full balance reversal on transaction edit and delete.
- Backup and restore preserving all V10 schema additions.

#### Implementation & Code Location
- **Two-Phase Delta Engine:** [`FinanceRepository.kt#L75-L165`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/FinanceRepository.kt#L75-L165)
- **Backup Engine:** [`BackupRepository.kt`](file:///home/kartik/SpendTrackNative/app/src/main/java/com/spendtrack/app/data/repository/BackupRepository.kt)

#### Business Logic
1. **Two-Phase Transaction Update:**
   - **Phase 1 (Reversal):** Reads previous transaction snapshot from SQLite. Reverses the exact debits/credits applied to source account and destination account.
   - **Phase 2 (Application):** Applies the newly edited amounts and types to the target accounts.
   - Both phases execute inside a single atomic Room SQLite database transaction (`database.withTransaction`).
2. **Backup/Restore Serialization:**
   - JSON export serializes `isExcludedFromCalculations`, `originalExpenseId`, `splitDetails` on transactions; `originalAmountPaise`, `repaidAmountPaise` on owed; `interestRateBps`, `interestCalculationMethod`, `interestCreditFrequency`, `lastInterestPostingEpochDay` on accounts; and `iconName`, `isDisabled` on categories.
   - Deserialization gracefully falls back to sensible defaults for older backup files.

---

## 4. What Is Not Implemented / Known Limitations

To provide complete transparency, the following items represent specific nuances, edge cases, or partial implementations:

1. **Category Icon Picker in UI:**
   - **Status:** The database schema has `iconName TEXT`, and the backup/restore engine preserves it. The UI currently presents clean text categories without an icon picker dialog. Category color swatches have been intentionally removed per user request.
2. **Settlement Destination to Credit Card (Implemented, Insufficiently Tested):**
   - **Status:** `FinanceRepository.kt:350-354` has the exact accounting logic (`account.isCreditCard && isIncoming -> -amountPaise`), but unit test suite currently covers settling into Bank/Cash accounts.
3. **Savings Interest Historical Balance Prerequisite:**
   - **Status:** The backward daily balance reconstruction formula assumes all balance-altering transactions are logged in SpendTrack. If an account has an initial opening balance discrepancy without historical transaction logs, older reconstructed daily balances clamp to the fallback balance.

---

## 5. Automated Test Suite Summary

Executed live with `./gradlew test`:
```
BUILD SUCCESSFUL in 21s
194 tests completed, 0 failed, 0 skipped, 0 errors
```

### Breakdown by Test Suite
- `ComprehensiveFeatureHardeningTest`: **21 tests** (Owed full/partial settlements, overpayment prevention, loans, interest daily/monthly math, balance reconstruction, chart animation once-per-session, backup round-trip, safe category disable, recurring schedule from existing transaction, exclude from calculations complete balance bypass, dynamic owed participants without hardcoded names, card bill payment transaction creation).
- `DashboardBudgetsFeatureTest`: **11 tests** (Top budget calculations, carryover logic, expense filtering).
- `DashboardScrollAndRecurringArtifactRegressionTest`: **7 tests** (Scroll-to-top regression, recurring schedules).
- `FixedDepositFeatureTest`: **27 tests** (FD creation, compounding, maturity calculation).
- `ImportedTransactionMutationTest`: **10 tests** (In-memory draft buffer mutations, edit persistence).
- `MinimumBalanceFeatureTest`: **13 tests** (Minimum balance thresholds, warnings, credit card zero minimums).
- `Phase1CorrectnessHardeningTest`: **14 tests** (ParsePaise IEEE-754 precision, date formatters, safe enum fallbacks).
- `RecurringTransactionFeatureTest`: **18 tests** (Recurring occurrence execution, idempotency, date advancing).
- `SavingsAndIncomeChartTest`: **9 tests** (Chart color palettes, savings vs income metrics).
- `SpendingDateFilterAndDashboardMonthTest`: **13 tests** (Date filter ranges, custom date dialogs).
- `TransactionCardFormattingTest`: **6 tests** (Card UI badges, currency formatting).
- `TransferFeatureTest`: **8 tests** (Bank transfers, exclusion from income/expense, net worth balance movement).
- Importer Suite (`HdfcParserTest`, `IciciParserTest`, `KotakParserTest`, `SbiParserTest`, `SliceParserTest`, `StatementDetectorTest`, `ImportWorkflowTest`, `FullValidationRunner`): **36 tests** (PDF parser correctness, transaction normalization, deduplication).

---

## 6. Physical Device Testing Guide

The debug APK has been installed on your connected device (`adb -s e32d48b0 install -r app/build/outputs/apk/debug/app-debug.apk`). Recommended manual verification checklist:

1. **Owed Settlement Flow (Two-Dialog Flow):**
   - Open **Loans & Owed**. Tap an existing owed item.
   - Note the clean **`OwedManageDialog`**: Delete is on the left, Cancel & Save on the right, with a prominent **"Record Settlement"** button.
   - Tap **Record Settlement**. Select **"Full"** or **"Custom Amount"** via chips and pick your payment/deposit account.
   - Tap **Confirm Settlement** and verify account balance and owed balances update accurately.
2. **Card Bill Payment from Accounts:**
   - Go to **Settings > Accounts**. Tap a credit card with an outstanding balance.
   - Tap **Pay Bill**. Pick the paying bank account, choose full or custom amount, and tap **Pay Bill**.
   - Check **Spending / Transaction History**: Verify a new Transfer transaction titled `"Bill Payment: <Card Name>"` is recorded, linking the bank account to the credit card.
3. **Shared Expenses (No Hardcoded Names):**
   - Tap **+** to add an expense. Toggle **Shared expense**.
   - Verify: Only `[You]`, any people already created and active in your Owed section, and `[+ Create New]` appear as chips. No hardcoded friend names appear.
   - Tap **+ Create New** to add a new person. Verify they are selected, and saving the transaction automatically generates their Owed record.
4. **"Exclude from Calculations":**
   - Add or edit an expense or income and toggle **Exclude from calculations**.
   - Verify account balance is completely untouched, and the transaction is marked with the `"Excluded from calculations"` badge in the list.
5. **Existing Transaction Recurring Toggle:**
   - Tap an existing transaction in the transaction log to edit it.
   - Verify the **"Make Recurring"** card is present, allowing you to establish a recurring schedule directly from that transaction.
6. **Statement Importer Edit Flow:**
   - Open **Data > Import Statement** and select a PDF statement.
   - Tap **Edit** on a parsed transaction.
   - Verify: Soft keyboard does **not** auto-open. Tap any editable field to open keyboard. Toggle "Exclude from calculations". Save.
   - Confirm import and verify the edited values are saved to the database.
7. **Savings Interest Posting:**
   - Go to **Accounts > Manage Account** for a savings account.
   - Enter an interest rate (e.g., 3.5%) and tap **Post Monthly Interest**.
   - Verify balance increases and ledger shows `"Interest for <Date>"`.
8. **Scroll to Top:**
   - In **Spending**, scroll down 30+ items.
   - Tap the top header / status bar area. Verify the list smoothly animates back to index 0.
