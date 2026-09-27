package com.spendtrack.app.importer.parser

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import com.spendtrack.app.ui.parsePaise
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class IciciCreditCardParser : StatementParser {
    override val identifier = "ICICI_CC_V1"

    override fun parse(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        val lines = rawText.lines()
        val transactions = mutableListOf<NormalizedTransaction>()
        
        val dateRegex = Regex("""^\s*(\d{2}/\d{2}/\d{4})\s+""")
        
        for (line in lines) {
            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                val dateStr = dateMatch.groupValues[1]
                val dateEpochDay = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd/MM/yyyy")).toEpochDay()
                
                val parts = line.trim().split(" ")
                if (parts.size >= 5) {
                    val isCr = parts.last() == "CR"
                    val amountStr = if (isCr) parts[parts.size - 2] else parts.last()
                    val amountPaise = parseAmount(amountStr)
                    val direction = if (isCr) Direction.CREDIT else Direction.DEBIT
                    
                    val refNo = parts[1]
                    // The reward points are right before the amount. Let's find where narration ends.
                    val narrationEnd = if (isCr) parts.size - 3 else parts.size - 2
                    val narration = parts.subList(2, narrationEnd).joinToString(" ")
                    
                    val type = when {
                        direction == Direction.CREDIT && (narration.contains("Payment", true) || narration.contains("Auto Debit", true)) -> ImporterTransactionType.CARD_PAYMENT
                        direction == Direction.CREDIT -> ImporterTransactionType.REFUND
                        else -> ImporterTransactionType.EXPENSE
                    }
                    
                    transactions.add(
                        NormalizedTransaction(
                            dateEpochDay = dateEpochDay,
                            amountPaise = amountPaise,
                            direction = direction,
                            rawNarration = narration,
                            normalizedMerchant = "",
                            referenceNumber = refNo,
                            transactionType = type,
                            sourceAccountId = 0L,
                            balanceAfterPaise = null,
                            sourceFilename = sourceFilename,
                            parserIdentifier = identifier,
                            duplicateFingerprint = "${dateEpochDay}_${amountPaise}_${direction.name}_${narration.hashCode()}"
                        )
                    )
                }
            }
        }
        
        return transactions
    }
    
    private fun parseAmount(str: String): Long {
        val cleanStr = str.replace("CR", "").trim()
        return parsePaise(cleanStr)
    }
}
