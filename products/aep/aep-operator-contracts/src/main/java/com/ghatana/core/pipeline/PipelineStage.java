package com.ghatana.core.pipeline;

import com.ghatana.core.operator.OperatorId;
import java.util.Objects;

/**
 * Immutable representation of a single stage (operator) in a pipeline.
 *
 * <p><b>Purpose</b><br>
 * Represents an operator instance within a pipeline DAG. Multiple stages
 * can reference the same operator ID but represent different deployment contexts.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * PipelineStage stage = new PipelineStage(
 *     "fraud-filter",  // Stage ID (unique within pipeline)
 *     OperatorId.parse("stream:filter:1.0"),
 *     Map.of("predicate", "amount > 1000")  // Operator-specific config
 * );
 * }</pre>
 *
 * @param stageId Unique identifier within pipeline (e.g., "fraud-filter")
 * @param operatorId Reference to operator in catalog (e.g., "stream:filter:1.0")
 * @param config Operator-specific configuration (immutable map)
 *
 * @doc.type record
 * @doc.purpose Pipeline stage representing an operator instance
 * @doc.layer core
 * @doc.pattern Value Object
 */
public record PipelineStage(
    String stageId,
    OperatorId operatorId,
    java.util.Map<String, Object> config
) {
    /**
     * Constructor with validation.
     *
     * @param stageId stage identifier (not null, non-empty)
     * @param operatorId operator reference (not null)
     * @param config operator configuration (not null, may be empty)
     */
    public PipelineStage {
        Objects.requireNonNull(stageId, "stageId cannot be null");
        Objects.requireNonNull(operatorId, "operatorId cannot be null");
        Objects.requireNonNull(config, "config cannot be null");
        
        if (stageId.isBlank()) {
            throw new IllegalArgumentException("stageId cannot be blank");
        }
    }

    /**
     * Creates a stage with no configuration.
     *
     * @param stageId stage identifier
     * @param operatorId operator reference
     * @return stage with empty config map
     */
    public static PipelineStage of(String stageId, OperatorId operatorId) {
        return new PipelineStage(stageId, operatorId, java.util.Collections.emptyMap());
    }
}
