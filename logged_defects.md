# SpendTrack UI Defect Log

Device audit on connected Android device (2026-10-31). This file records observed UI defects and the app flows inspected. No screenshots are embedded; the device captures include personal financial data and are kept out of the repository.

## Confirmed defects

| Defect | Description | Screen / evidence | Proposed change | Calculation impact |
|---|---|---|---|---|
| Floating controls obscure content | The floating tab pill and add button cover chart legend and transaction cards while scrolled near the top. | Home, Spending; device captures from 2026-10-31. | Adjust list insets/control positioning so visible content is not obscured. | None; layout only. **Open** |
| Charts used fixed colors and low distinction | Spending’s income and expense arcs were too similar; dashboard category slices used hard-coded colors unrelated to the device palette. | Home and Spending charts; before/after device captures from 2026-10-31. | Use dynamic Material 3 roles across all charts, with distinct income/expense roles. | None; presentation only. **Fixed** in `FinanceCharts.kt`; verified on the connected device. |
| Recurring screen title wraps awkwardly | “Recurring Transactions” wraps to two lines and crowds the back arrow at the inspected device width. | Recurring Transactions; device capture from 2026-10-31. | Rebalance heading/back layout for narrow screens and larger type. | None; layout only. |
| Long account names compete with balances | Long names can consume the same row/card space as balances and edit affordance, reducing scanability. | Accounts; device capture from 2026-10-31. | Define readable wrapping/truncation while retaining the full accessible name. | None; layout only. |
| Management lists are vertically sparse | Large headings and padded cards limit the number of account/category rows visible per screen. | Accounts, Categories; device captures from 2026-10-31. | Review shared heading/card spacing while preserving touch targets. | None; layout only. |
| Import review status wraps vertically | Long row controls compress the “New” / “Accepted” status to one character per line, and the Removed tab is partly clipped. | Slice statement review (183 parsed transactions) on the connected device. | Rebalance transaction metadata row and make all review filters reachable at compact width. | None; draft presentation only. **Open** |
| Fixed-deposit maturity amount crowds its label | “Maturity Amount” wraps across several lines in the deposit card, crowding the amount on this device width. | Fixed Deposits; device capture from 2026-10-31. | Give the label and amount a layout that remains readable at compact widths. | None; presentation only. **Open** |
| Pay Bill closes Accounts after payment | Completing a card payment returns to Settings instead of the Accounts list from which Pay Bill was opened. | Device audit: full and partial payments on `AUDITCARDPAY`; the screen returned to Settings after each payment. | Preserve the Accounts route after payment so users can continue managing the card. | None; navigation only. **Open** |
| Deleting a loan repayment leaves the account debited | Loan repayment deducts money from the account and creates an excluded transaction. Deleting that transaction skips the account reversal because it is excluded, leaving the account lower after the repayment record is removed. | `AUDITLOAN`: ₹1 repayment changed ₹100 outstanding to ₹99; loan and generated repayment transaction were then deleted. Repository flow confirms excluded-transaction deletion skips reversal. | Keep repayment/account effects linked through deletion, or prevent deleting the generated transaction independently. | **Yes**; account balance is left understated. **Open** |

## Flows exercised

- Opened Home, Spending, Loans & Owed, Settings, Accounts, Categories, Fixed Deposits, and Recurring Transactions.
- Opened the add-transaction form from the global add button. Switched Expense → Income → Transfer and observed conditional controls (category selector for income/expense, source and destination selectors for transfer, and shared-expense only for expense).
- Saved a clearly labeled ₹1 expense and a clearly labeled ₹1 income, verified each appeared in Spending, then deleted each exact temporary record. Dashboard totals returned to their initial values (net worth ₹56,965.43, this-month spending ₹4,984.00, income ₹40,383.33, savings ₹71,960.43).
- Saved a ₹1 transfer from HDFC Bank - 9642 to ICICI Bank - 6648, verified the row and the ₹1 balance movement, then deleted that exact transfer. Both account balances returned to their initial values (HDFC ₹7,670.00; ICICI ₹0.00).
- Added a zero-balance `AUDITCARD` with test-only digits `0000`, ₹11 limit, statement day 1 and due day 10, verified it appeared, then deleted it.
- Added a zero-balance `AUDITACCOUNT`, verified it appeared, then deleted it.
- Account/card creation form exposes name, opening/current balance, min balance, last four digits, credit limit and statement/due days. Data-entry taps required keyboard dismissal and fresh screen bounds; initial coordinates were misaligned, so no values were kept beyond the verified temporary records.
- Search query was initially appended to stale text; after clearing the full query, `AUDITTRANSFER` was located and cleaned up as above. Search is blank again.
- Added and deleted a ₹1 `AUDITREC` weekly expense schedule. Verified create, pause, and delete; the schedule's generated expense remained after deleting the schedule, as the app warned. Deleted that exact transaction and verified HDFC returned to ₹7,670.00 and ICICI remained ₹0.00.
- Added a ₹10 shared expense with a ₹5.00 + ₹5.00 custom split, verified the personal share impact and “Shared with Yash M.” label, then deleted the exact transaction and verified dashboard totals returned to baseline.
- Added a ₹1 October budget for `Other`, verified its displayed limit/spending, then deleted it; the budget list returned to “No Budgets.”
- Added and deleted a test fixed deposit (`AUDITFD`, ₹1 principal, 1%, quarterly, marked already existing). Verified the active-deposit count returned to zero. The fixed-deposit card also showed the maturity-label wrapping defect above.
- Created `AUDITLOAN` with ₹100 principal/outstanding, repaid ₹1 from HDFC, and observed outstanding change to ₹99 / paid to ₹1. Deleted the loan and its generated transaction; this exposed the account-reversal issue above.
- Created `AUDITOWED` (₹1 owed to me) and settled it in full; created `AUDITDEBT` (₹1 owed by me), settled a custom ₹0.50, verified ₹0.50 remained, then settled the rest in full. Both records displayed as ₹0.00 afterward with the same direction labels.
- Opened the recurring transaction form and inspected Daily/Weekly/Monthly/Yearly, end-date, pause and delete controls. A temporary weekly schedule was created, paused, and deleted as noted above.
- Opened reminder settings and the card-reminder configuration dialog; inspected the default 10/5 day values and dismissed without saving.
- Selected the downloaded Slice savings PDF. It parsed 183 transactions and reported reconciliation; mapped statement account 2503 to the existing Slice Atom repayment account. The review classified 170 as new; imported all 170 and verified “Import Successful!” with 13 transfers created.
- Created `spendtrack-backup.json` from Data settings (67,036 bytes), selected it through Restore Backup, confirmed the replace-local-data dialog, and verified “Backup restored.” This restored the pre-import checkpoint and removed the imported statement rows.
- Added `AUDITCARDPAY` with ₹1 outstanding. Full payment cleared it to ₹0. Set outstanding back to ₹1, paid custom ₹0.50 and verified ₹0.50 remained, then paid the remainder in full and verified ₹0.00. Payment records appeared in Spending; completion returned to Settings rather than Accounts.
- Edited the test card-payment transfer description and toggled Exclude from Calculations on and off; the edited description and exclusion state appeared on the transaction row.
- The device palette chart update was built, installed, and visually checked in Home and Spending. Home category slices use Material 3 dynamic roles; Spending income and expense use distinct theme roles; transaction bars use theme primary/container roles. No calculation code changed.
- The user authorized resetting app data after the audit. Final cleanup is recorded below.

## Design changes applied

Applied after review of the recommendations above. These changes affect copy and presentation only; no financial calculations or stored data were changed.

- Changed account/card, backup/restore, chart, loan, owed-entry, fixed-deposit, and recurring-transaction labels to sentence case.
- Reduced heading scale on management screens and tightened reusable management-row padding while retaining the edit button's touch target.
- Replaced the large empty loans card with a compact “No loans tracked yet” state that points to the + action.
- Kept settled owed records visible as history, but labeled zero-balance entries “Settled.”
- Restored the compact icon-pill navigation after the user found the labeled bar visually worse.

## Follow-up fixes

- Kept statement import under Data and removed the duplicate Settings entry.
- Added Light, Dark, and System appearance choices plus the optional True black dark palette.
- Added Daily, Monthly, and Quarterly interest crediting, accrued from daily closing balances.
- Aligned recurrence choices in a horizontally selectable row, aligned the Categories title with its back button, and corrected the settlement amount field layout.
- Reduced the recurring schedule editor heading and removed the explanatory copy under the statement import exclusion switch.

## Audit cleanup

- The Slice import was removed by a successful restore of the pre-import checkpoint.
- The user authorized resetting app data after the audit. The app database and preferences were cleared, and the generated `spendtrack-backup.json` file was deleted from Downloads.
- A final attempt to configure interest on the seeded Cash account was canceled before saving; savings-interest posting was not verified. The fixed-deposit maturity action and actual reminder delivery were also not exercised. These need a follow-up device pass before claiming every app action is covered.
- Screenshots remain in `/tmp` only because they contain personal financial information; none are embedded in this repository.

## Screenshots

Not embedded. Captures include the user's financial data; screenshots can be added later if requested.
