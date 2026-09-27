package com.spendtrack.app.importer.parser

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import com.spendtrack.app.ui.parsePaise
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

class SliceCreditCardParser : StatementParser {
    override val identifier = "SLICE_CC_V1"

    override fun parse(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        val lines = rawText.lines()
        val transactions = mutableListOf<NormalizedTransaction>()
        
        val l1Regex = Regex("""^\s*(.*?)\s+₹([\d,]+\.\d{2}|[\d,]+)""")
        val l2Regex = Regex("""^\s*(?:[A-Z]\s+)?(\d{1,2}\s+[A-Za-z]{3}\s+'\d{2})(?:\s+•\s+(.*))?$""")
        
        val dateFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d MMM ''yy")
            .toFormatter(Locale.ENGLISH)

        var currentLine1Match: MatchResult? = null
        var isRefundSection = false
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed == "Refunds & repayments" || trimmed == "Repayments") {
                isRefundSection = true
            } else if (trimmed == "Spends") {
                isRefundSection = false
            }
            
            if (currentLine1Match != null) {
                val l2Match = l2Regex.find(line)
                if (l2Match != null) {
                    val merchant = currentLine1Match.groupValues[1].trim()
                    val amountStr = currentLine1Match.groupValues[2]
                    val dateStr = l2Match.groupValues[1]
                    val typeStr = l2Match.groupValues[2]
                    
                    val dateEpochDay = LocalDate.parse(dateStr, dateFormatter).toEpochDay()
                    val amountPaise = parseAmount(amountStr)
                    
                    // If merchant says Repayment or we are in Refunds section, it's a CREDIT.
                    val direction = if (isRefundSection || merchant.equals("Repayment", ignoreCase = true) || merchant.equals("Refund", ignoreCase = true)) {
                        Direction.CREDIT
                    } else {
                        Direction.DEBIT
                    }
                    
                    val narration = if (typeStr.isNotBlank()) "$merchant • $typeStr" else merchant
                    
                    transactions.add(
                        NormalizedTransaction(
                            dateEpochDay = dateEpochDay,
                            amountPaise = amountPaise,
                            direction = direction,
                            rawNarration = narration,
                            normalizedMerchant = "",
                            referenceNumber = null,
                            transactionType = ImporterTransactionType.UNKNOWN,
                            sourceAccountId = 0L,
                            balanceAfterPaise = null,
                            sourceFilename = sourceFilename,
                            parserIdentifier = identifier,
                            duplicateFingerprint = "${dateEpochDay}_${amountPaise}_${direction.name}_${narration.hashCode()}"
                        )
                    )
                }
                currentLine1Match = null
            }
            
            val l1Match = l1Regex.find(line)
            if (l1Match != null) {
                // Ignore summary lines that have Total amount due etc.
                val m = l1Match.groupValues[1].trim()
                if (m != "Total amount due" && !m.startsWith("Min amount due") && m != "Spends" && m != "Refunds & repayments" && m != "Interest" && m != "Surcharge" && m != "IGST" && m != "CGST" && m != "SGST") {
                    currentLine1Match = l1Match
                }
            }
        }
        
        return transactions
    }
    
    private fun parseAmount(str: String): Long {
        return parsePaise(str)
    }
}
