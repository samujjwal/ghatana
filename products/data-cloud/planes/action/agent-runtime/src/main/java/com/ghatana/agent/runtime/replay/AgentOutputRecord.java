package com.ghatana.agent.runtime.replay;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable output record for a typed AgentCapabilityStep result.
 *
 * @doc.type record
 * @doc.purpose Records typed agent output hashes, confidence, and evidence for replay
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record AgentOutputRecord(
        String outputSchema,
        String outputHash,
        Map<String, Object> output,
        double modelConfidence,
        double retrievalConfidence,
        List<String> evidenceRefs
) {

    public AgentOutputRecord {
        requireNonBlank(outputSchema, "outputSchema");
        requireNonBlank(outputHash, "outputHash");
        requireConfidence(modelConfidence, "modelConfidence");
        requireConfidence(retrievalConfidence, "retrievalConfidence");
        output = output == null ? Map.of() : Map.copyOf(output);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }

    private static void requireNonBlank(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireConfidence(double value, String field) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(field + " must be between 0.0 and 1.0");
        }
    }
}
