package com.ghatana.appplatform.calendar.conversion;

import java.time.LocalDate;

/**
 * Bikram Sambat (BS) ↔ Gregorian calendar conversion using a compact lookup table.
 *
 * <p>Covers BS years 2070–2100. The table encodes, for each BS year:
 * <ol>
 *   <li>The Gregorian year, month, day of BS Baishakh 1 (first day of year).
 *   <li>The number of days in each of the 12 BS months.
 * </ol>
 *
 * <p>All methods are pure and stateless — no I/O, no external dependencies.
 *
 * @doc.type class
 * @doc.purpose Pure BS ↔ Gregorian calendar arithmetic using a lookup table
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class BsCalendarConversion {

    private BsCalendarConversion() {}

    // -------------------------------------------------------------------------
    // Lookup table: {bsYear, gregYear, gregMonth, gregDay, m1..m12}
    // Anchor verified: BS 2081/01/01 = April 13, 2024 (source: Nepal Rastra Bank)
    // -------------------------------------------------------------------------
    private static final int[][] CALENDAR_DATA = {
        // BS    GY    GM  GD   M1  M2  M3  M4  M5  M6  M7  M8  M9  M10 M11 M12
        {2070, 2013,  4, 14,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2071, 2014,  4, 14,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2072, 2015,  4, 14,  31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 365
        {2073, 2016,  4, 13,  31, 31, 32, 31, 31, 30, 30, 30, 29, 29, 30, 31}, // 365
        {2074, 2017,  4, 13,  30, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 364
        {2075, 2018,  4, 13,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2076, 2019,  4, 13,  31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 365
        {2077, 2020,  4, 12,  31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 365
        {2078, 2021,  4, 13,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2079, 2022,  4, 14,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2080, 2023,  4, 14,  31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 365
        {2081, 2024,  4, 13,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365 ← ANCHOR
        {2082, 2025,  4, 13,  31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 365
        {2083, 2026,  4, 13,  31, 31, 32, 31, 31, 30, 30, 30, 29, 29, 30, 31}, // 365
        {2084, 2027,  4, 13,  30, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 364
        {2085, 2028,  4, 12,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2086, 2029,  4, 12,  31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 365
        {2087, 2030,  4, 13,  31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 365
        {2088, 2031,  4, 13,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2089, 2032,  4, 12,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2090, 2033,  4, 12,  31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 365
        {2091, 2034,  4, 12,  31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 365
        {2092, 2035,  4, 12,  31, 32, 31, 32, 31, 30, 30, 30, 29, 29, 30, 30}, // 365
        {2093, 2036,  4, 11,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2094, 2037,  4, 12,  31, 31, 32, 32, 31, 30, 29, 30, 30, 29, 29, 31}, // 365
        {2095, 2038,  4, 12,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2096, 2039,  4, 12,  31, 32, 31, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 365
        {2097, 2040,  4, 11,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2098, 2041,  4, 12,  31, 31, 32, 31, 31, 31, 30, 29, 30, 29, 30, 30}, // 365
        {2099, 2042,  4, 12,  31, 31, 32, 31, 31, 30, 30, 30, 29, 29, 30, 31}, // 365
        {2100, 2043,  4, 12,  31, 31, 32, 32, 31, 30, 30, 29, 30, 29, 30, 30}, // 365
    };

    private static final int BS_YEAR_MIN = CALENDAR_DATA[0][0];
    private static final int BS_YEAR_MAX = CALENDAR_DATA[CALENDAR_DATA.length - 1][0];

    // -------------------------------------------------------------------------
    // K15-001: Gregorian → BS
    // -------------------------------------------------------------------------

    /**
     * Converts a Gregorian date to its Bikram Sambat equivalent.
     *
     * @param gregorian Gregorian date; must fall within supported lookup range
     * @return corresponding BS date
     * @throws CalendarRangeException if the date falls outside the supported range
     */
    public static BsDateComponents gregorianToBs(LocalDate gregorian) {
        // Walk from the first BS year anchor forward, counting days
        int rowIndex = 0;
        LocalDate anchor = LocalDate.of(
            CALENDAR_DATA[0][1], CALENDAR_DATA[0][2], CALENDAR_DATA[0][3]);

        if (gregorian.isBefore(anchor)) {
            throw new CalendarRangeException(
                "Date " + gregorian + " is before supported range (starts " + anchor + ")");
        }

        long remainingDays = gregorian.toEpochDay() - anchor.toEpochDay();

        while (rowIndex < CALENDAR_DATA.length) {
            int[] row = CALENDAR_DATA[rowIndex];
            int yearDays = totalDaysInBsYear(row);

            if (remainingDays < yearDays) {
                break;
            }
            remainingDays -= yearDays;
            rowIndex++;
        }

        if (rowIndex >= CALENDAR_DATA.length) {
            throw new CalendarRangeException(
                "Date " + gregorian + " is beyond supported range (BS " + BS_YEAR_MAX + ")");
        }

        int bsYear = CALENDAR_DATA[rowIndex][0];
        int[] monthLengths = monthLengths(CALENDAR_DATA[rowIndex]);

        int bsMonth = 1;
        for (int m = 0; m < 12; m++) {
            if (remainingDays < monthLengths[m]) {
                bsMonth = m + 1;
                break;
            }
            remainingDays -= monthLengths[m];
        }

        int bsDay = (int) remainingDays + 1;
        return new BsDateComponents(bsYear, bsMonth, bsDay);
    }

    /**
     * Converts a BS date to its Gregorian equivalent.
     *
     * @param bsYear  BS year (2070–2100)
     * @param bsMonth BS month (1–12)
     * @param bsDay   BS day (1–32)
     * @return corresponding Gregorian {@link LocalDate}
     * @throws CalendarRangeException   if the year is outside the supported range
     * @throws IllegalArgumentException if month or day is invalid for given year
     */
    public static LocalDate bsToGregorian(int bsYear, int bsMonth, int bsDay) {
        int[] row = findRow(bsYear);
        validateMonthDay(row, bsMonth, bsDay);

        // Start from the anchor (Baishakh 1) of this BS year
        LocalDate anchor = LocalDate.of(row[1], row[2], row[3]);
        int offsetDays = 0;

        // Add full months before the target month
        int[] monthLens = monthLengths(row);
        for (int m = 0; m < bsMonth - 1; m++) {
            offsetDays += monthLens[m];
        }
        // Add days within the target month (1-based → subtract 1)
        offsetDays += bsDay - 1;

        return anchor.plusDays(offsetDays);
    }

    // -------------------------------------------------------------------------
    // K15-002: Month length query
    // -------------------------------------------------------------------------

    /**
     * Returns the number of days in a given BS month.
     *
     * @param bsYear  BS year
     * @param bsMonth BS month (1–12)
     * @return number of days (29–32)
     * @throws CalendarRangeException   if bsYear is outside supported range
     * @throws IllegalArgumentException if bsMonth is not 1–12
     */
    public static int getMonthLength(int bsYear, int bsMonth) {
        if (bsMonth < 1 || bsMonth > 12) {
            throw new IllegalArgumentException("BS month must be 1–12, got: " + bsMonth);
        }
        return monthLengths(findRow(bsYear))[bsMonth - 1];
    }

    /**
     * Returns whether a BS date is valid (year in range, month 1–12, day within month).
     */
    public static boolean isValid(int bsYear, int bsMonth, int bsDay) {
        if (bsYear < BS_YEAR_MIN || bsYear > BS_YEAR_MAX) return false;
        if (bsMonth < 1 || bsMonth > 12) return false;
        int[] row = findRow(bsYear);
        return bsDay >= 1 && bsDay <= monthLengths(row)[bsMonth - 1];
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static int[] findRow(int bsYear) {
        if (bsYear < BS_YEAR_MIN || bsYear > BS_YEAR_MAX) {
            throw new CalendarRangeException(
                "BS year " + bsYear + " outside supported range [" + BS_YEAR_MIN + ", " + BS_YEAR_MAX + "]");
        }
        return CALENDAR_DATA[bsYear - BS_YEAR_MIN];
    }

    private static int[] monthLengths(int[] row) {
        int[] lens = new int[12];
        System.arraycopy(row, 4, lens, 0, 12);
        return lens;
    }

    private static int totalDaysInBsYear(int[] row) {
        int total = 0;
        for (int i = 4; i <= 15; i++) total += row[i];
        return total;
    }

    private static void validateMonthDay(int[] row, int bsMonth, int bsDay) {
        if (bsMonth < 1 || bsMonth > 12) {
            throw new IllegalArgumentException("BS month must be 1–12, got: " + bsMonth);
        }
        int max = monthLengths(row)[bsMonth - 1];
        if (bsDay < 1 || bsDay > max) {
            throw new IllegalArgumentException(
                "BS day " + bsDay + " invalid for month " + bsMonth
                    + " of year " + row[0] + " (max " + max + ")");
        }
    }

    /**
     * Intermediate plain int triple returned by {@link #gregorianToBs}.
     * Callers convert to {@link com.ghatana.appplatform.calendar.domain.BsDate}.
     */
    public record BsDateComponents(int year, int month, int day) {}

    /**
     * Thrown when a conversion is requested for a date outside the lookup table range.
     */
    public static final class CalendarRangeException extends RuntimeException {
        public CalendarRangeException(String message) {
            super(message);
        }
    }
}
