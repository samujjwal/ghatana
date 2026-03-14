package com.ghatana.appplatform.calendar.service;

import com.ghatana.appplatform.calendar.conversion.BsCalendarConversion;
import com.ghatana.appplatform.calendar.domain.BsDate;
import com.ghatana.appplatform.calendar.domain.BsHoliday;
import com.ghatana.appplatform.calendar.port.HolidayCalendar;
import io.activej.promise.Promise;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Business day calculation for Nepal's financial calendar.
 *
 * <p>A day is a business day when ALL of the following hold:
 * <ol>
 *   <li>The Gregorian day-of-week is not Saturday or Sunday.
 *   <li>The date is not in the holiday list for the given jurisdiction.
 * </ol>
 *
 * <p>Weekend definition: Saturday + Sunday (Nepal's official weekend since 2073 BS).
 * Jurisdiction examples: "NP" (federal Nepal), "NP-BAG" (Bagmati province).
 *
 * @doc.type class
 * @doc.purpose Business day predicates and counts for jurisdiction-scoped calendars
 * @doc.layer product
 * @doc.pattern Service
 */
public final class BusinessDayCalculator {

    private static final Set<DayOfWeek> WEEKEND = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private final HolidayCalendar holidayCalendar;

    public BusinessDayCalculator(HolidayCalendar holidayCalendar) {
        this.holidayCalendar = Objects.requireNonNull(holidayCalendar, "holidayCalendar");
    }

    // -------------------------------------------------------------------------
    // K15-007: Core business-day operations
    // -------------------------------------------------------------------------

    /**
     * Returns whether the given Gregorian date is a business day for the jurisdiction.
     *
     * @param date         Gregorian date to check
     * @param jurisdiction ISO jurisdiction code
     * @return a promise resolving to {@code true} if the date is a business day
     */
    public Promise<Boolean> isBusinessDay(LocalDate date, String jurisdiction) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(jurisdiction, "jurisdiction");

        if (isWeekend(date)) {
            return Promise.of(false);
        }

        return holidaysForDate(date, jurisdiction)
            .map(holidays -> !containsDate(holidays, date));
    }

    /**
     * Returns the next business day strictly after {@code date}.
     *
     * @param date         starting Gregorian date (exclusive)
     * @param jurisdiction ISO jurisdiction code
     * @return a promise resolving to the next business day
     */
    public Promise<LocalDate> nextBusinessDay(LocalDate date, String jurisdiction) {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(jurisdiction, "jurisdiction");

        return nextBusinessDayFrom(date.plusDays(1), jurisdiction);
    }

    /**
     * Returns the number of business days between {@code start} (inclusive) and
     * {@code end} (exclusive).
     *
     * @param start        start date (inclusive)
     * @param end          end date (exclusive)
     * @param jurisdiction ISO jurisdiction code
     * @return a promise resolving to the count of business days
     */
    public Promise<Integer> businessDaysBetween(LocalDate start, LocalDate end, String jurisdiction) {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        Objects.requireNonNull(jurisdiction, "jurisdiction");

        if (!start.isBefore(end)) {
            return Promise.of(0);
        }

        // Collect all unique BS years touched by [start, end) to batch holiday queries
        int startBsYear = toBS(start).year();
        int endBsYear   = toBS(end.minusDays(1)).year();

        return loadHolidaysForYears(jurisdiction, startBsYear, endBsYear)
            .map(allHolidays -> countBusinessDays(start, end, allHolidays));
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Promise<LocalDate> nextBusinessDayFrom(LocalDate candidate, String jurisdiction) {
        if (isWeekend(candidate)) {
            return nextBusinessDayFrom(candidate.plusDays(1), jurisdiction);
        }
        return holidaysForDate(candidate, jurisdiction)
            .then(holidays -> {
                if (containsDate(holidays, candidate)) {
                    return nextBusinessDayFrom(candidate.plusDays(1), jurisdiction);
                }
                return Promise.of(candidate);
            });
    }

    private Promise<List<BsHoliday>> holidaysForDate(LocalDate date, String jurisdiction) {
        BsDate bs = toBS(date);
        return holidayCalendar.getHolidays(jurisdiction, bs.year());
    }

    private Promise<List<BsHoliday>> loadHolidaysForYears(String jurisdiction, int fromYear, int toYear) {
        // Sequential accumulation — year range is almost always 1-2 years
        return collectHolidays(jurisdiction, fromYear, toYear, fromYear, List.of());
    }

    private Promise<List<BsHoliday>> collectHolidays(
            String jurisdiction, int fromYear, int toYear, int current, List<BsHoliday> acc) {
        if (current > toYear) return Promise.of(acc);
        return holidayCalendar.getHolidays(jurisdiction, current)
            .then(batch -> {
                List<BsHoliday> combined = new java.util.ArrayList<>(acc);
                combined.addAll(batch);
                return collectHolidays(jurisdiction, fromYear, toYear, current + 1, combined);
            });
    }

    private static int countBusinessDays(LocalDate start, LocalDate end, List<BsHoliday> holidays) {
        int count = 0;
        LocalDate current = start;
        while (current.isBefore(end)) {
            if (!isWeekend(current) && !containsDate(holidays, current)) {
                count++;
            }
            current = current.plusDays(1);
        }
        return count;
    }

    private static boolean isWeekend(LocalDate date) {
        return WEEKEND.contains(date.getDayOfWeek());
    }

    private static boolean containsDate(List<BsHoliday> holidays, LocalDate date) {
        return holidays.stream().anyMatch(h -> h.gregorianDate().equals(date));
    }

    private static BsDate toBS(LocalDate date) {
        BsCalendarConversion.BsDateComponents c = BsCalendarConversion.gregorianToBs(date);
        return BsDate.of(c.year(), c.month(), c.day());
    }
}
