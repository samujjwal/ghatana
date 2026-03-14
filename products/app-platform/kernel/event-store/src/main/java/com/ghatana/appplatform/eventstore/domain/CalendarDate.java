package com.ghatana.appplatform.eventstore.domain;

import java.util.Objects;

/**
 * Bikram Sambat (BS) calendar date — Nepal's official calendar system.
 *
 * <p>Holds a date as a simple string in {@code YYYY-MM-DD} format. Conversion
 * between BS and Gregorian is the responsibility of the calendar-service kernel.
 * This value object is deliberately thin: it carries the formatted date
 * without embedding conversion logic, keeping the dependency on the calendar
 * service optional and testable in isolation.
 *
 * @doc.type class
 * @doc.purpose Value object representing a Bikram Sambat calendar date (YYYY-MM-DD)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public final class CalendarDate {

    /** Expected BS date format — e.g. "2082-11-13". */
    private static final String FORMAT_PATTERN = "\\d{4}-\\d{2}-\\d{2}";

    private final String value;

    private CalendarDate(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    /**
     * Parse a BS date string.
     *
     * @param bsDate date in {@code YYYY-MM-DD} format
     * @throws IllegalArgumentException when the format is invalid
     */
    public static CalendarDate of(String bsDate) {
        if (bsDate == null || !bsDate.matches(FORMAT_PATTERN)) {
            throw new IllegalArgumentException(
                "Invalid BS date format — expected YYYY-MM-DD, got: " + bsDate);
        }
        return new CalendarDate(bsDate);
    }

    /** @return the BS date string in {@code YYYY-MM-DD} format */
    public String value() { return value; }

    @Override
    public String toString() { return value; }

    @Override
    public boolean equals(Object o) {
        return o instanceof CalendarDate cd && value.equals(cd.value);
    }

    @Override
    public int hashCode() { return value.hashCode(); }
}
