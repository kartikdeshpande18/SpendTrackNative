package com.spendtrack.app.data.repository

import androidx.room.withTransaction
import com.spendtrack.app.data.local.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

/** Portable, versioned JSON backup. IDs are preserved so every local relationship survives restore. */
class BackupRepository(private val database: SpendTrackDatabase) {
    suspend fun export(): String = JSONObject().apply {
        put("format", "spendtrack-backup"); put("version", 1)
        put("accounts", JSONArray().also { array -> database.accounts().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("name",e.name); put("bank",e.bank); put("masked",e.maskedNumber); put("balance",e.balancePaise); put("card",e.isCreditCard); put("limit",e.creditLimitPaise); put("cycle",e.statementCycleDay); put("due",e.dueDay) }) } })
        put("categories", JSONArray().also { array -> database.categories().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("name",e.name); put("income",e.isIncomeCategory); put("fallback",e.isSystemFallback) }) } })
        put("transactions", JSONArray().also { array -> database.transactions().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("date",e.dateEpochDay); put("description",e.description); put("amount",e.amountPaise); put("category",e.categoryId); put("account",e.accountId); put("type",e.type.name); put("shared",e.isShared); put("share",e.mySharePaise); put("sharedWith",e.sharedWith) }) } })
        put("budgets", JSONArray().also { array -> database.budgets().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("category",e.categoryId); put("month",e.monthKey); put("limit",e.limitPaise) }) } })
        put("loans", JSONArray().also { array -> database.loans().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("name",e.name); put("principal",e.originalPrincipalPaise); put("outstanding",e.currentOutstandingPaise); put("rate",e.interestRateBps); put("emi",e.emiPaise); put("due",e.nextDueEpochDay); put("tenure",e.remainingTenureMonths); put("paidPrincipal",e.principalPaidPaise); put("paidInterest",e.interestPaidPaise); put("netWorth",e.includeInNetWorth) }) } })
        put("owed", JSONArray().also { array -> database.owed().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("person",e.personName); put("amount",e.amountPaise); put("direction",e.direction.name); put("note",e.note); put("source",e.sourceTransactionId) }) } })
        put("cardPayments", JSONArray().also { array -> database.cardPayments().observeAll().first().forEach { e -> array.put(JSONObject().apply { put("id",e.id); put("card",e.cardAccountId); put("source",e.sourceAccountId); put("amount",e.amountPaise); put("date",e.dateEpochDay) }) } })
    }.toString(2)

    suspend fun restore(text: String) {
        val root = JSONObject(text)
        require(root.optString("format") == "spendtrack-backup" && root.optInt("version") == 1) { "Unsupported backup file" }
        fun array(name: String) = root.optJSONArray(name) ?: JSONArray()
        database.withTransaction {
            database.cardPayments().clear(); database.transactions().clear(); database.budgets().clear(); database.loans().clear(); database.owed().clear(); database.accounts().clear(); database.categories().clear()
            database.accounts().insertAll(array("accounts").objects { o -> AccountEntity(o.getLong("id"), o.getString("name"), o.optString("bank"), o.optString("masked"), o.getLong("balance"), o.getBoolean("card"), o.longOrNull("limit"), o.intOrNull("cycle"), o.intOrNull("due")) })
            database.categories().insertAll(array("categories").objects { o -> CategoryEntity(o.getLong("id"), o.getString("name"), o.getBoolean("income"), o.getBoolean("fallback")) })
            database.transactions().insertAll(array("transactions").objects { o -> TransactionEntity(o.getLong("id"),o.getLong("date"),o.getString("description"),o.getLong("amount"),o.getLong("category"),o.getLong("account"),TransactionType.valueOf(o.getString("type")),o.getBoolean("shared"),o.longOrNull("share"),o.optString("sharedWith")) })
            database.budgets().insertAll(array("budgets").objects { o -> BudgetEntity(o.getLong("id"),o.getLong("category"),o.getString("month"),o.getLong("limit")) })
            database.loans().insertAll(array("loans").objects { o -> LoanEntity(o.getLong("id"),o.getString("name"),o.getLong("principal"),o.getLong("outstanding"),o.getInt("rate"),o.getLong("emi"),o.getLong("due"),o.getInt("tenure"),o.getLong("paidPrincipal"),o.getLong("paidInterest"),o.getBoolean("netWorth")) })
            database.owed().insertAll(array("owed").objects { o -> OwedEntity(o.getLong("id"),o.getString("person"),o.getLong("amount"),OwedDirection.valueOf(o.getString("direction")),o.optString("note"),o.longOrNull("source")) })
            database.cardPayments().insertAll(array("cardPayments").objects { o -> CardPaymentEntity(o.getLong("id"),o.getLong("card"),o.getLong("source"),o.getLong("amount"),o.getLong("date")) })
        }
    }

    private fun <T> JSONArray.objects(map: (JSONObject) -> T): List<T> = List(length()) { map(getJSONObject(it)) }
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
    private fun JSONObject.intOrNull(key: String): Int? = if (isNull(key)) null else getInt(key)
}
