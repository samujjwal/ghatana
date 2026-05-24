package com.ghatana.aep.operator.contract;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Represents a deterministic operator runtime plan emitted by validation/compilation
 * @doc.layer product
 * @doc.pattern Contract
 */
public record RuntimePlan(
        String planId,
        List<String> operatorIds,
        Map<String, Object> executionHints,
        Map<String, Object> observability) {

    public RuntimePlan {
        if (planId == null || planId.isBlank()) {
            throw new IllegalArgumentException("planId must not be blank");
        }
        operatorIds = List.copyOf(operatorIds != null ? operatorIds : List.of());
        executionHints = Map.copyOf(executionHints != null ? executionHints : Map.of());
        observability = Map.copyOf(observability != null ? observability : Map.of());
    }
}
