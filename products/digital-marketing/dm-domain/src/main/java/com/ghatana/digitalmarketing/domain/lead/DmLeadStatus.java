package com.ghatana.digitalmarketing.domain.lead;

/**
 * Lead lifecycle status.
 *
 * @doc.type class
 * @doc.purpose Tracks lead processing status (DMOS-F2-011)
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum DmLeadStatus {
    NEW,
    QUALIFIED,
    CONTACTED,
    CONVERTED,
    DISQUALIFIED
}
