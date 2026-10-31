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
import com.spendtrack.app.data.local.OwedDirection
import com.spendtrack.app.data.local.OwedEntity
import com.spendtrack.app.data.local.SharedExpenseHelper
import com.spendtrack.app.data.local.SavingsInterestCalculator
import com.spendtrack.app.data.local.InterestCreditFrequency
import com.spendtrack.app.data.local.InterestCalculationMethod
import com.spendtrack.app.data.local.SavingsInterestPeriod
import com.spendtrack.app.data.local.SavingsInterestPeriods
import com.spendtrack.app.data.local.PersonEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.Locale

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
    val people: Flow<List<PersonEntity>> = database.people().observeAll()
    val activePeople: Flow<List<PersonEntity>> = database.people().observeActive()

    private fun normalizePersonName(name: String) = name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)

    private suspend fun ensurePerson(name: String) {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        if (clean.isBlank() || clean.equals("you", true) || clean.equals("me", true) || clean.equals("kartik", true)) return
        val key = normalizePersonName(clean)
        val existing = database.people().getByNormalizedName(key)
        if (existing == null) database.people().insert(PersonEntity(name = clean, normalizedName = key))
    }

    suspend fun syncPeopleFromHistory() = database.withTransaction {
        database.owed().observeAll().first().forEach { ensurePerson(it.personName) }
        database.transactions().observeAll().first().forEach { transaction ->
            val split = SharedExpenseHelper.deserialize(transaction.splitDetails)
            split?.participants?.forEach { ensurePerson(it) }
            split?.shares?.keys?.forEach { ensurePerson(it) }
            transaction.sharedWith.split(",").forEach { ensurePerson(it) }
        }
        database.recurringTransactions().observeAll().first().filter { it.isShared }
            .flatMap { it.sharedWith.split(",") }.forEach { ensurePerson(it) }
    }

    suspend fun createPerson(name: String): Long = database.withTransaction {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotBlank()) { "Person name cannot be blank" }
        val key = normalizePersonName(clean)
        val existing = database.people().getByNormalizedName(key)
        if (existing != null) {
            if (!existing.isActive) database.people().update(existing.copy(isActive = true))
            existing.id
        } else database.people().insert(PersonEntity(name = clean, normalizedName = key))
    }

    suspend fun renamePerson(personId: Long, name: String) = database.withTransaction {
        val person = database.people().getById(personId) ?: error("Person not found")
        val clean = name.trim().replace(Regex("\\s+"), " ")
        require(clean.isNotBlank()) { "Person name cannot be blank" }
        val key = normalizePersonName(clean)
        val duplicate = database.people().getByNormalizedName(key)
        require(duplicate == null || duplicate.id == personId) { "A person with this name already exists" }
        if (clean == person.name) return@withTransaction
        database.transactions().observeAll().first().forEach { tx ->
            val split = SharedExpenseHelper.deserialize(tx.splitDetails)
            val updatedSplit = split?.let {
                it.copy(
                    payer = it.payer.replacePersonName(person.name, clean),
                    participants = it.participants.map { n -> n.replacePersonName(person.name, clean) },
                    shares = it.shares.entries.associate { entry -> entry.key.replacePersonName(person.name, clean) to entry.value }
                )
            }
            val updatedSharedWith = tx.sharedWith.split(",").joinToString(", ") { it.trim().replacePersonName(person.name, clean) }
            if ((updatedSplit != null && updatedSplit != split) || updatedSharedWith != tx.sharedWith) {
                database.transactions().update(tx.copy(
                    splitDetails = if (updatedSplit != null) SharedExpenseHelper.serialize(updatedSplit) else tx.splitDetails,
                    sharedWith = updatedSharedWith
                ))
            }
        }
        database.owed().observeAll().first().filter { normalizePersonName(it.personName) == person.normalizedName }
            .forEach { database.owed().update(it.copy(personName = clean)) }
        database.recurringTransactions().observeAll().first().filter { it.isShared }.forEach { schedule ->
            val names = schedule.sharedWith.split(",").map { it.trim().replacePersonName(person.name, clean) }
            if (names.joinToString(", ") != schedule.sharedWith) database.recurringTransactions().update(schedule.copy(sharedWith = names.joinToString(", ")))
        }
        database.people().update(person.copy(name = clean, normalizedName = key))
    }

    private fun String.replacePersonName(old: String, new: String) =
        if (normalizePersonName(this) == normalizePersonName(old)) new else this

    suspend fun setPersonActive(personId: Long, active: Boolean) = database.people().getById(personId)?.let {
        database.people().update(it.copy(isActive = active))
    }

    suspend fun deletePerson(personId: Long) = database.withTransaction {
        val person = database.people().getById(personId) ?: return@withTransaction
        val owed = database.owed().observeAll().first().any { normalizePersonName(it.personName) == person.normalizedName }
        val shared = database.transactions().observeAll().first().any { tx ->
            val split = SharedExpenseHelper.deserialize(tx.splitDetails)
            listOfNotNull(split?.payer).plus(split?.participants.orEmpty()).plus(split?.shares?.keys.orEmpty())
                .any { normalizePersonName(it) == person.normalizedName } ||
                tx.sharedWith.split(",").any { normalizePersonName(it) == person.normalizedName }
        }
        val recurring = database.recurringTransactions().observeAll().first().any { it.sharedWith.split(",").any { n -> normalizePersonName(n) == person.normalizedName } }
        require(!owed && !shared && !recurring) { "Person has historical records and cannot be deleted" }
        database.people().delete(person)
    }

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
        val split = SharedExpenseHelper.deserialize(transaction.splitDetails)
        split?.participants?.forEach { ensurePerson(it) }
        split?.shares?.keys?.forEach { ensurePerson(it) }
        transaction.sharedWith.split(",").forEach { ensurePerson(it) }
        val account = database.accounts().getById(transaction.accountId) ?: error("Choose a valid account")
        if (transaction.type == TransactionType.TRANSFER) {
            val destAccount = transaction.destinationAccountId?.let { database.accounts().getById(it) }
            val sourceDelta = if (account.isCreditCard) transaction.amountPaise else -transaction.amountPaise
            val id = database.transactions().insert(transaction)
            if (!transaction.isExcludedFromCalculations) {
                database.accounts().update(account.copy(balancePaise = account.balancePaise + sourceDelta))
                if (destAccount != null) {
                    val destDelta = if (destAccount.isCreditCard) -transaction.amountPaise else transaction.amountPaise
                    database.accounts().update(destAccount.copy(balancePaise = destAccount.balancePaise + destDelta))
                }
            }
            id
        } else {
            val delta = when {
                account.isCreditCard && transaction.type == TransactionType.EXPENSE -> transaction.amountPaise
                account.isCreditCard -> -transaction.amountPaise
                transaction.type == TransactionType.EXPENSE -> -transaction.amountPaise
                else -> transaction.amountPaise
            }
            val id = database.transactions().insert(transaction)
            if (!transaction.isExcludedFromCalculations) {
                database.accounts().update(account.copy(balancePaise = account.balancePaise + delta))
            }
            recordSharedRecovery(transaction.copy(id = id))
            id
        }
    }

    suspend fun deleteTransaction(id: Long) = database.withTransaction {
        val transaction = database.transactions().getById(id) ?: return@withTransaction
        val account = database.accounts().getById(transaction.accountId)
        if (transaction.type == TransactionType.TRANSFER) {
            val destAccount = transaction.destinationAccountId?.let { database.accounts().getById(it) }
            database.transactions().delete(transaction)
            database.owed().deleteForTransaction(transaction.id)
            if (!transaction.isExcludedFromCalculations) {
                if (account != null) {
                    val sourceDelta = if (account.isCreditCard) transaction.amountPaise else -transaction.amountPaise
                    database.accounts().update(account.copy(balancePaise = account.balancePaise - sourceDelta))
                }
                if (destAccount != null) {
                    val destDelta = if (destAccount.isCreditCard) -transaction.amountPaise else transaction.amountPaise
                    database.accounts().update(destAccount.copy(balancePaise = destAccount.balancePaise - destDelta))
                }
            }
        } else {
            database.transactions().delete(transaction)
            database.owed().deleteForTransaction(transaction.id)
            if ((!transaction.isExcludedFromCalculations || transaction.isLoanRepayment) && account != null) {
                val delta = when {
                    account.isCreditCard && transaction.type == TransactionType.EXPENSE -> transaction.amountPaise
                    account.isCreditCard -> -transaction.amountPaise
                    transaction.type == TransactionType.EXPENSE -> -transaction.amountPaise
                    else -> transaction.amountPaise
                }
                database.accounts().update(account.copy(balancePaise = account.balancePaise - delta))
            }
            if (transaction.isLoanRepayment) {
                transaction.originalExpenseId?.let { database.loans().getById(it) }?.let { loan ->
                    database.loans().update(
                        loan.copy(
                            currentOutstandingPaise = loan.currentOutstandingPaise + transaction.amountPaise,
                            principalPaidPaise = (loan.principalPaidPaise - transaction.amountPaise).coerceAtLeast(0L)
                        )
                    )
                }
            }
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity) = database.withTransaction {
        val previous = database.transactions().getById(transaction.id) ?: return@withTransaction
        val updated = if (previous.isLoanRepayment) {
            require(transaction.type == TransactionType.EXPENSE) { "A loan repayment must remain an expense" }
            require(transaction.amountPaise > 0L) { "Repayment amount must be positive" }
            val account = database.accounts().getById(transaction.accountId) ?: error("Choose a valid account")
            require(!account.isCreditCard) { "Loan repayment cannot be paid from a credit card" }
            val loan = previous.originalExpenseId?.let { database.loans().getById(it) }
            if (loan != null) {
                require(transaction.amountPaise <= loan.currentOutstandingPaise + previous.amountPaise) {
                    "Repayment cannot exceed outstanding principal"
                }
            }
            transaction.copy(
                isExcludedFromCalculations = true,
                originalExpenseId = previous.originalExpenseId,
                isLoanRepayment = true
            )
        } else transaction
        val split = SharedExpenseHelper.deserialize(updated.splitDetails)
        split?.participants?.forEach { ensurePerson(it) }
        split?.shares?.keys?.forEach { ensurePerson(it) }
        updated.sharedWith.split(",").forEach { ensurePerson(it) }

        // Collect all balance adjustments by account ID, then apply them at the end.
        val adjustments = mutableMapOf<Long, Long>()
        fun adjust(accountId: Long, delta: Long) { adjustments[accountId] = (adjustments[accountId] ?: 0L) + delta }
        fun affectsBalance(value: TransactionEntity) = !value.isExcludedFromCalculations || value.isLoanRepayment

        // Helper: balance delta for a non-transfer transaction on a given account.
        fun incExpDelta(account: AccountEntity, value: TransactionEntity): Long = when {
            account.isCreditCard && value.type == TransactionType.EXPENSE -> value.amountPaise
            account.isCreditCard -> -value.amountPaise
            value.type == TransactionType.EXPENSE -> -value.amountPaise
            else -> value.amountPaise
        }

        // 1. Reverse the previous transaction's balance effect.
        if (affectsBalance(previous)) {
            val prevSource = database.accounts().getById(previous.accountId)
            if (prevSource != null) {
                if (previous.type == TransactionType.TRANSFER) {
                    val srcDelta = if (prevSource.isCreditCard) previous.amountPaise else -previous.amountPaise
                    adjust(prevSource.id, -srcDelta)
                } else {
                    adjust(prevSource.id, -incExpDelta(prevSource, previous))
                }
            }
            if (previous.type == TransactionType.TRANSFER && previous.destinationAccountId != null) {
                val prevDest = database.accounts().getById(previous.destinationAccountId)
                if (prevDest != null) {
                    val dstDelta = if (prevDest.isCreditCard) -previous.amountPaise else previous.amountPaise
                    adjust(prevDest.id, -dstDelta)
                }
            }
        }

        // 2. Apply the new transaction's balance effect.
        if (affectsBalance(updated)) {
            val newSource = database.accounts().getById(updated.accountId)
            if (newSource != null) {
                if (updated.type == TransactionType.TRANSFER) {
                    val srcDelta = if (newSource.isCreditCard) updated.amountPaise else -updated.amountPaise
                    adjust(newSource.id, srcDelta)
                } else {
                    adjust(newSource.id, incExpDelta(newSource, updated))
                }
            }
            if (updated.type == TransactionType.TRANSFER && updated.destinationAccountId != null) {
                val newDest = database.accounts().getById(updated.destinationAccountId)
                if (newDest != null) {
                    val dstDelta = if (newDest.isCreditCard) -updated.amountPaise else updated.amountPaise
                    adjust(newDest.id, dstDelta)
                }
            }
        }

        // 3. Apply all adjustments.
        for ((accountId, delta) in adjustments) {
            if (delta != 0L) {
                val acct = database.accounts().getById(accountId) ?: continue
                database.accounts().update(acct.copy(balancePaise = acct.balancePaise + delta))
            }
        }

        if (previous.isLoanRepayment) {
            previous.originalExpenseId?.let { database.loans().getById(it) }?.let { loan ->
                database.loans().update(
                    loan.copy(
                        currentOutstandingPaise = loan.currentOutstandingPaise + previous.amountPaise,
                        principalPaidPaise = (loan.principalPaidPaise - previous.amountPaise).coerceAtLeast(0L)
                    )
                )
            }
        }
        if (updated.isLoanRepayment) {
            updated.originalExpenseId?.let { database.loans().getById(it) }?.let { loan ->
                database.loans().update(
                    loan.copy(
                        currentOutstandingPaise = loan.currentOutstandingPaise - updated.amountPaise,
                        principalPaidPaise = loan.principalPaidPaise + updated.amountPaise
                    )
                )
            }
        }

        database.transactions().update(updated)
        database.owed().deleteForTransaction(previous.id)
        if (updated.type != TransactionType.TRANSFER) recordSharedRecovery(updated)
    }

    private suspend fun recordSharedRecovery(transaction: TransactionEntity) {
        if (transaction.type != TransactionType.EXPENSE || !transaction.isShared) return

        // 1. Check if detailed split information exists
        val split = SharedExpenseHelper.deserialize(transaction.splitDetails)
        if (split != null && split.shares.isNotEmpty()) {
            val payer = split.payer
            val isUserPayer = payer.equals("You", ignoreCase = true) || payer.equals("Kartik", ignoreCase = true) || payer.equals("Me", ignoreCase = true)
            if (isUserPayer) {
                // I paid for the group -> participants owe me their shares
                for ((person, share) in split.shares) {
                    if (!person.equals(payer, ignoreCase = true) && !person.equals("You", ignoreCase = true) && !person.equals("Kartik", ignoreCase = true) && !person.equals("Me", ignoreCase = true) && share > 0L) {
                        database.owed().insert(
                            OwedEntity(
                                personName = person.trim(),
                                amountPaise = share,
                                originalAmountPaise = share,
                                repaidAmountPaise = 0L,
                                direction = OwedDirection.OWED_TO_ME,
                                note = transaction.description,
                                sourceTransactionId = transaction.id
                            )
                        )
                    }
                }
            } else {
                // Another person paid -> I owe the payer my share
                val myShare = split.shares["You"] ?: split.shares["Kartik"] ?: split.shares["Me"] ?: (transaction.mySharePaise ?: 0L)
                if (myShare > 0L) {
                    database.owed().insert(
                        OwedEntity(
                            personName = payer.trim(),
                            amountPaise = myShare,
                            originalAmountPaise = myShare,
                            repaidAmountPaise = 0L,
                            direction = OwedDirection.I_OWE,
                            note = transaction.description,
                            sourceTransactionId = transaction.id
                        )
                    )
                }
            }
            return
        }

        // 2. Comma-separated names in sharedWith or single person
        val recoverable = transaction.amountPaise - (transaction.mySharePaise ?: transaction.amountPaise)
        if (recoverable > 0 && transaction.sharedWith.isNotBlank()) {
            val names = transaction.sharedWith.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (names.size > 1) {
                val base = recoverable / names.size
                var remainder = recoverable % names.size
                for (name in names) {
                    val share = base + if (remainder > 0L) { remainder--; 1L } else 0L
                    database.owed().insert(
                        OwedEntity(
                            personName = name,
                            amountPaise = share,
                            originalAmountPaise = share,
                            repaidAmountPaise = 0L,
                            direction = OwedDirection.OWED_TO_ME,
                            note = transaction.description,
                            sourceTransactionId = transaction.id
                        )
                    )
                }
            } else if (names.size == 1) {
                database.owed().insert(
                    OwedEntity(
                        personName = names[0],
                        amountPaise = recoverable,
                        originalAmountPaise = recoverable,
                        repaidAmountPaise = 0L,
                        direction = OwedDirection.OWED_TO_ME,
                        note = transaction.description,
                        sourceTransactionId = transaction.id
                    )
                )
            }
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

    suspend fun addCategory(name: String, income: Boolean, colorHex: String? = null, iconName: String? = null) {
        database.categories().insert(CategoryEntity(name = name, isIncomeCategory = income, colorHex = colorHex, iconName = iconName))
    }

    suspend fun updateAccount(account: AccountEntity) {
        val sanitizedMinBalance = if (account.isCreditCard) 0L else account.minimumBalancePaise.coerceAtLeast(0L)
        val baselineInterestDay = if (!account.isCreditCard && account.interestRateBps > 0 && account.lastInterestPostingEpochDay == null) {
            LocalDate.now().minusDays(1).toEpochDay()
        } else account.lastInterestPostingEpochDay
        database.accounts().update(
            account.copy(
                minimumBalancePaise = sanitizedMinBalance,
                interestCalculationMethod = InterestCalculationMethod.DAILY.name,
                lastInterestPostingEpochDay = baselineInterestDay,
            )
        )
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
        if (category.isSystemFallback) return@withTransaction false
        val hasTransactions = database.categories().transactionCount(category.id) > 0
        val hasBudgets = database.categories().budgetCount(category.id) > 0
        val hasRecurring = database.categories().recurringCount(category.id) > 0

        if (hasTransactions || hasBudgets || hasRecurring) {
            database.categories().update(category.copy(isDisabled = true))
            return@withTransaction true
        }
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
    suspend fun addOwed(owed: com.spendtrack.app.data.local.OwedEntity) = database.withTransaction {
        require(database.people().getByNormalizedName(normalizePersonName(owed.personName))?.isActive == true) {
            "Choose an active person from Shared Expenses"
        }
        database.owed().insert(owed)
    }

    suspend fun updateLoan(loan: com.spendtrack.app.data.local.LoanEntity) = database.loans().update(loan)
    suspend fun deleteLoan(loan: com.spendtrack.app.data.local.LoanEntity) = database.loans().delete(loan)
    suspend fun updateOwed(owed: com.spendtrack.app.data.local.OwedEntity) = database.owed().update(owed)
    suspend fun deleteOwed(owed: com.spendtrack.app.data.local.OwedEntity) = database.owed().delete(owed)

    suspend fun settleOwed(
        owedId: Long,
        amountPaise: Long,
        accountId: Long,
        dateEpochDay: Long = LocalDate.now().toEpochDay(),
        settlementNote: String = ""
    ): Long = database.withTransaction {
        val owed = database.owed().getById(owedId) ?: error("Owed record not found")
        require(amountPaise > 0) { "Settlement amount must be positive" }
        require(amountPaise <= owed.amountPaise) { "Settlement amount ($amountPaise) cannot exceed outstanding balance (${owed.amountPaise})" }
        val account = database.accounts().getById(accountId) ?: error("Account not found")

        val isIncoming = owed.direction == OwedDirection.OWED_TO_ME
        val transactionType = if (isIncoming) TransactionType.INCOME else TransactionType.EXPENSE

        // Balance delta:
        // When a person repays me: credit account (+ for bank/cash, - for credit card)
        // When I repay someone: debit account (- for bank/cash, + for credit card)
        val delta = when {
            account.isCreditCard && isIncoming -> -amountPaise
            account.isCreditCard -> amountPaise
            isIncoming -> amountPaise
            else -> -amountPaise
        }

        val desc = when {
            settlementNote.isNotBlank() -> settlementNote
            isIncoming -> "Settlement from ${owed.personName}${if (owed.note.isNotBlank()) ": ${owed.note}" else ""}"
            else -> "Settlement to ${owed.personName}${if (owed.note.isNotBlank()) ": ${owed.note}" else ""}"
        }

        // Avoid duplicate transaction entries on retry
        val existingLinked = if (owed.sourceTransactionId != null) {
            database.transactions().getByOriginalExpenseId(owed.sourceTransactionId)
                .firstOrNull { it.dateEpochDay == dateEpochDay && it.amountPaise == amountPaise && it.accountId == accountId && it.description == desc }
        } else null

        val txId = if (existingLinked != null) {
            existingLinked.id
        } else {
            val transaction = TransactionEntity(
                dateEpochDay = dateEpochDay,
                description = desc,
                amountPaise = amountPaise,
                categoryId = 0L,
                accountId = accountId,
                type = transactionType,
                isExcludedFromCalculations = true,
                originalExpenseId = owed.sourceTransactionId
            )
            val newId = database.transactions().insert(transaction)
            database.accounts().update(account.copy(balancePaise = account.balancePaise + delta))
            newId
        }

        val originalAmt = if (owed.originalAmountPaise > 0L) owed.originalAmountPaise else owed.amountPaise
        val newRepaid = owed.repaidAmountPaise + amountPaise
        val remaining = (owed.amountPaise - amountPaise).coerceAtLeast(0L)

        database.owed().update(owed.copy(amountPaise = remaining, originalAmountPaise = originalAmt, repaidAmountPaise = newRepaid))
        txId
    }

    suspend fun settleOwed(owed: com.spendtrack.app.data.local.OwedEntity, amountPaise: Long) = database.withTransaction {
        val defaultAccount = database.accounts().observeAll().first().firstOrNull { !it.isCreditCard }
            ?: database.accounts().observeAll().first().firstOrNull()
        if (defaultAccount != null) {
            settleOwed(owed.id, amountPaise, defaultAccount.id)
        } else {
            val remaining = (owed.amountPaise - amountPaise).coerceAtLeast(0)
            if (remaining == 0L) database.owed().delete(owed) else database.owed().update(owed.copy(amountPaise = remaining))
        }
    }

    suspend fun repayLoan(
        loanId: Long,
        accountId: Long,
        amountPaise: Long,
        dateEpochDay: Long = LocalDate.now().toEpochDay(),
        note: String = ""
    ): Long = database.withTransaction {
        val loan = database.loans().getById(loanId) ?: error("Loan not found")
        require(amountPaise > 0) { "Repayment amount must be positive" }
        require(amountPaise <= loan.currentOutstandingPaise) { "Repayment cannot exceed outstanding principal" }
        val account = database.accounts().getById(accountId) ?: error("Choose a valid account")
        require(!account.isCreditCard) { "Loan repayment cannot be paid from a credit card" }

        val newOutstanding = loan.currentOutstandingPaise - amountPaise
        val newPaidPrincipal = loan.principalPaidPaise + amountPaise
        database.loans().update(
            loan.copy(
                currentOutstandingPaise = newOutstanding,
                principalPaidPaise = newPaidPrincipal
            )
        )

        // Deduct payment from selected account
        database.accounts().update(account.copy(balancePaise = account.balancePaise - amountPaise))

        // Record repayment transaction linked to the loan, excluded from reporting
        val desc = "Loan Repayment: ${loan.name}" + if (note.isNotBlank()) " — $note" else ""
        val tx = TransactionEntity(
            dateEpochDay = dateEpochDay,
            description = desc,
            amountPaise = amountPaise,
            categoryId = 0L,
            accountId = accountId,
            type = TransactionType.EXPENSE,
            isExcludedFromCalculations = true,
            originalExpenseId = loanId,
            isLoanRepayment = true
        )
        database.transactions().insert(tx)
    }

    suspend fun postSavingsInterest(
        accountId: Long,
        amountPaise: Long,
        postingDateEpochDay: Long = LocalDate.now().toEpochDay(),
        calculationPeriodStartEpochDay: Long = postingDateEpochDay,
        calculationPeriodEndEpochDay: Long = calculationPeriodStartEpochDay
    ): Long = database.withTransaction {
        val account = database.accounts().getById(accountId) ?: error("Account not found")
        require(!account.isCreditCard) { "Interest cannot be posted to a credit card" }
        require(amountPaise > 0) { "Interest amount must be positive" }

        val frequency = runCatching { InterestCreditFrequency.valueOf(account.interestCreditFrequency) }
            .getOrDefault(InterestCreditFrequency.QUARTERLY)
        val period = SavingsInterestPeriod(
            LocalDate.ofEpochDay(calculationPeriodStartEpochDay),
            LocalDate.ofEpochDay(calculationPeriodEndEpochDay)
        )
        require(!period.end.isBefore(period.start)) { "Invalid interest calculation period" }
        val desc = SavingsInterestPeriods.description(frequency, period)

        // Prevent duplicate interest posting on retry
        val existing = database.transactions().observeAll().first()
            .firstOrNull { it.accountId == accountId && it.type == TransactionType.INCOME && it.description == desc }
        if (existing != null) {
            return@withTransaction existing.id
        }

        val existingInterestCategory = database.categories().getByName("Interest")
        val catId = if (existingInterestCategory != null) {
            existingInterestCategory.id
        } else {
            database.categories().insert(CategoryEntity(name = "Interest", isIncomeCategory = true))
        }

        // Credit to the savings account
        database.accounts().update(
            account.copy(
                balancePaise = account.balancePaise + amountPaise,
                lastInterestPostingEpochDay = calculationPeriodEndEpochDay
            )
        )

        // Record transaction
        val tx = TransactionEntity(
            dateEpochDay = postingDateEpochDay,
            description = desc,
            amountPaise = amountPaise,
            categoryId = catId,
            accountId = accountId,
            type = TransactionType.INCOME,
            isExcludedFromCalculations = false
        )
        database.transactions().insert(tx)
    }

    suspend fun processDueSavingsInterest(today: LocalDate = LocalDate.now()): Int = database.withTransaction {
        val allTransactions = database.transactions().observeAll().first().toMutableList()
        val interestCategoryId = database.categories().getByName("Interest")?.id
        var categoryId = interestCategoryId
        var postedCount = 0

        database.accounts().observeAll().first().forEach { account ->
            if (account.isCreditCard || account.interestRateBps <= 0) return@forEach
            val frequency = runCatching { InterestCreditFrequency.valueOf(account.interestCreditFrequency) }
                .getOrDefault(InterestCreditFrequency.QUARTERLY)
            val through = SavingsInterestPeriods.lastCompletedThrough(today, frequency)
            var nextDay = account.lastInterestPostingEpochDay?.let { LocalDate.ofEpochDay(it).plusDays(1) }
                ?: SavingsInterestPeriods.containing(through, frequency).start
            var currentBalance = account.balancePaise
            var cursor = account.lastInterestPostingEpochDay
            while (!nextDay.isAfter(through)) {
                val period = SavingsInterestPeriods.containing(nextDay, frequency)
                if (period.end.isAfter(through)) break
                val description = SavingsInterestPeriods.description(frequency, period)
                val duplicate = allTransactions.any {
                    it.accountId == account.id && it.type == TransactionType.INCOME && it.description == description
                }
                if (!duplicate) {
                    val accountTransactions = allTransactions.asSequence()
                        .filter { !it.isExcludedFromCalculations && (it.accountId == account.id || it.destinationAccountId == account.id) }
                        .map { transaction ->
                            if (transaction.type == TransactionType.TRANSFER && transaction.accountId != account.id) {
                                transaction.copy(type = TransactionType.INCOME, destinationAccountId = null)
                            } else transaction
                        }
                        .toList()
                    val balances = SavingsInterestCalculator.reconstructDailyBalances(
                        currentBalancePaise = currentBalance,
                        todayEpochDay = today.toEpochDay(),
                        accountTransactions = accountTransactions,
                        startEpochDay = period.start.toEpochDay(),
                        endEpochDay = period.end.toEpochDay(),
                    )
                    val interest = SavingsInterestCalculator.calculateInterestPaise(
                        dailyBalances = balances,
                        fallbackBalancePaise = currentBalance,
                        interestRateBps = account.interestRateBps,
                        method = InterestCalculationMethod.DAILY,
                        startEpochDay = period.start.toEpochDay(),
                        endEpochDay = period.end.toEpochDay(),
                    )
                    if (interest > 0L) {
                        if (categoryId == null) categoryId = database.categories().insert(CategoryEntity(name = "Interest", isIncomeCategory = true))
                        currentBalance += interest
                        val transaction = TransactionEntity(
                            dateEpochDay = today.toEpochDay(), description = description, amountPaise = interest,
                            categoryId = requireNotNull(categoryId), accountId = account.id, type = TransactionType.INCOME
                        )
                        val txId = database.transactions().insert(transaction)
                        allTransactions += transaction.copy(id = txId)
                        postedCount++
                    }
                }
                cursor = period.end.toEpochDay()
                nextDay = period.end.plusDays(1)
            }
            if (cursor != account.lastInterestPostingEpochDay) {
                database.accounts().update(account.copy(balancePaise = currentBalance, lastInterestPostingEpochDay = cursor))
            }
        }
        postedCount
    }

    suspend fun payCard(cardId: Long, sourceId: Long, amountPaise: Long, dateEpochDay: Long) = database.withTransaction {
        val card = database.accounts().getById(cardId) ?: error("Card not found")
        val source = database.accounts().getById(sourceId) ?: error("Payment account not found")
        require(card.isCreditCard && !source.isCreditCard && amountPaise > 0 && amountPaise <= card.balancePaise) { "Invalid payment" }
        database.accounts().update(card.copy(balancePaise = card.balancePaise - amountPaise))
        database.accounts().update(source.copy(balancePaise = source.balancePaise - amountPaise))
        database.cardPayments().insert(com.spendtrack.app.data.local.CardPaymentEntity(cardAccountId = cardId, sourceAccountId = sourceId, amountPaise = amountPaise, dateEpochDay = dateEpochDay))
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
