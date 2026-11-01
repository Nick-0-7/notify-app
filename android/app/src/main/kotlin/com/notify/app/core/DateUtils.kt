package com.notify.app.core

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

object DateUtils {

    private val MONTH_MAP = mapOf(
        "jan" to "01", "january" to "01",
        "feb" to "02", "february" to "02",
        "mar" to "03", "march" to "03",
        "apr" to "04", "april" to "04",
        "may" to "05",
        "jun" to "06", "june" to "06",
        "jul" to "07", "july" to "07",
        "aug" to "08", "august" to "08",
        "sep" to "09", "september" to "09",
        "oct" to "10", "october" to "10",
        "nov" to "11", "november" to "11",
        "dec" to "12", "december" to "12"
    )

    /**
     * Formats raw dates or strings into uniform dd/MM/yyyy format.
     * Examples:
     * - "2026-10-31T09:00:00Z" -> "31/10/2026"
     * - "2026-11-03" -> "03/11/2026"
     * - "03th November 2026 (Without Late Fee)" -> "03/11/2026 (Without Late Fee)"
     * - "10/11/2026" -> "10/11/2026"
     */
    fun formatToDdMmYyyy(rawDate: String?): String {
        if (rawDate.isNullOrBlank()) return "Recent"
        val clean = rawDate.trim()

        if (clean.equals("Today", ignoreCase = true) ||
            clean.equals("Recently", ignoreCase = true) ||
            clean.equals("Just now", ignoreCase = true)
        ) {
            return clean
        }

        // 1. Check for verbal dates like "03th November 2026" or "15 March 2026"
        val verbalPattern = Pattern.compile("""(\d{1,2})(?:st|nd|rd|th)?\s+([A-Za-z]+)\s+(\d{4})(.*)""", Pattern.CASE_INSENSITIVE)
        val verbalMatcher = verbalPattern.matcher(clean)
        if (verbalMatcher.find()) {
            val day = verbalMatcher.group(1)?.toIntOrNull()
            val monthStr = verbalMatcher.group(2)?.lowercase(Locale.US)
            val year = verbalMatcher.group(3)
            val extra = verbalMatcher.group(4) ?: ""
            if (day != null && monthStr != null && year != null) {
                val monthNum = MONTH_MAP[monthStr]
                if (monthNum != null) {
                    val formatted = String.format(Locale.US, "%02d/%s/%s", day, monthNum, year)
                    return if (extra.isNotBlank()) "$formatted ${extra.trim()}" else formatted
                }
            }
        }

        // 2. Check if already in dd/MM/yyyy or dd-MM-yyyy format
        val ddMmPattern = Pattern.compile("""^(\d{1,2})[/\-](\d{1,2})[/\-](\d{4})(.*)""")
        val ddMatcher = ddMmPattern.matcher(clean)
        if (ddMatcher.find()) {
            val d = ddMatcher.group(1)?.toIntOrNull()
            val m = ddMatcher.group(2)?.toIntOrNull()
            val y = ddMatcher.group(3)
            val rest = ddMatcher.group(4) ?: ""
            if (d != null && m != null && y != null) {
                val formatted = String.format(Locale.US, "%02d/%02d/%s", d, m, y)
                return if (rest.isNotBlank()) "$formatted ${rest.trim()}" else formatted
            }
        }

        // 3. Check for yyyy-MM-dd / yyyy-MM-ddTHH:mm:ss
        val parsers = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") },
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("yyyy/MM/dd", Locale.US)
        )
        val targetFormat = SimpleDateFormat("dd/MM/yyyy", Locale.US)

        for (parser in parsers) {
            try {
                val parsed = parser.parse(clean)
                if (parsed != null) {
                    return targetFormat.format(parsed)
                }
            } catch (_: Exception) {}
        }

        // 4. Fallback: if starts with yyyy-MM-dd
        val datePart = clean.substringBefore("T").substringBefore(" ")
        if (datePart.matches(Regex("""^\d{4}-\d{2}-\d{2}$"""))) {
            val parts = datePart.split("-")
            return "${parts[2]}/${parts[1]}/${parts[0]}"
        }

        return clean
    }

    fun formatDeadline(deadline: String?): String {
        return formatToDdMmYyyy(deadline)
    }

    fun formatPublishedDate(publishedAt: String?): String {
        return formatToDdMmYyyy(publishedAt)
    }
}
