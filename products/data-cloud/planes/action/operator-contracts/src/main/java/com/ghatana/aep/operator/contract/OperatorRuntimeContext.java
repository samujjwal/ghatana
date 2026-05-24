package com.ghatana.aep.operator.contract;

import java.util.Map;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Provides runtime identifiers, trace context, and execution policy to EventOperators
 * @doc.layer product
 * @doc.pattern Contract
 */
public record OperatorRuntimeContext(
        String tenantId,
        Optional<String> traceId,
        Optional<String> correlationId,
        Map<String, Object> policies,
        Map<String, Object> metricsTags) {

    public OperatorRuntimeContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        traceId = traceId != null ? traceId : Optional.empty();
        correlationId = correlationId != null ? correlationId : Optional.empty();
        policies = Map.copyOf(policies != null ? policies : Map.of());
        metricsTags = Map.copyOf(metricsTags != null ? metricsTags : Map.of());
    }
}
