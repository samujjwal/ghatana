package com.ghatana.core.pipeline;

import com.ghatana.core.operator.OperatorId;
import java.util.Map;

/**
 * Fluent builder interface for constructing pipelines.
 *
 * <p><b>Purpose</b><br>
 * Provides declarative API for building event processing pipelines with stages
 * and edges. Enables readable pipeline construction without manual DAG management.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * Pipeline pipeline = Pipeline.builder("fraud-detection", "1.0.0")
 *     .name("Real-Time Fraud Detection")
 *     .description("Detects fraudulent transactions")
 *     .stage("filter", OperatorId.parse("stream:filter:1.0"),
 *         Map.of("predicate", "amount > 1000"))
 *     .stage("enrich", OperatorId.parse("stream:map:1.0"))
 *     .stage("detect", OperatorId.parse("pattern:seq:1.0"))
 *     .edge("filter", "enrich")
 *     .edge("enrich", "detect")
 *     .metadata("owner", "fraud-team")
 *     .metadata("tags", List.of("fraud", "real-time"))
 *     .build();
 * }</pre>
 *
 * @doc.type interface
 * @doc.purpose Fluent builder for declarative pipeline construction
 * @doc.layer core
 * @doc.pattern Builder
 */
public interface PipelineBuilder {

    /**
     * Sets the pipeline name.
     *
     * @param name human-readable name
     * @return this builder for chaining
     */
    PipelineBuilder name(String name);

    /**
     * Sets the pipeline description.
     *
     * @param description purpose and behavior documentation
     * @return this builder for chaining
     */
    PipelineBuilder description(String description);

    /**
     * Adds a stage (operator instance) to the pipeline.
     *
     * @param stageId unique stage identifier within pipeline
     * @param operatorId reference to operator in catalog
     * @return this builder for chaining
     */
    PipelineBuilder stage(String stageId, OperatorId operatorId);

    /**
     * Adds a stage with configuration.
     *
     * @param stageId unique stage identifier within pipeline
     * @param operatorId reference to operator in catalog
     * @param config operator-specific configuration
     * @return this builder for chaining
     */
    PipelineBuilder stage(String stageId, OperatorId operatorId, Map<String, Object> config);

    /**
     * Adds a primary flow edge (data dependency).
     *
     * @param from source stage ID
     * @param to target stage ID
     * @return this builder for chaining
     */
    PipelineBuilder edge(String from, String to);

    /**
     * Adds a labeled edge (data dependency with routing label).
     *
     * @param from source stage ID
     * @param to target stage ID
     * @param label edge label (e.g., "primary", "error", "fallback")
     * @return this builder for chaining
     */
    PipelineBuilder edge(String from, String to, String label);

    /**
     * Adds an error handling edge.
     *
     * @param from source stage ID
     * @param to error handler stage ID
     * @return this builder for chaining
     */
    PipelineBuilder onError(String from, String to);

    /**
     * Adds a fallback edge.
     *
     * @param from source stage ID
     * @param to fallback stage ID
     * @return this builder for chaining
     */
    PipelineBuilder onFallback(String from, String to);

    /**
     * Adds metadata (tag, owner, custom properties).
     *
     * @param key metadata key
     * @param value metadata value
     * @return this builder for chaining
     */
    PipelineBuilder metadata(String key, Object value);

    /**
     * Builds the pipeline.
     *
     * <p>Validates pipeline structure (DAG, no cycles, references valid).
     * Throws exception if validation fails.
     *
     * @return constructed immutable pipeline
     * @throws IllegalStateException if pipeline structure is invalid
     */
    Pipeline build();
}
