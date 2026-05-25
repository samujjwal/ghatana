package com.ghatana.aep.pattern.spec;

import java.util.Map;

/**
 * Typed model for PatternSpec observability section.
 *
 * @doc.type record
 * @doc.purpose Typed representation of pattern observability
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternObservability(
        String metricsPolicy,
        String loggingPolicy,
        Map<String, Object> options) {

    public Map<String, Object> toMap() {
        return Map.of(
            "metricsPolicy", metricsPolicy != null ? metricsPolicy : "",
            "loggingPolicy", loggingPolicy != null ? loggingPolicy : "",
            "options", options != null ? options : Map.of());
    }
}
