package com.spendtrack.app.ui

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Formats a monetary amount in paise into an Indian currency representation (e.g. ₹1,54,999.99).
 */
fun formatIndianMoney(paise: Long): String {
    val absPaise = Math.abs(paise)
    val rupees = absPaise / 100
    val remPaise = (absPaise % 100).toInt()
    val paiseStr = if (remPaise < 10) "0$remPaise" else "$remPaise"
    val rupeeStr = rupees.toString()
    val formattedRupees = if (rupeeStr.length <= 3) {
        rupeeStr
    } else {
        val lastThree = rupeeStr.takeLast(3)
        val rest = rupeeStr.dropLast(3)
        val sb = java.lang.StringBuilder(rupeeStr.length + (rest.length / 2) + 1)
        for (i in rest.indices) {
            if (i > 0 && (rest.length - i) % 2 == 0) {
                sb.append(',')
            }
            sb.append(rest[i])
        }
        sb.append(',').append(lastThree).toString()
    }
    return (if (paise < 0) "−" else "") + "₹" + formattedRupees + "." + paiseStr
}

fun money(paise: Long): String = formatIndianMoney(paise)

/**
 * Parses user-entered monetary text (in Rupees) into exact Long paise without floating-point drift.
 *
 * Examples:
 *   "0" -> 0L
 *   "1" -> 100L
 *   "1.1" -> 110L
 *   "1.15" -> 115L
 *   "10.00" -> 1000L
 *   "1500.50" -> 150050L
 *   "-50.25" -> -5025L
 *
 * Malformed or blank text safely returns 0L.
 */
fun parsePaise(text: String): Long {
    val trimmed = text.trim().removePrefix("₹").trim().replace(",", "")
    if (trimmed.isEmpty()) return 0L
    return try {
        val bd = BigDecimal(trimmed)
        bd.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (_: Exception) {
        0L
    }
}

/**
 * Formats an epoch-day integer into a localized "d MMM yyyy" string.
 *
 * If the input epochDay is invalid or out of range, safely returns "—"
 * without throwing an exception or re-running the failing date operation.
 */
fun formatEpochDate(epochDay: Long): String = try {
    val d = LocalDate.ofEpochDay(epochDay)
    "${d.dayOfMonth} ${d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${d.year}"
} catch (_: Exception) {
    "—"
}

