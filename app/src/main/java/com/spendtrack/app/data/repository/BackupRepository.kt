package com.spendtrack.app.data.repository

import androidx.room.withTransaction
import com.spendtrack.app.data.local.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Portable, versioned JSON backup. IDs are preserved so every local relationship survives restore. */
class BackupRepository(private val database: SpendTrackDatabase) {
    suspend fun export(): String = database.withTransaction {
        JSONObject().apply {
            put("format", "spendtrack-backup"); put("version", 1)
            put("accounts", JSONArray().also { array -> database.accounts().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("name",e.name); put("bank",e.bank); put("masked",e.maskedNumber); put("balance",e.balancePaise); put("card",e.isCreditCard); put("limit",e.creditLimitPaise); put("cycle",e.statementCycleDay); put("due",e.dueDay); put("minimumBalancePaise",e.minimumBalancePaise) }) } })
            put("categories", JSONArray().also { array -> database.categories().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("name",e.name); put("income",e.isIncomeCategory); put("fallback",e.isSystemFallback) }) } })
            put("transactions", JSONArray().also { array -> database.transactions().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("date",e.dateEpochDay); put("description",e.description); put("amount",e.amountPaise); put("category",e.categoryId); put("account",e.accountId); put("type",e.type.name); put("shared",e.isShared); put("share",e.mySharePaise); put("sharedWith",e.sharedWith); put("destAccount",e.destinationAccountId) }) } })
            put("budgets", JSONArray().also { array -> database.budgets().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("category",e.categoryId); put("month",e.monthKey); put("limit",e.limitPaise) }) } })
            put("loans", JSONArray().also { array -> database.loans().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("name",e.name); put("principal",e.originalPrincipalPaise); put("outstanding",e.currentOutstandingPaise); put("rate",e.interestRateBps); put("emi",e.emiPaise); put("due",e.nextDueEpochDay); put("tenure",e.remainingTenureMonths); put("paidPrincipal",e.principalPaidPaise); put("paidInterest",e.interestPaidPaise); put("netWorth",e.includeInNetWorth) }) } })
            put("owed", JSONArray().also { array -> database.owed().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("person",e.personName); put("amount",e.amountPaise); put("direction",e.direction.name); put("note",e.note); put("source",e.sourceTransactionId) }) } })
            put("cardPayments", JSONArray().also { array -> database.cardPayments().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("card",e.cardAccountId); put("source",e.sourceAccountId); put("amount",e.amountPaise); put("date",e.dateEpochDay) }) } })
            put("fixedDeposits", JSONArray().also { array -> database.fixedDeposits().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("name",e.name); put("accountId",e.linkedAccountId); put("principal",e.principalPaise); put("rate",e.interestRateBps); put("startDate",e.startDateEpochDay); put("maturityDate",e.maturityDateEpochDay); put("maturityAmount",e.maturityAmountPaise); put("description",e.description); put("status",e.status.name); put("compoundingFrequency",e.compoundingFrequency.name); put("isExisting",e.isExisting) }) } })
            put("recurringTransactions", JSONArray().also { array -> database.recurringTransactions().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("description",e.description); put("amount",e.amountPaise); put("category",e.categoryId); put("account",e.accountId); put("type",e.type.name); put("frequency",e.frequency.name); put("startDate",e.startDateEpochDay); put("endDate",e.endDateEpochDay); put("nextOccurrence",e.nextOccurrenceEpochDay); put("isEnabled",e.isEnabled); put("isShared",e.isShared); put("share",e.mySharePaise); put("sharedWith",e.sharedWith); put("destAccount",e.destinationAccountId) }) } })
            put("recurringOccurrences", JSONArray().also { array -> database.recurringOccurrences().getAll().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("scheduleId",e.scheduleId); put("occurrenceDate",e.occurrenceEpochDay); put("transactionId",e.transactionId) }) } })
        }.toString(2)
    }

    suspend fun restore(text: String) {
        val root = JSONObject(text)
        require(root.optString("format") == "spendtrack-backup" && root.optInt("version") == 1) { "Unsupported backup file" }
        fun array(name: String) = root.optJSONArray(name) ?: JSONArray()
        database.withTransaction {
            database.recurringOccurrences().clear(); database.recurringTransactions().clear(); database.fixedDeposits().clear(); database.cardPayments().clear(); database.transactions().clear(); database.budgets().clear(); database.loans().clear(); database.owed().clear(); database.accounts().clear(); database.categories().clear()
            database.accounts().insertAll(array("accounts").objects { o ->
                val isCard = o.getBoolean("card")
                val minBalance = if (isCard) 0L else o.optLong("minimumBalancePaise", 0L).coerceAtLeast(0L)
                AccountEntity(o.getLong("id"), o.getString("name"), o.optString("bank"), o.optString("masked"), o.getLong("balance"), isCard, o.longOrNull("limit"), o.intOrNull("cycle"), o.intOrNull("due"), minBalance)
            })
            database.categories().insertAll(array("categories").objects { o -> CategoryEntity(o.getLong("id"), o.getString("name"), o.getBoolean("income"), o.getBoolean("fallback")) })
            database.transactions().insertAll(array("transactions").objects { o ->
                val type = runCatching { TransactionType.valueOf(o.getString("type")) }.getOrDefault(TransactionType.EXPENSE)
                TransactionEntity(o.getLong("id"),o.getLong("date"),o.getString("description"),o.getLong("amount"),o.getLong("category"),o.getLong("account"),type,o.getBoolean("shared"),o.longOrNull("share"),o.optString("sharedWith"),o.longOrNull("destAccount"))
            })
            database.budgets().insertAll(array("budgets").objects { o -> BudgetEntity(o.getLong("id"),o.getLong("category"),o.getString("month"),o.getLong("limit")) })
            database.loans().insertAll(array("loans").objects { o -> LoanEntity(o.getLong("id"),o.getString("name"),o.getLong("principal"),o.getLong("outstanding"),o.getInt("rate"),o.getLong("emi"),o.getLong("due"),o.getInt("tenure"),o.getLong("paidPrincipal"),o.getLong("paidInterest"),o.getBoolean("netWorth")) })
            database.owed().insertAll(array("owed").objects { o ->
                val direction = runCatching { OwedDirection.valueOf(o.getString("direction")) }.getOrDefault(OwedDirection.OWED_TO_ME)
                OwedEntity(o.getLong("id"),o.getString("person"),o.getLong("amount"),direction,o.optString("note"),o.longOrNull("source"))
            })
            database.cardPayments().insertAll(array("cardPayments").objects { o -> CardPaymentEntity(o.getLong("id"),o.getLong("card"),o.getLong("source"),o.getLong("amount"),o.getLong("date")) })
            database.fixedDeposits().insertAll(array("fixedDeposits").objects { o ->
                val freq = runCatching { CompoundingFrequency.valueOf(o.optString("compoundingFrequency", "QUARTERLY")) }.getOrDefault(CompoundingFrequency.QUARTERLY)
                val status = runCatching { FixedDepositStatus.valueOf(o.optString("status", "ACTIVE")) }.getOrDefault(FixedDepositStatus.ACTIVE)
                val isExisting = o.optBoolean("isExisting", false)
                FixedDepositEntity(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    linkedAccountId = o.getLong("accountId"),
                    principalPaise = o.getLong("principal"),
                    interestRateBps = o.getInt("rate"),
                    startDateEpochDay = o.getLong("startDate"),
                    maturityDateEpochDay = o.getLong("maturityDate"),
                    maturityAmountPaise = o.getLong("maturityAmount"),
                    description = o.optString("description", ""),
                    status = status,
                    compoundingFrequency = freq,
                    isExisting = isExisting
                )
            })
            database.recurringTransactions().insertAll(array("recurringTransactions").objects { o ->
                val type = runCatching { TransactionType.valueOf(o.getString("type")) }.getOrDefault(TransactionType.EXPENSE)
                val frequency = runCatching { RecurrenceFrequency.valueOf(o.getString("frequency")) }.getOrDefault(RecurrenceFrequency.MONTHLY)
                RecurringTransactionEntity(
                    id = o.getLong("id"),
                    description = o.getString("description"),
                    amountPaise = o.getLong("amount"),
                    categoryId = o.getLong("category"),
                    accountId = o.getLong("account"),
                    type = type,
                    frequency = frequency,
                    startDateEpochDay = o.getLong("startDate"),
                    endDateEpochDay = o.longOrNull("endDate"),
                    nextOccurrenceEpochDay = o.getLong("nextOccurrence"),
                    isEnabled = o.optBoolean("isEnabled", true),
                    isShared = o.optBoolean("isShared", false),
                    mySharePaise = o.longOrNull("share"),
                    sharedWith = o.optString("sharedWith", ""),
                    destinationAccountId = o.longOrNull("destAccount")
                )
            })
            database.recurringOccurrences().insertAll(array("recurringOccurrences").objects { o ->
                RecurringOccurrenceEntity(
                    id = o.getLong("id"),
                    scheduleId = o.getLong("scheduleId"),
                    occurrenceEpochDay = o.getLong("occurrenceDate"),
                    transactionId = o.getLong("transactionId")
                )
            })
        }
    }

    private fun <T> JSONArray.objects(map: (JSONObject) -> T): List<T> = List(length()) { map(getJSONObject(it)) }
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
}
