# SpendTrack — Features & Capabilities

A complete list of all features implemented in the SpendTrack app. Each feature is presented with its name, description, and a concrete example.

---

### 1. Fixed Deposit (FD) Tracking & Maturity Calculation
- **Description**: Track fixed deposits with principal, interest rate, tenure, and compounding frequency (Monthly, Quarterly, Half-yearly, Yearly) to calculate maturity amount, track maturity date, and include active deposits in net worth.
- **Example**: ₹1,500 @ 7.75% compounding quarterly, started on 15 July 2026, ends on 16 Feb 2028 (Maturity Amount: ₹1,691).

---

### 2. Daily Expense & Income Tracking
- **Description**: Record everyday expenses and income with amount, date, description, category, and payment account, maintaining balance accuracy down to the paise.
- **Example**: Expense of ₹450 on "Team Lunch" under "Food" category paid from "HDFC Savings Account" on 12 Oct 2026.

---

### 3. Account-to-Account Transfers
- **Description**: Move money between bank accounts or withdraw cash in hand without treating the transfer as an expense or income.
- **Example**: Transfer ₹5,000 from "SBI Savings Account" to "Cash in Hand" for daily cash expenses on 01 Nov 2026.

---

### 4. Multi-Account Management (Savings & Cash)
- **Description**: Manage multiple bank savings accounts and cash wallets with live running balances, bank names, masked account numbers, and minimum balance thresholds.
- **Example**: "ICICI Savings Account" with a current balance of ₹45,000 and a minimum balance requirement of ₹10,000.

---

### 5. Credit Card Management
- **Description**: Track credit cards with current outstanding amount, total credit limit, available credit limit, billing cycle statement day, and payment due day.
- **Example**: "Amazon Pay ICICI Credit Card" with ₹18,500 outstanding, ₹1,00,000 credit limit, statement cycle on 15th, and payment due on 5th of every month.

---

### 6. Credit Card Bill Payment
- **Description**: Pay credit card dues directly from a bank savings account, deducting the bank balance and clearing credit card dues without inflating monthly spending or expense budgets.
- **Example**: Pay ₹18,500 outstanding bill on "Amazon Pay ICICI Card" from "HDFC Savings Account".

---

### 7. Bank & Credit Card Statement PDF Importer
- **Description**: Import native PDF statements from supported Indian banks (HDFC, ICICI, SBI, Kotak) and credit cards (ICICI, Slice) with automatic statement format detection and transaction extraction.
- **Example**: Upload `HDFC_Statement_Sept2026.pdf` — the app auto-identifies it as HDFC Savings and extracts 35 transactions.

---

### 8. Merchant Name Normalization
- **Description**: Clean up cryptic bank, POS, and UPI transaction narrations into clean, human-readable merchant names during statement import.
- **Example**: Raw narration `"UPI-Debit-123456-SWIGGY-BANGALORE@icici"` is automatically normalized to `"Swiggy"`.

---

### 9. Automatic Transaction Categorization
- **Description**: Automatically assign categories to imported statement transactions based on recognized merchant names and keywords.
- **Example**: Transactions containing "Zomato" or "Swiggy" are automatically categorized under "Food", while "D-Mart" is categorized under "Shopping".

---

### 10. Duplicate Transaction Detection
- **Description**: Prevent duplicate records when importing statements by detecting exact and near-date matches against already recorded transactions.
- **Example**: If a ₹350 Swiggy payment on 04 Nov already exists in your account, the importer flags it as a duplicate and skips importing it again.

---

### 11. Inter-Account Transfer Matching in Imports
- **Description**: Automatically match paired debit and credit entries across imported statements and merge them into single inter-account transfers to avoid double-counting.
- **Example**: A ₹10,000 debit from HDFC and a corresponding ₹10,000 credit in SBI within 2 days are merged into a single transfer from HDFC to SBI.

---

### 12. Statement Import Review & Transaction Type Override
- **Description**: Inspect and edit parsed transactions before saving an import batch, allowing you to change amounts, categories, accounts, or switch transaction types (Expense, Income, Transfer).
- **Example**: Change a ₹2,000 incoming UPI credit from "Income" to "Transfer" before saving the statement to your database.

---

### 13. Statement Balance Reconciliation
- **Description**: Verify the mathematical consistency of statement line items against running balances to alert you if transactions are missing or corrupted in the PDF.
- **Example**: Verifies that opening balance (₹50,000) minus debits (₹12,000) plus credits (₹5,000) exactly matches the closing balance (₹43,000).

---

### 14. Shared Expenses & Equal Bill Splitting
- **Description**: Split a group bill equally among multiple participants, automatically calculating your personal share and creating owed balance records for each participant.
- **Example**: ₹3,000 restaurant dinner paid by You and shared equally with Rahul and Priya (₹1,000 each) — your expense is recorded as ₹1,000, and Rahul and Priya each get an Owed record of ₹1,000.

---

### 15. Shared Expenses (Custom Amount Split)
- **Description**: Split an expense with unequal custom amounts per person with remainder validation to ensure the split amounts exactly total the transaction.
- **Example**: ₹1,200 grocery bill split as You: ₹600, Amit: ₹400, Sneha: ₹200.

---

### 16. Money Owed Tracking (Personal IOUs)
- **Description**: Track money lent to others ("Owed to Me") and money borrowed from others ("I Owe"), with notes and links to original transactions.
- **Example**: Rahul owes you ₹2,500 for concert tickets, and you owe Vikram ₹800 for fuel.

---

### 17. Debt Settlement Flow (Full or Partial)
- **Description**: Record repayments for money lent or borrowed via a dedicated settlement dialog, updating both the remaining debt and the chosen bank account balance.
- **Example**: Rahul repays ₹1,500 of his ₹2,500 debt — ₹1,500 is credited to your HDFC Savings account and Rahul's remaining balance updates to ₹1,000.

---

### 18. Dynamic People Management
- **Description**: Maintain a directory of people for shared expenses and debts without hardcoding names, enabling adding, searching, and managing contacts on the fly.
- **Example**: Add "Ananya" as a new person directly from the split bill sheet to assign her a share.

---

### 19. Structured Loans Tracking
- **Description**: Track formal loans (Home, Car, Education, Personal) with principal amount, interest rate, EMI, remaining tenure, next due date, and net worth inclusion toggle.
- **Example**: Car Loan of ₹5,00,000 @ 8.75% p.a., EMI of ₹15,200 due on the 10th of every month, with 28 months remaining.

---

### 20. Loan EMI Repayment Tracking
- **Description**: Record monthly EMI payments debited from your savings account, automatically updating the remaining loan balance, principal paid, and interest paid.
- **Example**: Pay ₹15,200 EMI from SBI Account — ₹11,500 reduces loan principal, ₹3,700 is recorded as interest paid, and SBI account balance is debited by ₹15,200.

---

### 21. Savings Account Interest Calculator
- **Description**: Calculate quarterly bank savings interest using RBI-compliant daily product balance reconstruction, monthly average balance, or quarterly average balance methods.
- **Example**: ICICI Savings Account @ 3.50% interest p.a. calculated across reconstructed daily balances from 1 July to 30 Sept, computing ₹632 interest.

---

### 22. One-Tap Savings Interest Ledger Posting
- **Description**: Post computed savings account interest directly to your account balance and transaction history with built-in duplicate credit protection.
- **Example**: Tap "Post Interest" to credit ₹632 to your ICICI Bank account and automatically log an Income transaction under "Interest".

---

### 23. Recurring Transactions & Automated Scheduling
- **Description**: Automate recurring bills, subscriptions, salary, and transfers across daily, weekly, monthly, or yearly intervals with automatic transaction posting.
- **Example**: Monthly Netflix subscription of ₹649 debited from Credit Card on the 18th of every month.

---

### 24. Category-Wise Monthly Budgets
- **Description**: Define monthly spending limits for individual expense categories with visual progress indicators clamped at 100% and over-budget warnings.
- **Example**: ₹12,000 monthly budget for "Dining Out" — shows 75% progress when ₹9,000 is spent, and highlights in red if spending exceeds ₹12,000.

---

### 25. Monthly Budget Rollover
- **Description**: Automatically roll over existing budget limits into each new calendar month while resetting spending usage to zero.
- **Example**: The ₹15,000 budget for "Groceries" carries forward from October to November with ₹0 spent at the start of the month.

---

### 26. Real-Time Net Worth Calculation
- **Description**: Aggregate all financial assets and liabilities into a single net worth figure: (Savings + Active FDs + Money Owed to Me) minus (Credit Card Dues + Loans + Money I Owe).
- **Example**: Net Worth of ₹6,75,000 calculated from ₹2,50,000 savings + ₹3,00,000 FDs + ₹25,000 owed to you - ₹20,000 card dues - ₹80,000 loan.

---

### 27. Financial Dashboard & Quick Metrics
- **Description**: Central overview screen displaying Net Worth, monthly spending vs. income, total savings, card dues, top budget cards, and recent transactions.
- **Example**: Dashboard displaying "Net Worth: ₹6,75,000", "Spent this month: ₹34,200", and "Income this month: ₹85,000".

---

### 28. Expenses in the Last 7 Days (Canvas Bar Chart)
- **Description**: Custom Canvas-rendered bar chart displaying daily expense totals across the trailing 7 days with amounts, weekday identifiers, and date range.
- **Example**: Bar chart showing ₹250 (Mon), ₹1,400 (Tue), ₹0 (Wed), ₹650 (Thu), ₹3,200 (Fri), ₹800 (Sat), ₹1,100 (Sun).

---

### 29. Monthly Spending by Category (Canvas Donut Chart)
- **Description**: Custom Canvas-rendered donut chart showing proportional category expenses for the month, centered total spend, and clear color legend.
- **Example**: Donut chart showing ₹35,000 total spend divided into Rent (45%), Food (25%), Transport (15%), and Shopping (15%).

---

### 30. Transaction Search & Keyword Querying
- **Description**: Search transaction history instantly by keyword, merchant name, or note.
- **Example**: Typing "Uber" instantly lists all cab ride transactions across all accounts and dates.

---

### 31. Multi-Criteria Transaction Filters
- **Description**: Filter transactions by type (All, Expense, Income, Transfer), category, account, and amount range (minimum and maximum paise).
- **Example**: Filter for "Expense" transactions from "Slice Credit Card" between ₹500 and ₹2,000.

---

### 32. Flexible Date Filtering & Month Selector
- **Description**: Quickly filter transactions by predefined periods (Today, Yesterday, This Week, This Month, Last Month), custom date range picker, or dedicated month picker.
- **Example**: Select custom range "15 Aug 2026 to 31 Aug 2026" or select "September 2026" to view only that period's activity.

---

### 33. Transaction Report Exclusion (`isExcludedFromCalculations`)
- **Description**: Mark specific transactions as excluded from spending/income summaries and charts (useful for reimbursed work expenses or pass-through payments).
- **Example**: A ₹12,000 flight ticket booked for office work marked "Exclude from calculations" so it does not skew personal monthly expenditure.

---

### 34. Bill & Loan Due Reminders (Local Notifications)
- **Description**: Offline local notifications scheduled via Android WorkManager alerting you of upcoming credit card due dates and loan EMIs with customizable lead times.
- **Example**: Notification delivered 5 days prior: "Reminder: Slice Credit Card bill of ₹6,500 is due on 12 Nov".

---

### 35. Custom Lead Times for Reminders
- **Description**: Configure multiple alert lead times (e.g., 10 days and 5 days prior) separately for credit cards and loans, with automatic suppression if already paid.
- **Example**: Set credit card reminder lead times to 7 days and 2 days before the due date.

---

### 36. Category Customization & Management
- **Description**: Add custom income and expense categories with personalized colors and icons, edit category names, or disable unused categories safely without breaking transaction history.
- **Example**: Create a new expense category "Pet Care" with a custom green color; deactivating it later hides it from new transactions while preserving past transaction history.

---

### 37. Biometric App Lock & Privacy
- **Description**: Lock the app with Android BiometricPrompt (fingerprint or face unlock) whenever the app is closed or backgrounded to keep personal financial records confidential.
- **Example**: Fingerprint prompt appears whenever launching SpendTrack or switching back from another app.

---

### 38. Theme & Visual Customization (Light, Dark & True Black)
- **Description**: Choose between Light mode, Dark mode, System default, and an OLED True Black theme for maximum contrast and battery efficiency.
- **Example**: Select "True Black" in Settings > Appearance for a pure `#000000` AMOLED background.

---

### 39. Offline JSON Backup & Restore
- **Description**: Export and import complete application data as a versioned JSON file via Android's native Storage Access Framework, operating completely offline with zero cloud dependency.
- **Example**: Export backup file `spendtrack-backup.json` to local device storage and restore it on a new device to recover all accounts, transactions, FDs, and budgets.

---

### 40. Tap-to-Scroll Header Navigation
- **Description**: Tap the top app bar on the Dashboard or Spending screens to instantly smooth-scroll back to the top of long lists.
- **Example**: Tapping the "Spending" top header when scrolled 150 items down smoothly returns to the newest transaction.

---

### 41. Expressive Haptic Feedback & Motion
- **Description**: Subtle tactile vibration and Material 3 Expressive motion animations when interacting with buttons, chips, dialogs, and navigation elements.
- **Example**: Gentle vibration click when confirming a transaction or toggling recurrence.

---

### 42. Minor-Unit Paise Precision
- **Description**: Store all monetary values as integer paise (`Long`) internally to eliminate floating-point rounding errors and ensure 100% accounting accuracy.
- **Example**: ₹150.75 is stored internally as `15075L` paise, preventing rounding discrepancies.
