package com.spendtrack.app.importer.detector

enum class StatementFormat {
    HDFC_SAVINGS,
    ICICI_SAVINGS,
    ICICI_CREDIT_CARD,
    SLICE_SAVINGS,
    SLICE_CREDIT_CARD,
    SLICE_ATOM,
    SBI_SAVINGS,
    KOTAK_SAVINGS,
    UNSUPPORTED
}

object StatementDetector {
    fun detectFormat(extractedText: String): StatementFormat {
        val upperText = extractedText.uppercase()

        // Slice formats — detect first because "SLICE SMALL FINANCE BANK" is a unique
        // issuer identifier that cannot appear as a counterparty in other banks' statements.
        // Other bank names (Kotak, SBI) CAN appear as UPI counterparties in Slice statements,
        // so Slice must be checked before those banks.
        if (upperText.contains("SLICE SMALL FINANCE BANK") ||
            (upperText.contains("SLICE") && (upperText.contains("ACCOUNT SAVINGS") || upperText.contains("ATOM NAME") || upperText.contains("CREDIT CARD STATEMENT")))) {
            if (upperText.contains("ATOM NAME EMERGENCY FUND") || upperText.contains("ATOM NAME")) {
                return StatementFormat.SLICE_ATOM
            }
            if (upperText.contains("CREDIT CARD STATEMENT") || (upperText.contains("SPENDS") && upperText.contains("REFUNDS & REPAYMENTS"))) {
                return StatementFormat.SLICE_CREDIT_CARD
            }
            if (upperText.contains("ACCOUNT SAVINGS") && upperText.contains("INTEREST EARNED") && upperText.contains("TOTAL DEBITS")) {
                return StatementFormat.SLICE_SAVINGS
            }
        }

        // HDFC Savings: "HDFC BANK" + column headers unique to HDFC statements
        if (upperText.contains("HDFC BANK") && upperText.contains("WITHDRAWAL AMT") && upperText.contains("DEPOSIT AMT")) {
            return StatementFormat.HDFC_SAVINGS
        }

        // ICICI Savings: "ICICI Bank" + account type, excluding credit card statements
        if (upperText.contains("ICICI BANK") && (upperText.contains("SAVING ACCOUNT") || upperText.contains("SAVINGS ACCOUNT") || upperText.contains("DEPOSIT")) && !upperText.contains("CREDIT CARD STATEMENT")) {
            return StatementFormat.ICICI_SAVINGS
        }

        // ICICI Credit Card / Amazon Pay CC
        if ((upperText.contains("ICICI BANK") && upperText.contains("CREDIT CARD STATEMENT")) || 
            upperText.contains("AMAZON PAY ICICI BANK CREDIT CARD") ||
            (upperText.contains("ICICI") && upperText.contains("REWARD POINTS"))) {
            return StatementFormat.ICICI_CREDIT_CARD
        }

        // SBI Savings: "STATE BANK OF INDIA" as issuer (not counterparty) + statement markers,
        // or "STATEMENT OF ACCOUNT" with SBI-specific markers
        if (upperText.contains("STATE BANK OF INDIA") && (upperText.contains("STATEMENT OF ACCOUNT") || upperText.contains("TXN DATE") || upperText.contains("VALUE DATE"))) {
            return StatementFormat.SBI_SAVINGS
        }
        if (upperText.contains("STATEMENT OF ACCOUNT") && (upperText.contains("SBI") || upperText.contains("SBIN") || upperText.contains("INB"))) {
            return StatementFormat.SBI_SAVINGS
        }

        // Kotak Savings: require bank name + statement-level markers.
        // "KOTAK MAHINDRA BANK" alone is too broad — it can appear as a UPI counterparty.
        if ((upperText.contains("KOTAK MAHINDRA BANK") && (upperText.contains("ACCOUNT STATEMENT") || upperText.contains("SAVINGS ACCOUNT TRANSACTIONS") || upperText.contains("WITHDRAWAL (DR.)") || upperText.contains("DEPOSIT (CR.)"))) ||
            (upperText.contains("SAVINGS ACCOUNT TRANSACTIONS") && upperText.contains("WITHDRAWAL (DR.)") && upperText.contains("DEPOSIT (CR.)")) ||
            (upperText.contains("KOTAK") && upperText.contains("SAVINGS ACCOUNT TRANSACTIONS")) ||
            (upperText.contains("KOTAK811") && upperText.contains("SAVINGS") && upperText.contains("ACCOUNT STATEMENT"))) {
            return StatementFormat.KOTAK_SAVINGS
        }

        return StatementFormat.UNSUPPORTED
    }
}
