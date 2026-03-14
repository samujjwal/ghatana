package com.ghatana.appplatform.calendar.service;

import com.ghatana.appplatform.calendar.conversion.BsCalendarConversion;
import com.ghatana.appplatform.calendar.conversion.BsCalendarConversion.BsDateComponents;
import com.ghatana.appplatform.calendar.domain.BsDate;
import com.ghatana.appplatform.calendar.domain.FiscalQuarter;
import com.ghatana.appplatform.calendar.domain.FiscalYear;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Fiscal year, quarter, and period calculations for Nepal's BS-based fiscal calendar.
 *
 * <h3>Nepal Fiscal Year</h3>
 * <p>Nepal's fiscal year runs from <em>Shrawan 1</em> (BS month 4, typically mid-July)
 * to the last day of <em>Ashadh</em> (BS month 3, typically mid-July of the following
 * Gregorian year). The fiscal year is conventionally labelled as the pair of BS years
 * it spans, e.g. "FY 2081/82".
 *
 * <h3>Fiscal Quarters</h3>
 * <pre>
 * Q1: Shrawan–Ashwin   (BS months 4–6)  ~Jul–Sep
 * Q2: Kartik–Poush     (BS months 7–9)  ~Oct–Dec
 * Q3: Magh–Chaitra     (BS months 10–12) ~Jan–Mar
 * Q4: Baisakh–Ashadh   (BS months 1–3)  ~Apr–Jun
 * </pre>
 *
 * <h3>Fiscal Period</h3>
 * <p>Period 1 = Shrawan, Period 12 = Ashadh (following BS year).
 *
 * @doc.type class
 * @doc.purpose Nepal BS fiscal year, quarter, and period calculations
 * @doc.layer product
 * @doc.pattern Service
 */
public final class FiscalYearCalculator {

    /**
     * Nepal default fiscal year start: BS month 4 (Shrawan), day 1.
     */
    private static final int DEFAULT_FY_START_MONTH = 4;

    /** Fiscal year start month, overridable per jurisdiction (default = 4 = Shrawan). */
    private final int fyStartMonth;

    /** Creates a calculator using Nepal's standard fiscal year (Shrawan start). */
    public FiscalYearCalculator() {
        this(DEFAULT_FY_START_MONTH);
    }

    /**
     * Creates a calculator with a custom fiscal year start month (1–12 BS).
     *
     * @param fiscalYearStartMonth BS month number (1=Baisakh … 12=Chaitra)
     */
    public FiscalYearCalculator(int fiscalYearStartMonth) {
        if (fiscalYearStartMonth < 1 || fiscalYearStartMonth > 12) {
            throw new IllegalArgumentException(
                "fiscalYearStartMonth must be 1–12, got: " + fiscalYearStartMonth);
        }
        this.fyStartMonth = fiscalYearStartMonth;
    }

    // -------------------------------------------------------------------------
    // K15-008: Fiscal year boundaries
    // -------------------------------------------------------------------------

    /**
     * Returns the fiscal year that contains the given Gregorian date.
     *
     * @param gregorianDate Gregorian date to resolve
     * @return the containing {@link FiscalYear}
     */
    public FiscalYear getFiscalYear(LocalDate gregorianDate) {
        Objects.requireNonNull(gregorianDate, "gregorianDate");
        BsDateComponents bs  = BsCalendarConversion.gregorianToBs(gregorianDate);
        return fiscalYearForBs(bs.year(), bs.month());
    }

    /**
     * Returns the fiscal year that contains the given BS date.
     *
     * @param bsDate BS calendar date
     * @return the containing {@link FiscalYear}
     */
    public FiscalYear getFiscalYearForBs(BsDate bsDate) {
        Objects.requireNonNull(bsDate, "bsDate");
        return fiscalYearForBs(bsDate.year(), bsDate.month());
    }

    // -------------------------------------------------------------------------
    // K15-009: Fiscal quarter and period
    // -------------------------------------------------------------------------

    /**
     * Returns the fiscal quarter (1–4) for the given Gregorian date.
     *
     * @param gregorianDate Gregorian date to resolve
     * @return {@link FiscalQuarter} descriptor
     */
    public FiscalQuarter getFiscalQuarter(LocalDate gregorianDate) {
        Objects.requireNonNull(gregorianDate, "gregorianDate");
        BsDateComponents bs = BsCalendarConversion.gregorianToBs(gregorianDate);
        FiscalYear fy       = fiscalYearForBs(bs.year(), bs.month());
        int quarterNumber   = quarterForBsMonth(bs.month());
        return buildQuarter(fy, quarterNumber);
    }

    /**
     * Returns the fiscal period number (1–12) within the fiscal year for the given
     * Gregorian date.
     *
     * <p>Period 1 = first month of the fiscal year (e.g. Shrawan for Nepal).
     * Period 12 = last month (e.g. Ashadh).
     *
     * @param gregorianDate Gregorian date to resolve
     * @return period number in the range 1–12
     */
    public int getFiscalPeriod(LocalDate gregorianDate) {
        Objects.requireNonNull(gregorianDate, "gregorianDate");
        BsDateComponents bs = BsCalendarConversion.gregorianToBs(gregorianDate);
        return fiscalPeriodForBsMonth(bs.month());
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /**
     * Derives the fiscal year for a given BS year+month.
     *
     * <p>If the BS month is in [fyStartMonth .. 12] (e.g. Shrawan–Chaitra for Nepal),
     * the fiscal year starts in that BS year. Otherwise (months 1–fyStartMonth-1,
     * i.e. Baisakh–Ashadh for Nepal), the fiscal year started in the previous BS year.
     */
    private FiscalYear fiscalYearForBs(int bsYear, int bsMonth) {
        int fyBsStart = (bsMonth >= fyStartMonth) ? bsYear : bsYear - 1;
        int fyBsEnd   = fyBsStart + 1;

        // Start: fyStartMonth, day 1, in fyBsStart BS year
        BsDate startBs = BsDate.of(fyBsStart, fyStartMonth, 1);
        LocalDate startGreg = BsCalendarConversion.bsToGregorian(
            startBs.year(), startBs.month(), startBs.day());

        // End: last day of (fyStartMonth-1) in fyBsEnd BS year
        int endMonth = (fyStartMonth == 1) ? 12 : fyStartMonth - 1;
        int endBsYear = (fyStartMonth == 1) ? fyBsEnd - 1 : fyBsEnd;
        int endDay    = BsCalendarConversion.getMonthLength(endBsYear, endMonth);
        BsDate endBs  = BsDate.of(endBsYear, endMonth, endDay);
        LocalDate endGreg = BsCalendarConversion.bsToGregorian(
            endBs.year(), endBs.month(), endBs.day());

        String label = "FY " + fyBsStart + "/" + (fyBsEnd % 100 < 10
            ? "0" + (fyBsEnd % 100) : String.valueOf(fyBsEnd % 100));

        return new FiscalYear(fyBsStart, startBs, endBs, startGreg, endGreg, label);
    }

    /**
     * Maps a BS month to a quarter number (1–4) relative to the fiscal year start.
     *
     * <p>Quarter boundaries advance every 3 months from {@code fyStartMonth}.
     */
    private int quarterForBsMonth(int bsMonth) {
        return fiscalPeriodForBsMonth(bsMonth) / 3 + 1;
    }

    /**
     * Maps a BS month to a fiscal period number (1–12) relative to the fiscal year start.
     */
    private int fiscalPeriodForBsMonth(int bsMonth) {
        // Offset from fiscal year start (0-based), wrapping modulo 12
        return ((bsMonth - fyStartMonth + 12) % 12) + 1;
    }

    /** Quarter number → the three BS months in that quarter (relative to fiscal year start). */
    private FiscalQuarter buildQuarter(FiscalYear fy, int quarterNumber) {
        // Period 1, 4, 7, 10 are the first months of Q1, Q2, Q3, Q4
        int periodStart   = (quarterNumber - 1) * 3 + 1;
        int periodEnd     = periodStart + 2;

        int qStartBsMonth = bsMonthForPeriod(periodStart);
        int qEndBsMonth   = bsMonthForPeriod(periodEnd);

        // Derive BS years from the fiscal year
        int qStartYear, qEndYear;
        if (qStartBsMonth >= fyStartMonth) {
            qStartYear = fy.bsStartYear();
        } else {
            qStartYear = fy.bsStartYear() + 1;
        }
        if (qEndBsMonth >= fyStartMonth) {
            qEndYear = fy.bsStartYear();
        } else {
            qEndYear = fy.bsStartYear() + 1;
        }

        BsDate qStartBs = BsDate.of(qStartYear, qStartBsMonth, 1);
        int lastDay     = BsCalendarConversion.getMonthLength(qEndYear, qEndBsMonth);
        BsDate qEndBs   = BsDate.of(qEndYear, qEndBsMonth, lastDay);

        return new FiscalQuarter(fy, quarterNumber, qStartBs, qEndBs);
    }

    /** Converts a fiscal period number (1–12) back to the absolute BS month number (1–12). */
    private int bsMonthForPeriod(int period) {
        return ((fyStartMonth - 1 + period - 1) % 12) + 1;
    }
}
