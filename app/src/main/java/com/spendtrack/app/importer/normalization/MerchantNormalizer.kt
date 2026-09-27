package com.spendtrack.app.importer.normalization

object MerchantNormalizer {

    fun normalize(rawNarration: String): String {
        val upper = rawNarration.uppercase()
        
        if (upper.contains("SWIGGY")) return "Swiggy"
        if (upper.contains("ZOMATO")) return "Zomato"
        if (upper.contains("AMAZONGROCERY")) return "Amazon Grocery"
        if (upper.contains("D MART") || upper.contains("DMART")) return "D-Mart"
        if (upper.contains("PMPML")) return "PMPML"
        if (upper.contains("CRED")) return "CRED"
        if (upper.contains("AMAZON PAY")) return "Amazon Pay"
        
        // Remove UPI prefixes/suffixes roughly
        var normalized = rawNarration
        val upiPattern = Regex("UPI-(?:Debit|Credit)-[a-zA-Z0-9]+-(.*)")
        val match = upiPattern.find(normalized)
        if (match != null) {
            normalized = match.groupValues[1]
        }
        
        // Fallback cleanup
        normalized = normalized.split("-").firstOrNull()?.trim() ?: normalized
        normalized = normalized.split("@").firstOrNull()?.trim() ?: normalized
        
        return normalized.takeIf { it.isNotBlank() } ?: rawNarration
    }
}
