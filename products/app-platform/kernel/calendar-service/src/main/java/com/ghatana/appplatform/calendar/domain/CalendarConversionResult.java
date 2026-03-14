package com.ghatana.appplatform.calendar.domain;

import java.time.LocalDate;

/**
 * Result of a calendar conversion between Gregorian and Bikram Sambat.
 *
 * @param gregorian the Gregorian date
 * @param bs        the equivalent BS date
 *
 * @doc.type record
 * @doc.purpose Pairs a Gregorian date with its BS equivalent
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record CalendarConversionResult(LocalDate gregorian, BsDate bs) {

    public CalendarConversionResult {
        if (gregorian == null) throw new IllegalArgumentException("gregorian must not be null");
        if (bs == null)        throw new IllegalArgumentException("bs must not be null");
    }
}
