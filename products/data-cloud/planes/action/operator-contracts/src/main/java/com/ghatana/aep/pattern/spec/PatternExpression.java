package com.ghatana.aep.pattern.spec;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Typed model for PatternSpec pattern expression.
 *
 * @doc.type record
 * @doc.purpose Typed representation of a pattern expression
 * @doc.layer product
 * @doc.pattern Model
 */
public record PatternExpression(
        String operator,
        String event,
        String capabilityRef,
        String agentRef,
        String inputSchema,
        String outputSchema,
        List<PatternExpression> operands,
        PatternExpression pattern,
        Map<String, Object> parameters,
        Map<String, Object> toolPolicy,
        String window,
        String windowSpec) {

    public PatternExpression {
        if (operator == null && event == null) {
            throw new IllegalArgumentException("operator or event is required");
        }
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        if (operator != null) {
            map.put("operator", operator);
        }
        if (event != null) {
            map.put("event", event);
        }
        if (capabilityRef != null) {
            map.put("capabilityRef", capabilityRef);
        }
        if (agentRef != null) {
            map.put("agentRef", agentRef);
        }
        if (inputSchema != null) {
            map.put("inputSchema", inputSchema);
        }
        if (outputSchema != null) {
            map.put("outputSchema", outputSchema);
        }
        if (operands != null && !operands.isEmpty()) {
            map.put("operands", operands.stream().map(PatternExpression::toMap).toList());
        }
        if (pattern != null) {
            map.put("pattern", pattern.toMap());
        }
        if (window != null) {
            map.put("window", window);
        }
        if (windowSpec != null) {
            map.put("windowSpec", windowSpec);
        }
        if (parameters != null && !parameters.isEmpty()) {
            map.putAll(parameters);
        }
        if (toolPolicy != null && !toolPolicy.isEmpty()) {
            map.put("toolPolicy", toolPolicy);
        }
        return map;
    }
}
