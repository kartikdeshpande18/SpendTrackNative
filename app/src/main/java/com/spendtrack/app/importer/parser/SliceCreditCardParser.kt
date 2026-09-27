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

class SliceCreditCardParser : StatementParser {
    override val identifier = "SLICE_CC_V1"

    private val merchantAmountRegex = Regex("""^\s*(.*?)\s+₹([\d,]+(?:\.\d{2})?)\s*$""")
    private val dateRegex = Regex("""^\s*(?:[A-Za-z]\s+)?(\d{1,2}\s+[A-Za-z]{3}\s+['’]?\d{2,4})(?:\s+•\s*(.*))?$""")

    private val dateFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendOptional(DateTimeFormatter.ofPattern("d MMM ''yy", Locale.ENGLISH))
        .appendOptional(DateTimeFormatter.ofPattern("dd MMM ''yy", Locale.ENGLISH))
        .appendOptional(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
        .appendOptional(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH))
        .appendOptional(DateTimeFormatter.ofPattern("d MMM yy", Locale.ENGLISH))
        .appendOptional(DateTimeFormatter.ofPattern("dd MMM yy", Locale.ENGLISH))
        .toFormatter(Locale.ENGLISH)

    override fun parse(rawText: String, sourceFilename: String): List<NormalizedTransaction> {
        val lines = rawText.lines()
        val transactions = mutableListOf<NormalizedTransaction>()

        var isRefundSection = false
        var pendingMerchant: String? = null
        var pendingAmountStr: String? = null

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isBlank()) continue

            // Stop markers: end of transaction sections
            if (line.startsWith("GST details", ignoreCase = true) ||
                line.startsWith("monies", ignoreCase = true) ||
                line.startsWith("Glossary", ignoreCase = true) ||
                line.startsWith("End of Statement", ignoreCase = true)) {
                break
            }

            // Section markers
            if (line.equals("Refunds & repayments", ignoreCase = true) || line.equals("Repayments", ignoreCase = true)) {
                isRefundSection = true
                pendingMerchant = null
                pendingAmountStr = null
                continue
            } else if (line.equals("Spends", ignoreCase = true)) {
                isRefundSection = false
                pendingMerchant = null
                pendingAmountStr = null
                continue
            }

            // Check if line matches a date line (which finishes a pending transaction)
            if (pendingMerchant != null && pendingAmountStr != null) {
                val dateMatch = dateRegex.find(line)
                if (dateMatch != null) {
                    val dateRawStr = dateMatch.groupValues[1]
                        .replace("’", "'")
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                    val typeStr = dateMatch.groupValues[2].trim()

                    val parsedDate = runCatching { LocalDate.parse(dateRawStr, dateFormatter) }.getOrNull()
                    if (parsedDate != null) {
                        val amountPaise = parsePaise(pendingAmountStr)
                        val merchant = pendingMerchant

                        val isRepayment = isRefundSection ||
                                merchant.contains("Repayment", ignoreCase = true) ||
                                merchant.contains("Refund", ignoreCase = true)

                        val direction = if (isRepayment) Direction.CREDIT else Direction.DEBIT

                        val txType = when {
                            merchant.contains("Repayment", ignoreCase = true) || merchant.contains("Payment", ignoreCase = true) ->
                                ImporterTransactionType.CARD_PAYMENT
                            direction == Direction.CREDIT ->
                                ImporterTransactionType.REFUND
                            else ->
                                ImporterTransactionType.EXPENSE
                        }

                        val rawNarration = if (typeStr.isNotBlank()) "$merchant • $typeStr" else merchant
                        val cleanMerchant = merchant
                            .replace(Regex("""…$"""), "")
                            .trim()
                        val normalizedMerchant = MerchantNormalizer.normalize(cleanMerchant)

                        transactions.add(
                            NormalizedTransaction(
                                dateEpochDay = parsedDate.toEpochDay(),
                                amountPaise = amountPaise,
                                direction = direction,
                                rawNarration = rawNarration,
                                normalizedMerchant = normalizedMerchant,
                                referenceNumber = null,
                                transactionType = txType,
                                sourceAccountId = 0L,
                                balanceAfterPaise = null,
                                sourceFilename = sourceFilename,
                                parserIdentifier = identifier,
                                duplicateFingerprint = "${parsedDate.toEpochDay()}_${amountPaise}_${direction.name}_${rawNarration.hashCode()}"
                            )
                        )
                        pendingMerchant = null
                        pendingAmountStr = null
                        continue
                    }
                }

                // If line is a 1-character icon line (e.g. "S", "P", "J") or "Spends" page header, ignore and keep waiting for date line
                if (line.length <= 2 || line.equals("Spends", ignoreCase = true)) {
                    continue
                }
            }

            // Check if line is a merchant + amount line
            val mMatch = merchantAmountRegex.find(line)
            if (mMatch != null) {
                val candidateMerchant = mMatch.groupValues[1].trim()
                val candidateAmount = mMatch.groupValues[2]

                // Ignore header / summary lines
                if (!candidateMerchant.equals("Total amount due", ignoreCase = true) &&
                    !candidateMerchant.startsWith("Min amount due", ignoreCase = true) &&
                    !candidateMerchant.equals("Spends", ignoreCase = true) &&
                    !candidateMerchant.equals("Refunds & repayments", ignoreCase = true) &&
                    !candidateMerchant.equals("Interest", ignoreCase = true) &&
                    !candidateMerchant.equals("Surcharge", ignoreCase = true) &&
                    !candidateMerchant.equals("IGST", ignoreCase = true) &&
                    !candidateMerchant.equals("CGST", ignoreCase = true) &&
                    !candidateMerchant.equals("SGST", ignoreCase = true) &&
                    !candidateMerchant.equals("Statement summary", ignoreCase = true)) {
                    pendingMerchant = candidateMerchant
                    pendingAmountStr = candidateAmount
                }
            }
        }

        return transactions
    }
}
