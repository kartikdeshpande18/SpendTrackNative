package com.spendtrack.app.importer.parser

import com.spendtrack.app.importer.model.Direction
import com.spendtrack.app.importer.model.ImporterTransactionType
import com.spendtrack.app.importer.model.NormalizedTransaction
import com.spendtrack.app.importer.normalization.MerchantNormalizer
import com.spendtrack.app.ui.parsePaise
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class IciciSavingsParser : StatementParser {
    override val identifier = "ICICI_SAVINGS_V1"

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.US)
    private val dateRegex = Regex("""^\s*\d+\s+(\d{2}\.\d{2}\.\d{4})\s+""")
    private val amountRegex = Regex("""([\d,]+\.\d{2})\s+([\d,]+\.\d{2})""")

    override fun parse(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        // If positional data exists in rawText, parse positional lines
        if (rawText.contains("===POSITIONAL===")) {
            val posTxns = parsePositional(rawText, sourceFilename)
            if (posTxns.isNotEmpty()) return posTxns
        }

        return parseText(rawText, sourceFilename)
    }

    private fun parseText(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        val lines = rawText.lines()
        val transactions = mutableListOf<NormalizedTransaction>()

        class TxBuilder {
            var dateEpochDay: Long = 0
            var amountPaise: Long = 0
            var direction: Direction = Direction.DEBIT
            var balanceAfterPaise: Long = 0
            val narrationLines = mutableListOf<String>()
            var lastBalanceInferred: Long? = null

            fun build(): NormalizedTransaction? {
                if (narrationLines.isEmpty()) return null

                var foundMatch = false
                for (i in narrationLines.indices) {
                    val match = amountRegex.find(narrationLines[i])
                    if (match != null) {
                        val amountStr = match.groupValues[1]
                        val balanceStr = match.groupValues[2]

                        val parsedAmt = parsePaise(amountStr)
                        val parsedBal = parsePaise(balanceStr)

                        balanceAfterPaise = parsedBal
                        amountPaise = parsedAmt

                        // Determine direction
                        val fullNarration = narrationLines.joinToString(" ")
                        val isSelf = fullNarration.contains("Self Trans", ignoreCase = true) ||
                                fullNarration.contains("/Self", ignoreCase = true)
                        val isSalary = fullNarration.contains("Salary", ignoreCase = true)

                        if (lastBalanceInferred != null) {
                            if (parsedBal == lastBalanceInferred!! + parsedAmt) {
                                direction = Direction.CREDIT
                            } else if (parsedBal == lastBalanceInferred!! - parsedAmt) {
                                direction = Direction.DEBIT
                            } else {
                                direction = if (parsedBal > lastBalanceInferred!!) Direction.CREDIT else Direction.DEBIT
                            }
                        } else {
                            // First transaction
                            if (parsedBal == parsedAmt || isSalary || fullNarration.contains("Deposit", ignoreCase = true) || fullNarration.contains("CR", ignoreCase = true)) {
                                direction = Direction.CREDIT
                            } else {
                                direction = Direction.DEBIT
                            }
                        }

                        narrationLines[i] = narrationLines[i].replace(match.value, "").trim()
                        foundMatch = true
                        break
                    }
                }

                if (!foundMatch || amountPaise == 0L) {
                    return null
                }

                val cleanNarration = narrationLines.joinToString(" ")
                    .replace(Regex("""\s+"""), " ")
                    .trim()

                // Extract reference number
                var ref: String? = null
                val rrnMatch = Regex("""\b(\d{12})\b""").find(cleanNarration)
                if (rrnMatch != null) {
                    ref = rrnMatch.groupValues[1]
                }

                val isSelf = cleanNarration.contains("Self Trans", ignoreCase = true) ||
                        cleanNarration.contains("/Self", ignoreCase = true)
                val isSalary = cleanNarration.contains("Salary", ignoreCase = true)

                val txType = when {
                    isSelf -> ImporterTransactionType.TRANSFER
                    isSalary -> ImporterTransactionType.INCOME
                    direction == Direction.CREDIT -> ImporterTransactionType.INCOME
                    else -> ImporterTransactionType.EXPENSE
                }

                val normMerchant = MerchantNormalizer.normalize(cleanNarration)

                return NormalizedTransaction(
                    dateEpochDay = dateEpochDay,
                    amountPaise = amountPaise,
                    direction = direction,
                    rawNarration = cleanNarration,
                    normalizedMerchant = normMerchant,
                    referenceNumber = ref,
                    transactionType = txType,
                    sourceAccountId = 0L,
                    balanceAfterPaise = balanceAfterPaise,
                    sourceFilename = sourceFilename,
                    parserIdentifier = identifier,
                    duplicateFingerprint = "${dateEpochDay}_${amountPaise}_${direction.name}_${cleanNarration.hashCode()}"
                )
            }
        }

        var currentTx: TxBuilder? = null
        val bufferedNarration = mutableListOf<String>()
        var passedHeader = false
        var lastBalance: Long? = null

        for (line in lines) {
            if (line.contains("Statement of Transactions") || line.contains("Cheque Number Transaction Remarks")) {
                passedHeader = true
            }
            if (!passedHeader) continue

            val dateMatch = dateRegex.find(line)
            if (dateMatch != null) {
                if (currentTx != null) {
                    if (bufferedNarration.isNotEmpty()) {
                        currentTx.narrationLines.addAll(bufferedNarration)
                    }
                    currentTx.lastBalanceInferred = lastBalance
                    val built = currentTx.build()
                    if (built != null) {
                        transactions.add(built)
                        lastBalance = built.balanceAfterPaise
                    }
                }

                val newTx = TxBuilder()
                val dateStr = dateMatch.groupValues[1]
                newTx.dateEpochDay = LocalDate.parse(dateStr, dateFormatter).toEpochDay()

                val rest = line.substring(dateMatch.range.last + 1).trim()
                if (rest.isNotBlank()) newTx.narrationLines.add(rest)

                bufferedNarration.clear()
                currentTx = newTx
            } else {
                val trimmed = line.trim()
                if (trimmed.isNotBlank() && !trimmed.startsWith("Legends for transactions") &&
                    !trimmed.contains("Sincerly") && !trimmed.contains("Page No") && !trimmed.contains("www.icici.bank.in")) {
                    bufferedNarration.add(trimmed)
                }
                if (trimmed.startsWith("Sincerly") || trimmed.startsWith("Legends for")) {
                    break
                }
            }
        }

        if (currentTx != null) {
            currentTx.narrationLines.addAll(bufferedNarration)
            currentTx.lastBalanceInferred = lastBalance
            val built = currentTx.build()
            if (built != null) {
                transactions.add(built)
            }
        }

        return transactions
    }

    private class PosTxCollector {
        var dateEpochDay: Long = 0
        var amountPaise: Long = 0
        var direction: Direction = Direction.DEBIT
        var balancePaise: Long = 0
        val narrationTokens = mutableListOf<String>()
    }

    private fun parsePositional(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        val posSection = rawText.substringAfter("===POSITIONAL===")
        val lines = posSection.lines()
        val transactions = mutableListOf<NormalizedTransaction>()

        var currentCollector: PosTxCollector? = null

        // Withdrawal column is around x = 390..450, Deposit is around x = 460..520, Balance is around x = 525..570
        for (line in lines) {
            if (line.isBlank()) continue
            val tokens = line.split("~").map { tokenStr ->
                val parts = tokenStr.split("|")
                val text = parts.getOrNull(0) ?: ""
                val x = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                val y = parts.getOrNull(2)?.toFloatOrNull() ?: 0f
                Triple(text.trim(), x, y)
            }

            // Check if this line starts a new transaction: e.g. "1|30.1|253.02002~05.09.2026|61.4|..."
            val firstToken = tokens.firstOrNull()?.first ?: ""
            val secondToken = tokens.getOrNull(1)?.first ?: ""
            val isRowStart = firstToken.toIntOrNull() != null && Regex("""\d{2}\.\d{2}\.\d{4}""").matches(secondToken)

            if (isRowStart) {
                if (currentCollector != null && currentCollector.amountPaise > 0L) {
                    val built = buildFromPositional(currentCollector, sourceFilename)
                    if (built != null) transactions.add(built)
                }

                currentCollector = PosTxCollector().apply {
                    dateEpochDay = LocalDate.parse(secondToken, dateFormatter).toEpochDay()
                }

                // Add other tokens on this line to narration or amounts
                for (i in 2 until tokens.size) {
                    val (text, x, _) = tokens[i]
                    if (text.isBlank()) continue
                    processPosToken(text, x, currentCollector)
                }
            } else if (currentCollector != null) {
                // Continuation line
                if (firstToken.startsWith("Sincerly") || firstToken.startsWith("Legends for") || firstToken.startsWith("www.icici")) {
                    break
                }
                for (token in tokens) {
                    val (text, x, _) = token
                    if (text.isBlank()) continue
                    processPosToken(text, x, currentCollector)
                }
            }
        }

        if (currentCollector != null && currentCollector.amountPaise > 0L) {
            val built = buildFromPositional(currentCollector, sourceFilename)
            if (built != null) transactions.add(built)
        }

        return transactions
    }

    private fun processPosToken(text: String, x: Float, collector: PosTxCollector) {
        val isAmount = Regex("""^\d[\d,]*\.\d{2}$""").matches(text)
        if (isAmount) {
            if (x >= 525f) {
                collector.balancePaise = parsePaise(text)
            } else if (x >= 455f) {
                // Deposit column
                collector.amountPaise = parsePaise(text)
                collector.direction = Direction.CREDIT
            } else if (x in 380f..454.9f) {
                // Withdrawal column
                collector.amountPaise = parsePaise(text)
                collector.direction = Direction.DEBIT
            } else {
                collector.narrationTokens.add(text)
            }
        } else {
            collector.narrationTokens.add(text)
        }
    }

    private fun buildFromPositional(collector: PosTxCollector, sourceFilename: String): NormalizedTransaction? {
        val cleanNarration = collector.narrationTokens.joinToString(" ")
            .replace(Regex("""\s+"""), " ")
            .trim()

        var ref: String? = null
        val rrnMatch = Regex("""\b(\d{12})\b""").find(cleanNarration)
        if (rrnMatch != null) {
            ref = rrnMatch.groupValues[1]
        }

        val isSelf = cleanNarration.contains("Self Trans", ignoreCase = true) ||
                cleanNarration.contains("/Self", ignoreCase = true)
        val isSalary = cleanNarration.contains("Salary", ignoreCase = true)

        val txType = when {
            isSelf -> ImporterTransactionType.TRANSFER
            isSalary -> ImporterTransactionType.INCOME
            collector.direction == Direction.CREDIT -> ImporterTransactionType.INCOME
            else -> ImporterTransactionType.EXPENSE
        }

        val normMerchant = MerchantNormalizer.normalize(cleanNarration)

        return NormalizedTransaction(
            dateEpochDay = collector.dateEpochDay,
            amountPaise = collector.amountPaise,
            direction = collector.direction,
            rawNarration = cleanNarration,
            normalizedMerchant = normMerchant,
            referenceNumber = ref,
            transactionType = txType,
            sourceAccountId = 0L,
            balanceAfterPaise = collector.balancePaise,
            sourceFilename = sourceFilename,
            parserIdentifier = identifier,
            duplicateFingerprint = "${collector.dateEpochDay}_${collector.amountPaise}_${collector.direction.name}_${cleanNarration.hashCode()}"
        )
    }
}
