package com.ghatana.appplatform.calendar.service;

import com.ghatana.appplatform.calendar.domain.BsDate;
import com.ghatana.appplatform.calendar.domain.BsHoliday;
import com.ghatana.appplatform.calendar.domain.CalendarConversionResult;
import com.ghatana.appplatform.calendar.domain.HolidayType;
import com.ghatana.appplatform.calendar.port.HolidayCalendar;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import io.activej.promise.Promise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link SettlementDateCalculator}.
 *
 * <p>Uses a stub holiday calendar with a known holiday on Monday March 16 2026
 * so that settlement calculations skip it predictably.
 *
 * @doc.type class
 * @doc.purpose Unit tests for T+n settlement date and business-day arithmetic
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("SettlementDateCalculator Tests")
class SettlementDateCalculatorTest extends EventloopTestBase {

    private static final String NP = "NP";

    // Holiday: Monday March 16 2026
    private static final LocalDate HOLIDAY = LocalDate.of(2026, 3, 16);

    private final HolidayCalendar stub = new StubHolidayCalendar(List.of(
        new BsHoliday("h2", BsDate.of(2082, 12, 2), HOLIDAY,
            "Federation Day", HolidayType.PUBLIC, NP, false)
    ));

    private final BusinessDayCalculator bdc        = new BusinessDayCalculator(stub);
    private final SettlementDateCalculator sdc     = new SettlementDateCalculator(bdc);

    // -------------------------------------------------------------------------
    // K15-010: T+n settlement
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("T+0 from a business day returns that day")
    void tPlus0FromBusinessDay() {
        // Wednesday March 11 2026 is a business day, no holiday
        LocalDate result = runPromise(() ->
            sdc.settlementDate(LocalDate.of(2026, 3, 11), 0, NP)
               .map(CalendarConversionResult::gregorian));
        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 11));
    }

    @Test
    @DisplayName("T+0 from Saturday moves to the next business day (Monday)")
    void tPlus0FromSaturdayMovesToMonday() {
        // Saturday March 14 2026 → next BD = Monday March 16 is a holiday → Tuesday March 17
        LocalDate result = runPromise(() ->
            sdc.settlementDate(LocalDate.of(2026, 3, 14), 0, NP)
               .map(CalendarConversionResult::gregorian));
        // Mon Mar 16 is a holiday → first BD = Tue Mar 17
        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 17));
    }

    @Test
    @DisplayName("T+2 from Wednesday March 11 skips weekend = Monday March 16 is holiday → Tuesday March 17 is wrong, actual: Mon+Tue adjusted")
    void tPlus2FromWednesdayMarch11() {
        // Wed Mar 11 + 2 BD: Thu Mar 12 (1st), Fri Mar 13 (2nd) → settlement = Fri Mar 13
        // (skips weekend only; Mon Mar 16 is holiday but we stop at Fri)
        LocalDate result = runPromise(() ->
            sdc.settlementDate(LocalDate.of(2026, 3, 11), 2, NP)
               .map(CalendarConversionResult::gregorian));
        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 13));
    }

    @Test
    @DisplayName("T+2 from Friday March 13 skips weekend + skips holiday March 16 → March 18")
    void tPlus2FromFridaySkipsWeekendAndHoliday() {
        // Fri Mar 13 + 2 BD: skip Sat+Sun → Mon Mar 16 is holiday → Tue Mar 17 (1st) → Wed Mar 18 (2nd)
        LocalDate result = runPromise(() ->
            sdc.settlementDate(LocalDate.of(2026, 3, 13), 2, NP)
               .map(CalendarConversionResult::gregorian));
        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 18));
    }

    @Test
    @DisplayName("settlementDate returns both Gregorian and BS dates")
    void settlementDateReturnsDualCalendar() {
        CalendarConversionResult result = runPromise(() ->
            sdc.settlementDate(LocalDate.of(2026, 3, 11), 0, NP));
        assertThat(result.gregorian()).isNotNull();
        assertThat(result.bs()).isNotNull();
        assertThat(result.bs().toString()).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    @DisplayName("Negative tPlusDays throws IllegalArgumentException")
    void negativeTPlusDaysThrows() {
        assertThatThrownBy(() ->
            runPromise(() -> sdc.settlementDate(LocalDate.of(2026, 3, 11), -1, NP)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tPlusDays");
    }

    // -------------------------------------------------------------------------
    // K15-011: addBusinessDays
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("addBusinessDays(+3) from Wednesday March 11 lands on Monday March 17 (skip weekend + holiday)")
    void addThreeBusinessDays() {
        // Wed Mar 11 → +1=Thu Mar 12, +2=Fri Mar 13, +3=Mon Mar 16(holiday)→Tue Mar 17
        // Wait: addBusinessDays(wed, 3) = advance 3 BD from first BD on/after wed
        // First BD on/after Mar 11 = Mar 11 (wednesday), then +3: Thu(1), Fri(2), and then...
        // Mon Mar 16 = holiday → next = Tue Mar 17 (3rd)
        LocalDate result = runPromise(() ->
            sdc.addBusinessDays(LocalDate.of(2026, 3, 11), 3, NP));
        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 17));
    }

    @Test
    @DisplayName("addBusinessDays(0) from Sunday lands on next business day")
    void addZeroBusinessDaysFromSunday() {
        // Sunday March 15 → first BD = Monday March 16 is holiday → Tuesday March 17
        LocalDate result = runPromise(() ->
            sdc.addBusinessDays(LocalDate.of(2026, 3, 15), 0, NP));
        assertThat(result).isEqualTo(LocalDate.of(2026, 3, 17));
    }

    // --- Stub private inner class (reused from BusinessDayCalculatorTest pattern) ---

    private static class StubHolidayCalendar implements HolidayCalendar {
        private final List<BsHoliday> holidays;

        StubHolidayCalendar(List<BsHoliday> holidays) {
            this.holidays = holidays;
        }

        @Override
        public Promise<Void> addHoliday(BsHoliday holiday) {
            return Promise.of(null);
        }

        @Override
        public Promise<List<BsHoliday>> getHolidays(String jurisdiction, int bsYear) {
            return Promise.of(holidays.stream()
                .filter(h -> h.jurisdiction().equals(jurisdiction)
                    && h.date().year() == bsYear)
                .toList());
        }

        @Override
        public Promise<Void> deleteHoliday(String id) {
            return Promise.of(null);
        }
    }
}
