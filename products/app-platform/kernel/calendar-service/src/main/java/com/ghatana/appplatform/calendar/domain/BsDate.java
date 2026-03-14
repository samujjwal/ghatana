package com.ghatana.appplatform.calendar.domain;

import java.util.Objects;

/**
 * Immutable Bikram Sambat date value object.
 *
 * <p>BS calendar used in Nepal for official financial and government purposes.
 * Months are 1-based (1 = Baishakh, 12 = Chaitra). Days range from 29 to 32
 * depending on the month and year.
 *
 * <p>String representation: {@code "YYYY-MM-DD"} with zero-padded month and day.
 *
 * @doc.type class
 * @doc.purpose Immutable Bikram Sambat date with validated fields
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class BsDate {

    private final int year;
    private final int month;
    private final int day;

    private BsDate(int year, int month, int day) {
        if (year < 2000 || year > 2200) {
            throw new IllegalArgumentException("BS year out of plausible range: " + year);
        }
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("BS month must be 1–12, got: " + month);
        }
        if (day < 1 || day > 32) {
            throw new IllegalArgumentException("BS day must be 1–32, got: " + day);
        }
        this.year  = year;
        this.month = month;
        this.day   = day;
    }

    /**
     * Creates a {@link BsDate} with basic range validation.
     *
     * @param year  BS year (e.g. 2082)
     * @param month BS month (1–12)
     * @param day   BS day (1–32)
     */
    public static BsDate of(int year, int month, int day) {
        return new BsDate(year, month, day);
    }

    /**
     * Parses a {@code "YYYY-MM-DD"} string.
     *
     * @throws IllegalArgumentException if the string is malformed
     */
    public static BsDate parse(String value) {
        if (value == null || value.length() != 10 || value.charAt(4) != '-' || value.charAt(7) != '-') {
            throw new IllegalArgumentException("BsDate string must be YYYY-MM-DD, got: " + value);
        }
        try {
            int y = Integer.parseInt(value.substring(0, 4));
            int m = Integer.parseInt(value.substring(5, 7));
            int d = Integer.parseInt(value.substring(8, 10));
            return of(y, m, d);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("BsDate string must be YYYY-MM-DD, got: " + value, e);
        }
    }

    public int year()  { return year; }
    public int month() { return month; }
    public int day()   { return day; }

    /**
     * Returns {@code "YYYY-MM-DD"} zero-padded representation.
     */
    @Override
    public String toString() {
        return String.format("%04d-%02d-%02d", year, month, day);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BsDate other)) return false;
        return year == other.year && month == other.month && day == other.day;
    }

    @Override
    public int hashCode() {
        return Objects.hash(year, month, day);
    }
}
