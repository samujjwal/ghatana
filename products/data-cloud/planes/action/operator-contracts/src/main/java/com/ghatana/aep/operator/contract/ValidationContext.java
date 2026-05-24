package com.ghatana.aep.operator.contract;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Provides tenant, schema, and policy context for operator validation
 * @doc.layer product
 * @doc.pattern Contract
 */
public record ValidationContext(
        String tenantId,
        Map<String, Object> schemaRegistry,
        Map<String, Object> policies) {

    public ValidationContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        schemaRegistry = Map.copyOf(schemaRegistry != null ? schemaRegistry : Map.of());
        policies = Map.copyOf(policies != null ? policies : Map.of());
    }
}
