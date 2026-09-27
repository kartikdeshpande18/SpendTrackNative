package com.spendtrack.app.importer.parser

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImportStatus
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import com.spendtrack.app.ui.parsePaise
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HdfcSavingsParser : StatementParser {
    override val identifier = "HDFC_SAVINGS_V1"

    override fun parse(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        val lines = rawText.lines()
        val transactions = mutableListOf<NormalizedTransaction>()
        
        val dateRegex = Regex("""^\s*(\d{2}/\d{2}/\d{2})\s+""")
        
        var dateEpochDay: Long = 0
        var amountPaise: Long = 0
        var direction: Direction = Direction.DEBIT
        var referenceNumber: String? = null
        var balanceAfterPaise: Long = 0
        var lastBalance: Long = 0
        var currentNarration = ""
        var inTx = false
        
        fun buildTx() {
            if (inTx) {
                val cleanNarration = currentNarration.trim().replace(Regex("""\s+"""), " ")
                transactions.add(
                    NormalizedTransaction(
                        dateEpochDay = dateEpochDay,
                        amountPaise = amountPaise,
                        direction = direction,
                        rawNarration = cleanNarration,
                        normalizedMerchant = "",
                        referenceNumber = referenceNumber,
                        transactionType = ImporterTransactionType.UNKNOWN,
                        sourceAccountId = 0L,
                        balanceAfterPaise = balanceAfterPaise,
                        sourceFilename = sourceFilename,
                        parserIdentifier = identifier,
                        duplicateFingerprint = "${dateEpochDay}_${amountPaise}_${direction.name}_${cleanNarration.hashCode()}"
                    )
                )
            }
        }
        
        for (line in lines) {
            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                buildTx()
                
                val dateStr = dateMatch.groupValues[1]
                dateEpochDay = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yy")).toEpochDay()
                
                val parts = line.trim().split(" ")
                if (parts.size >= 4) {
                    val balanceStr = parts.last()
                    val amountStr = parts[parts.size - 2]
                    
                    val valueDateRegex = Regex("""\d{2}/\d{2}/\d{2}""")
                    val valueDateIdx = parts.indexOfLast { valueDateRegex.matches(it) && it != parts[0] }
                    
                    val refNo = if (valueDateIdx > 2) parts[valueDateIdx - 1] else null
                    val narrationEnd = if (valueDateIdx > 1) (if (refNo != null) valueDateIdx - 1 else valueDateIdx) else parts.size - 2
                    
                    currentNarration = parts.subList(1, narrationEnd).joinToString(" ")
                    
                    balanceAfterPaise = parseAmount(balanceStr)
                    amountPaise = parseAmount(amountStr)
                    
                    if (lastBalance != 0L) {
                        direction = if (balanceAfterPaise > lastBalance) Direction.CREDIT else Direction.DEBIT
                    } else {
                        direction = if (currentNarration.contains(" CR-") || currentNarration.contains("CREDIT", true)) Direction.CREDIT else Direction.DEBIT
                    }
                    lastBalance = balanceAfterPaise
                    referenceNumber = refNo
                    inTx = true
                }
            } else {
                if (inTx && line.isNotBlank() && !line.contains("STATEMENT SUMMARY") && !line.contains("HDFC BANK LIMITED") && !line.contains("Page No")) {
                    currentNarration += " " + line.trim()
                } else if (line.contains("STATEMENT SUMMARY")) {
                    buildTx()
                    inTx = false
                }
            }
        }
        buildTx()
        
        return transactions
    }
    
    private fun parseAmount(str: String): Long {
        val cleanStr = str.replace("Cr", "").replace("Dr", "").trim()
        return parsePaise(cleanStr)
    }
}
