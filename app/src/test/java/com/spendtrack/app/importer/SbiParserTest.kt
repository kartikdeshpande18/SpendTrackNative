package com.spendtrack.app.importer

import com.spendtrack.app.importer.detector.StatementDetector
import com.spendtrack.app.importer.detector.StatementFormat
import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.parser.SbiSavingsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SbiParserTest {

    private val sampleSbiStatement = """
        STATEMENT OF ACCOUNT
        State Bank of India
        Account Name: MR KARTIK KIRAN DESHPANDE
        Account Number: 00000020123456789
        Branch: PUNE MAIN BRANCH
        Txn Date Value Date Description Ref No./Cheque No. Debit Credit Balance
        25 Sep 2026 25 Sep 2026 DEP TFR
        UPI/CR/626912345678/KARTIKKI/HDFC/Self
        TRANSFER FROM 5010042345
        - - 4,889.00 4,889.00
        26 Sep 2026 26 Sep 2026 WDL TFR
        UPI/DR/627098765432/KARTIK/SBI/Self
        TRANSFER TO 0000001234
        - 4,891.04 - 0.00
        30 Sep 2026 30 Sep 2026 INTEREST CREDIT
        INTEREST EARNED FOR Q2
        - - 1.00 1.00
        **END OF STATEMENT**
    """.trimIndent()

    @Test
    fun testSbiDetector() {
        val format = StatementDetector.detectFormat(sampleSbiStatement)
        assertEquals(StatementFormat.SBI_SAVINGS, format)
    }

    @Test
    fun testSbiSavingsParsing() {
        val parser = SbiSavingsParser()
        val txs = parser.parse(sampleSbiStatement, "sbi_statement.pdf")

        assertEquals(3, txs.size)

        // 1. DEP TFR / UPI CR / Self transfer
        val tx1 = txs[0]
        assertEquals(488900L, tx1.amountPaise)
        assertEquals(Direction.CREDIT, tx1.direction)
        assertEquals(ImporterTransactionType.TRANSFER, tx1.transactionType)
        assertEquals("626912345678", tx1.referenceNumber)
        assertEquals(488900L, tx1.balanceAfterPaise)
        assertTrue(tx1.rawNarration.contains("DEP TFR"))
        assertTrue(tx1.rawNarration.contains("Self"))

        // 2. WDL TFR / UPI DR / Self transfer / zero balance
        val tx2 = txs[1]
        assertEquals(489104L, tx2.amountPaise)
        assertEquals(Direction.DEBIT, tx2.direction)
        assertEquals(ImporterTransactionType.TRANSFER, tx2.transactionType)
        assertEquals("627098765432", tx2.referenceNumber)
        assertEquals(0L, tx2.balanceAfterPaise)
        assertTrue(tx2.rawNarration.contains("WDL TFR"))
        assertTrue(tx2.rawNarration.contains("Self"))

        // 3. INTEREST CREDIT
        val tx3 = txs[2]
        assertEquals(100L, tx3.amountPaise)
        assertEquals(Direction.CREDIT, tx3.direction)
        assertEquals(ImporterTransactionType.INTEREST, tx3.transactionType)
        assertEquals(100L, tx3.balanceAfterPaise)
        assertTrue(tx3.rawNarration.contains("INTEREST CREDIT"))
    }

    @Test
    fun testMultilineNarration() {
        val multilineStatement = """
            STATEMENT OF ACCOUNT
            State Bank of India
            Txn Date Value Date Description Cheque No. Debit Credit Balance
            01 Oct 2026 01 Oct 2026 WDL TFR
            UPI/DR/123456789012/SWIGGY/ICICI/Order 12345
            Swiggy Food Delivery Services Bangalore
            Karnataka India
            - 350.50 - 5,000.00
            **END OF STATEMENT**
        """.trimIndent()

        val parser = SbiSavingsParser()
        val txs = parser.parse(multilineStatement, "sbi_multiline.pdf")

        assertEquals(1, txs.size)
        val tx = txs[0]
        assertEquals(35050L, tx.amountPaise)
        assertEquals(Direction.DEBIT, tx.direction)
        assertEquals("123456789012", tx.referenceNumber)
        assertEquals(500000L, tx.balanceAfterPaise)
        assertTrue(tx.rawNarration.contains("SWIGGY"))
        assertTrue(tx.rawNarration.contains("Bangalore"))
        assertEquals("Swiggy", tx.normalizedMerchant)
    }
}
