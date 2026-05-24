package com.ghatana.aep.agent.capability;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @doc.type record
 * @doc.purpose Represents a typed result from a capability invocation
 * @doc.layer product
 * @doc.pattern Contract
 */
public record CapabilityResult<O>(
        boolean success,
        Optional<O> output,
        double confidence,
        Duration latency,
        Map<String, Object> evidence,
        List<String> errors) {

    public CapabilityResult {
        output = output != null ? output : Optional.empty();
        if (Double.isNaN(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
        latency = latency != null ? latency : Duration.ZERO;
        evidence = Map.copyOf(evidence != null ? evidence : Map.of());
        errors = List.copyOf(errors != null ? errors : List.of());
        if (success && !errors.isEmpty()) {
            throw new IllegalArgumentException("successful capability result must not contain errors");
        }
    }

    public static <O> CapabilityResult<O> success(O output, double confidence, Duration latency, Map<String, Object> evidence) {
        return new CapabilityResult<>(true, Optional.ofNullable(output), confidence, latency, evidence, List.of());
    }

    public static <O> CapabilityResult<O> failure(List<String> errors, Map<String, Object> evidence) {
        return new CapabilityResult<>(false, Optional.empty(), 0.0, Duration.ZERO, evidence, errors);
    }
}
