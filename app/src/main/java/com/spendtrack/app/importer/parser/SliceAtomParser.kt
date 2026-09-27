package com.spendtrack.app.importer.parser

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import com.spendtrack.app.ui.parsePaise
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

class SliceAtomParser : StatementParser {
    override val identifier = "SLICE_ATOM_V1"

    override fun parse(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        val lines = rawText.lines()
        val transactions = mutableListOf<NormalizedTransaction>()
        
        val dateRegex = Regex("""^\s*(\d{2}\s+[A-Za-z]{3}\s+'\d{2})\s+""")
        
        val dateFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("dd MMM ''yy")
            .toFormatter(Locale.ENGLISH)

        var dateEpochDay: Long = 0
        var amountPaise: Long = 0
        var direction: Direction = Direction.DEBIT
        var referenceNumber: String? = null
        var balanceAfterPaise: Long = 0
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
        
        var passedHeader = false
        
        for (line in lines) {
            if (line.contains("DETAILS") && line.contains("REF NO.") && line.contains("AMOUNT")) {
                passedHeader = true
                continue
            }
            if (!passedHeader) continue
            
            val dateMatch = dateRegex.find(line)
            if (dateMatch != null && line.contains("₹")) {
                buildTx()
                
                val dateStr = dateMatch.groupValues[1]
                dateEpochDay = LocalDate.parse(dateStr, dateFormatter).toEpochDay()
                
                val parts = line.trim().split(" ")
                if (parts.size >= 6) {
                    val balanceStr = parts.last()
                    val amountStr = parts[parts.size - 2]
                    
                    balanceAfterPaise = parseAmount(balanceStr)
                    amountPaise = parseAmount(amountStr)
                    
                    direction = if (amountStr.contains("-")) Direction.DEBIT else Direction.CREDIT
                    referenceNumber = parts[parts.size - 3]
                    
                    // date is 3 words, so narration is from index 3 to size - 3
                    val narrationEnd = parts.size - 3
                    if (narrationEnd > 3) {
                        currentNarration = parts.subList(3, narrationEnd).joinToString(" ")
                    } else {
                        currentNarration = ""
                    }
                    inTx = true
                }
            } else {
                if (inTx && line.isNotBlank() && !line.contains("Page ") && !line.contains("Opening balance")) {
                    currentNarration += " " + line.trim()
                }
            }
        }
        buildTx()
        
        return transactions
    }
    
    private fun parseAmount(str: String): Long {
        val cleanStr = str.replace("₹", "").replace("-", "").trim()
        return parsePaise(cleanStr)
    }
}
