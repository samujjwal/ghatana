package com.ghatana.aep.pattern.spec;

import java.util.List;
import java.util.Map;

/**
 * @doc.type record
 * @doc.purpose Carries a validated PatternSpec compiled into deterministic runtime graph metadata
 * @doc.layer product
 * @doc.pattern Contract
 */
public record CompiledPattern(
        String patternId,
        String runtimePlanId,
        PatternRuntimeNode root,
        List<String> nodeOrder,
        Map<String, Object> metadata,
        Map<String, Object> semantics,
        Map<String, Object> emit,
        Map<String, Object> lifecycle,
        Map<String, Object> governance) {

    public CompiledPattern {
        if (patternId == null || patternId.isBlank()) {
            throw new IllegalArgumentException("patternId must not be blank");
        }
        if (runtimePlanId == null || runtimePlanId.isBlank()) {
            throw new IllegalArgumentException("runtimePlanId must not be blank");
        }
        if (root == null) {
            throw new IllegalArgumentException("root must not be null");
        }
        nodeOrder = List.copyOf(nodeOrder != null ? nodeOrder : List.of());
        metadata = Map.copyOf(metadata != null ? metadata : Map.of());
        semantics = Map.copyOf(semantics != null ? semantics : Map.of());
        emit = Map.copyOf(emit != null ? emit : Map.of());
        lifecycle = Map.copyOf(lifecycle != null ? lifecycle : Map.of());
        governance = Map.copyOf(governance != null ? governance : Map.of());
    }
}
