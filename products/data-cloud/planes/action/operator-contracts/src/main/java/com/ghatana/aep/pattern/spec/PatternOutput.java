package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * Canonical output specification for PatternSpec.
 *
 * <p>Defines the output contract for a pattern, including schema,
 * transformation rules, and destination configuration.
 *
 * @doc.type record
 * @doc.purpose Canonical output specification for PatternSpec
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternOutput(
        String schema,
        String destination,
        Map<String, Object> transformation,
        Map<String, Object> options) {

    public PatternOutput {
        Objects.requireNonNull(schema, "schema");
    }

    /**
     * Create a minimal PatternOutput with just a schema.
     *
     * @param schema output schema
     * @return PatternOutput instance
     */
    public static PatternOutput of(String schema) {
        return new PatternOutput(schema, null, null, null);
    }

    /**
     * Convert this PatternOutput to a map representation.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("schema", schema);
        if (destination != null) map.put("destination", destination);
        if (transformation != null && !transformation.isEmpty()) map.put("transformation", transformation);
        if (options != null && !options.isEmpty()) map.put("options", options);
        return java.util.Collections.unmodifiableMap(map);
    }
}
