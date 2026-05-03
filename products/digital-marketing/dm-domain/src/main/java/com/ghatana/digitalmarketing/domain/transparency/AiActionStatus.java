package com.ghatana.digitalmarketing.domain.transparency;

/**
 * Action visibility state used in AI transparency timeline.
 *
 * @doc.type enum
 * @doc.purpose DMOS transparency action status model
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum AiActionStatus {
    PROPOSED,
    EXECUTED,
    BLOCKED,
    APPROVED,
    REJECTED
}
