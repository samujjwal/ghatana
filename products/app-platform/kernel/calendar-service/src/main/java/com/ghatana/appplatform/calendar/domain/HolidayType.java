package com.ghatana.appplatform.calendar.domain;

/**
 * Classification of a holiday in the holiday calendar.
 *
 * <ul>
 *   <li>{@code PUBLIC}     — national/province public holiday (banks and offices closed)</li>
 *   <li>{@code TRADING}    — trading halt (e.g. stock market closed but banks open)</li>
 *   <li>{@code SETTLEMENT} — settlement holiday (payments/clearing suspended)</li>
 * </ul>
 *
 * @doc.type enum
 * @doc.purpose Categories of holidays for business-day and settlement logic
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum HolidayType {
    /** National/province public holiday — banks and government offices closed. */
    PUBLIC,
    /** Trading halt — exchange closed but banks may be open. */
    TRADING,
    /** Payment/clearing settlement suspended. */
    SETTLEMENT
}
