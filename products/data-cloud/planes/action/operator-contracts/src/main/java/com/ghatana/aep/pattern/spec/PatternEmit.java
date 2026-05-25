package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * Typed model for PatternSpec emit section.
 *
 * @doc.type record
 * @doc.purpose Typed representation of pattern emit configuration
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternEmit(
        String eventType,
        String outputSchema,
        Map<String, Object> payloadTransform) {

    public PatternEmit {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(outputSchema, "outputSchema");
    }

    public Map<String, Object> toMap() {
        return Map.of(
            "eventType", eventType,
            "outputSchema", outputSchema,
            "payloadTransform", payloadTransform != null ? payloadTransform : Map.of());
    }
}
