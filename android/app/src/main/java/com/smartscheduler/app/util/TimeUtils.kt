package com.smartscheduler.app.util

import java.util.Locale

object TimeUtils {

    /**
     * Converts integer minutes from midnight (0 to 1439) into "HH:mm" formatted 24-hour string.
     * e.g., 540 -> "09:00", 1380 -> "23:00", 720 -> "12:00", 760 -> "12:40"
     */
    fun minutesToFormattedTime(minutes: Int): String {
        val totalMinutes = minutes.coerceIn(0, 1439)
        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        return String.format(Locale.US, "%02d:%02d", hours, mins)
    }

    /**
     * Converts integer minutes from midnight (0 to 1439) into 12-hour formatted string with AM/PM.
     * e.g., 720 -> "12:00 PM", 760 -> "12:40 PM", 545 -> "09:05 AM", 0 -> "12:00 AM", 30 -> "12:30 AM"
     */
    fun minutesTo12HourTime(minutes: Int): String {
        val totalMinutes = minutes.coerceIn(0, 1439)
        val h24 = totalMinutes / 60
        val mins = totalMinutes % 60
        val isPm = h24 >= 12
        val h12 = when {
            h24 == 0 -> 12
            h24 > 12 -> h24 - 12
            else -> h24
        }
        val amPm = if (isPm) "PM" else "AM"
        return String.format(Locale.US, "%02d:%02d %s", h12, mins, amPm)
    }

    /**
     * Robust parser for time strings in:
     * 1) 12-hour format: "12:00 PM", "12:40 PM", "09:05 AM", "12.00 PM", "1:30 pm"
     * 2) 24-hour format: "12:40", "12.40", "09:05", "9:5", "23:00"
     *
     * Handles colon ':', dot '.', extra spaces, case-insensitivity.
     * Returns integer minutes from midnight (0..1439), or null if invalid.
     */
    fun timeStringToMinutes(timeStr: String?): Int? {
        if (timeStr.isNullOrBlank()) return null
        val cleaned = timeStr.trim().replace("\\s+".toRegex(), " ")

        // 1. Try 12-hour pattern: "12:00 PM", "12.40 am", "9:05PM"
        val amPmRegex = """^(\d{1,2})[:.](\d{1,2})\s*([aApP][mM])$""".toRegex()
        val amPmMatch = amPmRegex.matchEntire(cleaned)
        if (amPmMatch != null) {
            val (hStr, mStr, meridiem) = amPmMatch.destructured
            val rawH = hStr.toIntOrNull() ?: return null
            val mins = mStr.toIntOrNull() ?: return null
            if (rawH !in 1..12 || mins !in 0..59) return null

            val isPm = meridiem.uppercase(Locale.US) == "PM"
            val h24 = when {
                isPm && rawH < 12 -> rawH + 12
                !isPm && rawH == 12 -> 0
                else -> rawH
            }
            return h24 * 60 + mins
        }

        // 2. Try 24-hour pattern: "12:40", "12.40", "09:05", "9:5"
        val h24Regex = """^(\d{1,2})[:.](\d{1,2})$""".toRegex()
        val h24Match = h24Regex.matchEntire(cleaned)
        if (h24Match != null) {
            val (hStr, mStr) = h24Match.destructured
            val hours = hStr.toIntOrNull() ?: return null
            val mins = mStr.toIntOrNull() ?: return null
            if (hours in 0..23 && mins in 0..59) {
                return hours * 60 + mins
            }
            return null
        }

        return null
    }

    /**
     * Calculates end time in minutes from midnight given start time and duration.
     * e.g., 720 (12:00 PM) + 40 mins = 760 (12:40 PM).
     */
    fun calculateEndTime(startMinutes: Int, durationMinutes: Int): Int {
        return startMinutes + durationMinutes
    }

    /**
     * Checks if two time intervals [start1, end1) and [start2, end2) overlap.
     * Two intervals overlap if and only if max(start1, start2) < min(end1, end2).
     */
    fun hasOverlap(start1: Int, end1: Int, start2: Int, end2: Int): Boolean {
        return maxOf(start1, start2) < minOf(end1, end2)
    }

    /**
     * Validates if a task window [start, end] is within a day window [dayStart, dayEnd].
     */
    fun isWithinDayWindow(start: Int, end: Int, dayStart: Int, dayEnd: Int): Boolean {
        return start >= dayStart && end <= dayEnd && start < end
    }

    /**
     * Formats duration in minutes into a readable hour:min representation.
     * e.g., 690 -> "11h 30m", 60 -> "1h", 90 -> "1h 30m", 20 -> "20m", 0 -> "0m"
     */
    fun formatDuration(durationMinutes: Int): String {
        val total = maxOf(0, durationMinutes)
        val hours = total / 60
        val mins = total % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 -> "${hours}h"
            else -> "${mins}m"
        }
    }

    /**
     * Formats an ISO-8601 date-time string (e.g. "2026-10-22T05:45:08.086044" or "2026-10-22T05:45:08Z")
     * into a clean, human-readable format adjusted to device local time (e.g. "22 Oct 2026, 11:15 AM").
     */
    fun formatDateTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return ""
        try {
            val cleaned = isoString.trim()
            val instant = when {
                cleaned.endsWith("Z", ignoreCase = true) -> java.time.Instant.parse(cleaned)
                cleaned.contains("+") || (cleaned.contains("-") && cleaned.lastIndexOf('-') > 10) -> {
                    java.time.OffsetDateTime.parse(cleaned).toInstant()
                }
                else -> {
                    // Backend uses datetime.utcnow() -> parse as UTC LocalDateTime and convert to Instant
                    val normalized = cleaned.replace(" ", "T")
                    val ldt = java.time.LocalDateTime.parse(normalized)
                    ldt.atZone(java.time.ZoneOffset.UTC).toInstant()
                }
            }
            val localZoned = instant.atZone(java.time.ZoneId.systemDefault())
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.US)
            return localZoned.format(formatter)
        } catch (_: Exception) {
            // Graceful fallback: return formatted date/time from split
            try {
                val parts = isoString.trim().split("T")
                if (parts.size == 2) {
                    val datePart = parts[0]
                    val timePart = parts[1].substringBefore(".")
                    return "$datePart, $timePart"
                }
            } catch (_: Exception) {}
            return isoString
        }
    }

    /**
     * Checks if the given ISO date-time string falls within the current calendar month and year.
     * Used for schedule history monthly lifecycle.
     */
    fun isCurrentMonth(isoString: String?): Boolean {
        if (isoString.isNullOrBlank()) return true
        return try {
            val cleaned = isoString.trim()
            val instant = when {
                cleaned.endsWith("Z", ignoreCase = true) -> java.time.Instant.parse(cleaned)
                cleaned.contains("+") || (cleaned.contains("-") && cleaned.lastIndexOf('-') > 10) -> {
                    java.time.OffsetDateTime.parse(cleaned).toInstant()
                }
                else -> {
                    val normalized = cleaned.replace(" ", "T")
                    val ldt = java.time.LocalDateTime.parse(normalized)
                    ldt.atZone(java.time.ZoneOffset.UTC).toInstant()
                }
            }
            val date = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val now = java.time.LocalDate.now()
            date.year == now.year && date.month == now.month
        } catch (_: Exception) {
            true
        }
    }
}
