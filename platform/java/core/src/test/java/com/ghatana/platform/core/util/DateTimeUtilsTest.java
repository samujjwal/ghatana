package com.ghatana.platform.core.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DateTimeUtils.
 */
class DateTimeUtilsTest {

    @Test
    void testToday() { // GH-90000
        LocalDate today = DateTimeUtils.today(); // GH-90000
        assertNotNull(today); // GH-90000
        assertEquals(LocalDate.now(), today); // GH-90000
    }

    @Test
    void testNow() { // GH-90000
        LocalDateTime now = DateTimeUtils.now(); // GH-90000
        assertNotNull(now); // GH-90000
        assertTrue(now.isBefore(LocalDateTime.now().plusSeconds(1))); // GH-90000
    }

    @Test
    void testNowUtc() { // GH-90000
        OffsetDateTime nowUtc = DateTimeUtils.nowUtc(); // GH-90000
        assertNotNull(nowUtc); // GH-90000
        assertEquals(ZoneOffset.UTC, nowUtc.getOffset()); // GH-90000
    }

    @Test
    void testToUtc() { // GH-90000
        LocalDateTime local = LocalDateTime.of(2025, 1, 5, 14, 30, 0); // GH-90000
        OffsetDateTime utc = DateTimeUtils.toUtc(local); // GH-90000

        assertNotNull(utc); // GH-90000
        assertEquals(ZoneOffset.UTC, utc.getOffset()); // GH-90000
    }

    @Test
    void testToUtcWithNull() { // GH-90000
        assertNull(DateTimeUtils.toUtc(null)); // GH-90000
    }

    @Test
    void testToStartOfDayUtc() { // GH-90000
        LocalDate date = LocalDate.of(2025, 1, 5); // GH-90000
        OffsetDateTime startOfDay = DateTimeUtils.toStartOfDayUtc(date); // GH-90000

        assertNotNull(startOfDay); // GH-90000
        assertEquals(0, startOfDay.getHour()); // GH-90000
        assertEquals(0, startOfDay.getMinute()); // GH-90000
        assertEquals(0, startOfDay.getSecond()); // GH-90000
        assertEquals(0, startOfDay.getNano()); // GH-90000
        assertEquals(ZoneOffset.UTC, startOfDay.getOffset()); // GH-90000
    }

    @Test
    void testToEndOfDayUtc() { // GH-90000
        LocalDate date = LocalDate.of(2025, 1, 5); // GH-90000
        OffsetDateTime endOfDay = DateTimeUtils.toEndOfDayUtc(date); // GH-90000

        assertNotNull(endOfDay); // GH-90000
        assertEquals(23, endOfDay.getHour()); // GH-90000
        assertEquals(59, endOfDay.getMinute()); // GH-90000
        assertEquals(59, endOfDay.getSecond()); // GH-90000
        assertEquals(999999999, endOfDay.getNano()); // GH-90000
        assertEquals(ZoneOffset.UTC, endOfDay.getOffset()); // GH-90000
    }

    @Test
    void testStartOfDay() { // GH-90000
        LocalDate date = LocalDate.of(2025, 1, 5); // GH-90000
        LocalDateTime startOfDay = DateTimeUtils.startOfDay(date); // GH-90000

        assertNotNull(startOfDay); // GH-90000
        assertEquals(0, startOfDay.getHour()); // GH-90000
        assertEquals(0, startOfDay.getMinute()); // GH-90000
        assertEquals(0, startOfDay.getSecond()); // GH-90000
    }

    @Test
    void testEndOfDay() { // GH-90000
        LocalDate date = LocalDate.of(2025, 1, 5); // GH-90000
        LocalDateTime endOfDay = DateTimeUtils.endOfDay(date); // GH-90000

        assertNotNull(endOfDay); // GH-90000
        assertEquals(23, endOfDay.getHour()); // GH-90000
        assertEquals(59, endOfDay.getMinute()); // GH-90000
        assertEquals(59, endOfDay.getSecond()); // GH-90000
    }

    @Test
    void testStartOfMonth() { // GH-90000
        LocalDate date = LocalDate.of(2025, 1, 15); // GH-90000
        LocalDate startOfMonth = DateTimeUtils.startOfMonth(date); // GH-90000

        assertNotNull(startOfMonth); // GH-90000
        assertEquals(1, startOfMonth.getDayOfMonth()); // GH-90000
        assertEquals(1, startOfMonth.getMonthValue()); // GH-90000
        assertEquals(2025, startOfMonth.getYear()); // GH-90000
    }

    @Test
    void testEndOfMonth() { // GH-90000
        LocalDate date = LocalDate.of(2025, 1, 15); // GH-90000
        LocalDate endOfMonth = DateTimeUtils.endOfMonth(date); // GH-90000

        assertNotNull(endOfMonth); // GH-90000
        assertEquals(31, endOfMonth.getDayOfMonth()); // GH-90000
        assertEquals(1, endOfMonth.getMonthValue()); // GH-90000
        assertEquals(2025, endOfMonth.getYear()); // GH-90000
    }

    @Test
    void testEndOfMonthFebruary() { // GH-90000
        LocalDate date = LocalDate.of(2025, 2, 15); // GH-90000
        LocalDate endOfMonth = DateTimeUtils.endOfMonth(date); // GH-90000

        assertNotNull(endOfMonth); // GH-90000
        assertEquals(28, endOfMonth.getDayOfMonth()); // 2025 is not a leap year // GH-90000
    }

    @Test
    void testToDateAndBack() { // GH-90000
        LocalDateTime original = LocalDateTime.of(2025, 1, 5, 14, 30, 0); // GH-90000
        Date legacyDate = DateTimeUtils.toDate(original); // GH-90000
        LocalDateTime converted = DateTimeUtils.toLocalDateTime(legacyDate); // GH-90000

        assertNotNull(legacyDate); // GH-90000
        assertNotNull(converted); // GH-90000
        assertEquals(original.withNano(0), converted.withNano(0)); // Date has millisecond precision // GH-90000
    }

    @Test
    void testToDateWithNull() { // GH-90000
        assertNull(DateTimeUtils.toDate(null)); // GH-90000
    }

    @Test
    void testToLocalDateTimeWithNull() { // GH-90000
        assertNull(DateTimeUtils.toLocalDateTime(null)); // GH-90000
    }

    @Test
    void testParseDate() { // GH-90000
        String dateString = "2025-01-05";
        LocalDate parsed = DateTimeUtils.parseDate(dateString, DateTimeUtils.DATE_PATTERN); // GH-90000

        assertNotNull(parsed); // GH-90000
        assertEquals(2025, parsed.getYear()); // GH-90000
        assertEquals(1, parsed.getMonthValue()); // GH-90000
        assertEquals(5, parsed.getDayOfMonth()); // GH-90000
    }

    @Test
    void testParseDateWithNull() { // GH-90000
        assertNull(DateTimeUtils.parseDate(null, DateTimeUtils.DATE_PATTERN)); // GH-90000
    }

    @Test
    void testParseDateWithBlank() { // GH-90000
        assertNull(DateTimeUtils.parseDate("", DateTimeUtils.DATE_PATTERN)); // GH-90000
        assertNull(DateTimeUtils.parseDate("   ", DateTimeUtils.DATE_PATTERN)); // GH-90000
    }

    @Test
    void testParseDateTime() { // GH-90000
        String dateTimeString = "2025-01-05 14:30:00";
        LocalDateTime parsed = DateTimeUtils.parseDateTime(dateTimeString, DateTimeUtils.DATETIME_PATTERN); // GH-90000

        assertNotNull(parsed); // GH-90000
        assertEquals(2025, parsed.getYear()); // GH-90000
        assertEquals(1, parsed.getMonthValue()); // GH-90000
        assertEquals(5, parsed.getDayOfMonth()); // GH-90000
        assertEquals(14, parsed.getHour()); // GH-90000
        assertEquals(30, parsed.getMinute()); // GH-90000
        assertEquals(0, parsed.getSecond()); // GH-90000
    }

    @Test
    void testParseDateTimeWithNull() { // GH-90000
        assertNull(DateTimeUtils.parseDateTime(null, DateTimeUtils.DATETIME_PATTERN)); // GH-90000
    }

    @Test
    void testFormatWithFormatter() { // GH-90000
        LocalDate date = LocalDate.of(2025, 1, 5); // GH-90000
        String formatted = DateTimeUtils.format(date, DateTimeUtils.ISO_DATE_FORMATTER); // GH-90000

        assertEquals("2025-01-05", formatted); // GH-90000
    }

    @Test
    void testFormatWithPattern() { // GH-90000
        LocalDate date = LocalDate.of(2025, 1, 5); // GH-90000
        String formatted = DateTimeUtils.format(date, "MM/dd/yyyy"); // GH-90000

        assertEquals("01/05/2025", formatted); // GH-90000
    }

    @Test
    void testFormatWithNull() { // GH-90000
        assertNull(DateTimeUtils.format(null, DateTimeUtils.ISO_DATE_FORMATTER)); // GH-90000
    }

    @Test
    void testDaysBetween() { // GH-90000
        LocalDate start = LocalDate.of(2025, 1, 1); // GH-90000
        LocalDate end = LocalDate.of(2025, 1, 31); // GH-90000

        long days = DateTimeUtils.daysBetween(start, end); // GH-90000
        assertEquals(30, days); // GH-90000
    }

    @Test
    void testDaysBetweenReversed() { // GH-90000
        LocalDate start = LocalDate.of(2025, 1, 31); // GH-90000
        LocalDate end = LocalDate.of(2025, 1, 1); // GH-90000

        long days = DateTimeUtils.daysBetween(start, end); // GH-90000
        assertEquals(30, days); // Absolute value // GH-90000
    }

    @Test
    void testDaysBetweenWithNull() { // GH-90000
        LocalDate date = LocalDate.of(2025, 1, 1); // GH-90000

        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            DateTimeUtils.daysBetween(null, date) // GH-90000
        );

        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            DateTimeUtils.daysBetween(date, null) // GH-90000
        );
    }

    @Test
    void testHoursBetween() { // GH-90000
        OffsetDateTime start = OffsetDateTime.of(2025, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC); // GH-90000
        OffsetDateTime end = OffsetDateTime.of(2025, 1, 5, 14, 0, 0, 0, ZoneOffset.UTC); // GH-90000

        long hours = DateTimeUtils.hoursBetween(start, end); // GH-90000
        assertEquals(4, hours); // GH-90000
    }

    @Test
    void testHoursBetweenWithNull() { // GH-90000
        OffsetDateTime time = OffsetDateTime.now(); // GH-90000

        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            DateTimeUtils.hoursBetween(null, time) // GH-90000
        );

        assertThrows(IllegalArgumentException.class, () -> // GH-90000
            DateTimeUtils.hoursBetween(time, null) // GH-90000
        );
    }

    @Test
    void testIsWithinRangeDate() { // GH-90000
        LocalDate start = LocalDate.of(2025, 1, 1); // GH-90000
        LocalDate end = LocalDate.of(2025, 1, 31); // GH-90000
        LocalDate middle = LocalDate.of(2025, 1, 15); // GH-90000
        LocalDate before = LocalDate.of(2024, 12, 31); // GH-90000
        LocalDate after = LocalDate.of(2025, 2, 1); // GH-90000

        assertTrue(DateTimeUtils.isWithinRange(middle, start, end)); // GH-90000
        assertTrue(DateTimeUtils.isWithinRange(start, start, end)); // Inclusive // GH-90000
        assertTrue(DateTimeUtils.isWithinRange(end, start, end)); // Inclusive // GH-90000
        assertFalse(DateTimeUtils.isWithinRange(before, start, end)); // GH-90000
        assertFalse(DateTimeUtils.isWithinRange(after, start, end)); // GH-90000
    }

    @Test
    void testIsWithinRangeDateWithNull() { // GH-90000
        LocalDate start = LocalDate.of(2025, 1, 1); // GH-90000
        LocalDate end = LocalDate.of(2025, 1, 31); // GH-90000

        assertFalse(DateTimeUtils.isWithinRange(null, start, end)); // GH-90000
        assertFalse(DateTimeUtils.isWithinRange(start, null, end)); // GH-90000
        assertFalse(DateTimeUtils.isWithinRange(start, start, null)); // GH-90000
    }

    @Test
    void testIsWithinRangeDateTime() { // GH-90000
        LocalDateTime start = LocalDateTime.of(2025, 1, 5, 10, 0); // GH-90000
        LocalDateTime end = LocalDateTime.of(2025, 1, 5, 14, 0); // GH-90000
        LocalDateTime middle = LocalDateTime.of(2025, 1, 5, 12, 0); // GH-90000
        LocalDateTime before = LocalDateTime.of(2025, 1, 5, 9, 0); // GH-90000
        LocalDateTime after = LocalDateTime.of(2025, 1, 5, 15, 0); // GH-90000

        assertTrue(DateTimeUtils.isWithinRange(middle, start, end)); // GH-90000
        assertTrue(DateTimeUtils.isWithinRange(start, start, end)); // GH-90000
        assertTrue(DateTimeUtils.isWithinRange(end, start, end)); // GH-90000
        assertFalse(DateTimeUtils.isWithinRange(before, start, end)); // GH-90000
        assertFalse(DateTimeUtils.isWithinRange(after, start, end)); // GH-90000
    }

    @Test
    void testCurrentTimeMillis() { // GH-90000
        long before = System.currentTimeMillis(); // GH-90000
        long current = DateTimeUtils.currentTimeMillis(); // GH-90000
        long after = System.currentTimeMillis(); // GH-90000

        assertTrue(current >= before); // GH-90000
        assertTrue(current <= after); // GH-90000
    }

    @Test
    void testCurrentTimeSeconds() { // GH-90000
        long seconds = DateTimeUtils.currentTimeSeconds(); // GH-90000
        assertTrue(seconds > 0); // GH-90000
        assertTrue(seconds < System.currentTimeMillis()); // Seconds should be less than millis // GH-90000
    }

    @Test
    void testGetSystemTimezoneOffsetMinutes() { // GH-90000
        int offset = DateTimeUtils.getSystemTimezoneOffsetMinutes(); // GH-90000
        // Offset should be reasonable (-720 to +840 minutes, i.e., -12 to +14 hours) // GH-90000
        assertTrue(offset >= -720 && offset <= 840); // GH-90000
    }

    @Test
    void testFormatters() { // GH-90000
        // Verify formatters are not null
        assertNotNull(DateTimeUtils.ISO_DATE_FORMATTER); // GH-90000
        assertNotNull(DateTimeUtils.ISO_TIME_FORMATTER); // GH-90000
        assertNotNull(DateTimeUtils.ISO_DATETIME_FORMATTER); // GH-90000
        assertNotNull(DateTimeUtils.ISO_OFFSET_DATETIME_FORMATTER); // GH-90000
        assertNotNull(DateTimeUtils.ISO_ZONED_DATETIME_FORMATTER); // GH-90000
    }

    @Test
    void testPatterns() { // GH-90000
        // Verify patterns are not null
        assertNotNull(DateTimeUtils.DATE_PATTERN); // GH-90000
        assertNotNull(DateTimeUtils.TIME_PATTERN); // GH-90000
        assertNotNull(DateTimeUtils.DATETIME_PATTERN); // GH-90000
        assertNotNull(DateTimeUtils.TIMESTAMP_PATTERN); // GH-90000
        assertNotNull(DateTimeUtils.ISO_8601_PATTERN); // GH-90000
    }

    @Test
    void testZones() { // GH-90000
        // Verify zones are not null
        assertNotNull(DateTimeUtils.UTC); // GH-90000
        assertNotNull(DateTimeUtils.SYSTEM_ZONE); // GH-90000
        assertEquals("UTC", DateTimeUtils.UTC.getId()); // GH-90000
    }
}
