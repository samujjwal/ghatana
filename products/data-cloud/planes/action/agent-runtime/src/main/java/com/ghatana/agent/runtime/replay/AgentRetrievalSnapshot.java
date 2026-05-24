package com.ghatana.agent.runtime.replay;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable retrieval snapshot for replay and evidence inspection.
 *
 * @doc.type record
 * @doc.purpose Records retrieval versioning and evidence references for one agent execution
 * @doc.layer agent-runtime
 * @doc.pattern ValueObject
 */
public record AgentRetrievalSnapshot(
        String retrievalHash,
        String retrieverVersion,
        List<String> sourceRefs,
        List<String> evidenceRefs,
        Map<String, String> metadata
) {

    public AgentRetrievalSnapshot {
        requireNonBlank(retrievalHash, "retrievalHash");
        requireNonBlank(retrieverVersion, "retrieverVersion");
        sourceRefs = sourceRefs == null ? List.of() : List.copyOf(sourceRefs);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static void requireNonBlank(String value, String field) {
        if (Objects.requireNonNull(value, field).isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
