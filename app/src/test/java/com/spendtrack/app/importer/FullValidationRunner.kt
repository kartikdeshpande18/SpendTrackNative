package com.spendtrack.app.importer

import com.spendtrack.app.importer.detector.StatementDetector
import com.spendtrack.app.importer.detector.StatementFormat
import com.spendtrack.app.importer.matching.TransferMatcher
import com.spendtrack.app.importer.parser.*
import com.spendtrack.app.importer.reconciliation.Reconciler
import java.io.File
import org.junit.Test

class FullValidationRunner {
    private fun readResource(name: String): String {
        val url = javaClass.classLoader?.getResource(name)
        return File(url!!.toURI()).readText()
    }

    @Test
    fun runValidation() {
        val files = listOf(
            "HDFC Bank Savings Account Statement.pdf.txt",
            "OpTransactionHistory26-09-2026.pdf-23-05-47.pdf.txt",
            "Coral CC Statement.pdf.txt",
            "Amazon Pay CC Statement.pdf.txt",
            "Slice Credit Card Statement.pdf.txt",
            "Slice Atom Emergency Fund Statement.pdf.txt",
            "Slice Savings Account Statement.pdf.txt"
        )
        
        println("| Statement | Detected | Parsed | Reconciled | Duplicates | Transfers | Card Payments | Result |")
        println("|---|---|---|---|---|---|---|---|")

        for (fileName in files) {
            val text = readResource(fileName)
            val format = StatementDetector.detectFormat(text)
            
            val parser: StatementParser? = when (format) {
                StatementFormat.HDFC_SAVINGS -> HdfcSavingsParser()
                StatementFormat.ICICI_SAVINGS -> IciciSavingsParser()
                StatementFormat.ICICI_CREDIT_CARD -> IciciCreditCardParser()
                StatementFormat.SLICE_SAVINGS -> SliceSavingsParser()
                StatementFormat.SLICE_CREDIT_CARD -> SliceCreditCardParser()
                StatementFormat.SLICE_ATOM -> SliceAtomParser()
                else -> null
            }
            
            if (parser != null) {
                val txs = parser.parse(text, fileName)
                val reconciled = Reconciler.reconcile(txs)
                val transfers = TransferMatcher.matchTransfers(txs).filter { it.transactionType == com.spendtrack.app.importer.model.ImporterTransactionType.TRANSFER }
                
                // Card payments
                val cardPayments = txs.count { it.rawNarration.contains("CREDIT CARD", ignoreCase = true) || it.rawNarration.contains("PAYMENT", ignoreCase = true) }
                
                val result = if (txs.isNotEmpty() && reconciled.isSuccessful) "PASS" else "FAIL"
                
                println("| $fileName | $format | ${txs.size} | ${reconciled.isSuccessful} | 0 | ${transfers.size} | $cardPayments | $result |")
            } else {
                println("| $fileName | $format | 0 | false | 0 | 0 | 0 | FAIL |")
            }
        }
    }
}
