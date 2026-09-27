package com.spendtrack.app.importer

import com.spendtrack.app.importer.detector.StatementDetector
import com.spendtrack.app.importer.detector.StatementFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class StatementDetectorTest {

    private fun readResource(name: String): String {
        val url = javaClass.classLoader?.getResource(name)
        return File(url!!.toURI()).readText()
    }

    @Test
    fun detectHdfcSavings() {
        val text = readResource("HDFC Bank Savings Account Statement.pdf.txt")
        assertEquals(StatementFormat.HDFC_SAVINGS, StatementDetector.detectFormat(text))
    }

    @Test
    fun detectIciciSavings() {
        val text = readResource("OpTransactionHistory26-09-2026.pdf-23-05-47.pdf.txt")
        assertEquals(StatementFormat.ICICI_SAVINGS, StatementDetector.detectFormat(text))
    }

    @Test
    fun detectCoralCc() {
        val text = readResource("Coral CC Statement.pdf.txt")
        assertEquals(StatementFormat.ICICI_CREDIT_CARD, StatementDetector.detectFormat(text))
    }

    @Test
    fun detectAmazonPayCc() {
        val text = readResource("Amazon Pay CC Statement.pdf.txt")
        assertEquals(StatementFormat.ICICI_CREDIT_CARD, StatementDetector.detectFormat(text))
    }

    @Test
    fun detectSliceCc() {
        val text = readResource("Slice Credit Card Statement.pdf.txt")
        assertEquals(StatementFormat.SLICE_CREDIT_CARD, StatementDetector.detectFormat(text))
    }

    @Test
    fun detectSliceSavings() {
        val text = readResource("Slice Savings Account Statement.pdf.txt")
        assertEquals(StatementFormat.SLICE_SAVINGS, StatementDetector.detectFormat(text))
    }

    @Test
    fun detectSliceAtom() {
        val text = readResource("Slice Atom Emergency Fund Statement.pdf.txt")
        assertEquals(StatementFormat.SLICE_ATOM, StatementDetector.detectFormat(text))
    }

    @Test
    fun detectSbiSavings() {
        val text = "STATEMENT OF ACCOUNT\nState Bank of India\nTxn Date Description"
        assertEquals(StatementFormat.SBI_SAVINGS, StatementDetector.detectFormat(text))
    }

    @Test
    fun detectKotakSavings() {
        val text = "Account Statement\nKotak Mahindra Bank\nSavings Account Transactions\nWithdrawal (Dr.) Deposit (Cr.)"
        assertEquals(StatementFormat.KOTAK_SAVINGS, StatementDetector.detectFormat(text))
    }

    @Test
    fun detectUnsupported() {
        assertEquals(StatementFormat.UNSUPPORTED, StatementDetector.detectFormat("This is some random text."))
    }
}
