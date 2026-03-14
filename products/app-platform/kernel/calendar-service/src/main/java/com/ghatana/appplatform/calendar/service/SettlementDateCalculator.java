package com.ghatana.appplatform.calendar.service;

import com.ghatana.appplatform.calendar.conversion.BsCalendarConversion;
import com.ghatana.appplatform.calendar.domain.CalendarConversionResult;
import io.activej.promise.Promise;

import java.time.LocalDate;
import java.util.Objects;

/**
 * T+n settlement date calculation using business-day arithmetic.
 *
 * <p>Settlement rules (K15-010/K15-011):
 * <ul>
 *   <li>T+0 = trade date itself (if a business day; otherwise the next business day).
 *   <li>T+n adds {@code n} business days to the trade date.
 *   <li>Business days exclude weekends and jurisdiction-specific public holidays.
 *   <li>Negative values for {@code addBusinessDays} move backwards through the calendar.
 * </ul>
 *
 * <p>Returns a {@link CalendarConversionResult} so callers receive both the Gregorian
 * settlement date and its BS equivalent (required for SEBON/NRB regulatory filings).
 *
 * @doc.type class
 * @doc.purpose T+n settlement date calculation with BS/Gregorian dual-calendar result
 * @doc.layer product
 * @doc.pattern Service
 */
public final class SettlementDateCalculator {

    private final BusinessDayCalculator businessDayCalculator;

    /**
     * @param businessDayCalculator the business-day predicates provider (K15-007)
     */
    public SettlementDateCalculator(BusinessDayCalculator businessDayCalculator) {
        this.businessDayCalculator = Objects.requireNonNull(
            businessDayCalculator, "businessDayCalculator");
    }

    // -------------------------------------------------------------------------
    // K15-010 – Settlement date
    // -------------------------------------------------------------------------

    /**
     * Computes the settlement date for a trade by adding {@code tPlusDays} business days
     * to {@code tradeDate}.
     *
     * <p>{@code tPlusDays = 0} returns the next business day on or after {@code tradeDate}.
     * {@code tPlusDays = 2} (T+2) returns the second business day after {@code tradeDate}.
     *
     * @param tradeDate    trade date (Gregorian)
     * @param tPlusDays    number of business days to add (≥ 0)
     * @param jurisdiction ISO jurisdiction code (e.g. {@code "NP"})
     * @return promise resolving to a {@link CalendarConversionResult} with both calendar dates
     */
    public Promise<CalendarConversionResult> settlementDate(
            LocalDate tradeDate,
            int tPlusDays,
            String jurisdiction) {

        Objects.requireNonNull(tradeDate, "tradeDate");
        Objects.requireNonNull(jurisdiction, "jurisdiction");
        if (tPlusDays < 0) {
            throw new IllegalArgumentException("tPlusDays must be >= 0, got: " + tPlusDays);
        }

        // First find the settlement day on-or-after tradeDate that is a business day,
        // then advance tPlusDays additional business days.
        return addBusinessDays(tradeDate, tPlusDays, jurisdiction)
            .map(settlementGreg -> {
                var bsComponents = BsCalendarConversion.gregorianToBs(settlementGreg);
                var bsDate  = com.ghatana.appplatform.calendar.domain.BsDate.of(
                    bsComponents.year(), bsComponents.month(), bsComponents.day());
                return new CalendarConversionResult(settlementGreg, bsDate);
            });
    }

    // -------------------------------------------------------------------------
    // K15-011 – Business day arithmetic
    // -------------------------------------------------------------------------

    /**
     * Adds {@code businessDays} business days to {@code date}.
     *
     * <p>If {@code businessDays == 0} the result is the first business day on or after
     * {@code date}. For positive values, the result is further in the future. Negative
     * values move backwards.
     *
     * @param date         starting Gregorian date (may land on a weekend or holiday)
     * @param businessDays number of business days to advance (may be negative)
     * @param jurisdiction ISO jurisdiction code
     * @return promise resolving to the target Gregorian date
     */
    public Promise<LocalDate> addBusinessDays(
            LocalDate date,
            int businessDays,
            String jurisdiction) {

        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(jurisdiction, "jurisdiction");

        if (businessDays == 0) {
            // Move to the first business day on or after 'date'
            return firstBusinessDayOnOrAfter(date, jurisdiction);
        }

        if (businessDays > 0) {
            return addPositiveBusinessDays(date, businessDays, jurisdiction);
        }

        // businessDays < 0: move backwards
        return addNegativeBusinessDays(date, -businessDays, jurisdiction);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the first business day on or after {@code date}.
     * Uses the same recursive promise-chain pattern as {@link BusinessDayCalculator}.
     */
    private Promise<LocalDate> firstBusinessDayOnOrAfter(LocalDate date, String jurisdiction) {
        return businessDayCalculator.isBusinessDay(date, jurisdiction)
            .then(isBd -> isBd
                ? Promise.of(date)
                : firstBusinessDayOnOrAfter(date.plusDays(1), jurisdiction));
    }

    /**
     * Advances forward by exactly {@code n} business days from {@code date},
     * using the first business day on/after {@code date} as day 0.
     */
    private Promise<LocalDate> addPositiveBusinessDays(
            LocalDate date, int n, String jurisdiction) {

        // First land on a business day, then advance n more
        return firstBusinessDayOnOrAfter(date, jurisdiction)
            .then(start -> advanceNBusinessDays(start, n, jurisdiction));
    }

    /**
     * Starting from a known business day {@code current}, counts {@code remaining}
     * more business days forward.
     */
    private Promise<LocalDate> advanceNBusinessDays(
            LocalDate current, int remaining, String jurisdiction) {

        if (remaining == 0) {
            return Promise.of(current);
        }
        // Move to the next business day and reduce remaining count
        return businessDayCalculator.nextBusinessDay(current, jurisdiction)
            .then(next -> advanceNBusinessDays(next, remaining - 1, jurisdiction));
    }

    /**
     * Moves backwards by {@code n} business days from {@code date}.
     */
    private Promise<LocalDate> addNegativeBusinessDays(
            LocalDate date, int n, String jurisdiction) {

        if (n == 0) {
            return firstBusinessDayOnOrBefore(date, jurisdiction);
        }
        return firstBusinessDayOnOrBefore(date, jurisdiction)
            .then(bd -> addNegativeBusinessDays(bd.minusDays(1), n - 1, jurisdiction));
    }

    /** Returns the first business day on or before {@code date}. */
    private Promise<LocalDate> firstBusinessDayOnOrBefore(LocalDate date, String jurisdiction) {
        return businessDayCalculator.isBusinessDay(date, jurisdiction)
            .then(isBd -> isBd
                ? Promise.of(date)
                : firstBusinessDayOnOrBefore(date.minusDays(1), jurisdiction));
    }
}
