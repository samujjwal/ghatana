package com.ghatana.pipeline.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a single stage/step in a pipeline execution flow.
 *
 * <p>Each stage executes an operator and can depend on other stages,
 * forming a directed acyclic graph (DAG) of execution.
 *
 * @doc.type class
 * @doc.purpose Pipeline stage with operator execution and dependencies
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineStage {

    /**
     * Unique identifier for this stage within the pipeline.
     */
    private String id;

    /**
     * Human-readable name for this stage.
     */
    private String name;

    /**
     * Type of stage (FILTER, MAP, AGGREGATE, PATTERN, etc.).
     */
    private StageType type;

    /**
     * ID of the operator to execute for this stage.
     */
    private String operatorId;

    /**
     * Stage-specific configuration parameters.
     */
    @Builder.Default
    private Map<String, String> config = new HashMap<>();

    /**
     * List of stage IDs that must complete before this stage runs.
     */
    @Builder.Default
    private List<String> dependsOn = new ArrayList<>();

    /**
     * Maximum time allowed for this stage to execute.
     */
    private Duration timeout;

    /**
     * Whether this stage is enabled (allows conditional execution).
     */
    @Builder.Default
    private boolean enabled = true;

    /**
     * Stage type enumeration.
     */
    public enum StageType {
        FILTER,
        MAP,
        AGGREGATE,
        PATTERN,
        LEARNING,
        CUSTOM
    }

    /**
     * Check if this stage has dependencies.
     */
    public boolean hasDependencies() {
        return dependsOn != null && !dependsOn.isEmpty();
    }

    /**
     * Add a dependency on another stage.
     */
    public void addDependency(String stageId) {
        if (dependsOn == null) {
            dependsOn = new ArrayList<>();
        }
        dependsOn.add(stageId);
    }

    /**
     * Add a configuration parameter.
     */
    public void addConfig(String key, String value) {
        if (config == null) {
            config = new HashMap<>();
        }
        config.put(key, value);
    }
}

