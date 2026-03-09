package com.ghatana.pipeline.registry.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured pipeline configuration containing inputs, outputs, and operational settings.
 *
 * <p>This replaces the opaque JSON string config with a type-safe structure
 * that can be validated, queried, and evolved with backward compatibility.
 *
 * @doc.type class
 * @doc.purpose Structured pipeline configuration with type safety
 * @doc.layer product
 * @doc.pattern ValueObject
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineConfig {

    /**
     * Input event streams that feed this pipeline.
     */
    @Builder.Default
    private List<String> inputs = new ArrayList<>();

    /**
     * Output event streams produced by this pipeline.
     */
    @Builder.Default
    private List<String> outputs = new ArrayList<>();

    /**
     * Pipeline-level parameters (key-value configuration).
     */
    @Builder.Default
    private Map<String, String> parameters = new HashMap<>();

    /**
     * Error handling strategy configuration.
     */
    private ErrorHandling errorHandling;

    /**
     * Retry configuration for failed operations.
     */
    private RetryConfig retryConfig;

    /**
     * Resource requirements for pipeline execution.
     */
    private ResourceRequirements resources;

    /**
     * Check if this configuration has inputs defined.
     */
    public boolean hasInputs() {
        return inputs != null && !inputs.isEmpty();
    }

    /**
     * Check if this configuration has outputs defined.
     */
    public boolean hasOutputs() {
        return outputs != null && !outputs.isEmpty();
    }

    /**
     * Add an input stream.
     */
    public void addInput(String input) {
        if (inputs == null) {
            inputs = new ArrayList<>();
        }
        inputs.add(input);
    }

    /**
     * Add an output stream.
     */
    public void addOutput(String output) {
        if (outputs == null) {
            outputs = new ArrayList<>();
        }
        outputs.add(output);
    }

    /**
     * Add a parameter.
     */
    public void addParameter(String key, String value) {
        if (parameters == null) {
            parameters = new HashMap<>();
        }
        parameters.put(key, value);
    }
}

