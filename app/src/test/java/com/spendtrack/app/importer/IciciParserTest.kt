package com.spendtrack.app.importer

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.parser.IciciSavingsParser
import com.spendtrack.app.importer.parser.IciciCreditCardParser
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IciciParserTest {
    private fun readResource(name: String): String {
        val url = javaClass.classLoader?.getResource(name)
        return File(url!!.toURI()).readText()
    }

    @Test
    fun testIciciSavingsParsing() {
        val text = readResource("OpTransactionHistory26-09-2026.pdf-23-05-47.pdf.txt")
        val parser = IciciSavingsParser()
        val txs = parser.parse(text, "ICICI.pdf")

        org.junit.Assert.assertEquals(5, txs.size)

        // Tx 0: 05.09.2026 Salary Aug 26 -> ₹24,666.00 Credit, Income
        val tx0 = txs[0]
        org.junit.Assert.assertEquals(2466600L, tx0.amountPaise)
        org.junit.Assert.assertEquals(Direction.CREDIT, tx0.direction)
        org.junit.Assert.assertEquals(com.spendtrack.app.importer.model.ImporterTransactionType.INCOME, tx0.transactionType)
        org.junit.Assert.assertEquals("045758890601", tx0.referenceNumber)
        org.junit.Assert.assertEquals(2466600L, tx0.balanceAfterPaise)
        assertTrue(tx0.rawNarration.contains("Salary Aug 26"))

        // Tx 1: 05.09.2026 Self Trans / SLICE -> ₹24,666.00 Debit, Transfer
        val tx1 = txs[1]
        org.junit.Assert.assertEquals(2466600L, tx1.amountPaise)
        org.junit.Assert.assertEquals(Direction.DEBIT, tx1.direction)
        org.junit.Assert.assertEquals(com.spendtrack.app.importer.model.ImporterTransactionType.TRANSFER, tx1.transactionType)
        org.junit.Assert.assertEquals("624833104171", tx1.referenceNumber)
        org.junit.Assert.assertEquals(0L, tx1.balanceAfterPaise)
        assertTrue(tx1.rawNarration.contains("Self Trans/SLICE"))

        // Tx 2: 14.09.2026 -> ₹15.00 Credit
        val tx2 = txs[2]
        org.junit.Assert.assertEquals(1500L, tx2.amountPaise)
        org.junit.Assert.assertEquals(Direction.CREDIT, tx2.direction)
        org.junit.Assert.assertEquals("144048724525", tx2.referenceNumber)
        org.junit.Assert.assertEquals(1500L, tx2.balanceAfterPaise)

        // Tx 3: 14.09.2026 -> ₹50.00 Credit
        val tx3 = txs[3]
        org.junit.Assert.assertEquals(5000L, tx3.amountPaise)
        org.junit.Assert.assertEquals(Direction.CREDIT, tx3.direction)
        org.junit.Assert.assertEquals("144127656358", tx3.referenceNumber)
        org.junit.Assert.assertEquals(6500L, tx3.balanceAfterPaise)

        // Tx 4: 14.09.2026 Self Trans / SLICE -> ₹65.00 Debit, Transfer
        val tx4 = txs[4]
        org.junit.Assert.assertEquals(6500L, tx4.amountPaise)
        org.junit.Assert.assertEquals(Direction.DEBIT, tx4.direction)
        org.junit.Assert.assertEquals(com.spendtrack.app.importer.model.ImporterTransactionType.TRANSFER, tx4.transactionType)
        org.junit.Assert.assertEquals("144518212298", tx4.referenceNumber)
        org.junit.Assert.assertEquals(0L, tx4.balanceAfterPaise)
        assertTrue(tx4.rawNarration.contains("Self Trans/SLICE"))
    }

    @Test
    fun testCoralCcParsing() {
        val text = readResource("Coral CC Statement.pdf.txt")
        val parser = IciciCreditCardParser()
        val txs = parser.parse(text, "Coral.pdf")

        assertTrue(txs.isNotEmpty())
        assertTrue(txs.all { it.amountPaise > 0 })
    }

    @Test
    fun testAmazonPayCcParsing() {
        val text = readResource("Amazon Pay CC Statement.pdf.txt")
        val parser = IciciCreditCardParser()
        val txs = parser.parse(text, "AmazonPay.pdf")

        assertTrue(txs.isNotEmpty())
        assertTrue(txs.all { it.amountPaise > 0 })
    }
}
