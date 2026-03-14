package com.ghatana.appplatform.calendar.conversion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BsCalendarConversion}.
 *
 * <p>Anchor: BS 2081/01/01 = April 13, 2024 (verified against Nepal Rastra Bank).
 *
 * @doc.type class
 * @doc.purpose Unit tests for BS ↔ Gregorian calendar conversion
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BsCalendarConversion Tests")
class BsCalendarConversionTest {

    // -------------------------------------------------------------------------
    // K15-001: Gregorian → BS
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("gregorianToBs")
    class GregorianToBs {

        @Test
        @DisplayName("Anchor: April 13 2024 → BS 2081/01/01")
        void anchor2081() {
            var result = BsCalendarConversion.gregorianToBs(LocalDate.of(2024, 4, 13));
            assertThat(result.year()).isEqualTo(2081);
            assertThat(result.month()).isEqualTo(1);
            assertThat(result.day()).isEqualTo(1);
        }

        @Test
        @DisplayName("April 14 2024 → BS 2081/01/02")
        void secondDayOf2081() {
            var result = BsCalendarConversion.gregorianToBs(LocalDate.of(2024, 4, 14));
            assertThat(result.year()).isEqualTo(2081);
            assertThat(result.month()).isEqualTo(1);
            assertThat(result.day()).isEqualTo(2);
        }

        @Test
        @DisplayName("April 12 2025 → BS 2081/12/30 (last day of BS 2081)")
        void lastDayOf2081() {
            // BS 2081 ends: 31+31+32+31+31+31+30+29+30+29+30+30 = 365 days
            // April 13, 2024 + 364 days = April 12, 2025
            var result = BsCalendarConversion.gregorianToBs(LocalDate.of(2025, 4, 12));
            assertThat(result.year()).isEqualTo(2081);
            assertThat(result.month()).isEqualTo(12);
            assertThat(result.day()).isEqualTo(30);
        }

        @Test
        @DisplayName("April 13 2025 → BS 2082/01/01 (start of next year)")
        void startOf2082() {
            var result = BsCalendarConversion.gregorianToBs(LocalDate.of(2025, 4, 13));
            assertThat(result.year()).isEqualTo(2082);
            assertThat(result.month()).isEqualTo(1);
            assertThat(result.day()).isEqualTo(1);
        }

        @Test
        @DisplayName("March 13 2026 → BS 2082/11/29 (today in BS)")
        void todayMarch2026() {
            // BS 2082 starts April 13, 2025
            // March 13, 2026 = 334 days into BS 2082
            // months: 31+32+31+32+31+30+30+29+30+29 = 305, so month 11 day 29
            var result = BsCalendarConversion.gregorianToBs(LocalDate.of(2026, 3, 13));
            assertThat(result.year()).isEqualTo(2082);
            assertThat(result.month()).isEqualTo(11);
            assertThat(result.day()).isEqualTo(29);
        }

        @Test
        @DisplayName("throws CalendarRangeException for date before lookup range")
        void beforeRange() {
            assertThatThrownBy(() -> BsCalendarConversion.gregorianToBs(LocalDate.of(2000, 1, 1)))
                .isInstanceOf(BsCalendarConversion.CalendarRangeException.class);
        }
    }

    // -------------------------------------------------------------------------
    // K15-002: BS → Gregorian
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("bsToGregorian")
    class BsToGregorian {

        @Test
        @DisplayName("BS 2081/01/01 → April 13 2024")
        void anchor2081() {
            LocalDate result = BsCalendarConversion.bsToGregorian(2081, 1, 1);
            assertThat(result).isEqualTo(LocalDate.of(2024, 4, 13));
        }

        @Test
        @DisplayName("BS 2081/01/02 → April 14 2024")
        void secondDay() {
            LocalDate result = BsCalendarConversion.bsToGregorian(2081, 1, 2);
            assertThat(result).isEqualTo(LocalDate.of(2024, 4, 14));
        }

        @Test
        @DisplayName("BS 2082/01/01 → April 13 2025")
        void startOf2082() {
            LocalDate result = BsCalendarConversion.bsToGregorian(2082, 1, 1);
            assertThat(result).isEqualTo(LocalDate.of(2025, 4, 13));
        }

        @Test
        @DisplayName("Round-trip: today March 13 2026 → BS → back to March 13 2026")
        void roundTrip() {
            LocalDate original = LocalDate.of(2026, 3, 13);
            var bs = BsCalendarConversion.gregorianToBs(original);
            LocalDate back = BsCalendarConversion.bsToGregorian(bs.year(), bs.month(), bs.day());
            assertThat(back).isEqualTo(original);
        }

        @Test
        @DisplayName("throws CalendarRangeException for year outside range")
        void yearOutOfRange() {
            assertThatThrownBy(() -> BsCalendarConversion.bsToGregorian(2101, 1, 1))
                .isInstanceOf(BsCalendarConversion.CalendarRangeException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for invalid month")
        void invalidMonth() {
            assertThatThrownBy(() -> BsCalendarConversion.bsToGregorian(2081, 0, 1))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for invalid day")
        void invalidDay() {
            assertThatThrownBy(() -> BsCalendarConversion.bsToGregorian(2081, 1, 33))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -------------------------------------------------------------------------
    // K15-002: Month-length query
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getMonthLength")
    class GetMonthLength {

        @Test
        @DisplayName("BS 2081 month 1 (Baishakh) has 31 days")
        void month1of2081() {
            assertThat(BsCalendarConversion.getMonthLength(2081, 1)).isEqualTo(31);
        }

        @Test
        @DisplayName("BS 2082 month 2 (Jestha) has 32 days")
        void month2of2082() {
            assertThat(BsCalendarConversion.getMonthLength(2082, 2)).isEqualTo(32);
        }
    }

    @Nested
    @DisplayName("isValid")
    class IsValid {

        @Test
        @DisplayName("valid date returns true")
        void validDate() {
            assertThat(BsCalendarConversion.isValid(2081, 1, 31)).isTrue();
        }

        @Test
        @DisplayName("day exceeding month length returns false")
        void dayExceedsMonth() {
            // BS 2081 month 8 = 29 days
            assertThat(BsCalendarConversion.isValid(2081, 8, 30)).isFalse();
        }

        @Test
        @DisplayName("year out of range returns false")
        void yearOutOfRange() {
            assertThat(BsCalendarConversion.isValid(2101, 1, 1)).isFalse();
        }
    }
}
