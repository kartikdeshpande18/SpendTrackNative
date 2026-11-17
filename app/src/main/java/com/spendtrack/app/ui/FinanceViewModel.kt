package com.spendtrack.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spendtrack.app.data.local.DatabaseProvider
import com.spendtrack.app.data.local.TransactionEntity
import com.spendtrack.app.data.local.AccountEntity
import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.data.local.BudgetEntity
import com.spendtrack.app.data.local.FixedDepositEntity
import com.spendtrack.app.data.local.CompoundingFrequency
import com.spendtrack.app.data.local.RecurringTransactionEntity
import com.spendtrack.app.data.local.LoanEntity
import com.spendtrack.app.data.local.OwedEntity
import com.spendtrack.app.data.repository.FinanceRepository
import com.spendtrack.app.data.repository.BackupRepository
import com.spendtrack.app.widget.SpendTrackWidgetUpdater
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = FinanceRepository(DatabaseProvider.get(application))
    private val backupRepository = BackupRepository(DatabaseProvider.get(application))
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun clearError() {
        _errorMessage.value = null
    }

    fun reportError(message: String) {
        _errorMessage.value = message
    }

    private fun launchAction(action: suspend () -> Unit) = viewModelScope.launch {
        runCatching { action() }
            .onFailure {
                it.printStackTrace()
                _errorMessage.value = it.localizedMessage ?: "An unexpected error occurred"
            }
    }

    val accounts = repository.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val transactions = repository.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val loans = repository.loans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val owed = repository.owed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val budgets = repository.budgets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val cardPayments = repository.cardPayments.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val fixedDeposits = repository.fixedDeposits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recurringTransactions = repository.recurringTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val people = repository.people.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val activePeople = repository.activePeople.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureStarterData()
            repository.syncPeopleFromHistory()
            repository.matureDueDeposits()
            repository.processDueRecurringTransactions()
        }
    }

    fun addFixedDeposit(
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
    ) = launchAction {
        repository.addFixedDeposit(name, linkedAccountId, principalPaise, interestRateBps, startDateEpochDay, maturityDateEpochDay, maturityAmountPaise, description, compoundingFrequency, isExisting)
        notifyWidgets()
    }
    fun updateFixedDeposit(fd: FixedDepositEntity) = launchAction { repository.updateFixedDeposit(fd); notifyWidgets() }
    fun deleteFixedDeposit(fd: FixedDepositEntity) = launchAction { repository.deleteFixedDeposit(fd); notifyWidgets() }
    fun matureFixedDeposit(fdId: Long) = launchAction { repository.matureFixedDeposit(fdId); notifyWidgets() }
    fun matureDueDeposits() = launchAction { repository.matureDueDeposits(); notifyWidgets() }
    private fun notifyWidgets() {
        viewModelScope.launch {
            SpendTrackWidgetUpdater.updateAll(getApplication())
        }
    }

    fun addTransaction(value: TransactionEntity) = launchAction {
        repository.addTransaction(value)
        notifyWidgets()
    }
    fun deleteTransaction(id: Long) = launchAction {
        repository.deleteTransaction(id)
        notifyWidgets()
    }
    fun updateTransaction(value: TransactionEntity) = launchAction {
        repository.updateTransaction(value)
        notifyWidgets()
    }
    fun addAccount(name: String, card: Boolean, opening: Long, maskedNumber: String = "", creditLimit: Long? = null, statementCycle: Int? = null, dueDay: Int? = null, minimumBalance: Long = 0) = launchAction {
        repository.addAccount(name, card, opening, maskedNumber, creditLimit, statementCycle, dueDay, minimumBalance)
        notifyWidgets()
    }
    fun addCategory(name: String, income: Boolean, colorHex: String? = null) = launchAction {
        repository.addCategory(name, income, colorHex)
        notifyWidgets()
    }
    fun updateAccount(account: AccountEntity) = launchAction {
        repository.updateAccount(account)
        notifyWidgets()
    }
    fun deleteAccount(account: AccountEntity) = launchAction {
        repository.deleteAccount(account)
        notifyWidgets()
    }
    fun updateCategory(category: CategoryEntity) = launchAction {
        repository.updateCategory(category)
        notifyWidgets()
    }
    fun deleteCategory(category: CategoryEntity) = launchAction {
        repository.deleteCategory(category)
        notifyWidgets()
    }
    fun saveBudget(categoryId: Long, monthKey: String, limitPaise: Long) = launchAction {
        repository.saveBudget(categoryId, monthKey, limitPaise)
        notifyWidgets()
    }
    fun ensureBudgetMonth(monthKey: String) = launchAction {
        repository.ensureBudgetMonth(monthKey)
        notifyWidgets()
    }
    fun deleteBudget(budget: BudgetEntity) = launchAction {
        repository.deleteBudget(budget)
        notifyWidgets()
    }
    fun addLoan(loan: LoanEntity) = launchAction { repository.addLoan(loan); notifyWidgets() }
    fun addOwed(owed: OwedEntity) = launchAction { repository.addOwed(owed); notifyWidgets() }
    fun createPerson(name: String) = launchAction { repository.createPerson(name) }
    fun renamePerson(personId: Long, name: String) = launchAction { repository.renamePerson(personId, name) }
    fun setPersonActive(personId: Long, active: Boolean) = launchAction { repository.setPersonActive(personId, active) }
    fun deletePerson(personId: Long) = launchAction { repository.deletePerson(personId) }
    fun updateLoan(loan: LoanEntity) = launchAction { repository.updateLoan(loan); notifyWidgets() }
    fun deleteLoan(loan: LoanEntity) = launchAction { repository.deleteLoan(loan); notifyWidgets() }
    fun updateOwed(owed: OwedEntity) = launchAction { repository.updateOwed(owed); notifyWidgets() }
    fun deleteOwed(owed: OwedEntity) = launchAction { repository.deleteOwed(owed); notifyWidgets() }
    fun settleOwed(owed: OwedEntity, amountPaise: Long) = launchAction { repository.settleOwed(owed, amountPaise); notifyWidgets() }
    fun settleOwed(owedId: Long, amountPaise: Long, accountId: Long, dateEpochDay: Long = java.time.LocalDate.now().toEpochDay(), settlementNote: String = "") = launchAction {
        repository.settleOwed(owedId, amountPaise, accountId, dateEpochDay, settlementNote)
        notifyWidgets()
    }
    fun repayLoan(loanId: Long, accountId: Long, amountPaise: Long, dateEpochDay: Long = java.time.LocalDate.now().toEpochDay(), note: String = "") = launchAction {
        repository.repayLoan(loanId, accountId, amountPaise, dateEpochDay, note)
        notifyWidgets()
    }
    fun postSavingsInterest(accountId: Long, amountPaise: Long, dateEpochDay: Long = java.time.LocalDate.now().toEpochDay()) = launchAction {
        repository.postSavingsInterest(accountId, amountPaise, dateEpochDay)
        notifyWidgets()
    }
    fun payCard(cardId: Long, sourceId: Long, amountPaise: Long, dateEpochDay: Long) = launchAction { repository.payCard(cardId, sourceId, amountPaise, dateEpochDay); notifyWidgets() }
    fun addRecurringTransaction(schedule: RecurringTransactionEntity) = launchAction { repository.addRecurringTransaction(schedule); notifyWidgets() }
    fun updateRecurringTransaction(schedule: RecurringTransactionEntity) = launchAction { repository.updateRecurringTransaction(schedule); notifyWidgets() }
    fun deleteRecurringTransaction(schedule: RecurringTransactionEntity) = launchAction { repository.deleteRecurringTransaction(schedule); notifyWidgets() }
    fun toggleRecurringTransaction(id: Long, isEnabled: Boolean) = launchAction { repository.toggleRecurringTransaction(id, isEnabled); notifyWidgets() }
    fun processDueRecurringTransactions() = launchAction { repository.processDueRecurringTransactions(); notifyWidgets() }
    fun exportBackup(onResult: (Result<String>) -> Unit) = viewModelScope.launch { runCatching { backupRepository.export() }.also(onResult) }
    fun restoreBackup(text: String, onResult: (Result<Unit>) -> Unit) = viewModelScope.launch { runCatching { withContext(Dispatchers.IO) { backupRepository.restore(text) } }.also { onResult(it); notifyWidgets() } }
}
