package com.ghatana.appplatform.calendar.domain;

import java.time.LocalDate;

/**
 * Nepal fiscal year descriptor spanning from Shrawan 1 (BS month 4) to Ashadh
 * end (BS month 3) of the following BS year.
 *
 * <p>Example: fiscal year 2081/82 starts on BS 2081-04-01 (Shrawan 1, ~17 Jul 2024)
 * and ends on the last day of BS 2082-03 (Ashadh, ~16 Jul 2025).
 *
 * <p>Both Gregorian and BS boundary dates are provided for cross-calendar display
 * and range queries.
 *
 * @param bsStartYear   BS year in which Shrawan falls (e.g. 2081)
 * @param startBs       First day of the fiscal year in BS (Shrawan 1)
 * @param endBs         Last day of the fiscal year in BS (last day of Ashadh next year)
 * @param startGreg     Gregorian equivalent of startBs
 * @param endGreg       Gregorian equivalent of endBs
 * @param label         Human-readable label, e.g. {@code "FY 2081/82"}
 *
 * @doc.type record
 * @doc.purpose Immutable descriptor for a Nepal fiscal year
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FiscalYear(
    int       bsStartYear,
    BsDate    startBs,
    BsDate    endBs,
    LocalDate startGreg,
    LocalDate endGreg,
    String    label
) {
    /**
     * Returns {@code true} if the given Gregorian date falls within this fiscal year.
     *
     * @param date Gregorian date to test
     * @return {@code true} if {@code date >= startGreg && date <= endGreg}
     */
    public boolean contains(LocalDate date) {
        return !date.isBefore(startGreg) && !date.isAfter(endGreg);
    }
}
