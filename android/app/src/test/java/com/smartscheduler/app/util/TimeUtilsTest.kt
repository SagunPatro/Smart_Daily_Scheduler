package com.smartscheduler.app.util

import org.junit.Assert.*
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun testParse12HourAmPmTimes() {
        // 12:00 PM -> 720 minutes
        assertEquals(720, TimeUtils.timeStringToMinutes("12:00 PM"))
        assertEquals(720, TimeUtils.timeStringToMinutes("12:00PM"))
        assertEquals(720, TimeUtils.timeStringToMinutes("12:00 pm"))

        // 12:40 PM -> 760 minutes
        assertEquals(760, TimeUtils.timeStringToMinutes("12:40 PM"))
        assertEquals(760, TimeUtils.timeStringToMinutes("12:40pm"))

        // 09:05 AM -> 545 minutes
        assertEquals(545, TimeUtils.timeStringToMinutes("09:05 AM"))
        assertEquals(545, TimeUtils.timeStringToMinutes("9:05 AM"))
        assertEquals(545, TimeUtils.timeStringToMinutes("09:05 am"))

        // Midnight and noon edge cases
        assertEquals(0, TimeUtils.timeStringToMinutes("12:00 AM"))
        assertEquals(40, TimeUtils.timeStringToMinutes("12:40 AM"))
        assertEquals(690, TimeUtils.timeStringToMinutes("11:30 AM"))
        assertEquals(1380, TimeUtils.timeStringToMinutes("11:00 PM"))
    }

    @Test
    fun testParseDotDelimitedTimes() {
        // Keyboard inputs using '.' instead of ':'
        assertEquals(760, TimeUtils.timeStringToMinutes("12.40"))
        assertEquals(720, TimeUtils.timeStringToMinutes("12.00"))
        assertEquals(760, TimeUtils.timeStringToMinutes("12.40 PM"))
        assertEquals(545, TimeUtils.timeStringToMinutes("09.05 AM"))
    }

    @Test
    fun testParse24HourTimes() {
        assertEquals(540, TimeUtils.timeStringToMinutes("09:00"))
        assertEquals(720, TimeUtils.timeStringToMinutes("12:00"))
        assertEquals(760, TimeUtils.timeStringToMinutes("12:40"))
        assertEquals(1380, TimeUtils.timeStringToMinutes("23:00"))
        assertEquals(0, TimeUtils.timeStringToMinutes("00:00"))
    }

    @Test
    fun testDurationBasedEndTimeCalculation() {
        // 12:00 PM (720 min) + 40 minutes = 12:40 PM (760 min)
        val startMinutes = TimeUtils.timeStringToMinutes("12:00 PM")!!
        val duration = 40
        val endMinutes = TimeUtils.calculateEndTime(startMinutes, duration)

        assertEquals(760, endMinutes)
        assertEquals("12:40", TimeUtils.minutesToFormattedTime(endMinutes))
        assertEquals("12:40 PM", TimeUtils.minutesTo12HourTime(endMinutes))
    }

    @Test
    fun testHourChangeAndMidnightTransitions() {
        // 11:45 PM (1425) + 30 mins = 1455 (> 1440, exceeds midnight)
        val lateStart = TimeUtils.timeStringToMinutes("23:45")!!
        val calculatedEnd = TimeUtils.calculateEndTime(lateStart, 30)
        assertTrue("End time exceeds 1440 minutes", calculatedEnd > 1440)

        // 10:45 AM (645) + 30 mins = 11:15 AM (675)
        val morningStart = TimeUtils.timeStringToMinutes("10:45 AM")!!
        val morningEnd = TimeUtils.calculateEndTime(morningStart, 30)
        assertEquals(675, morningEnd)
        assertEquals("11:15 AM", TimeUtils.minutesTo12HourTime(morningEnd))
    }

    @Test
    fun testOverlapValidation() {
        // Task A: 10:00 - 11:00 (600 - 660)
        // Task B: 10:30 - 11:30 (630 - 690) -> Overlap!
        assertTrue(TimeUtils.hasOverlap(600, 660, 630, 690))

        // Task C: 11:00 - 12:00 (660 - 720) -> Adjacent, not overlapping
        assertFalse(TimeUtils.hasOverlap(600, 660, 660, 720))

        // Task D: 09:00 - 10:00 (540 - 600) -> Before, not overlapping
        assertFalse(TimeUtils.hasOverlap(600, 660, 540, 600))

        // Enclosing overlap: 09:30 - 11:30 contains 10:00 - 11:00
        assertTrue(TimeUtils.hasOverlap(570, 690, 600, 660))
    }

    @Test
    fun testDayWindowValidation() {
        val dayStart = 540  // 09:00
        val dayEnd = 1380  // 23:00

        // Inside window
        assertTrue(TimeUtils.isWithinDayWindow(600, 660, dayStart, dayEnd))

        // Starts before day window
        assertFalse(TimeUtils.isWithinDayWindow(500, 600, dayStart, dayEnd))

        // Ends after day window
        assertFalse(TimeUtils.isWithinDayWindow(1350, 1400, dayStart, dayEnd))

        // Invalid range: start >= end
        assertFalse(TimeUtils.isWithinDayWindow(700, 700, dayStart, dayEnd))
        assertFalse(TimeUtils.isWithinDayWindow(750, 700, dayStart, dayEnd))
    }

    @Test
    fun testInvalidTimeStringsReturnNull() {
        assertNull(TimeUtils.timeStringToMinutes(""))
        assertNull(TimeUtils.timeStringToMinutes("   "))
        assertNull(TimeUtils.timeStringToMinutes(null))
        assertNull(TimeUtils.timeStringToMinutes("invalid"))
        assertNull(TimeUtils.timeStringToMinutes("25:00"))
        assertNull(TimeUtils.timeStringToMinutes("12:65"))
        assertNull(TimeUtils.timeStringToMinutes("13:00 PM"))
        assertNull(TimeUtils.timeStringToMinutes("12..40"))
    }

    @Test
    fun testFormatDuration() {
        // Buffer period representation (690 minutes -> 11h 30m)
        assertEquals("11h 30m", TimeUtils.formatDuration(690))

        // Full hours
        assertEquals("1h", TimeUtils.formatDuration(60))
        assertEquals("2h", TimeUtils.formatDuration(120))

        // Hours and minutes
        assertEquals("1h 30m", TimeUtils.formatDuration(90))
        assertEquals("2h 15m", TimeUtils.formatDuration(135))
        assertEquals("1h 5m", TimeUtils.formatDuration(65))

        // Less than an hour
        assertEquals("45m", TimeUtils.formatDuration(45))
        assertEquals("30m", TimeUtils.formatDuration(30))
        assertEquals("10m", TimeUtils.formatDuration(10))
        assertEquals("0m", TimeUtils.formatDuration(0))
    }

    @Test
    fun testFormatDateTime() {
        val isoStr = "2026-10-22T05:45:08.086044"
        val formatted = TimeUtils.formatDateTime(isoStr)
        assertNotNull(formatted)
        assertTrue(formatted.contains("Oct 2026") || formatted.contains("2026"))
        assertTrue(formatted.contains("AM") || formatted.contains("PM"))

        // Blank and null handling
        assertEquals("", TimeUtils.formatDateTime(""))
        assertEquals("", TimeUtils.formatDateTime(null))

        // Standard UTC ISO string with Z
        val utcStr = "2026-10-22T05:45:08Z"
        val formattedUtc = TimeUtils.formatDateTime(utcStr)
        assertNotNull(formattedUtc)
        assertTrue(formattedUtc.contains("2026"))
    }

    @Test
    fun testIsCurrentMonth() {
        val now = java.time.LocalDate.now()
        val currentMonthIso = String.format(java.util.Locale.US, "%04d-%02d-15T10:00:00", now.year, now.monthValue)
        assertTrue(TimeUtils.isCurrentMonth(currentMonthIso))

        val pastMonthIso = "2020-01-15T10:00:00"
        assertFalse(TimeUtils.isCurrentMonth(pastMonthIso))
    }
}
