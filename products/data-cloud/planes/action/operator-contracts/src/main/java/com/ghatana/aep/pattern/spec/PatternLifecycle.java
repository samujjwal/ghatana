package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * Typed model for PatternSpec lifecycle section.
 *
 * @doc.type record
 * @doc.purpose Typed representation of pattern lifecycle
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternLifecycle(
        String state,
        String evidencePolicy,
        String evidenceStore,
        Map<String, Object> options) {

    public PatternLifecycle {
        Objects.requireNonNull(state, "state");
    }

    public Map<String, Object> toMap() {
        return Map.of(
            "state", state,
            "evidencePolicy", evidencePolicy != null ? evidencePolicy : "",
            "evidenceStore", evidenceStore != null ? evidenceStore : "",
            "options", options != null ? options : Map.of());
    }
}
