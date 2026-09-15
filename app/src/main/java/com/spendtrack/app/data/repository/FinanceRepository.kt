package com.spendtrack.app.data.repository

import androidx.room.withTransaction
import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.FixedDepositEntity
import com.spendtrack.app.data.local.FixedDepositStatus
import com.spendtrack.app.data.local.CompoundingFrequency
import com.spendtrack.app.data.local.RecurringTransactionEntity
import com.spendtrack.app.data.local.RecurringOccurrenceEntity
import com.spendtrack.app.data.local.RecurringTransactionScheduler
import com.spendtrack.app.data.local.SpendTrackDatabase
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Local-only accounting gateway. Balance-changing writes run atomically. */
class FinanceRepository(private val database: SpendTrackDatabase) {
    val accounts: Flow<List<AccountEntity>> = database.accounts().observeAll()
    val categories: Flow<List<CategoryEntity>> = database.categories().observeAll()
    val transactions: Flow<List<TransactionEntity>> = database.transactions().observeAll()
    val loans = database.loans().observeAll()
    val owed = database.owed().observeAll()
    val budgets = database.budgets().observeAll()
    val cardPayments = database.cardPayments().observeAll()
    val fixedDeposits = database.fixedDeposits().observeAll()
    val recurringTransactions: Flow<List<RecurringTransactionEntity>> = database.recurringTransactions().observeAll()

    suspend fun ensureStarterData() = database.withTransaction {
        if (database.categories().count() > 0) return@withTransaction
        listOf("Food", "Transport", "Shopping", "Bills", "Other").forEach {
            database.categories().insert(CategoryEntity(name = it, isSystemFallback = it == "Other"))
        }
        listOf("Salary", "Freelance Income", "Interest", "Refunds", "Other income").forEach {
            database.categories().insert(CategoryEntity(name = it, isIncomeCategory = true))
        }
        database.accounts().insert(AccountEntity(name = "Cash", bank = "Wallet"))
    }

    suspend fun addTransaction(transaction: TransactionEntity): Long = database.withTransaction {
        val account = database.accounts().getById(transaction.accountId) ?: error("Choose a valid account")
        if (transaction.type == TransactionType.TRANSFER) {
            val destAccount = database.accounts().getById(transaction.destinationAccountId!!) ?: error("Choose a valid destination account")
            val sourceDelta = if (account.isCreditCard) transaction.amountPaise else -transaction.amountPaise
            val destDelta = if (destAccount.isCreditCard) -transaction.amountPaise else transaction.amountPaise
            val id = database.transactions().insert(transaction)
            database.accounts().update(account.copy(balancePaise = account.balancePaise + sourceDelta))
            database.accounts().update(destAccount.copy(balancePaise = destAccount.balancePaise + destDelta))
            id
        } else {
            val delta = when {
                account.isCreditCard && transaction.type == TransactionType.EXPENSE -> transaction.amountPaise
                account.isCreditCard -> -transaction.amountPaise
                transaction.type == TransactionType.EXPENSE -> -transaction.amountPaise
                else -> transaction.amountPaise
            }
            val id = database.transactions().insert(transaction)
            database.accounts().update(account.copy(balancePaise = account.balancePaise + delta))
            recordSharedRecovery(transaction.copy(id = id))
            id
        }
    }

    suspend fun deleteTransaction(id: Long) = database.withTransaction {
        val transaction = database.transactions().getById(id) ?: return@withTransaction
        val account = database.accounts().getById(transaction.accountId) ?: return@withTransaction
        if (transaction.type == TransactionType.TRANSFER) {
            val destAccount = database.accounts().getById(transaction.destinationAccountId!!) ?: return@withTransaction
            val sourceDelta = if (account.isCreditCard) transaction.amountPaise else -transaction.amountPaise
            val destDelta = if (destAccount.isCreditCard) -transaction.amountPaise else transaction.amountPaise
            database.transactions().delete(transaction)
            database.accounts().update(account.copy(balancePaise = account.balancePaise - sourceDelta))
            database.accounts().update(destAccount.copy(balancePaise = destAccount.balancePaise - destDelta))
        } else {
            val delta = when {
                account.isCreditCard && transaction.type == TransactionType.EXPENSE -> transaction.amountPaise
                account.isCreditCard -> -transaction.amountPaise
                transaction.type == TransactionType.EXPENSE -> -transaction.amountPaise
                else -> transaction.amountPaise
            }
            database.transactions().delete(transaction)
            database.owed().deleteForTransaction(transaction.id)
            database.accounts().update(account.copy(balancePaise = account.balancePaise - delta))
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity) = database.withTransaction {
        val previous = database.transactions().getById(transaction.id) ?: error("Transaction not found")

        // Collect all balance adjustments by account ID, then apply them at the end.
        val adjustments = mutableMapOf<Long, Long>()
        fun adjust(accountId: Long, delta: Long) { adjustments[accountId] = (adjustments[accountId] ?: 0L) + delta }

        // Helper: balance delta for a non-transfer transaction on a given account.
        fun incExpDelta(account: AccountEntity, value: TransactionEntity): Long = when {
            account.isCreditCard && value.type == TransactionType.EXPENSE -> value.amountPaise
            account.isCreditCard -> -value.amountPaise
            value.type == TransactionType.EXPENSE -> -value.amountPaise
            else -> value.amountPaise
        }

        // 1. Reverse the previous transaction's effect.
        val prevSource = database.accounts().getById(previous.accountId) ?: error("Previous account not found")
        if (previous.type == TransactionType.TRANSFER) {
            val prevDest = database.accounts().getById(previous.destinationAccountId!!) ?: error("Previous destination not found")
            val srcDelta = if (prevSource.isCreditCard) previous.amountPaise else -previous.amountPaise
            val dstDelta = if (prevDest.isCreditCard) -previous.amountPaise else previous.amountPaise
            adjust(prevSource.id, -srcDelta)
            adjust(prevDest.id, -dstDelta)
        } else {
            adjust(prevSource.id, -incExpDelta(prevSource, previous))
        }

        // 2. Apply the new transaction's effect.
        val newSource = database.accounts().getById(transaction.accountId) ?: error("Choose a valid account")
        if (transaction.type == TransactionType.TRANSFER) {
            val newDest = database.accounts().getById(transaction.destinationAccountId!!) ?: error("Choose a valid destination account")
            val srcDelta = if (newSource.isCreditCard) transaction.amountPaise else -transaction.amountPaise
            val dstDelta = if (newDest.isCreditCard) -transaction.amountPaise else transaction.amountPaise
            adjust(newSource.id, srcDelta)
            adjust(newDest.id, dstDelta)
        } else {
            adjust(newSource.id, incExpDelta(newSource, transaction))
        }

        // 3. Apply all adjustments.
        for ((accountId, delta) in adjustments) {
            if (delta != 0L) {
                val acct = database.accounts().getById(accountId) ?: continue
                database.accounts().update(acct.copy(balancePaise = acct.balancePaise + delta))
            }
        }

        database.transactions().update(transaction)
        database.owed().deleteForTransaction(previous.id)
        if (transaction.type != TransactionType.TRANSFER) recordSharedRecovery(transaction)
    }

    private suspend fun recordSharedRecovery(transaction: TransactionEntity) {
        val recoverable = transaction.amountPaise - (transaction.mySharePaise ?: transaction.amountPaise)
        if (transaction.type == TransactionType.EXPENSE && transaction.isShared && recoverable > 0 && transaction.sharedWith.isNotBlank()) {
            database.owed().insert(com.spendtrack.app.data.local.OwedEntity(personName = transaction.sharedWith.trim(), amountPaise = recoverable, direction = com.spendtrack.app.data.local.OwedDirection.OWED_TO_ME, note = transaction.description, sourceTransactionId = transaction.id))
        }
    }

    suspend fun addAccount(
        name: String,
        isCreditCard: Boolean,
        openingPaise: Long,
        maskedNumber: String = "",
        creditLimitPaise: Long? = null,
        statementCycleDay: Int? = null,
        dueDay: Int? = null,
        minimumBalancePaise: Long = 0,
    ) {
        val sanitizedMinBalance = if (isCreditCard) 0L else minimumBalancePaise.coerceAtLeast(0L)
        database.accounts().insert(
            AccountEntity(
                name = name,
                maskedNumber = maskedNumber,
                balancePaise = openingPaise,
                isCreditCard = isCreditCard,
                creditLimitPaise = creditLimitPaise,
                statementCycleDay = statementCycleDay,
                dueDay = dueDay,
                minimumBalancePaise = sanitizedMinBalance
            )
        )
    }

    suspend fun addCategory(name: String, income: Boolean) {
        database.categories().insert(CategoryEntity(name = name, isIncomeCategory = income))
    }

    suspend fun updateAccount(account: AccountEntity) {
        val sanitizedMinBalance = if (account.isCreditCard) 0L else account.minimumBalancePaise.coerceAtLeast(0L)
        database.accounts().update(account.copy(minimumBalancePaise = sanitizedMinBalance))
    }

    suspend fun deleteAccount(account: AccountEntity): Boolean = database.withTransaction {
        if (database.accounts().transactionCount(account.id) > 0 ||
            database.fixedDeposits().activeCountForAccount(account.id) > 0 ||
            database.accounts().recurringCount(account.id) > 0) return@withTransaction false
        database.accounts().delete(account)
        true
    }

    suspend fun updateCategory(category: CategoryEntity) = database.categories().update(category)

    suspend fun deleteCategory(category: CategoryEntity): Boolean = database.withTransaction {
        if (category.isSystemFallback ||
            database.categories().transactionCount(category.id) > 0 ||
            database.categories().budgetCount(category.id) > 0 ||
            database.categories().recurringCount(category.id) > 0) return@withTransaction false
        database.categories().delete(category)
        true
    }

    suspend fun saveBudget(categoryId: Long, monthKey: String, limitPaise: Long) = database.withTransaction {
        val existing = database.budgets().find(categoryId, monthKey)
        if (existing == null) database.budgets().insert(com.spendtrack.app.data.local.BudgetEntity(categoryId = categoryId, monthKey = monthKey, limitPaise = limitPaise))
        else database.budgets().update(existing.copy(limitPaise = limitPaise))
    }

    /** Carries budget limits forward, never spending: every month begins with zero usage. */
    suspend fun ensureBudgetMonth(monthKey: String) = database.withTransaction {
        if (database.budgets().forMonth(monthKey).isNotEmpty()) return@withTransaction
        val previousMonth = database.budgets().latestMonthBefore(monthKey) ?: return@withTransaction
        database.budgets().forMonth(previousMonth).forEach { previous ->
            database.budgets().insert(previous.copy(id = 0, monthKey = monthKey))
        }
    }

    suspend fun deleteBudget(budget: com.spendtrack.app.data.local.BudgetEntity) = database.budgets().delete(budget)

    suspend fun addLoan(loan: com.spendtrack.app.data.local.LoanEntity) = database.loans().insert(loan)
    suspend fun addOwed(owed: com.spendtrack.app.data.local.OwedEntity) = database.owed().insert(owed)

    suspend fun updateLoan(loan: com.spendtrack.app.data.local.LoanEntity) = database.loans().update(loan)
    suspend fun deleteLoan(loan: com.spendtrack.app.data.local.LoanEntity) = database.loans().delete(loan)
    suspend fun updateOwed(owed: com.spendtrack.app.data.local.OwedEntity) = database.owed().update(owed)
    suspend fun deleteOwed(owed: com.spendtrack.app.data.local.OwedEntity) = database.owed().delete(owed)
    suspend fun settleOwed(owed: com.spendtrack.app.data.local.OwedEntity, amountPaise: Long) = database.withTransaction {
        val remaining = (owed.amountPaise - amountPaise).coerceAtLeast(0)
        if (remaining == 0L) database.owed().delete(owed) else database.owed().update(owed.copy(amountPaise = remaining))
    }

    suspend fun payCard(cardId: Long, sourceId: Long, amountPaise: Long, dateEpochDay: Long) = database.withTransaction {
        val card = database.accounts().getById(cardId) ?: error("Card not found")
        val source = database.accounts().getById(sourceId) ?: error("Payment account not found")
        require(card.isCreditCard && !source.isCreditCard && amountPaise > 0 && amountPaise <= card.balancePaise) { "Invalid payment" }
        database.accounts().update(card.copy(balancePaise = card.balancePaise - amountPaise))
        database.accounts().update(source.copy(balancePaise = source.balancePaise - amountPaise))
        database.cardPayments().insert(com.spendtrack.app.data.local.CardPaymentEntity(cardAccountId = cardId, sourceAccountId = sourceId, amountPaise = amountPaise, dateEpochDay = dateEpochDay))
    }

    suspend fun addFixedDeposit(
        name: String,
        linkedAccountId: Long,
        principalPaise: Long,
        interestRateBps: Int,
        startDateEpochDay: Long,
        maturityDateEpochDay: Long,
        maturityAmountPaise: Long,
        description: String = "",
        compoundingFrequency: CompoundingFrequency = CompoundingFrequency.QUARTERLY,
        isExisting: Boolean = false
    ): Long = database.withTransaction {
        require(name.isNotBlank()) { "Name cannot be blank" }
        require(principalPaise > 0) { "Principal must be positive" }
        require(interestRateBps >= 0) { "Interest rate cannot be negative" }
        require(maturityDateEpochDay > startDateEpochDay) { "Maturity date must be after start date" }
        require(maturityAmountPaise >= principalPaise) { "Maturity amount must be at least principal" }
        val account = database.accounts().getById(linkedAccountId) ?: error("Account not found")
        require(!account.isCreditCard) { "Fixed deposits cannot be linked to credit cards" }
        if (!isExisting) {
            require(account.balancePaise >= principalPaise) { "Insufficient account balance" }
            database.accounts().update(account.copy(balancePaise = account.balancePaise - principalPaise))
        }

        database.fixedDeposits().insert(
            FixedDepositEntity(
                name = name.trim(),
                linkedAccountId = linkedAccountId,
                principalPaise = principalPaise,
                interestRateBps = interestRateBps,
                startDateEpochDay = startDateEpochDay,
                maturityDateEpochDay = maturityDateEpochDay,
                maturityAmountPaise = maturityAmountPaise,
                description = description.trim(),
                status = FixedDepositStatus.ACTIVE,
                compoundingFrequency = compoundingFrequency,
                isExisting = isExisting
            )
        )
    }

    suspend fun updateFixedDeposit(fd: FixedDepositEntity) = database.withTransaction {
        val previous = database.fixedDeposits().getById(fd.id) ?: error("Deposit not found")
        if (previous.status == FixedDepositStatus.MATURED) {
            database.fixedDeposits().update(
                previous.copy(
                    name = fd.name.trim(),
                    description = fd.description.trim()
                )
            )
            return@withTransaction
        }

        require(fd.name.isNotBlank()) { "Name cannot be blank" }
        require(fd.principalPaise > 0) { "Principal must be positive" }
        require(fd.interestRateBps >= 0) { "Interest rate cannot be negative" }
        require(fd.maturityDateEpochDay > fd.startDateEpochDay) { "Maturity date must be after start date" }
        require(fd.maturityAmountPaise >= fd.principalPaise) { "Maturity amount must be at least principal" }

        if (!previous.isExisting && !fd.isExisting) {
            if (previous.linkedAccountId == fd.linkedAccountId) {
                val account = database.accounts().getById(fd.linkedAccountId) ?: error("Account not found")
                require(!account.isCreditCard) { "Fixed deposits cannot be linked to credit cards" }
                val diff = fd.principalPaise - previous.principalPaise
                if (diff > 0) {
                    require(account.balancePaise >= diff) { "Insufficient account balance" }
                }
                database.accounts().update(account.copy(balancePaise = account.balancePaise - diff))
            } else {
                val oldAccount = database.accounts().getById(previous.linkedAccountId) ?: error("Previous account not found")
                val newAccount = database.accounts().getById(fd.linkedAccountId) ?: error("New account not found")
                require(!newAccount.isCreditCard) { "Fixed deposits cannot be linked to credit cards" }
                require(newAccount.balancePaise >= fd.principalPaise) { "Insufficient account balance in new account" }
                database.accounts().update(oldAccount.copy(balancePaise = oldAccount.balancePaise + previous.principalPaise))
                database.accounts().update(newAccount.copy(balancePaise = newAccount.balancePaise - fd.principalPaise))
            }
        }
        database.fixedDeposits().update(
            fd.copy(
                name = fd.name.trim(),
                description = fd.description.trim()
            )
        )
    }

    suspend fun deleteFixedDeposit(fd: FixedDepositEntity) = database.withTransaction {
        val current = database.fixedDeposits().getById(fd.id) ?: return@withTransaction
        if (current.status == FixedDepositStatus.ACTIVE && !current.isExisting) {
            val account = database.accounts().getById(current.linkedAccountId)
            if (account != null) {
                database.accounts().update(account.copy(balancePaise = account.balancePaise + current.principalPaise))
            }
        }
        database.fixedDeposits().delete(current)
    }

    suspend fun matureFixedDeposit(fdId: Long, maturityEpochDay: Long = LocalDate.now().toEpochDay()): Boolean = database.withTransaction {
        val fd = database.fixedDeposits().getById(fdId) ?: return@withTransaction false
        if (fd.status != FixedDepositStatus.ACTIVE) return@withTransaction false
        val account = database.accounts().getById(fd.linkedAccountId) ?: error("Linked account not found")

        val interestPaise = (fd.maturityAmountPaise - fd.principalPaise).coerceAtLeast(0L)
        if (interestPaise > 0L) {
            val category = database.categories().observeAll().first().find {
                it.isIncomeCategory && (it.name.equals("FD Interest", ignoreCase = true) || it.name.equals("Interest", ignoreCase = true))
            } ?: run {
                val newCat = CategoryEntity(name = "FD Interest", isIncomeCategory = true)
                val catId = database.categories().insert(newCat)
                newCat.copy(id = catId)
            }
            database.transactions().insert(
                TransactionEntity(
                    dateEpochDay = maturityEpochDay,
                    description = "FD Maturity Interest - ${fd.name}",
                    amountPaise = interestPaise,
                    categoryId = category.id,
                    accountId = account.id,
                    type = TransactionType.INCOME
                )
            )
        }

        database.accounts().update(account.copy(balancePaise = account.balancePaise + fd.maturityAmountPaise))
        database.fixedDeposits().update(fd.copy(status = FixedDepositStatus.MATURED))
        true
    }

    suspend fun matureDueDeposits(todayEpochDay: Long = LocalDate.now().toEpochDay()): Int = database.withTransaction {
        val dueList = database.fixedDeposits().getDueActiveDeposits(todayEpochDay)
        var count = 0
        for (due in dueList) {
            if (matureFixedDeposit(due.id, due.maturityDateEpochDay)) {
                count++
            }
        }
        count
    }

    suspend fun addRecurringTransaction(schedule: RecurringTransactionEntity, todayEpochDay: Long = LocalDate.now().toEpochDay()): Long = database.withTransaction {
        val id = database.recurringTransactions().insert(schedule)
        val saved = schedule.copy(id = id)
        if (saved.isEnabled && saved.nextOccurrenceEpochDay <= todayEpochDay) {
            processDueRecurringSchedule(saved, todayEpochDay)
        }
        id
    }

    suspend fun updateRecurringTransaction(schedule: RecurringTransactionEntity) = database.recurringTransactions().update(schedule)

    suspend fun deleteRecurringTransaction(schedule: RecurringTransactionEntity) = database.withTransaction {
        // Deleting schedule does NOT delete historical transactions that were already generated
        database.recurringOccurrences().deleteForSchedule(schedule.id)
        database.recurringTransactions().delete(schedule)
    }

    suspend fun toggleRecurringTransaction(id: Long, isEnabled: Boolean) = database.withTransaction {
        val schedule = database.recurringTransactions().getById(id) ?: return@withTransaction
        database.recurringTransactions().update(schedule.copy(isEnabled = isEnabled))
    }

    suspend fun processDueRecurringTransactions(todayEpochDay: Long = LocalDate.now().toEpochDay()): Int = database.withTransaction {
        val dueSchedules = database.recurringTransactions().getDueSchedules(todayEpochDay)
        var count = 0
        for (schedule in dueSchedules) {
            count += processDueRecurringSchedule(schedule, todayEpochDay)
        }
        count
    }

    private suspend fun processDueRecurringSchedule(schedule: RecurringTransactionEntity, todayEpochDay: Long): Int {
        var generatedCount = 0
        var currentNext = schedule.nextOccurrenceEpochDay
        val startDate = LocalDate.ofEpochDay(schedule.startDateEpochDay)
        var updatedSchedule = schedule

        while (currentNext <= todayEpochDay && (schedule.endDateEpochDay == null || currentNext <= schedule.endDateEpochDay)) {
            val alreadyGenerated = database.recurringOccurrences().hasOccurrence(schedule.id, currentNext)
            if (!alreadyGenerated) {
                val transaction = TransactionEntity(
                    dateEpochDay = currentNext,
                    description = schedule.description,
                    amountPaise = schedule.amountPaise,
                    categoryId = schedule.categoryId,
                    accountId = schedule.accountId,
                    type = schedule.type,
                    isShared = schedule.isShared,
                    mySharePaise = schedule.mySharePaise,
                    sharedWith = schedule.sharedWith,
                    destinationAccountId = schedule.destinationAccountId,
                )
                val txId = addTransaction(transaction)
                database.recurringOccurrences().insert(
                    RecurringOccurrenceEntity(
                        scheduleId = schedule.id,
                        occurrenceEpochDay = currentNext,
                        transactionId = txId
                    )
                )
                generatedCount++
            }

            val nextDate = RecurringTransactionScheduler.calculateNextOccurrence(
                startDate = startDate,
                currentOccurrence = LocalDate.ofEpochDay(currentNext),
                frequency = schedule.frequency
            ).toEpochDay()

            currentNext = nextDate
            updatedSchedule = updatedSchedule.copy(nextOccurrenceEpochDay = currentNext)
        }

        if (schedule.endDateEpochDay != null && currentNext > schedule.endDateEpochDay) {
            updatedSchedule = updatedSchedule.copy(isEnabled = false, nextOccurrenceEpochDay = currentNext)
        }

        database.recurringTransactions().update(updatedSchedule)
        return generatedCount
    }
}
