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

class KotakSavingsParser : StatementParser {
    override val identifier = "KOTAK_SAVINGS_V1"

    private val dateFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("dd/MM/yy", Locale.US))
        .appendOptional(DateTimeFormatter.ofPattern("dd-MM-yy", Locale.US))
        .toFormatter(Locale.US)

    // Regex matching row start: optional serial number, then date: e.g. "1 01/09/2026", "01-Sep-2026"
    private val rowStartRegex = Regex("""^\s*(?:\d+\s+)?(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}(?:-|\s+)[A-Za-z]{3}(?:-|\s+)\d{2,4})\b""")

    // Amount patterns in Kotak:
    // 1. Withdrawal: <amount> - <balance> (e.g. "500.00 - 10,500.00")
    private val withdrawalDashPattern = Regex("""([\d,]+\.\d{2})\s+-\s+([\d,]+\.\d{2})""")
    // 2. Deposit: - <amount> <balance> (e.g. "- 500.00 11,000.00")
    private val depositDashPattern = Regex("""-\s+([\d,]+\.\d{2})\s+([\d,]+\.\d{2})""")
    // 3. Fallback: <amount> <balance>
    private val genericTwoAmountsPattern = Regex("""([\d,]+\.\d{2})\s+([\d,]+\.\d{2})""")

    override fun parse(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        val lines = rawText.lines()
        val transactions = mutableListOf<NormalizedTransaction>()

        class KotakTxCollector {
            var dateEpochDay: Long = 0
            var explicitDirection: Direction? = null
            var amountPaise: Long = 0
            var balanceAfterPaise: Long = 0
            var chqRefNo: String? = null
            val rawLines = mutableListOf<String>()

            fun build(lastBalancePaise: Long?): Pair<NormalizedTransaction?, Long?> {
                if (rawLines.isEmpty()) return Pair(null, lastBalancePaise)

                val fullText = rawLines.joinToString(" ")
                val upperText = fullText.uppercase()

                var parsedAmt: Long? = null
                var parsedBal: Long? = null
                var direction = explicitDirection

                for (line in rawLines) {
                    val wMatch = withdrawalDashPattern.find(line)
                    if (wMatch != null) {
                        parsedAmt = parsePaise(wMatch.groupValues[1])
                        parsedBal = parsePaise(wMatch.groupValues[2])
                        direction = Direction.DEBIT
                        val beforeToken = line.substring(0, wMatch.range.first).trim().split(Regex("""\s+""")).lastOrNull()
                        if (beforeToken != null && beforeToken.isNotBlank() && beforeToken != "-" &&
                            (beforeToken.any { it.isDigit() } || beforeToken.startsWith("REF", ignoreCase = true))) {
                            chqRefNo = beforeToken
                        }
                        break
                    }

                    val dMatch = depositDashPattern.find(line)
                    if (dMatch != null) {
                        parsedAmt = parsePaise(dMatch.groupValues[1])
                        parsedBal = parsePaise(dMatch.groupValues[2])
                        direction = Direction.CREDIT
                        val beforeToken = line.substring(0, dMatch.range.first).trim().split(Regex("""\s+""")).lastOrNull()
                        if (beforeToken != null && beforeToken.isNotBlank() && beforeToken != "-" &&
                            (beforeToken.any { it.isDigit() } || beforeToken.startsWith("REF", ignoreCase = true))) {
                            chqRefNo = beforeToken
                        }
                        break
                    }
                }

                if (parsedAmt == null) {
                    // Try generic amount matching: <amount> <balance>
                    for (i in rawLines.indices.reversed()) {
                        val m = genericTwoAmountsPattern.find(rawLines[i])
                        if (m != null) {
                            val amt = parsePaise(m.groupValues[1])
                            val bal = parsePaise(m.groupValues[2])
                            parsedAmt = amt
                            parsedBal = bal

                            val line = rawLines[i]
                            val beforeToken = line.substring(0, m.range.first).trim().split(Regex("""\s+""")).lastOrNull()
                            if (beforeToken != null && beforeToken.isNotBlank() && beforeToken != "-" &&
                                (beforeToken.any { it.isDigit() } || beforeToken.startsWith("REF", ignoreCase = true) ||
                                 beforeToken.startsWith("NEFT", ignoreCase = true) || beforeToken.startsWith("UPI", ignoreCase = true) ||
                                 beforeToken.startsWith("811", ignoreCase = true))) {
                                chqRefNo = beforeToken
                            }

                            // Determine direction from balance transition
                            if (direction == null) {
                                if (lastBalancePaise != null) {
                                    if (bal == lastBalancePaise + amt) {
                                        direction = Direction.CREDIT
                                    } else if (bal == lastBalancePaise - amt) {
                                        direction = Direction.DEBIT
                                    } else if (bal > lastBalancePaise) {
                                        direction = Direction.CREDIT
                                    } else if (bal < lastBalancePaise) {
                                        direction = Direction.DEBIT
                                    }
                                } else {
                                    if (bal == amt) {
                                        direction = Direction.CREDIT
                                    }
                                }
                            }
                            break
                        }
                    }
                }

                if (parsedAmt == null || parsedAmt == 0L) {
                    return Pair(null, lastBalancePaise)
                }

                // If direction still not set, look for explicit markers
                if (direction == null) {
                    if (upperText.contains("(CR.)") || upperText.contains(" CR ") || upperText.endsWith(" CR") ||
                        upperText.contains("CASHBACK") || upperText.contains("INT.PD") || upperText.contains("DEPOSIT")) {
                        direction = Direction.CREDIT
                    } else {
                        direction = Direction.DEBIT
                    }
                }

                // Extract Cheque / Ref Number
                var reference = chqRefNo
                if (reference == null) {
                    val refMatch = Regex("""\b(?:Chq/Ref\.?\s*No\.?|Ref\.?|RRN)[\s:]+([A-Za-z0-9-]+)\b""", RegexOption.IGNORE_CASE).find(fullText)
                    if (refMatch != null) {
                        reference = refMatch.groupValues[1]
                    } else {
                        val rrnMatch = Regex("""\b(\d{12})\b""").find(fullText)
                        if (rrnMatch != null) {
                            reference = rrnMatch.groupValues[1]
                        }
                    }
                }

                // Classify transaction type per Kotak rules
                val txType = when {
                    upperText.contains("CASHBACK EARNED") || upperText.contains("CASHBACK") -> ImporterTransactionType.INCOME
                    upperText.contains("INT.PD:") || upperText.contains("INTEREST") -> ImporterTransactionType.INTEREST
                    upperText.contains("KOTAK_AUTOFUND") -> ImporterTransactionType.TRANSFER
                    upperText.contains("/SELF") || upperText.contains(" SELF") || upperText.contains("PAYMENT FROM SL") || upperText.contains("OWN A/C") -> ImporterTransactionType.TRANSFER
                    upperText.contains("NACH-MUT-DR") || upperText.contains("ZERODHA") ||
                            ((upperText.contains("MUTUAL") || upperText.contains("MF ")) && direction == Direction.DEBIT) -> ImporterTransactionType.INVESTMENT
                    upperText.contains("SALARY") -> ImporterTransactionType.INCOME
                    direction == Direction.CREDIT -> ImporterTransactionType.INCOME
                    else -> ImporterTransactionType.EXPENSE
                }

                val cleanNarration = fullText
                    .replace(withdrawalDashPattern, "")
                    .replace(depositDashPattern, "")
                    .replace(genericTwoAmountsPattern, "")
                    .let { t -> if (reference != null) t.replace(reference, "") else t }
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                val normalizedMerchant = MerchantNormalizer.normalize(cleanNarration)

                val tx = NormalizedTransaction(
                    dateEpochDay = dateEpochDay,
                    amountPaise = parsedAmt,
                    direction = direction,
                    rawNarration = cleanNarration.ifBlank { fullText },
                    normalizedMerchant = normalizedMerchant,
                    referenceNumber = reference,
                    transactionType = txType,
                    sourceAccountId = 0L,
                    balanceAfterPaise = parsedBal,
                    sourceFilename = sourceFilename,
                    parserIdentifier = identifier,
                    duplicateFingerprint = "${dateEpochDay}_${parsedAmt}_${direction.name}_${cleanNarration.hashCode()}"
                )

                return Pair(tx, parsedBal ?: lastBalancePaise)
            }
        }

        var currentCollector: KotakTxCollector? = null
        var inTxSection = false
        var lastBalancePaise: Long? = null

        val openingBalMatch = Regex("""Opening\s+Balance.*?([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE).find(rawText)
        if (openingBalMatch != null) {
            lastBalancePaise = parsePaise(openingBalMatch.groupValues[1])
        }

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            val upper = line.uppercase()
            if (upper.contains("SAVINGS ACCOUNT TRANSACTIONS") || upper.contains("WITHDRAWAL (DR.)") || upper.contains("DEPOSIT (CR.)") || upper.contains("CHQ/REF. NO.")) {
                inTxSection = true
                continue
            }

            if (!inTxSection) continue

            // End of transaction table markers
            if (upper.contains("TOTAL WITHDRAWAL") || upper.contains("TOTAL DEPOSIT") || upper.contains("CLOSING BALANCE") || upper.contains("TRANSACTION TOTAL") || upper.contains("END OF STATEMENT")) {
                if (currentCollector != null) {
                    val (tx, newBal) = currentCollector.build(lastBalancePaise)
                    if (tx != null) {
                        transactions.add(tx)
                        lastBalancePaise = newBal
                    }
                    currentCollector = null
                }
                break
            }

            val rowMatch = rowStartRegex.find(line)
            if (rowMatch != null) {
                val dateStr = rowMatch.groupValues[1].replace(Regex("""\s+"""), " ").trim()
                val parsedDate = runCatching { LocalDate.parse(dateStr, dateFormatter) }.getOrNull()

                if (parsedDate != null) {
                    if (currentCollector != null) {
                        val (tx, newBal) = currentCollector.build(lastBalancePaise)
                        if (tx != null) {
                            transactions.add(tx)
                            lastBalancePaise = newBal
                        }
                    }

                    currentCollector = KotakTxCollector().apply {
                        dateEpochDay = parsedDate.toEpochDay()
                    }

                    val rest = line.substring(rowMatch.range.last + 1).trim()
                    if (rest.isNotBlank()) {
                        currentCollector.rawLines.add(rest)
                    }
                    continue
                }
            }

            // Continuation line
            if (currentCollector != null) {
                currentCollector.rawLines.add(line)
            }
        }

        if (currentCollector != null) {
            val (tx, _) = currentCollector.build(lastBalancePaise)
            if (tx != null) transactions.add(tx)
        }

        return transactions
    }
}
