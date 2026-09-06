# SpendTrack

Native Android personal finance and expense tracking application built with Kotlin, Jetpack Compose, and Material 3 Expressive.

SpendTrack is an offline-first personal finance app focused on giving users a clear view of everyday spending, income, accounts, credit cards, budgets, loans, debts, reminders, and financial trends while keeping financial data on-device.

## Overview

SpendTrack is designed for personal finance tracking without requiring a cloud account or network connection. The application stores financial data locally and provides a unified view of:

- Income and expenses
- Savings accounts and credit cards
- Monthly budgets
- Loans and personal debts
- Spending trends and category breakdowns
- Credit card and loan reminders
- Local JSON backup and restore

### Main User Workflow

1. **Daily Tracking** — Record expenses or income with descriptions, amounts, categories, dates, and accounts.
2. **Financial Overview** — Use the dashboard to review net worth, monthly spending, income, savings, card dues, recent transactions, and recent expense trends.
3. **Spending Analysis** — Review spending by month and category using the **Expenses in the Last 7 Days** bar chart and **Monthly Spending by Category** donut chart.
4. **Budgeting** — Define monthly category spending limits and monitor usage through visual budget progress indicators.
5. **Debt and Bill Management** — Track loans, personal debts, credit card dues, repayments, and configurable reminder lead times.
6. **Data Ownership** — Export and restore application data as versioned JSON backups through Android's native file picker.

## Features

### Expense and Income Tracking

- Record expense transactions with date, description, amount, category, and payment account.
- Record income transactions using dedicated income transaction types.
- Support shared expenses and personal-share tracking.
- Automatically maintain related owed records for shared expenses.
- Search and filter transactions.
- Edit and delete transactions.
- Apply atomic balance corrections when transactions are changed or removed.
- Store monetary values in integer paise to avoid floating-point rounding issues.

### Accounts and Credit Cards

- Manage savings accounts and other supported accounts.
- Edit account names and current balances.
- Manage credit cards with current outstanding amount, credit limit, statement cycle day, and payment due day.
- Edit credit card names and current outstanding amounts.
- Record credit card bill payments from savings accounts without treating the transfer as new spending or income.
- Protect accounts with associated transactions from unsafe deletion.

### Categories

Categories are managed separately by transaction type.

#### Income Categories

- Salary
- Freelance Income
- Interest
- Refunds
- Other income
- Custom income categories

#### Expense Categories

- Food
- Transport
- Shopping
- Bills
- Other
- Custom expense categories

System fallback categories and categories associated with existing transactions or budgets are protected from deletion where required by the application.

### Budgets

- Create monthly spending limits for expense categories.
- Monitor actual spending against the configured budget.
- Display a visual progress indicator for each budget.
- Progress is calculated from actual expense data.
- Visual progress is clamped at 100% when spending exceeds the limit while the actual spent amount remains visible.
- Existing budget limits can carry forward into new months while usage starts from zero.
- User-facing month values are displayed as full month names and years, for example **September 2026**, while internal month keys remain in `YYYY-MM` form.

### Loans and Money Owed

- Track structured loans.
- Store principal, outstanding amount, interest rate, EMI, due date, remaining tenure, and principal/interest paid amounts.
- Optionally include or exclude loans from net worth calculations.
- Record loan repayments.
- Track money owed to or by other people.
- Record partial or complete debt settlements.
- Maintain links between shared transactions and related owed records where applicable.

### Dashboard and Financial Metrics

- Net worth overview.
- Monthly spending and income.
- Total savings.
- Credit card dues.
- Recent transactions.
- Loan and personal-debt summaries.
- Financial dashboard cards and quick actions.

Net worth is calculated from the application's financial entities, including savings, amounts owed to the user, card dues, included loans, and amounts the user owes.

### Spending Charts

SpendTrack uses custom Jetpack Compose Canvas rendering rather than an external charting library.

#### Expenses in the Last 7 Days

- Displays daily expense totals across the trailing seven days.
- Includes expense transactions only.
- Excludes income transactions.
- Displays the amount associated with each bar.
- Displays weekday identifiers.
- Displays the selected seven-day date range centered beneath the chart.

#### Monthly Spending by Category

- Displays monthly expense distribution as a donut chart.
- Uses expense transactions only.
- Shows a central spending total.
- Shows category names and amounts in the legend.
- Keeps the category legend free of percentage text and leader lines.
- Maintains a circular, centered donut layout with clear spacing from the title and legend.

### Alerts and Reminders

- Local reminders for credit card bills and loan payments.
- Background scheduling through Android WorkManager.
- Configurable reminder lead times for cards and loans.
- Supports multiple reminder points, such as the default 10-day and 5-day lead times.
- Uses Android notification channels and runtime notification permission handling.

### Data Management

- Export application data to versioned JSON.
- Restore exported data through Android's Storage Access Framework.
- Preserve relationships between accounts, categories, transactions, budgets, loans, owed records, and card payments.
- Restore data within an atomic database transaction.

## Technology Stack

| Component | Technology | Version / Details |
|---|---|---|
| Language | Kotlin | 2.4.10 |
| Build System | Android Gradle Plugin / Gradle Wrapper | AGP 9.4.0 / Gradle 9.6.0 |
| Annotation Processing | KSP | 2.3.11 |
| Java | JDK | 17 |
| Android SDK | Min / Target / Compile | 26 / 37 / 37 |
| UI | Jetpack Compose | Compose BOM 2026.08.00 |
| Design System | Material 3 / Material 3 Expressive APIs | Material 3 1.5.0-alpha27 |
| Navigation | Navigation Compose | 2.10.0 |
| Persistence | Room | 2.8.4 |
| Background Work | WorkManager | 2.11.0 |
| Concurrency | Kotlin Coroutines / Flow | StateFlow, SharedFlow, viewModelScope |
| Testing | JUnit / Compose UI testing | JUnit 4.13.2 and Compose testing dependencies |
| Charts | Jetpack Compose Canvas | Custom in-house chart rendering |

## Architecture

SpendTrack is a native Android application using Jetpack Compose for the UI, Room for local persistence, ViewModel/state flows for UI state, repository abstractions for data operations, and WorkManager for scheduled reminders.

### Architecture Overview

```text
┌─────────────────────────────────────────────────────────────┐
│                       UI Layer (Compose)                    │
│  SpendTrackApp • Dashboard • Spending • Loans/Owed • etc.   │
└──────────────────────────────▲──────────────────────────────┘
                               │ StateFlow / events
┌──────────────────────────────┴──────────────────────────────┐
│                  ViewModel Layer (Coroutines)               │
│                       FinanceViewModel                      │
└──────────────────────────────▲──────────────────────────────┘
                               │ Flow / suspend functions
┌──────────────────────────────┴──────────────────────────────┐
│                       Repository Layer                      │
│               FinanceRepository • BackupRepository          │
└──────────────────────────────▲──────────────────────────────┘
                               │ Room operations
┌──────────────────────────────┴──────────────────────────────┐
│                    Persistence Layer (SQLite)               │
│            SpendTrackDatabase • Entities • DAOs             │
└─────────────────────────────────────────────────────────────┘
```

### Key Architectural Characteristics

- **Local single source of truth:** financial data is stored locally through Room.
- **Reactive UI:** Room flows and ViewModel state are consumed by Compose screens.
- **Atomic accounting updates:** account and card balance changes are performed through Room database transactions where required.
- **Minor-unit currency handling:** monetary values are stored as `Long` paise.
- **Custom chart rendering:** charts are drawn with Compose Canvas and standard Android text rendering.
- **Local reminders:** WorkManager schedules reminder processing without requiring a remote service.
- **Native file-based backup:** Android's Storage Access Framework is used for user-controlled backup and restore.

## Project Structure

```text
SpendTrackNative/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/spendtrack/app/
│           │   ├── MainActivity.kt
│           │   ├── data/
│           │   │   ├── local/
│           │   │   │   ├── Daos.kt
│           │   │   │   ├── DatabaseProvider.kt
│           │   │   │   ├── Entities.kt
│           │   │   │   ├── SpendTrackConverters.kt
│           │   │   │   └── SpendTrackDatabase.kt
│           │   │   └── repository/
│           │   │       ├── BackupRepository.kt
│           │   │       └── FinanceRepository.kt
│           │   ├── reminders/
│           │   │   ├── ReminderPreferences.kt
│           │   │   └── ReminderWorker.kt
│           │   └── ui/
│           │       ├── FinanceCharts.kt
│           │       ├── FinanceViewModel.kt
│           │       ├── SpendTrackApp.kt
│           │       ├── theme/
│           │       │   └── Theme.kt
│           │       └── transactions/
│           │           └── AddTransactionScreen.kt
│           └── res/
│               ├── drawable/
│               ├── mipmap-anydpi-v26/
│               ├── mipmap-*/
│               └── values/
├── build.gradle.kts
├── gradle/
├── gradlew
├── gradlew.bat
├── README.md
└── settings.gradle.kts
```

## Data Model

| Entity | Main purpose |
|---|---|
| `AccountEntity` | Represents savings accounts and credit cards, including running balances and credit-card-specific fields. |
| `CategoryEntity` | Represents income and expense categories and their classification. |
| `TransactionEntity` | Represents expense and income transactions, including category, account, date, amount, and shared-expense data. |
| `BudgetEntity` | Represents monthly spending limits associated with expense categories. |
| `LoanEntity` | Represents structured loans, balances, repayment information, and due dates. |
| `OwedEntity` | Represents personal receivables and payables. |
| `CardPaymentEntity` | Represents credit-card bill payments and their funding accounts. |

## UI / UX

- Material 3 / Material 3 Expressive visual language.
- System light/dark theme support.
- Edge-to-edge application layout.
- Bottom floating navigation and prominent FAB for primary actions.
- Rounded containers and expressive typography.
- Responsive Compose layouts with navigation-bar insets.
- Adaptive launcher icon with a centered black ₹ symbol on a white background.

## Build and Run

### Prerequisites

- Android Studio (Ladybug or newer recommended)
- JDK 17 configured as the Gradle JVM
- Android SDK platform 37
- Android device or emulator running API 26 or higher

### Build Debug APK

```bash
./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install on a Connected Device

```bash
adb devices
./gradlew installDebug
```

## Testing

The project includes dependencies for:

- JUnit 4.13.2
- Jetpack Compose UI testing

Run the configured unit tests with:

```bash
./gradlew testDebugUnitTest
```

Build verification can be performed with:

```bash
./gradlew assembleDebug
```

No committed automated unit-test or instrumentation-test suite is currently documented beyond the configured testing dependencies.

## Design Decisions and Implementation Notes

1. **Paise-only monetary representation** — Financial amounts are stored as integer paise (`Long`) and converted to display values only at the UI boundary.
2. **Credit-card payments are separate from spending** — Paying a credit-card bill transfers funds between accounts and reduces the card outstanding amount without recording the payment itself as a new expense.
3. **Shared expenses create linked debt information** — Shared expense data can generate an associated amount owed to the user.
4. **Budget rollover resets usage** — Budget limits may carry forward into a new month, while spending usage begins from zero for that month.
5. **Native custom charts** — The application's financial charts use Compose Canvas rather than an external charting library.
6. **User-controlled backups** — Backup and restore use Android's Storage Access Framework instead of requiring a cloud account.

## Planned Improvements

The following items are future work and are not part of the current implemented feature set:

- [ ] Expand automated unit-test coverage for accounting and balance-update rules.
- [ ] Add Compose UI instrumentation tests.
- [ ] Biometric app lock.
- [ ] Recurring transaction automation.
- [ ] Multi-currency support and locale-aware currency formatting.

## Repository Status

**Status:** Active development.

The current repository contains the core offline personal-finance workflow, including transaction tracking, account and credit-card management, budgets, loans and personal debts, reminders, custom financial charts, and JSON backup/restore.

## License

No formal open-source license is currently declared in the repository.

## Contribution and Development Guidelines

- Follow Kotlin and Jetpack Compose best practices.
- Keep monetary values in integer paise rather than floating-point database fields.
- Version and validate Room schema changes.
- Preserve atomic balance-update behavior when changing accounting logic.
- Avoid introducing network dependencies for functionality that is intended to remain local/offline.
- Verify that the project builds before submitting changes:

```bash
./gradlew assembleDebug
```