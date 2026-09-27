package com.spendtrack.app.importer

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.parser.HdfcSavingsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HdfcParserTest {
    private fun readResource(name: String): String {
        val url = javaClass.classLoader?.getResource(name)
        return File(url!!.toURI()).readText()
    }

    @Test
    fun testHdfcParsing() {
        val text = readResource("HDFC Bank Savings Account Statement.pdf.txt")
        val parser = HdfcSavingsParser()
        val txs = parser.parse(text, "HDFC.pdf")

        println("HDFC txs: " + txs.size)
        txs.forEach { println(it) }

        // Validate basic counts
        assertTrue(txs.isNotEmpty())

        // Spot check transactions
        val first = txs.first()
        // Find a debit
        val debit = txs.find { it.direction == Direction.DEBIT }
        assertTrue(debit != null)
        
        // Find a credit
        val credit = txs.find { it.direction == Direction.CREDIT }
        assertTrue(credit != null)

        // Find Amazon purchase
        
        // In this PDF, we know there are transactions.
        // E.g., we expect certain dates or amounts. We can just print them if needed.
        // Let's assert all amounts are positive
        assertTrue(txs.all { it.amountPaise > 0 })
    }
}
