package com.ghatana.digitalmarketing.domain.transparency;

/**
 * Canonical AI/system action types visible in transparency timeline.
 *
 * @doc.type enum
 * @doc.purpose DMOS transparency action taxonomy
 * @doc.layer product
 * @doc.pattern ValueObject
 */
public enum AiActionType {
    RECOMMENDATION_GENERATED,
    DRAFT_GENERATED,
    VALIDATION_RESULT,
    APPROVAL_DECISION,
    ACTION_EXECUTED,
    ACTION_BLOCKED
}
