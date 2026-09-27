package com.spendtrack.app.importer.parser

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import com.spendtrack.app.importer.normalization.MerchantNormalizer
import com.spendtrack.app.ui.parsePaise
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale

class SbiSavingsParser : StatementParser {
    override val identifier = "SBI_SAVINGS_V1"

    private val dateFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendOptional(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("dd-MM-yy", Locale.US))
        .toFormatter(Locale.US)

    // Regex to detect beginning of transaction: e.g. "25 Sep 2026", "25/09/2026", "1 Sep 2026"
    private val dateStartRegex = Regex("""^\s*(\d{1,2}(?:\s+[A-Za-z]{3}|\s*[/-]\d{1,2}[/-])\s*\d{2,4})\b""")

    // Amount patterns in SBI statements:
    // 1. Withdrawal line: - <withdrawal> - <balance> (e.g. "- 4,891.04 - 0.00")
    private val withdrawalDashPattern = Regex("""-\s+([\d,]+\.\d{2})\s+-\s+([\d,]+\.\d{2})""")
    // 2. Deposit line: - - <deposit> <balance> (e.g. "- - 4,889.00 4,889.00")
    private val depositDashPattern = Regex("""-\s+-\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})""")
    // 3. Fallback: <amount> <balance>
    private val genericTwoAmountsPattern = Regex("""([\d,]+\.\d{2})\s+([\d,]+\.\d{2})""")

    override fun parse(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        val lines = rawText.lines()
        val transactions = mutableListOf<NormalizedTransaction>()

        class SbiTxCollector {
            var dateEpochDay: Long = 0
            var explicitDirection: Direction? = null
            var amountPaise: Long = 0
            var balanceAfterPaise: Long = 0
            var refNumber: String? = null
            val rawLines = mutableListOf<String>()

            fun build(): NormalizedTransaction? {
                if (rawLines.isEmpty()) return null

                val fullText = rawLines.joinToString(" ")
                val upperText = fullText.uppercase()

                // Check explicit direction markers
                var direction = explicitDirection
                if (direction == null) {
                    if (upperText.contains("DEP TFR") || upperText.contains("UPI/CR") || upperText.contains("/CR/") || upperText.contains("INTEREST CREDIT")) {
                        direction = Direction.CREDIT
                    } else if (upperText.contains("WDL TFR") || upperText.contains("UPI/DR") || upperText.contains("/DR/")) {
                        direction = Direction.DEBIT
                    }
                }

                // Parse amounts from the collected lines
                var parsedAmt: Long? = null
                var parsedBal: Long? = null

                for (line in rawLines) {
                    val wMatch = withdrawalDashPattern.find(line)
                    if (wMatch != null) {
                        parsedAmt = parsePaise(wMatch.groupValues[1])
                        parsedBal = parsePaise(wMatch.groupValues[2])
                        direction = Direction.DEBIT
                        break
                    }

                    val dMatch = depositDashPattern.find(line)
                    if (dMatch != null) {
                        parsedAmt = parsePaise(dMatch.groupValues[1])
                        parsedBal = parsePaise(dMatch.groupValues[2])
                        direction = Direction.CREDIT
                        break
                    }
                }

                if (parsedAmt == null) {
                    // Try generic amount matching from bottom lines
                    for (i in rawLines.indices.reversed()) {
                        val m = genericTwoAmountsPattern.find(rawLines[i])
                        if (m != null) {
                            parsedAmt = parsePaise(m.groupValues[1])
                            parsedBal = parsePaise(m.groupValues[2])
                            break
                        }
                    }
                }

                if (parsedAmt == null || parsedAmt == 0L) {
                    return null
                }

                val finalDirection = direction ?: Direction.DEBIT

                // Extract reference number
                var reference = refNumber
                if (reference == null) {
                    val upiMatch = Regex("""UPI/(?:CR|DR)/(\d{12})/""").find(fullText)
                    if (upiMatch != null) {
                        reference = upiMatch.groupValues[1]
                    } else {
                        val genUpiMatch = Regex("""\b(\d{12})\b""").find(fullText)
                        if (genUpiMatch != null) {
                            reference = genUpiMatch.groupValues[1]
                        }
                    }
                }

                // Determine transaction type
                val txType = when {
                    upperText.contains("INTEREST CREDIT") || upperText.contains("INT.PD") -> ImporterTransactionType.INTEREST
                    upperText.contains("/SELF") || upperText.contains(" SELF") || upperText.contains("TRANSFER TO SELF") || upperText.contains("OWN A/C") -> ImporterTransactionType.TRANSFER
                    upperText.contains("SALARY") -> ImporterTransactionType.INCOME
                    finalDirection == Direction.CREDIT -> ImporterTransactionType.INCOME
                    else -> ImporterTransactionType.EXPENSE
                }

                val cleanNarration = fullText
                    .replace(withdrawalDashPattern, "")
                    .replace(depositDashPattern, "")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                val normalizedMerchant = MerchantNormalizer.normalize(cleanNarration)

                return NormalizedTransaction(
                    dateEpochDay = dateEpochDay,
                    amountPaise = parsedAmt,
                    direction = finalDirection,
                    rawNarration = cleanNarration,
                    normalizedMerchant = normalizedMerchant,
                    referenceNumber = reference,
                    transactionType = txType,
                    sourceAccountId = 0L,
                    balanceAfterPaise = parsedBal,
                    sourceFilename = sourceFilename,
                    parserIdentifier = identifier,
                    duplicateFingerprint = "${dateEpochDay}_${parsedAmt}_${finalDirection.name}_${cleanNarration.hashCode()}"
                )
            }
        }

        var currentCollector: SbiTxCollector? = null
        var inTxSection = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            val upper = line.uppercase()
            if (upper.contains("STATEMENT OF ACCOUNT") || upper.contains("TXN DATE") || upper.contains("VALUE DATE") || upper.contains("DESCRIPTION") || upper.contains("BALANCE")) {
                inTxSection = true
            }

            if (!inTxSection) continue

            // Check if end of statement
            if (upper.contains("CLOSING BALANCE") || upper.contains("STATEMENT SUMMARY") || upper.contains("**END OF STATEMENT**") || upper.contains("PLEASE DO NOT SHARE")) {
                if (currentCollector != null) {
                    val tx = currentCollector.build()
                    if (tx != null) transactions.add(tx)
                    currentCollector = null
                }
                break
            }

            val dateMatch = dateStartRegex.find(line)
            if (dateMatch != null) {
                // Parse date
                val dateStr = dateMatch.groupValues[1].replace(Regex("""\s+"""), " ").trim()
                val parsedDate = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull()

                if (parsedDate != null) {
                    // Finalize previous
                    if (currentCollector != null) {
                        val tx = currentCollector.build()
                        if (tx != null) transactions.add(tx)
                    }

                    currentCollector = SbiTxCollector().apply {
                        dateEpochDay = parsedDate.toEpochDay()
                    }

                    // Check rest of line for narration/amounts
                    var rest = line.substring(dateMatch.range.last + 1).trim()
                    // Strip optional second value date if identical/date-like
                    val secondDateMatch = dateStartRegex.find(rest)
                    if (secondDateMatch != null) {
                        rest = rest.substring(secondDateMatch.range.last + 1).trim()
                    }

                    if (rest.isNotBlank()) {
                        currentCollector.rawLines.add(rest)
                    }
                    continue
                }
            }

            // Continuation line for current transaction
            if (currentCollector != null) {
                currentCollector.rawLines.add(line)
            }
        }

        if (currentCollector != null) {
            val tx = currentCollector.build()
            if (tx != null) transactions.add(tx)
        }

        return transactions
    }
}
