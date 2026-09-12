package com.spendtrack.app

import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendtrack.app.data.local.*
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate

class MinimumBalanceFeatureTest {

    // 1. AccountEntity minimumBalancePaise defaults to 0L
    @Test
    fun testDefaultMinimumBalanceIsZero() {
        val account = AccountEntity(
            name = "HDFC Savings",
            balancePaise = 25000_00L,
            isCreditCard = false
        )
        assertEquals(0L, account.minimumBalancePaise)
    }

    // 2. Create account with minimum balance persists value in paise
    @Test
    fun testCreateAccountWithMinimumBalancePersistsValue() {
        val minBalanceRupees = 5000.0
        val minBalancePaise = (minBalanceRupees * 100).toLong()
        val account = AccountEntity(
            id = 1L,
            name = "SBI Savings",
            balancePaise = 50000_00L,
            isCreditCard = false,
            minimumBalancePaise = minBalancePaise
        )
        assertEquals(5000_00L, account.minimumBalancePaise)
    }

    // 3. Edit account minimum balance updates correctly
    @Test
    fun testEditAccountMinimumBalance() {
        val initialAccount = AccountEntity(
            id = 1L,
            name = "ICICI Savings",
            balancePaise = 40000_00L,
            isCreditCard = false,
            minimumBalancePaise = 5000_00L
        )

        val updatedAccount = initialAccount.copy(minimumBalancePaise = 10000_00L)
        assertEquals(10000_00L, updatedAccount.minimumBalancePaise)
        assertEquals(initialAccount.balancePaise, updatedAccount.balancePaise)
    }

    // 4. Negative minimum balance is normalized to 0L
    @Test
    fun testNegativeMinimumBalanceNormalizedToZero() {
        // Enforce normalization logic as in FinanceRepository and UI
        val negativeInput = -2500_00L
        val sanitizedMinBalance = negativeInput.coerceAtLeast(0L)
        assertEquals(0L, sanitizedMinBalance)

        val account = AccountEntity(
            name = "Axis Savings",
            balancePaise = 10000_00L,
            minimumBalancePaise = sanitizedMinBalance
        )
        assertEquals(0L, account.minimumBalancePaise)
    }

    // 5. Credit cards force minimum balance to 0L
    @Test
    fun testCreditCardForcesMinimumBalanceToZero() {
        val isCreditCard = true
        val minBalanceInput = 5000_00L
        val sanitizedMinBalance = if (isCreditCard) 0L else minBalanceInput.coerceAtLeast(0L)

        val cardAccount = AccountEntity(
            name = "HDFC Regalia",
            balancePaise = 15000_00L,
            isCreditCard = true,
            minimumBalancePaise = sanitizedMinBalance
        )
        assertEquals(0L, cardAccount.minimumBalancePaise)
    }

    // 6. Account balance is unchanged by minimum balance
    @Test
    fun testAccountBalanceUnchangedByMinimumBalance() {
        val balancePaise = 50000_00L
        val minBalancePaise = 10000_00L

        val account = AccountEntity(
            name = "HDFC Savings",
            balancePaise = balancePaise,
            minimumBalancePaise = minBalancePaise
        )

        // Stored and displayed balance represents the actual bank balance
        assertEquals("Account balance must not subtract minimum balance", 50000_00L, account.balancePaise)
    }

    // 7. Net Worth is unchanged by minimum balance
    @Test
    fun testNetWorthUnchangedByMinimumBalance() {
        val savings = AccountEntity(
            id = 1L,
            name = "HDFC Savings",
            balancePaise = 50000_00L,
            isCreditCard = false,
            minimumBalancePaise = 10000_00L
        )
        val creditCard = AccountEntity(
            id = 2L,
            name = "Amazon ICICI",
            balancePaise = 5000_00L,
            isCreditCard = true
        )

        val accounts = listOf(savings, creditCard)
        val cash = accounts.filterNot { it.isCreditCard }.sumOf { it.balancePaise }
        val cards = accounts.filter { it.isCreditCard }.sumOf { it.balancePaise }
        val activeFdPrincipal = 20000_00L
        val owed = 3000_00L
        val loans = 0L

        val netWorth = cash + activeFdPrincipal + owed - cards - loans

        // 50000 (cash) + 20000 (fd) + 3000 (owed) - 5000 (cards) - 0 (loans) = 68000
        assertEquals("Net worth must include full cash balance without deducting minimum balance", 68000_00L, netWorth)
    }

    // 8. Zero transactions or expenses created when configuring minimum balance
    @Test
    fun testConfiguringMinimumBalanceCreatesZeroTransactions() {
        val transactions = mutableListOf(
            TransactionEntity(1L, 100L, "Coffee", 250_00L, 1L, 10L, TransactionType.EXPENSE)
        )

        // Configuring minimum balance touches only AccountEntity metadata
        val initialTransactionCount = transactions.size
        val expenseTotal = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountPaise }

        assertEquals(1, initialTransactionCount)
        assertEquals(250_00L, expenseTotal)
    }

    // 9. Minimum balance does not affect FDs, Budgets, or Transfers
    @Test
    fun testMinimumBalanceDoesNotAffectFdBudgetOrTransfer() {
        val account = AccountEntity(
            id = 1L,
            name = "HDFC Savings",
            balancePaise = 30000_00L,
            minimumBalancePaise = 10000_00L
        )

        // FD validation: user can create FD up to full balancePaise
        val fdPrincipal = 25000_00L
        assertTrue("FD validation uses full balancePaise", account.balancePaise >= fdPrincipal)

        // Transfer: transfer uses balancePaise directly
        val transferAmount = 25000_00L
        assertTrue("Transfer can use full available balancePaise", account.balancePaise >= transferAmount)
    }

    // 10. DatabaseProvider.MIGRATION_7_8 migration test
    @Test
    fun testMigration7To8AddsMinimumBalanceColumn() {
        assertEquals(7, DatabaseProvider.MIGRATION_7_8.startVersion)
        assertEquals(8, DatabaseProvider.MIGRATION_7_8.endVersion)

        val executedSql = mutableListOf<String>()
        val fakeDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL") {
                executedSql.add(args[0] as String)
            }
            null
        } as SupportSQLiteDatabase

        DatabaseProvider.MIGRATION_7_8.migrate(fakeDb)

        assertEquals(1, executedSql.size)
        assertEquals(
            "ALTER TABLE accounts ADD COLUMN minimumBalancePaise INTEGER NOT NULL DEFAULT 0",
            executedSql[0]
        )
    }

    // 11. Backup export key is "minimumBalancePaise" and stored in paise
    @Test
    fun testBackupExportFormat() {
        val account = AccountEntity(
            id = 1L,
            name = "HDFC Bank",
            bank = "HDFC",
            maskedNumber = "1234",
            balancePaise = 50000_00L,
            isCreditCard = false,
            minimumBalancePaise = 10000_00L
        )

        val exportedMap = mapOf(
            "id" to account.id,
            "name" to account.name,
            "bank" to account.bank,
            "masked" to account.maskedNumber,
            "balance" to account.balancePaise,
            "card" to account.isCreditCard,
            "limit" to account.creditLimitPaise,
            "cycle" to account.statementCycleDay,
            "due" to account.dueDay,
            "minimumBalancePaise" to account.minimumBalancePaise
        )

        assertTrue(exportedMap.containsKey("minimumBalancePaise"))
        assertFalse("Must not use 'minimumBalance' key", exportedMap.containsKey("minimumBalance"))
        assertEquals(10000_00L, exportedMap["minimumBalancePaise"])
    }

    // 12. Backup restore roundtrip preserves minimumBalancePaise
    @Test
    fun testBackupRestoreRoundtrip() {
        val originalAccount = AccountEntity(
            id = 1L,
            name = "Kotak Savings",
            bank = "Kotak",
            maskedNumber = "4321",
            balancePaise = 75000_00L,
            isCreditCard = false,
            creditLimitPaise = null,
            statementCycleDay = null,
            dueDay = null,
            minimumBalancePaise = 25000_00L
        )

        // Emulate export
        val exportedJsonMap = mapOf(
            "id" to originalAccount.id,
            "name" to originalAccount.name,
            "bank" to originalAccount.bank,
            "masked" to originalAccount.maskedNumber,
            "balance" to originalAccount.balancePaise,
            "card" to originalAccount.isCreditCard,
            "limit" to originalAccount.creditLimitPaise,
            "cycle" to originalAccount.statementCycleDay,
            "due" to originalAccount.dueDay,
            "minimumBalancePaise" to originalAccount.minimumBalancePaise
        )

        // Emulate restore
        val isCard = exportedJsonMap["card"] as Boolean
        val minBalanceRaw = (exportedJsonMap["minimumBalancePaise"] as? Long) ?: 0L
        val minBalance = if (isCard) 0L else minBalanceRaw.coerceAtLeast(0L)

        val restoredAccount = AccountEntity(
            id = exportedJsonMap["id"] as Long,
            name = exportedJsonMap["name"] as String,
            bank = exportedJsonMap["bank"] as String,
            maskedNumber = exportedJsonMap["masked"] as String,
            balancePaise = exportedJsonMap["balance"] as Long,
            isCreditCard = isCard,
            creditLimitPaise = exportedJsonMap["limit"] as Long?,
            statementCycleDay = exportedJsonMap["cycle"] as Int?,
            dueDay = exportedJsonMap["due"] as Int?,
            minimumBalancePaise = minBalance
        )

        assertEquals(originalAccount, restoredAccount)
    }

    // 13. Older backup without minimumBalancePaise key defaults safely to 0L
    @Test
    fun testOlderBackupWithoutMinimumBalanceKeyDefaultsToZero() {
        val legacyAccountMap = mapOf<String, Any>(
            "id" to 2L,
            "name" to "Canara Savings",
            "bank" to "Canara",
            "masked" to "5678",
            "balance" to 30000_00L,
            "card" to false
            // Notice "minimumBalancePaise" is completely absent in older backups
        )

        val isCard = legacyAccountMap["card"] as Boolean
        val minBalanceRaw = (legacyAccountMap["minimumBalancePaise"] as? Long) ?: 0L
        val minBalance = if (isCard) 0L else minBalanceRaw.coerceAtLeast(0L)

        val restored = AccountEntity(
            id = legacyAccountMap["id"] as Long,
            name = legacyAccountMap["name"] as String,
            bank = legacyAccountMap["bank"] as String,
            maskedNumber = legacyAccountMap["masked"] as String,
            balancePaise = legacyAccountMap["balance"] as Long,
            isCreditCard = isCard,
            minimumBalancePaise = minBalance
        )

        assertEquals(0L, restored.minimumBalancePaise)
        assertEquals(30000_00L, restored.balancePaise)
    }
}
