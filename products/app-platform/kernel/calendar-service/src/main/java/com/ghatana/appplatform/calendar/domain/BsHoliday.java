package com.ghatana.appplatform.calendar.domain;

import java.time.LocalDate;

/**
 * A single holiday entry in a jurisdiction's holiday calendar.
 *
 * <p>Holidays can be jurisdiction-scoped (e.g., "NP" for Nepal federal,
 * "NP-BAG" for Bagmati province). A holiday with {@code recurringBsDate = true}
 * recurs on the same BS month/day every year.
 *
 * @param id              unique identifier (opaque string / UUID)
 * @param date            BS date of the holiday
 * @param gregorianDate   Gregorian equivalent of {@code date}
 * @param name            human-readable holiday name
 * @param type            category of the holiday (PUBLIC, TRADING, SETTLEMENT)
 * @param jurisdiction    ISO 3166-1/3166-2 code (e.g. "NP", "NP-BAG")
 * @param recurringBsDate if {@code true}, applies on the same BS month/day each year
 *
 * @doc.type record
 * @doc.purpose Jurisdiction-scoped BS holiday with Gregorian equivalent
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record BsHoliday(
    String id,
    BsDate date,
    LocalDate gregorianDate,
    String name,
    HolidayType type,
    String jurisdiction,
    boolean recurringBsDate
) {
    public BsHoliday {
        if (id == null || id.isBlank())            throw new IllegalArgumentException("id must not be blank");
        if (date == null)                           throw new IllegalArgumentException("date must not be null");
        if (gregorianDate == null)                  throw new IllegalArgumentException("gregorianDate must not be null");
        if (name == null || name.isBlank())         throw new IllegalArgumentException("name must not be blank");
        if (type == null)                           throw new IllegalArgumentException("type must not be null");
        if (jurisdiction == null || jurisdiction.isBlank()) throw new IllegalArgumentException("jurisdiction must not be blank");
    }
}
