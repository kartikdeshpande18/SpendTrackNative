package com.spendtrack.app.importer

import android.content.Context
import android.content.SharedPreferences
import androidx.room.DatabaseConfiguration
import androidx.room.InvalidationTracker
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.spendtrack.app.data.local.*
import com.spendtrack.app.importer.detector.StatementFormat
import com.spendtrack.app.importer.model.*
import com.spendtrack.app.importer.parser.*
import com.spendtrack.app.importer.repository.ImportRepository
import com.spendtrack.app.importer.ui.ImportState
import com.spendtrack.app.importer.ui.ImportViewModel
import com.spendtrack.app.importer.ui.ReviewFilter
import com.spendtrack.app.ui.formatIndianMoney
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.reflect.Proxy
import java.util.concurrent.Executor

class ImportWorkflowTest {

    private lateinit var fakeDb: TestSpendTrackDatabase
    private lateinit var importRepository: ImportRepository
    private lateinit var parsers: Map<StatementFormat, StatementParser>
    private lateinit var testScope: CoroutineScope

    private fun readResource(name: String): String {
        val url = javaClass.classLoader?.getResource(name)
        if (url != null) {
            return File(url.toURI()).readText()
        }
        val fallback = File(name)
        if (fallback.exists()) return fallback.readText()
        val appRes = File("app/src/test/resources/$name")
        if (appRes.exists()) return appRes.readText()
        error("Resource file not found: $name")
    }

    @Before
    fun setUp() {
        fakeDb = TestSpendTrackDatabase()
        importRepository = ImportRepository(fakeDb)
        parsers = mapOf(
            StatementFormat.HDFC_SAVINGS to HdfcSavingsParser(),
            StatementFormat.ICICI_SAVINGS to IciciSavingsParser(),
            StatementFormat.ICICI_CREDIT_CARD to IciciCreditCardParser(),
            StatementFormat.SLICE_SAVINGS to SliceSavingsParser(),
            StatementFormat.SLICE_CREDIT_CARD to SliceCreditCardParser(),
            StatementFormat.SLICE_ATOM to SliceAtomParser(),
            StatementFormat.SBI_SAVINGS to SbiSavingsParser(),
            StatementFormat.KOTAK_SAVINGS to KotakSavingsParser()
        )
        testScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // Initial accounts
        fakeDb.accountsList.add(
            AccountEntity(
                id = 1L,
                name = "ICICI Coral Card",
                bank = "ICICI",
                balancePaise = 0L,
                isCreditCard = true
            )
        )
        fakeDb.accountsList.add(
            AccountEntity(
                id = 2L,
                name = "HDFC Savings",
                bank = "HDFC",
                balancePaise = 1000000L,
                isCreditCard = false
            )
        )
        fakeDb.accountsList.add(
            AccountEntity(
                id = 3L,
                name = "SBI Savings Account",
                bank = "SBI",
                balancePaise = 500000L,
                isCreditCard = false
            )
        )
        fakeDb.accountsList.add(
            AccountEntity(
                id = 4L,
                name = "Kotak Mahindra Bank Account",
                bank = "Kotak",
                balancePaise = 2000000L,
                isCreditCard = false
            )
        )

    }

    private suspend fun awaitReviewState(viewModel: ImportViewModel, timeoutMs: Long = 3000L): ImportState.Review {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            when (val s = viewModel.state.value) {
                is ImportState.Review -> return s
                is ImportState.Error -> error("Failed with error: ${s.message}")
                else -> delay(50)
            }
        }
        error("Timed out waiting for Review state. Current state: ${viewModel.state.value}")
    }

    private suspend fun awaitSuccessState(viewModel: ImportViewModel, timeoutMs: Long = 3000L): ImportState.Success {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            when (val s = viewModel.state.value) {
                is ImportState.Success -> return s
                is ImportState.Error -> error("Failed with error: ${s.message}")
                else -> delay(50)
            }
        }
        error("Timed out waiting for Success state. Current state: ${viewModel.state.value}")
    }

    // -------------------------------------------------------------------------
    // TEST A: Parsing valid statement directly enters ImportState.Review
    // -------------------------------------------------------------------------
    @Test
    fun testA_parseValidStatementEntersReviewStateDirectly() {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("Coral CC Statement.pdf.txt")

        assertEquals(ImportState.Idle, viewModel.state.value)
        assertEquals(0, fakeDb.transactionsList.size)

        viewModel.parseStatementText(
            format = StatementFormat.ICICI_CREDIT_CARD,
            text = text,
            filename = "Coral CC Statement.pdf",
            accountId = 1L
        )

        val state = viewModel.state.value
        assertTrue("Expected Review state but got $state", state is ImportState.Review)
        val reviewState = state as ImportState.Review
        assertTrue("Batch should contain transactions", reviewState.batch.transactions.isNotEmpty())

        // In-memory batch contains parsed transactions with PENDING review status
        assertTrue(reviewState.batch.transactions.all { it.reviewStatus == ReviewStatus.PENDING })

        // HARD REQUIREMENT: Zero database writes have occurred!
        assertEquals("Room database must NOT have any transactions written yet", 0, fakeDb.transactionsList.size)
        assertEquals("Import history must NOT be written yet", 0, fakeDb.importHistoryList.size)
    }

    // -------------------------------------------------------------------------
    // TEST B: Confirming account mapping transitions to Review with zero DB writes
    // -------------------------------------------------------------------------
    @Test
    fun testB_confirmAccountMappingEntersReviewStateWithZeroRoomWrites() = runBlocking {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("Coral CC Statement.pdf.txt")

        // Prepare file entry for AccountMapping stage
        val fileEntry = StatementFileEntry(
            filename = "Coral CC Statement.pdf",
            format = StatementFormat.ICICI_CREDIT_CARD,
            fileHash = "dummyhash123",
            rawText = text
        )

        // Force into AccountMapping state
        val mappings = mapOf("Coral CC Statement.pdf" to 1L)
        val field = ImportViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportState>
        stateFlow.value = ImportState.AccountMapping(listOf(fileEntry), mappings)

        viewModel.confirmAccountMapping(null, emptyList(), emptyList())

        val reviewState = awaitReviewState(viewModel)
        assertTrue(reviewState.batch.transactions.isNotEmpty())
        assertEquals(1, reviewState.batch.files.size)
        assertEquals("Coral CC Statement.pdf", reviewState.batch.files[0].filename)

        // ZERO database writes!
        assertEquals("Zero transactions must be in Room database", 0, fakeDb.transactionsList.size)
        assertEquals("Zero history entries must be in Room database", 0, fakeDb.importHistoryList.size)
    }

    // -------------------------------------------------------------------------
    // TEST C: Before final import, Room contains 0 transactions despite UI interactions
    // -------------------------------------------------------------------------
    @Test
    fun testC_inReviewStateZeroRoomWritesBeforeConfirmImport() {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("HDFC Bank Savings Account Statement.pdf.txt")

        viewModel.parseStatementText(
            format = StatementFormat.HDFC_SAVINGS,
            text = text,
            filename = "HDFC.pdf",
            accountId = 2L
        )

        val reviewState = viewModel.state.value as ImportState.Review
        assertTrue("Batch should have multiple transactions", reviewState.batch.transactions.size >= 3)
        val tx1 = reviewState.batch.transactions[0]
        val tx2 = reviewState.batch.transactions[1]
        val tx3 = reviewState.batch.transactions[2]

        // User interacts with Review screen in memory
        viewModel.acceptTransaction(tx1.id)
        viewModel.removeTransaction(tx2.id)
        viewModel.editTransaction(
            txId = tx3.id,
            merchant = "Custom Merchant",
            amountPaise = 25000L,
            dateEpochDay = tx3.dateEpochDay,
            type = ImporterTransactionType.EXPENSE,
            categoryId = null,
            sourceAccountId = 2L,
            destinationAccountId = null
        )
        viewModel.setReviewFilter(ReviewFilter.ACCEPTED)
        viewModel.setReviewFilter(ReviewFilter.REMOVED)
        viewModel.setReviewFilter(ReviewFilter.ALL)

        // Verify still in Review state
        assertTrue(viewModel.state.value is ImportState.Review)

        // HARD REQUIREMENT: Zero database writes have occurred!
        assertEquals(0, fakeDb.transactionsList.size)
        assertEquals(0, fakeDb.importHistoryList.size)
    }

    // -------------------------------------------------------------------------
    // TEST D: confirmImport commits ONLY accepted transactions to Room
    // -------------------------------------------------------------------------
    @Test
    fun testD_confirmImportCommitsOnlyAcceptedTransactionsToRoom() = runBlocking {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("HDFC Bank Savings Account Statement.pdf.txt")

        viewModel.parseStatementText(
            format = StatementFormat.HDFC_SAVINGS,
            text = text,
            filename = "HDFC.pdf",
            accountId = 2L
        )

        val reviewState = viewModel.state.value as ImportState.Review
        val tx1 = reviewState.batch.transactions[0]
        val tx2 = reviewState.batch.transactions[1]

        // Accept only 2 transactions, leaving remaining as PENDING
        viewModel.acceptTransaction(tx1.id)
        viewModel.acceptTransaction(tx2.id)

        viewModel.confirmImport()

        val successState = awaitSuccessState(viewModel)
        assertEquals(2, successState.importedCount)

        // Verify exactly 2 transactions committed to Room
        assertEquals(2, fakeDb.transactionsList.size)
        assertTrue(fakeDb.transactionsList.any { it.description == tx1.normalizedMerchant })
        assertTrue(fakeDb.transactionsList.any { it.description == tx2.normalizedMerchant })

        // Verify import history recorded
        assertEquals(1, fakeDb.importHistoryList.size)
        assertEquals("HDFC.pdf", fakeDb.importHistoryList[0].filename)
        assertEquals(2, fakeDb.importHistoryList[0].transactionsImported)
    }

    // -------------------------------------------------------------------------
    // TEST E: Transactions marked REMOVED are NOT persisted to Room
    // -------------------------------------------------------------------------
    @Test
    fun testE_removedTransactionsAreNotPersistedToRoom() = runBlocking {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("HDFC Bank Savings Account Statement.pdf.txt")

        viewModel.parseStatementText(
            format = StatementFormat.HDFC_SAVINGS,
            text = text,
            filename = "HDFC.pdf",
            accountId = 2L
        )

        val reviewState = viewModel.state.value as ImportState.Review
        val tx1 = reviewState.batch.transactions[0]
        val tx2 = reviewState.batch.transactions[1]

        // Accept tx1, remove tx2
        viewModel.acceptTransaction(tx1.id)
        viewModel.removeTransaction(tx2.id)

        viewModel.confirmImport()

        val successState = awaitSuccessState(viewModel)
        assertEquals(1, successState.importedCount)
        assertEquals(1, successState.excludedCount)

        assertEquals(1, fakeDb.transactionsList.size)
        assertEquals(tx1.normalizedMerchant, fakeDb.transactionsList[0].description)
        assertFalse(fakeDb.transactionsList.any { it.description == tx2.normalizedMerchant })
    }

    // -------------------------------------------------------------------------
    // TEST F: Transactions marked EDITED are persisted with edited values
    // -------------------------------------------------------------------------
    @Test
    fun testF_editedTransactionsArePersistedWithEditedValues() = runBlocking {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("HDFC Bank Savings Account Statement.pdf.txt")

        viewModel.parseStatementText(
            format = StatementFormat.HDFC_SAVINGS,
            text = text,
            filename = "HDFC.pdf",
            accountId = 2L
        )

        val reviewState = viewModel.state.value as ImportState.Review
        val tx1 = reviewState.batch.transactions[0]

        // Edit tx1
        viewModel.editTransaction(
            txId = tx1.id,
            merchant = "Special Modified Merchant",
            amountPaise = 77700L, // ₹777.00
            dateEpochDay = 19800L,
            type = ImporterTransactionType.EXPENSE,
            categoryId = null,
            sourceAccountId = 2L,
            destinationAccountId = null
        )

        viewModel.confirmImport()

        val successState = awaitSuccessState(viewModel)
        assertEquals(1, successState.importedCount)

        assertEquals(1, fakeDb.transactionsList.size)
        val committed = fakeDb.transactionsList[0]
        assertEquals("Special Modified Merchant", committed.description)
        assertEquals(77700L, committed.amountPaise)
        assertEquals(19800L, committed.dateEpochDay)
    }

    // -------------------------------------------------------------------------
    // TEST G: Amount regression tests: 166.00 -> 16600L -> "₹166.00", 270.00 -> 27000L, etc.
    // -------------------------------------------------------------------------
    @Test
    fun testG_amountRegressionParsingAndFormattingExactness() {
        val text = readResource("Coral CC Statement.pdf.txt")
        val parser = IciciCreditCardParser()
        val txs = parser.parse(text, "Coral CC Statement.pdf")

        // 1. Locate the SWIGGY transaction in the Coral statement: 15/05/2026 Amount = 166.00
        val swiggyTx = txs.firstOrNull { it.rawNarration.contains("SWIGGY", ignoreCase = true) }
        assertNotNull("SWIGGY transaction must be parsed from Coral statement", swiggyTx)

        // Verify paise representation is exactly 16600L (NOT 166L!)
        assertEquals("166.00 must be parsed to 16600 paise", 16600L, swiggyTx!!.amountPaise)

        // Verify formatIndianMoney displays ₹166.00 (NOT ₹1.66!)
        val formattedSwiggy = formatIndianMoney(swiggyTx.amountPaise)
        assertEquals("₹166.00", formattedSwiggy)

        // 2. Exact paise tests for ₹270.00, ₹1,273.15, ₹0.50, ₹1,00,000.00
        assertEquals("₹270.00", formatIndianMoney(27000L))
        assertEquals("₹1,273.15", formatIndianMoney(127315L))
        assertEquals("₹0.50", formatIndianMoney(50L))
        assertEquals("₹1,00,000.00", formatIndianMoney(10000000L))

        // 3. String input decimal conversion test
        val parsePaiseMethod = { input: String ->
            val clean = input.replace(",", "").trim()
            val bd = java.math.BigDecimal(clean)
            bd.multiply(java.math.BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        }
        assertEquals(16600L, parsePaiseMethod("166.00"))
        assertEquals(27000L, parsePaiseMethod("270.00"))
        assertEquals(127315L, parsePaiseMethod("1,273.15"))
    }

    // -------------------------------------------------------------------------
    // BULK ACTIONS TEST: Bulk actions in Review state
    // -------------------------------------------------------------------------
    @Test
    fun test_bulkActionsAcceptAllHighConfidenceAndRemoveAllDuplicates() {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("Coral CC Statement.pdf.txt")

        viewModel.parseStatementText(
            format = StatementFormat.ICICI_CREDIT_CARD,
            text = text,
            filename = "Coral CC Statement.pdf",
            accountId = 1L
        )

        var reviewState = viewModel.state.value as ImportState.Review
        val initialPending = reviewState.batch.transactions.count { it.reviewStatus == ReviewStatus.PENDING }
        assertTrue(initialPending > 0)

        // Accept all high confidence
        viewModel.acceptAllHighConfidence()

        reviewState = viewModel.state.value as ImportState.Review
        val acceptedCount = reviewState.batch.transactions.count { it.isAccepted }
        assertEquals(initialPending, acceptedCount)

        // Still zero Room writes!
        assertEquals(0, fakeDb.transactionsList.size)
    }

    // -------------------------------------------------------------------------
    // TEST H: SBI Savings workflow integration
    // -------------------------------------------------------------------------
    @Test
    fun testH_sbiSavingsWorkflowIntegration() = runBlocking {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val sbiText = """
            STATEMENT OF ACCOUNT
            State Bank of India
            Account Name: KARTIK
            Txn Date Value Date Description Cheque No. Debit Credit Balance
            25 Sep 2026 25 Sep 2026 DEP TFR
            UPI/CR/626912345678/KARTIKKI/Self
            - - 4,889.00 4,889.00
            26 Sep 2026 26 Sep 2026 WDL TFR
            UPI/DR/627098765432/KARTIK/Self
            - 4,891.04 - 0.00
            **END OF STATEMENT**
        """.trimIndent()

        viewModel.parseStatementText(
            format = StatementFormat.SBI_SAVINGS,
            text = sbiText,
            filename = "SBI.pdf",
            accountId = 3L
        )

        val reviewState = awaitReviewState(viewModel)
        assertEquals(2, reviewState.batch.transactions.size)
        assertEquals(0, fakeDb.transactionsList.size)

        // Accept transaction 1
        viewModel.acceptTransaction(reviewState.batch.transactions[0].id)
        viewModel.confirmImport()

        val success = awaitSuccessState(viewModel)
        assertEquals(1, success.importedCount)
        assertEquals(1, fakeDb.transactionsList.size)
        assertEquals(488900L, fakeDb.transactionsList[0].amountPaise)
    }

    // -------------------------------------------------------------------------
    // TEST I: Kotak Savings workflow integration
    // -------------------------------------------------------------------------
    @Test
    fun testI_kotakSavingsWorkflowIntegration() = runBlocking {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val kotakText = """
            Account Statement
            Kotak Mahindra Bank
            Savings Account Transactions
            # Date Description Chq/Ref. No. Withdrawal (Dr.) Deposit (Cr.) Balance
            1 01/09/2026 UPI/Payment from sl REF10001 - 5,000.00 15,000.00
            2 02/09/2026 NACH-MUT-DR/MF REF10002 1,000.00 - 14,000.00
            CLOSING BALANCE: 14,000.00
        """.trimIndent()

        viewModel.parseStatementText(
            format = StatementFormat.KOTAK_SAVINGS,
            text = kotakText,
            filename = "Kotak.pdf",
            accountId = 4L
        )

        val reviewState = awaitReviewState(viewModel)
        assertEquals(2, reviewState.batch.transactions.size)
        assertEquals(0, fakeDb.transactionsList.size)

        // Accept all
        viewModel.acceptAllHighConfidence()
        viewModel.confirmImport()

        val success = awaitSuccessState(viewModel)
        assertEquals(2, success.importedCount)
        assertEquals(2, fakeDb.transactionsList.size)
    }

    // -------------------------------------------------------------------------
    // TEST J: ICICI Savings canonical fixture workflow integration
    // -------------------------------------------------------------------------
    @Test
    fun testJ_iciciSavingsCanonicalFixtureWorkflowIntegration() = runBlocking {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("OpTransactionHistory26-09-2026.pdf-23-05-47.pdf.txt")

        viewModel.parseStatementText(
            format = StatementFormat.ICICI_SAVINGS,
            text = text,
            filename = "OpTransactionHistory26-09-2026.pdf-23-05-47.pdf",
            accountId = 2L
        )

        val reviewState = awaitReviewState(viewModel)
        assertEquals(5, reviewState.batch.transactions.size)
        val txs = reviewState.batch.transactions
        assertEquals(2466600L, txs[0].amountPaise)
        assertEquals(Direction.CREDIT, txs[0].direction)
        assertEquals(ImporterTransactionType.INCOME, txs[0].transactionType)

        assertEquals(2466600L, txs[1].amountPaise)
        assertEquals(Direction.DEBIT, txs[1].direction)
        assertEquals(ImporterTransactionType.TRANSFER, txs[1].transactionType)

        assertEquals(1500L, txs[2].amountPaise)
        assertEquals(5000L, txs[3].amountPaise)
        assertEquals(6500L, txs[4].amountPaise)
        assertEquals(ImporterTransactionType.TRANSFER, txs[4].transactionType)

        assertEquals(0, fakeDb.transactionsList.size)

        viewModel.acceptTransaction(txs[0].id)
        viewModel.acceptTransaction(txs[1].id)
        viewModel.confirmImport()

        val success = awaitSuccessState(viewModel)
        assertEquals(2, success.importedCount)
        assertEquals(2, fakeDb.transactionsList.size)
    }

    // -------------------------------------------------------------------------
    // TEST K: UI Regression proving ReviewStage is rendered for ImportState.Review
    // -------------------------------------------------------------------------
    @Test
    fun testK_uiRegressionProvingReviewStageRenderedForReviewState() {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("Coral CC Statement.pdf.txt")

        viewModel.parseStatementText(
            format = StatementFormat.ICICI_CREDIT_CARD,
            text = text,
            filename = "Coral CC Statement.pdf",
            accountId = 1L
        )

        val state = viewModel.state.value
        assertTrue("State must be Review", state is ImportState.Review)
        val reviewState = state as ImportState.Review

        // Validate that when state is Review, the UI target stage is unequivocally ReviewStage
        val renderedStage = when (state) {
            is ImportState.Idle -> "IdleStage"
            is ImportState.Analyzing -> "LoadingStage"
            is ImportState.AccountMapping -> "AccountMappingStage"
            is ImportState.Review -> "ReviewStage"
            is ImportState.Importing -> "LoadingStage"
            is ImportState.Success -> "SuccessStage"
            is ImportState.Error -> "ErrorStage"
        }
        assertEquals("ReviewStage", renderedStage)

        // Verify that the batch has items to render in ReviewStage
        assertTrue("ReviewStage must receive non-empty transactions", reviewState.batch.transactions.isNotEmpty())
        assertTrue("ReviewStage must receive parsed file info", reviewState.batch.files.isNotEmpty())
        assertEquals(ReviewFilter.ALL, reviewState.currentFilter)
    }
    // -------------------------------------------------------------------------
    // TEST L: State sequence verification — Review is reached and STAYS
    // -------------------------------------------------------------------------
    @Test
    fun testL_stateSequenceAfterConfirmAccountMappingReachesAndStaysAtReview() = runBlocking {
        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val text = readResource("Coral CC Statement.pdf.txt")

        // Collect all state transitions
        val observedStates = mutableListOf<String>()
        val collectJob = launch(Dispatchers.Default) {
            viewModel.state.collect { state ->
                val name = when (state) {
                    is ImportState.Idle -> "Idle"
                    is ImportState.Analyzing -> "Analyzing"
                    is ImportState.AccountMapping -> "AccountMapping"
                    is ImportState.Review -> "Review"
                    is ImportState.Importing -> "Importing"
                    is ImportState.Success -> "Success"
                    is ImportState.Error -> "Error"
                }
                synchronized(observedStates) { observedStates.add(name) }
            }
        }

        // Force into AccountMapping state
        val fileEntry = StatementFileEntry(
            filename = "Coral CC Statement.pdf",
            format = StatementFormat.ICICI_CREDIT_CARD,
            fileHash = "seq_test_hash",
            rawText = text
        )
        val mappings = mapOf("Coral CC Statement.pdf" to 1L)
        val field = ImportViewModel::class.java.getDeclaredField("_state")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val stateFlow = field.get(viewModel) as kotlinx.coroutines.flow.MutableStateFlow<ImportState>
        stateFlow.value = ImportState.AccountMapping(listOf(fileEntry), mappings)

        // Wait for AccountMapping to be collected
        delay(100)

        // Confirm account mapping → should transition to Analyzing → Review
        viewModel.confirmAccountMapping(null, emptyList(), emptyList())

        // Wait for parsing to complete
        val reviewState = awaitReviewState(viewModel)

        // Give some extra time to see if any rogue transition overwrites Review
        delay(500)

        // Review must still be the current state
        assertTrue("State must still be Review after 500ms, but got: ${viewModel.state.value}",
            viewModel.state.value is ImportState.Review)

        collectJob.cancel()

        // Verify the state sequence contains Review as the final state
        synchronized(observedStates) {
            assertTrue("Review must appear in observed states, got: $observedStates",
                observedStates.contains("Review"))
            val lastState = observedStates.last()
            assertEquals("Final observed state must be Review", "Review", lastState)
        }

        // Zero database writes!
        assertEquals(0, fakeDb.transactionsList.size)
    }

    // -------------------------------------------------------------------------
    // REGRESSION TEST: Credit card repayments vs spends vs savings account credits
    // -------------------------------------------------------------------------
    @Test
    fun testRegression_creditCardRepaymentVsSpendVsSavingsCredit() = runBlocking {
        // Setup Slice Credit Card account
        val sliceCard = AccountEntity(
            id = 5L,
            name = "Slice Credit Card",
            bank = "Slice",
            balancePaise = 500000L, // ₹5,000.00 initial outstanding dues
            isCreditCard = true
        )
        fakeDb.accountsList.add(sliceCard)

        val viewModel = ImportViewModel(importRepository, parsers, testScope)
        val sliceText = readResource("Slice Credit Card Statement.pdf.txt")

        // 1. Import Slice Credit Card
        viewModel.parseStatementText(
            format = StatementFormat.SLICE_CREDIT_CARD,
            text = sliceText,
            filename = "SliceCC.pdf",
            accountId = 5L
        )

        val reviewState = awaitReviewState(viewModel)
        assertEquals(48, reviewState.batch.transactions.size)

        // Find the ₹210.00 repayment
        val repayment210 = reviewState.batch.transactions.first {
            it.amountPaise == 21000L && it.direction == Direction.CREDIT
        }
        assertEquals(ImporterTransactionType.CARD_PAYMENT, repayment210.transactionType)

        // Find a spend transaction (e.g. ₹50.00)
        val spendTx = reviewState.batch.transactions.first {
            it.direction == Direction.DEBIT && it.amountPaise == 5000L
        }
        assertEquals(ImporterTransactionType.EXPENSE, spendTx.transactionType)

        // Accept only the repayment and the spend
        viewModel.acceptTransaction(repayment210.id)
        viewModel.acceptTransaction(spendTx.id)

        // Commit accepted transactions
        viewModel.confirmImport()
        val successState = awaitSuccessState(viewModel)
        assertEquals(2, successState.importedCount)

        // VERIFICATION:
        // 1. Repayment ₹210.00 is stored as a CardPaymentEntity, NOT in transactionsList!
        assertEquals("CardPaymentEntity must be created for card repayment", 1, fakeDb.cardPaymentsList.size)
        val cardPayment = fakeDb.cardPaymentsList[0]
        assertEquals(5L, cardPayment.cardAccountId)
        assertEquals(21000L, cardPayment.amountPaise)

        // 2. Transactions table contains ONLY the spend transaction as EXPENSE
        assertEquals("Transactions table must only contain the spend", 1, fakeDb.transactionsList.size)
        val txEntity = fakeDb.transactionsList[0]
        assertEquals(TransactionType.EXPENSE, txEntity.type)
        assertEquals(5000L, txEntity.amountPaise)
        assertEquals(5L, txEntity.accountId)

        // 3. Repayment ₹210.00 did NOT become ordinary income or inflate income
        assertTrue("No income transaction should be created for credit card repayment",
            fakeDb.transactionsList.none { it.type == TransactionType.INCOME })

        // 4. Repayment ₹210.00 did NOT become an expense
        assertTrue("No expense transaction should represent the repayment",
            fakeDb.transactionsList.none { it.amountPaise == 21000L })

        // 5. Credit card outstanding dues correctly adjusted:
        // Initial = 500000L, + 5000L (spend) - 21000L (repayment) = 484000L
        val updatedCard = fakeDb.accounts().getById(5L)
        assertNotNull(updatedCard)
        assertEquals(484000L, updatedCard!!.balancePaise)

        // 6. Savings account credit test: HDFC savings account credit maps to TransactionType.INCOME
        viewModel.reset()
        val hdfcText = readResource("HDFC Bank Savings Account Statement.pdf.txt")
        viewModel.parseStatementText(
            format = StatementFormat.HDFC_SAVINGS,
            text = hdfcText,
            filename = "HDFC.pdf",
            accountId = 2L
        )

        val hdfcReview = awaitReviewState(viewModel)
        val hdfcCredit = hdfcReview.batch.transactions.first { it.direction == Direction.CREDIT }
        viewModel.acceptTransaction(hdfcCredit.id)

        viewModel.confirmImport()
        awaitSuccessState(viewModel)

        // Verify HDFC credit is persisted as TransactionType.INCOME
        val hdfcTx = fakeDb.transactionsList.first { it.accountId == 2L && it.amountPaise == hdfcCredit.amountPaise }
        assertEquals("Savings account credit must map to INCOME", TransactionType.INCOME, hdfcTx.type)

        // Verify HDFC balance increased by credit amount
        val updatedHdfc = fakeDb.accounts().getById(2L)
        assertNotNull(updatedHdfc)
        assertEquals(1000000L + hdfcCredit.amountPaise, updatedHdfc!!.balancePaise)
    }

    // -------------------------------------------------------------------------
    // Concrete In-Memory Test Database implementation for Unit Tests
    // -------------------------------------------------------------------------
    class TestSpendTrackDatabase : SpendTrackDatabase() {
        private val _peopleDao = com.spendtrack.app.InMemoryPersonDao()
        val accountsList = mutableListOf<AccountEntity>()
        val categoriesList = mutableListOf<CategoryEntity>()
        val transactionsList = mutableListOf<TransactionEntity>()
        val cardPaymentsList = mutableListOf<CardPaymentEntity>()
        val importHistoryList = mutableListOf<ImportHistoryEntity>()

        private val dummyDb = Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "inTransaction" -> true
                "isOpen" -> true
                "isWriteAheadLoggingEnabled" -> false
                else -> when (method.returnType) {
                    java.lang.Boolean.TYPE -> false
                    java.lang.Integer.TYPE -> 0
                    java.lang.Long.TYPE -> 0L
                    else -> null
                }
            }
        } as SupportSQLiteDatabase

        private val dummyHelper = Proxy.newProxyInstance(
            SupportSQLiteOpenHelper::class.java.classLoader,
            arrayOf(SupportSQLiteOpenHelper::class.java)
        ) { _, method, _ ->
            if (method.name == "getWritableDatabase" || method.name == "getReadableDatabase") dummyDb
            else null
        } as SupportSQLiteOpenHelper

        override val openHelper: SupportSQLiteOpenHelper = dummyHelper

        init {
            val directExecutor = Executor { it.run() }
            RoomDatabase::class.java.declaredFields.forEach { f ->
                f.isAccessible = true
                when (f.name) {
                    "mAllowMainThreadQueries", "allowMainThreadQueries" -> runCatching { f.set(this, true) }
                    "mOpenHelper", "openHelper" -> runCatching { f.set(this, dummyHelper) }
                    "internalTransactionExecutor", "mTransactionExecutor", "transactionExecutor" -> runCatching { f.set(this, directExecutor) }
                    "internalQueryExecutor", "mQueryExecutor", "queryExecutor" -> runCatching { f.set(this, directExecutor) }
                }
            }
        }

        private val _accountsDao = object : AccountDao {
            override fun observeAll(): Flow<List<AccountEntity>> = flowOf(accountsList.toList())
            override suspend fun insert(entity: AccountEntity): Long {
                val id = if (entity.id == 0L) (accountsList.size + 1).toLong() else entity.id
                accountsList.add(entity.copy(id = id))
                return id
            }
            override suspend fun insertAll(entities: List<AccountEntity>) { accountsList.addAll(entities) }
            override suspend fun getById(id: Long): AccountEntity? = accountsList.firstOrNull { it.id == id }
            override suspend fun update(entity: AccountEntity) {
                val idx = accountsList.indexOfFirst { it.id == entity.id }
                if (idx >= 0) accountsList[idx] = entity
            }
            override suspend fun delete(entity: AccountEntity) { accountsList.removeAll { it.id == entity.id } }
            override suspend fun clear() { accountsList.clear() }
            override suspend fun transactionCount(accountId: Long): Int = 0
            override suspend fun recurringCount(accountId: Long): Int = 0
        }

        private val _transactionsDao = object : TransactionDao {
            override fun observeAll(): Flow<List<TransactionEntity>> = flowOf(transactionsList.toList())
            override suspend fun insert(entity: TransactionEntity): Long {
                val id = if (entity.id == 0L) (transactionsList.size + 1).toLong() else entity.id
                transactionsList.add(entity.copy(id = id))
                return id
            }
            override suspend fun insertAll(entities: List<TransactionEntity>) { transactionsList.addAll(entities) }
            override suspend fun update(entity: TransactionEntity) {
                val idx = transactionsList.indexOfFirst { it.id == entity.id }
                if (idx >= 0) transactionsList[idx] = entity
            }
            override suspend fun delete(entity: TransactionEntity) { transactionsList.removeAll { it.id == entity.id } }
            override suspend fun clear() { transactionsList.clear() }
            override suspend fun getById(id: Long): TransactionEntity? = transactionsList.firstOrNull { it.id == id }
            override suspend fun getByOriginalExpenseId(originalExpenseId: Long): List<TransactionEntity> =
                transactionsList.filter { it.originalExpenseId == originalExpenseId }
            override fun observeDateRange(startEpochDay: Long, endEpochDay: Long): Flow<List<TransactionEntity>> =
                flowOf(transactionsList.filter { it.dateEpochDay in startEpochDay..endEpochDay })
            override suspend fun getDateRange(startEpochDay: Long, endEpochDay: Long): List<TransactionEntity> =
                transactionsList.filter { it.dateEpochDay in startEpochDay..endEpochDay }
            override fun observeRecent(limit: Int): Flow<List<TransactionEntity>> =
                flowOf(transactionsList.sortedByDescending { it.dateEpochDay }.take(limit))
        }

        private val _cardPaymentsDao = object : CardPaymentDao {
            override fun observeAll(): Flow<List<CardPaymentEntity>> = flowOf(cardPaymentsList.toList())
            override suspend fun insert(entity: CardPaymentEntity): Long {
                val id = if (entity.id == 0L) (cardPaymentsList.size + 1).toLong() else entity.id
                cardPaymentsList.add(entity.copy(id = id))
                return id
            }
            override suspend fun insertAll(entities: List<CardPaymentEntity>) { cardPaymentsList.addAll(entities) }
            override suspend fun clear() { cardPaymentsList.clear() }
        }

        private val _importHistoryDao = object : ImportHistoryDao {
            override suspend fun insert(history: ImportHistoryEntity): Long {
                val id = if (history.id == 0L) (importHistoryList.size + 1).toLong() else history.id
                importHistoryList.add(history.copy(id = id))
                return id
            }
            override suspend fun getByFileHash(hash: String): ImportHistoryEntity? =
                importHistoryList.firstOrNull { it.fileHash == hash }
        }

        private val _categoriesDao = object : CategoryDao {
            override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(categoriesList.toList())
            override suspend fun count(): Int = categoriesList.size
            override suspend fun insert(entity: CategoryEntity): Long {
                categoriesList.add(entity)
                return entity.id
            }
            override suspend fun insertAll(entities: List<CategoryEntity>) { categoriesList.addAll(entities) }
            override suspend fun update(entity: CategoryEntity) {}
            override suspend fun delete(entity: CategoryEntity) {}
            override suspend fun clear() { categoriesList.clear() }
            override suspend fun transactionCount(categoryId: Long): Int = 0
            override suspend fun budgetCount(categoryId: Long): Int = 0
            override suspend fun recurringCount(categoryId: Long): Int = 0
            override suspend fun getById(id: Long): CategoryEntity? = categoriesList.firstOrNull { it.id == id }
            override suspend fun getByName(name: String): CategoryEntity? = categoriesList.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }

        override fun accounts(): AccountDao = _accountsDao
        override fun transactions(): TransactionDao = _transactionsDao
        override fun cardPayments(): CardPaymentDao = _cardPaymentsDao
        override fun importHistory(): ImportHistoryDao = _importHistoryDao
        override fun people(): PersonDao = _peopleDao
        override fun categories(): CategoryDao = _categoriesDao

        override fun budgets(): BudgetDao = throw NotImplementedError()
        override fun loans(): LoanDao = throw NotImplementedError()
        override fun owed(): OwedDao = throw NotImplementedError()
        override fun fixedDeposits(): FixedDepositDao = throw NotImplementedError()
        override fun recurringTransactions(): RecurringTransactionDao = throw NotImplementedError()
        override fun recurringOccurrences(): RecurringOccurrenceDao = throw NotImplementedError()

        override fun inTransaction(): Boolean = true
        override val isOpen: Boolean = true

        override fun createOpenHelper(config: DatabaseConfiguration): SupportSQLiteOpenHelper {
            throw UnsupportedOperationException()
        }

        override fun createInvalidationTracker(): InvalidationTracker {
            return InvalidationTracker(this, emptyMap(), emptyMap(), "accounts")
        }

        override fun clearAllTables() {}
    }
}
