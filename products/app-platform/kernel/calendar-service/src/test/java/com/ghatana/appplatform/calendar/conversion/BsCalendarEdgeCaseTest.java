package com.ghatana.appplatform.calendar.conversion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Edge-case tests for {@link BsCalendarConversion} covering 32-day months and year boundaries.
 *
 * <p>These tests complement {@link BsCalendarConversionTest} by targeting
 * boundary conditions that are unique to the Bikram Sambat calendar:
 * <ul>
 *   <li>Months with 32 days (BS months 2, 3 in some years)</li>
 *   <li>The last day of Chaitra (month 12) transitioning to Baisakh (month 1) of the next year</li>
 *   <li>Dates at the boundary of the supported range (BS 2070–2100)</li>
 *   <li>Invalid month/day combinations</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Edge-case tests for 32-day months and year boundaries in BS calendar
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BsCalendarConversion — Edge Cases")
class BsCalendarEdgeCaseTest {

    // -------------------------------------------------------------------------
    // 32-day months
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("bsToGregorian_32DayMonth_lastDayValid — BS 2072/02/32 exists (Jestha has 32 days in 2072)")
    void bsToGregorian32DayMonthLastDayValid() {
        // From CALENDAR_DATA: BS 2072 months = {31,32,...}  so month 2 has 32 days
        // BS 2072/02/01 = bsToGregorian(2072,1,31) + 1 day = May 14, 2015 + 31 = June 13, 2015
        // BS 2072/02/32 is the last day of Jestha 2072
        LocalDate result = BsCalendarConversion.bsToGregorian(2072, 2, 32);
        assertThat(result).isNotNull();

        // Cross-check: convert back — should round-trip to exactly BS 2072/02/32
        BsDateComponents roundTrip = BsCalendarConversion.gregorianToBs(result);
        assertThat(roundTrip.year()).isEqualTo(2072);
        assertThat(roundTrip.month()).isEqualTo(2);
        assertThat(roundTrip.day()).isEqualTo(32);
    }

    @Test
    @DisplayName("bsToGregorian_32DayMonthPlusOne_isNextMonth — day after BS 2072/02/32 is BS 2072/03/01")
    void bsToGregorian32DayMonthPlusOneIsNextMonth() {
        LocalDate lastOfMonth2 = BsCalendarConversion.bsToGregorian(2072, 2, 32);
        LocalDate firstOfMonth3 = lastOfMonth2.plusDays(1);

        BsDateComponents nextDay = BsCalendarConversion.gregorianToBs(firstOfMonth3);
        assertThat(nextDay.year()).isEqualTo(2072);
        assertThat(nextDay.month()).isEqualTo(3);
        assertThat(nextDay.day()).isEqualTo(1);
    }

    // -------------------------------------------------------------------------
    // Year boundary: Chaitra (month 12) → Baisakh (month 1) of the next BS year
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("yearBoundary_lastDayChaitraToFirstDayBaisakh — year rolls over correctly")
    void yearBoundaryLastDayChaitraToFirstDayBaisakh() {
        // Last day of BS 2081 Chaitra: CALENDAR_DATA row for 2081 → month 12 has 30 days
        LocalDate lastDayChaitraGreg = BsCalendarConversion.bsToGregorian(2081, 12, 30);
        LocalDate newYearGreg        = lastDayChaitraGreg.plusDays(1);

        BsDateComponents newYear = BsCalendarConversion.gregorianToBs(newYearGreg);
        assertThat(newYear.year()).isEqualTo(2082);
        assertThat(newYear.month()).isEqualTo(1);
        assertThat(newYear.day()).isEqualTo(1);
    }

    @Test
    @DisplayName("yearBoundary_firstDayBaisakhToLastDayChaitra — reverse boundary check")
    void yearBoundaryFirstDayBaisakhToLastDayChaitra() {
        LocalDate baisakh1 = BsCalendarConversion.bsToGregorian(2082, 1, 1);
        LocalDate chaitraLast = baisakh1.minusDays(1);

        BsDateComponents prev = BsCalendarConversion.gregorianToBs(chaitraLast);
        assertThat(prev.year()).isEqualTo(2081);
        assertThat(prev.month()).isEqualTo(12);
    }

    // -------------------------------------------------------------------------
    // Range boundaries
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("supportedRangeStart_BS2070Baishakh01_convertsOk — first day of supported range")
    void supportedRangeStartBs2070Baishakh01ConvertsOk() {
        LocalDate greg = BsCalendarConversion.bsToGregorian(2070, 1, 1);
        assertThat(greg).isNotNull();

        BsDateComponents back = BsCalendarConversion.gregorianToBs(greg);
        assertThat(back.year()).isEqualTo(2070);
        assertThat(back.month()).isEqualTo(1);
        assertThat(back.day()).isEqualTo(1);
    }

    @Test
    @DisplayName("beforeSupportedRange_throws — dates before BS 2070 throw CalendarRangeException")
    void beforeSupportedRangeThrows() {
        // BS 2070 starts April 14, 2013 → April 13, 2013 is before range
        assertThatThrownBy(() ->
            BsCalendarConversion.gregorianToBs(LocalDate.of(2013, 4, 13)))
            .isInstanceOf(BsCalendarConversion.CalendarRangeException.class);
    }

    @Test
    @DisplayName("afterSupportedRange_throws — dates after BS 2100 throw CalendarRangeException")
    void afterSupportedRangeThrows() {
        // BS 2100 ends around April 2044. Use a clearly out-of-range date.
        assertThatThrownBy(() ->
            BsCalendarConversion.gregorianToBs(LocalDate.of(2050, 1, 1)))
            .isInstanceOf(BsCalendarConversion.CalendarRangeException.class);
    }

    // -------------------------------------------------------------------------
    // Invalid day/month arguments (bsToGregorian)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("invalidDay_exceedsMonthLength_throws — day 31 in a 30-day month throws")
    void invalidDayExceedsMonthLengthThrows() {
        // BS 2081, month 8 (Mangsir) has 29 days per CALENDAR_DATA
        assertThatThrownBy(() ->
            BsCalendarConversion.bsToGregorian(2081, 8, 30))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("invalidMonth_outOfRange_throws — month 0 and 13 throw")
    void invalidMonthOutOfRangeThrows() {
        assertThatThrownBy(() -> BsCalendarConversion.bsToGregorian(2081, 0, 1))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BsCalendarConversion.bsToGregorian(2081, 13, 1))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
