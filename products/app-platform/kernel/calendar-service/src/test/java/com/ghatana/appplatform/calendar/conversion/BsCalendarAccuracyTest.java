package com.ghatana.appplatform.calendar.conversion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Exhaustive round-trip accuracy tests for {@link BsCalendarConversion}.
 *
 * <p>Verifies that for every valid BS date in the supported range (BS 2070–2100),
 * the conversion pipeline {@code bsToGregorian → gregorianToBs} produces the exact
 * original BS date. This catches off-by-one errors in month-length arithmetic,
 * year-boundary transitions, and 32-day month handling.
 *
 * <p>The test is deliberately exhaustive rather than sampling because off-by-one
 * errors in calendar arithmetic are notoriously hard to spot with spot checks.
 *
 * @doc.type class
 * @doc.purpose Exhaustive round-trip accuracy test for BS ↔ Gregorian calendar conversion
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BsCalendarConversion — Exhaustive Accuracy Tests")
class BsCalendarAccuracyTest {

    /**
     * For every valid (year, month, day) in BS 2070–2100:
     * <ol>
     *   <li>Convert BS → Gregorian.</li>
     *   <li>Convert the result back Gregorian → BS.</li>
     *   <li>Assert the round-trip produces exactly the original BS date.</li>
     * </ol>
     *
     * <p>Failures are collected and reported together rather than stopping on the first failure.
     */
    @Test
    @DisplayName("roundTrip_allDatesBS2070to2100_noLoss — exhaustive BS→Greg→BS round-trip")
    void roundTripAllDatesBS2070to2100NoLoss() {
        List<String> failures = new ArrayList<>();

        for (int bsYear = 2070; bsYear <= 2100; bsYear++) {
            for (int bsMonth = 1; bsMonth <= 12; bsMonth++) {
                int maxDay = BsCalendarConversion.getMonthLength(bsYear, bsMonth);

                for (int bsDay = 1; bsDay <= maxDay; bsDay++) {
                    try {
                        // BS → Gregorian
                        LocalDate gregorian = BsCalendarConversion.bsToGregorian(bsYear, bsMonth, bsDay);

                        // Gregorian → BS
                        BsDateComponents back = BsCalendarConversion.gregorianToBs(gregorian);

                        // Round-trip check
                        if (back.year() != bsYear || back.month() != bsMonth || back.day() != bsDay) {
                            failures.add(String.format(
                                "BS %d/%02d/%02d → %s → BS %d/%02d/%02d (MISMATCH)",
                                bsYear, bsMonth, bsDay, gregorian,
                                back.year(), back.month(), back.day()));
                        }
                    } catch (Exception e) {
                        failures.add(String.format(
                            "BS %d/%02d/%02d → EXCEPTION: %s",
                            bsYear, bsMonth, bsDay, e.getMessage()));
                    }
                }
            }
        }

        if (!failures.isEmpty()) {
            String report = String.join("\n", failures.subList(0, Math.min(failures.size(), 20)));
            fail("Found " + failures.size() + " round-trip failures (first 20 shown):\n" + report);
        }
    }

    /**
     * Verifies monotonic Gregorian day progression: each successive BS date maps to a
     * Gregorian date that is exactly 1 day later than its predecessor.
     */
    @Test
    @DisplayName("gregorianProgression_allDatesBS2070to2100_monotonic — each BS day = +1 Gregorian day")
    void gregorianProgressionAllDatesBS2070to2100Monotonic() {
        List<String> failures = new ArrayList<>();

        LocalDate prevGregorian = null;
        String prevBs = null;

        for (int bsYear = 2070; bsYear <= 2100; bsYear++) {
            for (int bsMonth = 1; bsMonth <= 12; bsMonth++) {
                int maxDay = BsCalendarConversion.getMonthLength(bsYear, bsMonth);

                for (int bsDay = 1; bsDay <= maxDay; bsDay++) {
                    String currentBs = String.format("BS %d/%02d/%02d", bsYear, bsMonth, bsDay);
                    try {
                        LocalDate greg = BsCalendarConversion.bsToGregorian(bsYear, bsMonth, bsDay);

                        if (prevGregorian != null) {
                            long diff = greg.toEpochDay() - prevGregorian.toEpochDay();
                            if (diff != 1L) {
                                failures.add(String.format(
                                    "%s → %s — gap after %s → %s is %d days (expected 1)",
                                    prevBs, currentBs, prevGregorian, greg, diff));
                            }
                        }
                        prevGregorian = greg;
                        prevBs = currentBs;
                    } catch (Exception e) {
                        failures.add(currentBs + " → EXCEPTION: " + e.getMessage());
                    }
                }
            }
        }

        if (!failures.isEmpty()) {
            String report = String.join("\n", failures.subList(0, Math.min(failures.size(), 20)));
            fail("Found " + failures.size() + " monotonicity failures (first 20):\n" + report);
        }
    }
}
