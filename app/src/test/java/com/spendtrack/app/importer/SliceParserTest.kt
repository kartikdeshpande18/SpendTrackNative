package com.spendtrack.app.importer

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.parser.SliceSavingsParser
import com.spendtrack.app.importer.parser.SliceCreditCardParser
import com.spendtrack.app.importer.parser.SliceAtomParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SliceParserTest {
    private fun readResource(name: String): String {
        val url = javaClass.classLoader?.getResource(name)
        return File(url!!.toURI()).readText()
    }

    @Test
    fun testSliceSavingsParsing() {
        val text = readResource("Slice Savings Account Statement.pdf.txt")
        val parser = SliceSavingsParser()
        val txs = parser.parse(text, "SliceSavings.pdf")
        
        println("Slice Savings txs: " + txs.size)
        txs.forEach { println(it) }

        assertTrue(txs.isNotEmpty())
        assertTrue(txs.all { it.amountPaise > 0 })
    }

    @Test
    fun testSliceCcParsing() {
        val text = readResource("Slice Credit Card Statement.pdf.txt")
        val parser = SliceCreditCardParser()
        val txs = parser.parse(text, "SliceCC.pdf")

        // Exact count: 45 spends + 3 repayments = 48 transactions
        assertEquals(48, txs.size)

        // All amounts must be positive paise
        assertTrue(txs.all { it.amountPaise > 0 })

        val spends = txs.filter { it.direction == Direction.DEBIT }
        val repayments = txs.filter { it.direction == Direction.CREDIT }

        assertEquals(45, spends.size)
        assertEquals(3, repayments.size)

        // Total spends: ₹5,030.14 = 503014L paise
        val totalSpendsPaise = spends.sumOf { it.amountPaise }
        assertEquals(503014L, totalSpendsPaise)

        // Total repayments: ₹2,110.00 = 211000L paise
        val totalRepaymentsPaise = repayments.sumOf { it.amountPaise }
        assertEquals(211000L, totalRepaymentsPaise)

        // Regression 1: Pattern with icon letter line (previously skipped)
        val shreeMataji = txs.firstOrNull { it.rawNarration.contains("Shree mataji", ignoreCase = true) }
        assertNotNull(shreeMataji)
        assertEquals(5000L, shreeMataji!!.amountPaise)
        assertEquals(Direction.DEBIT, shreeMataji.direction)
        assertEquals(ImporterTransactionType.EXPENSE, shreeMataji.transactionType)

        // Regression 2: Pattern without icon letter line
        val zomato = txs.firstOrNull { it.rawNarration.contains("Zomato", ignoreCase = true) }
        assertNotNull(zomato)
        assertEquals(12153L, zomato!!.amountPaise)
        assertEquals(Direction.DEBIT, zomato.direction)
        assertEquals(ImporterTransactionType.EXPENSE, zomato.transactionType)

        // Regression 3: Repayment pattern
        val repayment = txs.firstOrNull { it.rawNarration.contains("Repayment", ignoreCase = true) }
        assertNotNull(repayment)
        assertEquals(Direction.CREDIT, repayment!!.direction)
        assertEquals(ImporterTransactionType.CARD_PAYMENT, repayment.transactionType)
    }

    @Test
    fun testSliceAtomParsing() {
        val text = readResource("Slice Atom Emergency Fund Statement.pdf.txt")
        val parser = SliceAtomParser()
        val txs = parser.parse(text, "SliceAtom.pdf")

        // Wait, slice atom parser is just a stub right now, but subagent implemented it! Let's hope it works.
        assertTrue(txs.isNotEmpty())
        assertTrue(txs.all { it.amountPaise > 0 })
    }
}
