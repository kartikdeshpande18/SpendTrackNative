package com.spendtrack.app.data.local

import org.json.JSONArray
import org.json.JSONObject

enum class SplitMethod(val displayName: String) {
    EQUAL("Equal Split"),
    CUSTOM("Custom Split")
}

data class SplitDetails(
    val payer: String = "You",
    val splitMethod: SplitMethod = SplitMethod.EQUAL,
    val participants: List<String> = emptyList(),
    val shares: Map<String, Long> = emptyMap()
)

object SharedExpenseHelper {

    val DEFAULT_PARTICIPANTS = emptyList<String>()

    fun calculateEqualSplit(totalPaise: Long, participants: List<String>): Map<String, Long> {
        if (participants.isEmpty() || totalPaise <= 0L) return emptyMap()
        val count = participants.size
        val baseShare = totalPaise / count
        var remainder = totalPaise % count
        val result = mutableMapOf<String, Long>()
        for (p in participants) {
            val extra = if (remainder > 0L) { remainder--; 1L } else 0L
            result[p] = baseShare + extra
        }
        return result
    }

    fun validateCustomSplit(totalPaise: Long, shares: Map<String, Long>): Boolean {
        if (totalPaise <= 0L || shares.isEmpty()) return false
        val sum = shares.values.sum()
        return sum == totalPaise && shares.values.all { it >= 0L }
    }

    fun serialize(details: SplitDetails): String {
        return JSONObject().apply {
            put("payer", details.payer)
            put("splitMethod", details.splitMethod.name)
            put("participants", JSONArray(details.participants))
            val sharesObj = JSONObject()
            details.shares.forEach { (name, share) -> sharesObj.put(name, share) }
            put("shares", sharesObj)
        }.toString()
    }

    fun deserialize(jsonStr: String): SplitDetails? {
        if (jsonStr.isBlank()) return null
        return runCatching {
            val root = JSONObject(jsonStr)
            val payer = root.optString("payer", "You")
            val methodStr = root.optString("splitMethod", SplitMethod.EQUAL.name)
            val method = runCatching { SplitMethod.valueOf(methodStr) }.getOrDefault(SplitMethod.EQUAL)
            val pArr = root.optJSONArray("participants") ?: JSONArray()
            val participants = mutableListOf<String>()
            for (i in 0 until pArr.length()) {
                participants.add(pArr.getString(i))
            }
            val sharesObj = root.optJSONObject("shares") ?: JSONObject()
            val shares = mutableMapOf<String, Long>()
            val keys = sharesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                shares[key] = sharesObj.getLong(key)
            }
            SplitDetails(
                payer = payer,
                splitMethod = method,
                participants = participants,
                shares = shares
            )
        }.getOrNull()
    }
}
