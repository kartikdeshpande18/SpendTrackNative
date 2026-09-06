package com.spendtrack.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.app.data.local.DatabaseProvider
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.BudgetEntity
import com.spendtrack.app.data.local.LoanEntity
import com.spendtrack.app.data.local.OwedEntity
import com.spendtrack.app.data.repository.FinanceRepository
import com.spendtrack.app.data.repository.BackupRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinanceRepository(DatabaseProvider.get(application))
    private val backupRepository = BackupRepository(DatabaseProvider.get(application))
    val accounts = repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions = repository.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val loans = repository.loans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val owed = repository.owed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = repository.budgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cardPayments = repository.cardPayments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init { viewModelScope.launch { repository.ensureStarterData() } }
    fun addTransaction(value: TransactionEntity) = viewModelScope.launch { repository.addTransaction(value) }
    fun deleteTransaction(id: Long) = viewModelScope.launch { repository.deleteTransaction(id) }
    fun updateTransaction(value: TransactionEntity) = viewModelScope.launch { repository.updateTransaction(value) }
    fun addAccount(name: String, card: Boolean, opening: Long, maskedNumber: String = "", creditLimit: Long? = null, statementCycle: Int? = null, dueDay: Int? = null) = viewModelScope.launch { repository.addAccount(name, card, opening, maskedNumber, creditLimit, statementCycle, dueDay) }
    fun addCategory(name: String, income: Boolean) = viewModelScope.launch { repository.addCategory(name, income) }
    fun updateAccount(account: AccountEntity) = viewModelScope.launch { repository.updateAccount(account) }
    fun deleteAccount(account: AccountEntity) = viewModelScope.launch { repository.deleteAccount(account) }
    fun updateCategory(category: CategoryEntity) = viewModelScope.launch { repository.updateCategory(category) }
    fun deleteCategory(category: CategoryEntity) = viewModelScope.launch { repository.deleteCategory(category) }
    fun saveBudget(categoryId: Long, monthKey: String, limitPaise: Long) = viewModelScope.launch { repository.saveBudget(categoryId, monthKey, limitPaise) }
    fun ensureBudgetMonth(monthKey: String) = viewModelScope.launch { repository.ensureBudgetMonth(monthKey) }
    fun deleteBudget(budget: BudgetEntity) = viewModelScope.launch { repository.deleteBudget(budget) }
    fun addLoan(loan: LoanEntity) = viewModelScope.launch { repository.addLoan(loan) }
    fun addOwed(owed: OwedEntity) = viewModelScope.launch { repository.addOwed(owed) }
    fun updateLoan(loan: LoanEntity) = viewModelScope.launch { repository.updateLoan(loan) }
    fun deleteLoan(loan: LoanEntity) = viewModelScope.launch { repository.deleteLoan(loan) }
    fun updateOwed(owed: OwedEntity) = viewModelScope.launch { repository.updateOwed(owed) }
    fun deleteOwed(owed: OwedEntity) = viewModelScope.launch { repository.deleteOwed(owed) }
    fun settleOwed(owed: OwedEntity, amountPaise: Long) = viewModelScope.launch { repository.settleOwed(owed, amountPaise) }
    fun payCard(cardId: Long, sourceId: Long, amountPaise: Long, dateEpochDay: Long) = viewModelScope.launch { repository.payCard(cardId, sourceId, amountPaise, dateEpochDay) }
    fun exportBackup(onResult: (Result<String>) -> Unit) = viewModelScope.launch { runCatching { backupRepository.export() }.also(onResult) }
    fun restoreBackup(text: String, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch { runCatching { backupRepository.restore(text) }.also(onResult) }
}
