package com.ghatana.aep.operator.contract;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Carries declarative operator configuration from PatternSpec or PipelineSpec
 * @doc.layer product
 * @doc.pattern Contract
 */
public record OperatorSpec(
        String operatorId,
        OperatorKind kind,
        String inputSchema,
        String outputSchema,
        Map<String, Object> parameters,
        Map<String, Object> policies) {

    public OperatorSpec {
        if (operatorId == null || operatorId.isBlank()) {
            throw new IllegalArgumentException("operatorId must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (inputSchema == null || inputSchema.isBlank()) {
            throw new IllegalArgumentException("inputSchema must not be blank");
        }
        if (outputSchema == null || outputSchema.isBlank()) {
            throw new IllegalArgumentException("outputSchema must not be blank");
        }
        parameters = Map.copyOf(parameters != null ? parameters : Map.of());
        policies = Map.copyOf(policies != null ? policies : Map.of());
    }
}
