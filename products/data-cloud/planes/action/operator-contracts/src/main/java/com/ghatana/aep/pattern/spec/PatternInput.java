package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * Canonical input specification for PatternSpec.
 *
 * <p>Defines the input contract for a pattern, including schema,
 * validation rules, and source configuration.
 *
 * @doc.type record
 * @doc.purpose Canonical input specification for PatternSpec
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternInput(
        String schema,
        String source,
        Map<String, Object> validation,
        Map<String, Object> options) {

    public PatternInput {
        Objects.requireNonNull(schema, "schema");
    }

    /**
     * Create a minimal PatternInput with just a schema.
     *
     * @param schema input schema
     * @return PatternInput instance
     */
    public static PatternInput of(String schema) {
        return new PatternInput(schema, null, null, null);
    }

    /**
     * Convert this PatternInput to a map representation.
     *
     * @return map representation
     */
    public Map<String, Object> toMap() {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("schema", schema);
        if (source != null) map.put("source", source);
        if (validation != null && !validation.isEmpty()) map.put("validation", validation);
        if (options != null && !options.isEmpty()) map.put("options", options);
        return java.util.Collections.unmodifiableMap(map);
    }
}
