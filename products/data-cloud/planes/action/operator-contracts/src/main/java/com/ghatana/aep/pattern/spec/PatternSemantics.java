package com.ghatana.aep.pattern.spec;

import java.util.Map;
import java.util.Objects;

/**
 * First-class typed model for PatternSpec semantics section (P4-02).
 *
 * <p>P4-02: PatternSemantics now includes schema information for
 * integration with the operator execution model.
 *
 * @doc.type record
 * @doc.purpose First-class typed representation of pattern semantics with schema contracts
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternSemantics(
        String timePolicy,
        String timeMode,
        String uncertaintyPolicy,
        String replayPolicy,
        String inputSchema,
        String outputSchema,
        Map<String, Object> options) {

    public PatternSemantics {
        Objects.requireNonNull(uncertaintyPolicy, "uncertaintyPolicy");
        Objects.requireNonNull(replayPolicy, "replayPolicy");
        if (timePolicy == null && timeMode == null) {
            throw new IllegalArgumentException("timePolicy or timeMode is required");
        }
    }

    /**
     * Backward-compatible constructor for existing callers.
     */
    public PatternSemantics(String timePolicy, String timeMode, String uncertaintyPolicy, String replayPolicy, Map<String, Object> options) {
        this(timePolicy, timeMode, uncertaintyPolicy, replayPolicy, "any", "any", options);
    }

    public Map<String, Object> toMap() {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("timePolicy", timePolicy != null ? timePolicy : "");
        map.put("timeMode", timeMode != null ? timeMode : "");
        map.put("uncertaintyPolicy", uncertaintyPolicy);
        map.put("replayPolicy", replayPolicy);
        map.put("inputSchema", inputSchema != null ? inputSchema : "any");
        map.put("outputSchema", outputSchema != null ? outputSchema : "any");
        map.put("options", options != null ? options : Map.of());
        return java.util.Collections.unmodifiableMap(map);
    }

    // ==================== Schema Methods (P4-02) ====================

    /**
     * Get the input schema for this pattern.
     * @return the input schema type (defaults to "any")
     */
    public String inputSchema() {
        return inputSchema != null ? inputSchema : "any";
    }

    /**
     * Get the output schema for this pattern.
     * @return the output schema type (defaults to "any")
     */
    public String outputSchema() {
        return outputSchema != null ? outputSchema : "any";
    }

    /**
     * Check if this pattern has defined input/output schemas.
     */
    public boolean hasSchemaContracts() {
        return !"any".equals(inputSchema()) || !"any".equals(outputSchema());
    }
}
