package com.ghatana.digitalmarketing.domain.lead;

/**
 * Qualification status of a CRM lead.
 *
 * @doc.type enum
 * @doc.purpose Lead qualification status for CRM-lite lifecycle
 * @doc.layer product
 * @doc.pattern StatePattern
 */
public enum LeadStatus {

    /** Lead has been captured but not yet reviewed. */
    NEW,

    /** Lead is being evaluated or contacted. */
    QUALIFIED,

    /** Lead has been converted to a customer opportunity. */
    CONVERTED,

    /** Lead has been discarded (duplicate, spam, or unresponsive). */
    DISQUALIFIED
}
