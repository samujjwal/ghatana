package com.ghatana.aep.operator.contract;

import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Provides tenant and runtime options for compiling operators into runtime plans
 * @doc.layer product
 * @doc.pattern Contract
 */
public record CompileContext(String tenantId, Map<String, Object> runtimeOptions) {

    public CompileContext {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        runtimeOptions = Map.copyOf(runtimeOptions != null ? runtimeOptions : Map.of());
    }
}
