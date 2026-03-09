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
    void testToday() {
        LocalDate today = DateTimeUtils.today();
        assertNotNull(today);
        assertEquals(LocalDate.now(), today);
    }

    @Test
    void testNow() {
        LocalDateTime now = DateTimeUtils.now();
        assertNotNull(now);
        assertTrue(now.isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void testNowUtc() {
        OffsetDateTime nowUtc = DateTimeUtils.nowUtc();
        assertNotNull(nowUtc);
        assertEquals(ZoneOffset.UTC, nowUtc.getOffset());
    }

    @Test
    void testToUtc() {
        LocalDateTime local = LocalDateTime.of(2025, 1, 5, 14, 30, 0);
        OffsetDateTime utc = DateTimeUtils.toUtc(local);
        
        assertNotNull(utc);
        assertEquals(ZoneOffset.UTC, utc.getOffset());
    }

    @Test
    void testToUtcWithNull() {
        assertNull(DateTimeUtils.toUtc(null));
    }

    @Test
    void testToStartOfDayUtc() {
        LocalDate date = LocalDate.of(2025, 1, 5);
        OffsetDateTime startOfDay = DateTimeUtils.toStartOfDayUtc(date);
        
        assertNotNull(startOfDay);
        assertEquals(0, startOfDay.getHour());
        assertEquals(0, startOfDay.getMinute());
        assertEquals(0, startOfDay.getSecond());
        assertEquals(0, startOfDay.getNano());
        assertEquals(ZoneOffset.UTC, startOfDay.getOffset());
    }

    @Test
    void testToEndOfDayUtc() {
        LocalDate date = LocalDate.of(2025, 1, 5);
        OffsetDateTime endOfDay = DateTimeUtils.toEndOfDayUtc(date);
        
        assertNotNull(endOfDay);
        assertEquals(23, endOfDay.getHour());
        assertEquals(59, endOfDay.getMinute());
        assertEquals(59, endOfDay.getSecond());
        assertEquals(999999999, endOfDay.getNano());
        assertEquals(ZoneOffset.UTC, endOfDay.getOffset());
    }

    @Test
    void testStartOfDay() {
        LocalDate date = LocalDate.of(2025, 1, 5);
        LocalDateTime startOfDay = DateTimeUtils.startOfDay(date);
        
        assertNotNull(startOfDay);
        assertEquals(0, startOfDay.getHour());
        assertEquals(0, startOfDay.getMinute());
        assertEquals(0, startOfDay.getSecond());
    }

    @Test
    void testEndOfDay() {
        LocalDate date = LocalDate.of(2025, 1, 5);
        LocalDateTime endOfDay = DateTimeUtils.endOfDay(date);
        
        assertNotNull(endOfDay);
        assertEquals(23, endOfDay.getHour());
        assertEquals(59, endOfDay.getMinute());
        assertEquals(59, endOfDay.getSecond());
    }

    @Test
    void testStartOfMonth() {
        LocalDate date = LocalDate.of(2025, 1, 15);
        LocalDate startOfMonth = DateTimeUtils.startOfMonth(date);
        
        assertNotNull(startOfMonth);
        assertEquals(1, startOfMonth.getDayOfMonth());
        assertEquals(1, startOfMonth.getMonthValue());
        assertEquals(2025, startOfMonth.getYear());
    }

    @Test
    void testEndOfMonth() {
        LocalDate date = LocalDate.of(2025, 1, 15);
        LocalDate endOfMonth = DateTimeUtils.endOfMonth(date);
        
        assertNotNull(endOfMonth);
        assertEquals(31, endOfMonth.getDayOfMonth());
        assertEquals(1, endOfMonth.getMonthValue());
        assertEquals(2025, endOfMonth.getYear());
    }

    @Test
    void testEndOfMonthFebruary() {
        LocalDate date = LocalDate.of(2025, 2, 15);
        LocalDate endOfMonth = DateTimeUtils.endOfMonth(date);
        
        assertNotNull(endOfMonth);
        assertEquals(28, endOfMonth.getDayOfMonth()); // 2025 is not a leap year
    }

    @Test
    void testToDateAndBack() {
        LocalDateTime original = LocalDateTime.of(2025, 1, 5, 14, 30, 0);
        Date legacyDate = DateTimeUtils.toDate(original);
        LocalDateTime converted = DateTimeUtils.toLocalDateTime(legacyDate);
        
        assertNotNull(legacyDate);
        assertNotNull(converted);
        assertEquals(original.withNano(0), converted.withNano(0)); // Date has millisecond precision
    }

    @Test
    void testToDateWithNull() {
        assertNull(DateTimeUtils.toDate(null));
    }

    @Test
    void testToLocalDateTimeWithNull() {
        assertNull(DateTimeUtils.toLocalDateTime(null));
    }

    @Test
    void testParseDate() {
        String dateString = "2025-01-05";
        LocalDate parsed = DateTimeUtils.parseDate(dateString, DateTimeUtils.DATE_PATTERN);
        
        assertNotNull(parsed);
        assertEquals(2025, parsed.getYear());
        assertEquals(1, parsed.getMonthValue());
        assertEquals(5, parsed.getDayOfMonth());
    }

    @Test
    void testParseDateWithNull() {
        assertNull(DateTimeUtils.parseDate(null, DateTimeUtils.DATE_PATTERN));
    }

    @Test
    void testParseDateWithBlank() {
        assertNull(DateTimeUtils.parseDate("", DateTimeUtils.DATE_PATTERN));
        assertNull(DateTimeUtils.parseDate("   ", DateTimeUtils.DATE_PATTERN));
    }

    @Test
    void testParseDateTime() {
        String dateTimeString = "2025-01-05 14:30:00";
        LocalDateTime parsed = DateTimeUtils.parseDateTime(dateTimeString, DateTimeUtils.DATETIME_PATTERN);
        
        assertNotNull(parsed);
        assertEquals(2025, parsed.getYear());
        assertEquals(1, parsed.getMonthValue());
        assertEquals(5, parsed.getDayOfMonth());
        assertEquals(14, parsed.getHour());
        assertEquals(30, parsed.getMinute());
        assertEquals(0, parsed.getSecond());
    }

    @Test
    void testParseDateTimeWithNull() {
        assertNull(DateTimeUtils.parseDateTime(null, DateTimeUtils.DATETIME_PATTERN));
    }

    @Test
    void testFormatWithFormatter() {
        LocalDate date = LocalDate.of(2025, 1, 5);
        String formatted = DateTimeUtils.format(date, DateTimeUtils.ISO_DATE_FORMATTER);
        
        assertEquals("2025-01-05", formatted);
    }

    @Test
    void testFormatWithPattern() {
        LocalDate date = LocalDate.of(2025, 1, 5);
        String formatted = DateTimeUtils.format(date, "MM/dd/yyyy");
        
        assertEquals("01/05/2025", formatted);
    }

    @Test
    void testFormatWithNull() {
        assertNull(DateTimeUtils.format(null, DateTimeUtils.ISO_DATE_FORMATTER));
    }

    @Test
    void testDaysBetween() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);
        
        long days = DateTimeUtils.daysBetween(start, end);
        assertEquals(30, days);
    }

    @Test
    void testDaysBetweenReversed() {
        LocalDate start = LocalDate.of(2025, 1, 31);
        LocalDate end = LocalDate.of(2025, 1, 1);
        
        long days = DateTimeUtils.daysBetween(start, end);
        assertEquals(30, days); // Absolute value
    }

    @Test
    void testDaysBetweenWithNull() {
        LocalDate date = LocalDate.of(2025, 1, 1);
        
        assertThrows(IllegalArgumentException.class, () -> 
            DateTimeUtils.daysBetween(null, date)
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            DateTimeUtils.daysBetween(date, null)
        );
    }

    @Test
    void testHoursBetween() {
        OffsetDateTime start = OffsetDateTime.of(2025, 1, 5, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.of(2025, 1, 5, 14, 0, 0, 0, ZoneOffset.UTC);
        
        long hours = DateTimeUtils.hoursBetween(start, end);
        assertEquals(4, hours);
    }

    @Test
    void testHoursBetweenWithNull() {
        OffsetDateTime time = OffsetDateTime.now();
        
        assertThrows(IllegalArgumentException.class, () -> 
            DateTimeUtils.hoursBetween(null, time)
        );
        
        assertThrows(IllegalArgumentException.class, () -> 
            DateTimeUtils.hoursBetween(time, null)
        );
    }

    @Test
    void testIsWithinRangeDate() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);
        LocalDate middle = LocalDate.of(2025, 1, 15);
        LocalDate before = LocalDate.of(2024, 12, 31);
        LocalDate after = LocalDate.of(2025, 2, 1);
        
        assertTrue(DateTimeUtils.isWithinRange(middle, start, end));
        assertTrue(DateTimeUtils.isWithinRange(start, start, end)); // Inclusive
        assertTrue(DateTimeUtils.isWithinRange(end, start, end)); // Inclusive
        assertFalse(DateTimeUtils.isWithinRange(before, start, end));
        assertFalse(DateTimeUtils.isWithinRange(after, start, end));
    }

    @Test
    void testIsWithinRangeDateWithNull() {
        LocalDate start = LocalDate.of(2025, 1, 1);
        LocalDate end = LocalDate.of(2025, 1, 31);
        
        assertFalse(DateTimeUtils.isWithinRange(null, start, end));
        assertFalse(DateTimeUtils.isWithinRange(start, null, end));
        assertFalse(DateTimeUtils.isWithinRange(start, start, null));
    }

    @Test
    void testIsWithinRangeDateTime() {
        LocalDateTime start = LocalDateTime.of(2025, 1, 5, 10, 0);
        LocalDateTime end = LocalDateTime.of(2025, 1, 5, 14, 0);
        LocalDateTime middle = LocalDateTime.of(2025, 1, 5, 12, 0);
        LocalDateTime before = LocalDateTime.of(2025, 1, 5, 9, 0);
        LocalDateTime after = LocalDateTime.of(2025, 1, 5, 15, 0);
        
        assertTrue(DateTimeUtils.isWithinRange(middle, start, end));
        assertTrue(DateTimeUtils.isWithinRange(start, start, end));
        assertTrue(DateTimeUtils.isWithinRange(end, start, end));
        assertFalse(DateTimeUtils.isWithinRange(before, start, end));
        assertFalse(DateTimeUtils.isWithinRange(after, start, end));
    }

    @Test
    void testCurrentTimeMillis() {
        long before = System.currentTimeMillis();
        long current = DateTimeUtils.currentTimeMillis();
        long after = System.currentTimeMillis();
        
        assertTrue(current >= before);
        assertTrue(current <= after);
    }

    @Test
    void testCurrentTimeSeconds() {
        long seconds = DateTimeUtils.currentTimeSeconds();
        assertTrue(seconds > 0);
        assertTrue(seconds < System.currentTimeMillis()); // Seconds should be less than millis
    }

    @Test
    void testGetSystemTimezoneOffsetMinutes() {
        int offset = DateTimeUtils.getSystemTimezoneOffsetMinutes();
        // Offset should be reasonable (-720 to +840 minutes, i.e., -12 to +14 hours)
        assertTrue(offset >= -720 && offset <= 840);
    }

    @Test
    void testFormatters() {
        // Verify formatters are not null
        assertNotNull(DateTimeUtils.ISO_DATE_FORMATTER);
        assertNotNull(DateTimeUtils.ISO_TIME_FORMATTER);
        assertNotNull(DateTimeUtils.ISO_DATETIME_FORMATTER);
        assertNotNull(DateTimeUtils.ISO_OFFSET_DATETIME_FORMATTER);
        assertNotNull(DateTimeUtils.ISO_ZONED_DATETIME_FORMATTER);
    }

    @Test
    void testPatterns() {
        // Verify patterns are not null
        assertNotNull(DateTimeUtils.DATE_PATTERN);
        assertNotNull(DateTimeUtils.TIME_PATTERN);
        assertNotNull(DateTimeUtils.DATETIME_PATTERN);
        assertNotNull(DateTimeUtils.TIMESTAMP_PATTERN);
        assertNotNull(DateTimeUtils.ISO_8601_PATTERN);
    }

    @Test
    void testZones() {
        // Verify zones are not null
        assertNotNull(DateTimeUtils.UTC);
        assertNotNull(DateTimeUtils.SYSTEM_ZONE);
        assertEquals("UTC", DateTimeUtils.UTC.getId());
    }
}
