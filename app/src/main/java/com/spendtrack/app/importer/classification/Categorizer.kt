package com.spendtrack.app.importer.classification

import com.spendtrack.app.data.local.CategoryEntity
import com.spendtrack.app.importer.model.NormalizedTransaction

object Categorizer {

    fun categorize(transaction: NormalizedTransaction, existingCategories: List<CategoryEntity>): CategoryEntity? {
        val titleLower = transaction.normalizedMerchant.lowercase()
        val rawLower = transaction.rawNarration.lowercase()

        // Very basic deterministic rules as requested
        val suggestedName = when {
            titleLower.contains("swiggy") || titleLower.contains("zomato") || rawLower.contains("restaurant") -> "Food"
            titleLower.contains("d-mart") || titleLower.contains("grocery") -> "Shopping" // mapping to something standard
            titleLower.contains("pmpml") || rawLower.contains("bus") || titleLower.contains("transit") -> "Transport"
            titleLower.contains("petrol") || titleLower.contains("fuel") -> "Transport"
            titleLower.contains("rent") -> "Bills"
            titleLower.contains("sip") || rawLower.contains("mutual fund") -> "Other"
            else -> null
        }

        if (suggestedName != null) {
            val match = existingCategories.find { it.name.equals(suggestedName, ignoreCase = true) }
            if (match != null) return match
        }

        return null
    }
}
