package com.spendtrack.app.importer

import com.spendtrack.app.importer.detector.StatementDetector
import com.spendtrack.app.importer.detector.StatementFormat
import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.parser.KotakSavingsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KotakParserTest {

    private val sampleKotakStatement = """
        Account Statement
        Kotak Mahindra Bank
        Account Type Savings
        Savings Account Transactions
        # Date Description Chq/Ref. No. Withdrawal (Dr.) Deposit (Cr.) Balance
        1 01/09/2026 UPI/Mr Kartik Kiran/Payment from sl REF10001 - 5,000.00 15,000.00
        2 02/09/2026 UPI/ZERODHA BROKING/invest REF10002 2,500.00 - 12,500.00
        3 03/09/2026 NEFT CR/ABC CORP/SALARY AUG REF10003 - 50,000.00 62,500.00
        4 04/09/2026 Recd:IMPS/Client Pay/123456789012 REF10004 - 1,200.50 63,700.50
        5 05/09/2026 KOTAK811/DEPOSIT REF10005 - 811.00 64,511.50
        6 06/09/2026 NACH-MUT-DR/NIPPON INDIA MF REF10006 1,000.00 - 63,511.50
        7 07/09/2026 CASHBACK EARNED/PROMO REWARD REF10007 - 50.00 63,561.50
        8 08/09/2026 Int.Pd:01-07-2026 to 08-09-2026 REF10008 - 125.75 63,687.25
        9 09/09/2026 KOTAK_AUTOFUND/ADDFUNDS/SWEEP REF10009 10,000.00 - 53,687.25
        10 10/09/2026 UPI/Swiggy/Food Order
        Swiggy Delivery Pune MH
        REF10010 350.00 - 53,337.25
        TOTAL WITHDRAWAL: 13,850.00 TOTAL DEPOSIT: 57,187.25
        CLOSING BALANCE: 53,337.25
    """.trimIndent()

    @Test
    fun testKotakDetector() {
        val format = StatementDetector.detectFormat(sampleKotakStatement)
        assertEquals(StatementFormat.KOTAK_SAVINGS, format)
    }

    @Test
    fun testKotakSavingsParsing() {
        val parser = KotakSavingsParser()
        val txs = parser.parse(sampleKotakStatement, "kotak_statement.pdf")

        assertEquals(10, txs.size)

        // 1. UPI Credit / Self transfer ("Payment from sl")
        val tx1 = txs[0]
        assertEquals(500000L, tx1.amountPaise)
        assertEquals(Direction.CREDIT, tx1.direction)
        assertEquals(ImporterTransactionType.TRANSFER, tx1.transactionType)
        assertEquals("REF10001", tx1.referenceNumber)
        assertEquals(1500000L, tx1.balanceAfterPaise)

        // 2. UPI Investment Debit (ZERODHA)
        val tx2 = txs[1]
        assertEquals(250000L, tx2.amountPaise)
        assertEquals(Direction.DEBIT, tx2.direction)
        assertEquals(ImporterTransactionType.INVESTMENT, tx2.transactionType)
        assertEquals("REF10002", tx2.referenceNumber)
        assertEquals(1250000L, tx2.balanceAfterPaise)

        // 3. NEFT Credit (Salary)
        val tx3 = txs[2]
        assertEquals(5000000L, tx3.amountPaise)
        assertEquals(Direction.CREDIT, tx3.direction)
        assertEquals(ImporterTransactionType.INCOME, tx3.transactionType)
        assertEquals("REF10003", tx3.referenceNumber)

        // 4. IMPS Credit
        val tx4 = txs[3]
        assertEquals(120050L, tx4.amountPaise)
        assertEquals(Direction.CREDIT, tx4.direction)
        assertEquals(ImporterTransactionType.INCOME, tx4.transactionType)

        // 5. KOTAK811 Credit
        val tx5 = txs[4]
        assertEquals(81100L, tx5.amountPaise)
        assertEquals(Direction.CREDIT, tx5.direction)
        assertEquals(ImporterTransactionType.INCOME, tx5.transactionType)

        // 6. NACH Debit (NACH-MUT-DR)
        val tx6 = txs[5]
        assertEquals(100000L, tx6.amountPaise)
        assertEquals(Direction.DEBIT, tx6.direction)
        assertEquals(ImporterTransactionType.INVESTMENT, tx6.transactionType)

        // 7. Cashback Earned
        val tx7 = txs[6]
        assertEquals(5000L, tx7.amountPaise)
        assertEquals(Direction.CREDIT, tx7.direction)
        assertEquals(ImporterTransactionType.INCOME, tx7.transactionType)

        // 8. Interest Paid (Int.Pd:)
        val tx8 = txs[7]
        assertEquals(12575L, tx8.amountPaise)
        assertEquals(Direction.CREDIT, tx8.direction)
        assertEquals(ImporterTransactionType.INTEREST, tx8.transactionType)

        // 9. KOTAK_AUTOFUND (Transfer, not salary)
        val tx9 = txs[8]
        assertEquals(1000000L, tx9.amountPaise)
        assertEquals(Direction.DEBIT, tx9.direction)
        assertEquals(ImporterTransactionType.TRANSFER, tx9.transactionType)

        // 10. Multiline narration (Swiggy)
        val tx10 = txs[9]
        assertEquals(35000L, tx10.amountPaise)
        assertEquals(Direction.DEBIT, tx10.direction)
        assertTrue(tx10.rawNarration.contains("Swiggy"))
        assertTrue(tx10.rawNarration.contains("Pune MH"))
        assertEquals("Swiggy", tx10.normalizedMerchant)
    }
}
