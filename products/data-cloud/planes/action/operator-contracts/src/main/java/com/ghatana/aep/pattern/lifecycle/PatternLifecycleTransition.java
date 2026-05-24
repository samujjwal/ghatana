package com.ghatana.aep.pattern.lifecycle;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Represents an auditable pattern lifecycle transition request
 * @doc.layer product
 * @doc.pattern Contract
 */
public record PatternLifecycleTransition(
        String patternId,
        String tenantId,
        PatternLifecycleState from,
        PatternLifecycleState to,
        PatternLifecycleEventType eventType,
        String actor,
        Map<String, Object> evidence) {

    public PatternLifecycleTransition {
        patternId = requireText(patternId, "patternId");
        tenantId = requireText(tenantId, "tenantId");
        if (from == null) {
            throw new IllegalArgumentException("from must not be null");
        }
        if (to == null) {
            throw new IllegalArgumentException("to must not be null");
        }
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null");
        }
        actor = requireText(actor, "actor");
        evidence = Map.copyOf(evidence != null ? evidence : Map.of());
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
