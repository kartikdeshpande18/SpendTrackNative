package com.spendtrack.app.data.repository

import androidx.room.withTransaction
import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.SpendTrackDatabase
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.TransactionType
import kotlinx.coroutines.flow.Flow

/** Local-only accounting gateway. Balance-changing writes run atomically. */
class FinanceRepository(private val database: SpendTrackDatabase) {
    val accounts: Flow<List<AccountEntity>> = database.accounts().observeAll()
    val categories: Flow<List<CategoryEntity>> = database.categories().observeAll()
    val transactions: Flow<List<TransactionEntity>> = database.transactions().observeAll()
    val loans = database.loans().observeAll()
    val owed = database.owed().observeAll()
    val budgets = database.budgets().observeAll()
    val cardPayments = database.cardPayments().observeAll()

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

    suspend fun addTransaction(transaction: TransactionEntity) = database.withTransaction {
        val account = database.accounts().getById(transaction.accountId) ?: error("Choose a valid account")
        val delta = when {
            account.isCreditCard && transaction.type == TransactionType.EXPENSE -> transaction.amountPaise
            account.isCreditCard -> -transaction.amountPaise
            transaction.type == TransactionType.EXPENSE -> -transaction.amountPaise
            else -> transaction.amountPaise
        }
        val id = database.transactions().insert(transaction)
        database.accounts().update(account.copy(balancePaise = account.balancePaise + delta))
        recordSharedRecovery(transaction.copy(id = id))
    }

    suspend fun deleteTransaction(id: Long) = database.withTransaction {
        val transaction = database.transactions().getById(id) ?: return@withTransaction
        val account = database.accounts().getById(transaction.accountId) ?: return@withTransaction
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

    suspend fun updateTransaction(transaction: TransactionEntity) = database.withTransaction {
        val previous = database.transactions().getById(transaction.id) ?: error("Transaction not found")
        val previousAccount = database.accounts().getById(previous.accountId) ?: error("Previous account not found")
        val newAccount = database.accounts().getById(transaction.accountId) ?: error("Choose a valid account")
        fun balanceDelta(account: AccountEntity, value: TransactionEntity): Long = when {
            account.isCreditCard && value.type == TransactionType.EXPENSE -> value.amountPaise
            account.isCreditCard -> -value.amountPaise
            value.type == TransactionType.EXPENSE -> -value.amountPaise
            else -> value.amountPaise
        }
        val reversedPrevious = -balanceDelta(previousAccount, previous)
        val appliedNew = balanceDelta(newAccount, transaction)
        if (previousAccount.id == newAccount.id) {
            database.accounts().update(previousAccount.copy(balancePaise = previousAccount.balancePaise + reversedPrevious + appliedNew))
        } else {
            database.accounts().update(previousAccount.copy(balancePaise = previousAccount.balancePaise + reversedPrevious))
            database.accounts().update(newAccount.copy(balancePaise = newAccount.balancePaise + appliedNew))
        }
        database.transactions().update(transaction)
        database.owed().deleteForTransaction(previous.id)
        recordSharedRecovery(transaction)
    }

    private suspend fun recordSharedRecovery(transaction: TransactionEntity) {
        val recoverable = transaction.amountPaise - (transaction.mySharePaise ?: transaction.amountPaise)
        if (transaction.type == TransactionType.EXPENSE && transaction.isShared && recoverable > 0 && transaction.sharedWith.isNotBlank()) {
            database.owed().insert(com.spendtrack.app.data.local.OwedEntity(personName = transaction.sharedWith.trim(), amountPaise = recoverable, direction = com.spendtrack.app.data.local.OwedDirection.OWED_TO_ME, note = transaction.description, sourceTransactionId = transaction.id))
        }
    }

    suspend fun addAccount(name: String, isCreditCard: Boolean, openingPaise: Long, maskedNumber: String = "", creditLimitPaise: Long? = null, statementCycleDay: Int? = null, dueDay: Int? = null) {
        database.accounts().insert(AccountEntity(name = name, maskedNumber = maskedNumber, balancePaise = openingPaise, isCreditCard = isCreditCard, creditLimitPaise = creditLimitPaise, statementCycleDay = statementCycleDay, dueDay = dueDay))
    }

    suspend fun addCategory(name: String, income: Boolean) {
        database.categories().insert(CategoryEntity(name = name, isIncomeCategory = income))
    }

    suspend fun updateAccount(account: AccountEntity) = database.accounts().update(account)

    suspend fun deleteAccount(account: AccountEntity): Boolean {
        if (database.accounts().transactionCount(account.id) > 0) return false
        database.accounts().delete(account)
        return true
    }

    suspend fun updateCategory(category: CategoryEntity) = database.categories().update(category)

    suspend fun deleteCategory(category: CategoryEntity): Boolean {
        if (category.isSystemFallback || database.categories().transactionCount(category.id) > 0 || database.categories().budgetCount(category.id) > 0) return false
        database.categories().delete(category)
        return true
    }

    suspend fun saveBudget(categoryId: Long, monthKey: String, limitPaise: Long) {
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
    suspend fun settleOwed(owed: com.spendtrack.app.data.local.OwedEntity, amountPaise: Long) {
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
}
