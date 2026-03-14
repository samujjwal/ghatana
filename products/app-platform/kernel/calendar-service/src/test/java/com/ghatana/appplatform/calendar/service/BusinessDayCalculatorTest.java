package com.ghatana.appplatform.calendar.service;

import com.ghatana.appplatform.calendar.domain.BsDate;
import com.ghatana.appplatform.calendar.domain.BsHoliday;
import com.ghatana.appplatform.calendar.domain.HolidayType;
import com.ghatana.appplatform.calendar.port.HolidayCalendar;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BusinessDayCalculator}.
 *
 * <p>Uses a stub {@link HolidayCalendar} to avoid database dependencies.
 *
 * @doc.type class
 * @doc.purpose Unit tests for business-day calculation logic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("BusinessDayCalculator Tests")
class BusinessDayCalculatorTest extends EventloopTestBase {

    private static final String NP = "NP";

    // Stub holiday: Maha Shivaratri on March 8 2026 (BS 2082/11/24)
    private static final LocalDate HOLIDAY_DATE = LocalDate.of(2026, 3, 8);

    private final HolidayCalendar calendar = new StubHolidayCalendar(List.of(
        new BsHoliday("h-1", BsDate.of(2082, 11, 24), HOLIDAY_DATE,
            "Maha Shivaratri", HolidayType.PUBLIC, NP, false)
    ));

    private final BusinessDayCalculator calculator = new BusinessDayCalculator(calendar);

    @Test
    @DisplayName("Wednesday March 11 2026 is a business day (no holiday)")
    void wednesdayIsBusinessDay() {
        boolean result = runPromise(() -> calculator.isBusinessDay(LocalDate.of(2026, 3, 11), NP));
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Saturday March 7 2026 is NOT a business day (weekend)")
    void saturdayIsNotBusinessDay() {
        boolean result = runPromise(() -> calculator.isBusinessDay(LocalDate.of(2026, 3, 7), NP));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Sunday March 8 2026 is NOT a business day (weekend takes precedence over holiday)")
    void sundayIsNotBusinessDay() {
        boolean result = runPromise(() -> calculator.isBusinessDay(HOLIDAY_DATE, NP));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Holiday on a weekday (if it fell on weekday) is not a business day")
    void holidayOnWeekdayIsNotBusinessDay() {
        // Use March 9 2026 (Monday) as a synthetic holiday date
        LocalDate mondayHoliday = LocalDate.of(2026, 3, 9);
        HolidayCalendar withMonday = new StubHolidayCalendar(List.of(
            new BsHoliday("h-2", BsDate.of(2082, 11, 25), mondayHoliday,
                "Test Holiday", HolidayType.PUBLIC, NP, false)
        ));
        BusinessDayCalculator calc = new BusinessDayCalculator(withMonday);

        boolean result = runPromise(() -> calc.isBusinessDay(mondayHoliday, NP));
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("nextBusinessDay skips weekend: Friday Mar 13 → Monday Mar 16 2026")
    void nextBusinessDaySkipsWeekend() {
        // March 13 2026 = Friday
        LocalDate friday = LocalDate.of(2026, 3, 13);
        LocalDate next = runPromise(() -> calculator.nextBusinessDay(friday, NP));
        // Saturday 14 and Sunday 15 are skipped
        assertThat(next).isEqualTo(LocalDate.of(2026, 3, 16));
    }

    @Test
    @DisplayName("businessDaysBetween counts only weekdays without holidays")
    void businessDaysBetween_oneWeek() {
        // March 9 (Mon) to March 14 (Sat) = 5 days: Mon, Tue, Wed, Thu, Fri
        LocalDate start = LocalDate.of(2026, 3, 9);
        LocalDate end   = LocalDate.of(2026, 3, 14);
        int count = runPromise(() -> calculator.businessDaysBetween(start, end, NP));
        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("businessDaysBetween returns 0 when start == end")
    void businessDaysBetween_zeroWhenEqual() {
        LocalDate d = LocalDate.of(2026, 3, 11);
        int count = runPromise(() -> calculator.businessDaysBetween(d, d, NP));
        assertThat(count).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Stub
    // -------------------------------------------------------------------------

    private static final class StubHolidayCalendar implements HolidayCalendar {
        private final List<BsHoliday> holidays;

        StubHolidayCalendar(List<BsHoliday> holidays) {
            this.holidays = holidays;
        }

        @Override
        public Promise<Void> addHoliday(BsHoliday holiday) {
            return Promise.complete();
        }

        @Override
        public Promise<List<BsHoliday>> getHolidays(String jurisdiction, int bsYear) {
            return Promise.of(holidays.stream()
                .filter(h -> h.jurisdiction().equals(jurisdiction) && h.date().year() == bsYear)
                .toList());
        }

        @Override
        public Promise<Void> deleteHoliday(String id) {
            return Promise.complete();
        }
    }
}
