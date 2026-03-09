package com.ghatana.platform.core.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;
import java.time.temporal.TemporalAdjusters;
import java.util.Date;
import java.util.TimeZone;

/**
 * Date and time manipulation utilities for Java 8+ java.time API with UTC normalization.
 * 
 * Provides production-grade temporal operations including UTC conversion, date/time parsing,
 * formatting with ISO-8601 standards, range calculations, and legacy java.util.Date interoperability.
 * 
 * Thread-safe: All methods are static and stateless. DateTimeFormatter instances are immutable.
 *
 * @doc.type class
 * @doc.purpose Date and time manipulation utilities with UTC normalization for Java 8+ java.time API
 * @doc.layer platform
 * @doc.pattern Utility
 */
public final class DateTimeUtils {

    // Common date/time formatters (thread-safe, immutable)
    public static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter ISO_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_TIME;
    public static final DateTimeFormatter ISO_DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    public static final DateTimeFormatter ISO_OFFSET_DATETIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    public static final DateTimeFormatter ISO_ZONED_DATETIME_FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

    // Common date/time patterns
    public static final String DATE_PATTERN = "yyyy-MM-dd";
    public static final String TIME_PATTERN = "HH:mm:ss";
    public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    public static final String TIMESTAMP_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX";

    // Common time zones
    public static final ZoneId UTC = ZoneId.of("UTC");
    public static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private DateTimeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Get current date in system timezone.
     */
    @NotNull
    public static LocalDate today() {
        return LocalDate.now();
    }

    /**
     * Get current date-time in system timezone.
     */
    @NotNull
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * Get current date-time in UTC.
     * Use this for database persistence.
     */
    @NotNull
    public static OffsetDateTime nowUtc() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Convert local date-time to UTC.
     */
    @Nullable
    public static OffsetDateTime toUtc(@Nullable LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC)
                .toOffsetDateTime();
    }

    /**
     * Get start of day in UTC (00:00:00.000000000Z).
     */
    @Nullable
    public static OffsetDateTime toStartOfDayUtc(@Nullable LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime();
    }

    /**
     * Get end of day in UTC (23:59:59.999999999Z).
     */
    @Nullable
    public static OffsetDateTime toEndOfDayUtc(@Nullable LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toOffsetDateTime().minusNanos(1);
    }

    /**
     * Get start of day in system timezone.
     */
    @Nullable
    public static LocalDateTime startOfDay(@Nullable LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

    /**
     * Get end of day in system timezone.
     */
    @Nullable
    public static LocalDateTime endOfDay(@Nullable LocalDate date) {
        return date != null ? date.plusDays(1).atStartOfDay().minusNanos(1) : null;
    }

    /**
     * Get first day of month.
     */
    @Nullable
    public static LocalDate startOfMonth(@Nullable LocalDate date) {
        return date != null ? date.with(TemporalAdjusters.firstDayOfMonth()) : null;
    }

    /**
     * Get last day of month.
     */
    @Nullable
    public static LocalDate endOfMonth(@Nullable LocalDate date) {
        return date != null ? date.with(TemporalAdjusters.lastDayOfMonth()) : null;
    }

    /**
     * Convert LocalDateTime to legacy java.util.Date.
     */
    @Nullable
    public static Date toDate(@Nullable LocalDateTime dateTime) {
        return dateTime != null ? 
                Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant()) : null;
    }

    /**
     * Convert legacy java.util.Date to LocalDateTime.
     */
    @Nullable
    public static LocalDateTime toLocalDateTime(@Nullable Date date) {
        return date != null ? 
                date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : null;
    }

    /**
     * Parse date from string with custom pattern.
     */
    @Nullable
    public static LocalDate parseDate(@Nullable String dateString, @NotNull String pattern) {
        if (StringUtils.isBlank(dateString)) {
            return null;
        }
        return LocalDate.parse(dateString, DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Parse date-time from string with custom pattern.
     */
    @Nullable
    public static LocalDateTime parseDateTime(@Nullable String dateTimeString, @NotNull String pattern) {
        if (StringUtils.isBlank(dateTimeString)) {
            return null;
        }
        return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * Format temporal with predefined formatter.
     */
    @Nullable
    public static String format(@Nullable TemporalAccessor temporal, @NotNull DateTimeFormatter formatter) {
        return temporal != null ? formatter.format(temporal) : null;
    }

    /**
     * Format temporal with custom pattern.
     */
    @Nullable
    public static String format(@Nullable TemporalAccessor temporal, @NotNull String pattern) {
        return temporal != null ? DateTimeFormatter.ofPattern(pattern).format(temporal) : null;
    }

    /**
     * Calculate days between two dates (absolute value).
     */
    public static long daysBetween(@NotNull LocalDate start, @NotNull LocalDate end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Both start and end dates must not be null");
        }
        return Math.abs(Period.between(start, end).getDays());
    }

    /**
     * Calculate hours between two temporal values (absolute value).
     */
    public static long hoursBetween(@NotNull Temporal start, @NotNull Temporal end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Both start and end times must not be null");
        }
        return Math.abs(Duration.between(start, end).toHours());
    }

    /**
     * Check if date is within range (inclusive).
     */
    public static boolean isWithinRange(@Nullable LocalDate date, @NotNull LocalDate start, @NotNull LocalDate end) {
        if (date == null || start == null || end == null) {
            return false;
        }
        return !date.isBefore(start) && !date.isAfter(end);
    }

    /**
     * Check if date-time is within range (inclusive).
     */
    public static boolean isWithinRange(@Nullable LocalDateTime dateTime, @NotNull LocalDateTime start, @NotNull LocalDateTime end) {
        if (dateTime == null || start == null || end == null) {
            return false;
        }
        return !dateTime.isBefore(start) && !dateTime.isAfter(end);
    }

    /**
     * Get current time in milliseconds (Unix epoch).
     */
    public static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    /**
     * Get current time in seconds (Unix epoch).
     */
    public static long currentTimeSeconds() {
        return System.currentTimeMillis() / 1000L;
    }

    /**
     * Get system timezone offset in minutes.
     */
    public static int getSystemTimezoneOffsetMinutes() {
        return TimeZone.getDefault().getOffset(System.currentTimeMillis()) / (60 * 1000);
    }
}
