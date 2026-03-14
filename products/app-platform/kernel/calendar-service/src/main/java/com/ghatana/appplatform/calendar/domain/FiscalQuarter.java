package com.ghatana.appplatform.calendar.domain;

/**
 * A fiscal quarter within a Nepal fiscal year (Shrawan-based).
 *
 * <p>Quarters are numbered 1–4 relative to the fiscal year start (Shrawan = Q1):
 * <pre>
 * Q1: Shrawan, Bhadra, Ashwin       (BS months 4, 5, 6)  ~Jul–Sep
 * Q2: Kartik, Mangsir, Poush        (BS months 7, 8, 9)  ~Oct–Dec
 * Q3: Magh, Falgun, Chaitra         (BS months 10, 11, 12) ~Jan–Mar
 * Q4: Baisakh, Jestha, Ashadh       (BS months 1, 2, 3)  ~Apr–Jun
 * </pre>
 *
 * @param fiscalYear    The parent fiscal year
 * @param quarter       Quarter number (1–4)
 * @param startBs       First day of the quarter (BS)
 * @param endBs         Last day of the quarter (BS)
 *
 * @doc.type record
 * @doc.purpose Fiscal quarter descriptor within a Nepal BS fiscal year
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public record FiscalQuarter(
    FiscalYear fiscalYear,
    int        quarter,
    BsDate     startBs,
    BsDate     endBs
) {
    public FiscalQuarter {
        if (quarter < 1 || quarter > 4) {
            throw new IllegalArgumentException("quarter must be 1–4, got: " + quarter);
        }
    }

    /** Returns a label such as {@code "Q2 FY 2081/82"}. */
    public String label() {
        return "Q" + quarter + " " + fiscalYear.label();
    }
}
