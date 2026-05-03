package com.ghatana.digitalmarketing.domain.narrative;

/**
 * Period type for narrative reviews.
 *
 * @doc.type class
 * @doc.purpose Distinguishes weekly vs monthly reviews (DMOS-F3-006)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmNarrativePeriodType {
    WEEKLY,
    MONTHLY
}
