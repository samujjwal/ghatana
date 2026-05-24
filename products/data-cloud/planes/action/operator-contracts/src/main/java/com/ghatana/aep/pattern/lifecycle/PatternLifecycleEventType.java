package com.ghatana.aep.pattern.lifecycle;

/**
 * @doc.type enum
 * @doc.purpose Enumerates auditable PatternSpec lifecycle event types
 * @doc.layer product
 * @doc.pattern Enumeration
 */
public enum PatternLifecycleEventType {
    PATTERN_CREATED("pattern.created"),
    PATTERN_VALIDATED("pattern.validated"),
    PATTERN_COMPILED("pattern.compiled"),
    PATTERN_SHADOW_DEPLOYED("pattern.shadow_deployed"),
    PATTERN_SHADOW_EVALUATED("pattern.shadow_evaluated"),
    PATTERN_RECOMMENDED("pattern.recommended"),
    PATTERN_REVIEW_REQUESTED("pattern.review_requested"),
    PATTERN_REVIEW_COMPLETED("pattern.review_completed"),
    PATTERN_APPROVED("pattern.approved"),
    PATTERN_PROMOTED("pattern.promoted"),
    PATTERN_DEGRADED("pattern.degraded"),
    PATTERN_RETIRED("pattern.retired"),
    PATTERN_ROLLBACK_REQUESTED("pattern.rollback_requested"),
    PATTERN_ROLLBACK_COMPLETED("pattern.rollback_completed");

    private final String eventType;

    PatternLifecycleEventType(String eventType) {
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }
}
