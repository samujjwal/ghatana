package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * Typed model for PatternSpec semantics section.
 *
 * @doc.type record
 * @doc.purpose Typed representation of pattern semantics
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternSemantics(
        String timePolicy,
        String timeMode,
        String uncertaintyPolicy,
        String replayPolicy,
        Map<String, Object> options) {

    public PatternSemantics {
        Objects.requireNonNull(uncertaintyPolicy, "uncertaintyPolicy");
        Objects.requireNonNull(replayPolicy, "replayPolicy");
        if (timePolicy == null && timeMode == null) {
            throw new IllegalArgumentException("timePolicy or timeMode is required");
        }
    }

    public Map<String, Object> toMap() {
        return Map.of(
            "timePolicy", timePolicy != null ? timePolicy : "",
            "timeMode", timeMode != null ? timeMode : "",
            "uncertaintyPolicy", uncertaintyPolicy,
            "replayPolicy", replayPolicy,
            "options", options != null ? options : Map.of());
    }
}
