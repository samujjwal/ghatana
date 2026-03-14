package com.ghatana.appplatform.calendar.service;

import com.ghatana.appplatform.calendar.domain.BsDate;
import com.ghatana.appplatform.calendar.domain.FiscalQuarter;
import com.ghatana.appplatform.calendar.domain.FiscalYear;
import com.ghatana.platform.testing.activej.EventloopTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link FiscalYearCalculator}.
 *
 * <p>Reference dates verified against Nepal government and NRB calendars.
 * Nepal fiscal year starts Shrawan 1 (BS month 4), running to Ashadh end (BS month 3).
 *
 * @doc.type class
 * @doc.purpose Unit tests for BS fiscal year, quarter, and period calculations
 * @doc.layer product
 * @doc.pattern Test
 */
@DisplayName("FiscalYearCalculator Tests")
class FiscalYearCalculatorTest extends EventloopTestBase {

    private final FiscalYearCalculator calc = new FiscalYearCalculator();

    // -------------------------------------------------------------------------
    // K15-008: Fiscal year boundaries
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Shrawan 1 2081 is the start of fiscal year FY 2081/82")
    void shrawan1IsStartOfFY2081() {
        // Shrawan 1, 2081 BS ≈ July 17, 2024 Greg (approximate)
        // FY 2081/82 runs Shrawan 1, 2081 → Ashadh end, 2082
        FiscalYear fy = calc.getFiscalYear(LocalDate.of(2024, 7, 17));
        assertThat(fy.bsStartYear()).isEqualTo(2081);
        assertThat(fy.label()).isEqualTo("FY 2081/82");
        assertThat(fy.startBs()).isEqualTo(BsDate.of(2081, 4, 1));
        assertThat(fy.endBs().month()).isEqualTo(3);
        assertThat(fy.endBs().year()).isEqualTo(2082);
    }

    @Test
    @DisplayName("March 13 2026 (BS 2082/11/29) is in fiscal year FY 2082/83")
    void march2026IsInFY2082() {
        // BS 2082/11 = Falgun = Q3 of FY 2082/83 (FY starts Shrawan 2082 ≈ Jul 2025)
        FiscalYear fy = calc.getFiscalYear(LocalDate.of(2026, 3, 13));
        assertThat(fy.bsStartYear()).isEqualTo(2082);
        assertThat(fy.label()).isEqualTo("FY 2082/83");
    }

    @Test
    @DisplayName("Ashadh last day 2082 is in fiscal year FY 2081/82 (last day of previous FY)")
    void lastDayAshadh2082IsInFY2081() {
        // Ashadh (month 3) 2082 ends the FY 2081/82
        int lastDay = com.ghatana.appplatform.calendar.conversion.BsCalendarConversion
            .getMonthLength(2082, 3);
        java.time.LocalDate gregorianEnd = com.ghatana.appplatform.calendar.conversion
            .BsCalendarConversion.bsToGregorian(2082, 3, lastDay);
        FiscalYear fy = calc.getFiscalYear(gregorianEnd);
        assertThat(fy.bsStartYear()).isEqualTo(2081);
        assertThat(fy.label()).isEqualTo("FY 2081/82");
    }

    @Test
    @DisplayName("FiscalYear.contains() returns true for days within that year")
    void fiscalYearContains() {
        FiscalYear fy = calc.getFiscalYear(LocalDate.of(2024, 7, 17));
        assertThat(fy.contains(LocalDate.of(2025, 1, 15))).isTrue();
        assertThat(fy.contains(LocalDate.of(2025, 7, 30))).isFalse(); // next FY
        assertThat(fy.contains(LocalDate.of(2024, 7, 16))).isFalse(); // before FY
    }

    @Test
    @DisplayName("Custom fiscal year start month is respected")
    void customFiscalYearStart() {
        FiscalYearCalculator janStart = new FiscalYearCalculator(10); // Poush = month 10 ≈ Jan
        FiscalYear fy = janStart.getFiscalYear(LocalDate.of(2026, 1, 15));
        // month 10 of 2082 ≈ Jan 2026 → FY starts at month 10 of 2082
        assertThat(fy.startBs().month()).isEqualTo(10);
    }

    @Test
    @DisplayName("Invalid fiscal year start month throws IllegalArgumentException")
    void invalidStartMonthThrows() {
        assertThatThrownBy(() -> new FiscalYearCalculator(13))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("1–12");
    }

    // -------------------------------------------------------------------------
    // K15-009: Fiscal quarter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Shrawan (fiscal month 1) is in Q1")
    void shrawanIsQ1() {
        // A date in Shrawan (BS month 4) should be Q1
        LocalDate shrawanDate = LocalDate.of(2024, 7, 20); // approximately Shrawan 2081
        FiscalQuarter q = calc.getFiscalQuarter(shrawanDate);
        assertThat(q.quarter()).isEqualTo(1);
        assertThat(q.label()).startsWith("Q1");
    }

    @Test
    @DisplayName("Poush (fiscal period 6) is in Q2")
    void poushIsQ2() {
        // Poush = BS month 9, fiscal period = 9-4+1=6, Q2
        LocalDate poushDate = LocalDate.of(2025, 1, 5); // approximately Poush 2081
        FiscalQuarter q = calc.getFiscalQuarter(poushDate);
        assertThat(q.quarter()).isEqualTo(2);
    }

    @Test
    @DisplayName("Falgun (BS month 11) is in Q3")
    void falgunIsQ3() {
        // March 13 2026 = BS 2082/11 = Falgun = fiscal period 8, Q3
        FiscalQuarter q = calc.getFiscalQuarter(LocalDate.of(2026, 3, 13));
        assertThat(q.quarter()).isEqualTo(3);
    }

    @Test
    @DisplayName("Ashadh (BS month 3 = last month of FY) is in Q4")
    void ashadhIsQ4() {
        // Ashadh = BS month 3, fiscal period = (3-4+12)%12+1 = 12, Q4
        LocalDate ashadhDate = LocalDate.of(2025, 6, 20); // approximately Ashadh 2082
        FiscalQuarter q = calc.getFiscalQuarter(ashadhDate);
        assertThat(q.quarter()).isEqualTo(4);
    }

    // -------------------------------------------------------------------------
    // K15-009: Fiscal period
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Shrawan is fiscal period 1")
    void shrawanIsPeriod1() {
        LocalDate shrawanDate = LocalDate.of(2024, 7, 20);
        int period = calc.getFiscalPeriod(shrawanDate);
        assertThat(period).isEqualTo(1);
    }

    @Test
    @DisplayName("Ashadh is fiscal period 12")
    void ashadhIsPeriod12() {
        LocalDate ashadhDate = LocalDate.of(2025, 6, 20);
        int period = calc.getFiscalPeriod(ashadhDate);
        assertThat(period).isEqualTo(12);
    }
}
